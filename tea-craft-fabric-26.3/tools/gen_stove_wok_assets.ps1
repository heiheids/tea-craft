# 生成碳焙框 / 铁锅 的贴图与模型
#   碳焙框：侧面、顶部用用户给的图（128→64，四角 flood fill 去白底）；
#           底部按边框色调模拟"铁锅"质感；再加一张点燃态顶面（炭火发红）
#   铁锅  ：炼药锅模型 Y 轴 ×2/3（0/3/4/16 → 0/2/2.67/10.67），贴图自绘深色铁
# 用法： powershell -File tools\gen_stove_wok_assets.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$srcDir = Join-Path $root '茶工艺三种茶\碳焙框'
$texDir = Join-Path $root 'src\main\resources\assets\teacraft\textures'
$res    = Join-Path $root 'src\main\resources\assets\teacraft'
$N = 64

function W([string]$path, [string]$text) {
    $dir = Split-Path -Parent $path
    if (!(Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
}
function Save([System.Drawing.Bitmap]$bmp, [string]$path) {
    $d = Split-Path -Parent $path
    if (!(Test-Path -LiteralPath $d)) { New-Item -ItemType Directory -Force -Path $d | Out-Null }
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}
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
# 四角 flood fill 去白底（保留内部白色高光）
function Remove-Background([System.Drawing.Bitmap]$bmp) {
    $w = $bmp.Width; $h = $bmp.Height
    $seen = New-Object 'bool[]' ($w * $h)
    $stack = New-Object System.Collections.Stack
    $x1 = $w - 1; $y1 = $h - 1
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
        if ($c.R -lt 232 -or $c.G -lt 232 -or $c.B -lt 232) { continue }
        $seen[$idx] = $true
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, $c.R, $c.G, $c.B))
        $nx1 = $x + 1; $nx2 = $x - 1; $ny1 = $y + 1; $ny2 = $y - 1
        $q0 = New-Object 'int[]' 2; $q0[0] = $nx1; $q0[1] = $y
        $q1 = New-Object 'int[]' 2; $q1[0] = $nx2; $q1[1] = $y
        $q2 = New-Object 'int[]' 2; $q2[0] = $x;   $q2[1] = $ny1
        $q3 = New-Object 'int[]' 2; $q3[0] = $x;   $q3[1] = $ny2
        $stack.Push($q0); $stack.Push($q1); $stack.Push($q2); $stack.Push($q3)
    }
}

# ---- 碳焙框：用户给的侧面/顶部 ----
foreach ($pair in @(@('碳焙框侧面.png','tea_charcoal_stove_side.png'),
                    @('碳焙框顶部.png','tea_charcoal_stove_top.png'))) {
    $b = Load-Scaled (Join-Path $srcDir $pair[0]) $N
    Remove-Background $b
    Save $b (Join-Path $texDir ('block\' + $pair[1]))
    "  " + $pair[1]
}

# ---- 铁质贴图（自绘，模拟铁锅/铁皮） ----
#   基调取自碳焙框边框的暖褐色金属，压暗后就是"铁"
function New-Iron([int]$n, [int]$baseR, [int]$baseG, [int]$baseB, [double]$vGrad, [string]$path) {
    $b = [System.Drawing.Bitmap]::new($n, $n)
    $rnd = New-Object System.Random 20261022
    for ($y = 0; $y -lt $n; $y++) {
        $t = $y / [double]($n - 1)
        $f = 1.0 + $vGrad * (0.5 - $t)      # 上亮下暗
        for ($x = 0; $x -lt $n; $x++) {
            $noise = ($rnd.NextDouble() - 0.5) * 0.10
            $streak = if ((($y % 7) -eq 0)) { 0.10 } else { 0.0 }
            $k = $f + $noise + $streak
            $r = [Math]::Max(0, [Math]::Min(255, [int]($baseR * $k)))
            $g = [Math]::Max(0, [Math]::Min(255, [int]($baseG * $k)))
            $bb = [Math]::Max(0, [Math]::Min(255, [int]($baseB * $k)))
            $b.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $bb))
        }
    }
    Save $b $path
}
New-Iron $N 92 78 62 0.28 (Join-Path $texDir 'block\tea_charcoal_stove_bottom.png')

