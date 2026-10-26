# 生成 茶工艺 的全部资源 JSON（物品定义 / 物品模型 / 方块模型 / 方块状态）
# 用法： powershell -File tools\gen_assets.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$res  = Join-Path $root 'src\main\resources\assets\teacraft'
$art  = Join-Path $root 'art'

function W([string]$path, [string]$text) {
    $dir = Split-Path -Parent $path
    if (!(Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    # UTF-8 无 BOM：JSON 用得到，也避免任何解析器把 BOM 当内容
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
}

# ---------------- 贴图 ----------------
$itemTexDir  = Join-Path $res 'textures\item'
$blockTexDir = Join-Path $res 'textures\block'
New-Item -ItemType Directory -Force -Path $itemTexDir, $blockTexDir | Out-Null

$stageItems = @(
    'fresh_leaf',
    'green_2_spreading','green_3_killgreen','green_4_rolling','green_5_drying','green_6_finished',
    'black_2_withering','black_3_rolling','black_4_fermenting','black_5_firstbake','black_6_finished',
    'dancong_2_sunwither','dancong_3_shaking','dancong_4_killgreen','dancong_5_rolling','dancong_6_roasting','dancong_7_primary','dancong_8_finished'
)
$originItems = @('fresh_leaf_tropical','fresh_leaf_subtropical','fresh_leaf_temperate','fresh_leaf_boreal','fresh_leaf_polar')
$machines = @('spreading_rack','withering_rack','sun_yard','kill_green_pan','rolling_machine','fermentation_box','roasting_oven','refining_table')

foreach ($id in $stageItems) {
    Copy-Item -LiteralPath (Join-Path $art "item\$id.png") -Destination (Join-Path $itemTexDir "$id.png") -Force
}
# 五个产地鲜叶共用同一张图（需求：每种茶 1 阶段新鲜茶叶都一样，对应 1.png）
foreach ($id in $originItems) {
    Copy-Item -LiteralPath (Join-Path $art "item\fresh_leaf.png") -Destination (Join-Path $itemTexDir "$id.png") -Force
}
for ($i = 0; $i -le 3; $i++) {
    Copy-Item -LiteralPath (Join-Path $art "plant\tea_bush_stage$i.png") -Destination (Join-Path $blockTexDir "tea_bush_stage$i.png") -Force
}

# ---------------- 物品定义 + 物品模型 ----------------
$plainItems = @() + $originItems + ($stageItems | Where-Object { $_ -ne 'fresh_leaf' })
$blockItems = @('tea_bush') + $machines

foreach ($id in $plainItems) {
    W (Join-Path $res "items\$id.json") "{`n  `"model`": {`n    `"type`": `"minecraft:model`",`n    `"model`": `"teacraft:item/$id`"`n  }`n}`n"
    W (Join-Path $res "models\item\$id.json") "{`n  `"parent`": `"minecraft:item/generated`",`n  `"textures`": {`n    `"layer0`": `"teacraft:item/$id`"`n  }`n}`n"
}

foreach ($id in $machines) {
    W (Join-Path $res "items\$id.json") "{`n  `"model`": {`n    `"type`": `"minecraft:model`",`n    `"model`": `"teacraft:block/$id`"`n  }`n}`n"
    W (Join-Path $res "models\block\$id.json") "{`n  `"parent`": `"minecraft:block/cube_all`",`n  `"textures`": {`n    `"all`": `"teacraft:block/$id`"`n  }`n}`n"
    W (Join-Path $res "blockstates\$id.json") "{`n  `"variants`": {`n    `"`": { `"model`": `"teacraft:block/$id`" }`n  }`n}`n"
}

# 茶树：物品用第 4 阶段贴图做成普通图标，方块用十字模型
W (Join-Path $res "items\tea_bush.json") "{`n  `"model`": {`n    `"type`": `"minecraft:model`",`n    `"model`": `"teacraft:item/tea_bush`"`n  }`n}`n"
W (Join-Path $res "models\item\tea_bush.json") "{`n  `"parent`": `"minecraft:item/generated`",`n  `"textures`": {`n    `"layer0`": `"teacraft:block/tea_bush_stage3`"`n  }`n}`n"

$variants = for ($i = 0; $i -le 3; $i++) {
    W (Join-Path $res "models\block\tea_bush_stage$i.json") "{`n  `"parent`": `"minecraft:block/cross`",`n  `"textures`": {`n    `"cross`": `"teacraft:block/tea_bush_stage$i`",`n    `"particle`": `"teacraft:block/tea_bush_stage$i`"`n  }`n}`n"
    '    "age=' + $i + '": { "model": "teacraft:block/tea_bush_stage' + $i + '" }'
}
$bushBlockstate = "{`n  `"variants`": {`n" + ($variants -join ",`n") + "`n  }`n}`n"
W (Join-Path $res "blockstates\tea_bush.json") $bushBlockstate

$count = (Get-ChildItem -LiteralPath $res -Recurse -Filter *.json | Measure-Object).Count
"assets written: $count json files"
"item textures: $((Get-ChildItem -LiteralPath $itemTexDir -Filter *.png).Count)   block textures: $((Get-ChildItem -LiteralPath $blockTexDir -Filter *.png).Count)"
