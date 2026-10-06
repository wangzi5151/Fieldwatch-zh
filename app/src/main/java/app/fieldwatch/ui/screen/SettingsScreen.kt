package app.fieldwatch.ui.screen

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import app.fieldwatch.ui.component.FieldwatchFilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import app.fieldwatch.ui.component.FieldwatchSlider
import androidx.compose.material3.Surface
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fieldwatch.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.fieldwatch.domain.AlertVoiceWhat
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.ScanIntensity
import app.fieldwatch.domain.TakDefaults
import app.fieldwatch.domain.TakFeedStatus
import app.fieldwatch.domain.TakPublish
import app.fieldwatch.domain.TakUdpPreset
import app.fieldwatch.radio.WifiRadio
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.SectionCard
import app.fieldwatch.ui.component.FieldwatchFilterChip
import app.fieldwatch.ui.component.StableCaption
import app.fieldwatch.ui.component.StickyHeight
import java.net.Inet4Address
import java.net.NetworkInterface

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    onRadioBookmarks: () -> Unit,
    onShowLiveTour: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings = state.settings
    val saveSignatures = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::saveSignaturesToUri) }
    val importSignatures = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importSignaturesFromUri) }
    val saveSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::saveSettingsToUri) }
    val importSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importSettingsFromUri) }
    var confirmRestore by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = { NestedTopBar("设置") },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("外观") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("夜间模式", Modifier.weight(1f))
                FieldwatchSwitch(settings.nightMode, { on -> vm.updateSettings { it.copy(nightMode = on) } })
            }
            Text(
                "Off by default. Red-on-black field display so chips, text, and signal marks " +
                    "do not dump green or blue into a dark sit. Background stays dark. " +
                    "Phone brightness is unchanged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("保持屏幕常亮", Modifier.weight(1f))
                FieldwatchSwitch(settings.keepScreenOn, { on -> vm.updateSettings { it.copy(keepScreenOn = on) } })
            }
            Text(
                "默认开启。在 Fieldwatch 打开时阻止屏幕休眠，这样手机熄屏时 BLE 不会被挂起。如果你离开应用，扫描仍在通知中运行。把手机装进口袋时请关闭此项。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("隐私模式", Modifier.weight(1f))
                FieldwatchSwitch(settings.demoMode, { on -> vm.updateSettings { it.copy(demoMode = on) } })
            }
            Text(
                "在实时、雷达、时间线、详情、追踪、已命名设备和关注列表卡片中，将每个 MAC 的后三个字节隐藏为 **:**:**，这样屏幕和监测报告不会显示完整地址。GPS 最后定位以及简报 / AI 导出 / 详情分享的坐标会变为“已屏蔽”；这些监测报告中会省略街道名称。前三个字节（OUI / 厂商前缀）保留。默认关闭。当开启在线地名与地图时，报告 → 轨迹上的地图仍会加载。日志、匹配、筛选、追踪计算、随你移动以及已保存的特征仍使用真实的 MAC 和 GPS。如果你开启了 TAK / CoT 数据流，在此项开启期间会暂停，以免完整 MAC 和坐标发送到 LAN 上。当你需要屏幕上显示完整地址或坐标时，请关闭此项。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard("扫描") {
            val label = when (settings.intensity) {
                ScanIntensity.SAVER -> "省电"
                ScanIntensity.BALANCED -> "均衡"
                ScanIntensity.PERFORMANCE -> "高性能"
            }
            Text("扫描强度  ·  $label")
            FieldwatchSlider(
                value = settings.intensity.ordinal.toFloat(),
                onValueChange = { v ->
                    val next = ScanIntensity.entries[v.toInt().coerceIn(0, 2)]
                    vm.updateSettings { it.copy(intensity = next) }
                },
                valueRange = 0f..2f,
                steps = 1,
            )
            Text(
                "Wi-Fi 是批量无线设备：手机会一次性抓取所有接入点，然后必须等待。高性能模式大约每 30 秒请求一次——这是不触发操作系统每两分钟四次扫描限制的最快频率。BLE 在此期间仍持续推送。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StableCaption(
                state.throttleHint.ifBlank { " " },
                "Wi-Fi 等待系统",
                "Wi-Fi 扫描中",
                "Wi-Fi 下次 99s",
                " ",
            )

            val lifecycleOwner = LocalLifecycleOwner.current
            var osThrottled by remember { mutableStateOf(WifiRadio.osScanThrottled(context)) }
            var backgroundAllowed by remember { mutableStateOf(isBackgroundUsageAllowed(context)) }
            var unrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
            var needDevOptions by remember { mutableStateOf(false) }
            var batteryGate by remember { mutableStateOf<BatteryAndroidGate?>(null) }
            DisposableEffect(lifecycleOwner) {
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        osThrottled = WifiRadio.osScanThrottled(context)
                        backgroundAllowed = isBackgroundUsageAllowed(context)
                        unrestricted = isIgnoringBatteryOptimizations(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(obs)
                onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
            }
            val fastActive = settings.wifiFastScan && !osThrottled
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("更快的 Wi-Fi 接入点扫描", Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = settings.wifiFastScan,
                    onCheckedChange = { on ->
                        if (!on) {
                            vm.updateSettings { it.copy(wifiFastScan = false) }
                        } else if (!osThrottled) {
                            vm.updateSettings { it.copy(wifiFastScan = true) }
                        } else {
                            needDevOptions = true
                        }
                    },
                )
            }
            StableCaption(
                when {
                    Build.VERSION.SDK_INT < 30 ->
                        "需要 Android 11 及以上，Fieldwatch 才能读取操作系统是否仍在限制扫描。此手机无法确认，因此开关保持关闭。"
                    fastActive ->
                        "已开启。Fieldwatch 大约每 8 秒请求一次新的接入点列表。更耗电、更热。如果操作系统开始拒绝扫描，会自动退让。"
                    settings.wifiFastScan && osThrottled ->
                        "已保存开启，但未生效——Android Wi-Fi 扫描限制仍处于开启状态。请在开发者选项中关闭它，然后返回此处。"
                    else ->
                        "原生 Android 每两分钟允许约四次接入点扫描。只有在开发者选项中关闭 Wi-Fi 扫描限制后，更快的扫描才会运行。Fieldwatch 会在开启此项前检查该操作系统开关，且无法替你更改。"
                },
                "需要 Android 11 及以上，Fieldwatch 才能读取操作系统是否仍在限制扫描。此手机无法确认，因此开关保持关闭。",
                "已开启。Fieldwatch 大约每 8 秒请求一次新的接入点列表。更耗电、更热。如果操作系统开始拒绝扫描，会自动退让。",
                "已保存开启，但未生效——Android Wi-Fi 扫描限制仍处于开启状态。请在开发者选项中关闭它，然后返回此处。",
                "原生 Android 每两分钟允许约四次接入点扫描。只有在开发者选项中关闭 Wi-Fi 扫描限制后，更快的扫描才会运行。Fieldwatch 会在开启此项前检查该操作系统开关，且无法替你更改。",
            )
            if (needDevOptions) {
                AlertDialog(
                    onDismissRequest = { needDevOptions = false },
                    title = { Text("需要开发者选项") },
                    text = {
                        Text(
                            if (Build.VERSION.SDK_INT < 30) {
                                "此手机早于 Android 11，因此 Fieldwatch 无法读取操作系统的 Wi-Fi 扫描限制开关。更快的接入点扫描保持关闭。"
                            } else {
                                "Android is still throttling Wi-Fi scans (about four per two minutes). Fieldwatch will not turn Faster Wi-Fi AP scans on until that is off.\n\n" +
                                    "Enable Developer options (tap Build number seven times in About phone), then Settings → Developer options → Wi-Fi scan throttling → Off. Come back and flip this switch again."
                            },
                        )
                    },
                    confirmButton = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            TextButton(
                                onClick = {
                                    needDevOptions = false
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                    }
                                },
                            ) { Text("打开开发者选项") }
                        } else {
                            TextButton(onClick = { needDevOptions = false }) { Text("确定") }
                        }
                    },
                    dismissButton = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            TextButton(onClick = { needDevOptions = false }) { Text("暂不") }
                        }
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("允许后台使用", Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = backgroundAllowed,
                    onCheckedChange = { batteryGate = BatteryAndroidGate.BACKGROUND },
                )
            }
            Text(
                "Mirrors Android Allow background usage. Tap to open Fieldwatch’s Battery page and " +
                    "use that switch. Fieldwatch updates when you return. Off: the OS can kill the scan " +
                    "as soon as you leave. Not Keep screen on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("不受限制的电池", Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = unrestricted,
                    onCheckedChange = { batteryGate = BatteryAndroidGate.UNRESTRICTED },
                )
            }
            Text(
                "Mirrors Android Unrestricted (not Optimized). Some phones (Samsung among them) do not " +
                    "open onto that choice. If you only see Allow background usage, tap that row to " +
                    "click through and select Unrestricted. Fieldwatch updates when you return.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (batteryGate != null) {
                val background = batteryGate == BatteryAndroidGate.BACKGROUND
                AlertDialog(
                    onDismissRequest = { batteryGate = null },
                    title = {
                        Text(if (background) "允许后台使用" else "不受限制的电池")
                    },
                    text = {
                        Text(
                            if (background) {
                                "The next screen is Fieldwatch’s Battery page. Use the Allow background usage switch. " +
                                    "Fieldwatch will match that setting when you return."
                            } else {
                                "Some phones (Samsung among them) do not open onto Unrestricted / " +
                                    "Optimized / Restricted. If you only see Allow background usage, " +
                                    "tap that row (the words, not the blue switch) to click through, " +
                                    "then select Unrestricted. Fieldwatch will match that when you return."
                            },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val gate = batteryGate
                                batteryGate = null
                                openAppBatteryPage(
                                    context,
                                    highlightBackground = gate == BatteryAndroidGate.BACKGROUND,
                                )
                            },
                        ) { Text("打开 Android 设置") }
                    },
                    dismissButton = {
                        TextButton(onClick = { batteryGate = null }) { Text("暂不") }
                    },
                )
            }
            }

            SectionCard("关注列表") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("关注列表警报", Modifier.weight(1f))
                FieldwatchSwitch(settings.alertsEnabled, { on -> vm.updateSettings { it.copy(alertsEnabled = on) } })
            }
            Text(
                "默认开启。这是收藏特征和设备的的总开关。关闭：不发出提示音、振动、闪烁、跳转或通知卡片。收藏仍然有效——只是当该设备出现时不会通知你。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val radioWatchN = state.watchlist.count { it.deviceKey != null }
            FieldwatchActionButton(
                onClick = onRadioBookmarks,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("已命名设备 ($radioWatchN)") }
            Text(
                "为单个 MAC 设置自定义名称。警报可选。筛选 → 已命名设备只会在实时中显示它们。特征关注仍在特征中。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("关注特征时提示音", Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.alertBeep,
                    { on -> vm.updateSettings { it.copy(alertBeep = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                "当收藏的特征或设备首次出现，或离开后再次返回时，以媒体音量发出双提示音。持续监测的检测不会再次提示。独立于语音——可单用提示音、语音或两者。如果听不到，请调高媒体音量，然后点击测试警报。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("关注特征时语音播报", Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.alertVoice,
                    { on -> vm.updateSettings { it.copy(alertVoice = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                "默认开启。使用与提示音相同的媒体音量进行语音播报。独立于提示音：提示音开启时，语音在提示音后播报；提示音关闭时，仅语音。并非追踪。如果已有语句正在播报，第二次命中会被跳过。没有文字转语音的手机在提示音开启时仍会发出提示音。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("播报内容", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertVoiceWhat.entries.forEach { item ->
                    FieldwatchFilterChip(
                        selected = settings.alertVoiceWhat == item,
                        onClick = { vm.updateSettings { it.copy(alertVoiceWhat = item) } },
                        enabled = settings.alertsEnabled && settings.alertVoice,
                        label = { Text(item.label()) },
                    )
                }
            }
            Text(
                "对于特征关注：类别是实时图标分桶（查找标签、音频……）。特征是目录行（Apple AirTags、Axon……）。类别 + 特征（默认）两者都播报。开启警报的已命名设备始终播报其自定义名称，即使它没有类别。测试警报会播放你已开启的特征组合。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::testWatchBeep,
                modifier = Modifier.fillMaxWidth(),
                enabled = settings.alertsEnabled && (settings.alertBeep || settings.alertVoice),
            ) { Text("测试警报") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("跳转到新的关注检测", Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.snapToBeep,
                    { on -> vm.updateSettings { it.copy(snapToBeep = on) } },
                    enabled = settings.alertsEnabled && (settings.alertBeep || settings.alertVoice),
                )
            }
            Text(
                "当新的关注特征或设备出现时，实时会滚动到该行，以便你看到闪烁。适用于提示音、语音或两者。弱信号命中位于按强度排序列表的底部。如果你不希望列表移动，请关闭此项。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("系统通知", Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.alertShade,
                    { on -> vm.updateSettings { it.copy(alertShade = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                "可选。当关注的设备出现时发布一张静默通知卡片。默认关闭——提示音和闪烁已足够，跳过卡片可让扫描循环更轻量。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard("位置") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("用 GPS 标记检测", Modifier.weight(1f))
                FieldwatchSwitch(settings.tagLocation, { on -> vm.updateSettings { it.copy(tagLocation = on) } })
            }
            Text(
                "On by default. Requests live GPS/network updates and stamps each hear (Live detail, Moving with you, " +
                    "Debrief, and lat/lon on new log rows). Last-known-only is ignored if older than 30 s. " +
                    "That is your GPS at hear-time, not an independent fix on the other radio. " +
                    "Use high-accuracy Location or the path stays 0. Turn off if you do not want operator coordinates on logs. " +
                    "Heard-here TAK pins also need this; advertised payload coordinates (Remote ID) do not.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("在线地名与地图", Modifier.weight(1f))
                FieldwatchSwitch(settings.onlineLookup, { on -> vm.updateSettings { it.copy(onlineLookup = on) } })
            }
            Text(
                "On by default. When the phone has internet, Debrief / AI Export reverse-geocode GPS stamps " +
                    "to street/city, and Reports → Path loads OpenStreetMap tiles under the trace. " +
                    "No Fieldwatch cloud, no API key. Offline or no geocoder: Debrief uses coordinates only and Path stays the current north-up trace — no error dialog. " +
                    "Turn off to keep streets and map tiles out of reports and Path. " +
                    "Debrief, Sit export, Log export, and Reset / clear log are on the Reports tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard("TAK / CoT") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TAK / CoT 数据流", Modifier.weight(1f))
                FieldwatchSwitch(settings.takEnabled, { on -> vm.updateSettings { it.copy(takEnabled = on) } })
            }
            Text(
                "Off by default. Sends Cursor-on-Target UDP markers to ATAK, WinTAK, or iTAK. " +
                    "This phone (${TakDefaults.LOOPBACK}:${TakDefaults.PORT}) is ATAK CIV on this handset. " +
                    "LAN multicast is ${TakDefaults.SA_HOST}:${TakDefaults.SA_PORT}. " +
                    "Custom is a unicast IPv4 or hostname. UDP only — a TAK server’s TCP 8087 is not this feed. " +
                    "Heard-here pins sit at this phone’s GPS at the loudest hear (closest approach) and are labeled (here). " +
                    "Walking away does not drag the pin; a louder hear moves it. Keep-alives refresh the same lat/lon every ~10 s so ATAK does not drop it. " +
                    "Advertised lat/lon (stock Remote ID) sit on the aircraft; the same Remote ID " +
                    "keeps one marker that moves (UAS ID, not the rotating BLE MAC). " +
                    "A decoded pilot location is a second pin. Gone radios are dropped on ATAK instead of sitting 120 s. " +
                    "Tap a marker in ATAK for remarks (name, MAC, RSSI, signatures). " +
                    "Not direction-finding. Not a Remote ID plugin. Privacy mode pauses the feed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (settings.takEnabled && settings.demoMode) {
                Text(
                    "隐私模式已开启——数据流已暂停，因此不会发送完整 MAC 和坐标。关闭隐私模式即可发布。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (settings.takEnabled) {
                TakFeedSettings(settings, vm, state.takStatus)
            }
            }

            SectionCard("记录") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("将检测写入磁盘", Modifier.weight(1f))
                FieldwatchSwitch(settings.loggingEnabled, { on -> vm.updateSettings { it.copy(loggingEnabled = on) } })
            }
            StableCaption(
                if (settings.loggingEnabled) {
                    "记录已开启。新的检测会追加到轮转文件中。"
                } else {
                    "记录已关闭。扫描仍在运行；在你重新开启之前不会写入任何新内容。"
                },
                "记录已开启。新的检测会追加到轮转文件中。",
                "记录已关闭。扫描仍在运行；在你重新开启之前不会写入任何新内容。",
            )
            Text(
                "轮转文件为 JSON 行（每行一次侦听）。报告 → 日志 → 格式在你分享或保存时写入 CSV、JSON 行、GPX、KML 或 WiGLE。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var rotateDrag by remember { mutableIntStateOf(settings.logRotateKb) }
            var rotateDragging by remember { mutableStateOf(false) }
            LaunchedEffect(settings.logRotateKb) {
                if (!rotateDragging) rotateDrag = settings.logRotateKb
            }
            Text("轮转大小 $rotateDrag KB")
            FieldwatchSlider(
                value = rotateDrag.toFloat(),
                onValueChange = {
                    rotateDragging = true
                    rotateDrag = it.toInt().coerceIn(128, 4096)
                },
                onValueChangeFinished = {
                    vm.updateSettings { s -> s.copy(logRotateKb = rotateDrag) }
                    rotateDragging = false
                },
                valueRange = 128f..4096f,
            )
            var staleDrag by remember { mutableIntStateOf(settings.staleSec) }
            var staleDragging by remember { mutableStateOf(false) }
            LaunchedEffect(settings.staleSec) {
                if (!staleDragging) staleDrag = settings.staleSec
            }
            Text("过期时间 ${staleDrag}s")
            FieldwatchSlider(
                value = staleDrag.toFloat(),
                onValueChange = {
                    staleDragging = true
                    staleDrag = it.toInt().coerceIn(15, 180)
                },
                onValueChangeFinished = {
                    vm.updateSettings { s -> s.copy(staleSec = staleDrag) }
                    staleDragging = false
                },
                valueRange = 15f..180f,
            )
            StickyHeight("log-stats") {
                Text(
                    "${state.logLines} lines this session  ·  ${vm.logBytes() / 1024} KB on disk. " +
                        "Share, Save, and Reset / clear log are on the Reports tab.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            }

            SectionCard("特征") {
            Text(
                "导出目录（原生加上你添加或编辑的内容），以便与另一个 Fieldwatch 分享或作为备份。导入会添加新行和额外规则；它不会删除任何内容。相同 id 或相同匹配规则会被跳过，因此一个包可以导入两次。从 GitHub 更新原生目录会用仓库上的 v2 包替换原生行（包括特别关注）；收藏、设置以及你添加的特征会保留。需要网络。离线：从文件导入特征。下面的恢复默认仍会清除自定义内容。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::startSignatureShare,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("导出特征") }
            FieldwatchActionButton(
                onClick = { saveSignatures.launch(vm.suggestedSignaturesName()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存特征到 SD 卡 / 存储…") }
            FieldwatchActionButton(
                onClick = {
                    importSignatures.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("导入特征…") }
            FieldwatchActionButton(
                onClick = vm::updateStockCatalogFromGitHub,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("从 GitHub 更新原生目录") }

            FieldwatchActionButton(
                onClick = { confirmRestore = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("恢复默认特征和预设")
            }
            }

            SectionCard("设置备份") {
            Text(
                "Settings switches, the current filter, filter presets, named radios, and signature watches. " +
                    "Not the catalog — that is Export signatures. Not logs or GPS. " +
                    "Import replaces those on this phone; the catalog stays. " +
                    "Use this after a factory reset or on a new phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::startSettingsShare,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("导出设置") }
            FieldwatchActionButton(
                onClick = { saveSettings.launch(vm.suggestedSettingsName()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存设置到 SD 卡 / 存储…") }
            FieldwatchActionButton(
                onClick = {
                    importSettings.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("导入设置…") }
            }

            FieldwatchActionButton(
                onClick = onShowLiveTour,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("显示实时导览") }
            Text(
                "实时上的界面覆盖层：Tune 是显示（雷达、列表、按类别）、暂停、筛选、特征、报告、设置。首次运行在许可之后；此按钮会再次显示它。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                "Fieldwatch ${app.fieldwatch.BuildConfig.VERSION_NAME}  ·  目录 ${state.catalogVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Passive Wi-Fi + BLE only. " +
                    "Stock Android cannot promiscuously capture Wi-Fi stations; access points and BLE advertisers are what the radios expose.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val footerLifecycle = LocalLifecycleOwner.current
            var ipv4 by remember { mutableStateOf(localIpv4Addresses()) }
            DisposableEffect(footerLifecycle) {
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) ipv4 = localIpv4Addresses()
                }
                footerLifecycle.lifecycle.addObserver(obs)
                onDispose { footerLifecycle.lifecycle.removeObserver(obs) }
            }
            Text(
                if (ipv4.isEmpty()) {
                    "此手机的 IPv4  ·  无"
                } else {
                    "This phone’s IPv4  ·  ${ipv4.joinToString("  ·  ")}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            CreditFooter()
        }
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("恢复默认值？") },
            text = {
                Text(
                    "Rewrites the catalog (stock rows, class colors, Decode fields), stock bookmarks, " +
                        "stock filter chips, and default Settings switches. Custom signatures and chips you " +
                        "saved are wiped. Export signatures and Export settings first if you want a backup. " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        vm.restoreDefaults()
                    },
                ) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CreditFooter() {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "版权所有 (c) 2026 Off Grid Pete LLC。保留所有权利。",
            style = MaterialTheme.typography.labelSmall,
            color = muted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SocialChip(
                icon = R.drawable.ic_instagram,
                label = "@OffGridPete",
                tint = muted,
                onClick = { openUrl(context, "https://instagram.com/OffGridPete") },
            )
            SocialChip(
                icon = R.drawable.ic_x,
                label = "@OGridPete",
                tint = muted,
                onClick = { openUrl(context, "https://x.com/OGridPete") },
            )
        }
    }
}

@Composable
private fun SocialChip(
    icon: Int,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TakFeedSettings(settings: AppSettings, vm: FieldwatchViewModel, status: TakFeedStatus) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var hostText by remember { mutableStateOf(settings.takHost) }
    var portText by remember { mutableStateOf(settings.takPort.toString()) }
    LaunchedEffect(settings.takHost) { hostText = settings.takHost }
    LaunchedEffect(settings.takPort) { portText = settings.takPort.toString() }
    val preset = TakPublish.udpPreset(settings.takHost, settings.takPort)
    Text("目标", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.THIS_PHONE,
            onClick = {
                val (host, port) = TakPublish.applyPreset(TakUdpPreset.THIS_PHONE)
                vm.updateSettings { it.copy(takHost = host, takPort = port) }
            },
            enabled = !settings.demoMode,
            label = { Text("此手机") },
        )
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.LAN_MULTICAST,
            onClick = {
                val (host, port) = TakPublish.applyPreset(TakUdpPreset.LAN_MULTICAST)
                vm.updateSettings { it.copy(takHost = host, takPort = port) }
            },
            enabled = !settings.demoMode,
            label = { Text("LAN 组播") },
        )
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.CUSTOM,
            onClick = {
                if (preset != TakUdpPreset.CUSTOM) {
                    val (host, port) = TakPublish.applyPreset(TakUdpPreset.CUSTOM)
                    vm.updateSettings { it.copy(takHost = host, takPort = port) }
                }
            },
            enabled = !settings.demoMode,
            label = { Text("自定义") },
        )
    }
    Text(
        "This phone: ${TakDefaults.LOOPBACK}:${TakDefaults.PORT} (ATAK CIV on this handset). " +
            "LAN multicast: ${TakDefaults.SA_HOST}:${TakDefaults.SA_PORT} (other ATAKs on this Wi-Fi). " +
            "Custom: type a unicast IPv4 or hostname. UDP only. A TAK server’s TCP 8087 is not this feed. " +
            "If This phone does not plot, use Custom with this phone’s Wi-Fi IPv4 from the footer and port ${TakDefaults.PORT}.",
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
    FieldwatchOutlinedField(
        value = hostText,
        onValueChange = { value ->
            hostText = value
            val trimmed = value.trim()
            if (trimmed.isNotEmpty()) {
                vm.updateSettings { it.copy(takHost = trimmed) }
            }
        },
        label = "主机",
        placeholder = TakDefaults.HOST,
        enabled = !settings.demoMode,
    )
    FieldwatchOutlinedField(
        value = portText,
        onValueChange = { value ->
            val filtered = value.filter { it.isDigit() }.take(5)
            portText = filtered
            filtered.toIntOrNull()?.let { n ->
                if (n in 1..65_535) {
                    vm.updateSettings { it.copy(takPort = n) }
                }
            }
        },
        label = "端口",
        placeholder = TakDefaults.PORT.toString(),
        supportingText = "UDP。ATAK CIV ${TakDefaults.PORT}。SA 组播 ${TakDefaults.SA_PORT}。不是 TCP 8087。",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = !settings.demoMode,
    )
    Text(takStatusLine(status), style = MaterialTheme.typography.bodySmall, color = muted)
    Text("发送内容", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldwatchFilterChip(
            selected = settings.takAttention,
            onClick = { vm.updateSettings { it.copy(takAttention = !it.takAttention) } },
            enabled = !settings.demoMode,
            label = { Text("特别关注") },
        )
        FieldwatchFilterChip(
            selected = settings.takPayloadFix,
            onClick = { vm.updateSettings { it.copy(takPayloadFix = !it.takPayloadFix) } },
            enabled = !settings.demoMode,
            label = { Text("有效载荷位置") },
        )
        FieldwatchFilterChip(
            selected = settings.takWatchlist,
            onClick = { vm.updateSettings { it.copy(takWatchlist = !it.takWatchlist) } },
            enabled = !settings.demoMode,
            label = { Text("关注列表") },
        )
        FieldwatchFilterChip(
            selected = settings.takAllSignatures,
            onClick = { vm.updateSettings { it.copy(takAllSignatures = !it.takAllSignatures) } },
            enabled = !settings.demoMode,
            label = { Text("所有特征") },
        )
    }
    Text(
        "Independent chips. Extra attention (on): body-cam, glasses, recording wearables, pentest, public-safety APs. " +
            "Payload location (on): advertised lat/lon from a decode map — required for stock Remote ID, which has no Extra attention mark. " +
            "Watchlist (off): bookmarked signatures and named radios with Alert on. " +
            "All signatures (off): every labeled radio — noisy in a plaza. Unmatched radios never go. " +
            "A pin still needs coordinates: advertised payload, or GPS tagging with a live fix. " +
            "Heard-here holds the loudest hear, not the last, and callsigns end in (here). " +
            "Remote ID keeps one aircraft marker (UAS ID) plus a pilot pin when that location decoded.",
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
}

private fun takStatusLine(status: TakFeedStatus): String {
    if (status.paused) return "数据流状态  ·  已暂停（隐私模式）"
    if (status.error != null) {
        val whenAt = takStatusWhen(status.at)
        return "数据流状态  ·  错误：${status.error}" + if (whenAt.isNotEmpty()) "  ·  $whenAt" else ""
    }
    if (status.at <= 0L) {
        return "数据流状态  ·  本次会话尚未发送"
    }
    val bits = ArrayList<String>(5)
    bits += "数据流中 ${status.onFeed}"
    bits += "已发送 ${status.sent}"
    if (status.gone > 0) {
        bits += if (status.gone == 1) "1 gone" else "${status.gone} 个已消失"
    }
    if (status.dest.isNotBlank()) bits += status.dest
    val whenAt = takStatusWhen(status.at)
    if (whenAt.isNotEmpty()) bits += whenAt
    val head = "Feed status  ·  ${bits.joinToString("  ·  ")}"
    return if (status.detail.isNotBlank() && status.sent == 0 && status.gone == 0) {
        "$head  ·  ${status.detail}"
    } else {
        head
    }
}

private fun takStatusWhen(at: Long): String {
    if (at <= 0L) return ""
    return java.time.Instant.ofEpochMilli(at)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
}

private fun localIpv4Addresses(): List<String> {
    val found = LinkedHashSet<String>()
    val nifs = runCatching {
        java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
    }.getOrDefault(emptyList())
    for (nif in nifs) {
        if (!nif.isUp || nif.isLoopback) continue
        for (addr in java.util.Collections.list(nif.inetAddresses)) {
            if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                addr.hostAddress?.let { found += it }
            }
        }
    }
    return found.toList()
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

private fun isBackgroundUsageAllowed(context: Context): Boolean =
    context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted != true

private enum class BatteryAndroidGate { BACKGROUND, UNRESTRICTED }

/**
 * Fieldwatch’s per-app Battery page. Samsung keeps Allow background usage and
 * Unrestricted on this same screen. [highlightBackground] asks Settings to
 * focus the background-usage switch when the OEM supports it.
 */
private fun openAppBatteryPage(context: Context, highlightBackground: Boolean) {
    val pkgUri = Uri.fromParts("package", context.packageName, null)
    val attempts = listOf(
        Intent("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL").apply {
            data = pkgUri
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("request_ignore_background_restriction", highlightBackground)
            if (!highlightBackground) {
                putExtra(":settings:fragment_args_key", "unrestricted_pref")
            }
        },
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = pkgUri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
    for (intent in attempts) {
        if (intent.resolveActivity(context.packageManager) == null) continue
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
