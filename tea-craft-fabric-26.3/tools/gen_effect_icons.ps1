# 生成三种茶效果的图标（18x18，仿原版像素画风格）
#
#  原版规格（从 26.3 的 jar 里读到的 regeneration.png）：18x18、透明背景、
#  深色描边 + 饱和填充 + 少量亮部高光。
#
#    dancong_tea  单丛茶劲 —— 琥珀茶叶 + 火苗（焙火 / 急迫）
#    black_tea    红茶暖身 —— 茶碗 + 红褐茶汤 + 热气
#    green_tea    绿茶清神 —— 绿叶 + 迅捷箭头
#
#  用法：powershell -NoProfile -ExecutionPolicy Bypass -File tools\gen_effect_icons.ps1
#
#  ⚠ PowerShell 的哈希键**不区分大小写**（'L' 与 'l' 会冲突），
#    所以这里用 Dictionary[string, int[]] + Ordinal 比较器。
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$outDir = Join-Path $root 'src\main\resources\assets\teacraft\textures\mob_effect'
if (!(Test-Path -LiteralPath $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }

$S = 18

#  调色板用「字符串规格」而不是哈希字面量：
#  PowerShell 在**解析阶段**就会拒绝大小写重复的哈希键（'L' 与 'l'），
#  而字符串里的大小写是安全的。
function New-Palette([string]$spec) {
    $d = [System.Collections.Generic.Dictionary[string, int[]]]::new(
        [System.StringComparer]::Ordinal)
    foreach ($pair in $spec.Split(';')) {
        if ([string]::IsNullOrWhiteSpace($pair)) { continue }
        $kv = $pair.Split('=')
        $ch = $kv[0].Trim()
        $rgb = $kv[1].Split(',')
        $d[$ch] = @([int]$rgb[0], [int]$rgb[1], [int]$rgb[2])
    }
    return $d
}

# ---- 单丛：琥珀茶叶 + 火苗 ----
$dancongArt = @(
    '..................'
    '..........ff......'
    '.........fFFf.....'
    '.........fFFf.....'
    '....lll...fff.....'
    '...lLLLl..........'
    '..lLLLLLl.........'
    '..lLvLLLLl........'
    '..lLvLLLLl........'
    '..lLLvLLLl........'
    '...lLLvLLl........'
    '....lLLLLl........'
    '.....lLLl.........'
    '......ll..........'
    '..................'
    '..................'
    '..................'
    '..................'
)
#                         描边     叶身      叶脉高光   火苗描边  火苗填充
$dancongPal = New-Palette 'l=90,62,14;L=217,160,60;v=240,206,132;f=138,59,0;F=255,154,60'

# ---- 红茶：茶碗 + 茶汤 + 热气 ----
$blackArt = @(
    '..................'
    '..................'
    '..................'
    '..................'
    '..................'
    '..oooooooooooo....'
    '..owwwwwwwwwwo....'
    '..oRrRRRRRRrRo....'
    '..oRRRRRRRRRRo....'
    '...oRRRRRRRRo.....'
    '....oRRRRRRo......'
    '.....oRRRRo.......'
    '......oooo........'
    '....oooooooo......'
    '..................'
    '..................'
    '..................'
    '..................'
)
#                       描边      茶汤      高光       热气外     热气内
#                       描边      茶汤      高光       碗口高光
$blackPal = New-Palette 'o=58,26,14;R=142,58,30;r=192,106,69;w=198,120,80'

# ---- 绿茶：绿叶 + 迅捷箭头 ----
$greenArt = @(
    '..................'
    '....llllll........'
    '...lGGGGGGl.......'
    '..lGGvGGGGGl......'
    '..lGGvGGGGGl......'
    '..lGGvGGGGGl......'
    '...lGGvGGGGl......'
    '....lGGvGGl.......'
    '.....lGGGl........'
    '......lll.........'
    '..................'
    '...cc..cc.........'
    '....cc..cc........'
    '...cc..cc.........'
    '..................'
    '..................'
    '..................'
    '..................'
)
#                       描边     叶身        叶脉高光    迅捷箭头
$greenPal = New-Palette 'l=30,59,18;G=76,139,43;v=150,205,110;c=214,240,255'

function Write-Icon([string[]]$art, $pal, [string]$name) {
    if ($art.Count -ne $S) { throw "$name 的行数应为 $S，实际 $($art.Count)" }
    $bmp = [System.Drawing.Bitmap]::new($S, $S, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $S; $y++) {
        $row = [string]$art[$y]
        if ($row.Length -ne $S) { throw "$name 第 $y 行长度应为 $S，实际 $($row.Length)" }
        for ($x = 0; $x -lt $S; $x++) {
            $ch = [string]$row[$x]
            if ($ch -eq '.') { continue }
            if (-not $pal.ContainsKey($ch)) { throw "$name 出现未定义字符 '$ch'" }
            $c = $pal[$ch]
            $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $c[0], $c[1], $c[2]))
        }
    }
    $path = Join-Path $outDir "$name.png"
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    "  写出 $name.png（18x18）"
}

Write-Icon $dancongArt $dancongPal 'dancong_tea'
Write-Icon $blackArt   $blackPal   'black_tea'
Write-Icon $greenArt   $greenPal   'green_tea'
"效果图标生成完毕 -> $outDir"