# 铁锅「侧面」：上缘一圈亮锅沿、下段锅身收窄并带锈色纹理。
# 需求：「底部本应是个铁锅用来放木炭的」—— 光有底面（朝下）外面看不见，
# 所以要把铁锅做成方块下半段可见的部分。
$ironSide = [System.Drawing.Bitmap]::new($N, $N)
for ($y = 0; $y -lt $N; $y++) {
    $t2 = $y / [double]($N - 1)
    for ($x = 0; $x -lt $N; $x++) {
        # 顶部 3 行 = 锅沿（亮），下面 = 锅身（暗），越往下越暗
        if ($y -lt 3) {
            $v = 150 - $y * 10
            $r = $v; $g = [int]($v * 0.98); $b3 = [int]($v * 0.92)
        } else {
            $base = 96 - [int]($t2 * 34)
            # 横向铁锈/锤痕
            $mark = if ((($y % 9) -eq 0) -or (($y % 13) -eq 5)) { -14 } else { 0 }
            $r = $base + $mark + 10
            $g = $base + $mark + 4
            $b3 = $base + $mark
        }
        if ($r -lt 0) { $r = 0 }; if ($g -lt 0) { $g = 0 }; if ($b3 -lt 0) { $b3 = 0 }
        if ($r -gt 255) { $r = 255 }; if ($g -gt 255) { $g = 255 }; if ($b3 -gt 255) { $b3 = 255 }
        $ironSide.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b3))
    }
}
Save $ironSide (Join-Path $texDir 'block\tea_charcoal_stove_iron_side.png')
New-Iron $N 74 74 78 0.30 (Join-Path $texDir 'block\tea_wok_side.png')
New-Iron $N 58 58 62 0.16 (Join-Path $texDir 'block\tea_wok_bottom.png')
New-Iron $N 46 46 50 0.10 (Join-Path $texDir 'block\tea_wok_inner.png')
"  iron textures"

# ---- 铁锅顶面：外圈铁沿 + 内圈凹陷 ----
$top = [System.Drawing.Bitmap]::new($N, $N)
for ($y = 0; $y -lt $N; $y++) {
    for ($x = 0; $x -lt $N; $x++) {
        $edge = [Math]::Min([Math]::Min($x, $N - 1 - $x), [Math]::Min($y, $N - 1 - $y))
        if ($edge -lt 6) { $v = 84 } elseif ($edge -lt 10) { $v = 62 } else { $v = 44 }
        $top.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $v, $v, [Math]::Min(255, $v + 4)))
    }
}
Save $top (Join-Path $texDir 'block\tea_wok_top.png')

# ---- 碳焙框点燃态顶面：中心炭火发红 ----
$lit = Load-Scaled (Join-Path $srcDir '碳焙框顶部.png') $N
Remove-Background $lit
$cx = ($N - 1) / 2.0
for ($y = 0; $y -lt $N; $y++) {
    for ($x = 0; $x -lt $N; $x++) {
        $d = [Math]::Sqrt([Math]::Pow($x - $cx, 2) + [Math]::Pow($y - $cx, 2)) / $cx
        if ($d -gt 1.0) { continue }
        $heat = [Math]::Pow(1.0 - $d, 1.6)
        $c = $lit.GetPixel($x, $y)
        $r = [Math]::Min(255, [int]($c.R * (1 - $heat) + 255 * $heat))
        $g = [Math]::Min(255, [int]($c.G * (1 - $heat) + 132 * $heat))
        $b2 = [Math]::Min(255, [int]($c.B * (1 - $heat) + 40 * $heat))
        $lit.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $r, $g, $b2))
    }
}
Save $lit (Join-Path $texDir 'block\tea_charcoal_stove_top_lit.png')

# ---- 物品图标：直接用顶面俯视图（扁平图标，与晒盘 icon 一致） ----
Copy-Item -LiteralPath (Join-Path $texDir 'block\tea_charcoal_stove_top.png') `
          -Destination (Join-Path $texDir 'item\tea_charcoal_stove_icon_0.png') -Force
Copy-Item -LiteralPath (Join-Path $texDir 'block\tea_wok_top.png') `
          -Destination (Join-Path $texDir 'item\tea_wok_icon_0.png') -Force
"  item icons"

# ================= 模型 =================

