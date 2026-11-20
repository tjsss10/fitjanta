# =====================================================================
#  FitJanta: public HTTPS link (works on ANY network / mobile data)
#
#  Default: Pinggy tunnel over port 443 using Windows' built-in SSH.
#           Works even on college networks that block other ports.
#           Free links last 60 minutes, then run this again.
#  Option : -Cloudflare  uses tools\cloudflared.exe (no time limit,
#           but needs port 7844, which some networks block).
#
#  - Start FitJanta first (start-fitjanta.bat), then run this.
#  - The link changes every time. Anyone with the link can open the app.
#  - Pinggy shows a one-time "Caution" page: tap "Continue".
#  - Stop with Ctrl + C.
# =====================================================================

param(
    [switch]$Cloudflare,
    # How long to keep the public link running (0 = until Ctrl + C)
    [double]$Hours = 0
)

# Keep the laptop awake while the public link runs (like a video player does).
# This does not change any Windows settings; it ends when this window closes.
Add-Type -Namespace FitJanta -Name Power -MemberDefinition @"
[System.Runtime.InteropServices.DllImport("kernel32.dll")]
public static extern uint SetThreadExecutionState(uint esFlags);
"@
$ES_CONTINUOUS = [uint32]"0x80000000"
$ES_SYSTEM_REQUIRED = [uint32]"0x00000001"
[void][FitJanta.Power]::SetThreadExecutionState($ES_CONTINUOUS -bor $ES_SYSTEM_REQUIRED)

$stopAt = if ($Hours -gt 0) { (Get-Date).AddHours($Hours) } else { $null }

$appDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$tools   = Join-Path $appDir "tools"
$urlFile = Join-Path $tools "public-url.txt"
$outFile = Join-Path $tools "tunnel.out"
$errFile = Join-Path $tools "tunnel.err"
New-Item -ItemType Directory -Force $tools | Out-Null

try { Invoke-WebRequest "http://localhost:8080/api/food/info" -UseBasicParsing -TimeoutSec 3 | Out-Null }
catch {
    Write-Host "FitJanta is not running. Start it first (start-fitjanta.bat)." -ForegroundColor Red
    exit 1
}

# Stop an older tunnel started by this script
if (Test-Path "$tools\tunnel.pid") {
    $old = Get-Content "$tools\tunnel.pid" -ErrorAction SilentlyContinue
    if ($old) { Stop-Process -Id $old -Force -ErrorAction SilentlyContinue }
}
Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Remove-Item $urlFile, $outFile, $errFile -ErrorAction SilentlyContinue

$proc = $null
try {
# Keep a link alive: when one expires (60 min) or the network drops, start a new one
while ($true) {

if ($stopAt -and (Get-Date) -ge $stopAt) {
    Write-Host ("  {0} hour(s) are up. Stopping the public link." -f $Hours) -ForegroundColor Yellow
    break
}

Write-Host ""
Write-Host ("  FITJANTA  |  starting public HTTPS link...  ({0})" -f (Get-Date -Format "HH:mm")) -ForegroundColor White -BackgroundColor DarkMagenta
Remove-Item $outFile, $errFile -ErrorAction SilentlyContinue

if ($Cloudflare) {
    $exe = Join-Path $tools "cloudflared.exe"
    $proc = Start-Process -FilePath $exe -ArgumentList "tunnel", "--url", "http://localhost:8080", "--no-autoupdate", "--logfile", "`"$errFile`"" -WindowStyle Hidden -PassThru
    $pattern = 'https://[a-z0-9-]+\.trycloudflare\.com'
}
else {
    # A key just for this tunnel (Pinggy's free tier accepts any key)
    $key = Join-Path $tools "pinggy_key"
    if (-not (Test-Path $key)) {
        & "$env:WINDIR\System32\OpenSSH\ssh-keygen.exe" -q -t ed25519 -N '""' -C "fitjanta-tunnel" -f $key
    }
    $proc = Start-Process -FilePath "$env:WINDIR\System32\OpenSSH\ssh.exe" `
        -ArgumentList "-p", "443", "-T", "-i", "`"$key`"", "-o", "IdentitiesOnly=yes", "-o", "StrictHostKeyChecking=accept-new",
                      "-o", "ServerAliveInterval=30", "-o", "ExitOnForwardFailure=yes", "-o", "BatchMode=yes",
                      "-R0:localhost:8080", "a.pinggy.io" `
        -RedirectStandardOutput $outFile -RedirectStandardError $errFile -WindowStyle Hidden -PassThru
    $pattern = 'https://[a-z0-9-]+\.(free\.pinggy\.net|run\.pinggy-free\.link|a\.free\.pinggy\.link)'
}
$proc.Id | Out-File -Encoding ascii "$tools\tunnel.pid"

$url = $null
for ($i = 0; $i -lt 45 -and -not $url; $i++) {
    Start-Sleep -Seconds 1
    $text = (Get-Content $outFile, $errFile -Raw -ErrorAction SilentlyContinue) -join "`n"
    $m = [regex]::Match($text, $pattern)
    if ($m.Success) { $url = $m.Value }
    if ($proc.HasExited) { break }
}

if (-not $url) {
    Write-Host "  Could not create the link (network problem). Retrying in 10 seconds..." -ForegroundColor Red
    Get-Content $outFile, $errFile -ErrorAction SilentlyContinue | Select-Object -Last 3
    Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 10
    continue
}

Set-Content -Path $urlFile -Value $url -Encoding ascii

Write-Host ""
Write-Host "  Public link (open on any phone, any network):" -ForegroundColor Green
Write-Host "     $url" -ForegroundColor Cyan
Write-Host ""
if (-not $Cloudflare) {
    Write-Host "  First visit shows a Pinggy 'Caution' page: tap Continue." -ForegroundColor Yellow
    Write-Host "  Free links last 60 minutes; a NEW link is created automatically after that." -ForegroundColor Yellow
}
Write-Host "  QR code (always the current link): http://localhost:8080/qr.html on this laptop" -ForegroundColor Gray
if ($stopAt) {
    Write-Host ("  Running until {0} ({1} h). Laptop is kept awake; don't close the lid." -f $stopAt.ToString("HH:mm"), $Hours) -ForegroundColor Gray
}
Write-Host "  Keep this window open. Press Ctrl + C to stop the public link." -ForegroundColor Gray
Write-Host ""

# Wait for the tunnel to end, but stop it when the time limit is reached
while (-not $proc.HasExited) {
    if ($stopAt -and (Get-Date) -ge $stopAt) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue; break }
    Start-Sleep -Seconds 5
}
if ($stopAt -and (Get-Date) -ge $stopAt) { continue }
Remove-Item $urlFile -ErrorAction SilentlyContinue
Write-Host ("  Link ended at {0} (expired or network dropped). Creating a new one..." -f (Get-Date -Format "HH:mm")) -ForegroundColor Yellow
Start-Sleep -Seconds 3
}
}
finally {
    if ($proc) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
    Remove-Item $urlFile -ErrorAction SilentlyContinue
    [void][FitJanta.Power]::SetThreadExecutionState($ES_CONTINUOUS)   # allow sleep again
    Write-Host "  Public link stopped." -ForegroundColor Yellow
}
