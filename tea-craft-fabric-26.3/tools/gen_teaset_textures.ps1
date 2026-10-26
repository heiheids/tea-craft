# 茶盘（盖碗 + 三个方杯）的贴图生成
#
#  规格：每张 16x16，像素风，参照用户给的像素参考图配色：
#    木盘：深棕、四角有装饰块
#    盘底：更暗的内凹面
#    白瓷：近白 + 一条蓝线（参考图里的青花线）
#    茶叶：褐色的碎叶堆
#
#  为什么每张单独 16x16：方块模型里不写 uv 时，默认把整张图铺满每个面。
#  一张图一个材质、不写 uv，就不用手工算 uv 坐标了，改起来也不容易错。
#
#  用法：powershell -NoProfile -ExecutionPolicy Bypass -File tools\gen_teaset_textures.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$outDir = Join-Path $root 'src\main\resources\assets\teacraft\textures\block'
if (!(Test-Path -LiteralPath $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }

$S = 16

function New-Bitmap { [System.Drawing.Bitmap]::new($S, $S, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb) }

# 确定性伪随机，保证每次生成同一张图（不然每次构建贴图都会变）
function Rnd([int]$x, [int]$y, [int]$salt) {
    $h = ($x * 73856093) -bxor ($y * 19349663) -bxor ($salt * 83492791)
    return [math]::Abs($h) % 1000
}

function Save($bmp, [string]$name) {
    $p = Join-Path $outDir "$name.png"
    $bmp.Save($p, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    "  $name.png"
}

# ---------------- 木盘外框 ----------------
$wood = New-Bitmap
for ($y = 0; $y -lt $S; $y++) {
    for ($x = 0; $x -lt $S; $x++) {
        $n = Rnd $x $y 1
        # 木纹：沿 x 方向的深浅条带
        $r = 74; $g = 46; $b = 30
        if (($y % 4) -eq 1) { $r -= 10; $g -= 7; $b -= 5 }
        if ($n -lt 70) { $r += 9; $g += 6; $b += 4 }
        elseif ($n -gt 930) { $r -= 8; $g -= 5; $b -= 4 }
        # 四角装饰块（参考图角落有亮色方块）
        $corner = (($x -lt 4) -or ($x -ge 12)) -and (($y -lt 4) -or ($y -ge 12))
        if ($corner) {
            $d = [math]::Max([math]::Abs(($x % 4) - 1), [math]::Abs(($y % 4) - 1))
            if ($d -le 1) { $r = 122; $g = 74; $b = 46 }
        }
        $wood.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b))
    }
}

# ---------------- 盘底（内凹面，更暗）----------------
$floor = New-Bitmap
for ($y = 0; $y -lt $S; $y++) {
    for ($x = 0; $x -lt $S; $x++) {
        $n = Rnd $x $y 2
        $r = 46; $g = 27; $b = 16
        if ($n -lt 90) { $r += 8; $g += 5; $b += 3 }
        elseif ($n -gt 940) { $r -= 6; $g -= 4; $b -= 3 }
        # 零星木刺/划痕
        if ($n -gt 985) { $r = 90; $g = 56; $b = 34 }
        $floor.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b))
    }
}

# ---------------- 白瓷侧面：素瓷（不带青花线）----------------
$porcPlain = New-Bitmap
for ($y = 0; $y -lt $S; $y++) {
    for ($x = 0; $x -lt $S; $x++) {
        # 上亮下暗，做出圆柱感
        $t = $y / 15.0
        $v = 247 - [int](16 * $t)
        $r = $v; $g = $v + 2; $b = $v + 5
        if ($b -gt 255) { $b = 255 }
        if ($y -ge 14) { $r = 208; $g = 216; $b = 226 }
        $porcPlain.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b))
    }
}

