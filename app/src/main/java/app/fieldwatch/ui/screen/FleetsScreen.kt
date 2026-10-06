package app.fieldwatch.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import app.fieldwatch.ui.component.DecodeGlyph
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import app.fieldwatch.ui.component.FieldwatchFilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.component.FieldwatchDropdownField
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.Palette
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.nightIf
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.RuleKind
import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.domain.SignatureListSort
import app.fieldwatch.domain.groupedByClass
import app.fieldwatch.domain.sortedForCatalog
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.RadioClassBadge
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.SectionCard
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetsScreen(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    onCandidateDraftClosed: () -> Unit = {},
) {
    val draft = state.draftFleet
    if (draft != null) {
        val isNew = state.fleets.none { it.id == draft.id }
        var working by remember(draft.id) { mutableStateOf(draft) }
        var editingDecode by remember(draft.id) { mutableStateOf(false) }
        if (editingDecode) {
            val preview = state.devices.firstOrNull { working.id in it.fleetIds }
                ?: state.selected?.takeIf { working.id in it.fleetIds }
            DecodeFieldsScreen(
                fleet = working,
                previewDevice = preview,
                onSave = { decode ->
                    val next = working.copy(decode = decode)
                    working = next
                    vm.saveFleetKeepDraft(next)
                    editingDecode = false
                },
                onBack = { editingDecode = false },
            )
            return
        }
        FleetEditor(
            working,
            isNew = isNew,
            onSave = { fleet ->
                val bounce = vm.takeDraftFromCandidates()
                vm.upsertFleet(fleet) {
                    if (bounce) {
                        vm.startSignatureCandidates()
                        onCandidateDraftClosed()
                    }
                }
            },
            onCancel = {
                val bounce = vm.takeDraftFromCandidates()
                vm.cancelDraft()
                if (bounce) onCandidateDraftClosed()
            },
            onDelete = if (isNew) null else ({ vm.deleteFleet(draft.id) }),
            onOpenDecode = { current ->
                working = current
                editingDecode = true
            },
        )
        return
    }
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = { NestedTopBar("特征（${state.fleets.size}）") },
        floatingActionButton = {
            FloatingActionButton(onClick = vm::beginNewFleet) {
                Icon(Icons.Outlined.Add, "新建特征")
            }
        },
    ) { pad ->
        val sort = state.settings.signatureListSort
        val openClasses by vm.catalogOpenClasses.collectAsStateWithLifecycle()
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(
                    if (sort == SignatureListSort.CLASS) {
                        "点按类别以展开其特征。收藏 = 提示音。在筛选中隐藏家族，而不是这里。"
                    } else {
                        "点按以编辑。收藏 = 该家族出现时发出提示音。在筛选中隐藏家族，而不是这里。"
                    },
                    style = compactLine(12.sp, 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    FieldwatchFilterChip(
                        selected = sort == SignatureListSort.NAME,
                        onClick = { vm.setSignatureListSort(SignatureListSort.NAME) },
                        label = { Text("名称 A–Z") },
                    )
                    FieldwatchFilterChip(
                        selected = sort == SignatureListSort.CLASS,
                        onClick = { vm.setSignatureListSort(SignatureListSort.CLASS) },
                        label = { Text("类别 A–Z") },
                    )
                }
            }
            if (sort == SignatureListSort.CLASS) {
                state.fleets.groupedByClass().forEach { (kind, rows) ->
                    val classId = kind.name
                    val expanded = classId in openClasses
                    item(key = "class-$classId") {
                        SignatureClassHeader(
                            kind = kind,
                            count = rows.size,
                            colorIndex = rows.firstOrNull()?.colorIndex ?: 0,
                            expanded = expanded,
                            onToggle = { vm.toggleCatalogClass(classId) },
                        )
                    }
                    if (expanded) {
                        items(rows, key = { it.id }) { fleet ->
                            Box(Modifier.padding(start = 16.dp)) {
                                SignatureRow(fleet, state, vm)
                            }
                        }
                    }
                }
            } else {
                items(state.fleets.sortedForCatalog(sort), key = { it.id }) { fleet ->
                    SignatureRow(fleet, state, vm)
                }
            }
        }
    }
}