# 碳焙框：一个 0~16 的立方体（像熔炉那样是整块）
# 碳焙框模型：下半段是铁锅（放木炭的地方），上半段是竹筐。
#   之前是一整块 16³ 实心方块，铁锅只在「朝下」那一面，从外面完全看不见，
#   所以需求「底部本应是个铁锅」其实没实现。
#   现在：
#     铁锅  0 ~ 5px  —— 侧面用 iron_side（亮锅沿 + 收窄锅身），底面用 bottom
#   ⚠ 竹筐元件<b>不能有 down 面</b>：它在 y=5，比锅顶(6)低，会从锅沿露出一圈白面。
#     竹筐  5 ~ 16px —— 侧面/顶面用用户给的图
#   铁锅比竹筐窄一圈（内缩 1px），从斜上方能看到"筐坐在锅里"的层次。
function StoveModel([string]$topTex, [string]$path) {
    $t = @"
{
  "textures": {
    "particle": "teacraft:block/tea_charcoal_stove_side",
    "side": "teacraft:block/tea_charcoal_stove_side",
    "top": "teacraft:block/$topTex",
    "bottom": "teacraft:block/tea_charcoal_stove_bottom",
    "iron": "teacraft:block/tea_charcoal_stove_iron_side"
  },
  "elements": [
    {
      "from": [0, 5, 0],
      "to": [16, 16, 16],
      "faces": {
        "up":    { "texture": "#top" },
        "north": { "texture": "#side", "cullface": "north" },
        "south": { "texture": "#side", "cullface": "south" },
        "west":  { "texture": "#side", "cullface": "west" },
        "east":  { "texture": "#side", "cullface": "east" }
      }
    },
    {
      "from": [0, 0, 0],
      "to": [16, 5, 16],
      "faces": {
        "north": { "texture": "#iron", "uv": [0, 10, 16, 16] },
        "south": { "texture": "#iron", "uv": [0, 10, 16, 16] },
        "west":  { "texture": "#iron", "uv": [0, 10, 16, 16] },
        "east":  { "texture": "#iron", "uv": [0, 10, 16, 16] },
        "down":  { "texture": "#bottom", "cullface": "down" }
      }
    }
  ]
}
"@
    W $path $t
}
StoveModel 'tea_charcoal_stove_top'     (Join-Path $res 'models\block\tea_charcoal_stove.json')
StoveModel 'tea_charcoal_stove_top_lit' (Join-Path $res 'models\block\tea_charcoal_stove_lit.json')

# 铁锅：炼药锅模型 Y 轴 ×2/3 —— 0→0, 3→2, 4→2.667, 16→10.667
$wok = @'
{
  "ambientocclusion": false,
  "textures": {
    "particle": "teacraft:block/tea_wok_side",
    "top": "teacraft:block/tea_wok_top",
    "bottom": "teacraft:block/tea_wok_bottom",
    "side": "teacraft:block/tea_wok_side",
    "inside": "teacraft:block/tea_wok_inner"
  },
  "elements": [
    { "from": [0, 2, 0],   "to": [2, 10.667, 16],  "faces": { "north": {"texture":"#side","cullface":"north"}, "east": {"texture":"#side"}, "south": {"texture":"#side","cullface":"south"}, "west": {"texture":"#side","cullface":"west"}, "up": {"texture":"#top","cullface":"up"}, "down": {"texture":"#inside"} } },
    { "from": [2, 2, 2],   "to": [14, 2.667, 14],  "faces": { "up": {"texture":"#inside"}, "down": {"texture":"#inside"} } },
    { "from": [14, 2, 0],  "to": [16, 10.667, 16], "faces": { "north": {"texture":"#side","cullface":"north"}, "east": {"texture":"#side","cullface":"east"}, "south": {"texture":"#side","cullface":"south"}, "west": {"texture":"#side"}, "up": {"texture":"#top","cullface":"up"}, "down": {"texture":"#inside"} } },
    { "from": [2, 2, 0],   "to": [14, 10.667, 2],  "faces": { "north": {"texture":"#side","cullface":"north"}, "south": {"texture":"#side"}, "up": {"texture":"#top","cullface":"up"}, "down": {"texture":"#inside"} } },
    { "from": [2, 2, 14],  "to": [14, 10.667, 16], "faces": { "north": {"texture":"#side"}, "south": {"texture":"#side","cullface":"south"}, "up": {"texture":"#top","cullface":"up"}, "down": {"texture":"#inside"} } },
    { "from": [0, 0, 0],   "to": [4, 2, 2],        "faces": { "north": {"texture":"#side","cullface":"north"}, "east": {"texture":"#side"}, "south": {"texture":"#side"}, "west": {"texture":"#side","cullface":"west"}, "down": {"texture":"#bottom","cullface":"down"} } },
    { "from": [0, 0, 2],   "to": [2, 2, 4],        "faces": { "east": {"texture":"#side"}, "south": {"texture":"#side"}, "west": {"texture":"#side","cullface":"west"}, "down": {"texture":"#bottom","cullface":"down"} } },
    { "from": [12, 0, 0],  "to": [16, 2, 2],       "faces": { "north": {"texture":"#side","cullface":"north"}, "east": {"texture":"#side","cullface":"east"}, "south": {"texture":"#side"}, "west": {"texture":"#side"}, "down": {"texture":"#bottom","cullface":"down"} } },
    { "from": [14, 0, 2],  "to": [16, 2, 4],       "faces": { "east": {"texture":"#side","cullface":"east"}, "south": {"texture":"#side"}, "west": {"texture":"#side"}, "down": {"texture":"#bottom","cullface":"down"} } },
    { "from": [0, 0, 14],  "to": [4, 2, 16],       "faces": { "north": {"texture":"#side"}, "east": {"texture":"#side"}, "south": {"texture":"#side","cullface":"south"}, "west": {"texture":"#side","cullface":"west"}, "down": {"texture":"#bottom","cullface":"down"} } },
    { "from": [0, 0, 12],  "to": [2, 2, 14],       "faces": { "north": {"texture":"#side"}, "east": {"texture":"#side"}, "west": {"texture":"#side","cullface":"west"}, "down": {"texture":"#bottom","cullface":"down"} } },
    { "from": [12, 0, 14], "to": [16, 2, 16],      "faces": { "north": {"texture":"#side"}, "east": {"texture":"#side","cullface":"east"}, "south": {"texture":"#side","cullface":"south"}, "west": {"texture":"#side"}, "down": {"texture":"#bottom","cullface":"down"} } },
    { "from": [14, 0, 12], "to": [16, 2, 14],      "faces": { "north": {"texture":"#side"}, "east": {"texture":"#side","cullface":"east"}, "west": {"texture":"#side"}, "down": {"texture":"#bottom","cullface":"down"} } }
  ]
}
'@
W (Join-Path $res 'models\block\tea_wok.json') $wok

