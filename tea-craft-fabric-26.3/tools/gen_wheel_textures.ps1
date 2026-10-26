# 生成拉坯机贴图（按参考图配色）
Add-Type -AssemblyName System.Drawing
$dir = 'D:\工程-茶工艺\src\main\resources\assets\teacraft\textures\block'

function Clamp255([int]$v) {
    if ($v -lt 0) { return 0 }
    if ($v -gt 255) { return 255 }
    return $v
}

function Noise([int]$x, [int]$y, [int]$seed) {
    $h = (($x * 374761393) + ($y * 668265263) + ($seed * 104729)) -band 0x7FFFFFFF
    $h = (($h -bxor ($h -shr 13)) * 1274126177) -band 0x7FFFFFFF
    return ($h % 1000) / 1000.0
}

function Save-Bmp($bmp, $name) {
    $path = Join-Path $dir $name
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Output ("  OK  " + $name)
}

# ---------- 1. 木桌面：暖褐色，横向木纹 ----------
$bmp = New-Object System.Drawing.Bitmap(16, 16)
for ($y = 0; $y -lt 16; $y++) {
    $grain = Noise 0 $y 37
    for ($x = 0; $x -lt 16; $x++) {
        $n = Noise $x $y 31
        $r = 176 + [int]($grain * 30) - 15
        $g = 118 + [int]($grain * 24) - 12
        $b = 68 + [int]($grain * 18) - 9
        $j = [int](($n - 0.5) * 12)
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, (Clamp255 ($r + $j)), (Clamp255 ($g + $j)), (Clamp255 ($b + $j))))
    }
}
Save-Bmp $bmp 'potter_wheel_wood_top.png'

# ---------- 2. 木侧面：略深，竖向木纹 ----------
$bmp = New-Object System.Drawing.Bitmap(16, 16)
for ($x = 0; $x -lt 16; $x++) {
    $grain = Noise $x 0 43
    for ($y = 0; $y -lt 16; $y++) {
        $n = Noise $x $y 41
        $r = 140 + [int]($grain * 26) - 13
        $g = 90 + [int]($grain * 20) - 10
        $b = 50 + [int]($grain * 16) - 8
        $j = [int](($n - 0.5) * 10)
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, (Clamp255 ($r + $j)), (Clamp255 ($g + $j)), (Clamp255 ($b + $j))))
    }
}
Save-Bmp $bmp 'potter_wheel_wood_side.png'

# ---------- 3. 石转盘顶面：青灰，带一圈圈研磨痕 ----------
$bmp = New-Object System.Drawing.Bitmap(16, 16)
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $n = Noise $x $y 47
        $r = 132 + [int]($n * 22) - 11
        $g = 138 + [int]($n * 22) - 11
        $b = 128 + [int]($n * 20) - 10
        # 深色矿物颗粒
        if ($n -lt 0.07) { $r = $r - 20; $g = $g - 20; $b = $b - 18 }
        if ($n -gt 0.95) { $r = $r + 12; $g = $g + 12; $b = $b + 12 }
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, (Clamp255 $r), (Clamp255 $g), (Clamp255 $b)))
    }
}
Save-Bmp $bmp 'potter_wheel_stone.png'

# ---------- 4. 石转盘侧面：略深，表现厚度 ----------
$bmp = New-Object System.Drawing.Bitmap(16, 16)
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $n = Noise $x $y 53
        $r = 108 + [int]($n * 18) - 9
        $g = 114 + [int]($n * 18) - 9
        $b = 106 + [int]($n * 18) - 9
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, (Clamp255 $r), (Clamp255 $g), (Clamp255 $b)))
    }
}
Save-Bmp $bmp 'potter_wheel_stone_side.png'

# ---------- 5. 瓷坯：白瓷，微暖 ----------
$bmp = New-Object System.Drawing.Bitmap(16, 16)
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $n = Noise $x $y 59
        $r = 240 + [int]($n * 12) - 6
        $g = 238 + [int]($n * 12) - 6
        $b = 230 + [int]($n * 12) - 6
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, (Clamp255 $r), (Clamp255 $g), (Clamp255 $b)))
    }
}
Save-Bmp $bmp 'potter_wheel_clay.png'

# ---------- 6. 瓷坯内壁：略暗，表现凹陷 ----------
$bmp = New-Object System.Drawing.Bitmap(16, 16)
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $n = Noise $x $y 61
        $v = 208 + [int]($n * 14) - 7
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, (Clamp255 $v), (Clamp255 ($v - 2)), (Clamp255 ($v - 10))))
    }
}
Save-Bmp $bmp 'potter_wheel_clay_inner.png'

Write-Output '--- 拉坯机贴图完成 ---'
