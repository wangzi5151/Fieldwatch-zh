package app.fieldwatch.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ReportBar(
    val label: String,
    val value: Int,
    val detail: String = "",
    val second: Int? = null,
)

data class ReportChart(
    val rows: List<ReportBar>,
    val split: Boolean = false,
    val caption: String = "",
) {
    fun asText(): String = buildString {
        if (caption.isNotBlank()) appendLine(caption)
        rows.forEach { row ->
            if (row.second == null) {
                append(row.label).append("  ").append(row.value)
                if (row.detail.isNotBlank()) append("  ").append(row.detail)
                appendLine()
            } else {
                append(row.label).append(": ")
                append(row.value).append(" Wi-Fi，")
                append(row.second).append(" BLE")
                appendLine()
            }
        }
    }.trimEnd()
}

data class DebriefSection(
    val number: String,
    val title: String,
    val body: String,
    val alert: Boolean = false,
    val chart: ReportChart? = null,
    val after: String = "",
)

data class DebriefPlaces(
    val attempted: Boolean,
    val available: Boolean,
    val note: String,
    val lines: List<String> = emptyList(),
    val namesByCell: Map<String, String> = emptyMap(),
) {
    /** Exact GPS cell, then nearest named cell within [maxM]. */
    fun nameNear(lat: Double, lon: Double, maxM: Double = 90.0): String? {
        namesByCell[Geo.cellKey(lat, lon)]?.let { return it }
        var best: String? = null
        var bestD = maxM
        for ((key, name) in namesByCell) {
            val parts = key.split(',')
            if (parts.size != 2) continue
            val klat = parts[0].toDoubleOrNull() ?: continue
            val klon = parts[1].toDoubleOrNull() ?: continue
            val d = Geo.meters(lat, lon, klat, klon)
            if (d < bestD) {
                bestD = d
                best = name
            }
        }
        return best
    }

    fun areaLine(): String {
        if (!attempted) return "off"
        val named = namesByCell.values.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (named.isEmpty()) return note
        return named.joinToString(" · ")
    }

    companion object {
        val Off = DebriefPlaces(false, false, "off")
    }
}

data class ExtraAttentionHit(
    val signature: String,
    val radioLabel: String,
    val note: String,
)

data class DebriefDoc(
    val generatedUtc: String,
    val windowLine: String,
    val meta: List<Pair<String, String>>,
    val disclaimer: String,
    val trackingAlert: Boolean,
    val takeaway: String,
    val sections: List<DebriefSection>,
    val extraAttention: List<ExtraAttentionHit> = emptyList(),
    val heading: String = "FIELDWATCH 现场简报",
    val pdfKicker: String = "现场简报",
    val pdfTitle: String = "现场简报",
    val pathFigure: SitPathPlot.Figure? = null,
    val extraFigures: List<SitPathPlot.Figure> = emptyList(),
) {
    fun toPlainText(): String = buildString {
        appendLine(heading)
        appendLine()
        appendLine("免责声明")
        appendLine(disclaimer)
        appendLine()
        meta.forEach { (k, v) -> appendLine("${k.padEnd(14)}$v") }
        appendLine()
        sections.forEach { sec ->
            appendLine("${sec.number}. ${sec.title.uppercase()}")
            val body = sec.body.trimEnd()
            if (body.isNotEmpty()) appendLine(body)
            sec.chart?.asText()?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
            val after = sec.after.trimEnd()
            if (after.isNotEmpty()) appendLine(after)
            appendLine()
        }
        appendLine("—")
        appendLine("要点：$takeaway")
    }

    fun withDemoMacs(macs: Collection<String>, demo: Boolean): DebriefDoc {
        if (!demo) return this
        fun t(s: String) = Geo.redactCoordsIn(MacUtil.redactMacsIn(s, macs, true), true)
        val note = "MAC 末段（**:**:**）和 GPS 坐标已遮蔽。手机上的日志未更改。"
        return copy(
            meta = listOf("隐私" to note) + meta.map { it.first to t(it.second) },
            disclaimer = t(disclaimer),
            takeaway = t(takeaway),
            sections = sections.map {
                it.copy(
                    title = t(it.title),
                    body = t(it.body),
                    after = t(it.after),
                    chart = it.chart?.let { chart ->
                        chart.copy(
                            caption = t(chart.caption),
                            rows = chart.rows.map { row ->
                                row.copy(label = t(row.label), detail = t(row.detail))
                            },
                        )
                    },
                )
            },
            extraAttention = extraAttention.map {
                it.copy(signature = t(it.signature), radioLabel = t(it.radioLabel), note = t(it.note))
            },
        )
    }
}

/**
 * Standalone field debrief (not an AI prompt). Heuristic sit report from
 * the last 15 minutes plus GPS co-travel of tracker-like radios.
 */
object DebriefReport {
    private const val WINDOW_MS = 15 * 60_000L
    private const val SHORT_MS = 5 * 60_000L
    private const val MOVE_M = 45.0
    /** Possible-tail extra gates. Own-kit uses a louder, longer “still here” window. */
    private const val COVER_FRAC = 0.5
    private const val FADE_DB = 12
    private const val TRAIL_LOUD_DBM = CoTravel.TRAIL_LOUD_DBM
    /** Own-kit “still here” — AirTags advertise slowly and rotate. */
    private const val OWN_HERE_MS = 180_000L
    private const val TAIL_HERE_MS = 20_000L
    private const val ON_BODY_MAX = -55
    private const val ON_BODY_MIN = -70

    fun build(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        operatorPath: List<GpsSample>,
        now: Long = System.currentTimeMillis(),
        places: DebriefPlaces = DebriefPlaces.Off,
        window: DebriefWindow? = null,
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        watchedFleetIds: Set<String> = emptySet(),
        mineKeys: Set<String> = emptySet(),
        floods: List<FloodBurst> = emptyList(),
    ): String = document(
        devices, fleets, settings, operatorPath, now, places, window,
        customNames, observerNotes, bookmarkedKeys, watchedFleetIds, mineKeys,
        floods,
    ).toPlainText()

