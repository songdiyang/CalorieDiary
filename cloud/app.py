#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
热量日记 - 云端 AI 识别代理（阿里云函数计算 FC / 任意可运行 Python 3 的容器）

设计要点：
1. DeepSeek 密钥只存在服务端环境变量，App 不接触、不写入客户端。
2. App 上传 JPEG base64，函数注入密钥转发 DeepSeek，回传 {"foods":[...]}。
3. 无状态、即算即回，仅依赖 Python 标准库。
4. 安全兜底：
   - APP_KEY 未配置时拒绝一切识别请求（fail-closed，绝不静默放行）
   - 请求体大小上限，防止超大请求打爆实例内存
   - 按真实客户端 IP 限流 + 单实例每日全局配额
5. 错误码与 App 端 NetworkErrorKind 对齐，详见同目录 README.md 错误码表。

超时链路约定：App read(100s) > FC 函数超时(90s) > 上游 urlopen(80s)
本地运行：python3 app.py
"""

import hashlib
import hmac
import json
import os
import re
import threading
import time
import urllib.error
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# ---------------- 配置（全部从环境变量读取） ----------------
DEEPSEEK_ENDPOINT = os.environ.get(
    "DEEPSEEK_ENDPOINT", "https://api.deepseek.com/chat/completions"
)
DEEPSEEK_MODEL = os.environ.get("DEEPSEEK_MODEL", "deepseek-flash")
DEEPSEEK_TIMEOUT = int(os.environ.get("DEEPSEEK_TIMEOUT", "80"))

# 应用标识：App 内置同一个值，函数校验，挡住非官方调用；未配置则拒绝服务
EXPECTED_APP_KEY = os.environ.get("APP_KEY", "").strip()

# Supabase 用户会话鉴权；Publishable key 只用于调用 Auth 用户接口，不使用 service_role。
SUPABASE_URL = os.environ.get("SUPABASE_URL", "").strip().rstrip("/")
SUPABASE_PUBLISHABLE_KEY = os.environ.get(
    "SUPABASE_PUBLISHABLE_KEY", os.environ.get("SUPABASE_ANON_KEY", "")
).strip()
SUPABASE_AUTH_TIMEOUT = int(os.environ.get("SUPABASE_AUTH_TIMEOUT", "10"))

# 限流（单实例内存级，多实例不共享；精确全局控制需接 Redis）
RATE_PER_MINUTE = int(os.environ.get("RATE_PER_MINUTE", "20"))
RATE_PER_DAY = int(os.environ.get("RATE_PER_DAY", "200"))
# 单实例每日总配额（0 表示关闭），防止换 IP 盗刷耗尽额度
GLOBAL_RATE_PER_DAY = int(os.environ.get("GLOBAL_RATE_PER_DAY", "1000"))

# 请求体上限：App 端图片压缩到 1280px / JPEG85 后 base64 约 1~3MB，8MB 留足余量
MAX_BODY_MB = int(os.environ.get("MAX_BODY_MB", "8"))
MAX_BODY_BYTES = MAX_BODY_MB * 1024 * 1024
MAX_IMAGE_CHARS = MAX_BODY_BYTES

# 数值清洗上限，防止模型输出离谱数据
MAX_GRAMS = 5000
MAX_CALORIES = 10000

PROMPT = """
你是一名专业营养师。请识别图片中出现的每一种食物，结合常见份量估算每种食物的重量（单位：克）和该份食物的总热量（单位：千卡）。
要求：
1. 只输出一个 JSON 数组，不要输出任何解释、前后缀或 markdown 代码块。
2. 数组每个元素格式为 {"name":"食物名称","grams":估算克重,"calories":该份总热量}，数字均为整数。
3. 特殊彩蛋：若画面主体明显是核弹/导弹、燃气罐/煤气罐、汽油桶/汽油这类绝不可食用的危险物品，输出一条彩蛋 {"easterEgg":true,"name":"物品名","grams":0,"calories":0}，name 用简短中文名，且只输出这一条。
4. 除彩蛋物品外，若图片中没有可识别的食物，输出 []。
示例：[{"name":"白米饭","grams":200,"calories":232},{"name":"番茄炒蛋","grams":150,"calories":120}]
彩蛋示例：[{"easterEgg":true,"name":"燃气罐","grams":0,"calories":0}]
"""

TEXT_PROMPT = """
你是热量日记的文字饮食记录助手。请把用户输入解析为结构化 JSON 对象，不要输出解释或 markdown。
格式：
{
  "intent":"add|delete|query|stats",
  "dateOffset":0,
  "mealType":null,
  "foods":[{"name":"食物名称","grams":100,"calories":100}],
  "directCalories":null,
  "removeNames":[],
  "unmatched":[]
}
规则：
1. 新增饮食时 intent=add，foods 返回每种食物的名称、估算总克重和该份总热量（千卡）。
2. 用户明确给出克重或热量时优先使用用户数值；只有直接热量而没有食物时填 directCalories。
3. 删除记录时 intent=delete，removeNames 填要删除的食物名，不返回 foods。
4. 查询或统计时 intent=query 或 stats，不修改任何记录。
5. 今天 dateOffset=0，昨天=-1，前天=-2，明天=1；未提及时为 0。
6. 早餐 mealType=0，午餐=1，晚餐=2，加餐=3；未提及时为 null。
7. 无法识别的内容放入 unmatched。数字均为整数，食物名称使用简洁中文名。
"""


# ---------------- 上游异常分类 ----------------
class UpstreamError(Exception):
    """DeepSeek 返回了非预期的错误（HTTP 5xx / 连接失败 / 结构异常等）。"""


class UpstreamAuthError(UpstreamError):
    """DeepSeek 鉴权失败（服务端密钥错误或未配置）。"""


class UpstreamRateLimited(UpstreamError):
    """DeepSeek 触发限流（HTTP 429）。"""


class UpstreamTimeout(UpstreamError):
    """DeepSeek 请求超时。"""


class ModelOutputError(Exception):
    """模型输出无法解析为食物数组（区别于"图里没有食物"的空数组）。"""


class AuthRejected(Exception):
    """用户令牌缺失、无效或已过期。"""


class AuthUnavailable(Exception):
    """Supabase 鉴权配置缺失或服务暂不可用；必须拒绝而不是放行。"""


# ---------------- 限流（单实例内存） ----------------
class RateLimiter:
    def __init__(self, per_minute, per_day, global_per_day=0, cleanup_threshold=512):
        self.per_minute = per_minute
        self.per_day = per_day
        self.global_per_day = global_per_day
        self.cleanup_threshold = cleanup_threshold
        self.lock = threading.Lock()
        self.minute_windows = {}   # ip -> [timestamps]
        self.day_windows = {}      # ip -> [timestamps]
        self.global_day = []       # 全实例共享的当日时间戳

    @staticmethod
    def _recent(timestamps, now, window):
        return [t for t in (timestamps or []) if now - t < window]

    def _maybe_cleanup(self, now):
        """窗口字典按 IP 增长，定期清掉已过期条目，避免长期运行的内存累积。"""
        if len(self.minute_windows) + len(self.day_windows) <= self.cleanup_threshold:
            return
        self.minute_windows = {
            ip: ts for ip, ts in self.minute_windows.items()
            if self._recent(ts, now, 60)
        }
        self.day_windows = {
            ip: ts for ip, ts in self.day_windows.items()
            if self._recent(ts, now, 86400)
        }

    def check(self, ip):
        now = time.time()
        with self.lock:
            self._maybe_cleanup(now)
            minute = self._recent(self.minute_windows.get(ip), now, 60)
            day = self._recent(self.day_windows.get(ip), now, 86400)
            global_day = self._recent(self.global_day, now, 86400)

            if len(minute) >= self.per_minute:
                self.minute_windows[ip] = minute
                return False, "请求过于频繁，请稍后再试"
            if len(day) >= self.per_day:
                self.day_windows[ip] = day
                return False, "今日识别次数已达上限"
            if self.global_per_day and len(global_day) >= self.global_per_day:
                self.minute_windows[ip] = minute
                self.day_windows[ip] = day
                return False, "今日服务总次数已达上限，请明天再试"

            minute.append(now)
            day.append(now)
            global_day.append(now)
            self.minute_windows[ip] = minute
            self.day_windows[ip] = day
            self.global_day = global_day
            return True, ""


rate_limiter = RateLimiter(RATE_PER_MINUTE, RATE_PER_DAY, GLOBAL_RATE_PER_DAY)


# ---------------- 请求解析与工具 ----------------
def strip_data_uri(image_value):
    """兼容两种输入：纯 base64 或 data:image/jpeg;base64,xxx 前缀。"""
    if not isinstance(image_value, str):
        return ""
    value = image_value.strip()
    if value.startswith("data:") and "," in value:
        value = value.split(",", 1)[1]
    return value


def check_app_key(value):
    """常量时间比较；未配置 APP_KEY 时一律拒绝（fail-closed）。"""
    if not EXPECTED_APP_KEY:
        return False
    return hmac.compare_digest(str(value or ""), EXPECTED_APP_KEY)


def bearer_token(headers):
    """只提取 Authorization Bearer；空值或非法格式由鉴权层拒绝。"""
    value = headers.get("Authorization", "") if headers else ""
    if len(value) < 8 or value[:7].lower() != "bearer ":
        return ""
    return value[7:].strip()


def verify_bearer_token(token):
    """通过 Supabase Auth 获取真实用户；任何异常均不返回用户身份。"""
    if not token:
        raise AuthRejected("缺少访问令牌")
    if not SUPABASE_URL or not SUPABASE_PUBLISHABLE_KEY:
        raise AuthUnavailable("Supabase Auth 未配置")
    request = urllib.request.Request(
        SUPABASE_URL + "/auth/v1/user",
        headers={
            "Authorization": "Bearer " + token,
            "apikey": SUPABASE_PUBLISHABLE_KEY,
            "Accept": "application/json",
        },
        method="GET",
    )
    try:
        with urllib.request.urlopen(request, timeout=SUPABASE_AUTH_TIMEOUT) as resp:
            raw = resp.read().decode("utf-8")
    except urllib.error.HTTPError as err:
        if err.code in (400, 401, 403):
            raise AuthRejected("访问令牌无效") from err
        raise AuthUnavailable("Supabase Auth HTTP %s" % err.code) from err
    except (urllib.error.URLError, TimeoutError) as err:
        raise AuthUnavailable("Supabase Auth 暂不可用") from err
    try:
        data = json.loads(raw)
        user_id = data.get("id")
    except (ValueError, AttributeError) as err:
        raise AuthUnavailable("Supabase Auth 响应异常") from err
    if not isinstance(user_id, str) or not user_id.strip():
        raise AuthUnavailable("Supabase Auth 缺少用户 ID")
    return user_id


def user_log_id(user_id):
    """日志仅保留不可逆的用户标识散列，不记录令牌。"""
    return hashlib.sha256(user_id.encode("utf-8")).hexdigest()[:12]


def _to_int(value):
    """把 200 / 150.6 / "200g" 之类的模型输出清洗成整数；无法解析返回 None。"""
    if isinstance(value, bool):
        return None
    if isinstance(value, int):
        return value
    if isinstance(value, float):
        return int(value)
    if isinstance(value, str):
        match = re.search(r"\d+", value)
        return int(match.group()) if match else None
    return None


def parse_foods(content):
    """从模型返回文本里提取 JSON 数组；单个坏元素跳过，整体 JSON 非法则抛错。"""
    start = content.find("[")
    end = content.rfind("]")
    if start < 0 or end <= start:
        return []
    try:
        arr = json.loads(content[start:end + 1])
    except ValueError as err:
        raise ModelOutputError("模型输出不是合法 JSON") from err
    if not isinstance(arr, list):
        raise ModelOutputError("模型输出不是数组")

    foods = []
    for item in arr:
        if not isinstance(item, dict):
            continue
        name = str(item.get("name", "")).strip()[:40]
        if not name:
            continue
        # 彩蛋物品（核弹/燃气罐/汽油等）只是趣味提示，数值一律归零，由 App 端特殊展示
        if item.get("easterEgg") is True:
            foods.append({
                "name": name,
                "grams": 0,
                "calories": 0,
                "easterEgg": True,
            })
            continue
        grams = _to_int(item.get("grams"))
        calories = _to_int(item.get("calories"))
        if grams is None or calories is None:
            continue
        foods.append({
            "name": name,
            "grams": min(max(grams, 0), MAX_GRAMS),
            "calories": min(max(calories, 0), MAX_CALORIES),
        })
    return foods


def parse_text_result(content):
    """清洗文字记录模型输出，确保 App 只接收固定结构和受控数值。"""
    start = content.find("{")
    end = content.rfind("}")
    if start < 0 or end <= start:
        raise ModelOutputError("文字解析结果不是 JSON 对象")
    try:
        data = json.loads(content[start:end + 1])
    except ValueError as err:
        raise ModelOutputError("文字解析 JSON 非法") from err
    if not isinstance(data, dict):
        raise ModelOutputError("文字解析结果不是对象")
    intent = str(data.get("intent", "add")).strip().lower()
    if intent not in ("add", "delete", "query", "stats"):
        intent = "unknown"
    date_offset = _to_int(data.get("dateOffset"))
    meal_type = _to_int(data.get("mealType"))
    direct_calories = _to_int(data.get("directCalories"))
    foods_raw = data.get("foods", [])
    foods = parse_foods(json.dumps(foods_raw, ensure_ascii=False)) if isinstance(foods_raw, list) else []
    return {
        "intent": intent,
        "dateOffset": min(max(date_offset if date_offset is not None else 0, -30), 30),
        "mealType": meal_type if meal_type in (0, 1, 2, 3) else None,
        "foods": foods,
        "directCalories": min(max(direct_calories, 0), MAX_CALORIES)
            if direct_calories is not None else None,
        "removeNames": [str(v).strip()[:40] for v in data.get("removeNames", [])
                        if isinstance(v, str) and v.strip()] if isinstance(data.get("removeNames"), list) else [],
        "unmatched": [str(v).strip()[:40] for v in data.get("unmatched", [])
                      if isinstance(v, str) and v.strip()] if isinstance(data.get("unmatched"), list) else [],
    }


def call_deepseek(image_b64):
    """组装 DeepSeek 请求并调用，返回食物列表。异常按类型抛出供上层映射 HTTP 状态。"""
    api_key = os.environ.get("DEEPSEEK_API_KEY", "").strip()
    if not api_key:
        raise UpstreamAuthError("服务端未配置 DEEPSEEK_API_KEY")

    content = [
        {"type": "text", "text": PROMPT.strip()},
        {"type": "image_url", "image_url": {
            "url": "data:image/jpeg;base64," + image_b64,
            "detail": "high",
        }},
    ]
    payload = {
        "model": DEEPSEEK_MODEL,
        "messages": [{"role": "user", "content": content}],
        "temperature": 0.2,
    }
    request = urllib.request.Request(
        DEEPSEEK_ENDPOINT,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": "Bearer " + api_key,
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=DEEPSEEK_TIMEOUT) as resp:
            raw = resp.read().decode("utf-8")
    except urllib.error.HTTPError as err:
        body = err.read().decode("utf-8", "ignore")
        if err.code in (401, 403):
            raise UpstreamAuthError("DeepSeek 鉴权失败 HTTP %s" % err.code) from err
        if err.code == 429:
            raise UpstreamRateLimited("DeepSeek 限流 HTTP 429") from err
        raise UpstreamError("DeepSeek HTTP %s: %s" % (err.code, body[:200])) from err
    except urllib.error.URLError as err:
        reason = getattr(err, "reason", err)
        if isinstance(reason, TimeoutError) or "timed out" in str(reason).lower():
            raise UpstreamTimeout("DeepSeek 请求超时") from err
        raise UpstreamError("无法连接 DeepSeek: %s" % reason) from err
    except TimeoutError as err:
        raise UpstreamTimeout("DeepSeek 请求超时") from err

    try:
        data = json.loads(raw)
        text = data["choices"][0]["message"]["content"]
    except (ValueError, KeyError, IndexError, TypeError) as err:
        raise ModelOutputError("DeepSeek 返回结构异常") from err
    return parse_foods(text)


def call_deepseek_text(text):
    """使用与图片识别相同的云端密钥和超时配置解析文字记录。"""
    api_key = os.environ.get("DEEPSEEK_API_KEY", "").strip()
    if not api_key:
        raise UpstreamAuthError("服务端未配置 DEEPSEEK_API_KEY")
    payload = {
        "model": DEEPSEEK_MODEL,
        "messages": [
            {"role": "system", "content": TEXT_PROMPT.strip()},
            {"role": "user", "content": text},
        ],
        "temperature": 0.1,
    }
    request = urllib.request.Request(
        DEEPSEEK_ENDPOINT,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={
            "Authorization": "Bearer " + api_key,
            "Content-Type": "application/json; charset=utf-8",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=DEEPSEEK_TIMEOUT) as resp:
            raw = resp.read().decode("utf-8")
    except urllib.error.HTTPError as err:
        body = err.read().decode("utf-8", "ignore")
        if err.code in (401, 403):
            raise UpstreamAuthError("DeepSeek 鉴权失败 HTTP %s" % err.code) from err
        if err.code == 429:
            raise UpstreamRateLimited("DeepSeek 限流 HTTP 429") from err
        raise UpstreamError("DeepSeek HTTP %s: %s" % (err.code, body[:200])) from err
    except urllib.error.URLError as err:
        reason = getattr(err, "reason", err)
        if isinstance(reason, TimeoutError) or "timed out" in str(reason).lower():
            raise UpstreamTimeout("DeepSeek 请求超时") from err
        raise UpstreamError("无法连接 DeepSeek: %s" % reason) from err
    except TimeoutError as err:
        raise UpstreamTimeout("DeepSeek 请求超时") from err
    try:
        data = json.loads(raw)
        text_result = data["choices"][0]["message"]["content"]
    except (ValueError, KeyError, IndexError, TypeError) as err:
        raise ModelOutputError("DeepSeek 返回结构异常") from err
    return parse_text_result(text_result)


# ---------------- HTTP 服务 ----------------
class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"   # 支持 keep-alive（响应均带 Content-Length）
    timeout = 30                    # 单连接读写超时，防止慢请求挂死线程
    server_version = "CalorieDiaryProxy/1.0"
    sys_version = ""

    # ---- 基础工具 ----
    def _path(self):
        return self.path.split("?", 1)[0].rstrip("/") or "/"

    def _client_ip(self):
        """FC 网关后面 client_address 常是网关 IP，优先取转发头里的真实地址。"""
        forwarded = self.headers.get("X-Forwarded-For", "")
        if forwarded:
            return forwarded.split(",")[0].strip()
        real_ip = self.headers.get("X-Real-IP", "").strip()
        if real_ip:
            return real_ip
        return self.client_address[0]

    def _read_body(self, length):
        chunks = []
        remaining = length
        while remaining > 0:
            chunk = self.rfile.read(min(remaining, 64 * 1024))
            if not chunk:
                break
            chunks.append(chunk)
            remaining -= len(chunk)
        return b"".join(chunks)

    def _send(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Request-Id", getattr(self, "request_id", "-"))
        self.end_headers()
        self.wfile.write(body)

    def _error(self, code, error, message):
        self._send(code, {"error": error, "message": message})

    # ---- 路由 ----
    def do_GET(self):
        # 健康检查（平台就绪探针会调用），不暴露密钥，只报告是否已配置
        if self._path() in ("/", "/health"):
            self._send(200, {
                "ok": True,
                "service": "calorie-diary-proxy",
                "version": "1.0",
                "authConfigured": bool(EXPECTED_APP_KEY),
                "supabaseConfigured": bool(SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY),
                "deepseekConfigured": bool(os.environ.get("DEEPSEEK_API_KEY", "").strip()),
            })
        else:
            self._error(404, "not_found", "接口不存在")

    def do_POST(self):
        self.request_id = uuid.uuid4().hex[:12]
        started = time.time()

        if self._path() not in ("/recognize", "/parse-food"):
            self._error(404, "not_found", "接口不存在")
            return

        # 鉴权配置缺失时拒绝服务（fail-closed），但保留健康检查可用
        if not EXPECTED_APP_KEY:
            print("[%s] APP_KEY 未配置，拒绝识别请求" % self.request_id, flush=True)
            self._error(503, "not_configured", "服务端未完成配置")
            return

        # 先验证 Supabase 用户会话；未授权请求不读取图片、不调用模型。
        token = bearer_token(self.headers)
        try:
            user_id = verify_bearer_token(token)
        except AuthRejected:
            self._error(401, "unauthorized", "请先登录后再使用识别")
            return
        except AuthUnavailable:
            self._error(503, "auth_unavailable", "登录验证暂不可用，请稍后重试")
            return

        # 请求体大小校验：先看头部，再读受限长度，防止大包打爆内存
        length_header = self.headers.get("Content-Length")
        if length_header is None:
            self._error(411, "length_required", "缺少 Content-Length")
            return
        try:
            length = int(length_header)
        except ValueError:
            self._error(400, "bad_request", "Content-Length 非法")
            return
        if length <= 0:
            self._error(400, "bad_request", "请求体为空")
            return
        if length > MAX_BODY_BYTES:
            self._error(413, "payload_too_large", "请求体超过 %sMB 上限" % MAX_BODY_MB)
            return

        raw = self._read_body(length)
        try:
            payload = json.loads(raw.decode("utf-8") or "{}")
        except (ValueError, UnicodeDecodeError):
            self._error(400, "bad_request", "请求体不是合法 JSON")
            return
        if not isinstance(payload, dict):
            self._error(400, "bad_request", "请求体不是 JSON 对象")
            return

        if not check_app_key(payload.get("appKey")):
            print("[%s] ip=%s appKey 校验失败" % (self.request_id, self._client_ip()), flush=True)
            self._error(403, "forbidden", "非法调用")
            return

        text_input = ""
        image_b64 = ""
        if self._path() == "/parse-food":
            text_input = str(payload.get("text", "")).strip()
            if not text_input:
                self._error(400, "missing_text", "缺少文字内容")
                return
            if len(text_input) > 500:
                self._error(413, "text_too_large", "文字内容超过 500 字上限")
                return
        else:
            image_b64 = strip_data_uri(payload.get("image"))
            if not image_b64:
                self._error(400, "missing_image", "缺少图片数据")
                return
            if len(image_b64) > MAX_IMAGE_CHARS:
                self._error(413, "payload_too_large", "图片数据超过上限")
                return

        # 校验通过后再扣限流额度，畸形请求不消耗用户配额
        ok, msg = rate_limiter.check(user_id)
        if not ok:
            print("[%s] ip=%s 触发限流: %s" % (self.request_id, self._client_ip(), msg), flush=True)
            self._error(429, "rate_limited", msg)
            return

        try:
            if self._path() == "/parse-food":
                text_result = call_deepseek_text(text_input)
            else:
                foods = call_deepseek(image_b64)
        except UpstreamAuthError as err:
            print("[%s] 上游鉴权失败: %s" % (self.request_id, err), flush=True)
            self._error(502, "upstream_auth", "云端识别服务配置异常，请稍后再试")
            return
        except UpstreamRateLimited as err:
            print("[%s] 上游限流: %s" % (self.request_id, err), flush=True)
            self._error(429, "upstream_rate_limited", "识别服务繁忙，请稍后再试")
            return
        except UpstreamTimeout as err:
            print("[%s] 上游超时: %s" % (self.request_id, err), flush=True)
            self._error(504, "upstream_timeout", "识别超时，请稍后再试")
            return
        except ModelOutputError as err:
            print("[%s] 模型输出异常: %s" % (self.request_id, err), flush=True)
            self._error(502, "bad_model_output", "识别结果解析失败，请重试")
            return
        except UpstreamError as err:
            print("[%s] 上游错误: %s" % (self.request_id, err), flush=True)
            self._error(502, "upstream_error", "识别服务暂不可用，请稍后再试")
            return
        except Exception as err:  # noqa: BLE001 - 兜底防止线程崩溃
            print("[%s] 未捕获异常: %r" % (self.request_id, err), flush=True)
            self._error(500, "internal_error", "服务内部错误，请稍后再试")
            return

        cost = int((time.time() - started) * 1000)
        if self._path() == "/parse-food":
            print("[%s] ip=%s user=%s ok text cost=%dms" % (
                self.request_id, self._client_ip(), user_log_id(user_id), cost), flush=True)
            self._send(200, text_result)
            return
        print("[%s] ip=%s user=%s ok foods=%d cost=%dms" % (
            self.request_id, self._client_ip(), user_log_id(user_id), len(foods), cost), flush=True)
        self._send(200, {"foods": foods})

    def log_message(self, fmt, *args):
        # 输出到函数日志，便于在控制台排查
        print("[%s] %s - %s" % (
            getattr(self, "request_id", "-"),
            self.address_string(),
            fmt % args,
        ), flush=True)


def main():
    # 阿里云自定义运行时通过 PORT 环境变量告知监听端口（默认 9000）
    port = int(os.environ.get("PORT", "9000"))
    print("calorie proxy listening on 0.0.0.0:%s" % port, flush=True)
    if not EXPECTED_APP_KEY:
        print("WARN: 未配置 APP_KEY，/recognize 将一律返回 503（健康检查不受影响）", flush=True)
    if not os.environ.get("DEEPSEEK_API_KEY", "").strip():
        print("WARN: 未配置 DEEPSEEK_API_KEY，识别请求将返回 502 upstream_auth", flush=True)
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    server.serve_forever()


if __name__ == "__main__":
    main()
