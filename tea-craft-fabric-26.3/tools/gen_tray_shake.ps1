# 生成晒盘的「摇青手持动画」：6 帧模型 + 按 using_item/use_duration 切换的物品定义
# 原理同原版弓：condition(using_item) -> range_dispatch(use_duration)
# 用法： powershell -File tools\gen_tray_shake.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$res  = Join-Path $root 'src\main\resources\assets\teacraft'

function W([string]$path, [string]$text) {
    $dir = Split-Path -Parent $path
    if (!(Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
}

$frames = 6
# 每帧模型（普通 item/generated，只是贴图不同）
for ($i = 0; $i -lt $frames; $i++) {
    $json = "{`n  `"parent`": `"minecraft:item/generated`",`n  `"textures`": {`n    `"layer0`": `"teacraft:item/tea_tray_shake_$i`"`n  }`n}`n"
    W (Join-Path $res "models\item\tea_tray_shake_$i.json") $json
}

# use_duration 的 scale：原版弓用 0.05（1 tick 记 0.05）。60 tick 的摇动共 3.0。
# 6 帧 → 每帧 10 tick = 0.5，阈值从 0.5 起。
$scale = 0.05
$entries = @()
for ($i = 1; $i -lt $frames; $i++) {
    $threshold = [math]::Round($i * 0.5, 2)
    $entries += @"
      {
        "threshold": $threshold,
        "model": { "type": "minecraft:model", "model": "teacraft:item/tea_tray_shake_$i" }
      }
"@
}
$entryBlock = ($entries -join ",`n")

$itemDef = @"
{
  "model": {
    "type": "minecraft:condition",
    "property": "minecraft:using_item",
    "on_false": {
      "type": "minecraft:model",
      "model": "teacraft:item/tea_tray"
    },
    "on_true": {
      "type": "minecraft:range_dispatch",
      "property": "minecraft:use_duration",
      "scale": $scale,
      "fallback": { "type": "minecraft:model", "model": "teacraft:item/tea_tray_shake_0" },
      "entries": [
$entryBlock
      ]
    }
  }
}
"@
W (Join-Path $res "items\tea_tray.json") $itemDef

"shake frames: $frames   物品定义已改为 using_item + use_duration 驱动"
