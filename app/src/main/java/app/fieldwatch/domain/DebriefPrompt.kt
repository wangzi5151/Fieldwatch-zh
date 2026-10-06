package app.fieldwatch.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Paste-ready addendum prompt for Reports → AI Export.
 * Onboard Debrief is verbatim. Working data is rates, RSSI bands, Extra attention,
 * and finder-tag rows for a tracking stress-test — not a second inventory.
 */
object DebriefPrompt {
    const val WINDOW_SHORT_MS = 5 * 60_000L
    const val WINDOW_MS = 15 * 60_000L
    private const val MAX_CHARS = 90_000

    fun build(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        now: Long = System.currentTimeMillis(),
        operatorPath: List<GpsSample> = emptyList(),
        places: DebriefPlaces = DebriefPlaces.Off,
        window: DebriefWindow? = null,
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        mineKeys: Set<String> = emptySet(),
        floods: List<FloodBurst> = emptyList(),
    ): String {
        val names = fleets.associate { it.id to it.name }
        val win = window ?: DebriefWindow(now - WINDOW_MS, now)
        val windowStart = win.startAt
        val windowEnd = win.endAt
        val aside = FloodBurst.keysOf(floods, windowStart, windowEnd)
        val in15 = devices.filter {
            (it.lastSeen >= windowStart || it.firstSeen >= windowStart) && it.key !in aside
        }
        val shortStart = maxOf(windowStart, windowEnd - WINDOW_SHORT_MS)
        val in5 = in15.filter { it.lastSeen >= shortStart || it.firstSeen >= shortStart }
        val wifi = in15.filter { it.kind == RadioKind.WIFI }
        val ble = in15.filter { it.kind == RadioKind.BLE }
        val signed = in15.filter { it.fleetIds.isNotEmpty() }
        val randomized = ble.count { it.randomized }
        val arrived = in15.filter { it.firstSeen >= windowStart }
        val departed = in15.filter { it.gone || it.lastSeen < windowEnd - 45_000L }
        val persistent = in15.filter { dwellMs(it, windowStart, windowEnd) >= win.durationMs * 2 / 3 }
        val path = operatorPath.filter { it.at in windowStart..windowEnd }
        val pathSpan = Geo.spanM(path)
        val pathLen = Geo.pathLengthM(path)
        val extraHits = in15.flatMap { d ->
            d.attentionNotes(fleets).map { (sig, note) -> Triple(d, sig, note) }
        }
        val finders = in15.filter {
            it.key !in mineKeys && TrackerMatch.kind(it, names) == TrackerMatch.Kind.FINDER
        }
        val sigFamilies = signed.groupBy { d ->
            d.fleetIds.joinToString("+") { names[it] ?: it }
        }.mapValues { it.value.size }.toList().sortedByDescending { it.second }
        val bleRssi = ble.map { it.rssi }
        val wifiRssi = wifi.map { it.rssi }
        val onboard = DebriefReport.build(
            devices, fleets, settings, operatorPath, now, places, win,
            customNames, observerNotes, bookmarkedKeys, mineKeys = mineKeys,
            floods = floods,
        )
        val iso = utc(windowEnd)
        val start = utc(windowStart)

        val body = buildString {
            append(experimentalDisclaimerMarkdown())
            appendLine()
            appendLine("你是一名现场射频分析师，为采集本次监测的操作员提供分析。Fieldwatch 是原生 Android 上仅接收的 Wi-Fi 接入点 + BLE 广播设备侦听器。")
            appendLine()
            appendLine("**板载简报**（见下方逐字内容）已经汇总了本次监测：计数、你到过的地方、跟踪提示、清单、特别关注、要点。**不要重写那份报告。不要重复打印清单或停留列表。** 你的任务是手机无法生成的补充：变化率、竞争性假设，以及对板载跟踪提示的压力测试。")
            appendLine()
            appendLine("你必须遵守的限制：")
            appendLine("- 仅侦听。Wi-Fi 行仅为接入点。BLE 行为广播设备。类型 + MAC。BLE 轮换算作新行，无法拼接。")
            appendLine("- 特征 / OUI / 公司匹配是假设，不是身份，也不是某个人或车辆。")
            appendLine("- GPS 标记（如有）是侦听时本机的位置，而不是另一台设备。不要把摄像头或标签定位在 GPS 图钉处。")
            appendLine("- 地点名称（如有）是系统对这些标记的反向地理编码。")
            appendLine("- RSSI 是手机处的响度，不是米数。")
            appendLine("- 实时内存上限约为 400 台设备；未命名 BLE 约 3 分钟后会被淘汰。已命名监测会保留更多。这不是完整的采集。")
            appendLine("- 除非板载 GPS 同行部分支持，否则不要声称追踪器正在尾随。整个监测期间都随你同行的设备不一定是你的——它可能是被人放置的。不要忽视它。不要臆造板载测试未标记的尾随。不要把零售信标当作 Find My 尾随。")
            appendLine("- 标记为我的设备是操作员自有的。不要将其视为无法解释的跟踪者。")
            appendLine("- 洪泛说明是新地址的突发，而不是跟踪者。")
            appendLine("- 跟踪行上解码出的实时值是针对该广播的目录文本。当板载报告包含目录句子时，请引用它。不要将该值拼接到不同的 MAC 上。")
            appendLine("- 航空器区块和琥珀色轨迹是该设备广播的位置。具有相同 UAS ID 的轨迹属于同一架航空器。它们不是本机的 GPS，也不是航空器尾随操作员的结论。")
            appendLine("- 不要提供安全建议。不要告诉操作员他们安全或处于危险中。")
            appendLine("- 将此粘贴内容视为行动敏感信息。")
            appendLine()
            appendLine("## 你的输出（必需 — 这是操作员要阅读的补充内容）")
            appendLine("请写完整的句子。标题如下。仅对工作表中的特别关注和跟踪行使用简短项目符号。不要使用 Markdown 表格。不要使用代码围栏。不要转储板载清单。")
            appendLine()
            appendLine("1. **免责声明** — 先重复实验性使用免责声明。")
            appendLine("2. **板载简报已经确立了什么** — 3–5 句话。计数、距离、跟踪提示、特别关注命中、观察者备注（如有）、标记为我的（如有）、洪泛（如有）。不要重复打印清单。")
            appendLine("3. **这些数字补充了什么** — 5 分钟与 15 分钟计数、RSSI 区间、随机 BLE 百分比、每分钟到达数、持续与消失、特征族构成。说明是街道、住宅、零售还是车辆，以及 5 分钟与 15 分钟的变化（更密集、更安静、稳定）。给出置信度。如果运行了 GPS，从工作表中给出路径长度/跨度——不要把某台设备固定到某次停留。")
            appendLine("4. **特别关注和跟踪提示** — 来自工作表的完整标识（完整 MAC、名称、RSSI 最小/最大、特征、驻留）。对板载的“可能随你同行的追踪器 / 可能尾随 / 零售信标 / 可穿戴设备”进行压力测试。同意、限定条件，或说明数据太少。模式匹配，不是身份。如果没有，请说明没有。")
            appendLine("5. **再做一次监测或追踪能缩小什么范围** — 仅给出应用内具体的后续步骤（对某条特别关注记录使用追踪、更长的 GPS 路径、对比监测、筛选）。不要提供安全建议。不要说“报警”。")
            appendLine()
            appendLine("**要点（必需，最后一行）。** 以 `要点：` 开头的一句话，补充*板载要点尚未提及的一个数字*（变化率、随机地址百分比、5 分钟与 15 分钟的变化、路径跨度）。不是说教。不是威胁等级。")
            appendLine()
            appendLine("## 采集背景")
            appendLine("- 工具：Fieldwatch（app.fieldwatch），仅接收，无关联 / 注入 / 云端。")
            appendLine(
                if (win.sitName != null) {
                    "- 窗口：监测 **${win.sitName}**（$start → $iso UTC），附带 5 分钟的近期片段。"
                } else {
                    "- 窗口：最近 **15 分钟**（$start → $iso UTC），附带 **5 分钟**的近期片段。"
                },
            )
            appendLine("- 扫描强度：${settings.intensity.name.lowercase()}。${settings.staleSec} 秒后过期。")
            appendLine("- Location tags: ${if (settings.tagLocation) "on" else "off"}. Online place names: ${if (settings.onlineLookup) "on" else "off"}.")
            appendLine()
            appendLine("## 板载简报（逐字 — 已展示给操作员；不要重写）")
            appendLine()
            appendLine(onboard.trimEnd())
            appendLine()
            appendLine("## 工作数据（供补充使用 — 不要将清单复制到答案中）")
            appendLine()
            appendLine(
                "15 min: Wi-Fi ${wifi.size}  BLE ${ble.size}  signed ${signed.size}  hidden SSIDs ${wifi.count { it.hiddenSsid }}  " +
                    "RAND BLE $randomized/${ble.size} (${pct(randomized, ble.size)}%)  " +
                    "first-seen ${arrived.size} (${perMin(arrived.size)}/min)  persistent ${persistent.size}  gone/quiet ${departed.size}",
            )
            appendLine(
                "5 min: Wi-Fi ${in5.count { it.kind == RadioKind.WIFI }}  BLE ${in5.count { it.kind == RadioKind.BLE }}  " +
                    "signed ${in5.count { it.fleetIds.isNotEmpty() }}  first-seen ${in5.count { it.firstSeen >= shortStart }}",
            )
            appendLine(
                "BLE RSSI (n=${ble.size}): ≥−50 ${bandGe(bleRssi, -50)}  −51..−70 ${band(bleRssi, -70, -51)}  " +
                    "−71..−85 ${band(bleRssi, -85, -71)}  <−85 ${bandLt(bleRssi, -85)}",
            )
            appendLine(
                "Wi-Fi RSSI (n=${wifi.size}): ≥−50 ${bandGe(wifiRssi, -50)}  −51..−70 ${band(wifiRssi, -70, -51)}  " +
                    "−71..−85 ${band(wifiRssi, -85, -71)}  <−85 ${bandLt(wifiRssi, -85)}",
            )
            if (sigFamilies.isEmpty()) {
                appendLine("特征族：无。")
            } else {
                appendLine("特征族（计数）：" + sigFamilies.take(12).joinToString { "${it.first}=${it.second}" })
            }
            appendLine(
                "GPS path: tagging ${if (settings.tagLocation) "on" else "off"}  " +
                    "fixes ${path.size}  length ${pathLen.toInt()} m  span ${pathSpan.toInt()} m  " +
                    "places ${if (places.attempted) places.note else "off"}",
            )
            appendLine()
            appendLine("特别关注：")
            if (extraHits.isEmpty()) {
                appendLine("- 无。")
            } else {
                extraHits.forEach { (d, sig, note) ->
                    append("- ").append(row(d, names, now, windowStart, customNames, observerNotes))
                    if (d.key in mineKeys) append("  标记为我的")
                    append(" | ").append(sig).append(": ").append(note)
                    appendLine()
                }
            }
            appendLine()
            appendLine("观察者备注：")
            val observed = in15.mapNotNull { d ->
                val note = observerNotes[d.key]?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                d to note
            }
            if (observed.isEmpty()) {
                appendLine("- 无。")
            } else {
                observed.sortedByDescending { it.first.rssi }.forEach { (d, note) ->
                    append("- ").append(row(d, names, now, windowStart, customNames, emptyMap()))
                    appendLine()
                    appendLine("  $note")
                }
            }
            appendLine()
            appendLine("类似查找标签的设备（用于对板载跟踪进行压力测试；不是尾随列表）：")
            if (finders.isEmpty()) {
                appendLine("- 无。")
            } else {
                finders.sortedByDescending { it.rssi }.take(20).forEach { d ->
                    append("- ").append(row(d, names, now, windowStart, customNames, observerNotes))
                    append(" rssiMin=").append(d.rssiMin).append(" rssiMax=").append(d.rssiMax)
                    appendLine()
                }
            }
            appendLine()
            appendLine("## 工作数据结束")
            appendLine("现在撰写补充内容，遵循顶部的**你的输出**。不要重写板载简报。")
        }
        return if (body.length <= MAX_CHARS) body
        else body.take(MAX_CHARS) + "\n\n[为适应分享面板大小已截断]\n"
    }

