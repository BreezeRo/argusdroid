param(
  [int]$Port = 8091,
  [string]$ConfigPath = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$serverDir = Join-Path $root "dashboard-server"

if (-not (Test-Path (Join-Path $serverDir "server.js"))) {
  throw "Node dashboard server not found at $serverDir"
}

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
  throw "Node.js is required but was not found in PATH. Install Node.js LTS and try again."
}

Push-Location $serverDir
try {
  if (-not (Test-Path (Join-Path $serverDir "node_modules"))) {
    Write-Host "Installing dashboard-server dependencies..."
    npm install
  }

  if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    node .\server.js --port $Port
  }
  else {
    node .\server.js --port $Port --config $ConfigPath
  }
}
finally {
  Pop-Location
}
