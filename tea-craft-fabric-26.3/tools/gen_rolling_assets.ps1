# 生成揉茶匾/杀青所需的贴图（64x64，与现有晒盘贴图同尺寸）
#   揉茶匾.png  -> tea_rolling_tray_top_empty.png / tea_rolling_tray_side.png
#   发酵.png     -> tea_rolling_tray_ferment.png（白布不能被抠掉，所以用四角 flood fill 而不是"去白"）
#   摇青贴图变暗 -> tea_tray_top_killgreen.png / tea_rolling_tray_tea.png
# 用法： powershell -File tools\gen_rolling_assets.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$srcDir = Join-Path $root '茶工艺三种茶'
$outDir = Join-Path $root 'src\main\resources\assets\teacraft\textures\block'
$N = 64

function Load-Scaled([string]$path, [int]$n) {
    $src = [System.Drawing.Bitmap]::new($path)
    $dst = [System.Drawing.Bitmap]::new($n, $n)
    $g = [System.Drawing.Graphics]::FromImage($dst)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($src, 0, 0, $n, $n)
    $g.Dispose(); $src.Dispose()
    return $dst
}

# 用四角 flood fill 去掉白底：只删和边缘连通的近白像素，内部的白布/高光保留
function Remove-Background([System.Drawing.Bitmap]$bmp) {
    $w = $bmp.Width; $h = $bmp.Height
    $seen = New-Object 'bool[]' ($w * $h)
    $stack = New-Object System.Collections.Stack
    $x1 = $w - 1; $y1 = $h - 1
    # PowerShell 会把 @(0,$h-1) 解析成「数组(0,$h) 减 1」，必须预计算
    $c0 = New-Object 'int[]' 2; $c0[0] = 0;  $c0[1] = 0
    $c1 = New-Object 'int[]' 2; $c1[0] = $x1; $c1[1] = 0
    $c2 = New-Object 'int[]' 2; $c2[0] = 0;  $c2[1] = $y1
    $c3 = New-Object 'int[]' 2; $c3[0] = $x1; $c3[1] = $y1
    $stack.Push($c0); $stack.Push($c1); $stack.Push($c2); $stack.Push($c3)
    while ($stack.Count -gt 0) {
        $p = $stack.Pop()
        $x = [int]$p[0]; $y = [int]$p[1]
        if ($x -lt 0 -or $y -lt 0 -or $x -ge $w -or $y -ge $h) { continue }
        $idx = $y * $w + $x
        if ($seen[$idx]) { continue }
        $c = $bmp.GetPixel($x, $y)
        if ($c.A -lt 8) { $seen[$idx] = $true; continue }
        if ($c.R -lt 232 -or $c.G -lt 232 -or $c.B -lt 232) { continue }   # 不是背景，停
        $seen[$idx] = $true
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, $c.R, $c.G, $c.B))
        $nx1 = $x + 1; $nx2 = $x - 1; $ny1 = $y + 1; $ny2 = $y - 1
        $p0 = New-Object 'int[]' 2; $p0[0] = $nx1; $p0[1] = $y
        $p1 = New-Object 'int[]' 2; $p1[0] = $nx2; $p1[1] = $y
        $p2 = New-Object 'int[]' 2; $p2[0] = $x;   $p2[1] = $ny1
        $p3 = New-Object 'int[]' 2; $p3[0] = $x;   $p3[1] = $ny2
        $stack.Push($p0); $stack.Push($p1); $stack.Push($p2); $stack.Push($p3)
    }
}

