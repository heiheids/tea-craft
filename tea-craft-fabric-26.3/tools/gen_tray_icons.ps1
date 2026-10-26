# 生成晒盘的手持图标（按阶段）+ 物品定义（按内容切换图标 / 摇动时播放动画）
#
#   手持图标：tea_tray_icon_0..3 = 空盘/新茶/萎凋/摇青（盘面 + 80% 茶叶合成）
#   物品定义：condition(using_item)
#               on_false -> range_dispatch(custom_model_data) -> 四个图标
#               on_true  -> range_dispatch(use_duration)     -> 6 帧摇动
#
# items\tea_tray.json 由本脚本**独占**（其他脚本不要再写它）。
# 用法： powershell -File tools\gen_tray_icons.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$res  = Join-Path $root 'src\main\resources\assets\teacraft'
Add-Type -AssemblyName System.Drawing

function W([string]$path, [string]$text) {
    $dir = Split-Path -Parent $path
    if (!(Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
}

$texBlock = Join-Path $res 'textures\block'
$texItem  = Join-Path $res 'textures\item'

# ---------- 1. 合成四个手持图标 ----------
$tray = [System.Drawing.Bitmap]::new((Join-Path $texBlock 'tea_tray_top_empty.png'))
$stageTex = @($null, 'tea_tray_top_fresh', 'tea_tray_top_withered', 'tea_tray_top_shaken')
for ($s = 0; $s -lt 4; $s++) {
    $icon = [System.Drawing.Bitmap]::new(64, 64, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($icon)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.DrawImage($tray, 0, 0, 64, 64)
    if ($null -ne $stageTex[$s]) {
        $leaf = [System.Drawing.Bitmap]::new((Join-Path $texBlock ($stageTex[$s] + '.png')))
        # 与模型一致：1.6/16 = 10% 留边 → 占 80%
        $g.DrawImage($leaf, [int](64 * 0.1), [int](64 * 0.1), [int](64 * 0.8), [int](64 * 0.8))
        $leaf.Dispose()
    }
    $g.Dispose()
    $icon.Save((Join-Path $texItem "tea_tray_icon_$s.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $icon.Dispose()
}
$tray.Dispose()

# ---------- 2. 四个图标的物品模型 ----------
for ($s = 0; $s -lt 4; $s++) {
    $json = "{`n  `"parent`": `"minecraft:item/generated`",`n  `"textures`": {`n    `"layer0`": `"teacraft:item/tea_tray_icon_$s`"`n  }`n}`n"
    W (Join-Path $res "models\item\tea_tray_icon_$s.json") $json
}

# ---------- 3. 摇动 6 帧（沿用已有的帧贴图/模型） ----------
$frames = 6
$scale = 0.05
$entries = @()
for ($i = 1; $i -lt $frames; $i++) {
    $entries += @"
        {
          "threshold": $([math]::Round($i * 0.5, 2)),
          "model": { "type": "minecraft:model", "model": "teacraft:item/tea_tray_shake_$i" }
        }
"@
}

# ---------- 4. 物品定义 ----------
$iconEntries = @()
for ($s = 1; $s -lt 4; $s++) {
    $iconEntries += @"
          {
            "threshold": $s,
            "model": { "type": "minecraft:model", "model": "teacraft:item/tea_tray_icon_$s" }
          }
"@
}

$def = @"
{
  "model": {
    "type": "minecraft:condition",
    "property": "minecraft:using_item",
    "on_false": {
      "type": "minecraft:range_dispatch",
      "property": "minecraft:custom_model_data",
      "scale": 1,
      "fallback": { "type": "minecraft:model", "model": "teacraft:item/tea_tray_icon_0" },
      "entries": [
$($iconEntries -join ",`n")
      ]
    },
    "on_true": {
      "type": "minecraft:range_dispatch",
      "property": "minecraft:use_duration",
      "scale": $scale,
      "fallback": { "type": "minecraft:model", "model": "teacraft:item/tea_tray_shake_0" },
      "entries": [
$($entries -join ",`n")
      ]
    }
  }
}
"@
W (Join-Path $res 'items\tea_tray.json') $def

"icons: 4   摇动帧: $frames   物品定义：按 custom_model_data 换图标 + using_item 播动画"