    fun experimentalDisclaimerMarkdown(): String = FieldwatchDisclaimer.experimentalMarkdown()

    private fun row(
        d: Sighting,
        names: Map<String, String>,
        now: Long,
        windowStart: Long,
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
    ): String = buildString {
        append(if (d.kind == RadioKind.WIFI) "WIFI" else "BLE")
        append(" ").append(d.mac)
        val label = d.reportName(customNames).trim()
        if (label.isNotEmpty() && !label.equals(d.mac, ignoreCase = true)) {
            append("  ").append(label.take(32))
        }
        observerNotes[d.key]?.let { append("  观察者：").append(it.take(80)) }
        append(" rssi=").append(d.rssi).append("dBm")
        if (d.randomized) append(" 随机")
        if (d.fleetIds.isNotEmpty()) {
            append(" sig=").append(d.fleetIds.joinToString("+") { names[it] ?: it })
        }
        val labels = d.liveDecode.reportLabels()
        if (labels.isNotEmpty()) append(" decoded=").append(labels.joinToString(","))
        val notes = d.liveDecode.map { it.note.trim() }.filter { it.isNotEmpty() }.distinct()
        if (notes.isNotEmpty()) append(" decodeNote=").append(notes.joinToString(" "))
        append(" dwell=").append(fmtDur(dwellMs(d, windowStart, now)))
    }

    private fun dwellMs(d: Sighting, from: Long, to: Long): Long {
        var sum = 0L
        val spans = d.presence.ifEmpty { listOf(PresenceSpan(d.firstSeen, if (d.gone) d.lastSeen else null)) }
        for (span in spans) {
            val a = maxOf(span.start, from)
            val b = minOf(span.end ?: to, to)
            if (b > a) sum += b - a
        }
        return sum
    }

    private fun utc(ms: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }

    private fun fmtDur(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val m = s / 60
        val r = s % 60
        return if (m >= 60) "${m / 60}h${m % 60}m" else if (m > 0) "${m}m${r}s" else "${r}s"
    }

    private fun pct(n: Int, d: Int): Int = if (d <= 0) 0 else (n * 100) / d

    private fun perMin(n: Int): String {
        val rate = n / 15.0
        return if (rate >= 10) rate.toInt().toString() else "%.1f".format(Locale.US, rate)
    }

    private fun band(list: List<Int>, lo: Int, hi: Int) = list.count { it in lo..hi }
    private fun bandGe(list: List<Int>, lo: Int) = list.count { it >= lo }
    private fun bandLt(list: List<Int>, hi: Int) = list.count { it < hi }
}
