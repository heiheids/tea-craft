# 生成茶盘模型 JSON（models/block/tea_set.json）
#
#  为什么用脚本而不是手写 JSON：
#    1. 三个杯子结构完全一样，手写容易错位；
#    2. 以后搬进 BlockEntityRenderer 时，这里的尺寸可以直接对应
#       CubeListBuilder.addBox(...)，数值不用二次换算。
#
#  用法：powershell -NoProfile -ExecutionPolicy Bypass -File tools\gen_teaset_model.ps1
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$outFile = Join-Path $root 'src\main\resources\assets\teacraft\models\block\tea_set.json'

function Round([double]$v) { return [math]::Round($v, 3) }

function Face([string]$tex, $uv) {
  $f = [ordered]@{ texture = $tex }
  if ($uv) { $f['uv'] = $uv }
  return $f
}

# 带显式 UV 的盒子。
#
# 【为什么必须这样】
#   方块模型不给 uv 时用 auto-UV：贴图行 = 16-元件上界 .. 16-元件下界。
#   也就是说"能看见贴图的哪几行"由元件的 y 坐标决定，而不是由画在哪决定。
#   实测：
#     盖碗物品 [4] y 8.54..9.04 → 可见行 6.96..7.46 → 蓝线(6..8)在范围内 ✓
#     盖碗方块 [4] y 4.20..4.75 → 可见行 11.25..11.8 → 蓝线不在范围内 ✗
#   这就是"拿在手里有蓝线、放下没有"的真正原因。
#   壶身 y 1.7..6.5 → 可见行 9.5..14.3，蓝线 5..10 只碰到 9.5..10，所以也几乎看不见。
#
#   解决：显式写 uv，让每个面都取蓝线所在的 5..11 行。
function BoxUv($x0, $y0, $z0, $x1, $y1, $z1, $tex, $uvDown, $uvUp, $uvSide, $texUp) {
  $faces = [ordered]@{}
  if ($uvDown) { $faces['down']  = Face $tex $uvDown }
  if ($uvUp) {
    # texUp 允许顶面单独指定贴图（侧面青花、顶面白瓷）
    $ut = if ($texUp) { $texUp } else { $tex }
    $faces['up'] = Face $ut $uvUp
  }
  if ($uvSide) {
    $faces['north'] = Face $tex $uvSide
    $faces['south'] = Face $tex $uvSide
    $faces['west']  = Face $tex $uvSide
    $faces['east']  = Face $tex $uvSide
  }
  return [ordered]@{
    from  = @((Round $x0), (Round $y0), (Round $z0))
    to    = @((Round $x1), (Round $y1), (Round $z1))
    faces = $faces
  }
}
function Box($x0, $y0, $z0, $x1, $y1, $z1, $down, $up, $north, $south, $west, $east) {
    $faces = [ordered]@{}
    if ($down)  { $faces['down']  = Face $down }
    if ($up)    { $faces['up']    = Face $up }
    if ($north) { $faces['north'] = Face $north }
    if ($south) { $faces['south'] = Face $south }
    if ($west)  { $faces['west']  = Face $west }
    if ($east)  { $faces['east']  = Face $east }
    # 坐标统一保留 3 位小数：否则会写出 13.100000000000001 这种浮点噪声
    return [ordered]@{
        from  = @((Round $x0), (Round $y0), (Round $z0))
        to    = @((Round $x1), (Round $y1), (Round $z1))
        faces = $faces
    }
}

$elements = New-Object System.Collections.Generic.List[object]

# ---------------- 茶盘：底 + 四边盘沿（总高 3px）----------------
$elements.Add((Box 0 0 0 16 1 16 '#wood' '#floor' '#wood' '#wood' '#wood' '#wood'))
$elements.Add((Box 0 1 0 16 3 1  $null '#wood' '#wood' '#wood' '#wood' '#wood'))
$elements.Add((Box 0 1 15 16 3 16 $null '#wood' '#wood' '#wood' '#wood' '#wood'))
$elements.Add((Box 0 1 1 1 3 15  $null '#wood' '#wood' '#wood' '#wood' '#floor'))
$elements.Add((Box 15 1 1 16 3 15 $null '#wood' '#wood' '#wood' '#floor' '#wood'))

