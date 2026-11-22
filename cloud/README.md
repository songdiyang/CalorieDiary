# 热量日记 · 云端识别代理

把 DeepSeek 密钥从 App 移到服务端，App 只上传图片、拿回食物列表。
零第三方依赖（纯 Python 标准库），可跑在阿里云函数计算 FC、任意容器、任意 VPS。

```
App ──POST /recognize {appKey, image(base64)} + Bearer 用户令牌──▶ 云函数
云函数 ──GET /auth/v1/user──▶ Supabase Auth（验证令牌并返回用户 ID）
云函数 ──Bearer 密钥──▶ DeepSeek
App ◀──── {"foods":[{name,grams,calories,easterEgg?}]} ──── 云函数 ◀── choices 文本 ──── DeepSeek
```

支持两个登录鉴权接口：
- `/recognize`：图片识别食物。
- `/parse-food`：AI 解析文字中的食物、克重、热量、餐次、日期和记录意图；请求格式为 `{appKey, text}`。

## 彩蛋识别（/recognize）

画面主体明显是核弹/导弹、燃气罐/煤气罐、汽油桶/汽油这类绝不可食用的危险物品时，模型返回一条彩蛋：

```json
{"foods":[{"name":"核弹","grams":0,"calories":0,"easterEgg":true}]}
```

- `grams` / `calories` 恒为 0，`easterEgg` 只以 `true` 出现在彩蛋条目上；
- App 端展示「∞ 千卡」警报卡片，不写入饮食记录；
- 其他非食物仍返回空数组（App 提示「没有识别出食物」）。

> 修改提示词后需重新部署函数（`s deploy --skip-push` 或控制台上传 `app.py`）才会生效。

## 目录说明

| 文件 | 作用 |
|---|---|
| `app.py` | 云函数本体（HTTP 服务，监听 `PORT`，默认 9000） |
| `test_app.py` | 核心逻辑单测：`python3 -m unittest test_app -v` |
| `Dockerfile` | 容器镜像方式部署（本地也可 docker run 验证） |
| `s.yaml` | Serverless Devs 一键部署配置（FC 3.0 自定义运行时） |
| `.gitignore` | 忽略缓存与本地部署状态 |

## 环境变量

| 变量 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `DEEPSEEK_API_KEY` | 是 | - | DeepSeek 密钥，只存服务端 |
| `APP_KEY` | 是 | - | App 内置的应用标识；**未配置时 /recognize 一律返回 503（fail-closed）** |
| `SUPABASE_URL` | 是 | - | Supabase 项目 URL，用于验证用户令牌 |
| `SUPABASE_PUBLISHABLE_KEY` | 是 | - | Supabase Publishable key；兼容变量名 `SUPABASE_ANON_KEY`，禁止使用 service_role |
| `SUPABASE_AUTH_TIMEOUT` | 否 | `10` | Supabase Auth 用户验证超时（秒） |
| `DEEPSEEK_MODEL` | 否 | `deepseek-flash` | 模型名，需与账号可用模型一致 |
| `DEEPSEEK_TIMEOUT` | 否 | `80` | 上游请求超时（秒），应小于函数超时 |
| `RATE_PER_MINUTE` | 否 | `20` | 单用户每分钟上限 |
| `RATE_PER_DAY` | 否 | `200` | 单用户每天上限 |
| `GLOBAL_RATE_PER_DAY` | 否 | `1000` | 单实例每天总配额（0 = 关闭），防换 IP 盗刷 |
| `MAX_BODY_MB` | 否 | `8` | 请求体上限；App 端 1280px JPEG85 后约 1~3MB |
| `PORT` | 否 | `9000` | 监听端口（FC 自定义运行时用 9000） |

生成一个随机 APP_KEY：

```bash
python -c "import secrets; print(secrets.token_hex(16))"
```

## 部署方式 A：控制台手动（最直观）

1. 阿里云函数计算 → 创建函数 → 运行环境选 **自定义运行时 (Debian10)**
2. 代码：上传本目录的 `app.py`（单文件即可）
3. 启动命令：`python3 app.py`；监听端口：`9000`
4. 规格：内存 `512MB`，函数超时 `90s`，实例并发 `10`
5. 环境变量：填上表里的必填项（`DEEPSEEK_API_KEY`、`APP_KEY`）
6. 触发器：创建 **HTTP 触发器**，认证方式选 `anonymous`（鉴权由 APP_KEY 负责）
7. 复制函数 URL（形如 `https://xxxx.cn-hangzhou.fcapp.run`），填到 App 配置里

## 部署方式 B：Serverless Devs（可重复部署）

```bash
cd cloud
npm install -g @serverless-devs/s
s config add                      # 配置阿里云 AccessKey（仅存本机）

export DEEPSEEK_API_KEY=sk-xxx
export APP_KEY=$(python -c "import secrets; print(secrets.token_hex(16))")
export SUPABASE_URL=https://your-project.supabase.co
export SUPABASE_PUBLISHABLE_KEY=sb_publishable_xxx

s deploy                          # 创建函数 + HTTP 触发器
```

之后改代码只需 `s deploy --skip-push`。`s deploy` 输出里的 `urlInternet` 即函数地址。
如所在区域/账号不同，调整 `s.yaml` 中的 `region` 与 `functionName`。

