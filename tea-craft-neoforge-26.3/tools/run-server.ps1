# TeaCraft 26.3 NeoForge dev server launcher (PowerShell)
#
# Why not `gradlew runServer`:
#   ModDevGradle's runServer/prepareServerRun needs to download
#   `neoforge-26.3.0.67-beta-moddev-config.json`, which is unreachable and not
#   in the offline cache. So we assemble the classpath ourselves (exported
#   offline by the gradle task `printRuntimeClasspath`).
#
# Usage:
#   .\tools\run-server.ps1                  # start, watch 300s
#   .\tools\run-server.ps1 -WatchSec 120    # shorter

param(
    [int]$WatchSec = 300,
    [switch]$Keep
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$root = Split-Path $PSScriptRoot -Parent
$ws = 'D:\工程-泡世界'
$cache = Join-Path $ws 'mcmod\.gradle-home\caches\modules-2\files-2.1'
$java = Join-Path $ws 'mcmod\tools\jdk25\bin\java.exe'
$runDir = Join-Path $root 'run-server'
$cpFile = Join-Path $root 'build\runtime-classpath.txt'

if (-not (Test-Path $cpFile)) {
    throw "Missing classpath file. Run: gradlew.bat printRuntimeClasspath --offline"
}

# ---- 1. read userdev config.json: main / jvmArgs / props ----
$userdev = Get-ChildItem $cache -Recurse -Filter 'neoforge-26.3.0.67-beta-userdev.jar' |
        Select-Object -First 1
if (-not $userdev) { throw 'cannot find neoforge-26.3.0.67-beta-userdev.jar in cache' }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($userdev.FullName)
$entry = $zip.Entries | Where-Object { $_.FullName -eq 'config.json' }
if (-not $entry) { $zip.Dispose(); throw 'userdev has no config.json' }
$sr = New-Object System.IO.StreamReader($entry.Open())
$cfg = $sr.ReadToEnd() | ConvertFrom-Json
$sr.Close()
$zip.Dispose()

$run = $cfg.runs.server

# ---- 2. run directory ----
New-Item -ItemType Directory -Path $runDir -Force | Out-Null
Set-Content -Path (Join-Path $runDir 'eula.txt') -Value 'eula=true' -Encoding ascii
$props = Join-Path $runDir 'server.properties'
if (-not (Test-Path $props)) {
    @(
        'level-name=world'
        'online-mode=false'
        'gamemode=creative'
        'spawn-protection=0'
        'server-port=25568'
        'motd=TeaCraft nf-26.3 dev server'
        'max-players=2'
        'view-distance=6'
        'sync-chunk-writes=false'
        'enable-rcon=false'
    ) | Set-Content -Path $props -Encoding ascii
}

# ---- 3. build command line ----
$cp = (Get-Content $cpFile -Raw).Trim()
$classes = Join-Path $root 'build\classes\java\main'
$resources = Join-Path $root 'build\resources\main'
$modFolders = "teacraft%%$classes" + [IO.Path]::PathSeparator + "teacraft%%$resources"

$argsList = New-Object System.Collections.Generic.List[string]
foreach ($a in $run.jvmArgs) { $argsList.Add($a) }
$argsList.Add('-cp'); $argsList.Add($cp)
$argsList.Add("-Dfml.modFolders=$modFolders")
$argsList.Add('-Dfml.earlyprogresswindow=false')
foreach ($p in $run.props.PSObject.Properties) {
    $argsList.Add("-D$($p.Name)=$($p.Value)")
}
$argsList.Add($run.main)
$argsList.Add('--nogui')

# ---- 4. launch ----
$logFile = Join-Path $runDir 'console.log'
$errFile = Join-Path $runDir 'console.err.log'
if (Test-Path $logFile) { Remove-Item $logFile -Force }
if (Test-Path $errFile) { Remove-Item $errFile -Force }

Write-Host "[run-server] starting... log: $logFile" -ForegroundColor Cyan
$p = Start-Process -FilePath $java -ArgumentList $argsList -WorkingDirectory $runDir `
        -RedirectStandardOutput $logFile -RedirectStandardError $errFile `
        -PassThru -WindowStyle Hidden

# ---- 5. watch ----
$deadline = (Get-Date).AddSeconds($WatchSec)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    if ($p.HasExited) { break }
    if (Test-Path $logFile) {
        $head = Get-Content $logFile -Raw -ErrorAction SilentlyContinue
        if ($head -and $head -match 'Done \(') { break }
    }
}

# ---- 6. report ----
Start-Sleep -Seconds 2
$full = if (Test-Path $logFile) { Get-Content $logFile -Raw } else { '' }
$errText = if (Test-Path $errFile) { Get-Content $errFile -Raw } else { '' }

Write-Host ''
Write-Host '===== key log lines =====' -ForegroundColor Cyan
($full -split "`r?`n") | Where-Object {
    $_ -match '\[teacraft\]|Done \(|Loading \d+ mods|ERROR|Exception|Caused by'
} | Select-Object -First 60 | ForEach-Object { Write-Host "  $_" }

if ($errText) {
    Write-Host ''
    Write-Host '===== stderr =====' -ForegroundColor Yellow
    ($errText -split "`r?`n") | Select-Object -First 30 | ForEach-Object { Write-Host "  $_" }
}

Write-Host ''
if ($full -match 'Done \(') {
    Write-Host '  [OK] server started (Done)' -ForegroundColor Green
} elseif ($p.HasExited) {
    Write-Host "  [FAIL] process exited, code=$($p.ExitCode)" -ForegroundColor Red
} else {
    Write-Host '  [WARN] no Done within watch window' -ForegroundColor Yellow
}

if (-not $Keep) {
    if (-not $p.HasExited) {
        Write-Host '  stopping server...' -ForegroundColor Yellow
        $p.Kill()
    }
}
