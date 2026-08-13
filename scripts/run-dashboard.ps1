param(
  [int]$Port = 8088,
  [string]$ConfigPath = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$dashboardDir = Join-Path $root "dashboard"
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
  $ConfigPath = Join-Path $dashboardDir "config\mesh-config.json"
}

if (-not (Test-Path $dashboardDir)) {
  throw "Dashboard directory not found: $dashboardDir"
}

function Get-LanIpCandidates {
  $virtualPattern = "Hyper-V|Virtual|VMware|Loopback|Tunnel|WSL"

  $byGateway =
    Get-NetIPConfiguration |
    Where-Object {
      $_.IPv4DefaultGateway -and
      $_.IPv4Address -and
      $_.NetAdapter.Status -eq "Up" -and
      $_.NetAdapter.InterfaceDescription -notmatch $virtualPattern
    } |
    ForEach-Object {
      $_.IPv4Address | ForEach-Object { $_.IPAddress }
    }

  if ($byGateway) {
    return $byGateway | Select-Object -Unique
  }

  return (
    Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object {
      $_.IPAddress -notlike "169.254.*" -and
      $_.IPAddress -ne "127.0.0.1" -and
      $_.PrefixOrigin -ne "WellKnown"
    } |
    Select-Object -ExpandProperty IPAddress -Unique
  )
}

$lanIps = @(Get-LanIpCandidates)
Write-Host "Serving dashboard in view-only mode"
Write-Host "Local URL:   http://localhost:$Port"
if ($lanIps.Count -gt 0) {
  Write-Host "LAN URL(s):"
  foreach ($candidate in $lanIps) {
    Write-Host "  http://${candidate}:$Port"
  }
}
else {
  Write-Host "LAN URL:     Unable to detect a LAN IP automatically"
}
Write-Host "Live API:    GET /api/mesh/live"
Write-Host "Config file: $ConfigPath"
Write-Host "Press Ctrl+C to stop"

$mimeTypes = @{
  ".html" = "text/html; charset=utf-8"
  ".css"  = "text/css; charset=utf-8"
  ".js"   = "application/javascript; charset=utf-8"
  ".json" = "application/json; charset=utf-8"
  ".png"  = "image/png"
  ".jpg"  = "image/jpeg"
  ".jpeg" = "image/jpeg"
  ".svg"  = "image/svg+xml"
  ".ico"  = "image/x-icon"
  ".txt"  = "text/plain; charset=utf-8"
}

$script:liveSnapshotCache = @{
  generatedAt = (Get-Date).ToUniversalTime().ToString("o")
  mesh = @{ discoveredPeers = 0; connectedPeers = 0; peers = @() }
  encounters = @()
  meta = @{ live = $false; reason = "Snapshot cache warming up" }
}
$script:liveSnapshotCacheEpochMs = 0L
$script:liveSnapshotCacheTtlMs = 60000L
$script:liveSnapshotBuilding = $false

function Get-MeshConfig {
  param([string]$Path)

  $default = [ordered]@{
    enabled = $false
    requesterNodeId = "dashboard-$($env:COMPUTERNAME)"
    sharedSecret = ""
    syncWindowMinutes = 120
    peers = @()
  }

  if (-not (Test-Path $Path)) {
    return $default
  }

  try {
    $raw = Get-Content -Path $Path -Raw
    $cfg = $raw | ConvertFrom-Json
    return [ordered]@{
      enabled = [bool]$cfg.enabled
      requesterNodeId = if ($cfg.requesterNodeId) { [string]$cfg.requesterNodeId } else { $default.requesterNodeId }
      sharedSecret = if ($cfg.sharedSecret) { [string]$cfg.sharedSecret } else { "" }
      syncWindowMinutes = if ($cfg.syncWindowMinutes) { [int]$cfg.syncWindowMinutes } else { 120 }
      peers = @($cfg.peers)
    }
  }
  catch {
    return $default
  }
}