# ---------------- 盖碗：圈足 → 3 层腹 → 口沿 → 盖沿 → 盖顶 → 钮 ----------------
# 逐层变宽，做出碗的收腰；口沿与盖沿用带青花的 #porc
$elements.Add((Box 6.9 1.02 4.9 9.1 1.5 7.1  '#porc_plain' $null '#porc_plain' '#porc_plain' '#porc_plain' '#porc_plain'))
$elements.Add((Box 6.5 1.5 4.5 9.5 2.3 7.5   '#porc_plain' $null '#porc_plain' '#porc_plain' '#porc_plain' '#porc_plain'))
$elements.Add((Box 6.1 2.3 4.1 9.9 3.2 7.9   '#porc_plain' $null '#porc_plain' '#porc_plain' '#porc_plain' '#porc_plain'))
$elements.Add((Box 5.7 3.2 3.7 10.3 4.2 8.3  '#porc_plain' $null '#porc_plain' '#porc_plain' '#porc_plain' '#porc_plain'))
$elements.Add((BoxUv 5.4 4.2 3.4 10.6 4.75 8.6  '#band' @(0, 0, 16, 16) $null @(0, 6, 16, 9)))
$elements.Add((BoxUv 5.2 4.75 3.2 10.8 5.2 8.8  '#band' @(0, 0, 16, 16) @(0, 0, 16, 16) @(0, 6, 16, 9) '#porc_plain'))
$elements.Add((Box 5.5 5.21 3.5 10.5 5.7 8.5  '#porc_plain' '#porc_top' '#porc_plain' '#porc_plain' '#porc_plain' '#porc_plain'))
$elements.Add((Box 7.2 5.71 5.2 8.8 6.3 6.8   '#porc_plain' '#porc_top' '#porc_plain' '#porc_plain' '#porc_plain' '#porc_plain'))

# ---------------- 三个方杯：薄壁中空 ----------------
#  中空不是"看起来像"，是真的四壁 + 底：
#    外壁 #porc（口沿青花） / 内壁 #porc_inner（更暗，做出深度）
#  这样从上方能直接看到内壁和杯底，凹陷感是几何本身产生的。
function Cup([double]$x0, [double]$z0) {
    $w = 3.2      # 外径（比高度大，才像茶杯而不是方盒）
    $t = 0.5      # 壁厚
    $y0 = 1.02    # 底面
    $yb = 1.5     # 杯底厚度
    $y1 = 3.4     # 杯口：必须高于 3px 的盘沿，否则杯子会被盘沿挡掉一半
    $res = New-Object System.Collections.Generic.List[object]

    # 杯底（顶面是内壁色，从上方看就是"杯底"）
    $res.Add((Box $x0 $y0 $z0 ($x0 + $w) $yb ($z0 + $w) '#porc_plain' '#porc_inner' '#porc_plain' '#porc_plain' '#porc_plain' '#porc_plain'))
    # 北壁：外北、内南
    $res.Add((Box $x0 $yb $z0 ($x0 + $w) $y1 ($z0 + $t) $null '#porc_top' '#porc' '#porc_inner' '#porc_plain' '#porc_plain'))
    # 南壁
    $res.Add((Box $x0 $yb ($z0 + $w - $t) ($x0 + $w) $y1 ($z0 + $w) $null '#porc_top' '#porc_inner' '#porc' '#porc_plain' '#porc_plain'))
    # 西壁
    $res.Add((Box $x0 $yb ($z0 + $t) ($x0 + $t) $y1 ($z0 + $w - $t) $null '#porc_top' '#porc_plain' '#porc_plain' '#porc' '#porc_inner'))
    # 东壁
    $res.Add((Box ($x0 + $w - $t) $yb ($z0 + $t) ($x0 + $w) $y1 ($z0 + $w - $t) $null '#porc_top' '#porc_plain' '#porc_plain' '#porc_inner' '#porc'))
    return $res
}

