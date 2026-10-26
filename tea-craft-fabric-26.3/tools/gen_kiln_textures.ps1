# 生成窑炉 / 拉坯机贴图（按参考图配色）
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

# ---------- 1. 砖体：红橙砖 + 浅色砖缝（参考图砖缝是浅米色，不是深色） ----------
$bmp = New-Object System.Drawing.Bitmap(16, 16)
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $row = [Math]::Floor($y / 4)
        $offset = if (($row % 2) -eq 0) { 0 } else { 4 }
        $isMortar = (($y % 4) -eq 0) -or ((($x + $offset) % 8) -eq 0)
        $n = Noise $x $y 3
        if ($isMortar) {
            # 浅米色砖缝
            $r = 214 + [int]($n * 16) - 8
            $g = 200 + [int]($n * 16) - 8
            $b = 178 + [int]($n * 16) - 8
        }
        else {
            if ($n -lt 0.2) { $r = 172; $g = 72; $b = 54 }
            elseif ($n -gt 0.82) { $r = 214; $g = 106; $b = 82 }
            else { $r = 196; $g = 88; $b = 68 }
            $j = [int](($n - 0.5) * 10)
            $r = $r + $j; $g = $g + $j; $b = $b + $j
        }
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, (Clamp255 $r), (Clamp255 $g), (Clamp255 $b)))
    }
}
Save-Bmp $bmp 'clay_kiln_brick.png'

# ---------- 2. 顶盖石板：米色，带一点颗粒 ----------
$bmp = New-Object System.Drawing.Bitmap(16, 16)
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $n = Noise $x $y 11
        $r = 224 + [int]($n * 14) - 7
        $g = 212 + [int]($n * 14) - 7
        $b = 188 + [int]($n * 14) - 7
        if ($n -gt 0.9) { $r = $r - 14; $g = $g - 14; $b = $b - 12 }
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, (Clamp255 $r), (Clamp255 $g), (Clamp255 $b)))
    }
}
Save-Bmp $bmp 'clay_kiln_top.png'

# ---------- 3. 烟囱：比顶盖略深一点，区分层次 ----------
$bmp = New-Object System.Drawing.Bitmap(16, 16)
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $n = Noise $x $y 13
        $r = 210 + [int]($n * 14) - 7
        $g = 197 + [int]($n * 14) - 7
        $b = 172 + [int]($n * 14) - 7
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, (Clamp255 $r), (Clamp255 $g), (Clamp255 $b)))
    }
}
Save-Bmp $bmp 'clay_kiln_chimney.png'

# ---------- 4. 烟囱口：深色内壁 ----------
$bmp = New-Object System.Drawing.Bitmap(16, 16)
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $n = Noise $x $y 17
        $v = 46 + [int]($n * 16) - 8
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, (Clamp255 $v), (Clamp255 ($v + 2)), (Clamp255 ($v + 3))))
    }
}
Save-Bmp $bmp 'clay_kiln_chimney_hole.png'

# ---------- 5. 瓷门：奶油色，细颗粒 ----------
$bmp = New-Object System.Drawing.Bitmap(16, 16)
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $n = Noise $x $y 19
        $r = 232 + [int]($n * 14) - 7
        $g = 218 + [int]($n * 14) - 7
        $b = 188 + [int]($n * 16) - 8
        if ($n -gt 0.93) { $r = $r - 16; $g = $g - 16; $b = $b - 14 }
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, (Clamp255 $r), (Clamp255 $g), (Clamp255 $b)))
    }
}
Save-Bmp $bmp 'clay_kiln_door.png'

# ---------- 6. 金属把手 / 铰链：深灰绿 ----------
$bmp = New-Object System.Drawing.Bitmap(16, 16)
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $n = Noise $x $y 23
        $r = 70 + [int]($n * 14) - 7
        $g = 76 + [int]($n * 14) - 7
        $b = 72 + [int]($n * 14) - 7
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, (Clamp255 $r), (Clamp255 $g), (Clamp255 $b)))
    }
}
Save-Bmp $bmp 'clay_kiln_metal.png'

# ---------- 7. 底座：深灰 ----------
$bmp = New-Object System.Drawing.Bitmap(16, 16)
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $n = Noise $x $y 29
        $v = 88 + [int]($n * 18) - 9
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, (Clamp255 $v), (Clamp255 ($v + 2)), (Clamp255 $v)))
    }
}
Save-Bmp $bmp 'clay_kiln_base.png'

# ---------- 8. 瓷脚：浅灰 ----------
$bmp = New-Object System.Drawing.Bitmap(16, 16)
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $n = Noise $x $y 31
        $v = 176 + [int]($n * 18) - 9
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, (Clamp255 $v), (Clamp255 ($v - 2)), (Clamp255 ($v - 8))))
    }
}
Save-Bmp $bmp 'clay_kiln_foot.png'

Write-Output '--- 窑炉贴图完成 ---'
