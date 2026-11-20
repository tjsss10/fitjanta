# =====================================================================
#  FitJanta: LIVE MySQL monitor
#  Shows the latest rows of every FitJanta table and refreshes every
#  few seconds. New rows (created on the phone or website) turn GREEN.
#
#  Run:   powershell -ExecutionPolicy Bypass -File .\mysql-live-monitor.ps1
#  Stop:  Ctrl + C
# =====================================================================

param([int]$RefreshSeconds = 3)

$appDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$mysql  = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"

# Read the database login from the app's own config (no password typed here)
$props = Get-Content "$appDir\src\main\resources\application.properties"
$user  = (($props | Where-Object { $_ -match '^spring\.datasource\.username=' }) -replace '^[^=]+=', '').Trim()
$pass  = (($props | Where-Object { $_ -match '^spring\.datasource\.password=' }) -replace '^[^=]+=', '').Trim()
$env:MYSQL_PWD = $pass

function Invoke-Sql([string]$sql) {
    $out = & $mysql -u $user -h 127.0.0.1 fitjanta --batch --raw -e $sql 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($out -join " ") }
    $lines = @($out | ForEach-Object { "$_" })
    if ($lines.Count -lt 1) { return @() }
    $cols = $lines[0] -split "`t"
    $rows = @()
    foreach ($line in $lines | Select-Object -Skip 1) {
        $vals = $line -split "`t"
        $o = [ordered]@{}
        for ($i = 0; $i -lt $cols.Count; $i++) { $o[$cols[$i]] = if ($vals[$i] -eq 'NULL') { '' } else { $vals[$i] } }
        $rows += [pscustomobject]$o
    }
    return $rows
}

$sections = @(
    @{ Title = "USERS  (sign up / profile / photo / location)"; Table = "users";      Sql = "SELECT id, name, email, city, xp, level, streak, IF(avatar_path IS NULL,'-','yes') AS photo, IFNULL(CONCAT(ROUND(latitude,3),',',ROUND(longitude,3)),'-') AS location, IF(IFNULL(share_location,1),'on','off') AS on_map FROM users ORDER BY id DESC LIMIT 5" },
    @{ Title = "COMMENTS  (on photo posts)";         Table = "comments";              Sql = "SELECT c.id, c.post_id AS post, u.name AS user, LEFT(c.text,40) AS comment, DATE_FORMAT(c.created_at,'%H:%i:%s') AS time FROM comments c JOIN users u ON u.id = c.user_id ORDER BY c.id DESC LIMIT 5" },
    @{ Title = "TERRITORIES  (claimed on the map)";  Table = "territories";           Sql = "SELECT t.id, t.name, u.name AS owner, t.radius AS km, ROUND(t.latitude,3) AS lat, ROUND(t.longitude,3) AS lng, DATE_FORMAT(t.claimed_at,'%d %b %H:%i') AS claimed FROM territories t LEFT JOIN users u ON u.id = t.user_id ORDER BY t.id DESC LIMIT 5" },
    @{ Title = "FOOD_LOGS  (Food AI - meals)";       Table = "food_logs";             Sql = "SELECT id, user_id AS user, meal_type AS meal, LEFT(description,34) AS description, calories AS kcal, ROUND(protein) AS protein, health_score AS score, DATE_FORMAT(created_at,'%H:%i:%s') AS time FROM food_logs ORDER BY id DESC LIMIT 5" },
    @{ Title = "FOOD_LOG_ITEMS  (each food in a meal)"; Table = "food_log_items";     Sql = "SELECT id, food_log_id AS meal_id, food_name AS food, quantity AS qty, unit, grams, calories AS kcal FROM food_log_items ORDER BY id DESC LIMIT 6" },
    @{ Title = "ACTIVITIES  (workouts)";             Table = "activities";            Sql = "SELECT id, user_id AS user, type, distance AS km, duration AS min, calories AS kcal, date FROM activities ORDER BY id DESC LIMIT 5" },
    @{ Title = "POSTS  (photo + caption)";           Table = "posts";                 Sql = "SELECT id, user_id AS user, LEFT(IFNULL(caption,''),36) AS caption, SUBSTRING_INDEX(image_path,'/',-1) AS image_file, DATE_FORMAT(created_at,'%H:%i:%s') AS time FROM posts ORDER BY id DESC LIMIT 5" },
    @{ Title = "CHALLENGE_COMPLETIONS";              Table = "challenge_completions"; Sql = "SELECT c.id, c.user_id AS user, ch.title AS challenge, c.xp_earned AS xp, DATE_FORMAT(c.completed_at,'%H:%i:%s') AS time FROM challenge_completions c JOIN challenges ch ON ch.id = c.challenge_id ORDER BY c.id DESC LIMIT 5" }
)