foreach ($cup in @(@(1.2, 9.9), @(6.4, 10.3), @(11.6, 9.9))) {
    foreach ($el in (Cup $cup[0] $cup[1])) { $elements.Add($el) }
}

# ---------------- 茶叶堆 ----------------
$elements.Add((Box 11.4 1.02 7.4 13.4 1.9 9.2 '#leaf' '#leaf' '#leaf' '#leaf' '#leaf' '#leaf'))
$elements.Add((Box 11.8 1.9 7.8 13.0 2.4 8.8  $null '#leaf' '#leaf' '#leaf' '#leaf' '#leaf'))

$model = [ordered]@{
    textures = [ordered]@{
        particle   = 'teacraft:block/tea_set_wood'
        wood       = 'teacraft:block/tea_set_wood'
        floor      = 'teacraft:block/tea_set_floor'
        porc       = 'teacraft:block/tea_set_porcelain_side'
    band       = 'teacraft:block/tea_set_porcelain_band'
        porc_plain = 'teacraft:block/tea_set_porcelain_plain'
        porc_inner = 'teacraft:block/tea_set_porcelain_inner'
        porc_top   = 'teacraft:block/tea_set_porcelain_top'
        leaf       = 'teacraft:block/tea_set_leaf'
    }
    elements = $elements
}

$json = $model | ConvertTo-Json -Depth 12
[System.IO.File]::WriteAllText($outFile, $json, (New-Object System.Text.UTF8Encoding($false)))
"生成 $($elements.Count) 个元件 -> $outFile"

# ---------------- 物品模型：盖碗 / 茶杯 ----------------
#  复用方块里的同一套几何，只把坐标平移到以原点为中心
#  （方块里这些物件是从盘面 y=1.02 开始、x/z 在盘内某个位置）。
function ShiftEls($list, [double]$dx, [double]$dy, [double]$dz) {
    $out = New-Object System.Collections.Generic.List[object]
    foreach ($e in $list) {
        $out.Add([ordered]@{
            from  = @((Round ($e.from[0] + $dx)), (Round ($e.from[1] + $dy)), (Round ($e.from[2] + $dz)))
            to    = @((Round ($e.to[0] + $dx)), (Round ($e.to[1] + $dy)), (Round ($e.to[2] + $dz)))
            faces = $e.faces
        })
    }
    return $out
}

$itemTextures = [ordered]@{
    particle   = 'teacraft:block/tea_set_porcelain_plain'
    porc       = 'teacraft:block/tea_set_porcelain_side'
    band       = 'teacraft:block/tea_set_porcelain_band'
    porc_plain = 'teacraft:block/tea_set_porcelain_plain'
    porc_inner = 'teacraft:block/tea_set_porcelain_inner'
    porc_top   = 'teacraft:block/tea_set_porcelain_top'
    tea        = 'teacraft:block/tea_set_tea'
}

#  $s：缩放。模型宽度只有 5.6（盖碗）/ 3.2（茶杯）单位，
#      用方块标准的 0.625 在格子里只有 3 像素，几乎看不见，所以按物件大小单独给。
function ItemDisplay([double]$s) {
    return [ordered]@{
        gui = [ordered]@{
            rotation = @(30, 225, 0); translation = @(0, 0, 0); scale = @((Round $s), (Round $s), (Round $s))
        }
        ground = [ordered]@{
            rotation = @(0, 0, 0); translation = @(0, 2, 0); scale = @((Round ($s * 0.4)), (Round ($s * 0.4)), (Round ($s * 0.4)))
        }
        fixed = [ordered]@{
            rotation = @(0, 0, 0); translation = @(0, 0, 0); scale = @((Round ($s * 0.8)), (Round ($s * 0.8)), (Round ($s * 0.8)))
        }
        thirdperson_righthand = [ordered]@{
            rotation = @(75, 45, 0); translation = @(0, 2.5, 0); scale = @((Round ($s * 0.6)), (Round ($s * 0.6)), (Round ($s * 0.6)))
        }
        firstperson_righthand = [ordered]@{
            rotation = @(0, 45, 0); translation = @(0, 1.5, 0); scale = @((Round ($s * 0.75)), (Round ($s * 0.75)), (Round ($s * 0.75)))
        }
    }
}

