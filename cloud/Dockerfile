# 热量日记 - 识别代理云函数（自定义容器镜像）
# 本地验证：
#   docker build -t calorie-proxy .
#   docker run --rm -p 9000:9000 -e APP_KEY=test -e DEEPSEEK_API_KEY=sk-xxx calorie-proxy
# 阿里云 FC：将镜像推送到 ACR，函数选择「容器镜像」方式，监听端口 9000，超时 90s。

FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PORT=9000

WORKDIR /app

# 仅拷贝运行所需文件，避免把测试与文档打进镜像
COPY app.py ./

# 以非 root 运行
RUN useradd --create-home --uid 10001 appuser
USER appuser

EXPOSE 9000

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
    CMD python3 -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:9000/health', timeout=2)" || exit 1

CMD ["python3", "app.py"]