# blockstate：碳焙框按 lit 切换；铁锅按 has_tea 切换（两个模型都用同一份）
W (Join-Path $res 'blockstates\tea_charcoal_stove.json') @'
{
  "variants": {
    "lit=false": { "model": "teacraft:block/tea_charcoal_stove" },
    "lit=true":  { "model": "teacraft:block/tea_charcoal_stove_lit" }
  }
}
'@
W (Join-Path $res 'blockstates\tea_wok.json') @'
{
  "variants": {
    "has_tea=false": { "model": "teacraft:block/tea_wok" },
    "has_tea=true":  { "model": "teacraft:block/tea_wok" }
  }
}
'@

# ---- 碳焙筐物品图：和炒茶锅一样用 3D 方块模型 + 等距视角 ----
#   千万别退回 item/generated —— 那样图标就是一块平板，看不出是个筐。
#   这里和炒茶锅一样从 tools/ 下的独立 json 读取，避免手改后被脚本覆盖。
$stoveItemModel = Join-Path $PSScriptRoot 'stove_item_model.json'
if (Test-Path -LiteralPath $stoveItemModel) {
    [System.IO.File]::WriteAllText((Join-Path $res "models\item\tea_charcoal_stove.json"), `
        (Get-Content -LiteralPath $stoveItemModel -Raw), (New-Object System.Text.UTF8Encoding($false)))
} else {
    Write-Warning "缺少 tools/stove_item_model.json，碳焙筐物品图未更新"
}
W (Join-Path $res "items\tea_charcoal_stove.json") `
  ("{`n  `"model`": {`n    `"type`": `"minecraft:model`",`n    `"model`": `"teacraft:item/tea_charcoal_stove`"`n  }`n}`n")

# ---- 炒茶锅物品图：3D 方块模型 + 原版等距视角（30/225）----
#   这个千万别退回 item/generated —— 那样图标就是一块平板，认不出是锅。
#   之前这里被本脚本覆盖过一次，导致已经修好的图标又变回平板。
[System.IO.File]::WriteAllText((Join-Path $res "models\item\tea_wok.json"), (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'wok_item_model.json') -Raw), (New-Object System.Text.UTF8Encoding($false)))
W (Join-Path $res "items\tea_wok.json") `
  ("{`n  `"model`": {`n    `"type`": `"minecraft:model`",`n    `"model`": `"teacraft:item/tea_wok`"`n  }`n}`n")

"stove + wok assets done"
