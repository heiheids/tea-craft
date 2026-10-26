# 生成碳焙筐界面的 GUI 贴图（176x166，和原版熔炉同尺寸）
#   为什么用贴图而不是用 fill 画：
#     之前用 fill 逐个画槽位底，坐标对不上就出现「图标偏移」。
#     把槽位底直接画进贴图、再整张贴上去，槽位坐标只要和 Slot.x/y 一致即可，
#     不存在"画的格子和物品位置差几像素"的问题。
#
#   布局（与原版熔炉一致）：
#     三个机器槽（照抄原版熔炉）：输入 (56,17) / 燃料 (56,53) / 成品 (116,35)
#     玩家背包 (8,84) 3x9，快捷栏 (8,142)
#     槽位尺寸 18x18，槽底在贴图里是 x-1 ~ x+17（含 1px 边框）
#
# 用法： powershell -File tools\gen_stove_gui.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$outDir = Join-Path $root 'src\main\resources\assets\teacraft\textures\gui'

$W = 176
$H = 166

$bmp = [System.Drawing.Bitmap]::new($W, $H, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
$g.Clear([System.Drawing.Color]::FromArgb(0, 0, 0, 0))

$bg      = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 198, 198, 198))
$light   = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 255, 255, 255))
$dark    = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 85, 85, 85))
$slotBg  = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 139, 139, 139))
$slotSh  = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 55, 55, 55))

# ---- 主面板 ----
$g.FillRectangle($bg, 0, 0, $W, $H)
# 立体边
$g.FillRectangle($light, 0, 0, $W, 1)
$g.FillRectangle($light, 0, 0, 1, $H)
$g.FillRectangle($dark, 0, $H - 1, $W, 1)
$g.FillRectangle($dark, $W - 1, 0, 1, $H)

# ---- 槽位底（含 1px 阴影）----
function Draw-Slot([int]$sx, [int]$sy) {
    # 原版槽位底是 18x18，画在 (sx-1, sy-1)，比 16x16 的物品图标四周各多 1px 边框。
    # 画在 (sx, sy) 会让格子整体偏右下 1px —— 看起来就是"物品偏左上"。
    $g.FillRectangle($slotBg, ($sx - 1), ($sy - 1), 18, 18)
    $g.FillRectangle($slotSh, ($sx - 1), ($sy - 1), 18, 1)   # 上边阴影
    $g.FillRectangle($slotSh, ($sx - 1), ($sy - 1), 1, 18)   # 左边阴影
}

# 三个机器槽
Draw-Slot 56 17
Draw-Slot 56 53
Draw-Slot 116 35

# 玩家背包 3x9
for ($row = 0; $row -lt 3; $row++) {
    for ($col = 0; $col -lt 9; $col++) {
        Draw-Slot (8 + $col * 18) (84 + $row * 18)
    }
}
# 快捷栏
for ($col = 0; $col -lt 9; $col++) {
    Draw-Slot (8 + $col * 18) 142
}

# ---- 火焰底（燃料槽左侧）----
$flameBg = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 58, 58, 58))
# 火焰：燃料槽(56,53)上方，熔炉贴图里就是这块
$g.FillRectangle($flameBg, 56, 36, 14, 14)

# ---- 箭头底（待焙 → 成品之间）----
$arrowBg = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 139, 139, 139))
# 箭头：输入(56,17)与输出(116,35)之间
$g.FillRectangle($arrowBg, 79, 34, 24, 16)

$g.Dispose()

if (!(Test-Path -LiteralPath $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
$out = Join-Path $outDir 'tea_charcoal_stove.png'
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
"wrote $out  (${W}x${H}  熔炉风格 GUI)"
