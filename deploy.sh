#!/bin/bash
# ============================================================
# hltgq-site 部署脚本（银河麒麟 ARM64 服务器上执行）
# 用法：
#   ./deploy.sh            → 完整部署（停旧→构建→启动→跟踪日志）
#   ./deploy.sh restart    → 重启容器并跟踪日志
#   ./deploy.sh logs       → 查看实时日志
# ============================================================
set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
IMAGE_NAME="hltgq-site"
CONTAINER_NAME="hltgq-site"

case "${1:-deploy}" in
    deploy)
        docker stop ${CONTAINER_NAME} 2>/dev/null || true
        docker rm ${CONTAINER_NAME} 2>/dev/null || true
        docker build -t ${IMAGE_NAME}:latest "$PROJECT_DIR"
        mkdir -p /service/hltgq/logs/hltgq-site
        mkdir -p /service/hltgq/hltgq-site/decision-excel
        docker run -d \
            --name ${CONTAINER_NAME} \
            --restart unless-stopped \
            -p 18687:8080 \
            -v /service/hltgq/logs/hltgq-site:/app/logs \
            -v /service/hltgq/hltgq-site/decision-excel:/app/decision-excel \
            -e TZ=Asia/Shanghai \
            -e MODEL_BASE_URL="${MODEL_BASE_URL:-http://10.68.18.11:8000}" \
            --memory="1024m" \
            ${IMAGE_NAME}:latest
        echo "部署完成，查看日志："
        docker logs -f ${CONTAINER_NAME}
        ;;
    restart)
        docker restart ${CONTAINER_NAME}
        docker logs -f ${CONTAINER_NAME}
        ;;
    logs)
        docker logs -f ${CONTAINER_NAME}
        ;;
    *)
        echo "用法: $0 {deploy|restart|logs}"
        ;;
esac
