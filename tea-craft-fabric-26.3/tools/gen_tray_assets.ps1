# 生成晒盘方块模型与 blockstate
#   盘沿凸起、中间下沉：
#     底板   0 ~ 1.5px（顶面用盘面贴图的"内部编织区"，避开画出来的边框）
#     围边   1.5 ~ 3px，1px 厚四道墙 → 从侧面/斜上方看得到凹槽
#     茶叶   1.51px 平面，缩到 1.6~14.4（80%），落在盘底
# 用法： powershell -File tools\gen_tray_assets.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$res  = Join-Path $root 'src\main\resources\assets\teacraft'

function W([string]$path, [string]$text) {
    $dir = Split-Path -Parent $path
    if (!(Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
}

# 底板：只有顶面用盘面贴图（且只取中间编织区），四周/底面用竹篾
$floor = @'
    {
      "from": [0, 0, 0],
      "to": [16, 1.5, 16],
      "faces": {
        "up":    { "texture": "#traytop", "uv": [2, 2, 14, 14] },
        "down":  { "texture": "#side",    "uv": [0, 0, 16, 16] },
        "north": { "texture": "#side",    "uv": [0, 0, 16, 1.5] },
        "south": { "texture": "#side",    "uv": [0, 0, 16, 1.5] },
        "west":  { "texture": "#side",    "uv": [0, 0, 16, 1.5] },
        "east":  { "texture": "#side",    "uv": [0, 0, 16, 1.5] }
      }
    }
'@

# 四道围边（1px 厚，1.5 ~ 3px 高）
$rimN = '    {
      "from": [0, 1.5, 0],
      "to": [16, 3, 1],
      "faces": {
        "up":    { "texture": "#side", "uv": [0, 0, 16, 1] },
        "north": { "texture": "#side", "uv": [0, 0, 16, 1.5] },
        "south": { "texture": "#side", "uv": [0, 0, 16, 1.5] },
        "east":  { "texture": "#side", "uv": [0, 0, 1, 1.5] },
        "west":  { "texture": "#side", "uv": [0, 0, 1, 1.5] }
      }
    }'
$rimS = '    {
      "from": [0, 1.5, 15],
      "to": [16, 3, 16],
      "faces": {
        "up":    { "texture": "#side", "uv": [0, 0, 16, 1] },
        "north": { "texture": "#side", "uv": [0, 0, 16, 1.5] },
        "south": { "texture": "#side", "uv": [0, 0, 16, 1.5] },
        "east":  { "texture": "#side", "uv": [0, 0, 1, 1.5] },
        "west":  { "texture": "#side", "uv": [0, 0, 1, 1.5] }
      }
    }'
$rimW = '    {
      "from": [0, 1.5, 1],
      "to": [1, 3, 15],
      "faces": {
        "up":    { "texture": "#side", "uv": [0, 0, 1, 14] },
        "west":  { "texture": "#side", "uv": [0, 0, 14, 1.5] },
        "east":  { "texture": "#side", "uv": [0, 0, 14, 1.5] },
        "north": { "texture": "#side", "uv": [0, 0, 1, 1.5] },
        "south": { "texture": "#side", "uv": [0, 0, 1, 1.5] }
      }
    }'
$rimE = '    {
      "from": [15, 1.5, 1],
      "to": [16, 3, 15],
      "faces": {
        "up":    { "texture": "#side", "uv": [0, 0, 1, 14] },
        "west":  { "texture": "#side", "uv": [0, 0, 14, 1.5] },
        "east":  { "texture": "#side", "uv": [0, 0, 14, 1.5] },
        "north": { "texture": "#side", "uv": [0, 0, 1, 1.5] },
        "south": { "texture": "#side", "uv": [0, 0, 1, 1.5] }
      }
    }'

# 茶叶：落在盘底上，占 80%
$leaves = '    {
      "from": [1.6, 1.51, 1.6],
      "to": [14.4, 1.52, 14.4],
      "faces": {
        "up": { "texture": "#leaves", "uv": [0, 0, 16, 16] }
      }
    }'

$stages = @(
    @{ key = 'empty';    leaf = $null },
    @{ key = 'fresh';    leaf = 'tea_tray_top_fresh' },
    @{ key = 'withered'; leaf = 'tea_tray_top_withered' },
    @{ key = 'shaken';   leaf = 'tea_tray_top_shaken' },
    @{ key = 'killgreen'; leaf = 'tea_tray_top_killgreen' }
)

foreach ($s in $stages) {
    $texLines = @(
        '    "traytop": "teacraft:block/tea_tray_top_empty"',
        '    "side": "teacraft:block/tea_tray_side"',
        '    "particle": "teacraft:block/tea_tray_top_empty"'
    )
    if ($null -ne $s.leaf) { $texLines += ('    "leaves": "teacraft:block/' + $s.leaf + '"') }
    $texBlock = ($texLines -join ",`n")

    $elems = @($floor, $rimN, $rimS, $rimW, $rimE)
    if ($null -ne $s.leaf) { $elems += $leaves }
    $elemBlock = ($elems -join ",`n")

    $json = "{`n  `"textures`": {`n" + $texBlock + "`n  },`n  `"elements`": [`n" + $elemBlock + "`n  ]`n}`n"
    W (Join-Path $res "models\block\tea_tray_$($s.key).json") $json
}

# blockstate：count 在前、stage 在后（原版按属性名排序）
$names = @('empty', 'fresh', 'withered', 'shaken', 'killgreen')
$lines = for ($st = 0; $st -le 4; $st++) {
    # count 上限跟着 TeaTrayBlock.MAX_LEAVES（统一 32）
    for ($c = 0; $c -le 32; $c++) {
        '    "count=' + $c + ',stage=' + $st + '": { "model": "teacraft:block/tea_tray_' + $names[$st] + '" }'
    }
}
$bs = "{`n  `"variants`": {`n" + ($lines -join ",`n") + "`n  }`n}`n"
W (Join-Path $res "blockstates\tea_tray.json") $bs

# 注意：items\tea_tray.json 归 gen_tray_shake.ps1 管（含摇动动画的组合定义），这里不要写

"tray models: $($stages.Count)   blockstate variants: $($lines.Count)   （盘沿凸起 / 中间下沉）"
