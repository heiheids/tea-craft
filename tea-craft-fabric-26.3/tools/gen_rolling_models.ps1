# 生成揉茶匾的模型与 blockstate
#   外形与晒盘一致：盘沿凸起（1.5~3px）、中间下沉（底板 0~1.5px），共 3 像素高
#   阶段：0 空 / 1 杀青叶 / 2 揉捻完成 / 3 发酵中
#     1、2 叠茶叶贴图；3 叠「发酵」贴图（白布包茶）—— 需求：贴图自动换成这个
# 用法： powershell -File tools\gen_rolling_models.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$res  = Join-Path $root 'src\main\resources\assets\teacraft'

function W([string]$path, [string]$text) {
    $dir = Split-Path -Parent $path
    if (!(Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
}

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

$rims = @(
'    {
      "from": [0, 1.5, 0],
      "to": [16, 3, 1],
      "faces": {
        "up":    { "texture": "#side", "uv": [0, 0, 16, 1] },
        "north": { "texture": "#side", "uv": [0, 0, 16, 1.5] },
        "south": { "texture": "#side", "uv": [0, 0, 16, 1.5] },
        "east":  { "texture": "#side", "uv": [0, 0, 1, 1.5] },
        "west":  { "texture": "#side", "uv": [0, 0, 1, 1.5] }
      }
    }',
'    {
      "from": [0, 1.5, 15],
      "to": [16, 3, 16],
      "faces": {
        "up":    { "texture": "#side", "uv": [0, 0, 16, 1] },
        "north": { "texture": "#side", "uv": [0, 0, 16, 1.5] },
        "south": { "texture": "#side", "uv": [0, 0, 16, 1.5] },
        "east":  { "texture": "#side", "uv": [0, 0, 1, 1.5] },
        "west":  { "texture": "#side", "uv": [0, 0, 1, 1.5] }
      }
    }',
'    {
      "from": [0, 1.5, 1],
      "to": [1, 3, 15],
      "faces": {
        "up":    { "texture": "#side", "uv": [0, 0, 1, 14] },
        "west":  { "texture": "#side", "uv": [0, 0, 14, 1.5] },
        "east":  { "texture": "#side", "uv": [0, 0, 14, 1.5] },
        "north": { "texture": "#side", "uv": [0, 0, 1, 1.5] },
        "south": { "texture": "#side", "uv": [0, 0, 1, 1.5] }
      }
    }',
'    {
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
)

# 茶/发酵物：落在盘底，占 80%
$overlay = '    {
      "from": [1.6, 1.51, 1.6],
      "to": [14.4, 1.52, 14.4],
      "faces": {
        "up": { "texture": "#content", "uv": [0, 0, 16, 16] }
      }
    }'

# 需求：「开始发酵就用有布的贴图，而不是结束后」→ rolled（刚开始发酵）就用布
$stages = @(
    @{ key = 'empty';     content = $null },
    @{ key = 'killgreen'; content = 'tea_rolling_tray_tea' },
    @{ key = 'rolled';    content = 'tea_rolling_tray_ferment' },
    @{ key = 'fermented'; content = 'tea_rolling_tray_ferment_done' }
)

foreach ($s in $stages) {
    $texLines = @(
        '    "traytop": "teacraft:block/tea_rolling_tray_top_empty"',
        '    "side": "teacraft:block/tea_rolling_tray_side"',
        '    "particle": "teacraft:block/tea_rolling_tray_top_empty"'
    )
    if ($null -ne $s.content) { $texLines += ('    "content": "teacraft:block/' + $s.content + '"') }
    $texBlock = ($texLines -join ",`n")

    $elems = @($floor) + $rims
    if ($null -ne $s.content) { $elems += $overlay }
    $elemBlock = ($elems -join ",`n")

    $json = "{`n  `"textures`": {`n" + $texBlock + "`n  },`n  `"elements`": [`n" + $elemBlock + "`n  ]`n}`n"
    W (Join-Path $res "models\block\tea_rolling_tray_$($s.key).json") $json
}

# blockstate：count 在前、stage 在后（原版按属性名排序）
$names = @('empty', 'killgreen', 'rolled', 'fermented')
$lines = for ($st = 0; $st -le 3; $st++) {
    # count 上限要跟着 TeaRollingTrayBlock.MAX_LEAVES（现在是 32）
    for ($c = 0; $c -le 32; $c++) {
        '    "count=' + $c + ',stage=' + $st + '": { "model": "teacraft:block/tea_rolling_tray_' + $names[$st] + '" }'
    }
}
$bs = "{`n  `"variants`": {`n" + ($lines -join ",`n") + "`n  }`n}`n"
W (Join-Path $res "blockstates\tea_rolling_tray.json") $bs

# 物品图标：扁平图标（与晒盘 icon 一致）。
#   不能直接 parent 到方块模型 —— 那样模型自己没有 textures，
#   自检的「模型引用的贴图存在」会失败（它只看本模型的 layer0/all/cross/particle）。
#   注意：PowerShell 的单引号不解释 `n，必须用双引号拼 JSON
$itemModel = "{`n  `"parent`": `"minecraft:item/generated`",`n  `"textures`": {`n    `"layer0`": `"teacraft:item/tea_rolling_tray_icon_0`"`n  }`n}`n"
W (Join-Path $res "models\item\tea_rolling_tray.json") $itemModel

# 物品定义（1.21.4+ 的 items/ 层，和 models/item/ 是两回事）：
#   少了这个文件，自检会报 item_def_tea_rolling_tray -> 0 个模型
$itemDef = "{`n  `"model`": {`n    `"type`": `"minecraft:model`",`n    `"model`": `"teacraft:item/tea_rolling_tray`"`n  }`n}`n"
W (Join-Path $res "items\tea_rolling_tray.json") $itemDef

"rolling tray models: $($stages.Count)   blockstate variants: $($lines.Count)   （有边框、3 像素高）"
