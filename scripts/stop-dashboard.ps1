param(
  [int]$Port = 8091
)

$ErrorActionPreference = "Stop"

$connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if (-not $connections) {
  Write-Host "No listening process found on port $Port"
  exit 0
}

$pids = @($connections | Select-Object -ExpandProperty OwningProcess -Unique)
foreach ($procId in $pids) {
  try {
    Stop-Process -Id $procId -Force -ErrorAction Stop
    Write-Host "Stopped PID $procId on port $Port"
  }
  catch {
    Write-Host "Failed to stop PID $procId on port ${Port}: $($_.Exception.Message)"
  }
}
