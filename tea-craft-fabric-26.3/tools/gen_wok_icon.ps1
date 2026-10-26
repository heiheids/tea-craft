# 给「炒茶锅」画物品图（第三版：16x16 手绘像素画 → 放大到 64x64）
#   前两版是平滑着色的"3D 渲染"，看起来像陶碗。
#   这一版按原版物品贴图的画法：先画 16x16 的硬边像素，再最近邻放大 4 倍。
#   炒茶锅特征：很宽很浅、锅沿薄、两侧双耳、锅底小而平
# 用法： powershell -File tools\gen_wok_icon.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$outDir = Join-Path $root 'src\main\resources\assets\teacraft\textures\item'

# 16x16 像素图。'.'=透明
#   R 锅沿亮 / r 锅沿中 / I 锅内暗 / L 锅内微亮 / B 锅身 / b 锅身暗 / h 双耳
$rows = @(
    '................',
    '................',
    '................',
    '....rrrrrrrr....',
    '..rrRRRRRRRRrr..',
    '.rRIIIIIIIIIIRr.',
    'hRIIIIIIIIIIIIRh',
    'hRIILIIIIIILIIRh',
    '.rRIIIIIIIIIIRr.',
    '..rrBBBBBBBBrr..',
    '...bBBBBBBBBb...',
    '....bBBBBBBb....',
    '.....bBBBBb.....',
    '......bBBb......',
    '.......bb.......',
    '................'
)

# 注意：PowerShell 的哈希表键「不区分大小写」，'R' 和 'r' 会撞车报
# Duplicate keys are not allowed in hash literals → 必须用区分大小写的字典
$pal = New-Object 'System.Collections.Generic.Dictionary[string,System.Drawing.Color]' ([System.StringComparer]::Ordinal)
$pal['R'] = [System.Drawing.Color]::FromArgb(255, 158, 163, 173)   # 锅沿亮
$pal['r'] = [System.Drawing.Color]::FromArgb(255, 104, 109, 118)   # 锅沿中
$pal['I'] = [System.Drawing.Color]::FromArgb(255, 38, 39, 43)      # 锅内暗
$pal['L'] = [System.Drawing.Color]::FromArgb(255, 74, 76, 84)      # 锅内微亮
$pal['B'] = [System.Drawing.Color]::FromArgb(255, 108, 112, 120)   # 锅身
$pal['b'] = [System.Drawing.Color]::FromArgb(255, 68, 71, 78)      # 锅身暗
$pal['h'] = [System.Drawing.Color]::FromArgb(255, 136, 141, 151)   # 双耳

# 校验每行都是 16 字符（画歪了要立刻报错，不要静默产出错图）
for ($i = 0; $i -lt $rows.Count; $i++) {
    if ($rows[$i].Length -ne 16) { throw "row $i has $($rows[$i].Length) chars: $($rows[$i])" }
}
if ($rows.Count -ne 16) { throw "expected 16 rows, got $($rows.Count)" }

$small = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $ch = [string]$rows[$y][$x]
        if ($ch -eq '.') {
            $small.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
        } else {
            $small.SetPixel($x, $y, $pal[$ch])
        }
    }
}

# 最近邻放大 4 倍 → 64x64
$big = [System.Drawing.Bitmap]::new(64, 64, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($big)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$g.Clear([System.Drawing.Color]::FromArgb(0, 0, 0, 0))
$g.DrawImage($small, 0, 0, 64, 64)
$g.Dispose(); $small.Dispose()

if (!(Test-Path -LiteralPath $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
$out = Join-Path $outDir 'tea_wok_icon_0.png'
$big.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$big.Dispose()
"wrote $out  (16x16 像素画 → 64x64)"