    fun document(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        operatorPath: List<GpsSample>,
        now: Long = System.currentTimeMillis(),
        places: DebriefPlaces = DebriefPlaces.Off,
        window: DebriefWindow? = null,
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        watchedFleetIds: Set<String> = emptySet(),
        mineKeys: Set<String> = emptySet(),
        floods: List<FloodBurst> = emptyList(),
    ): DebriefDoc {
        val names = fleets.associate { it.id to it.name }
        val win = window ?: DebriefWindow(now - WINDOW_MS, now)
        val windowStart = win.startAt
        val windowEnd = win.endAt
        val aside = FloodBurst.keysOf(floods, windowStart, windowEnd)
        val inWin = devices
            .filter { (it.lastSeen >= windowStart || it.firstSeen >= windowStart) && it.key !in aside }
            .sortedByDescending { it.rssi }
        val wifi = inWin.filter { it.kind == RadioKind.WIFI }
        val ble = inWin.filter { it.kind == RadioKind.BLE }
        val named = inWin.filter { it.fleetIds.isNotEmpty() }
        val hidden = wifi.filter { it.hiddenSsid }
        val randomized = ble.count { it.randomized }
        val arrived = inWin.filter { it.firstSeen >= windowStart }
        val persistent = inWin.filter { dwellMs(it, windowStart, windowEnd) >= win.durationMs * 2 / 3 }
        val path = operatorPath.filter { it.at in windowStart..windowEnd }
        val pathSpan = Geo.spanM(path)
        val pathLen = Geo.pathLengthM(path)
        val trackers = inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.FINDER }
        val follow = followAssessments(trackers, names, path, windowStart, windowEnd, TrackerMatch.Kind.FINDER)
        val beaconFollow = followAssessments(
            inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.BEACON },
            names, path, windowStart, windowEnd, TrackerMatch.Kind.BEACON,
        )
        val wearableFollow = followAssessments(
            inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.WEARABLE },
            names, path, windowStart, windowEnd, TrackerMatch.Kind.WEARABLE,
        )
        val assessed = follow + beaconFollow + wearableFollow
        fun List<FollowHit>.open(): List<FollowHit> = filter { it.device.key !in mineKeys }
        val following = follow.filter { it.verdict == Verdict.FOLLOWING }.open()
        val withYou = follow.filter { it.verdict == Verdict.MOVED_WITH_YOU }.open()
        val ownLikely = follow.filter { it.verdict == Verdict.OWN_LIKELY }.open()
        val wholeSit = ownLikely + withYou
        val beaconsWithYou = stayedWithYou(beaconFollow).open()
        val wearablesWithYou = stayedWithYou(wearableFollow).open()
        val mineHeard = inWin.count { it.key in mineKeys }

        val showAll = settings.debriefShowAllRadios
        fun Sighting.listedWhenShort(): Boolean =
            key in customNames || key in mineKeys || key in bookmarkedKeys
        val byCh = wifi.groupBy { it.channel }.toSortedMap()
        val channelChart = if (byCh.isEmpty()) {
            null
        } else {
            ReportChart(
                rows = byCh.map { (ch, list) ->
                    val label = if (ch == 0) "unknown" else "信道 $ch"
                    ReportBar(label, list.size, detail = "最强 ${list.maxOf { it.rssi }} dBm")
                },
            )
        }
        val networks = "侦听到 ${wifi.size} 个 AP；${hidden.size} 个隐藏 SSID；${persistent.count { it.kind == RadioKind.WIFI }} 个在窗口大部分时间驻留。"
        val networksAfter = if (!showAll) {
            wifiLines(
                wifi.filter { it.listedWhenShort() && it.fleetIds.isEmpty() },
                names, windowStart, now, customNames, fleets, mineKeys,
            )
        } else {
            buildString {
                appendLine("最强的 AP：")
                wifi.take(12).forEach { d ->
                    appendLine("  · ${wifiLine(d, names, windowStart, now, customNames)}")
                    if (d.key in mineKeys) appendLine("    标记为我的")
                    d.attentionNotes(fleets).forEach { (sig, note) ->
                        appendLine("    特别关注（$sig）：$note")
                    }
                }
                if (hidden.isNotEmpty()) {
                    appendLine("隐藏 SSID：")
                    hidden.forEach { appendLine("  · ${it.mac}  ${it.vendor ?: ""}  ${it.rssi} dBm  ch ${it.channel}") }
                }
            }.trimEnd()
        }
        val notable = ble.filter {
            inventoryKeep(it, settings, bookmarkedKeys) &&
                (it.fleetIds.isNotEmpty() || it.name.isNotBlank() || it.rssi >= -65 || it.manufacturerId != null)
        }.sortedByDescending { it.rssi }.take(20)
        val omittedRand = ble.count { !inventoryKeep(it, settings, bookmarkedKeys) }
        val bleBody = buildString {
            appendLine("侦听到 ${ble.size} 个广播设备；$randomized 个使用随机地址；${named.count { it.kind == RadioKind.BLE }} 个特征匹配。")
            if (omittedRand > 0) {
                appendLine("未匹配的轮换 BLE 已从列表中省略（$omittedRand）。计数包含它们。监测导出包含每一台设备。")
            }
        }.trimEnd()
        val bleAfter = if (showAll && notable.isNotEmpty()) {
            buildString {
                appendLine("值得注意的 BLE：")
                notable.forEach { d ->
                    val guess = DeviceExplain.guess(d, d.fleetIds.map { names[it] ?: it })
                    appendLine("  · ${bleLine(d, names, windowStart, now, customNames)}  |  ${guess.headline}")
                    if (d.key in mineKeys) appendLine("    标记为我的")
                    d.attentionNotes(fleets).forEach { (sig, note) ->
                        appendLine("    特别关注（$sig）：$note")
                    }
                    val decoded = SignatureFieldDecoder.decodeSighting(d, fleets)
                    if (decoded.isNotEmpty()) {
                        decoded.forEach { row ->
                            appendLine("    ${row.label}: ${row.display}")
                            if (row.note.isNotBlank()) appendLine("    ${row.note}")
                        }
                    } else {
                        d.liveDecode.forEach { chip ->
                            append("    ${chip.reportLabel()}")
                            if (chip.note.isNotBlank()) append("  ").append(chip.note)
                            appendLine()
                        }
                    }
                }
            }.trimEnd()
        } else if (!showAll) {
            bleLines(
                ble.filter { it.listedWhenShort() && it.fleetIds.isEmpty() },
                names, windowStart, now, customNames, mineKeys,
            )
        } else {
            ""
        }
        val sigChart = signatureChart(named, names)
        val sigBody = if (named.isEmpty()) "此窗口内无。" else ""
        val sigAfter = if (named.isEmpty()) {
            ""
        } else if (showAll) {
            buildString {
                named.groupBy { it.fleetIds.joinToString("+") { id -> names[id] ?: id } }
                    .toList().sortedByDescending { it.second.size }
                    .forEach { (sig, list) ->
                        appendLine(sig)
                        list.sortedByDescending { it.rssi }.take(8).forEach { d ->
                            append("  · ${d.reportName(customNames)}  ${d.mac}  ${d.rssi} dBm")
                            if (d.key in mineKeys) append("  标记为我的")
                            val labels = d.liveDecode.reportLabels()
                            if (labels.isNotEmpty()) append("  ").append(labels.joinToString(", "))
                            appendLine()
                        }
                        list.flatMap { it.attentionNotes(fleets) }.distinct().forEach { (name, note) ->
                            appendLine("  特别关注（$name）：$note")
                        }
                    }
            }.trimEnd()
        } else {
            buildString {
                val marked = named.filter { it.listedWhenShort() }.sortedByDescending { it.rssi }
                if (marked.isNotEmpty()) appendLine("已命名或已标记：")
                marked.forEach { d ->
                    append("  · ${d.reportName(customNames)}  ${d.mac}  ${d.rssi} dBm")
                    if (d.key in mineKeys) append("  标记为我的")
                    val labels = d.liveDecode.reportLabels()
                    if (labels.isNotEmpty()) append("  ").append(labels.joinToString(", "))
                    appendLine()
                }
            }.trimEnd()
        }
        val persistBody = buildString {
            appendLine("在窗口大部分时间驻留：${persistent.size}")
            append("本窗口首次出现：${arrived.size}")
        }.trimEnd()
        val persistAfter = if (!showAll) {
            ""
        } else {
            buildString {
                persistent.filter { inventoryKeep(it, settings, bookmarkedKeys) }.take(15).forEach {
                    appendLine("  · ${it.reportName(customNames)}  ${it.mac}  dwell ${fmtDur(dwellMs(it, windowStart, now))}")
                }
                if (persistent.isEmpty()) appendLine("  · 无。")
                appendLine("最响的首次出现：")
                arrived.filter { inventoryKeep(it, settings, bookmarkedKeys) }.sortedByDescending { it.rssi }.take(8).forEach {
                    appendLine("  · ${it.reportName(customNames)}  ${it.mac}  ${it.rssi} dBm")
                }
            }.trimEnd()
        }
        val flags = anomalyLines(inWin, customNames, settings, bookmarkedKeys, showAll)
        val anomalyBody = if (flags.isEmpty()) {
            "没有额外标记。特征命中、特别关注和跟踪提示已涵盖已命名的模式匹配。"
        } else flags.joinToString("\n") { "  · $it" }
        val attentionHits = inWin.flatMap { d ->
            d.attentionNotes(fleets).map { (sig, note) -> Triple(d, sig, note) }
        }
        val actionBody = actions(following, withYou, ownLikely, beaconsWithYou, wearablesWithYou, settings, pathSpan)
            .joinToString("\n") { "  · $it" }

        val distanceLine = when {
            !settings.tagLocation -> "GPS 标记关闭——无轨迹"
            path.size < 2 -> "GPS 标记开启，本窗口内定位点少于 2 个"
            else -> "沿轨迹行进 ${fmtDist(pathLen)} · 跨度 ${fmtDist(pathSpan)} · ${path.size} 个定位点"
        }
        val lookupLine = when {
            !places.attempted -> "off"
            places.namesByCell.isNotEmpty() -> places.areaLine()
            else -> places.note
        }
        val pictures = AircraftTrail.pictures(
            inWin.mapNotNull { d ->
                AircraftTrail.source(d, d.reportName(customNames))
            },
            path,
        )
        val aircraftBody = AircraftTrail.body(pictures)
        var n = 1
        fun next() = (n++).toString()
        val sections = buildList {
            add(DebriefSection(
                next(),
                "执行摘要",
                execSummary(wifi, ble, named, hidden, randomized, pathSpan, pathLen, following, withYou, ownLikely, beaconsWithYou, wearablesWithYou, settings, places, win, mineHeard) + craftSentence(pictures),
                chart = classChart(inWin, fleets),
            ))
            add(DebriefSection(next(), "你到过的地方", whereYouWere(settings, path, pathLen, pathSpan, inWin, names, places, windowEnd, customNames, bookmarkedKeys)))
            if (aircraftBody.isNotEmpty()) {
                add(DebriefSection(next(), "飞行器", aircraftBody))
            }
            observerNotesSection(inWin, customNames, observerNotes)?.let { body ->
                add(DebriefSection(next(), "观察者备注", body))
            }
            markedMineSection(inWin, customNames, mineKeys, assessed)?.let { body ->
                add(DebriefSection(next(), "标记为我的", body))
            }
            add(
                DebriefSection(
                    next(),
                    "尾随评估",
                    trackingSection(settings, path, pathSpan, pathLen, following, wholeSit, beaconsWithYou, wearablesWithYou),
                ),
            )
            if (wholeSit.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        "可能随你同行的追踪器",
                        trackerCallout(
                            "Finder tags (AirTag / Find My, SmartTag, Tile, Chipolo, Pebblebee) and loud pocket Apple BLE. " +
                                "These radios stayed with your GPS path for this sit. " +
                                "Fieldwatch cannot tell your own tag or phone from a tracker planted in the car, bag, or on you before you started. " +
                                "Account for each MAC. Not a finding and not identity.",
                            wholeSit,
                            customNames,
                        ),
                        alert = true,
                    ),
                )
            }
            if (following.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        "可能尾随",
                        trackerCallout(
                            "Finder tags that were not heard when this sit started, then stayed with your path. " +
                                "That can mean someone started following you (their phone or tag), or a device was added during the trip. " +
                                "Not a finding and not identity.",
                            following,
                            customNames,
                        ),
                        alert = true,
                    ),
                )
            }
            if (beaconsWithYou.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        "随你同行的零售信标",
                        trackerCallout(
                            "iBeacon / Minew / Estimote / Kontakt.io / Atrius cart tag radios that stayed with your GPS path. " +
                                "Location beacons are usually fixtures in a store or venue — they do not typically move with you. " +
                                "If one did, account for it (a cart you pushed, your own test tag, a badge, or a short path that still overlaps a fixture). " +
                                "Not the same as a Find My tail. Not a finding and not identity.",
                            beaconsWithYou,
                            customNames,
                        ),
                        alert = true,
                    ),
                )
            }
            if (wearablesWithYou.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        "随你同行的可穿戴设备",
                        trackerCallout(
                            "Garmin / Fitbit / Oura radios that stayed with your GPS path. " +
                                "Watches and rings usually move with the person wearing them — often your own kit or someone walking with you. " +
                                "They are not typically planted trackers. Account for each MAC. Not a finding and not identity.",
                            wearablesWithYou,
                            customNames,
                        ),
                        alert = true,
                    ),
                )
            }
            add(DebriefSection(next(), "环境", environment(wifi, ble, randomized, persistent, pathSpan, pathLen)))
            add(DebriefSection(
                next(),
                "网络（Wi-Fi 接入点）",
                networks.trimEnd(),
                chart = channelChart,
                after = networksAfter.trimEnd(),
            ))
            add(DebriefSection(next(), "蓝牙 LE", bleBody.trimEnd(), after = bleAfter.trimEnd()))
            add(DebriefSection(next(), "特征命中", sigBody.trimEnd(), chart = sigChart, after = sigAfter.trimEnd()))
            add(DebriefSection(next(), "持续性", persistBody.trimEnd(), after = persistAfter.trimEnd()))
            if (attentionHits.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        "特别关注",
                        buildString {
                            appendLine("模式匹配，并非身份确认，不是盗刷检测器，也不是安全结论。")
                            attentionHits.forEach { (d, sig, note) ->
                                append("  · ${d.reportName(customNames)}  ${d.mac}  ${d.rssi} dBm  [$sig]")
                                if (d.key in mineKeys) append("  标记为我的")
                                appendLine()
                                appendLine("    $note")
                            }
                        }.trimEnd(),
                        alert = true,
                    ),
                )
            }
            add(DebriefSection(next(), "异常", anomalyBody))
            floodBody(floods, windowStart, windowEnd)?.let { body ->
                add(DebriefSection(next(), "洪泛", body))
            }
            add(DebriefSection(next(), "隐私", privacy(wifi, ble, randomized, hidden, settings, places, pictures.isNotEmpty())))
            add(DebriefSection(next(), "建议操作", actionBody))
        }

        val windowLine = if (win.sitName != null) {
            "监测 ${win.sitName}（${utc(windowStart)} → ${utc(windowEnd)} UTC）"
        } else {
            "最近 15 分钟（${utc(windowStart)} → ${utc(windowEnd)} UTC）"
        }
        val heading = if (win.sitName != null) {
            "FIELDWATCH 监测 — ${win.sitName}"
        } else {
            "FIELDWATCH 现场简报"
        }
        val meta = buildList {
            add("生成时间" to "${utc(now)} UTC")
            if (win.sitName != null) add("监测" to win.sitName)
            add("窗口" to windowLine)
            add("设备" to "${inWin.size}")
            add("工具" to "Fieldwatch (app.fieldwatch) · 原生 Android · 仅接收的 Wi-Fi AP + BLE 广播设备")
            add("扫描" to "${settings.intensity.name.lowercase()} · 过期 ${settings.staleSec}s · 短暂保持 ${settings.decaySec}s")
            add("GPS 标记" to if (settings.tagLocation) "on" else "off")
            add("距离" to distanceLine)
            add("地点" to lookupLine)
            add(
                "分类" to if (pictures.isNotEmpty()) {
                    "行动敏感——邻近 SSID、MAC、操作员 GPS、广播的航空器轨迹"
                } else {
                    "行动敏感——邻近 SSID、MAC、操作员 GPS"
                },
            )
        }
        return DebriefDoc(
            generatedUtc = utc(now),
            windowLine = windowLine,
            meta = meta,
            disclaimer = FieldwatchDisclaimer.report(win),
            trackingAlert = following.isNotEmpty() || ownLikely.isNotEmpty() || withYou.isNotEmpty(),
            takeaway = takeaway(following, withYou, ownLikely, beaconsWithYou, wearablesWithYou, pathSpan, settings, named),
            sections = sections,
            extraAttention = attentionHits.map { (d, sig, note) ->
                ExtraAttentionHit(
                    signature = sig,
                    radioLabel = buildString {
                        append("${d.reportName(customNames)}  ${d.mac}  ${d.rssi} dBm")
                        if (d.key in mineKeys) append("  标记为我的")
                    },
                    note = note,
                )
            },
            heading = heading,
            pdfKicker = if (win.sitName != null) "监测" else "现场简报",
            pdfTitle = if (win.sitName != null) "监测 — ${win.sitName}" else "现场简报",
            pathFigure = AircraftTrail.applyWalk(
                pathFigure(
                    win.sitName ?: "最近 15 分钟", path, inWin, fleets,
                    customNames, observerNotes, bookmarkedKeys, watchedFleetIds, mineKeys,
                ),
                pictures,
                secondary = false,
            ),
            extraFigures = AircraftTrail.ownFigures(pictures),
        )
    }

    private fun pathFigure(
        title: String,
        path: List<GpsSample>,
        devices: List<Sighting>,
        fleets: List<Fleet>,
        customNames: Map<String, String>,
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        watchedFleetIds: Set<String> = emptySet(),
        mineKeys: Set<String> = emptySet(),
    ): SitPathPlot.Figure? {
        val path = Geo.despikePath(path)
        if (path.size < 2) return null
        val plot = SitPathPlot.dotsFrom(
            devices, fleets, namedKeys = customNames.keys,
            customNames = customNames, observerNotes = observerNotes,
            bookmarkedKeys = bookmarkedKeys,
            watchedFleetIds = watchedFleetIds,
            alertsOnly = true,
            mineKeys = mineKeys,
        )
        return SitPathPlot.Figure(
            kicker = "操作员轨迹",
            tracks = listOf(SitPathPlot.FigureTrack(title, path)),
            dots = plot.points,
            lengthM = Geo.pathLengthM(path),
            spanM = Geo.spanM(path),
            caption = "北向朝上。线条代表本机（${path.lengthM()}）。MAC 警报或特征警报各绘制一次。解码出的纬度与经度是最后广播的位置。其他情况都是最强的侦听位置。数字表示该地点（轨迹键）。",
        )
    }

    private fun List<GpsSample>.lengthM(): String {
        val m = Geo.pathLengthM(this)
        return if (m >= 1000) "${"%.1f".format(java.util.Locale.US, m / 1000)} km" else "${m.toInt()} m"
    }

    /**
     * GPS / places / co-travel block for the AI Export prompt. Same heuristics
     * as the field debrief; markdown so a chat model can cite it.
     */
    fun gpsAnalystMarkdown(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        operatorPath: List<GpsSample>,
        now: Long = System.currentTimeMillis(),
        places: DebriefPlaces = DebriefPlaces.Off,
        window: DebriefWindow? = null,
        customNames: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        mineKeys: Set<String> = emptySet(),
    ): String = buildString {
        val names = fleets.associate { it.id to it.name }
        val win = window ?: DebriefWindow(now - WINDOW_MS, now)
        val windowStart = win.startAt
        val windowEnd = win.endAt
        val path = operatorPath.filter { it.at in windowStart..windowEnd }
        val pathSpan = Geo.spanM(path)
        val pathLen = Geo.pathLengthM(path)
        val inWin = devices.filter { it.lastSeen >= windowStart || it.firstSeen >= windowStart }
        val trackers = inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.FINDER }
        val follow = followAssessments(trackers, names, path, windowStart, windowEnd, TrackerMatch.Kind.FINDER)
        val beaconsMd = stayedWithYou(
            followAssessments(
                inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.BEACON },
                names, path, windowStart, windowEnd, TrackerMatch.Kind.BEACON,
            ),
        )
        val wearablesMd = stayedWithYou(
            followAssessments(
                inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.WEARABLE },
                names, path, windowStart, windowEnd, TrackerMatch.Kind.WEARABLE,
            ),
        )

        appendLine("## 你到过的地方（操作员 GPS）")
        appendLine("- Tag detections with GPS: ${if (settings.tagLocation) "on" else "off"}.")
        appendLine(
            "- 在线地点名称：" +
                if (places.attempted) places.note
                else "关闭（设置 → 简报中的在线地点名称）。本次导出不进行反向地理编码。",
        )
        append(whereYouWere(settings, path, pathLen, pathSpan, inWin, names, places, windowEnd, customNames, bookmarkedKeys).trimEnd())
        appendLine()
        appendLine()
        if (path.size < 2 || pathSpan < MOVE_M) {
            appendLine("- 尾随测试：位移不足（需要约 45 米跨度）。不要推断出尾随。")
            appendLine()
        }
        appendLine("## GPS 同行")
        appendLine(
            "Only radios that stayed with the operator path are listed. " +
                "House tags and other radios the operator only passed are omitted — they are not tracking. " +
                "Not identity. Find My MAC rotation will not stitch a tail that changes address. " +
                "Possible tail extra gates (walks): trail covers ≥ half the operator path, " +
                "≥ 2/3 of GPS stamps at −75 dBm or louder, last stamp not 12 dB below loudest. " +
                "Fail any one → omit (pass-by), not a tail. " +
                "Finder tags (AirTag / SmartTag / Tile / Chipolo / Pebblebee / Find My / loud pocket Apple) " +
                "are the tracking test. Retail beacons and wearables that co-travel are listed separately — " +
                "they do not typically move with you (beacons) or are usually own kit (wearables).",
        )
        fun List<FollowHit>.open(): List<FollowHit> = filter { it.device.key !in mineKeys }
        val followingMd = follow.filter { it.verdict == Verdict.FOLLOWING }.open()
        val wholeSitMd = follow.filter {
            it.verdict == Verdict.OWN_LIKELY || it.verdict == Verdict.MOVED_WITH_YOU
        }.open()
        val beaconsOpen = beaconsMd.open()
        val wearablesOpen = wearablesMd.open()
        if (followingMd.isEmpty() && wholeSitMd.isEmpty() && beaconsOpen.isEmpty() && wearablesOpen.isEmpty()) {
            appendLine("- 没有任何设备随轨迹同行。")
        } else {
            fun dump(title: String, rows: List<FollowHit>) {
                if (rows.isEmpty()) return
                appendLine()
                appendLine("### $title")
                rows.forEach { h ->
                    val d = h.device
                    appendLine(
                        "- ${h.label}  ${d.reportName(customNames)}  ${d.mac}  RSSI ${d.rssi} dBm " +
                            "(min ${d.rssiMin} / max ${d.rssiMax})  trail ${h.samples} fixes, span ${h.spanM.toInt()} m",
                    )
                    appendLine("  ${h.detail}")
                }
            }
            dump(
                "可能随你同行的追踪器（查找标签，整个监测期间——你自己的，或在你开始前就被放置的）",
                wholeSitMd,
            )
            dump(
                "可能尾随（查找标签，在本次监测开始后首次侦听，随后一直存在）",
                followingMd,
            )
            dump(
                "随你同行的零售信标（iBeacon / Minew / Estimote / Kontakt.io / Atrius 购物车标签——固定设施；你推的购物车会与你同行）",
                beaconsOpen,
            )
            dump(
                "随你同行的可穿戴设备（Garmin / Fitbit / Oura——通常是自己的装备或同伴）",
                wearablesOpen,
            )
        }
    }

    private enum class Verdict { FOLLOWING, MOVED_WITH_YOU, OWN_LIKELY, STATIONARY, INSUFFICIENT }

    private data class FollowHit(
        val device: Sighting,
        val label: String,
        val verdict: Verdict,
        val detail: String,
        val spanM: Double,
        val samples: Int,
    )

    private fun stayedWithYou(hits: List<FollowHit>): List<FollowHit> =
        hits.filter {
            it.verdict == Verdict.FOLLOWING ||
                it.verdict == Verdict.MOVED_WITH_YOU ||
                it.verdict == Verdict.OWN_LIKELY
        }

    private fun followAssessments(
        trackers: List<Sighting>,
        names: Map<String, String>,
        operatorPath: List<GpsSample>,
        windowStart: Long,
        now: Long,
        kind: TrackerMatch.Kind,
    ): List<FollowHit> {
        val opSpan = Geo.spanM(operatorPath)
        val opLen = Geo.pathLengthM(operatorPath)
        return trackers.map { d ->
            val label = TrackerMatch.label(d, names)
            val trail = d.gpsTrail.filter { it.at >= windowStart }
            val span = Geo.spanM(trail)
            val trailLen = Geo.pathLengthM(trail)
            val presentAtStart = d.firstSeen <= windowStart + 15_000L
            val stillHere = now - d.lastSeen <= TAIL_HERE_MS
            val ownHere = now - d.lastSeen <= OWN_HERE_MS
            val onBody = d.rssiMax >= ON_BODY_MAX && d.rssiMin >= ON_BODY_MIN && trail.size >= 2
            val cover = opLen > 0.0 && trailLen >= COVER_FRAC * opLen
            val (verdict, detail) = when {
                operatorPath.size < 2 || opSpan < MOVE_M ->
                    Verdict.INSUFFICIENT to "操作员 GPS 轨迹太短（${opSpan.toInt()} 米），无法测试尾随。"
                trail.size < 2 ->
                    Verdict.INSUFFICIENT to "已侦听到，但不在两个 GPS 点上。无法测试同行。"
                onBody && ownHere ->
                    Verdict.OWN_LIKELY to onBodyLine(kind, d, trail.size)
                cover && ownHere && d.rssiMax >= ON_BODY_MAX ->
                    Verdict.OWN_LIKELY to
                        "在你 ${opLen.toInt()} 米轨迹的其中 ${trailLen.toInt()} 米上被侦听到，且信号仍然很强（${d.rssiMax} dBm）。 " +
                        withYouNote(kind, d)
                span < MOVE_M * 0.6 ->
                    Verdict.STATIONARY to "在你移动 ${opSpan.toInt()} 米期间，仅在一处附近被侦听到（跨度 ${span.toInt()} 米）。看起来是静止的——是你走开了。"
                presentAtStart && stillHere && d.rssiMax >= ON_BODY_MAX ->
                    Verdict.OWN_LIKELY to
                        "随你移动了 ${span.toInt()} 米，在此 15 分钟窗口开始时就已经在广播，信号强（${d.rssi} dBm）。 " +
                        withYouNote(kind, d)
                presentAtStart && stillHere ->
                    Verdict.MOVED_WITH_YOU to
                        "GPS 样本沿你的轨迹跨越 ${span.toInt()} 米（${trail.size} 个定位点）。在本窗口开始时就已经在广播，且仍存在。 " +
                        withYouNote(kind, d)
                !presentAtStart && span >= MOVE_M && trail.size >= 3 ->
                    possibleTail(trail, span, opLen, kind, d)
                else ->
                    Verdict.STATIONARY to
                        "沿 ${span.toInt()} 米被侦听到（${trail.size} 个 GPS 标记），但并未一直大声地跟随你。属于附近掠过/路过，而非尾随。"
            }
            FollowHit(d, label, verdict, detail, span, trail.size)
        }.sortedBy { it.verdict.ordinal }
    }

    private fun onBodyLine(kind: TrackerMatch.Kind, d: Sighting, stamps: Int): String {
        val loud = "整个监测期间一直大声地随你同行（${d.rssiMax} 至 ${d.rssiMin} dBm，$stamps 个 GPS 标记）。 "
        return loud + withYouNote(kind, d)
    }

    /**
     * Catalog sentence for a live decode, when the signature wrote one.
     * A label with no sentence is named only. No fleet id is special.
     */
    private fun liveDecodeSentence(device: Sighting): String? {
        val chips = device.liveDecode
        if (chips.isEmpty()) return null
        val notes = chips.map { it.note.trim() }.filter { it.isNotEmpty() }.distinct()
        if (notes.isNotEmpty()) return notes.joinToString(" ")
        val labels = chips.reportLabels()
        if (labels.isEmpty()) return null
        return "Decoded: ${labels.joinToString(", ")}."
    }

    private fun withYouNote(kind: TrackerMatch.Kind, device: Sighting): String {
        val decoded = liveDecodeSentence(device)
        val base = when (kind) {
            TrackerMatch.Kind.FINDER ->
                "整个监测期间都随你同行——你自己放置的，或在你开始前就被放置的。请核实。"
            TrackerMatch.Kind.BEACON ->
                "位置信标通常不会随你移动。请核实（自己的测试标签、工牌，或与固定设施的短暂重叠）。"
            TrackerMatch.Kind.WEARABLE ->
                "通常是你或同伴佩戴的手表或戒指。一般不是被人放置的追踪器。"
        }
        return when {
            decoded != null -> "$base $decoded"
            kind == TrackerMatch.Kind.FINDER ->
                "$base Find My / iPhone 地址会轮换；此 MAC 仅属于本次会话。"
            else -> base
        }
    }

    /**
     * Extra gates on possible tail only. A neighborhood radio heard on a sidewalk
     * arc, or that faded as you walked, is stationary — not a follower.
     * Bag/car tags still cover most of the path and stay loud.
     */
    private fun possibleTail(
        trail: List<GpsSample>,
        span: Double,
        opLen: Double,
        kind: TrackerMatch.Kind,
        device: Sighting,
    ): Pair<Verdict, String> {
        val trailLen = Geo.pathLengthM(trail)
        val peak = trail.maxOf { it.rssi }
        val last = trail.last().rssi
        val fade = peak - last
        val loudN = trail.count { it.rssi >= TRAIL_LOUD_DBM }
        val loudNeed = (trail.size * 2 + 2) / 3
        val coverNeed = opLen * COVER_FRAC
        val coverPct = if (opLen <= 0.0) 0 else ((trailLen / opLen) * 100.0).toInt()
        return when {
            fade >= FADE_DB ->
                Verdict.STATIONARY to
                    "在监测开始后出现，但最后一个 GPS 标记为 $last dBm，而最强曾达 $peak dBm（−${fade} dB）。看起来是你走离了某个固定设施，而不是尾随。"
            loudN < loudNeed ->
                Verdict.STATIONARY to
                    "在监测开始后出现，GPS 跨度为 ${span.toInt()} 米，但只有 $loudN/${trail.size} 个标记信号较强（−75 dBm 以上）。看起来是路过，而不是尾随。"
            trailLen < coverNeed ->
                Verdict.STATIONARY to
                    "在监测开始后出现，但仅在你 ${opLen.toInt()} 米轨迹的其中 ${trailLen.toInt()} 米上被侦听到（$coverPct%）。属于附近掠过/路过，而非尾随。"
            else -> {
                val stats =
                    "Appeared after the sit started, then stayed loud with you across ${span.toInt()} m " +
                        "(${trailLen.toInt()} m of your ${opLen.toInt()} m path, $coverPct%; " +
                        "$loudN/${trail.size} GPS stamps ≥ −75 dBm). "
                val note = when (kind) {
                    TrackerMatch.Kind.FINDER ->
                        "在你亲眼核实之前，请按可能的尾随对待。"
                    TrackerMatch.Kind.BEACON ->
                        "对零售/位置信标而言不寻常——它们通常不会随你移动。请核实；这与 Find My 尾随不同。"
                    TrackerMatch.Kind.WEARABLE ->
                        "通常是加入本次监测的手表（你戴上了它，或有人与你同行）。一般不是被人放置的追踪器。"
                }
                Verdict.FOLLOWING to stats + note
            }
        }.let { (verdict, text) ->
            val extra = liveDecodeSentence(device)
            verdict to if (extra == null) text else "$text $extra"
        }
    }

    private fun execSummary(
        wifi: List<Sighting>,
        ble: List<Sighting>,
        named: List<Sighting>,
        hidden: List<Sighting>,
        randomized: Int,
        pathSpan: Double,
        pathLen: Double,
        following: List<FollowHit>,
        withYou: List<FollowHit>,
        ownLikely: List<FollowHit>,
        beaconsWithYou: List<FollowHit>,
        wearablesWithYou: List<FollowHit>,
        settings: AppSettings,
        places: DebriefPlaces,
        window: DebriefWindow,
        mineHeard: Int = 0,
    ): String = buildString {
        val whenPhrase = if (window.sitName != null) {
            "在监测 ${window.sitName} 中"
        } else {
            "在最近 15 分钟内"
        }
        append("$whenPhrase，Fieldwatch 侦听到 ${wifi.size} 个 Wi-Fi 接入点和 ${ble.size} 个 BLE 广播设备")
        append("（其中 ${named.size} 个特征匹配")
        if (mineHeard > 0) append("，$mineHeard 个标记为我的")
        append("，${hidden.size} 个隐藏 SSID，$randomized 个随机 BLE）。 ")
        if (settings.tagLocation && pathLen > 0) {
            append("总行进距离：沿 GPS 轨迹 ${fmtDist(pathLen)}（直线跨度 ${fmtDist(pathSpan)}）。 ")
        }
        if (places.namesByCell.isNotEmpty()) {
            append("停留点/区域：${places.areaLine()}。 ")
        } else if (places.attempted && settings.tagLocation) {
            append("${places.note} ")
        }
        val wholeSit = ownLikely + withYou
        when {
            following.isNotEmpty() || wholeSit.isNotEmpty() -> {
                append("跟踪提示。 ")
                if (wholeSit.isNotEmpty()) {
                    append("${wholeSit.size} 个查找标签整个监测期间都随你同行（你的装备，或在你开始前被放置的）： ")
                    append(wholeSit.joinToString { trackId(it) })
                    append(". ")
                }
                if (following.isNotEmpty()) {
                    append("${following.size} 个可能尾随，在本次监测开始后首次侦听：")
                    append(following.joinToString { trackId(it) })
                    append(". ")
                }
                append("请逐一核实每个 MAC——Fieldwatch 无法区分你的设备与被人放置的设备。 ")
            }
            !settings.tagLocation -> {
                append("GPS 标记已关闭，因此未执行尾随测试。请启用“为检测添加 GPS 标记”并走动以进行测试。 ")
            }
            pathSpan < MOVE_M -> {
                append("GPS 位移仅 ${pathSpan.toInt()} 米——太短，无法测试是否有追踪器在尾随。请在开启标记的情况下走得更远。 ")
            }
            else -> append("本窗口内没有查找标签明显随 GPS 轨迹同行。 ")
        }
        if (beaconsWithYou.isNotEmpty()) {
            append("零售信标也随轨迹同行（不寻常——固定设施通常不会随你移动）： ")
            append(beaconsWithYou.joinToString { "${it.label} ${it.device.mac}" })
            append(". ")
        }
        if (wearablesWithYou.isNotEmpty()) {
            append("可穿戴设备随轨迹同行（通常是你的手表/戒指或同伴）： ")
            append(wearablesWithYou.joinToString { "${it.label} ${it.device.mac}" })
            append(".")
        }
    }

    private fun trackingSection(
        settings: AppSettings,
        path: List<GpsSample>,
        pathSpan: Double,
        pathLen: Double,
        following: List<FollowHit>,
        wholeSit: List<FollowHit>,
        beaconsWithYou: List<FollowHit>,
        wearablesWithYou: List<FollowHit>,
    ): String = buildString {
        if (!settings.tagLocation) {
            appendLine("GPS 标记已关闭。Fieldwatch 无法测试设备是否随你移动。")
            appendLine("请开启设置 → 为检测添加 GPS 标记，步行或驾车 50 米以上，然后再次运行简报。")
            return@buildString
        }
        appendLine("总行进距离：沿 GPS 轨迹 ${fmtDist(pathLen)}（${path.size} 个样本）。直线跨度 ${fmtDist(pathSpan)}。")
        appendLine("同行按类别划分：查找标签（AirTag / Find My、SmartTag、Tile、Chipolo、Pebblebee、口袋中信号强的 Apple 设备）、零售信标（iBeacon、Minew、Estimote、Kontakt.io、Atrius 购物车标签）以及可穿戴设备（Garmin、Fitbit、Oura）。")
        if (path.size < 2 || pathSpan < MOVE_M) {
            appendLine("位移不足，无法区分随你同行的设备和你路过的设备。请步行或驾车更远后重试。")
            return@buildString
        }
        if (following.isEmpty() && wholeSit.isEmpty() && beaconsWithYou.isEmpty() && wearablesWithYou.isEmpty()) {
            appendLine("没有查找标签、零售信标或可穿戴设备随你同行。家用标签以及你只是路过的其他设备不会列出。")
        } else {
            appendLine("下方提示仅包含随轨迹同行的设备。你路过的设备（商店固定设施、家用标签）已省略。")
        }
    }

    private fun markedMineSection(
        devices: List<Sighting>,
        customNames: Map<String, String>,
        mineKeys: Set<String>,
        assessed: List<FollowHit>,
    ): String? {
        val hits = devices.filter { it.key in mineKeys }
        if (hits.isEmpty()) return null
        return buildString {
            appendLine("你标记为我的设备。在本窗口中侦听到。仍会列出。标记开启期间不发出提示音。")
            hits.sortedWith(
                compareByDescending<Sighting> { it.rssi }.thenBy { it.mac },
            ).forEach { d ->
                val kind = if (d.kind == RadioKind.WIFI) "WIFI" else "BLE"
                appendLine("  · $kind  ${d.mac}  ${d.reportName(customNames)}  ${d.rssi} dBm")
                val verdict = assessed.firstOrNull { it.device.key == d.key }?.verdict
                val line = when (verdict) {
                    Verdict.FOLLOWING ->
                        "标记为我的。在监测开始后首次侦听到，并随轨迹同行。"
                    Verdict.OWN_LIKELY, Verdict.MOVED_WITH_YOU ->
                        "标记为我的。整个监测期间都随你同行。"
                    else -> "标记为我的。"
                }
                appendLine("    $line")
            }
        }.trimEnd()
    }

    private fun observerNotesSection(
        devices: List<Sighting>,
        customNames: Map<String, String>,
        observerNotes: Map<String, String>,
    ): String? {
        val hits = devices.mapNotNull { d ->
            val note = observerNotes[d.key]?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            d to note
        }
        if (hits.isEmpty()) return null
        return buildString {
            appendLine("你对本窗口中侦听到的设备所加的说明。与已命名设备使用相同的类型+MAC。不是目录备注。")
            hits.sortedWith(
                compareByDescending<Pair<Sighting, String>> { it.first.rssi }.thenBy { it.first.mac },
            ).forEach { (d, note) ->
                val kind = if (d.kind == RadioKind.WIFI) "WIFI" else "BLE"
                appendLine("  · $kind  ${d.reportName(customNames)}  ${d.mac}  ${d.rssi} dBm")
                appendLine("    $note")
            }
        }.trimEnd()
    }

    private fun trackerCallout(
        intro: String,
        rows: List<FollowHit>,
        customNames: Map<String, String> = emptyMap(),
    ): String = buildString {
        appendLine(intro)
        appendLine()
        rows.forEach { h ->
            val d = h.device
            appendLine("  • ${h.label}")
            appendLine("    ${d.reportName(customNames)}  ${d.mac}  RSSI ${d.rssi} dBm (min ${d.rssiMin} / max ${d.rssiMax})")
            appendLine("    ${h.detail}")
        }
    }.trimEnd()

    private fun whereYouWere(
        settings: AppSettings,
        path: List<GpsSample>,
        pathLen: Double,
        pathSpan: Double,
        devices: List<Sighting>,
        names: Map<String, String>,
        places: DebriefPlaces,
        now: Long,
        customNames: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
    ): String = buildString {
        appendLine("侦听时手机的 GPS，而不是对方无线设备的位置，也不是摄像头杆的位置。停留是约 40 米范围内的聚类；停留点之间的跳跃为移动过程。坐标不会在每条 Wi-Fi/BLE 行上重复。")
        if (!settings.tagLocation) {
            appendLine("GPS 标记已关闭。请开启设置 → 为检测添加 GPS 标记，以记录侦听到设备时你所在的位置。")
            return@buildString
        }
        if (path.isEmpty()) {
            appendLine("GPS 标记已开启，但本窗口尚无定位点。")
            return@buildString
        }
        appendLine("总计：沿轨迹 ${fmtDist(pathLen)}，跨度 ${fmtDist(pathSpan)}，${path.size} 个定位点。")
        if (places.attempted) {
            appendLine(places.note)
            appendLine("街道名称是近似值。不要把某条街道当作匹配到的摄像头或标签的位置。")
        }
        val legs = Geo.legs(path, now = now)
        if (legs.isEmpty()) {
            appendLine("没有轨迹路段。")
            return@buildString
        }
        val stopNames = legs.filter { it.stay }.mapNotNull { places.nameNear(it.lat, it.lon) }
        if (stopNames.isNotEmpty()) {
            appendLine("停留点：" + stopNames.joinToString(" → "))
        }
        var stayN = 0
        legs.forEachIndexed { i, leg ->
            if (leg.stay) {
                stayN++
                appendLine()
                appendLine("${i + 1}. 停留  ${clock(leg.startAt)}–${clock(leg.endAt)} UTC  （${fmtDur(leg.durationMs)}）")
                appendLine("   ${placeAndGps(leg.lat, leg.lon, places)}")
                val here = devices.filter { heardAt(it, leg) }
                val aps = here.count { it.kind == RadioKind.WIFI }
                val ble = here.count { it.kind == RadioKind.BLE }
                val sigs = here.flatMap { d -> d.fleetIds.map { names[it] ?: it } }.distinct()
                append("   此处侦听到：$aps 个 AP，$ble 个 BLE")
                if (sigs.isNotEmpty()) append("  ·  ${sigs.take(6).joinToString(", ")}")
                appendLine()
                here.filter { inventoryKeep(it, settings, bookmarkedKeys) }.sortedByDescending { it.rssi }.take(4).forEach { d ->
                    appendLine("   · ${d.reportName(customNames)}  ${d.mac}  ${d.rssi} dBm")
                }
                if (here.isEmpty()) appendLine("   · 没有与本次停留关联的带 GPS 标记的设备（标记可能在这些设备首次被侦听到之后才开启）。")
            } else {
                appendLine()
                appendLine(
                    "${i + 1}. 移动  ${clock(leg.startAt)}–${clock(leg.endAt)} UTC  " +
                        "沿轨迹 ${fmtDist(leg.pathM)}",
                )
                appendLine("   ${placeAndGps(leg.lat, leg.lon, places)}")
                appendLine("   → ${placeAndGps(leg.endLat, leg.endLon, places)}")
            }
        }
        val stays = legs.count { it.stay }
        if (stays == 1 && pathSpan < MOVE_M) {
            appendLine()
            appendLine("仅一次停留——本窗口内你的位移不足以拆分出多个地点。")
        }
    }

    private fun heardAt(device: Sighting, leg: Geo.PathLeg): Boolean {
        val nearM = 60.0
        val trail = device.gpsTrail.filter { it.at >= leg.startAt && it.at <= leg.endAt }
        if (trail.isNotEmpty()) {
            return trail.any { Geo.meters(it.lat, it.lon, leg.lat, leg.lon) <= nearM }
        }
        val lat = device.latitude ?: return false
        val lon = device.longitude ?: return false
        if (device.lastSeen < leg.startAt || device.firstSeen > leg.endAt) return false
        return Geo.meters(lat, lon, leg.lat, leg.lon) <= nearM
    }

    private fun environment(
        wifi: List<Sighting>,
        ble: List<Sighting>,
        randomized: Int,
        persistent: List<Sighting>,
        pathSpan: Double,
        pathLen: Double,
    ): String {
        val ap = wifi.size
        val persistAp = persistent.count { it.kind == RadioKind.WIFI }
        val guess = when {
            pathSpan > 200 && ap in 1..25 -> "在混合射频环境中移动（步行/车辆）。"
            ap <= 4 && ble.size < 30 && persistAp >= 1 -> "可能是住宅或小型办公室——驻留 AP 很少，BLE 有限。"
            ap >= 15 && randomized >= 40 -> "密集的公共场所/零售/街道：大量 AP 和类似手机的随机 BLE。"
            ap >= 8 && persistAp >= 4 -> "可能是有固定基础设施 AP 及顾客的建筑物。"
            else -> "混合或采样不足的环境。"
        }
        return "$guess  （${ap} 个 AP，${ble.size} 个 BLE，${persistAp} 个持续 AP，行进 ${fmtDist(pathLen)}，跨度 ${fmtDist(pathSpan)}。）"
    }

    private fun wifiLine(
        d: Sighting,
        names: Map<String, String>,
        from: Long,
        now: Long,
        customNames: Map<String, String> = emptyMap(),
    ): String = buildString {
        append(d.reportName(customNames)).append("  ").append(d.mac)
        d.vendor?.let { append("  ").append(it) }
        append("  ").append(d.rssi).append(" dBm")
        if (d.channel != 0) append("  ch ").append(d.channel)
        if (d.hiddenSsid) append("  hidden")
        if (d.fleetIds.isNotEmpty()) append("  ").append(d.fleetIds.joinToString("+") { names[it] ?: it })
        append("  dwell ").append(fmtDur(dwellMs(d, from, now)))
    }

    private fun bleLine(
        d: Sighting,
        names: Map<String, String>,
        from: Long,
        now: Long,
        customNames: Map<String, String> = emptyMap(),
    ): String = buildString {
        append(d.reportName(customNames)).append("  ").append(d.mac)
        if (d.randomized) append("  随机")
        append("  ").append(d.rssi).append(" dBm")
        if (d.fleetIds.isNotEmpty()) append("  ").append(d.fleetIds.joinToString("+") { names[it] ?: it })
        append("  dwell ").append(fmtDur(dwellMs(d, from, now)))
    }

    /** Unmatched rotating BLE stays in counts/export; inventories omit it unless Extra attention, named, bookmark, or payload. */
    private fun inventoryKeep(
        d: Sighting,
        settings: AppSettings,
        bookmarkedKeys: Set<String>,
    ): Boolean {
        if (settings.debriefShowUnmatchedRandomBle && settings.debriefShowAllRadios) return true
        if (d.kind != RadioKind.BLE) return true
        if (!d.randomized) return true
        if (d.fleetIds.isNotEmpty()) return true
        if (d.payloadLat != null && d.payloadLon != null) return true
        if (d.key in bookmarkedKeys) return true
        return false
    }

    private fun classChart(devices: List<Sighting>, fleets: List<Fleet>): ReportChart? {
        if (devices.isEmpty()) return null
        val byId = fleets.associate { it.id to it.kind }
        val counts = linkedMapOf<SignatureClass, Int>()
        var unmatched = 0
        var multi = false
        for (d in devices) {
            val classes = d.fleetIds.mapNotNull { byId[it] }.toSet()
            if (classes.isEmpty()) {
                unmatched++
            } else {
                if (classes.size > 1) multi = true
                classes.forEach { counts[it] = (counts[it] ?: 0) + 1 }
            }
        }
        val rows = SignatureClass.entries.mapNotNull { kind ->
            val n = counts[kind] ?: return@mapNotNull null
            ReportBar(kind.label(), n)
        }.toMutableList()
        if (unmatched > 0) rows += ReportBar("未匹配", unmatched)
        if (rows.isEmpty()) return null
        return ReportChart(
            rows = rows,
            caption = if (multi) "同时属于两个类别的设备会在每个类别中各计一次。" else "",
        )
    }

    private fun signatureChart(named: List<Sighting>, names: Map<String, String>): ReportChart? {
        if (named.isEmpty()) return null
        val counts = linkedMapOf<String, Int>()
        for (d in named) {
            val sigs = d.fleetIds.map { names[it] ?: it }.filter { it.isNotBlank() }.distinct()
            val key = if (sigs.isEmpty()) "未匹配" else sigs.joinToString(" + ")
            counts[key] = (counts[key] ?: 0) + 1
        }
        val rows = counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { ReportBar(it.key, it.value) }
        return ReportChart(rows = rows)
    }

    private fun wifiLines(
        rows: List<Sighting>,
        names: Map<String, String>,
        from: Long,
        now: Long,
        customNames: Map<String, String>,
        fleets: List<Fleet>,
        mineKeys: Set<String>,
    ): String {
        if (rows.isEmpty()) return ""
        return buildString {
            appendLine("已命名或已标记：")
            rows.sortedByDescending { it.rssi }.forEach { d ->
                appendLine("  · ${wifiLine(d, names, from, now, customNames)}")
                if (d.key in mineKeys) appendLine("    标记为我的")
                d.attentionNotes(fleets).forEach { (sig, note) ->
                    appendLine("    特别关注（$sig）：$note")
                }
            }
        }.trimEnd()
    }

    private fun bleLines(
        rows: List<Sighting>,
        names: Map<String, String>,
        from: Long,
        now: Long,
        customNames: Map<String, String>,
        mineKeys: Set<String>,
    ): String {
        if (rows.isEmpty()) return ""
        return buildString {
            appendLine("已命名或已标记：")
            rows.sortedByDescending { it.rssi }.forEach { d ->
                appendLine("  · ${bleLine(d, names, from, now, customNames)}")
                if (d.key in mineKeys) appendLine("    标记为我的")
            }
        }.trimEnd()
    }

    private fun floodBody(floods: List<FloodBurst>, start: Long, end: Long): String? {
        val rows = floods.filter { it.at in start..end }.sortedBy { it.at }
        if (rows.isEmpty()) return null
        return buildString {
            appendLine(FloodBurst.intro(rows))
            appendLine()
            rows.forEach { appendLine(it.reportLine(FloodBurst.clock(it.at))) }
        }.trimEnd()
    }

    private fun anomalyLines(
        devices: List<Sighting>,
        customNames: Map<String, String> = emptyMap(),
        settings: AppSettings,
        bookmarkedKeys: Set<String>,
        showAll: Boolean,
    ): List<String> {
        val out = ArrayList<String>()
        val pairing = devices.filter { d ->
            d.facts.serviceData.any { it.uuid.contains("FE2C", true) && it.dataHex.length == 6 }
        }
        if (pairing.isNotEmpty()) {
            out += if (showAll || pairing.size <= 8) {
                "处于配对模式的 Google Fast Pair：" +
                    pairing.joinToString { "${it.reportName(customNames)} ${it.mac}" }
            } else {
                "处于配对模式的 Google Fast Pair：${pairing.size} 台设备。"
            }
        }
        val loudUnknown = devices.filter {
            it.rssi >= -50 && it.fleetIds.isEmpty() && it.name.isBlank() &&
                inventoryKeep(it, settings, bookmarkedKeys)
        }
        if (loudUnknown.isNotEmpty()) {
            out += "极强的未命名设备（≥ −50 dBm）：" +
                loudUnknown.take(8).joinToString { "${it.mac} ${it.rssi} dBm" }
        }
        val rand = devices.count { it.kind == RadioKind.BLE && it.randomized }
        if (rand >= 20) {
            out += "高比例随机 BLE（$rand）——通常是手机，不是跟踪结论。"
        }
        return out
    }

    private fun privacy(
        wifi: List<Sighting>,
        ble: List<Sighting>,
        randomized: Int,
        hidden: List<Sighting>,
        settings: AppSettings,
        places: DebriefPlaces,
        includeAircraft: Boolean,
    ): String = buildString {
        append("使用相同无线电的被动观察者会看到 ${wifi.size} 个已命名/隐藏 AP ")
        append("以及 ${ble.size} 个 BLE 广播设备（$randomized 个随机）。 ")
        if (hidden.isNotEmpty()) append("隐藏 SSID 仍会发送信标，并通过 BSSID 标识该 AP。 ")
        if (settings.tagLocation) append("本简报包含用于计算距离和尾随测试的操作员 GPS 样本。 ")
        if (includeAircraft) {
            append("本简报包含广播了纬度和经度的设备所通告的航空器位置。 ")
        }
        if (places.attempted && places.available) {
            append("街道名称来自手机联网时的系统地理编码器。 ")
        }
        append("未经脱敏，请勿将此文件分享到设备之外。")
    }

    private fun actions(
        following: List<FollowHit>,
        withYou: List<FollowHit>,
        ownLikely: List<FollowHit>,
        beaconsWithYou: List<FollowHit>,
        wearablesWithYou: List<FollowHit>,
        settings: AppSettings,
        pathSpan: Double,
    ): List<String> = buildList {
        if (following.isNotEmpty()) {
            add("可能尾随（在本次监测开始后出现）：${following.joinToString { trackId(it) }}。暂停实时，打开详情，在你走折线路径时记录 RSSI。不要禁用他人的标签。")
        }
        if (ownLikely.isNotEmpty() || withYou.isNotEmpty()) {
            add(
                "可能随你同行的追踪器：${(ownLikely + withYou).joinToString { trackId(it) }}。 " +
                    "可能是你自己的，也可能是在你开始前就被人放在车里/包里/身上的。请逐一核实每个 MAC——不要想当然地认为是你自己的。",
            )
        }
        if (beaconsWithYou.isNotEmpty()) {
            add(
                "随你同行的零售信标（不寻常——固定设施通常不会随你移动）： " +
                    beaconsWithYou.joinToString { it.label + " " + it.device.mac } +
                    "。在将其视为跟踪者之前，请核实是否为测试标签或工牌。",
            )
        }
        if (wearablesWithYou.isNotEmpty()) {
            add(
                "随你同行的可穿戴设备（通常是自己的装备或同伴）： " +
                    wearablesWithYou.joinToString { it.label + " " + it.device.mac } +
                    ".",
            )
        }
        if (!settings.tagLocation) add("启用为检测添加 GPS 标记并步行 50 米以上，然后再次运行简报以进行尾随测试。")
        else if (pathSpan < MOVE_M) add("在开启 GPS 标记的情况下走得更远（50 米以上），然后重新运行简报。")
        add("使用实时 → 暂停来查看繁忙的列表。如果本次监测信号杂乱，请留意跟踪器特征。")
        add("站点侧的 Wi-Fi（探测请求/客户端）仍需要专用嗅探器——Fieldwatch 无法看到它们。")
    }

    private fun takeaway(
        following: List<FollowHit>,
        withYou: List<FollowHit>,
        ownLikely: List<FollowHit>,
        beaconsWithYou: List<FollowHit>,
        wearablesWithYou: List<FollowHit>,
        pathSpan: Double,
        settings: AppSettings,
        named: List<Sighting>,
    ): String {
        val extra = buildString {
            if (beaconsWithYou.isNotEmpty()) {
                append(" 零售信标也随轨迹同行（不寻常）： ")
                append(beaconsWithYou.joinToString { it.label + " (" + it.device.mac + ")" })
                append(".")
            }
            if (wearablesWithYou.isNotEmpty()) {
                append(" 可穿戴设备随轨迹同行（通常是自己的装备）： ")
                append(wearablesWithYou.joinToString { it.label + " (" + it.device.mac + ")" })
                append(".")
            }
        }
        val core = when {
            following.isNotEmpty() && (ownLikely.isNotEmpty() || withYou.isNotEmpty()) ->
                "可能尾随（在监测开始后出现）：${following.joinToString { trackId(it) }}。 " +
                    "还有随你同行的查找标签（你自己的，或之前被放置的）：${(ownLikely + withYou).joinToString { trackId(it) }}。请逐一核实每个 MAC。"
            following.isNotEmpty() ->
                "可能尾随（在本次监测开始后出现）：${following.joinToString { trackId(it) }}。请在相关人员/车辆上核实。"
            !settings.tagLocation ->
                "开启 GPS 标记并走动，然后才能测试是否有追踪器在尾随你。"
            pathSpan < MOVE_M ->
                "GPS 位移不足（${pathSpan.toInt()} 米），无法测试尾随；请走动后重新运行简报。"
            ownLikely.isNotEmpty() || withYou.isNotEmpty() ->
                "随你同行的查找标签（你自己的，或在你开始前被放置的）：${(ownLikely + withYou).joinToString { trackId(it) }}。本窗口内没有新到达的。请逐一核实每个 MAC——不要想当然地认为是你自己的。"
            beaconsWithYou.isNotEmpty() || wearablesWithYou.isNotEmpty() ->
                "没有查找标签随轨迹同行。"
            named.isEmpty() ->
                "在此 15 分钟窗口内没有特征命中，也没有追踪器与 GPS 同行。"
            else ->
                "本窗口内没有查找标签、零售信标或可穿戴设备明显随你的 GPS 轨迹同行。"
        }
        return (core + extra).trim()
    }

    /** Label and MAC, plus the live-decode name when the signature asked for one. */
    private fun trackId(hit: FollowHit): String {
        val labels = hit.device.liveDecode.reportLabels()
        val id = "${hit.label} ${hit.device.mac}"
        return if (labels.isEmpty()) id else "$id (${labels.joinToString(", ")})"
    }

    private fun craftSentence(pictures: List<AircraftTrail.Picture>): String {
        if (pictures.isEmpty()) return ""
        val bits = pictures.take(3).joinToString { pic ->
            if (pic.status.isBlank()) pic.title else "${pic.title} (${pic.status})"
        }
        val more = if (pictures.size > 3) " 以及另外 ${pictures.size - 3} 个" else ""
        return " 广播位置：$bits$more。"
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

    private fun absDelta(a: Long, b: Long) = kotlin.math.abs(a - b)

    private fun utc(ms: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }

    private fun clock(ms: Long): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }

    private fun fmtDist(m: Double): String =
        if (m >= 1000.0) String.format(Locale.US, "%.2f km", m / 1000.0) else "${m.toInt()} m"

    private fun fmtCoord(s: GpsSample): String =
        String.format(Locale.US, "%.5f, %.5f", s.lat, s.lon)

    private fun placeAndGps(lat: Double, lon: Double, places: DebriefPlaces): String {
        val gps = fmtCoord(GpsSample(0L, lat, lon))
        val name = places.nameNear(lat, lon)
        return if (!name.isNullOrBlank()) {
            "$name  （$gps，操作员手机）"
        } else if (places.attempted) {
            "$gps  （操作员手机；本次导出无街道名称）"
        } else {
            "$gps  （操作员手机）"
        }
    }

    private fun fmtDur(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val m = s / 60
        val r = s % 60
        return if (m >= 60) "${m / 60}h${m % 60}m" else if (m > 0) "${m}m${r}s" else "${r}s"
    }
}
