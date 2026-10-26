# 把方块模型 JSON 渲染成等距视角 PNG 预览
#
#  用途：模型改完直接出图，不用进游戏就能看外观。
#  原理：把每个 element 的「顶面 / 南面 / 东面」用仿射变换贴上对应贴图，
#        按 (x+y+z) 从远到近画（画家算法），再叠一层明暗做体积感。
#
#  用法：
#    powershell -NoProfile -ExecutionPolicy Bypass -File tools\render_model_preview.ps1 `
#        -Model assets\teacraft\models\block\tea_set.json -Out tools\ref\tea_set_preview.png
param(
    [string]$Model = 'src\main\resources\assets\teacraft\models\block\tea_set.json',
    [string]$Out = 'tools\ref\preview.png',
    [int]$Pixel = 34,          # 每个模型单位（1/16 格）在图上占多少像素
    [double]$Yaw = 45,         # 水平旋转角度
    [double]$Pitch = 30,      # 俯仰角（度），30 是原版等距视角
    [int]$Pad = 28
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$modelPath = if ([System.IO.Path]::IsPathRooted($Model)) { $Model } else { Join-Path $root $Model }
$outPath = if ([System.IO.Path]::IsPathRooted($Out)) { $Out } else { Join-Path $root $Out }

$json = Get-Content -LiteralPath $modelPath -Raw | ConvertFrom-Json

# ---------- 贴图解析 ----------
$texCache = @{}
$assetsRoot = Join-Path $root 'src\main\resources\assets'

function Get-Texture([string]$ref) {
    # "#name" → 模型里 textures.name；"ns:block/xxx" → 直接路径
    if ($ref.StartsWith('#')) {
        $key = $ref.Substring(1)
        $resolved = $json.textures.$key
        if (-not $resolved) { throw "模型里没有贴图键 $ref" }
        $ref = $resolved
    }
    if ($texCache.ContainsKey($ref)) { return $texCache[$ref] }
    $i = $ref.IndexOf(':')
    $ns = if ($i -ge 0) { $ref.Substring(0, $i) } else { 'minecraft' }
    $path = if ($i -ge 0) { $ref.Substring($i + 1) } else { $ref }
    $file = Join-Path $assetsRoot "$ns\textures\$path.png"
    if (-not (Test-Path -LiteralPath $file)) { throw "贴图不存在：$file" }
    $bmp = [System.Drawing.Bitmap]::new($file)
    $texCache[$ref] = $bmp
    return $bmp
}

# ---------- 等距投影 ----------
$rad = $Yaw * [math]::PI / 180.0
$cos = [math]::Cos($rad)
$sin = [math]::Sin($rad)
# 世界中心 (8, 0, 8)
function Project([double]$x, [double]$y, [double]$z) {
    $dx = $x - 8.0
    $dz = $z - 8.0
    # 绕 Y 轴旋转后做等距投影
    $rx = $dx * $cos - $dz * $sin
    $rz = $dx * $sin + $dz * $cos
    $sx = $rx
    $pitchRad = $Pitch * [math]::PI / 180.0
    $pS = [math]::Sin($pitchRad)
    $pC = [math]::Cos($pitchRad)
    $sy = ($rz * $pS) - ($y * $pC)
    return @($sx, $sy)
}

# ---------- 收集面 ----------
#  ⚠ 必须按法线判断可见面。
#    第一版写死了"只画南面+东面"，只有 yaw=45 才碰巧对；
#    换到 135/225 度就整块穿帮（看到的是内壁）。
$quads = New-Object System.Collections.Generic.List[object]

# 相机方向（旋转后的坐标系里）：略微俯视
$camX = 0.0; $camY = 0.62; $camZ = 1.0

function RotateNormal([double]$nx, [double]$ny, [double]$nz) {
    $rx = $nx * $cos - $nz * $sin
    $rz = $nx * $sin + $nz * $cos
    return , @($rx, $ny, $rz)
}

function AddFace($texRef, [double]$shade, $corners, [double]$nx, [double]$ny, [double]$nz, $uv) {
    $n = RotateNormal $nx $ny $nz
    $dot = ($n[0] * $camX) + ($n[1] * $camY) + ($n[2] * $camZ)
    if ($dot -le 0.001) { return }   # 背面剔除

    $depth = 0.0
    $cx = 0.0; $cy = 0.0; $cz = 0.0
    foreach ($c in $corners) { $cx += $c[0]; $cy += $c[1]; $cz += $c[2] }
    $cx = $cx / 4.0; $cy = $cy / 4.0; $cz = $cz / 4.0
    # 深度投影到视线方向。不能直接用 x+y+z：换 yaw 后大小关系会变，遮挡顺序就错。
    $rx = ($cx - 8.0) * $cos - ($cz - 8.0) * $sin
    $rz = ($cx - 8.0) * $sin + ($cz - 8.0) * $cos
    $depth = ($rz * $camZ) + ($cy * $camY)

    $script:quads.Add([pscustomobject]@{
        tex = $texRef; shade = $shade; p = $corners; depth = $depth; uv = $uv
    })
}

foreach ($el in $json.elements) {
    $fx = [double]$el.from[0]; $fy = [double]$el.from[1]; $fz = [double]$el.from[2]
    $tx = [double]$el.to[0];   $ty = [double]$el.to[1];   $tz = [double]$el.to[2]

    $faces = $el.faces
    if (-not $faces) { continue }

    if ($faces.up) {
        AddFace $faces.up.texture 1.00 @(
            @($fx, $ty, $fz), @($tx, $ty, $fz), @($tx, $ty, $tz), @($fx, $ty, $tz)) 0 1 0 $faces.up.uv
    }
    if ($faces.down) {
        AddFace $faces.down.texture 0.50 @(
            @($fx, $fy, $tz), @($tx, $fy, $tz), @($tx, $fy, $fz), @($fx, $fy, $fz)) 0 -1 0 $faces.down.uv
    }
    if ($faces.south) {
        AddFace $faces.south.texture 0.80 @(
            @($fx, $ty, $tz), @($tx, $ty, $tz), @($tx, $fy, $tz), @($fx, $fy, $tz)) 0 0 1 $faces.south.uv
    }
    if ($faces.north) {
        AddFace $faces.north.texture 0.80 @(
            @($fx, $ty, $fz), @($tx, $ty, $fz), @($tx, $fy, $fz), @($fx, $fy, $fz)) 0 0 -1 $faces.north.uv
    }
    if ($faces.east) {
        AddFace $faces.east.texture 0.66 @(
            @($tx, $ty, $tz), @($tx, $ty, $fz), @($tx, $fy, $fz), @($tx, $fy, $tz)) 1 0 0 $faces.east.uv
    }
    if ($faces.west) {
        AddFace $faces.west.texture 0.66 @(
            @($fx, $ty, $fz), @($fx, $ty, $tz), @($fx, $fy, $tz), @($fx, $fy, $fz)) -1 0 0 $faces.west.uv
    }
}

# 远的先画
$sorted = $quads | Sort-Object depth

# ---------- 计算画布范围 ----------
$minX = [double]::MaxValue; $maxX = [double]::MinValue
$minY = [double]::MaxValue; $maxY = [double]::MinValue
foreach ($q in $sorted) {
    foreach ($pt in $q.p) {
        $s = Project $pt[0] $pt[1] $pt[2]
        if ($s[0] -lt $minX) { $minX = $s[0] }
        if ($s[0] -gt $maxX) { $maxX = $s[0] }
        if ($s[1] -lt $minY) { $minY = $s[1] }
        if ($s[1] -gt $maxY) { $maxY = $s[1] }
    }
}
$w = [int](($maxX - $minX) * $Pixel) + $Pad * 2
$h = [int](($maxY - $minY) * $Pixel) + $Pad * 2
$canvas = [System.Drawing.Bitmap]::new([math]::Max($w, 32), [math]::Max($h, 32),
    [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($canvas)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$g.Clear([System.Drawing.Color]::FromArgb(255, 236, 240, 244))

function ToScreen($s) {
    # 分开算成标量再组数组：PowerShell 里 `a + b, c + d` 容易被解析成数组相加
    [double]$sx = $s[0]
    [double]$sy = $s[1]
    [double]$rx = (($sx - $minX) * $Pixel) + $Pad
    [double]$ry = (($sy - $minY) * $Pixel) + $Pad
    return , @($rx, $ry)
}

foreach ($q in $sorted) {
    $tex = Get-Texture $q.tex
    $s0 = ToScreen (Project $q.p[0][0] $q.p[0][1] $q.p[0][2])
    $s1 = ToScreen (Project $q.p[1][0] $q.p[1][1] $q.p[1][2])
    $s3 = ToScreen (Project $q.p[3][0] $q.p[3][1] $q.p[3][2])

    # 把贴图矩形仿射映射到平行四边形
    $m = [System.Drawing.Drawing2D.Matrix]::new()
    $ax = ($s1[0] - $s0[0]) / $tex.Width
    $ay = ($s1[1] - $s0[1]) / $tex.Width
    $bx = ($s3[0] - $s0[0]) / $tex.Height
    $by = ($s3[1] - $s0[1]) / $tex.Height
    $m.Multiply([System.Drawing.Drawing2D.Matrix]::new($ax, $ay, $bx, $by, $s0[0], $s0[1]),
        [System.Drawing.Drawing2D.MatrixOrder]::Append)
    $g.Transform = $m
    $u = $q.uv
    if ($u -and $u.Count -eq 4) {
        # uv=[x0,y0,x1,y1]，单位是贴图像素（16x16 网格）
        $sx0 = [double]$u[0] * $tex.Width / 16.0
        $sy0 = [double]$u[1] * $tex.Height / 16.0
        $sw  = ([double]$u[2] - [double]$u[0]) * $tex.Width / 16.0
        $sh  = ([double]$u[3] - [double]$u[1]) * $tex.Height / 16.0
        if ($sw -le 0) { $sw = $tex.Width }
        if ($sh -le 0) { $sh = $tex.Height }
        $srcRect = [System.Drawing.RectangleF]::new($sx0, $sy0, $sw, $sh)
        $dstRect = [System.Drawing.RectangleF]::new(0, 0, $tex.Width, $tex.Height)
        $g.DrawImage($tex, $dstRect, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
    } else {
        $g.DrawImage($tex, 0, 0, $tex.Width, $tex.Height)
    }
    $g.ResetTransform()

    # 明暗（用同形状多边形叠半透明黑）
    if ($q.shade -lt 0.999) {
        $alpha = [int]((1.0 - $q.shade) * 255)
        $brush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb($alpha, 0, 0, 0))
        $pts = @(
            [System.Drawing.PointF]::new($s0[0], $s0[1]),
            [System.Drawing.PointF]::new($s1[0], $s1[1]),
            [System.Drawing.PointF]::new(
                $s1[0] + ($s3[0] - $s0[0]), $s1[1] + ($s3[1] - $s0[1])),
            [System.Drawing.PointF]::new($s3[0], $s3[1]))
        $g.FillPolygon($brush, $pts)
        $brush.Dispose()
    }
}

$g.Dispose()
$dir = Split-Path -Parent $outPath
if (!(Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
$canvas.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
$canvas.Dispose()
foreach ($b in $texCache.Values) { $b.Dispose() }

"渲染完成：$outPath  ($([math]::Max($w,32))x$([math]::Max($h,32)))  面数 $($sorted.Count)"