function Get-MapsApiKey {
  param([string]$RepoRoot)

  $paths = @(
    (Join-Path $RepoRoot "local.properties"),
    (Join-Path $RepoRoot "app\local.properties")
  )

  foreach ($path in $paths) {
    if (-not (Test-Path $path)) {
      continue
    }

    try {
      $lines = Get-Content -Path $path
      foreach ($name in @("DASHBOARD_MAPS_API_KEY", "MAPS_API_KEY")) {
        $line = $lines | Where-Object { $_ -match ("^\s*{0}\s*=" -f [Regex]::Escape($name)) } | Select-Object -First 1
        if (-not [string]::IsNullOrWhiteSpace($line)) {
          $value = ($line -split '=', 2)[1].Trim()
          if (-not [string]::IsNullOrWhiteSpace($value)) {
            return $value
          }
        }
      }
    }
    catch {
      continue
    }
  }

  return ""
}

function Get-BodyJson {
  param($Object)
  return ($Object | ConvertTo-Json -Depth 12 -Compress)
}

function New-HmacSha256Hex {
  param(
    [string]$Secret,
    [string]$Payload
  )

  $hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($Secret))
  try {
    $hash = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($Payload))
    return ($hash | ForEach-Object { $_.ToString("x2") }) -join ""
  }
  finally {
    $hmac.Dispose()
  }
}

function New-AuthHeaders {
  param(
    [string]$SharedSecret,
    [string]$NodeId,
    [string]$Method,
    [string]$Path,
    [string]$Body
  )

  $timestampMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  $canonical = "{0}`n{1}`n{2}`n{3}`n{4}" -f $Method.ToUpperInvariant(), $Path, $NodeId, $timestampMs, $Body
  $signature = New-HmacSha256Hex -Secret $SharedSecret -Payload $canonical

  return @{
    "X-Argus-Auth-Node" = $NodeId
    "X-Argus-Auth-Timestamp-Ms" = "$timestampMs"
    "X-Argus-Auth-Signature" = $signature
  }
}

function Invoke-JsonGet {
  param(
    [string]$Url,
    [int]$TimeoutMs = 1200
  )

  try {
    $timeoutSec = [int][Math]::Ceiling($TimeoutMs / 1000.0)
    return Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec $timeoutSec
  }
  catch {
    return $null
  }
}

function Invoke-JsonPost {
  param(
    [string]$Url,
    [string]$Body,
    [hashtable]$Headers,
    [int]$TimeoutMs = 2200
  )

  try {
    $timeoutSec = [int][Math]::Ceiling($TimeoutMs / 1000.0)
    return Invoke-RestMethod -Uri $Url -Method Post -Body $Body -ContentType "application/json" -Headers $Headers -TimeoutSec $timeoutSec
  }
  catch {
    return $null
  }
}

function Estimate-DistanceMeters {
  param([object]$Rssi)

  if ($null -eq $Rssi) {
    return 0.0
  }

  $txPower = -59.0
  $n = 2.2
  $exp = (($txPower - [double]$Rssi) / (10.0 * $n))
  $meters = [Math]::Pow(10.0, $exp)
  return [Math]::Round([Math]::Max(0.5, [Math]::Min(1200.0, $meters)), 1)
}

function Get-ZoneLabel {
  param(
    [double]$Lat,
    [double]$Lng,
    [string]$PeerLabel
  )

  $latBucket = [Math]::Floor($Lat * 100) / 100
  $lngBucket = [Math]::Floor($Lng * 100) / 100
  return "{0},{1} ({2})" -f $latBucket, $lngBucket, $PeerLabel
}