$itemDir = Join-Path $root 'src\main\resources\assets\teacraft\models\item'
$itemsDir = Join-Path $root 'src\main\resources\assets\teacraft\items'

# 盖碗：方块里第 5..12 个元件；下移到地面，并居中到 (8,8)
#   y 方向让包围盒中心落在 8：盖碗高 5.28 → 底部应在 5.36，相对原 1.02 需 +4.34
$gaiwanEls = ShiftEls ($elements[5..12]) 0 4.34 2
$gaiwanModel = [ordered]@{
    textures = $itemTextures
    display  = (ItemDisplay 1.26)
    elements = $gaiwanEls
}
[System.IO.File]::WriteAllText((Join-Path $itemDir 'gaiwan.json'),
    ($gaiwanModel | ConvertTo-Json -Depth 12), (New-Object System.Text.UTF8Encoding($false)))

# 茶杯（有茶）：空杯 + 一层茶汤，供物品模型按 tea_brew 组件切换
$cupFullEls = New-Object System.Collections.Generic.List[object]
foreach ($e in (ShiftEls (Cup 0 0) 6.4 5.79 6.4)) { $cupFullEls.Add($e) }
$cupFullEls.Add((Box 6.9 7.29 6.9 9.1 8.34 9.1 $null '#tea' $null $null $null $null))
# 茶杯：单独建一只，居中到 (8,8)
#   y 方向同理：茶杯高 2.38 → 底部应在 6.81，需 +5.79
$cupEls = ShiftEls (Cup 0 0) 6.4 5.79 6.4
$cupModel = [ordered]@{
    textures = $itemTextures
    display  = (ItemDisplay 1.56)
    elements = $cupEls
}
[System.IO.File]::WriteAllText((Join-Path $itemDir 'tea_cup.json'),
    ($cupModel | ConvertTo-Json -Depth 12), (New-Object System.Text.UTF8Encoding($false)))

# items/*.json：物品到模型的指向
#  ⚠ 这两个文件**手写、不由本脚本生成**：
#    它们是 minecraft:condition（按 tea_brew 组件切换空/满两种模型），
#    用 ConvertTo-Json 生成会被压成简单 model 引用，把"有茶汤"的切换逻辑冲掉。

"物品模型：gaiwan($($gaiwanEls.Count) 元件)、tea_cup($($cupEls.Count) 元件)"


# ---------------- 拆分模型：盘 / 盖碗 / 三个杯子 ----------------
#  方块状态要能单独显示/隐藏每个物件（拿走盖碗、杯子空了/满了），
#  所以把整块模型拆成若干小模型，由 multipart 组合。
$blockDir = Join-Path $root 'src\main\resources\assets\teacraft\models\block\tea_set'
if (!(Test-Path -LiteralPath $blockDir)) { New-Item -ItemType Directory -Force -Path $blockDir | Out-Null }

$blockTextures = [ordered]@{
    particle   = 'teacraft:block/tea_set_wood'
    wood       = 'teacraft:block/tea_set_wood'
    floor      = 'teacraft:block/tea_set_floor'
    porc       = 'teacraft:block/tea_set_porcelain_side'
    band       = 'teacraft:block/tea_set_porcelain_band'
    porc_plain = 'teacraft:block/tea_set_porcelain_plain'
    porc_inner = 'teacraft:block/tea_set_porcelain_inner'
    porc_top   = 'teacraft:block/tea_set_porcelain_top'
    leaf       = 'teacraft:block/tea_set_leaf'
    tea        = 'teacraft:block/tea_set_tea'
}

