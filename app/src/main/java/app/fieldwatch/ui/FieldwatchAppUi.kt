package app.fieldwatch.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBarDefaults
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.component.FieldwatchDropdownField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.fieldwatch.domain.ListLine
import app.fieldwatch.domain.ListSort
import app.fieldwatch.domain.StrengthSort
import app.fieldwatch.domain.ViewMode
import app.fieldwatch.domain.FieldwatchDisclaimer
import app.fieldwatch.domain.disclaimerOk
import app.fieldwatch.radio.RadioPermissions
import app.fieldwatch.ui.screen.DeviceDetailScreen
import app.fieldwatch.ui.screen.HuntScreen
import app.fieldwatch.ui.screen.FiltersScreen
import app.fieldwatch.ui.screen.FleetsScreen
import app.fieldwatch.ui.screen.LivePane
import app.fieldwatch.ui.screen.CandidatesScreen
import app.fieldwatch.ui.screen.RadioBookmarksScreen
import app.fieldwatch.ui.screen.ReportsScreen
import app.fieldwatch.ui.screen.SettingsScreen
import app.fieldwatch.ui.theme.FieldwatchTheme

@Composable
fun FieldwatchRoot(vm: FieldwatchViewModel, onRequestPermissions: () -> Unit) {
    val state by vm.ui.collectAsStateWithLifecycle()
    FieldwatchTheme(
        darkTheme = true,
        nightMode = state.settings.nightMode,
    ) {
        if (!state.settings.disclaimerOk()) {
            DisclaimerGate(onAccept = vm::acceptDisclaimer)
        } else if (!state.permissionsOk) {
            PermissionGate(onRequestPermissions)
        } else {
            FieldwatchShell(state, vm)
        }
    }
}