function Get-SourceDisplayLabel {
  param(
    [string]$Source,
    [string]$SecondaryId
  )

  switch ($Source) {
    "CELL" {
      if (-not [string]::IsNullOrWhiteSpace($SecondaryId)) {
        return "CELL TOWER ($SecondaryId)"
      }
      return "CELL TOWER"
    }
    "WIFI_DIRECT" { return "WIFI DIRECT" }
    "BLUETOOTH_LE" { return "BLUETOOTH LE" }
    "BLUETOOTH_CLASSIC" { return "BLUETOOTH CLASSIC" }
    "REMOTE_ID" { return "REMOTE ID" }
    "UNKNOWN_RF" { return "UNKNOWN RF" }
    default { return $Source }
  }
}

function Build-LiveSnapshot {
  param([string]$ConfigFile)

  $config = Get-MeshConfig -Path $ConfigFile
  if (-not $config.enabled) {
    return @{
      generatedAt = (Get-Date).ToUniversalTime().ToString("o")
      mesh = @{
        discoveredPeers = 0
        connectedPeers = 0
        peers = @()
      }
      encounters = @()
      meta = @{
        live = $false
        reason = "Live mesh mode disabled in config"
      }
    }
  }

  $sharedSecret = [string]$config.sharedSecret
  $requesterNodeId = ([string]$config.requesterNodeId).Trim()
  if ([string]::IsNullOrWhiteSpace($requesterNodeId)) {
    $requesterNodeId = "dashboard-$($env:COMPUTERNAME)"
  }

  $hosts = @($config.peers | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne "" } | Select-Object -Unique)
  if ($hosts.Count -eq 0) {
    return @{
      generatedAt = (Get-Date).ToUniversalTime().ToString("o")
      mesh = @{
        discoveredPeers = 0
        connectedPeers = 0
        peers = @()
      }
      encounters = @()
      meta = @{
        live = $false
        reason = "No peers configured. Add peer IPs in dashboard/config/mesh-config.json"
      }
    }
  }

  $sinceMs = [DateTimeOffset]::UtcNow.AddMinutes(-1 * [Math]::Max(1, [int]$config.syncWindowMinutes)).ToUnixTimeMilliseconds()
  $allEncounters = @()
  $peerRows = @()

  foreach ($peerHost in $hosts) {
    $hello = $null
    $helloError = ""
    try {
      $hello = Invoke-JsonGet -Url ("http://{0}:18777/argus/v1/hello" -f $peerHost) -TimeoutMs 900
    }
    catch {
      $helloError = $_.Exception.Message
    }

    if ($null -eq $hello) {
      $statusText = "Unavailable"
      if (-not [string]::IsNullOrWhiteSpace($helloError)) {
        $statusText = "Unavailable: $helloError"
      }
      $peerRows += [pscustomobject]@{
        name = $peerHost
        state = "FAILED"
        lastSeen = $statusText
      }
      continue
    }

    $peerName = if ($hello.deviceName) { [string]$hello.deviceName } elseif ($hello.nodeId) { [string]$hello.nodeId } else { $peerHost }

    if ([string]::IsNullOrWhiteSpace($sharedSecret)) {
      $peerRows += [pscustomobject]@{
        name = $peerName
        state = "DISCOVERED"
        lastSeen = "Hello ok, waiting for shared secret"
      }
      continue
    }

    $reqObject = [ordered]@{
      requesterNodeId = $requesterNodeId
      sinceEpochMs = $sinceMs
      encounters = @()
    }
    $reqBody = Get-BodyJson -Object $reqObject
    $headers = New-AuthHeaders -SharedSecret $sharedSecret -NodeId $requesterNodeId -Method "POST" -Path "/argus/v1/sync" -Body $reqBody

    $syncResponse = $null
    $syncError = ""
    try {
      $syncResponse = Invoke-JsonPost -Url ("http://{0}:18777/argus/v1/sync" -f $peerHost) -Body $reqBody -Headers $headers -TimeoutMs 1400
    }
    catch {
      $syncError = $_.Exception.Message
    }

    if ($null -eq $syncResponse) {
      $statusText = "Sync failed"
      if (-not [string]::IsNullOrWhiteSpace($syncError)) {
        $statusText = "Sync failed: $syncError"
      }
      $peerRows += [pscustomobject]@{
        name = $peerName
        state = "FAILED"
        lastSeen = $statusText
      }
      continue
    }

    $peerRows += [pscustomobject]@{
      name = $peerName
      state = "CONNECTED"
      lastSeen = "Synced just now"
    }

    foreach ($enc in @($syncResponse.encounters)) {
      try {
        if ($null -eq $enc.lat -or $null -eq $enc.lon) {
          continue
        }

        if ($null -eq $enc.timestampEpochMs) {
          continue
        }

        $ts = [long]$enc.timestampEpochMs
        $iso = [DateTimeOffset]::FromUnixTimeMilliseconds($ts).ToUniversalTime().ToString("o")
        $label = if ($enc.primaryId) { [string]$enc.primaryId } else { "unknown" }
        $secondary = if ($enc.secondaryId) { [string]$enc.secondaryId } else { "" }
        $source = if ($enc.source) { [string]$enc.source } else { "UNKNOWN_RF" }
        $signal = if ($null -ne $enc.rssiDbm) { [int]$enc.rssiDbm } else { -120 }
        $distance = Estimate-DistanceMeters -Rssi $enc.rssiDbm

        $allEncounters += [pscustomobject]@{
          id = ("{0}:{1}:{2}" -f $peerHost, $source, $enc.timestampEpochMs)
          timestamp = $iso
          source = $source
          sourceLabel = Get-SourceDisplayLabel -Source $source -SecondaryId $secondary
          label = $label
          secondaryId = $secondary
          signalDbm = $signal
          distanceMeters = $distance
          lat = [double]$enc.lat
          lng = [double]$enc.lon
          zone = Get-ZoneLabel -Lat ([double]$enc.lat) -Lng ([double]$enc.lon) -PeerLabel $peerHost
        }
      }
      catch {
        # Skip malformed encounter rows from a peer instead of failing the entire snapshot.
        continue
      }
    }
  }

  $connected = @($peerRows | Where-Object { $_.state -eq "CONNECTED" }).Count

  return @{
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    mesh = @{
      discoveredPeers = $peerRows.Count
      connectedPeers = $connected
      peers = $peerRows
    }
    encounters = $allEncounters
    meta = @{
      live = $true
      requesterNodeId = $requesterNodeId
      peerCount = $peerRows.Count
    }
  }
}

