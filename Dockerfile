# ============================================================
# hltgq-site Docker 镜像（ARM64 / 银河麒麟）
# ============================================================
# 构建方式（在 ARM64 服务器上执行）：
#   1. 将 hltgq-site-0.0.1-SNAPSHOT.jar 放到此目录
#   2. docker build -t hltgq-site:latest .
#   3. docker run -d -p 18687:8080 --name hltgq-site hltgq-site:latest
# ============================================================

FROM eclipse-temurin:8-jre-jammy

LABEL maintainer="hltgq-site"
LABEL description="HLTGQ 数据服务平台"

# ----- 安装 curl（便于容器内排障与健康检查）-----
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# ----- 设置时区 -----
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# ----- 应用目录 -----
WORKDIR /app

# ----- 复制 JAR（在服务器端 docker build 前把 JAR 放到同目录）-----
COPY hltgq-site-0.0.1-SNAPSHOT.jar /app/app.jar

# ----- 暴露端口 -----
EXPOSE 8080

# ----- 启动命令 -----
# JVM 参数说明：
#   -XX:+UseContainerSupport  容器内存感知
#   -XX:MaxRAMPercentage=75   堆内存上限=容器内存的75%
#   -Dfile.encoding=UTF-8     文件编码
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Dfile.encoding=UTF-8", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/app.jar"]