# ---------------- 白瓷内壁（比外面暗一点，做出"凹陷"的深度感）----------------
$porcInner = New-Bitmap
for ($y = 0; $y -lt $S; $y++) {
    for ($x = 0; $x -lt $S; $x++) {
        # 上暗下更暗：杯内越深越暗
        $t = $y / 15.0
        $v = 214 - [int](34 * $t)
        $r = $v; $g = $v + 3; $b = $v + 9
        if ($b -gt 255) { $b = 255 }
        $porcInner.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b))
    }
}
# ---------------- 青花环专用贴图 ----------------
#  用在很薄（0.5px）的环带上。面被压得极扁，
#  所以整张图必须几乎全是蓝色，否则玩家看不到线。
$porcBand = New-Bitmap
for ($y = 0; $y -lt $S; $y++) {
    for ($x = 0; $x -lt $S; $x++) {
        # 只在中间几行画细带，两侧留白瓷 —— 否则整块壶身全蓝
        if ($y -ge 6 -and $y -le 7) { $r = 58; $g = 100; $b = 170 }
        elseif ($y -eq 8) { $r = 96; $g = 138; $b = 196 }
        else { $r = 244; $g = 247; $b = 252 }
        $porcBand.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b))
    }
}
# ---------------- 壶身青花纹（面高 4.8px，要更宽的带才看得见）----------------
#  方块里的壶身面高 4.8px，若沿用 band(3/16) 只有 0.9px —— 太小被抗锯齿吃掉。
#  这里单独做一张 6/16 宽的带，落在 4.8px 面上约 1.8px，肉眼清楚。
$porcBodyBand = New-Bitmap
for ($y = 0; $y -lt $S; $y++) {
    for ($x = 0; $x -lt $S; $x++) {
        if ($y -ge 5 -and $y -le 9) { $r = 58; $g = 100; $b = 170 }
        elseif ($y -eq 10) { $r = 96; $g = 138; $b = 196 }
        else { $r = 244; $g = 247; $b = 252 }
        $porcBodyBand.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b))
    }
}
# ---------------- 白瓷口沿（带一道青花线）----------------
$porcSide = New-Bitmap
for ($y = 0; $y -lt $S; $y++) {
    for ($x = 0; $x -lt $S; $x++) {
        $t = $y / 15.0
        $v = 247 - [int](16 * $t)
        $r = $v; $g = $v + 2; $b = $v + 5
        if ($b -gt 255) { $b = 255 }
        # 青花线：靠上的一道蓝带，像瓷器的口沿纹
        if ($y -ge 2 -and $y -le 5) { $r = 54; $g = 98; $b = 165 }
        elseif ($y -eq 6) { $r = 120; $g = 158; $b = 205 }
        if ($y -ge 14) { $r = 208; $g = 216; $b = 226 }
        $porcSide.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b))
    }
}

# ---------------- 白瓷顶面（素白）----------------
$porcTop = New-Bitmap
for ($y = 0; $y -lt $S; $y++) {
    for ($x = 0; $x -lt $S; $x++) {
        $r = 250; $g = 252; $b = 255
        # 外沿一圈略暗，像瓷器口沿的转折
        if ($x -lt 1 -or $y -lt 1 -or $x -ge 15 -or $y -ge 15) { $r = 228; $g = 234; $b = 243 }
        # 再往里一圈更淡的阴影，做出"碗口下凹"的感觉
        elseif ($x -lt 2 -or $y -lt 2 -or $x -ge 14 -or $y -ge 14) { $r = 242; $g = 246; $b = 251 }
        $porcTop.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b))
    }
}

# ---------------- 茶叶堆 ----------------
$leaf = New-Bitmap
for ($y = 0; $y -lt $S; $y++) {
    for ($x = 0; $x -lt $S; $x++) {
        $n = Rnd $x $y 4
        $r = 139; $g = 94; $b = 52
        if ($n -lt 260) { $r = 107; $g = 69; $b = 38 }
        elseif ($n -gt 780) { $r = 168; $g = 118; $b = 66 }
        # 叶片轮廓感：随机暗点
        if ($n -gt 960) { $r = 78; $g = 50; $b = 28 }
        $leaf.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b))
    }
}

# ---------------- 茶汤（琥珀色）----------------
$tea = New-Bitmap
for ($y = 0; $y -lt $S; $y++) {
    for ($x = 0; $x -lt $S; $x++) {
        $n = Rnd $x $y 5
        # 上浅下深，像液面反光
        $tt = $y / 15.0
        # 琥珀/茶褐而不是橙：红低、蓝更低，整体压暗
        $r = 150 - [int](30 * $tt); $g = 96 - [int](30 * $tt); $b = 40 - [int](16 * $tt)
        if ($n -lt 180) { $r += 16; $g += 12; $b += 6 }
        elseif ($n -gt 860) { $r -= 14; $g -= 10; $b -= 6 }
        if ($r -lt 0) { $r = 0 }; if ($g -lt 0) { $g = 0 }; if ($b -lt 0) { $b = 0 }
        $tea.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b))
    }
}
# ---------------- 提梁（深棕木/藤）----------------
$handle = New-Bitmap
for ($y = 0; $y -lt $S; $y++) {
    for ($x = 0; $x -lt $S; $x++) {
        $n = Rnd $x $y 6
        # 深棕，带一点木纹
        $r = 92; $g = 58; $b = 34
        if (($x % 5) -eq 2) { $r -= 8; $g -= 6; $b -= 4 }
        if ($n -lt 200) { $r += 14; $g += 9; $b += 5 }
        elseif ($n -gt 880) { $r -= 10; $g -= 7; $b -= 4 }
        $handle.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b))
    }
}

"茶盘贴图："
Save $wood     'tea_set_wood'
Save $floor    'tea_set_floor'
Save $porcPlain 'tea_set_porcelain_plain'
Save $porcInner 'tea_set_porcelain_inner'
Save $porcSide 'tea_set_porcelain_side'
Save $porcBand 'tea_set_porcelain_band'
Save $porcBodyBand 'tea_set_porcelain_body_band'
Save $porcTop  'tea_set_porcelain_top'
Save $leaf     'tea_set_leaf'
Save $tea      'tea_set_tea'
Save $handle   'tea_set_handle'
"完成 -> $outDir"