function Get-LiveSnapshotCached {
  param([string]$ConfigFile)

  $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  $cacheAgeMs = $nowMs - $script:liveSnapshotCacheEpochMs

  if ($script:liveSnapshotCache -and $cacheAgeMs -lt $script:liveSnapshotCacheTtlMs) {
    return $script:liveSnapshotCache
  }

  if ($script:liveSnapshotBuilding) {
    return $script:liveSnapshotCache
  }

  $script:liveSnapshotBuilding = $true
  try {
    $fresh = Build-LiveSnapshot -ConfigFile $ConfigFile
    $script:liveSnapshotCache = $fresh
    $script:liveSnapshotCacheEpochMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    return $fresh
  }
  catch {
    return $script:liveSnapshotCache
  }
  finally {
    $script:liveSnapshotBuilding = $false
  }
}

function Write-HttpResponse {
  param(
    [System.IO.Stream]$Stream,
    [int]$StatusCode,
    [string]$StatusText,
    [string]$ContentType,
    [byte[]]$BodyBytes,
    [long]$ContentLength = -1,
    [hashtable]$ExtraHeaders = @{}
  )

  if ($ContentLength -lt 0) {
    $ContentLength = $BodyBytes.Length
  }

  $header = "HTTP/1.1 $StatusCode $StatusText`r`n" +
    "Content-Type: $ContentType`r`n" +
    "Content-Length: $ContentLength`r`n" +
    "Connection: close`r`n"

  foreach ($entry in $ExtraHeaders.GetEnumerator()) {
    $header += "$($entry.Key): $($entry.Value)`r`n"
  }
  $header += "`r`n"

  $headerBytes = [Text.Encoding]::ASCII.GetBytes($header)
  try {
    $Stream.Write($headerBytes, 0, $headerBytes.Length)
    if ($BodyBytes.Length -gt 0) {
      $Stream.Write($BodyBytes, 0, $BodyBytes.Length)
    }
    $Stream.Flush()
  }
  catch {
    # Ignore aborted client connections so one dropped request does not kill the server loop.
  }
}