# 已经带 alpha 的图：按 alpha 求包围盒裁切，不碰像素本身
function Import-SquareNoCut([string]$path) {
    $src = [System.Drawing.Bitmap]::new($path)
    $minX = $src.Width; $minY = $src.Height; $maxX = -1; $maxY = -1
    for ($y = 0; $y -lt $src.Height; $y++) {
        for ($x = 0; $x -lt $src.Width; $x++) {
            if ($src.GetPixel($x, $y).A -le 8) { continue }
            if ($x -lt $minX) { $minX = $x }
            if ($x -gt $maxX) { $maxX = $x }
            if ($y -lt $minY) { $minY = $y }
            if ($y -gt $maxY) { $maxY = $y }
        }
    }
    if ($maxX -lt 0) { $src.Dispose(); throw "no opaque pixel in $path" }

    $rw = $maxX - $minX + 1; $rh = $maxY - $minY + 1
    $side = [Math]::Max($rw, $rh)
    $rx = $minX - [int](($side - $rw) / 2); $ry = $minY - [int](($side - $rh) / 2)
    if ($rx -lt 0) { $rx = 0 }
    if ($ry -lt 0) { $ry = 0 }
    if (($rx + $side) -gt $src.Width)  { $side = $src.Width - $rx }
    if (($ry + $side) -gt $src.Height) { $side = $src.Height - $ry }

    $dst = [System.Drawing.Bitmap]::new($N, $N)
    $g = [System.Drawing.Graphics]::FromImage($dst)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::FromArgb(0, 0, 0, 0))
    $rectSrc = [System.Drawing.Rectangle]::new($rx, $ry, $side, $side)
    $rectDst = [System.Drawing.Rectangle]::new(0, 0, $N, $N)
    $g.DrawImage($src, $rectDst, $rectSrc, [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose(); $src.Dispose()
    return $dst
}

# 在 64x64 的探针图上找非白像素的包围盒，再按比例回到原图裁切
function Import-Square([string]$path) {
    $probe = Load-Scaled $path $N
    $minX = $N; $minY = $N; $maxX = -1; $maxY = -1
    for ($y = 0; $y -lt $N; $y++) {
        for ($x = 0; $x -lt $N; $x++) {
            $c = $probe.GetPixel($x, $y)
            if ($c.R -lt 232 -or $c.G -lt 232 -or $c.B -lt 232) {
                if ($x -lt $minX) { $minX = $x }
                if ($x -gt $maxX) { $maxX = $x }
                if ($y -lt $minY) { $minY = $y }
                if ($y -gt $maxY) { $maxY = $y }
            }
        }
    }
    $probe.Dispose()
    if ($maxX -lt 0) { throw "no content in $path" }

    $src = [System.Drawing.Bitmap]::new($path)
    $sx = $src.Width / $N; $sy = $src.Height / $N
    $rx = [int]($minX * $sx); $ry = [int]($minY * $sy)
    $rw = [int](($maxX - $minX + 1) * $sx); $rh = [int](($maxY - $minY + 1) * $sy)
    $side = [Math]::Max($rw, $rh)
    $rx -= [int](($side - $rw) / 2); $ry -= [int](($side - $rh) / 2)
    if ($rx -lt 0) { $rx = 0 }
    if ($ry -lt 0) { $ry = 0 }
    if ($rx + $side -gt $src.Width) { $side = $src.Width - $rx }
    if ($ry + $side -gt $src.Height) { $side = $src.Height - $ry }

    $dst = [System.Drawing.Bitmap]::new($N, $N)
    $g = [System.Drawing.Graphics]::FromImage($dst)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $rectSrc = [System.Drawing.Rectangle]::new($rx, $ry, $side, $side)
    $rectDst = [System.Drawing.Rectangle]::new(0, 0, $N, $N)
    $g.DrawImage($src, $rectDst, $rectSrc, [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose(); $src.Dispose()
    Remove-Background $dst
    return $dst
}

function Save([System.Drawing.Bitmap]$bmp, [string]$name) {
    $p = Join-Path $outDir $name
    $bmp.Save($p, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    "  $name"
}

function Tint([System.Drawing.Bitmap]$srcBmp, [double]$mul, [double]$gBoost, [double]$bMul, [string]$name) {
    $b = [System.Drawing.Bitmap]::new($srcBmp.Width, $srcBmp.Height)
    for ($y = 0; $y -lt $b.Height; $y++) {
        for ($x = 0; $x -lt $b.Width; $x++) {
            $c = $srcBmp.GetPixel($x, $y)
            if ($c.A -lt 8) { $b.SetPixel($x, $y, $c); continue }
            $r = [Math]::Min(255, [int]($c.R * $mul))
            $g = [Math]::Min(255, [int]($c.G * $mul * $gBoost))
            $bb = [Math]::Min(255, [int]($c.B * $mul * $bMul))
            $b.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $r, $g, $bb))
        }
    }
    Save $b $name
}

# ---- 揉茶匾本体 ----
$rolling = Import-Square (Join-Path $srcDir '揉茶匾.png')
Save $rolling 'tea_rolling_tray_top_empty.png'

# 侧面：取边框平均色，加竖向纹理
$rimSumR = 0; $rimSumG = 0; $rimSumB = 0; $rimN = 0
$rb = [System.Drawing.Bitmap]::new((Join-Path $outDir 'tea_rolling_tray_top_empty.png'))
for ($y = 0; $y -lt $rb.Height; $y++) {
    for ($x = 0; $x -lt $rb.Width; $x++) {
        $edge = ($x -lt 5 -or $x -ge $rb.Width - 5 -or $y -lt 5 -or $y -ge $rb.Height - 5)
        if (-not $edge) { continue }
        $c = $rb.GetPixel($x, $y)
        if ($c.A -lt 200) { continue }
        $rimSumR += $c.R; $rimSumG += $c.G; $rimSumB += $c.B; $rimN++
    }
}
$rb.Dispose()
if ($rimN -eq 0) { $rimN = 1 }
$rimR = [int]($rimSumR / $rimN); $rimG = [int]($rimSumG / $rimN); $rimB = [int]($rimSumB / $rimN)

$side = [System.Drawing.Bitmap]::new($N, $N)
for ($y = 0; $y -lt $N; $y++) {
    for ($x = 0; $x -lt $N; $x++) {
        # 竖向细纹 + 上下压暗，看起来像竹条侧面
        $stripe = if ((($x / 4) % 2) -lt 1) { 1.0 } else { 0.86 }
        $fade = 1.0
        if ($y -lt 4) { $fade = 0.72 }
        if ($y -ge ($N - 4)) { $fade = 0.78 }
        $r = [Math]::Min(255, [int]($rimR * $stripe * $fade))
        $g = [Math]::Min(255, [int]($rimG * $stripe * $fade))
        $b2 = [Math]::Min(255, [int]($rimB * $stripe * $fade))
        $side.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b2))
    }
}
Save $side 'tea_rolling_tray_side.png'

