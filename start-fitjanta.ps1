# =====================================================================
#  FitJanta auto-start
#  1. Waits for MySQL (service MYSQL80) to accept connections
#  2. Starts the Spring Boot app in a minimized window
#  3. Opens the QR code page so a phone can scan and open the app
#
#  Run manually:   double-click start-fitjanta.bat
#  Runs at login:  via "FitJanta.bat" in the Windows Startup folder
# =====================================================================

$appDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$appPort = 8080
$qrPage  = "http://localhost:$appPort/qr.html"

function Test-Port([int]$port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect("127.0.0.1", $port, $null, $null)
        return ($async.AsyncWaitHandle.WaitOne(1000) -and $client.Connected)
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

Write-Host ""
Write-Host "  FIT JANTA - starting up" -ForegroundColor White
Write-Host "  ------------------------"

# --- Already running? Just show the QR page. -------------------------
if (Test-Port $appPort) {
    Write-Host "  FitJanta is already running. Opening the QR page..."
    Start-Process $qrPage
    exit 0
}

# --- 1. Wait for MySQL (up to 3 minutes) ------------------------------
Write-Host "  Waiting for MySQL on port 3306..."
$mysqlReady = $false
for ($i = 0; $i -lt 90; $i++) {
    if (Test-Port 3306) { $mysqlReady = $true; break }
    Start-Sleep -Seconds 2
}

if (-not $mysqlReady) {
    Write-Host ""
    Write-Host "  MySQL is not running." -ForegroundColor Red
    Write-Host "  Start it: Win + R -> services.msc -> MYSQL80 -> right-click -> Start"
    Write-Host "  (Set 'Startup type' to Automatic so it starts with Windows.)"
    Write-Host ""
    Read-Host "  Press Enter to close"
    exit 1
}
Write-Host "  MySQL is running." -ForegroundColor Green

# --- 2. Start the Spring Boot app in its own minimized window --------
Write-Host "  Starting FitJanta server (this takes about 30 seconds)..."
Start-Process -FilePath "$appDir\gradlew.bat" `
              -ArgumentList "bootRun", "--console=plain" `
              -WorkingDirectory $appDir `
              -WindowStyle Minimized

# --- 3. Wait for the app, then open the QR page ----------------------
for ($i = 0; $i -lt 120; $i++) {
    if (Test-Port $appPort) {
        Start-Sleep -Seconds 2
        Write-Host "  FitJanta is running!" -ForegroundColor Green
        Start-Process $qrPage
        exit 0
    }
    Start-Sleep -Seconds 2
}

Write-Host ""
Write-Host "  FitJanta did not start within 4 minutes." -ForegroundColor Red
Write-Host "  Open the minimized 'gradlew' window on the taskbar to see the error."
Read-Host "  Press Enter to close"
exit 1