## 部署方式 C：自定义容器（自有服务器 / FC 容器镜像）

```bash
cd cloud
docker build -t calorie-proxy .
docker run --rm -p 9000:9000 \
  -e APP_KEY=test-app-key \
  -e DEEPSEEK_API_KEY=sk-xxx \
  calorie-proxy
```

推送 ACR 后在 FC 选择「容器镜像」创建函数，端口 `9000`、超时 `90s`。

## 本地冒烟测试

```bash
cd cloud
export APP_KEY=test-app-key DEEPSEEK_API_KEY=sk-xxx
python3 app.py
```

```bash
# 健康检查（不返回密钥，仅报告是否配置）
curl http://127.0.0.1:9000/health
# {"ok": true, "service": "calorie-diary-proxy", "authConfigured": true, "deepseekConfigured": true, ...}

# 识别（image 为 JPEG 的 base64；示例用 1x1 图片，真实场景需替换）
IMG=$(python -c "import base64;print(base64.b64encode(open('food.jpg','rb').read()).decode())") 
curl -X POST http://127.0.0.1:9000/recognize \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <Supabase access token>" \
  -d "{\"appKey\":\"test-app-key\",\"image\":\"$IMG\"}"
# {"foods": [{"name": "白米饭", "grams": 200, "calories": 232}]}

# 单元测试
python3 -m unittest test_app -v
```

## 错误码表（与 App 端映射）

| HTTP | `error` | 含义 | App 端表现 |
|---|---|---|---|
| 200 | - | 成功，`foods` 可为空数组或含彩蛋条目（`easterEgg:true`） | 正常展示 / 「没识别到食物」/ 彩蛋警报卡片 |
| 400 | `bad_request` / `missing_image` | 请求体非法 / 缺图片 | 「识别失败，请重试」 |
| 401 | `unauthorized` | 用户令牌缺失、无效或过期 | 清除失效会话并返回登录页 |
| 403 | `forbidden` | appKey 不匹配 | 「云端识别服务暂不可用」 |
| 413 | `payload_too_large` | 请求体/图片超限 | 通用失败提示（App 压缩后正常不会触发） |
| 429 | `rate_limited` / `upstream_rate_limited` | 本代理限流 / DeepSeek 限流 | 「请求太频繁，请稍后再试」 |
| 502 | `upstream_auth` | 服务端 DeepSeek 密钥错误 | 「云端识别服务暂不可用」（提示运维问题） |
| 502 | `bad_model_output` | 模型输出无法解析 | 「识别结果异常，请重试」 |
| 502 | `upstream_error` | DeepSeek 5xx / 连接失败 | 「服务繁忙，请稍后再试」（可重试） |
| 503 | `not_configured` | APP_KEY 未配置（拒绝服务） | 「云端识别服务暂不可用」 |
| 503 | `auth_unavailable` | Supabase 鉴权配置缺失或服务异常 | 「识别服务繁忙，请稍后再试」 |
| 504 | `upstream_timeout` | 上游超时 | 「识别超时，请稍后再试」（可重试） |
| 500 | `internal_error` | 未捕获异常 | 「服务繁忙」 |

## 接入 App

在项目根目录 `local.properties` 中配置（该文件不入库）：

```properties
vision.proxyUrl=https://xxxx.cn-hangzhou.fcapp.run
vision.appKey=与云端 APP_KEY 完全一致
```

构建优先级：`local.properties` > `-Pvision.proxyUrl=...` > 环境变量 `VISION_PROXY_URL` / `VISION_APP_KEY`。

PowerShell 下用 `-P` 传参时需要加引号，否则 `-Pvision` 前缀会被吞掉：

```powershell
.\gradlew.bat :app:assembleDebug "-Pvision.proxyUrl=https://xxxx.cn-hangzhou.fcapp.run" "-Pvision.appKey=你的APP_KEY"
```

- App 已移除本地密钥：识别一律经由云端代理，DeepSeek 密钥只存在服务端
- 未配置代理地址时，识别页提示「云端识别服务暂时不可用」，不会发出请求

改完配置后 Sync + 重新打包即可（`buildConfigField` 在构建期注入）。

## 超时链路

```
App OkHttp read 100s  >  FC 函数超时 90s  >  上游 urlopen 80s
```

保证客户端总是最后放弃，用户拿到的是可读的错误码而不是连接被掐断。

## 安全与限制

- `APP_KEY` 内置在 APK 中，可被逆向提取，仅作为二级应用标识；真正的用户鉴权由 Supabase Bearer Token 完成
- 识别请求必须通过 Supabase `/auth/v1/user` 验证；鉴权失败、配置缺失或鉴权超时均 Fail-Closed，不调用 DeepSeek
- 单实例内存限流按 Supabase 用户 ID 计数：多实例不共享计数、冷启动后计数归零；要精确全局限流需接 Redis
- 函数 URL 建议只通过 HTTPS 访问（FC 默认提供），避免图片、用户令牌与 appKey 明文传输
- 建议在阿里云账单侧设置预算告警，防止密钥泄露导致额度被刷
- 日志会记录客户端 IP / 用户 ID 散列 / 耗时 / 错误类型，不记录令牌、密钥与图片内容
