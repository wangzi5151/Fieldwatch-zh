package app.fieldwatch.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import app.fieldwatch.ui.component.DecodeGlyph
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fieldwatch.domain.CodDecoder
import app.fieldwatch.domain.FamilyVerdict
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.DeviceExplain
import app.fieldwatch.domain.Palette
import app.fieldwatch.domain.RadioDb
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.Rssi
import app.fieldwatch.domain.ServiceDataRecord
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SignatureFamilyHint
import app.fieldwatch.domain.SignatureFieldDecoder
import app.fieldwatch.domain.hexSpaced
import app.fieldwatch.domain.label
import app.fieldwatch.radio.BleAdParser
import app.fieldwatch.ui.RadioKindMark
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.theme.Amber
import app.fieldwatch.ui.theme.Cyan
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.nightIf
import app.fieldwatch.ui.component.PresenceTrack
import app.fieldwatch.ui.component.Sparkline
import app.fieldwatch.ui.component.StickyHeight
import app.fieldwatch.ui.component.rssiColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    device: Sighting,
    vm: FieldwatchViewModel,
    watched: Boolean,
    onBack: () -> Unit,
    onCreateFleet: () -> Unit,
    onHunt: () -> Unit,
    demoMode: Boolean = false,
) {
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    val accent = (device.fleetIds.firstOrNull()
        ?.let { Color(Palette.color(vm.fleetColor(it))) }
        ?: rssiColor(device.rssi))
        .nightIf(LocalNightMode.current)
    val facts = device.facts
    val familyHint by vm.familyHint.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val custom = vm.watchLabelFor(device.key)
                    val title = custom?.takeIf { it.isNotBlank() }
                        ?: device.listTitle(device.fleetIds.map { vm.fleetName(it) })
                    Text(MacUtil.redactMacIn(title, device.mac, demoMode), maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { vm.toggleWatchDevice(device) }) {
                        Icon(if (watched) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, "关注")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(MacUtil.screenMac(device.mac, demoMode), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
            if (device.gone) {
                Text(
                    "已不在空中。这是我们最后一次侦听到的详情。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            var nameDraft by remember(device.key) {
                mutableStateOf(vm.watchLabelFor(device.key).orEmpty())
            }
            var lastSaved by remember(device.key) {
                mutableStateOf(vm.watchLabelFor(device.key).orEmpty())
            }
            var editingName by remember(device.key) { mutableStateOf(false) }
            val draftLabel = RadioBookmarks.clip(nameDraft)
            val nameIsSaved = lastSaved.isNotBlank() && draftLabel == lastSaved
            val canName = RadioBookmarks.canSetCustomName(device)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (lastSaved.isNotBlank()) {
                        Text(
                            "自定义名称",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(lastSaved, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "广播",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            device.name.ifBlank { "无广播名称" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (device.name.isNotBlank()) {
                        Text(
                            "广播名称",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(device.name, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            "名称",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "无广播名称",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (canName) {
                    IconButton(onClick = { editingName = !editingName }) {
                        Icon(
                            Icons.Outlined.Edit,
                            if (editingName) "隐藏自定义名称" else "自定义名称",
                        )
                    }
                }
            }
            if (editingName && canName) {
                FieldwatchOutlinedField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(RadioBookmarks.MAX_NAME) },
                    label = "自定义名称",
                    supportingText = RadioBookmarks.customNameHint(device),
                )
                FieldwatchActionButton(
                    onClick = {
                        vm.saveRadioName(device, nameDraft)
                        nameDraft = draftLabel
                        lastSaved = draftLabel
                        scope.launch {
                            snackbarHostState.showSnackbar("已保存为 $draftLabel")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = nameDraft.isNotBlank() && !nameIsSaved,
                ) {
                    if (nameIsSaved) {
                        Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.padding(4.dp))
                        Text("已保存")
                    } else {
                        Text("保存名称")
                    }
                }
            }

            var notesDraft by remember(device.key) {
                mutableStateOf(vm.watchObserverNoteFor(device.key).orEmpty())
            }
            var lastSavedNotes by remember(device.key) {
                mutableStateOf(vm.watchObserverNoteFor(device.key).orEmpty())
            }
            var editingNotes by remember(device.key) { mutableStateOf(false) }
            val draftNotes = RadioBookmarks.clipNotes(notesDraft)
            val notesIsSaved = draftNotes == lastSavedNotes
            if (canName || lastSavedNotes.isNotBlank()) {
                ObserverNotesCard(
                    notes = lastSavedNotes,
                    canEdit = canName,
                    editing = editingNotes && canName,
                    draft = notesDraft,
                    onToggleEdit = { editingNotes = !editingNotes },
                    onDraftChange = { notesDraft = it.take(RadioBookmarks.MAX_NOTES) },
                    onSave = {
                        vm.saveRadioNotes(device, notesDraft)
                        notesDraft = draftNotes
                        lastSavedNotes = draftNotes
                        if (lastSaved.isBlank() && draftNotes.isNotBlank()) {
                            val suggest = RadioBookmarks.suggestLabel(
                                device,
                                device.fleetIds.map { vm.fleetName(it) },
                            )
                            lastSaved = suggest
                            nameDraft = suggest
                        }
                        editingNotes = false
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (draftNotes.isBlank()) "观察者备注已清除" else "观察者备注已保存",
                            )
                        }
                    },
                    saveEnabled = !notesIsSaved,
                    saved = notesIsSaved && lastSavedNotes.isNotBlank(),
                )
            }
            if (canName) {
                var mineOn by remember(device.key) { mutableStateOf(vm.isMine(device.key)) }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("我的", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "开启后，此设备保持列出且不发出提示音。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FieldwatchSwitch(
                            checked = mineOn,
                            onCheckedChange = { on ->
                                vm.setRadioMine(device, on)
                                mineOn = on
                                if (on && lastSaved.isBlank()) {
                                    val suggest = RadioBookmarks.suggestLabel(
                                        device,
                                        device.fleetIds.map { vm.fleetName(it) },
                                    )
                                    lastSaved = suggest
                                    nameDraft = suggest
                                }
                                if (on) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "已标记为你的。开启期间不发出提示音。仍会列出。",
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }

            val guess = DeviceExplain.guess(device, device.fleetIds.map { vm.fleetName(it) })
            StickyHeight(device.key to "guess") { GuessCard(guess) }
            val attention = vm.attentionNotesFor(device)
            if (attention.isNotEmpty()) {
                StickyHeight(device.key to "attention") { ExtraAttentionCard(attention) }
            }
            val notes = vm.signatureNotesFor(device)
            if (notes.isNotEmpty()) {
                StickyHeight(device.key to "notes") { SignatureNotesCard(notes) }
            }

            StickyHeight(device.key to "identity") {
                Section("标识")
                Meta(
                    "无线设备",
                    if (device.kind == RadioKind.WIFI) {
                        "Wi-Fi 接入点（正在广播网络）"
                    } else {
                        "低功耗蓝牙广播设备"
                    },
                )
                Meta("地址", DeviceExplain.addressExplain(device))
                vendorLine(device)?.let { Meta("制造商", it) }
                    ?: Meta("OUI（厂商前缀）", "${device.oui} — 无 IEEE 匹配；随机化地址通常没有。")
                if (device.hiddenSsid) {
                    Meta("网络名称（SSID）", "隐藏 — 接入点正在广播但未发布名称")
                }
            }

            StickyHeight(device.key to "signal") {
                Section("信号")
                if (device.gone) {
                    Meta("此处信号强度（RSSI）", "不可用")
                    Meta(
                        "最后侦听到",
                        buildString {
                            append(fmt.format(Date(device.lastSeen)))
                            Rssi.lastMeasured(device.rssi, device.rssiHistory)?.let {
                                append(" 为 $it dBm")
                            }
                        },
                    )
                } else {
                    Meta("此处信号强度（RSSI）", DeviceExplain.rssiExplain(device.rssi))
                    Text(
                        "此处越接近 0 dBm 信号越强，并不代表距离。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Meta(
                    "本次会话侦听到的范围",
                    Rssi.sessionRange(device.rssiMin, device.rssiMax, device.rssiHistory),
                )
                facts.txPowerDbm?.let {
                    Meta("声称的发射功率", "$it dBm — 它声称的发射强度，并不代表距离")
                }
                if (device.channel != 0 || device.frequencyMhz != 0) {
                    Meta(
                        "信道 / 频率",
                        buildString {
                            if (device.channel != 0) append("信道 ${device.channel}")
                            if (device.frequencyMhz != 0) {
                                if (isNotEmpty()) append("  ·  ")
                                append("${device.frequencyMhz} MHz")
                            }
                            facts.channelWidth?.let { append("  ·  宽 $it") }
                        },
                    )
                }
                facts.wifiStandard?.let { Meta("Wi-Fi 代际", it) }
                if (facts.centerFreq0 != null || facts.centerFreq1 != null) {
                    Meta(
                        "中心频率",
                        listOfNotNull(
                            facts.centerFreq0?.let { "$it MHz" },
                            facts.centerFreq1?.let { "$it MHz" },
                        ).joinToString("  ·  "),
                    )
                }
            }

            if (device.kind == RadioKind.BLE) {
                StickyHeight(device.key to "ble") {
                Section("蓝牙广播")
                facts.primaryPhy?.let {
                    val phys = listOfNotNull(it, facts.secondaryPhy).distinct()
                    Meta("无线 PHY", phys.joinToString(" / ") { phy -> DeviceExplain.phyExplain(phy) })
                }
                facts.connectable?.let {
                    Meta(
                        "可连接",
                        if (it) "是 — 手机可以建立 BLE 连接"
                        else "否 — 仅广播（你能侦听到它，但无法通过本次扫描加入）",
                    )
                }
                facts.advertisingIntervalMs?.let {
                    Meta(
                        "广播频率",
                        "%.0f ms 每次广播间隔（越小 = 空中越频繁）".format(it),
                    )
                }
                facts.periodicIntervalMs?.let {
                    Meta("周期性广播", "%.0f ms".format(it))
                }
                facts.advFlags?.let { flags ->
                    Meta("可发现性", DeviceExplain.flagsExplain(flags))
                    Meta("标志（原始）", "0x%02X".format(flags), mono = true)
                }
                facts.appearance?.let { value ->
                    val name = RadioDb.appearance(value)
                    Meta(
                        "它自称的类型（Appearance）",
                        name?.let { "$it\n设备发布此 GAP 外观代码以描述自身。" }
                            ?: "未列出的外观 0x%04X".format(value),
                    )
                    Meta("外观代码", "0x%04X".format(value), mono = true)
                }
                CodDecoder.decodeOrNull(facts.deviceClass)?.let { cod ->
                    Meta(
                        "经典蓝牙类别",
                        buildString {
                            append(cod.major)
                            if (cod.minor.isNotBlank()) append(" / ").append(cod.minor)
                            append("\n这是经典蓝牙使用的设备类别位域。")
                            if (cod.services.isNotEmpty()) {
                                append("\n还提供： ")
                                append(cod.services.joinToString(", "))
                            }
                        },
                    )
                }
                }
            }

            if (device.kind == RadioKind.WIFI) {
                StickyHeight(device.key to "wifi") {
                    Section("Wi-Fi 接入点")
                    facts.security?.let {
                        Meta("加密 / 登录", DeviceExplain.wifiSecurityExplain(it))
                        if (it.isNotBlank()) Meta("安全字符串", it, mono = true)
                    }
                    facts.supportedRates?.let {
                        Meta("支持的速率", "$it Mbps  （* = 必需基础速率）")
                    }
                    facts.capabilities?.takeIf { it.isNotBlank() && it != facts.security }?.let {
                        Meta("能力字符串", it, mono = true)
                    }
                }
            }

            if (device.serviceUuids.isNotEmpty() || facts.serviceData.isNotEmpty()) {
                StickyHeight(device.key to "services") {
                    if (device.serviceUuids.isNotEmpty()) {
                        Section("它提供的服务")
                        Meta(
                            "服务 ID",
                            device.serviceUuids.joinToString("\n") { uuid ->
                                DeviceExplain.uuidGloss(uuid)?.let { "$uuid  ·  $it" } ?: uuid
                            },
                            mono = true,
                        )
                    }
                    facts.serviceData.forEach { sd ->
                        val decoded = app.fieldwatch.domain.AdvPayloadDecoder.decodeService(sd)
                        decoded.forEach { field -> Meta(field.label, field.value) }
                        Meta(
                            serviceDataHeading(sd),
                            sd.dataHex.hexSpaced().ifBlank { "（空）" },
                            mono = true,
                        )
                    }
                }
            }

            if (device.kind == RadioKind.BLE || device.kind == RadioKind.WIFI) {
                val aircraft = device.payloadAircraft?.trim().orEmpty()
                if (aircraft.isNotEmpty()) Meta("飞行器", aircraft)
                val fleets = vm.ui.value.fleets
                val decoded = remember(device.key, device.facts, device.fleetIds) {
                    SignatureFieldDecoder.decodeSighting(device, fleets)
                }
                val mapped = device.fleetIds.mapNotNull { id -> fleets.find { it.id == id && it.decode != null } }
                val hasPayload = device.facts.mfgRecords.isNotEmpty() ||
                    device.manufacturerDataHex.isNotBlank() ||
                    device.facts.serviceData.isNotEmpty()
                if (decoded.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DecodeGlyph(
                            tint = MaterialTheme.colorScheme.primary,
                            size = 16.dp,
                        )
                        Text(
                            "已解码字段",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val multi = decoded.map { it.fleetId }.distinct().size > 1
                    decoded.forEach { row ->
                        Meta(if (multi) "${row.fleetName} · ${row.label}" else row.label, row.display)
                        if (row.note.isNotBlank()) {
                            Text(
                                row.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else if (mapped.isNotEmpty()) {
                    val govee = mapped.any { it.id == "fleet-govee" }
                    Text(
                        when {
                            hasPayload && govee ->
                                "解码字段未应用到这条广播（负载过短、公司 ID 不同或布局不同）。Govee 灯具通常只发送名称；湿度计为 H5074/H5075/H510x。原始字节见下方。"
                            hasPayload ->
                                "解码字段未应用到这条广播（负载过短、公司 ID 不同或布局不同）。原始字节见下方。"
                            govee ->
                                "此特征有解码映射，但这条广播没有可供解析的厂商或服务负载。许多 Govee 灯具只广播名称。"
                            else ->
                                "此特征有解码映射，但这条广播没有可供解析的厂商或服务负载。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val mfg = facts.mfgRecords.ifEmpty {
                device.manufacturerId?.let {
                    listOf(app.fieldwatch.domain.MfgRecord(it, device.manufacturerDataHex))
                } ?: emptyList()
            }
            if (mfg.isNotEmpty()) {
                StickyHeight(device.key to "mfg") {
                    Section("广告中的厂商数据")
                    mfg.forEach { rec ->
                        val company = RadioDb.company(rec.companyId) ?: "不在蓝牙公司列表中"
                        Meta(
                            "Bluetooth 公司 0x%04X".format(rec.companyId),
                            "$company\n此 ID 由 Bluetooth SIG 分配，并携带在厂商特定数据中。",
                        )
                        val decoded = BleAdParser.mfgDecodedFields(rec)
                        decoded.forEach { (k, v) -> Meta(k, v) }
                        if (rec.dataHex.isNotBlank()) {
                            Meta("原始负载（${rec.dataHex.length / 2} 字节）", rec.dataHex.hexSpaced(), mono = true)
                        }
                    }
                }
            }

            if (facts.vendorIes.isNotEmpty() || device.vendorIeOuis.isNotEmpty()) {
                StickyHeight(device.key to "ies") {
                    Section("Wi-Fi 厂商标签")
                    val rows = facts.vendorIes.ifEmpty {
                        device.vendorIeOuis.map { app.fieldwatch.domain.VendorIeRecord(it, -1, "") }
                    }
                    rows.forEach { ie ->
                        val org = RadioDb.vendorForOui24(ie.oui)
                        val type = if (ie.type >= 0) " type %d".format(ie.type) else ""
                        Meta(
                            "厂商 OUI ${ie.oui}$type",
                            buildString {
                                append(org ?: "未知 IEEE OUI")
                                append(" — 额外的接入点信息元素，不是 SSID。")
                                if (ie.dataHex.isNotBlank()) {
                                    append("\n")
                                    append(ie.dataHex.hexSpaced())
                                }
                            },
                        )
                    }
                }
            }

            StickyHeight(device.key to "session") {
                Section("会话")
                Meta("首次侦听到", fmt.format(Date(device.firstSeen)))
                Meta("最后侦听到", fmt.format(Date(device.lastSeen)))
                Meta("命中次数", device.hitCount.toString())
                Geo.screenCoord(device.latitude, device.longitude, demoMode)?.let { Meta("最后定位", it) }
                if (device.fleetIds.isNotEmpty()) {
                    Meta(
                        "匹配的特征",
                        device.fleetIds.joinToString("\n") { id ->
                            val name = vm.fleetName(id)
                            if (vm.fleetHasDecode(id)) "$name  ⬡" else name
                        },
                    )
                }
                if (device.rawHex.isNotBlank() && device.kind == RadioKind.BLE) {
                    Meta("原始广播", device.rawHex.hexSpaced())
                }
            }

            Text("信号趋势", style = MaterialTheme.typography.titleSmall)
            Sparkline(device.rssiHistory, accent, modifier = Modifier.fillMaxWidth().height(56.dp))
            Text("存在状态（15 分钟）", style = MaterialTheme.typography.titleSmall)
            PresenceTrack(device, System.currentTimeMillis(), 15 * 60 * 1000L, accent)
            if (device.kind == RadioKind.BLE) {
                FieldwatchActionButton(
                    onClick = onHunt,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.NearMe, null)
                    Spacer(Modifier.padding(4.dp))
                    Text("追踪")
                }
            } else {
                Text(
                    "追踪仅适用于 BLE。在原生 Android 上，Wi-Fi 接入点更新太慢，无法通过走动接近。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            familyHint?.let { hint ->
                StickyHeight(device.key to "family") { FamilyCard(hint) }
            }
            FieldwatchActionButton(
                onClick = onCreateFleet,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.GroupAdd, null)
                Spacer(Modifier.padding(4.dp))
                Text("从设备创建特征")
            }
            FieldwatchActionButton(
                onClick = { vm.startDeviceDetailShare(device) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Share, null)
                Spacer(Modifier.padding(4.dp))
                Text("以文本分享")
            }
            FieldwatchActionButton(
                onClick = { vm.startDeviceDetailAiExport(device) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.AutoAwesome, null)
                Spacer(Modifier.padding(4.dp))
                Text("AI 导出")
            }
            Text(
                "打开一段可直接粘贴到聊天的提示：解码此无线设备，查询 OUI/公司/UUID，并说明它最可能是什么。与设置 → AI 导出相同的实验性免责声明。仅限单个设备 — 并非身份识别。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FamilyCard(hint: SignatureFamilyHint) {
    val scheme = MaterialTheme.colorScheme
    val container = when (hint.verdict) {
        FamilyVerdict.STRONG -> scheme.primaryContainer
        FamilyVerdict.POSSIBLE -> Amber.nightIf(LocalNightMode.current).copy(alpha = 0.22f)
        FamilyVerdict.SINGLE, FamilyVerdict.TAGGED -> scheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val onContainer = when (hint.verdict) {
        FamilyVerdict.STRONG -> scheme.onPrimaryContainer
        FamilyVerdict.POSSIBLE, FamilyVerdict.SINGLE, FamilyVerdict.TAGGED -> scheme.onSurface
    }
    val muted = onContainer.copy(alpha = 0.78f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = container,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "特征家族",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                    Text(hint.title, style = MaterialTheme.typography.titleMedium, color = onContainer)
                }
                if (hint.displayCount > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            hint.displayCount.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = onContainer,
                        )
                        RadioKindMark(hint.radioKind, size = 13.dp)
                    }
                }
            }
            hint.ruleLabel?.let { rule ->
                Text(
                    rule,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = scheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(hint.body, style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

@Composable
private fun SignatureNotesCard(notes: List<Pair<String, String>>) {
    if (notes.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "备注",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            notes.forEach { (name, note) ->
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ObserverNotesCard(
    notes: String,
    canEdit: Boolean,
    editing: Boolean,
    draft: String,
    onToggleEdit: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    saved: Boolean,
) {
    val ink = Cyan.nightIf(LocalNightMode.current)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = ink.copy(alpha = 0.18f),
        border = BorderStroke(1.5.dp, ink),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "观察者备注",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    modifier = Modifier.weight(1f),
                )
                if (canEdit) {
                    IconButton(onClick = onToggleEdit) {
                        Icon(
                            Icons.Outlined.Edit,
                            if (editing) "隐藏观察者备注" else "观察者备注",
                        )
                    }
                }
            }
            if (!editing) {
                if (notes.isNotBlank()) {
                    Text(notes, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "无观察者备注",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                FieldwatchOutlinedField(
                    value = draft,
                    onValueChange = onDraftChange,
                    label = "观察者备注",
                    singleLine = false,
                    minLines = 3,
                    supportingText = "${draft.trim().length}/${RadioBookmarks.MAX_NOTES}. ${RadioBookmarks.observerNotesHint()}",
                )
                FieldwatchActionButton(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = saveEnabled,
                ) {
                    if (saved) {
                        Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.padding(4.dp))
                        Text("已保存")
                    } else {
                        Text("保存备注")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraAttentionCard(notes: List<Pair<String, String>>) {
    if (notes.isEmpty()) return
    val warn = Amber.nightIf(LocalNightMode.current)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = warn.copy(alpha = 0.28f),
        border = BorderStroke(1.5.dp, warn),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = warn,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    "特别关注",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = warn,
                )
            }
            notes.forEach { (name, note) ->
                Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "模式匹配，并非身份识别。不构成安全结论。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GuessCard(guess: DeviceExplain.Guess) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "这看起来像什么",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(guess.headline, style = MaterialTheme.typography.titleMedium)
            Text(guess.because, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun Meta(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun vendorLine(device: Sighting): String? {
    val parts = ArrayList<String>(3)
    device.vendor?.let {
        parts += "IEEE 板卡/芯片厂商：$it（${device.oui}）。这是 MAC 前缀的拥有者，并不总是产品品牌。"
    }
    val mfgId = device.facts.mfgRecords.firstOrNull()?.companyId ?: device.manufacturerId
    if (mfgId != null) {
        val company = RadioDb.company(mfgId)
        parts += "Bluetooth company in the ad: ${company ?: "unlisted"} (0x%04X).".format(mfgId)
    }
    return parts.joinToString("\n").ifBlank { null }
}

private fun uuidShort(uuid: String): String {
    val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
    return if (hex.length >= 8 && hex.startsWith("0000")) hex.substring(4, 8) else uuid.take(8)
}

private fun serviceDataHeading(sd: ServiceDataRecord): String {
    val named = RadioDb.serviceUuid(sd.uuid)?.let { " （$it）" } ?: ""
    val frame = eddystoneFrameTag(sd)?.let { " · $it" } ?: ""
    return "服务数据 ${uuidShort(sd.uuid)}$named$frame"
}

private fun eddystoneFrameTag(sd: ServiceDataRecord): String? {
    val hex = sd.uuid.filter { it.isLetterOrDigit() }.uppercase()
    val short = when {
        hex.length == 4 -> hex
        hex.length >= 8 && hex.startsWith("0000") -> hex.substring(4, 8)
        else -> return null
    }
    if (short != "FEAA") return null
    return when (sd.dataHex.filter { it.isLetterOrDigit() }.uppercase().take(2)) {
        "00" -> "UID"
        "10" -> "URL"
        "20" -> "TLM"
        "30" -> "EID"
        else -> null
    }
}
