# 茶工艺 源码备份脚本
#
#  用法：
#    powershell -NoProfile -ExecutionPolicy Bypass -File tools\backup_source.ps1
#    powershell -NoProfile -ExecutionPolicy Bypass -File tools\backup_source.ps1 -Tag 改炒茶锅前
#
#  输出：D:\茶工艺备份目录\tea-craft-src-yyyyMMdd-HHmmss[-Tag].zip
#
#  设计原则：**白名单**。只打包源码与工程配置，不打包生成物。
#  反过来（黑名单排除 build/run/.gradle）容易漏，一旦漏进几个 G 的缓存就很难受。
#
#  打包范围：
#    src\                  Java 源码 + 全部资源（模型/贴图/语言/数据）
#    tools\                资源生成脚本、自检辅助、模型模板
#    茶工艺三种茶\           仅 -IncludeRefArt 时打包（55MB 参考图，不是源码）
#    art\                  美术源文件（若有）
#    gradle\  gradlew  gradlew.bat   Gradle 包装器（没它别人拉下来跑不了）
#    build.gradle  settings.gradle  gradle.properties
#    mod_context.md        交接文档（最重要的上下文）
#    .gitignore
#
#  刻意**不**打包：
#    build\  .gradle\  run\   构建产物与运行目录（体积大、可重建）
#    build-*.txt  run-*.txt   历次构建/自检日志（几百个，不是源码）

param(
    [string]$Tag = '',
    # 默认不打包「茶工艺三种茶」参考图（55 MB 原始美术参考，不是源码）。
    # 需要连参考图一起备份时加 -IncludeRefArt。
    [switch]$IncludeRefArt
)

$ErrorActionPreference = 'Stop'
$backupRoot = 'D:\茶工艺备份目录'
$projectRoot = Split-Path -Parent $PSScriptRoot

if (-not (Test-Path -LiteralPath $backupRoot)) {
    New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$safeTag = if ($Tag) { '-' + ($Tag -replace '[\\/:*?"<>|]', '_') } else { '' }
$zipPath = Join-Path $backupRoot "tea-craft-src-$stamp$safeTag.zip"

# ---- 白名单条目（相对工程根）----
$includeDirs = @('src', 'tools', 'art', 'gradle')
if ($IncludeRefArt) { $includeDirs += '茶工艺三种茶' }
$includeFiles = @(
    'build.gradle', 'settings.gradle', 'gradle.properties',
    'gradlew', 'gradlew.bat', 'mod_context.md', '.gitignore'
)

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$files = New-Object System.Collections.Generic.List[string]

foreach ($d in $includeDirs) {
    $full = Join-Path $projectRoot $d
    if (Test-Path -LiteralPath $full) {
        Get-ChildItem -LiteralPath $full -Recurse -File | ForEach-Object { $files.Add($_.FullName) }
    }
}
foreach ($f in $includeFiles) {
    $full = Join-Path $projectRoot $f
    if (Test-Path -LiteralPath $full) { $files.Add($full) }
}

# 排除常见的垃圾（跨平台编辑器残留）
$junk = @('\.DS_Store$', '\\Thumbs\.db$', '\\desktop\.ini$', '~$')
$files = $files | Where-Object {
    $p = $_
    -not ($junk | Where-Object { $p -match $_ })
}

if ($files.Count -eq 0) { throw "没有找到要备份的文件（工程根：$projectRoot）" }

# ---- 写 zip ----
$zip = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($f in $files) {
        $rel = $f.Substring($projectRoot.Length).TrimStart('\', '/')
        $entryName = $rel -replace '\\', '/'
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zip, $f, $entryName, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally {
    $zip.Dispose()
}

# ---- 校验：能打开、条目数对得上 ----
$check = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
$count = $check.Entries.Count
$check.Dispose()

$sizeKb = [math]::Round((Get-Item -LiteralPath $zipPath).Length / 1KB, 1)
"备份完成：$zipPath"
"  文件数 $count / 源文件数 $($files.Count)   体积 ${sizeKb} KB"

if ($count -ne $files.Count) {
    throw "条目数与源文件数不一致（$count vs $($files.Count)）——备份可能不完整"
}