# ---- 茶叶堆（用摇青贴图压暗成"杀青后的茶青"） ----
$shaken = [System.Drawing.Bitmap]::new((Join-Path $outDir 'tea_tray_top_shaken.png'))
Tint $shaken 0.72 1.05 0.72 'tea_rolling_tray_tea.png'
Tint $shaken 0.84 1.02 0.84 'tea_tray_top_killgreen.png'
$shaken.Dispose()

# ---- 发酵：白布包裹的茶 ----
#   用户另给了一张「发酵-透明背景.png」，本身已经抠好了，不要再走 flood fill
#   （再跑一次抠图反而会把布上的浅色高光啃掉）
$ferment = Import-SquareNoCut (Join-Path $srcDir '发酵-透明背景.png')
Save $ferment 'tea_rolling_tray_ferment.png'

# 发酵「完成」态：同一块布，但里面的茶氧化得更深（红褐），用来和刚开始发酵区分
$done = [System.Drawing.Bitmap]::new((Join-Path $outDir 'tea_rolling_tray_ferment.png'))
for ($y = 0; $y -lt $done.Height; $y++) {
    for ($x = 0; $x -lt $done.Width; $x++) {
        $c = $done.GetPixel($x, $y)
        if ($c.A -lt 8) { continue }
        # 只压暗偏绿的像素（茶叶），白布几乎不受影响
        $greenish = ($c.G -gt $c.R) -or (($c.R -lt 150) -and ($c.G -lt 150))
        if (-not $greenish) { continue }
        $r = [Math]::Min(255, [int]($c.R * 1.15 + 18))
        $g = [Math]::Min(255, [int]($c.G * 0.80))
        $b2 = [Math]::Min(255, [int]($c.B * 0.70))
        $done.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $r, $g, $b2))
    }
}
Save $done 'tea_rolling_tray_ferment_done.png'

"textures done -> $outDir"
