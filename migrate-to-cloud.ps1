# =====================================================================
#  FitJanta: copy your local MySQL data to the Aiven cloud MySQL
#  Copies every table (users, meals, workouts, posts, photos, comments...)
#
#  Run:  powershell -ExecutionPolicy Bypass -File .\migrate-to-cloud.ps1
#  You will be asked for the Aiven details (password is typed hidden).
#  WARNING: this REPLACES the fitjanta tables in the cloud database.
# =====================================================================

$bin   = "C:\Program Files\MySQL\MySQL Server 8.0\bin"
$dump  = Join-Path $env:TEMP "fitjanta-cloud-dump.sql"
$appDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# Local login comes from the local-only config file
$props = Get-Content "$appDir\config\application.properties"
$localUser = (($props | Where-Object { $_ -match '^spring\.datasource\.username=' }) -replace '^[^=]+=', '').Trim()
$localPass = (($props | Where-Object { $_ -match '^spring\.datasource\.password=' }) -replace '^[^=]+=', '').Trim()

Write-Host ""
Write-Host "  FITJANTA  |  copy local MySQL -> Aiven cloud MySQL" -ForegroundColor White -BackgroundColor DarkMagenta
Write-Host "  Find these on Aiven: your MySQL service > Overview > Connection information" -ForegroundColor Gray
Write-Host ""
$cloudHost = (Read-Host "  Aiven Host (e.g. mysql-xxxx.aivencloud.com)").Trim()
$cloudPort = (Read-Host "  Aiven Port (e.g. 12345)").Trim()
$cloudUser = (Read-Host "  Aiven User [avnadmin]").Trim()
if (-not $cloudUser) { $cloudUser = "avnadmin" }
$secure = Read-Host "  Aiven Password (hidden)" -AsSecureString
$cloudPass = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))

$confirm = Read-Host "  This replaces the 'fitjanta' tables on Aiven with your local data. Type YES to continue"
if ($confirm -ne "YES") { Write-Host "  Cancelled." -ForegroundColor Yellow; exit 0 }

try {
    Write-Host "`n  1/3  Exporting local database..." -ForegroundColor Cyan
    $env:MYSQL_PWD = $localPass
    & "$bin\mysqldump.exe" -u $localUser -h 127.0.0.1 --single-transaction --set-gtid-purged=OFF --no-tablespaces `
        --max-allowed-packet=256M --default-character-set=utf8mb4 --result-file="$dump" fitjanta
    if ($LASTEXITCODE -ne 0) { throw "Export failed." }
    Write-Host ("       {0:N1} MB exported" -f ((Get-Item $dump).Length / 1MB))

    Write-Host "  2/3  Creating database on Aiven..." -ForegroundColor Cyan
    $env:MYSQL_PWD = $cloudPass
    & "$bin\mysql.exe" -u $cloudUser -h $cloudHost -P $cloudPort --ssl-mode=REQUIRED -e "CREATE DATABASE IF NOT EXISTS fitjanta CHARACTER SET utf8mb4;"
    if ($LASTEXITCODE -ne 0) { throw "Could not connect to Aiven. Check host, port, user and password." }

    Write-Host "  3/3  Importing into Aiven (this can take a minute)..." -ForegroundColor Cyan
    & "$bin\mysql.exe" -u $cloudUser -h $cloudHost -P $cloudPort --ssl-mode=REQUIRED --max-allowed-packet=256M `
        --default-character-set=utf8mb4 fitjanta -e "source $($dump -replace '\\','/')"
    if ($LASTEXITCODE -ne 0) { throw "Import failed." }

    Write-Host "`n  Done! Rows now in the cloud:" -ForegroundColor Green
    & "$bin\mysql.exe" -u $cloudUser -h $cloudHost -P $cloudPort --ssl-mode=REQUIRED fitjanta --table -e `
        "SELECT (SELECT COUNT(*) FROM users) users, (SELECT COUNT(*) FROM posts) posts, (SELECT COUNT(*) FROM media) photos, (SELECT COUNT(*) FROM food_logs) meals, (SELECT COUNT(*) FROM activities) workouts, (SELECT COUNT(*) FROM comments) comments;"

    Write-Host "`n  Values for Render (Environment):" -ForegroundColor Yellow
    Write-Host "     DB_URL      = jdbc:mysql://${cloudHost}:${cloudPort}/fitjanta?sslMode=REQUIRED"
    Write-Host "     DB_USER     = $cloudUser"
    Write-Host "     DB_PASSWORD = (your Aiven password)"
}
catch {
    Write-Host "`n  ERROR: $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    Remove-Item $dump -ErrorAction SilentlyContinue
    $cloudPass = $null
}