@Composable
private fun DisclaimerGate(onAccept: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    var agreed by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Text(
            "免责声明和许可",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = ink,
        )
        Text(
            "免责声明",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            FieldwatchDisclaimer.firstRunDisclaimer,
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            FieldwatchDisclaimer.LICENSE_TITLE,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            FieldwatchDisclaimer.LICENSE_BODY,
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            FieldwatchDisclaimer.ACCEPT,
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            modifier = Modifier.padding(top = 20.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .toggleable(
                    value = agreed,
                    onValueChange = { agreed = it },
                    role = Role.Checkbox,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = agreed, onCheckedChange = null)
            Text(
                "我已阅读并同意",
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Button(
            onClick = onAccept,
            enabled = agreed,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) { Text("继续") }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Fieldwatch 需要无线权限", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "位置、附近的 Wi-Fi、蓝牙扫描和通知权限让 Fieldwatch 被动监测广播的网络和 BLE 设备。不会发送任何内容。",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRequest) { Text("授予权限") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldwatchShell(state: FieldwatchUi, vm: FieldwatchViewModel) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route ?: "live"
    val context = LocalContext.current
    val export by vm.export.collectAsStateWithLifecycle()
    val logKind by vm.logExportKind.collectAsStateWithLifecycle()
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(vm.exportMime()),
    ) { uri ->
        uri?.let(vm::startSaveToUri)
    }
    val sitSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(vm.sitExportMime()),
    ) { uri ->
        uri?.let(vm::startSitSaveToUri)
    }
    androidx.compose.runtime.LaunchedEffect(export.share) {
        export.share?.let { intent ->
            context.startActivity(Intent.createChooser(intent, export.shareTitle))
            vm.consumeShare()
        }
    }
    if (export.active) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                val m = export.message.lowercase()
                Text(
                    when {
                        "sit compare" in m && "pdf" in m -> "监测对比 PDF"
                        "sit compare" in m && "ai" in m -> "监测对比 AI 导出"
                        "sit compare" in m -> "监测对比"
                        "ai export" in m || "ai export" in m -> "AI 导出"
                        "pdf" in m -> "简报 PDF"
                        "debrief" in m -> "简报"
                        else -> "导出"
                    },
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(export.message.ifBlank { "请稍候…" })
                    if ("debrief" !in export.message.lowercase() &&
                        "ai export" !in export.message.lowercase() &&
                        "sit compare" !in export.message.lowercase()
                    ) {
                        Text(
                            "实时记录将暂停，直到此操作完成。扫描仍在继续。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { export.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${(export.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = { },
        )
    }
    if (export.error != null) {
        AlertDialog(
            onDismissRequest = vm::consumeExportNotice,
            title = { Text(export.errorTitle ?: "无法导出") },
            text = { Text(export.error ?: "") },
            confirmButton = {
                TextButton(onClick = vm::consumeExportNotice) { Text("确定") }
            },
        )
    }
    if (export.saved) {
        AlertDialog(
            onDismissRequest = vm::consumeExportNotice,
            title = { Text("日志已保存") },
            text = {
                Text("文件已写入你选择的文件夹。在系统选择器中，如果你想把它存到内部存储之外，请使用菜单选择 SD 卡。")
            },
            confirmButton = {
                TextButton(onClick = vm::consumeExportNotice) { Text("确定") }
            },
        )
    }
    if (export.cleared) {
        AlertDialog(
            onDismissRequest = vm::consumeExportNotice,
            title = { Text("日志已清除") },
            text = { Text("轮转文件已删除。新的检测将开始一份新日志。") },
            confirmButton = {
                TextButton(onClick = vm::consumeExportNotice) { Text("确定") }
            },
        )
    }
    if (export.noticeTitle != null) {
        AlertDialog(
            onDismissRequest = vm::consumeExportNotice,
            title = { Text(export.noticeTitle ?: "") },
            text = { Text(export.noticeMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = vm::consumeExportNotice) { Text("确定") }
            },
        )
    }
    val view = LocalView.current
    var tourTargets by remember { mutableStateOf(LiveTourTargets()) }
    val showTour = !state.settings.liveTourDone && route == "live"
    LaunchedEffect(showTour) {
        if (showTour && state.settings.scanControlsExpanded) {
            vm.setScanControlsExpanded(false)
        }
    }
    val keepAwake = state.settings.keepScreenOn || route == "hunt"
    DisposableEffect(keepAwake) {
        val window = (view.context as? android.app.Activity)?.window
        if (keepAwake) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            if (route != "detail" && route != "hunt") {
                TopAppBar(
                    expandedHeight = 52.dp,
                    title = {
                        val screenW = LocalConfiguration.current.screenWidthDp.dp
                        val actionW = if (route == "live") 56.dp else 16.dp
                        Column(
                            modifier = Modifier
                                .widthIn(max = (screenW - 20.dp - actionW).coerceAtLeast(120.dp))
                                .fillMaxWidth()
                                .pointerInput(route) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (route != "live") {
                                                nav.navigate("live") { launchSingleTop = true }
                                            }
                                            vm.focusLiveList()
                                        },
                                    )
                                },
                        ) {
                            Text(
                                when {
                                    state.sit.open != null && state.displayPaused ->
                                        "FIELDWATCH  ·  监测  ·  已暂停"
                                    state.sit.open != null -> "FIELDWATCH  ·  监测"
                                    state.displayPaused -> "FIELDWATCH  ·  已暂停"
                                    else -> "FIELDWATCH"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                                HeaderCount(state.wifiNow, Icons.Outlined.Wifi, "Wi-Fi")
                                HeaderCount(state.bleNow, Icons.Outlined.Bluetooth, "BLE")
                                HeaderCount(state.namedNow, Icons.Outlined.Hub, "signatures")
                                if (state.throttleHint.isNotBlank()) {
                                    Text(
                                        "·  ${state.throttleHint}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = muted,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (route == "live") {
                            IconButton(
                                onClick = {
                                    vm.setScanControlsExpanded(!state.settings.scanControlsExpanded)
                                },
                            ) {
                                Icon(
                                    if (state.settings.scanControlsExpanded) {
                                        Icons.Outlined.ExpandLess
                                    } else {
                                        Icons.Outlined.Tune
                                    },
                                    if (state.settings.scanControlsExpanded) {
                                        "隐藏扫描选项"
                                    } else {
                                        "显示扫描选项"
                                    },
                                    modifier = Modifier.onGloballyPositioned {
                                        tourTargets = tourTargets.copy(tune = it.boundsInRoot())
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (route != "detail" && route != "hunt") {
                Column {
                    if (route == "live" && (state.filter.arrivalsOnly || state.filter.movingWithYou)) {
                        LiveSessionBar(
                            arrivalsOnly = state.filter.arrivalsOnly,
                            movingWithYou = state.filter.movingWithYou,
                            onMarkSeen = vm::markArrivalsSeen,
                            onResetSeen = vm::resetArrivalsSeen,
                            onStartOverFollow = vm::resetFollowSession,
                        )
                    }
                    Surface(
                        color = NavigationBarDefaults.containerColor,
                        tonalElevation = NavigationBarDefaults.Elevation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 1.dp)
                                .height(48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                        FieldwatchNavTab(
                            weight = 1f,
                            selected = route == "live",
                            onBounds = { tourTargets = tourTargets.copy(pause = it) },
                            onClick = {
                                if (route == "live") {
                                    vm.toggleLiveDisplay()
                                } else {
                                    nav.navigate("live") { launchSingleTop = true }
                                }
                            },
                            icon = {
                                Icon(
                                    when {
                                        route == "live" && !state.displayPaused -> Icons.Outlined.Pause
                                        route == "live" && state.displayPaused -> Icons.Outlined.PlayArrow
                                        else -> Icons.Outlined.CellTower
                                    },
                                    if (route == "live" && !state.displayPaused) "暂停显示" else "实时",
                                )
                            },
                            label = if (route == "live" && !state.displayPaused) "暂停" else "实时",
                        )
                        FieldwatchNavTab(
                            weight = 1f,
                            selected = route == "filters",
                            onBounds = { tourTargets = tourTargets.copy(filters = it) },
                            onClick = { nav.navigate("filters") { launchSingleTop = true } },
                            icon = { Icon(Icons.Outlined.FilterAlt, null) },
                            label = "筛选",
                        )
                        FieldwatchNavTab(
                            weight = 1.45f,
                            selected = route == "fleets",
                            onBounds = { tourTargets = tourTargets.copy(signatures = it) },
                            onClick = { nav.navigate("fleets") { launchSingleTop = true } },
                            icon = { Icon(Icons.Outlined.Hub, null) },
                            label = "特征",
                        )
                        FieldwatchNavTab(
                            weight = 1f,
                            selected = route == "reports" || route == "candidates",
                            onBounds = { tourTargets = tourTargets.copy(reports = it) },
                            onClick = { nav.navigate("reports") { launchSingleTop = true } },
                            icon = { Icon(Icons.Outlined.Description, null) },
                            label = "报告",
                        )
                        FieldwatchNavTab(
                            weight = 1.05f,
                            selected = route == "settings" || route == "radio-bookmarks",
                            onBounds = { tourTargets = tourTargets.copy(settings = it) },
                            onClick = { nav.navigate("settings") { launchSingleTop = true } },
                            icon = { Icon(Icons.Outlined.Settings, null) },
                            label = "设置",
                        )
                        }
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            nav,
            startDestination = "live",
            modifier = Modifier.padding(pad),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable("live") {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    LivePane(
                        state = state,
                        vm = vm,
                        onOpen = {
                            vm.select(it)
                            nav.navigate("detail")
                        },
                    )
                    AnimatedVisibility(
                        visible = state.settings.scanControlsExpanded,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { vm.setScanControlsExpanded(false) },
                        )
                    }
                    AnimatedVisibility(
                        visible = state.settings.scanControlsExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .heightIn(max = maxHeight),
                    ) {
                        ViewPicker(
                            maxHeight = maxHeight,
                            mode = state.settings.viewMode,
                            sort = state.settings.strengthSort,
                            listSort = state.settings.listSort,
                            windowSec = state.settings.averageWindowSec,
                            decaySec = state.settings.decaySec,
                            showBar = state.settings.showRssiBar,
                            showFleet = state.settings.showFleetName,
                            showFrequency = state.settings.showFrequency,
                            showSeenTimes = state.settings.showSeenTimes,
                            titleLine = state.settings.listTitleLine,
                            subtitleLine = state.settings.listSubtitleLine,
                            onChangeView = vm::setViewMode,
                            onChangeSort = vm::setStrengthSort,
                            onChangeListSort = vm::setListSort,
                            onChangeDecay = vm::setDecaySec,
                            onToggleBar = vm::toggleRssiBar,
                            onToggleFleet = vm::toggleFleetName,
                            onToggleFrequency = vm::toggleFrequency,
                            onToggleSeenTimes = vm::toggleSeenTimes,
                            onChangeTitleLine = vm::setListTitleLine,
                            onChangeSubtitleLine = vm::setListSubtitleLine,
                        )
                    }
                }
            }
            composable("fleets") {
                FleetsScreen(
                    state = state,
                    vm = vm,
                    onCandidateDraftClosed = {
                        if (!nav.popBackStack("candidates", false)) {
                            nav.navigate("candidates")
                        }
                    },
                )
            }
            composable("filters") { FiltersScreen(state, vm) }
            composable("reports") {
                ReportsScreen(
                    state = state,
                    vm = vm,
                    exporting = export.active,
                    onSaveToStorage = { saveLauncher.launch(vm.suggestedExportName()) },
                    onSaveSitToStorage = { sitSaveLauncher.launch(vm.suggestedSitExportName()) },
                    onSignatureCandidates = {
                        vm.startSignatureCandidates()
                        nav.navigate("candidates")
                    },
                    onOpenPathRadio = { key ->
                        if (vm.openPathRadio(key)) nav.navigate("detail")
                    },
                )
            }
            composable("candidates") {
                CandidatesScreen(
                    vm = vm,
                    demoMode = state.settings.demoMode,
                    onBack = { nav.popBackStack() },
                    onCreate = { candidate ->
                        vm.beginCreateFromCandidate(candidate)
                        nav.navigate("fleets")
                    },
                )
            }
            composable("settings") {
                SettingsScreen(
                    state = state,
                    vm = vm,
                    onRadioBookmarks = { nav.navigate("radio-bookmarks") },
                    onShowLiveTour = {
                        vm.showLiveTour {
                            nav.navigate("live") { launchSingleTop = true }
                        }
                    },
                )
            }
            composable("radio-bookmarks") {
                RadioBookmarksScreen(
                    state = state,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onOpen = { key ->
                        vm.select(key)
                        nav.navigate("detail")
                    },
                )
            }
            composable("detail") {
                val device = state.selected
                val onBack = { nav.popBackStack(); vm.select(null); Unit }
                if (device == null) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("详情") },
                                navigationIcon = {
                                    IconButton(onClick = onBack) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                                    }
                                },
                            )
                        },
                    ) { pad ->
                        Text(
                            "未选择无线设备。",
                            Modifier.padding(pad).padding(24.dp),
                        )
                    }
                } else {
                    DeviceDetailScreen(
                        device = device,
                        vm = vm,
                        watched = vm.isWatched(device.key),
                        onBack = onBack,
                        onCreateFleet = { vm.beginCreateFrom(device); nav.navigate("fleets") },
                        onHunt = {
                            vm.startHunt(device)
                            nav.navigate("hunt")
                        },
                        demoMode = state.settings.demoMode,
                    )
                }
            }
            composable("hunt") {
                HuntScreen(
                    vm = vm,
                    demoMode = state.settings.demoMode,
                    huntBeep = state.settings.huntBeep,
                    huntVibrate = state.settings.huntVibrate,
                    onBack = {
                        vm.stopHunt()
                        nav.popBackStack()
                    },
                )
            }
        }
    }
    if (showTour) {
        LiveChromeTour(targets = tourTargets, onDismiss = vm::dismissLiveTour)
    }
    }
}

@Composable
private fun HeaderCount(n: Int, icon: ImageVector, desc: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            icon,
            contentDescription = desc,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            n.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun RowScope.FieldwatchNavTab(
    weight: Float,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    onBounds: (Rect) -> Unit = {},
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        Modifier
            .weight(weight)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides color) {
            Box(
                Modifier.height(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.onGloballyPositioned { onBounds(it.boundsInRoot()) }) {
                    icon()
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewPicker(
    maxHeight: Dp,
    mode: ViewMode,
    sort: StrengthSort,
    listSort: ListSort,
    windowSec: Int,
    decaySec: Int,
    showBar: Boolean,
    showFleet: Boolean,
    showFrequency: Boolean,
    showSeenTimes: Boolean,
    titleLine: ListLine,
    subtitleLine: ListLine,
    onChangeView: (ViewMode) -> Unit,
    onChangeSort: (StrengthSort, Int?) -> Unit,
    onChangeListSort: (ListSort) -> Unit,
    onChangeDecay: (Int) -> Unit,
    onToggleBar: () -> Unit,
    onToggleFleet: () -> Unit,
    onToggleFrequency: () -> Unit,
    onToggleSeenTimes: () -> Unit,
    onChangeTitleLine: (ListLine) -> Unit,
    onChangeSubtitleLine: (ListLine) -> Unit,
) {
    var openView by remember { mutableStateOf(false) }
    var openSort by remember { mutableStateOf(false) }
    val viewLabel = mode.label()
    var openDecay by remember { mutableStateOf(false) }
    var openTitle by remember { mutableStateOf(false) }
    var openSubtitle by remember { mutableStateOf(false) }
    val sortLabel = when (listSort) {
        ListSort.STRENGTH -> if (sort == StrengthSort.AVERAGE) "最强 · 平均 ${windowSec}s" else "最强"
        ListSort.NEWEST -> "最新侦听到"
        ListSort.NEWEST_ALERT -> "最新警报"
        ListSort.FIRST_SEEN -> "最新到达"
        ListSort.ARRIVAL -> "新项在底部"
        ListSort.NAME -> "名称 A–Z"
        ListSort.SIGNATURES -> "特征优先"
    }
    val decayLabel = if (decaySec <= 0) "关闭" else "保持 ${decaySec}s"
    val scroll = rememberScrollState()
    val panelMax = (maxHeight - 8.dp).coerceAtLeast(140.dp)
    val surfaceColor = MaterialTheme.colorScheme.surface
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .heightIn(max = panelMax),
        shape = RoundedCornerShape(16.dp),
        color = surfaceColor,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Box {
        Column(
            Modifier
                .verticalScroll(scroll)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .padding(bottom = if (scroll.canScrollForward) 20.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "显示",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val dropdownPad = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ExposedDropdownMenuBox(openView, { openView = it }, dropdownPad) {
                FieldwatchDropdownField("视图", viewLabel, openView)
                ExposedDropdownMenu(openView, { openView = false }) {
                    ViewMode.entries.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label()) },
                            onClick = { onChangeView(item); openView = false },
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(openSort, { openSort = it }, dropdownPad) {
                FieldwatchDropdownField("排序", sortLabel, openSort)
                ExposedDropdownMenu(openSort, { openSort = false }) {
                    DropdownMenuItem(
                        text = { Text("最强信号") },
                        onClick = { onChangeSort(StrengthSort.INSTANT, null); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text("最强（平均 30 秒）") },
                        onClick = { onChangeSort(StrengthSort.AVERAGE, 30); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text("最新侦听到") },
                        onClick = { onChangeListSort(ListSort.NEWEST); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text("最新警报") },
                        onClick = { onChangeListSort(ListSort.NEWEST_ALERT); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text("最新到达") },
                        onClick = { onChangeListSort(ListSort.FIRST_SEEN); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text("新项在底部") },
                        onClick = { onChangeListSort(ListSort.ARRIVAL); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text("名称 A–Z") },
                        onClick = { onChangeListSort(ListSort.NAME); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text("特征优先") },
                        onClick = { onChangeListSort(ListSort.SIGNATURES); openSort = false },
                    )
                }
            }
            ExposedDropdownMenuBox(openDecay, { openDecay = it }, dropdownPad) {
                FieldwatchDropdownField("短暂保持", decayLabel, openDecay)
                ExposedDropdownMenu(openDecay, { openDecay = false }) {
                    DropdownMenuItem(
                        text = { Text("关闭 — 仅在过期后") },
                        onClick = { onChangeDecay(0); openDecay = false },
                    )
                    listOf(10, 30, 60).forEach { sec ->
                        DropdownMenuItem(
                            text = { Text("最后一个数据包后保持 ${sec}s") },
                            onClick = { onChangeDecay(sec); openDecay = false },
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(openTitle, { openTitle = it }, dropdownPad) {
                FieldwatchDropdownField("标题行", listLineLabel(titleLine), openTitle)
                ExposedDropdownMenu(openTitle, { openTitle = false }) {
                    listOf(ListLine.ADVERTISED_NAME, ListLine.NAME_AND_TYPE, ListLine.MAC).forEach { item ->
                        DropdownMenuItem(
                            text = { Text(listLineLabel(item)) },
                            onClick = { onChangeTitleLine(item); openTitle = false },
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(openSubtitle, { openSubtitle = it }, dropdownPad) {
                FieldwatchDropdownField("副标题行", listLineLabel(subtitleLine), openSubtitle)
                ExposedDropdownMenu(openSubtitle, { openSubtitle = false }) {
                    listOf(
                        ListLine.ADVERTISED_NAME,
                        ListLine.NAME_AND_TYPE,
                        ListLine.MAC,
                        ListLine.NONE,
                    ).forEach { item ->
                        DropdownMenuItem(
                            text = { Text(listLineLabel(item)) },
                            onClick = { onChangeSubtitleLine(item); openSubtitle = false },
                        )
                    }
                }
            }
            OptionSwitch("RSSI 条", showBar, onToggleBar)
            OptionSwitch("特征名称", showFleet, onToggleFleet)
            OptionSwitch("频率", showFrequency, onToggleFrequency)
            OptionSwitch("首次 / 最后侦听到", showSeenTimes, onToggleSeenTimes)
        }
        if (scroll.canScrollForward) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, surfaceColor),
                        ),
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = "下方有更多显示选项",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        }
    }
}

private fun listLineLabel(line: ListLine): String = when (line) {
    ListLine.ADVERTISED_NAME -> "广播名称"
    ListLine.NAME_AND_TYPE -> "名称 + 类型"
    ListLine.MAC -> "MAC 地址"
    ListLine.NONE -> "无"
}

@Composable
private fun LiveSessionBar(
    arrivalsOnly: Boolean,
    movingWithYou: Boolean,
    onMarkSeen: () -> Unit,
    onResetSeen: () -> Unit,
    onStartOverFollow: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (arrivalsOnly) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FieldwatchActionButton(
                        onClick = onMarkSeen,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text("标记为已见") }
                    FieldwatchActionButton(
                        onClick = onResetSeen,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text("重置已见") }
                }
            }
            if (movingWithYou) {
                FieldwatchActionButton(
                    onClick = onStartOverFollow,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) { Text("重新开始") }
            }
        }
    }
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        FieldwatchSwitch(checked, { onToggle() })
    }
}

@Suppress("unused")
private val unusedPerms = RadioPermissions