$lastMax = @{}
$firstRound = $true

try {
    while ($true) {
        $output = New-Object System.Collections.Generic.List[object]

        try {
            $counts = Invoke-Sql "SELECT (SELECT COUNT(*) FROM users) users, (SELECT COUNT(*) FROM food_logs) meals, (SELECT COUNT(*) FROM food_log_items) food_items, (SELECT COUNT(*) FROM activities) workouts, (SELECT COUNT(*) FROM posts) posts, (SELECT COUNT(*) FROM comments) comments, (SELECT COUNT(*) FROM territories) territories, (SELECT COUNT(*) FROM challenge_completions) challenges_done"
            $c = $counts[0]

            Clear-Host
            Write-Host ""
            Write-Host "  FITJANTA  |  LIVE MySQL MONITOR  (database: fitjanta)" -ForegroundColor White -BackgroundColor DarkMagenta
            Write-Host ("  Updated {0}  |  refresh every {1}s  |  new rows = GREEN  |  Ctrl+C to stop" -f (Get-Date -Format "HH:mm:ss"), $RefreshSeconds) -ForegroundColor DarkGray
            Write-Host ("  TOTAL ROWS ->  users: {0}  meals: {1}  food items: {2}  workouts: {3}  posts: {4}  comments: {5}  territories: {6}  challenges: {7}" -f $c.users, $c.meals, $c.food_items, $c.workouts, $c.posts, $c.comments, $c.territories, $c.challenges_done) -ForegroundColor Cyan

            foreach ($s in $sections) {
                $rows = @(Invoke-Sql $s.Sql)
                Write-Host ""
                Write-Host ("  " + $s.Title) -ForegroundColor Yellow
                if ($rows.Count -eq 0) {
                    Write-Host "    (no rows yet)" -ForegroundColor DarkGray
                    $lastMax[$s.Table] = 0
                    continue
                }
                $prev = if ($lastMax.ContainsKey($s.Table)) { $lastMax[$s.Table] } else { [int]::MaxValue }
                $lines = ($rows | Format-Table -AutoSize | Out-String -Width 220).TrimEnd() -split "`r?`n"
                # first two lines are header + dashes
                for ($i = 0; $i -lt $lines.Count; $i++) {
                    $line = "    " + $lines[$i]
                    if ($i -lt 2) { Write-Host $line -ForegroundColor Gray; continue }
                    $rowIndex = $i - 2
                    $isNew = (-not $firstRound) -and $rowIndex -lt $rows.Count -and ([int]$rows[$rowIndex].id -gt $prev)
                    if ($isNew) { Write-Host ($line + "   <-- NEW") -ForegroundColor Green }
                    else { Write-Host $line }
                }
                $lastMax[$s.Table] = ($rows | ForEach-Object { [int]$_.id } | Measure-Object -Maximum).Maximum
            }
            $firstRound = $false
        }
        catch {
            Clear-Host
            Write-Host ""
            Write-Host "  Can't read MySQL right now:" -ForegroundColor Red
            Write-Host "  $($_.Exception.Message)" -ForegroundColor Red
            Write-Host "  Is the MYSQL80 service running? (Win + R -> services.msc -> MYSQL80 -> Start)" -ForegroundColor DarkGray
        }

        Start-Sleep -Seconds $RefreshSeconds
    }
}
finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}
