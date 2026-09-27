#!/bin/sh
# =============================================================================
# 花凉亭灌区信息化管理平台 · 服务器资源采集上报脚本
# 配合 hltgq-site 系统资源监控页（POST /system-monitor/report 接口）使用。
# 脚本只做“读取原始数据并转发”，解析/计算（CPU 差分、内存/磁盘换算）全部在平台侧完成。
#
# 部署方法（在每台被监控服务器上，以 root 执行）：
#   1) 上传本脚本至 /service/hltgq-collect/sysmon-collect.sh
#      （仅部署于平台之外的被监控服务器；hltgq-site 所在机器由平台实时采集，无需脚本）
#   2) 替换下方两个占位符：TOKEN（与平台 deploy.sh 中 SYSTEM_MONITOR_REPORT_TOKEN 一致）、
#      ALIAS（该服务器在页面上的显示名，如“中间件服务器”；留空则用主机名）：
#        sed -i 's/__REPORT_TOKEN__/实际token/; s/__HOST_ALIAS__/实际别名/' /service/hltgq-collect/sysmon-collect.sh
#   3) 添加 crontab（每分钟上报一次；用 sh 调用，无需执行位）：
#        ( crontab -l 2>/dev/null; echo '* * * * * sh /service/hltgq-collect/sysmon-collect.sh >/dev/null 2>&1' ) | crontab -
#   4) 手动执行一次验证，应输出 {"ok":true,"hostname":"..."}：
#        sh /service/hltgq-collect/sysmon-collect.sh
# =============================================================================
# 上报地址（内网直连应用端口）：不带 /hltgq-site 前缀——该前缀仅外网 Nginx 入口使用，
# 由 Nginx 转发时剥离；带前缀直连会落到静态资源兜底被鉴权拦截（401）
URL="http://10.68.18.4:18687/system-monitor/report"
TOKEN="__REPORT_TOKEN__"
ALIAS="__HOST_ALIAS__"

{
  echo "###ALIAS###";     echo "$ALIAS"
  echo "###HOSTNAME###";  hostname
  echo "###OSRELEASE###"; cat /etc/os-release 2>/dev/null
  echo "###LOADAVG###";   cat /proc/loadavg 2>/dev/null
  echo "###STAT###";      cat /proc/stat 2>/dev/null
  sleep 1
  echo "###STAT2###";     cat /proc/stat 2>/dev/null
  echo "###MEMINFO###";  cat /proc/meminfo 2>/dev/null
  # df 选项必须分开写：-B1TP 会被 GNU df 当作 -B 的参数 "1TP" 而报错（2>/dev/null 吞错会导致 DF 段为空）
  echo "###DF###";        df -B1 -T -P 2>/dev/null
} | curl -sS -m 15 -X POST \
    -H "Content-Type: text/plain; charset=utf-8" \
    -H "X-Sysmon-Token: $TOKEN" \
    --data-binary @- "$URL"
