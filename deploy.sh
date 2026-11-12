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

# ============================================================
# 短信凭证（仓库不保存明文，部署时通过环境变量注入）：
#   SMS_ALIYUN_AK_ID='xxx' SMS_ALIYUN_AK_SECRET='xxx' \
#   SMS_CALLBACK_TOKEN='xxx' ./deploy.sh
# 值含 # @ 等字符，必须用单引号包裹；AK/SK 为空时短信总开关自动关闭
# ============================================================
SMS_ALIYUN_AK_ID="${SMS_ALIYUN_AK_ID:-}"
SMS_ALIYUN_AK_SECRET="${SMS_ALIYUN_AK_SECRET:-}"
SMS_CALLBACK_TOKEN="${SMS_CALLBACK_TOKEN:-}"
# 接收人非敏感信息；联调期告警与日汇总均只发测试号，恢复生产接收人：注释掉下面两行再重跑
SMS_RECEIVERS_ALARM="${SMS_RECEIVERS_ALARM:-15212773620}"
SMS_RECEIVERS_DAILY="${SMS_RECEIVERS_DAILY:-15212773620}"

case "${1:-deploy}" in
    deploy)
        docker stop ${CONTAINER_NAME} 2>/dev/null || true
        docker rm ${CONTAINER_NAME} 2>/dev/null || true
        docker build -t ${IMAGE_NAME}:latest "$PROJECT_DIR"
        mkdir -p /service/hltgq/logs/hltgq-site
        mkdir -p /service/hltgq/hltgq-site/decision-excel
        mkdir -p /service/hltgq/hltgq-site/file-dir
        # 可选：平台登录账号/密码密文覆盖（不设置则用镜像内 application.properties 默认值）
        EXTRA_ENV=()
        if [ -n "${AUTH_LOGIN_USERNAME:-}" ]; then
            EXTRA_ENV+=(-e "AUTH_LOGIN_USERNAME=${AUTH_LOGIN_USERNAME}")
        fi
        if [ -n "${AUTH_LOGIN_PASSWORD:-}" ]; then
            EXTRA_ENV+=(-e "AUTH_LOGIN_PASSWORD=${AUTH_LOGIN_PASSWORD}")
        fi
        # 可选：/auth/login 及档案同步接口的调用密钥覆盖（不设置则用镜像内默认值）
        if [ -n "${AUTH_API_KEY:-}" ]; then
            EXTRA_ENV+=(-e "AUTH_API_KEY=${AUTH_API_KEY}")
        fi
        if [ -n "${AUTH_API_SECRET:-}" ]; then
            EXTRA_ENV+=(-e "AUTH_API_SECRET=${AUTH_API_SECRET}")
        fi
        # 短信（凭证通过环境变量注入，见文件顶部说明）
        if [ -n "${SMS_ALIYUN_AK_ID:-}" ]; then
            EXTRA_ENV+=(-e "SMS_ALIYUN_AK_ID=${SMS_ALIYUN_AK_ID}")
        fi
        if [ -n "${SMS_ALIYUN_AK_SECRET:-}" ]; then
            EXTRA_ENV+=(-e "SMS_ALIYUN_AK_SECRET=${SMS_ALIYUN_AK_SECRET}")
        fi
        if [ -n "${SMS_CALLBACK_TOKEN:-}" ]; then
            EXTRA_ENV+=(-e "SMS_CALLBACK_TOKEN=${SMS_CALLBACK_TOKEN}")
        fi
        # 接收人：顶部默认值已兜底（恒注入）；恢复镜像内默认清单需注释掉顶部对应行
        if [ -n "${SMS_RECEIVERS_ALARM:-}" ]; then
            EXTRA_ENV+=(-e "SMS_RECEIVERS_ALARM=${SMS_RECEIVERS_ALARM}")
        fi
        if [ -n "${SMS_RECEIVERS_DAILY:-}" ]; then
            EXTRA_ENV+=(-e "SMS_RECEIVERS_DAILY=${SMS_RECEIVERS_DAILY}")
        fi
        # 短信总开关：AK 未注入时默认关闭（避免落一堆 NOT_CONFIGURED 失败记录）
        SMS_ENABLED_DEFAULT=true
        if [ -z "${SMS_ALIYUN_AK_ID:-}" ] || [ -z "${SMS_ALIYUN_AK_SECRET:-}" ]; then
            SMS_ENABLED_DEFAULT=false
            echo "⚠ 未配置短信凭证（AK/SK 为空），本次部署 SMS_ENABLED=false"
        fi
        # -v /:/host:ro → 系统资源监控采集宿主机视角（/proc、磁盘挂载点），只读挂载
        # SYSTEM_MONITOR_DB_CAPACITY_GB → 表空间使用率分母（数据库服务器数据盘 /service 分区 7.7TiB）
        # SYSTEM_MONITOR_REPORT_TOKEN → 各服务器采集脚本上报凭证（须与各机 sysmon-collect.sh 内一致）
        # SYSTEM_MONITOR_REPORT_ALLOW_CIDR → 上报来源 IP 前缀白名单（默认仅内网段）
        docker run -d \
            --name ${CONTAINER_NAME} \
            --restart unless-stopped \
            -p 18687:8080 \
            -v /service/hltgq/logs/hltgq-site:/app/logs \
            -v /service/hltgq/hltgq-site/decision-excel:/app/decision-excel \
            -v /service/hltgq/hltgq-site/file-dir:/app/file-dir \
            -v /:/host:ro \
            -e TZ=Asia/Shanghai \
            -e MODEL_BASE_URL="${MODEL_BASE_URL:-http://10.68.18.11:8000}" \
            -e ARCHIVE_BASE_URL="${ARCHIVE_BASE_URL:-http://10.68.18.12:8090}" \
            -e SYSTEM_MONITOR_DB_CAPACITY_GB="${SYSTEM_MONITOR_DB_CAPACITY_GB:-7885}" \
            -e SYSTEM_MONITOR_REPORT_TOKEN="${SYSTEM_MONITOR_REPORT_TOKEN:-hltgq-sysmon-2026}" \
            -e SYSTEM_MONITOR_REPORT_ALLOW_CIDR="${SYSTEM_MONITOR_REPORT_ALLOW_CIDR:-10.68.18.}" \
            -e SMS_ENABLED="${SMS_ENABLED:-$SMS_ENABLED_DEFAULT}" \
            "${EXTRA_ENV[@]}" \
            --memory="1024m" \
            ${IMAGE_NAME}:latest
        echo "部署完成，查看日志（提醒：短信记录表需已执行 sms-record-ddl.sql，否则无记录/幂等/静默）："
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