@Composable
private fun SignatureClassHeader(
    kind: SignatureClass,
    count: Int,
    colorIndex: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val accent = Color(Palette.color(colorIndex)).nightIf(LocalNightMode.current)
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioClassBadge(classKind = kind, accent = accent)
            Spacer(Modifier.width(10.dp))
            Text(
                kind.label(),
                style = compactLine(16.sp, 18.sp, FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (expanded) "▾  $count" else "▸  $count",
                style = compactLine(14.sp, 16.sp, FontWeight.Bold).copy(fontFamily = FontFamily.Monospace),
                color = accent,
            )
        }
    }
}

@Composable
private fun SignatureRow(fleet: Fleet, state: FieldwatchUi, vm: FieldwatchViewModel) {
    val color = Color(Palette.color(fleet.colorIndex)).nightIf(LocalNightMode.current)
    val liveHits = state.devices.count { fleet.id in it.fleetIds && !it.gone }
    Surface(
        onClick = { vm.editFleet(fleet) },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioClassBadge(
                classKind = fleet.kind,
                accent = color,
            )
            Spacer(Modifier.width(8.dp))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        fleet.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = compactLine(16.sp, 18.sp, FontWeight.SemiBold),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (fleet.decode != null) {
                        DecodeGlyph(
                            tint = color,
                            size = 14.dp,
                        )
                    }
                }
                Text(
                    "${fleet.kind.label()} · ${fleet.rules.size} rules · $liveHits live · ${if (fleet.matchAny) "OR" else "AND"}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = compactLine(11.sp, 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { vm.toggleWatchFleet(fleet) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    if (vm.isFleetWatched(fleet.id)) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    "当此特征出现时提示音",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetEditor(
    initial: Fleet,
    isNew: Boolean = false,
    onSave: (Fleet) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onOpenDecode: (Fleet) -> Unit = {},
) {
    var fleet by remember(initial.id) { mutableStateOf(initial) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(initial.decode) {
        fleet = fleet.copy(decode = initial.decode)
    }
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = {
            NestedTopBar(
                title = if (isNew) "新建特征" else "编辑特征",
                navigationIcon = { TextButton(onClick = onCancel) { Text("取消") } },
                actions = { TextButton(onClick = { onSave(fleet) }) { Text("保存") } },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("标识") {
            FieldwatchOutlinedField(fleet.name, { fleet = fleet.copy(name = it) }, "名称")
            FieldwatchOutlinedField(
                fleet.notes,
                { fleet = fleet.copy(notes = it) },
                "备注",
                supportingText = "会显示在匹配设备的设备详情中，以及分享 / AI 导出中。不是特别关注 — 不会在实时中显示“！”，也不是琥珀色卡片。",
                singleLine = false,
                minLines = 2,
            )
            FieldwatchOutlinedField(
                fleet.attentionNote,
                { fleet = fleet.copy(attentionNote = it) },
                "特别关注",
                supportingText = "可选。若此处不为空，匹配的设备会在实时中获得一个“！”，在详情中显示此琥珀色卡片，并在简报中显示一行。与上面的备注相互独立。",
                singleLine = false,
                minLines = 3,
            )
            }

            SectionCard("匹配") {
            var classMenu by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(classMenu, { classMenu = it }) {
                FieldwatchDropdownField("类别", fleet.kind.label(), classMenu)
                ExposedDropdownMenu(classMenu, { classMenu = false }) {
                    SignatureClass.visible.sortedBy { it.label().lowercase() }.forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(kind.label()) },
                            onClick = {
                                fleet = fleet.copy(kind = kind)
                                classMenu = false
                            },
                        )
                    }
                }
            }
            Text(
                "筛选 → 仅显示 / 隐藏这些。类别监测（查找标签、摄像头……）就是那些芯片 — 若想保存为预设，请使用另存当前为…。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("匹配任意规则（OR）", Modifier.weight(1f))
                FieldwatchSwitch(fleet.matchAny, { fleet = fleet.copy(matchAny = it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("按 OUI 聚类", Modifier.weight(1f))
                FieldwatchSwitch(fleet.clusterByOui, { fleet = fleet.copy(clusterByOui = it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("连续 MAC", Modifier.weight(1f))
                FieldwatchSwitch(fleet.sequentialMac, { fleet = fleet.copy(sequentialMac = it) })
            }
            FieldwatchOutlinedField(
                fleet.minPeers.toString(),
                { fleet = fleet.copy(minPeers = it.toIntOrNull() ?: 0) },
                "最少对端数（0 = 关闭）",
            )
            FieldwatchOutlinedField(
                fleet.peerWindowSec.toString(),
                { fleet = fleet.copy(peerWindowSec = it.toIntOrNull() ?: 60) },
                "对端窗口（秒）",
            )
            }

            SectionCard("颜色") {
            ColorPicker(fleet.colorIndex) { fleet = fleet.copy(colorIndex = it) }
            Text(
                "默认颜色按类别区分（红色渗透测试，琥珀色摄像头/ALPR，紫色手机/标签，青色可穿戴设备，绿色网状网络，橙色音频/眼镜，青色车载/车辆）。可更改任意行。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard("规则") {
            Text(
                "每条规则都有自己的开关。关闭会保留规则但不参与匹配。" +
                    "可用于在某条特征上静音嘈杂的 OUI 或名称，而无需删除它们。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            fleet.rules.forEachIndexed { index, rule ->
                RuleEditor(
                    rule = rule,
                    onChange = { next ->
                        val rules = fleet.rules.toMutableList()
                        rules[index] = next
                        fleet = fleet.copy(rules = rules)
                    },
                    onDelete = {
                        fleet = fleet.copy(rules = fleet.rules.filterIndexed { i, _ -> i != index })
                    },
                )
            }
            FieldwatchActionButton(
                onClick = {
                    fleet = fleet.copy(rules = fleet.rules + MatchRule(RuleKind.OUI, text = ""))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("添加规则") }
            }

            if (fleet.canHaveBleDecode()) {
                val decodeCount = fleet.decode?.fields?.size ?: 0
                SectionCard("解码字段") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDecode(fleet) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (decodeCount == 0) "无" else "$decodeCount 个字段",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "›",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "可选。此特征匹配后，将明文 BLE 字节映射为标签。加密负载保持十六进制。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                }
            }
            if (onDelete != null) {
                FieldwatchActionButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Delete, null)
                    Spacer(Modifier.padding(4.dp))
                    Text("删除特征")
                }
            }
        }
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除此特征？") },
            text = {
                Text(
                    if (initial.builtIn) {
                        "“${fleet.name}”是内置特征。删除它会移除匹配、其收藏和筛选芯片。在设置中恢复默认特征可找回原始集合。"
                    } else {
                        "“${fleet.name}”将被移除。匹配、其收藏和筛选芯片都会随之删除。此操作无法撤销。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Palette.fleet.forEachIndexed { index, argb ->
                val on = index == selected
                val fill = Color(argb).nightIf(LocalNightMode.current)
                Surface(
                    onClick = { onSelect(index) },
                    shape = RoundedCornerShape(8.dp),
                    color = fill,
                    border = BorderStroke(
                        width = if (on) 2.dp else 1.dp,
                        color = if (on) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (on) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = "已选颜色",
                                tint = if (fill.luminance() > 0.45f) {
                                    Color(0xFF12171C)
                                } else {
                                    Color.White
                                },
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditor(rule: MatchRule, onChange: (MatchRule) -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldwatchSwitch(
                rule.enabled,
                { onChange(rule.copy(enabled = it)) },
            )
            ExposedDropdownMenuBox(
                expanded,
                { expanded = it },
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 4.dp),
            ) {
                FieldwatchDropdownField("类型", ruleKindLabel(rule.kind), expanded)
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    RuleKind.entries.forEach { kind ->
                        DropdownMenuItem(text = { Text(ruleKindLabel(kind)) }, onClick = {
                            onChange(rule.copy(kind = kind))
                            expanded = false
                        })
                    }
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "删除规则") }
        }
        Column(
            Modifier.padding(top = 12.dp, start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        when (rule.kind) {
            RuleKind.OUI, RuleKind.MAC_PREFIX, RuleKind.NAME_CONTAINS, RuleKind.NAME_GLOB, RuleKind.SERVICE_UUID, RuleKind.VENDOR_IE_OUI -> {
                FieldwatchOutlinedField(
                    rule.text,
                    { onChange(rule.copy(text = it)) },
                    "值",
                )
            }
            RuleKind.MANUFACTURER_ID -> {
                FieldwatchOutlinedField(
                    if (rule.companyId == 0) "" else "0x%04X".format(rule.companyId),
                    {
                        val parsed = it.removePrefix("0x").removePrefix("0X").toIntOrNull(16) ?: 0
                        onChange(rule.copy(companyId = parsed))
                    },
                    "公司 ID（十六进制）",
                )
            }
            RuleKind.MANUFACTURER_DATA -> {
                FieldwatchOutlinedField(
                    if (rule.companyId == 0) "" else "0x%04X".format(rule.companyId),
                    {
                        val parsed = it.removePrefix("0x").removePrefix("0X").toIntOrNull(16) ?: 0
                        onChange(rule.copy(companyId = parsed))
                    },
                    "公司 ID（十六进制）",
                )
                FieldwatchOutlinedField(
                    rule.dataPrefixHex,
                    { onChange(rule.copy(dataPrefixHex = it)) },
                    "数据前缀（十六进制）",
                )
            }
            RuleKind.SERVICE_DATA -> {
                FieldwatchOutlinedField(
                    rule.text,
                    { onChange(rule.copy(text = it)) },
                    "服务 UUID（空 = 任意，包含）",
                )
                FieldwatchOutlinedField(
                    rule.dataPrefixHex,
                    { onChange(rule.copy(dataPrefixHex = it)) },
                    if (rule.text.isBlank()) "包含十六进制" else "数据前缀（十六进制）",
                )
            }
            RuleKind.RADIO_KIND -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Wi-Fi", Modifier.padding(end = 8.dp))
                    FieldwatchSwitch(rule.radio != RadioKind.BLE, { onChange(rule.copy(radio = if (it) RadioKind.WIFI else RadioKind.BLE)) })
                }
            }
            RuleKind.HIDDEN_SSID -> Text("匹配隐藏 SSID", style = MaterialTheme.typography.bodySmall)
        }
        }
    }
}

private fun ruleKindLabel(kind: RuleKind): String = when (kind) {
    RuleKind.OUI -> "OUI"
    RuleKind.MAC_PREFIX -> "MAC 前缀"
    RuleKind.NAME_CONTAINS -> "名称包含"
    RuleKind.NAME_GLOB -> "名称通配"
    RuleKind.SERVICE_UUID -> "服务 UUID"
    RuleKind.SERVICE_DATA -> "服务数据"
    RuleKind.MANUFACTURER_ID -> "制造商 ID"
    RuleKind.MANUFACTURER_DATA -> "制造商数据"
    RuleKind.RADIO_KIND -> "无线设备类型"
    RuleKind.HIDDEN_SSID -> "隐藏 SSID"
    RuleKind.VENDOR_IE_OUI -> "厂商 IE OUI"
}

private fun compactLine(
    size: androidx.compose.ui.unit.TextUnit,
    line: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontSize = size,
    lineHeight = line,
    fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)