function WritePart([string]$file, $els) {
    $m = [ordered]@{ textures = $blockTextures; elements = $els }
    [System.IO.File]::WriteAllText((Join-Path $blockDir $file),
        ($m | ConvertTo-Json -Depth 12), (New-Object System.Text.UTF8Encoding($false)))
}

# 盘：底座 + 四边盘沿 + 茶叶堆（茶叶是装饰，永远在）
$trayEls = New-Object System.Collections.Generic.List[object]
for ($i = 0; $i -le 4; $i++) { $trayEls.Add($elements[$i]) }
for ($i = 28; $i -le 29; $i++) { $trayEls.Add($elements[$i]) }
WritePart 'tray.json' $trayEls

# 盖碗
$gEls = New-Object System.Collections.Generic.List[object]
for ($i = 5; $i -le 12; $i++) { $gEls.Add($elements[$i]) }
WritePart 'gaiwan.json' $gEls

# 三个杯子：空 / 有茶（有茶 = 杯内加一层茶汤）
$cupNames = @('a', 'b', 'c')
for ($k = 0; $k -lt 3; $k++) {
    $cupEls = New-Object System.Collections.Generic.List[object]
    for ($i = 0; $i -lt 5; $i++) { $cupEls.Add($elements[13 + $k * 5 + $i]) }
    WritePart "cup_$($cupNames[$k])_empty.json" $cupEls

    # 茶汤：在杯内贴壁的一层，比内壁略小
    $first = $cupEls[0]
    $x0 = [double]$first.from[0] + 0.5
    $z0 = [double]$first.from[2] + 0.5
    $x1 = [double]$first.to[0] - 0.5
    $z1 = [double]$first.to[2] - 0.5
    $withTea = New-Object System.Collections.Generic.List[object]
    foreach ($e in $cupEls) { $withTea.Add($e) }
    $withTea.Add((Box $x0 1.5 $z0 $x1 2.55 $z1 $null '#tea' $null $null $null $null))
    WritePart "cup_$($cupNames[$k])_full.json" $withTea
}

"拆分模型：tray + gaiwan + 3×2 杯 -> $blockDir"
$cupFullModel = [ordered]@{
    textures = $itemTextures
    display  = (ItemDisplay 1.56)
    elements = $cupFullEls
}
[System.IO.File]::WriteAllText((Join-Path $itemDir 'tea_cup_full.json'),
    ($cupFullModel | ConvertTo-Json -Depth 12), (New-Object System.Text.UTF8Encoding($false)))

"有茶杯模型：$($cupFullEls.Count) 元件"
# 盖碗（有茶汤）：碗口内加一层茶汤，供物品模型按 tea_brew 组件切换
$gaiwanFullEls = New-Object System.Collections.Generic.List[object]
foreach ($e in $gaiwanEls) { $gaiwanFullEls.Add($e) }
# 茶汤面必须落在碗内腔里，且**与碗同心（x=8, z=8）**。
# 旧值 z 3.85..8.15 中心是 6，偏了 2 格 —— 所以茶汤从碗里冒出来（用户反馈）。
# 现在按同心算：x 5.85..10.15（中心 8）、z 5.85..10.15（中心 8），
# 比碗内腔（[1] 层 6.5..9.5）略大一圈，正好贴在碗壁上沿。
$gaiwanFullEls.Add((Box 6.4 6.7 6.4 9.6 7.0 9.6 $null '#tea' $null $null $null $null))

$gaiwanFullModel = [ordered]@{
    textures = $gaiwanModel.textures
    display  = (ItemDisplay 1.26)
    elements = $gaiwanFullEls
}
[System.IO.File]::WriteAllText((Join-Path $itemDir 'gaiwan_full.json'),
    ($gaiwanFullModel | ConvertTo-Json -Depth 12), (New-Object System.Text.UTF8Encoding($false)))

"有茶盖碗模型：$($gaiwanFullEls.Count) 元件"