function Start-PowerShellDashboardServer {
  param(
    [string]$Root,
    [int]$ListenPort,
    [hashtable]$TypeMap,
    [string]$MeshConfigPath
  )

  $repoRoot = Split-Path -Parent $Root

  $address = [System.Net.IPAddress]::Any
  $listener = [System.Net.Sockets.TcpListener]::new($address, $ListenPort)
  $listener.Start()

  try {
    while ($true) {
      $client = $listener.AcceptTcpClient()
      try {
        $stream = $client.GetStream()
        $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::ASCII, $false, 1024, $true)
        $requestLine = $reader.ReadLine()

        if ([string]::IsNullOrWhiteSpace($requestLine)) {
          $client.Close()
          continue
        }

        while (($headerLine = $reader.ReadLine()) -ne "") {
          if ($null -eq $headerLine) {
            break
          }
        }

        $parts = $requestLine.Split(' ')
        $method = if ($parts.Length -gt 0) { $parts[0].ToUpperInvariant() } else { "" }
        $rawPath = if ($parts.Length -gt 1) { $parts[1] } else { "/" }

        if ($method -ne "GET" -and $method -ne "HEAD") {
          Write-HttpResponse -Stream $stream -StatusCode 405 -StatusText "Method Not Allowed" -ContentType "text/plain; charset=utf-8" -BodyBytes ([Text.Encoding]::UTF8.GetBytes("405 Method Not Allowed"))
          $client.Close()
          continue
        }

        $pathWithoutQuery = $rawPath.Split('?')[0]
        if ([string]::IsNullOrWhiteSpace($pathWithoutQuery) -or $pathWithoutQuery -eq "/") {
          $pathWithoutQuery = "/index.html"
        }

        if ($pathWithoutQuery -eq "/api/mesh/live") {
          try {
            $snapshot = Get-LiveSnapshotCached -ConfigFile $MeshConfigPath
            $body = Get-BodyJson -Object $snapshot
            $bytes = [Text.Encoding]::UTF8.GetBytes($body)
            if ($method -eq "HEAD") {
              Write-HttpResponse -Stream $stream -StatusCode 200 -StatusText "OK" -ContentType "application/json; charset=utf-8" -BodyBytes @() -ContentLength $bytes.Length -ExtraHeaders @{"Cache-Control" = "no-store"}
            }
            else {
              Write-HttpResponse -Stream $stream -StatusCode 200 -StatusText "OK" -ContentType "application/json; charset=utf-8" -BodyBytes $bytes -ExtraHeaders @{"Cache-Control" = "no-store"}
            }
          }
          catch {
            $message = $_.Exception.Message
            $line = $_.InvocationInfo.ScriptLineNumber
            $type = $_.Exception.GetType().FullName
            $stack = $_.ScriptStackTrace
            $dotNetStack = $_.Exception.StackTrace
            $targetSite = if ($_.Exception.TargetSite) { $_.Exception.TargetSite.ToString() } else { "" }
            Write-Host "[mesh-live] $type @ line $line :: $message"
            $errorPayload = Get-BodyJson -Object ([ordered]@{
              generatedAt = (Get-Date).ToUniversalTime().ToString("o")
              mesh = [ordered]@{
                discoveredPeers = 0
                connectedPeers = 0
                peers = @()
              }
              encounters = @()
              meta = [ordered]@{
                live = $false
                reason = "Live API error: $message"
                errorType = $type
                errorLine = $line
                scriptStack = $stack
                targetSite = $targetSite
                dotNetStack = $dotNetStack
              }
            })
            $errorBytes = [Text.Encoding]::UTF8.GetBytes($errorPayload)
            Write-HttpResponse -Stream $stream -StatusCode 200 -StatusText "OK" -ContentType "application/json; charset=utf-8" -BodyBytes $errorBytes -ExtraHeaders @{"Cache-Control" = "no-store"}
          }
          $client.Close()
          continue
        }

        if ($pathWithoutQuery -eq "/api/dashboard-config") {
          $apiKey = Get-MapsApiKey -RepoRoot $repoRoot
          $payload = Get-BodyJson -Object @{
            googleMapsApiKey = $apiKey
          }
          $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
          if ($method -eq "HEAD") {
            Write-HttpResponse -Stream $stream -StatusCode 200 -StatusText "OK" -ContentType "application/json; charset=utf-8" -BodyBytes @() -ContentLength $bytes.Length -ExtraHeaders @{"Cache-Control" = "no-store"}
          }
          else {
            Write-HttpResponse -Stream $stream -StatusCode 200 -StatusText "OK" -ContentType "application/json; charset=utf-8" -BodyBytes $bytes -ExtraHeaders @{"Cache-Control" = "no-store"}
          }
          $client.Close()
          continue
        }

        $requestPath = [System.Uri]::UnescapeDataString($pathWithoutQuery)
        $safeRelativePath = $requestPath.TrimStart('/').Replace('/', [IO.Path]::DirectorySeparatorChar)
        $targetPath = Join-Path $Root $safeRelativePath

        $fullRoot = [IO.Path]::GetFullPath($Root)
        $fullTarget = [IO.Path]::GetFullPath($targetPath)

        if (-not $fullTarget.StartsWith($fullRoot, [StringComparison]::OrdinalIgnoreCase)) {
          Write-HttpResponse -Stream $stream -StatusCode 403 -StatusText "Forbidden" -ContentType "text/plain; charset=utf-8" -BodyBytes ([Text.Encoding]::UTF8.GetBytes("403 Forbidden"))
          $client.Close()
          continue
        }

        if (-not (Test-Path $fullTarget -PathType Leaf)) {
          Write-HttpResponse -Stream $stream -StatusCode 404 -StatusText "Not Found" -ContentType "text/plain; charset=utf-8" -BodyBytes ([Text.Encoding]::UTF8.GetBytes("404 Not Found"))
          $client.Close()
          continue
        }

        $extension = [IO.Path]::GetExtension($fullTarget).ToLowerInvariant()
        $contentType = $TypeMap[$extension]
        if (-not $contentType) {
          $contentType = "application/octet-stream"
        }

        $bodyBytes = [IO.File]::ReadAllBytes($fullTarget)
        if ($method -eq "HEAD") {
          Write-HttpResponse -Stream $stream -StatusCode 200 -StatusText "OK" -ContentType $contentType -BodyBytes @() -ContentLength $bodyBytes.Length
        }
        else {
          Write-HttpResponse -Stream $stream -StatusCode 200 -StatusText "OK" -ContentType $contentType -BodyBytes $bodyBytes
        }
      }
      catch {
        try {
          $errorBytes = [Text.Encoding]::UTF8.GetBytes("500 Internal Server Error")
          Write-HttpResponse -Stream $stream -StatusCode 500 -StatusText "Internal Server Error" -ContentType "text/plain; charset=utf-8" -BodyBytes $errorBytes
        }
        catch { }
      }
      finally {
        $client.Close()
      }
    }
  }
  finally {
    $listener.Stop()
  }
}

Start-PowerShellDashboardServer -Root $dashboardDir -ListenPort $Port -TypeMap $mimeTypes -MeshConfigPath $ConfigPath
