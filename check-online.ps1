$ErrorActionPreference = 'Continue'
$base = 'http://220.179.1.110:8081/hltgq-site'
$session = 'dev_hltgq_session:jgnB8MSdYvJo6V310oQ'
$headers = @{ 'Authorization' = $session }
$log = 'D:\Java\code\hltgq\hltgq-site\check-online.log'
"stations:" | Out-File -Encoding UTF8 $log
try {
  $r = Invoke-WebRequest -Uri "$base/flood-drought/stations" -Headers $headers -UseBasicParsing -TimeoutSec 15
  "HTTP $($r.StatusCode) $($r.Content)" | Out-File -Append -Encoding UTF8 $log
} catch {
  $code = $_.Exception.Response.StatusCode.value__
  "ERR $code $($_.Exception.Message)" | Out-File -Append -Encoding UTF8 $log
}
"history:" | Out-File -Append -Encoding UTF8 $log
try {
  $r = Invoke-WebRequest -Uri "$base/flood-drought/history?startDate=2026-09-01&endDate=2026-09-03" -Headers $headers -UseBasicParsing -TimeoutSec 15
  "HTTP $($r.StatusCode) $($r.Content)" | Out-File -Append -Encoding UTF8 $log
} catch {
  $code = $_.Exception.Response.StatusCode.value__
  "ERR $code $($_.Exception.Message)" | Out-File -Append -Encoding UTF8 $log
}
Write-Output 'DONE'
