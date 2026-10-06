package app.fieldwatch.domain

/** Two sit windows, keyed kind + MAC. BLE rotation is a new row. */
object SitDiff {
    data class Radio(
        val key: String,
        val kind: RadioKind,
        val mac: String,
        val name: String,
        val extraAttention: Boolean,
        val named: Boolean,
        val bookmarked: Boolean = false,
        val fleetNames: List<String>,
        val fleetIds: List<String> = emptyList(),
        val randomized: Boolean = false,
        val lat: Double? = null,
        val lon: Double? = null,
        val observerNotes: String = "",
        val gpsTrail: List<GpsSample> = emptyList(),
        val firstSeen: Long = 0L,
        val lastSeen: Long = 0L,
        val liveDecode: List<LiveDecodeChip> = emptyList(),
        val payloadLat: Double? = null,
        val payloadLon: Double? = null,
        val payloadAlt: Double? = null,
        val payloadHeading: Double? = null,
        val payloadSpeed: Double? = null,
        val payloadOpLat: Double? = null,
        val payloadOpLon: Double? = null,
        val payloadUasId: String = "",
        val payloadTrail: List<PayloadFix> = emptyList(),
        val payloadAircraft: String = "",
        val mine: Boolean = false,
    )

    data class Side(
        val name: String,
        val ram: Boolean,
        val radios: List<Radio>,
        val path: List<GpsSample> = emptyList(),
        val floods: List<FloodBurst> = emptyList(),
    ) {
        val keys: Set<String> get() = radios.map { it.key }.toSet()

        /** The sit file still holds these radios. Reports leave the counted addresses out. */
        fun withoutFloodRadios(): Side {
            val aside = FloodBurst.keysOf(floods)
            if (aside.isEmpty()) return this
            return copy(radios = radios.filter { it.key !in aside })
        }
    }

    fun secondSitChoices(
        closed: List<SitSummary>,
        thisSavedId: String?,
    ): List<SitSummary> = closed.filter { it.id != thisSavedId }

    fun defaultSecondSitId(
        closed: List<SitSummary>,
        thisSavedId: String?,
    ): String? = secondSitChoices(closed, thisSavedId).firstOrNull()?.id

    fun thisSavedId(open: SitSummary?, selectedId: String?): String? =
        if (open != null) null else selectedId

    fun fromSighting(
        device: Sighting,
        fleets: List<Fleet>,
        customNames: Map<String, String>,
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        mineKeys: Set<String> = emptySet(),
    ): Radio = Radio(
        key = device.key,
        kind = device.kind,
        mac = device.mac,
        name = device.reportName(customNames),
        extraAttention = device.attentionNotes(fleets).isNotEmpty(),
        named = device.key in customNames,
        bookmarked = device.key in bookmarkedKeys,
        fleetNames = device.fleetIds.map { id -> fleets.firstOrNull { it.id == id }?.name ?: id },
        fleetIds = device.fleetIds.toList(),
        randomized = device.randomized,
        lat = SitPathPlot.loudestFix(device)?.lat ?: device.latitude,
        lon = SitPathPlot.loudestFix(device)?.lon ?: device.longitude,
        observerNotes = observerNotes[device.key].orEmpty(),
        gpsTrail = device.gpsTrail,
        firstSeen = device.firstSeen,
        lastSeen = device.lastSeen,
        liveDecode = device.liveDecode,
        payloadLat = device.payloadLat,
        payloadLon = device.payloadLon,
        payloadAlt = device.payloadAlt,
        payloadHeading = device.payloadHeading,
        payloadSpeed = device.payloadSpeed,
        payloadOpLat = device.payloadOpLat,
        payloadOpLon = device.payloadOpLon,
        payloadUasId = device.payloadUasId?.trim().orEmpty(),
        payloadTrail = device.payloadTrail,
        payloadAircraft = device.payloadAircraft?.trim().orEmpty(),
        mine = device.key in mineKeys,
    )

    fun fromSitRadio(
        row: SitRadio,
        fleets: List<Fleet>,
        customNames: Map<String, String>,
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        mineKeys: Set<String> = emptySet(),
    ): Radio = Radio(
        key = row.key,
        kind = row.kind,
        mac = row.mac,
        name = customNames[row.key]?.trim()?.takeIf { it.isNotEmpty() } ?: row.name.ifBlank { row.mac },
        extraAttention = row.extraAttention,
        named = row.key in customNames,
        bookmarked = row.key in bookmarkedKeys,
        fleetNames = row.fleetIds.map { id -> fleets.firstOrNull { it.id == id }?.name ?: id },
        fleetIds = row.fleetIds.toList(),
        randomized = row.randomized,
        lat = SitPathPlot.loudestFix(row.gpsTrail)?.lat,
        lon = SitPathPlot.loudestFix(row.gpsTrail)?.lon,
        observerNotes = observerNotes[row.key].orEmpty(),
        gpsTrail = row.gpsTrail,
        firstSeen = row.firstSeen,
        lastSeen = row.lastSeen,
        liveDecode = row.liveDecode,
        payloadLat = row.payloadLat,
        payloadLon = row.payloadLon,
        payloadAlt = row.payloadAlt,
        payloadHeading = row.payloadHeading,
        payloadSpeed = row.payloadSpeed,
        payloadOpLat = row.payloadOpLat,
        payloadOpLon = row.payloadOpLon,
        payloadUasId = row.payloadUasId?.trim().orEmpty(),
        payloadTrail = row.payloadTrail,
        payloadAircraft = row.payloadAircraft?.trim().orEmpty(),
        mine = row.key in mineKeys,
    )

    fun report(
        thisSit: Side,
        second: Side,
        demoMode: Boolean,
        showAllRadios: Boolean = false,
    ): String {
        val macs = (thisSit.radios + second.radios).map { it.mac }
        return document(thisSit, second, showAllRadios = showAllRadios)
            .withDemoMacs(macs, demoMode)
            .toPlainText()
    }

    /** Same shape as Debrief so Compare (PDF) uses the Debrief letter layout. */
    fun document(
        thisSit: Side,
        second: Side,
        watchedFleetIds: Set<String> = emptySet(),
        showAllRadios: Boolean = false,
    ): DebriefDoc {
        val thisSit = thisSit.withoutFloodRadios()
        val second = second.withoutFloodRadios()
        val thisKeys = thisSit.keys
        val secondKeys = second.keys
        val byKey = (thisSit.radios + second.radios).associateBy { it.key }
        val onlyThis = thisKeys.minus(secondKeys)
        val onlySecond = secondKeys.minus(thisKeys)
        val both = thisKeys.intersect(secondKeys)
        val ramNote = if (thisSit.ram || second.ram) {
            "最近 15 分钟是实时的内存集合（约 400 台设备）。 " +
                "已命名的监测最多保留 ${Sit.RADIO_CAP} 台。两者的计数不是同一范围。"
        } else {
            null
        }
        val extraHits = exclusiveExtra(onlyThis, onlySecond, byKey)
        val sections = ArrayList<DebriefSection>()
        var n = 1
        fun next() = n++.toString()
        sections += DebriefSection(
            next(),
            "窗口",
            buildString {
                append(sideBlock("此监测", thisSit))
                append(sideBlock("第二个监测", second))
                if (ramNote != null) {
                    appendLine(ramNote)
                    appendLine()
                }
                append("本机侦听到的设备。类型 + MAC。BLE 轮换算作新行。不是对设备的定位。")
            },
            chart = presenceChart(onlyThis, onlySecond, both, byKey),
        )
        val thisCraft = AircraftTrail.pictures(thisSit.radios.mapNotNull { it.toCraftSource() }, thisSit.path)
        val secondCraft = AircraftTrail.pictures(second.radios.mapNotNull { it.toCraftSource() }, second.path)
        val aircraftBody = AircraftTrail.compareBody(thisSit.name, thisCraft, second.name, secondCraft)
        if (aircraftBody.isNotEmpty()) {
            sections += DebriefSection(next(), "飞行器", aircraftBody)
        }
        observerNotesSection(thisSit, second)?.let { body ->
            sections += DebriefSection(next(), "观察者备注", body)
        }
        markedMineSection(thisSit, second)?.let { body ->
            sections += DebriefSection(next(), "标记为我的", body)
        }
        if (extraHits.isNotEmpty()) {
            sections += DebriefSection(
                next(),
                "特别关注",
                extraHits.joinToString("\n") { "${it.radioLabel}\n${it.note}" },
                alert = true,
            )
        }
        floodSection(thisSit, second)?.let { body ->
            sections += DebriefSection(next(), "洪泛", body)
        }
        sections += rosterSection(next(), "仅在此监测中（${onlyThis.size}）", onlyThis, byKey, showAllRadios)
        sections += rosterSection(next(), "仅在第二个监测中（${onlySecond.size}）", onlySecond, byKey, showAllRadios)
        sections += bothSection(next(), "两者皆有（${both.size}）", both, thisSit, second, byKey, showAllRadios)
        val meta = buildList {
            add("此监测" to thisSit.name)
            add("第二个监测" to second.name)
            add("此监测设备数" to thisSit.radios.size.toString())
            add("第二监测设备数" to second.radios.size.toString())
            if (ramNote != null) add("上限" to "内存约 400 对比监测 ${Sit.RADIO_CAP}")
        }
        return DebriefDoc(
            generatedUtc = "",
            windowLine = "${thisSit.name} vs ${second.name}",
            meta = meta,
            disclaimer = FieldwatchDisclaimer.compare(),
            trackingAlert = extraHits.isNotEmpty(),
            takeaway = compareTakeaway(onlyThis.size, onlySecond.size, both.size, mineCount(thisSit, second)),
            sections = sections,
            extraAttention = extraHits,
            heading = "FIELDWATCH 监测对比",
            pdfKicker = "监测对比",
            pdfTitle = "监测对比",
            pathFigure = AircraftTrail.applyWalk(
                AircraftTrail.applyWalk(
                    pathFigure(thisSit, second, watchedFleetIds),
                    thisCraft,
                    secondary = false,
                ),
                secondCraft,
                secondary = true,
            ),
            extraFigures = AircraftTrail.compareOwnFigures(thisCraft, secondCraft),
        )
    }

    private fun pathFigure(
        thisSit: Side,
        second: Side,
        watchedFleetIds: Set<String>,
    ): SitPathPlot.Figure? {
        val tracks = listOfNotNull(
            Geo.despikePath(thisSit.path).takeIf { it.size >= 2 }?.let {
                SitPathPlot.FigureTrack(thisSit.name, it, secondary = false)
            },
            Geo.despikePath(second.path).takeIf { it.size >= 2 }?.let {
                SitPathPlot.FigureTrack(second.name, it, secondary = true)
            },
        )
        if (tracks.isEmpty()) return null
        val pinNote = "一个 MAC 警报或特征警报只绘制一次。解码出的经纬度是最后广播的位置。其他情况都是最强的侦听位置。数字表示该地点（轨迹键）。"
        val points = (thisSit.radios + second.radios)
            .filter { it.bookmarked || it.fleetIds.any { id -> id in watchedFleetIds } }
            .distinctBy { it.key }
            .mapNotNull { r ->
                val advertised = r.advertisedCoord()
                val pin = advertised ?: r.hearCoord() ?: return@mapNotNull null
                val notes = RadioBookmarks.pathNote(r.bookmarked, r.observerNotes, r.mine)
                val label = r.name.ifBlank { r.mac }
                val kept = r.payloadTrail.lastOrNull { PayloadLocation.validCoord(it.lat, it.lon) }
                SitPathPlot.Dot(
                    key = r.key,
                    lat = pin.first,
                    lon = pin.second,
                    label = label,
                    extraAttention = r.extraAttention,
                    named = r.bookmarked || r.named,
                    kind = r.kind,
                    mac = r.mac,
                    fleetNames = r.fleetNames,
                    observerNotes = notes,
                    advertised = advertised != null,
                    advertisedNote = if (advertised != null) {
                        AircraftTrail.advertisedNote(
                            status = r.liveDecode.reportLabels().joinToString(", "),
                            uasId = r.payloadUasId,
                            label = label,
                            lat = pin.first,
                            lon = pin.second,
                            alt = kept?.alt ?: r.payloadAlt,
                            heading = kept?.heading ?: r.payloadHeading,
                            speed = kept?.speed ?: r.payloadSpeed,
                            pilotLat = r.payloadOpLat,
                            pilotLon = r.payloadOpLon,
                            aircraft = r.payloadAircraft,
                        )
                    } else {
                        ""
                    },
                )
            }
            .sortedBy { it.label }
            .take(48)
        val dots = points
        val all = tracks.flatMap { it.samples }
        val cap = if (tracks.size == 2) {
            "同一张北向朝上的图上显示两段行走轨迹。绿色 = 此监测。蓝色 = 第二个监测。$pinNote"
        } else {
            "北向朝上。线条是本机。$pinNote"
        }
        return SitPathPlot.Figure(
            kicker = if (tracks.size == 2) "操作员轨迹" else "操作员轨迹",
            tracks = tracks,
            dots = dots,
            lengthM = Geo.pathLengthM(all),
            spanM = Geo.spanM(all),
            caption = cap,
        )
    }

    private fun Radio.advertisedCoord(): Pair<Double, Double>? {
        val kept = payloadTrail.lastOrNull { PayloadLocation.validCoord(it.lat, it.lon) }
        if (kept != null) return kept.lat to kept.lon
        if (PayloadLocation.validCoord(payloadLat, payloadLon)) return payloadLat!! to payloadLon!!
        return null
    }

    private fun Radio.hearCoord(): Pair<Double, Double>? {
        val la = lat ?: return null
        val lo = lon ?: return null
        return if (PayloadLocation.validCoord(la, lo)) la to lo else null
    }

    private fun Radio.toCraftSource(): AircraftTrail.Source? {
        val fixes = payloadTrail.ifEmpty {
            val lat = payloadLat ?: return null
            val lon = payloadLon ?: return null
            listOf(PayloadFix(lastSeen, lat, lon, payloadAlt, payloadHeading, payloadSpeed))
        }.filter { PayloadLocation.validCoord(it.lat, it.lon) }
        if (fixes.isEmpty()) return null
        val last = fixes.last()
        return AircraftTrail.Source(
            uasId = payloadUasId,
            title = name.ifBlank { mac },
            lastSeen = lastSeen,
            status = liveDecode.reportLabels().joinToString(", "),
            fixes = fixes,
            alt = last.alt ?: payloadAlt,
            heading = last.heading ?: payloadHeading,
            speed = last.speed ?: payloadSpeed,
            pilotLat = payloadOpLat,
            pilotLon = payloadOpLon,
            key = key,
            mac = mac,
            aircraft = payloadAircraft,
        )
    }

    private fun sideBlock(heading: String, side: Side): String {
        val net = if (side.ram) {
            "内存中最近 15 分钟（约 400 台设备）"
        } else {
            "已命名窗口（最多 ${Sit.RADIO_CAP} 台）"
        }
        return "$heading：${side.name}\n${side.radios.size} 台设备 · $net\n"
    }

    private fun exclusiveExtra(
        onlyThis: Set<String>,
        onlySecond: Set<String>,
        byKey: Map<String, Radio>,
    ): List<ExtraAttentionHit> {
        fun hits(keys: Set<String>, where: String) = keys.mapNotNull { key ->
            val row = byKey[key] ?: return@mapNotNull null
            if (!row.extraAttention) return@mapNotNull null
            ExtraAttentionHit(
                signature = row.fleetNames.firstOrNull() ?: "特别关注",
                radioLabel = line(row),
                note = where,
            )
        }
        return hits(onlyThis, "仅在此监测中。") + hits(onlySecond, "仅在第二个监测中。")
    }

    private fun presenceChart(
        onlyThis: Set<String>,
        onlySecond: Set<String>,
        both: Set<String>,
        byKey: Map<String, Radio>,
    ): ReportChart {
        fun row(label: String, keys: Set<String>): ReportBar {
            var wifi = 0
            var ble = 0
            for (key in keys) {
                val radio = byKey[key] ?: continue
                if (radio.kind == RadioKind.WIFI) wifi++ else ble++
            }
            return ReportBar(label, wifi, second = ble)
        }
        return ReportChart(
            split = true,
            rows = listOf(
                row("仅在此监测中", onlyThis),
                row("两者皆有", both),
                row("仅在第二个监测中", onlySecond),
            ),
        )
    }

    private fun rosterSection(
        number: String,
        title: String,
        keys: Set<String>,
        byKey: Map<String, Radio>,
        showAll: Boolean,
    ): DebriefSection {
        if (keys.isEmpty()) return DebriefSection(number, title, "(none)")
        val radios = keys.mapNotNull { byKey[it] }
        val shown = if (showAll) keys else keys.filter { byKey[it]?.keepShort() == true }.toSet()
        return DebriefSection(
            number,
            title,
            body = "",
            chart = radioSignatureChart(radios),
            after = if (shown.isEmpty()) "" else listBody(shown, byKey),
        )
    }

    private fun bothSection(
        number: String,
        title: String,
        keys: Set<String>,
        thisSit: Side,
        second: Side,
        byKey: Map<String, Radio>,
        showAll: Boolean,
    ): DebriefSection {
        if (keys.isEmpty()) return DebriefSection(number, title, "(none)")
        val earlier = thisSit.radios.associateBy { it.key }
        val later = second.radios.associateBy { it.key }
        val shown = if (showAll) {
            keys
        } else {
            keys.filter { key ->
                val a = earlier[key] ?: return@filter false
                val b = later[key] ?: return@filter false
                a.keepShort() || b.keepShort() || decodeChanged(a, b)
            }.toSet()
        }
        return DebriefSection(
            number,
            title,
            body = "",
            chart = radioSignatureChart(keys.mapNotNull { byKey[it] }),
            after = if (shown.isEmpty()) "" else bothBody(shown, thisSit, second),
        )
    }

    private fun radioSignatureChart(radios: List<Radio>): ReportChart? {
        if (radios.isEmpty()) return null
        val counts = linkedMapOf<String, Int>()
        for (radio in radios) {
            val sigs = radio.fleetNames.filter { it.isNotBlank() }.distinct()
            val key = if (sigs.isEmpty()) "未匹配" else sigs.joinToString(" + ")
            counts[key] = (counts[key] ?: 0) + 1
        }
        val rows = counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { ReportBar(it.key, it.value) }
        return ReportChart(rows = rows)
    }

    private fun Radio.keepShort(): Boolean =
        extraAttention || named || mine || bookmarked

    private fun decodeChanged(earlier: Radio, later: Radio): Boolean {
        val left = earlier.liveDecode.reportLabels()
        val right = later.liveDecode.reportLabels()
        return left.isNotEmpty() && right.isNotEmpty() && left != right
    }

    private fun listBody(keys: Set<String>, byKey: Map<String, Radio>): String {
        if (keys.isEmpty()) return "(none)"
        return keys.mapNotNull { byKey[it] }
            .sortedWith(
                compareByDescending<Radio> { it.extraAttention }
                    .thenByDescending { it.named }
                    .thenBy { it.kind.name }
                    .thenBy { it.mac },
            )
            .joinToString("\n") { line(it) }
    }

    /** Kind + MAC is the same radio. A live label can still change between windows. */
    private fun bothBody(keys: Set<String>, thisSit: Side, second: Side): String {
        if (keys.isEmpty()) return "(none)"
        val earlier = thisSit.radios.associateBy { it.key }
        val later = second.radios.associateBy { it.key }
        return keys.mapNotNull { key ->
            val a = earlier[key] ?: return@mapNotNull null
            val b = later[key] ?: return@mapNotNull null
            a to b
        }.sortedWith(
            compareByDescending<Pair<Radio, Radio>> { it.second.extraAttention }
                .thenByDescending { it.second.named }
                .thenBy { it.second.kind.name }
                .thenBy { it.second.mac },
        ).joinToString("\n") { (a, b) -> bothLine(a, b) }
    }

    private fun bothLine(earlier: Radio, later: Radio): String = buildString {
        append(line(later, chips = false))
        val left = earlier.liveDecode.reportLabels()
        val right = later.liveDecode.reportLabels()
        when {
            left.isNotEmpty() && right.isNotEmpty() && left != right -> {
                append("  decoded value changed: ")
                append(left.joinToString(", "))
                append(" → ")
                append(right.joinToString(", "))
            }
            else -> append(chipSuffix(if (right.isNotEmpty()) later.liveDecode else earlier.liveDecode))
        }
    }

    private fun line(row: Radio, chips: Boolean = true): String = buildString {
        append(if (row.kind == RadioKind.WIFI) "WIFI" else "BLE ")
        append("  ")
        append(row.mac)
        val label = row.name.trim()
        if (label.isNotEmpty() && !label.equals(row.mac, ignoreCase = true)) {
            append("  ")
            append(label)
        }
        if (row.mine) append("  标记为我的")
        row.fleetNames.filter { it.isNotBlank() }.forEach { name ->
            append("  ")
            append(name)
        }
        if (row.extraAttention) append("  特别关注")
        if (chips) append(chipSuffix(row.liveDecode))
    }

    private fun chipSuffix(chips: List<LiveDecodeChip>): String {
        val labels = chips.reportLabels()
        val notes = chips.map { it.note.trim() }.filter { it.isNotEmpty() }.distinct()
        if (labels.isEmpty() && notes.isEmpty()) return ""
        return buildString {
            if (labels.isNotEmpty()) {
                append("  ")
                append(labels.joinToString(", "))
            }
            if (notes.isNotEmpty()) {
                append("  ")
                append(notes.joinToString(" "))
            }
        }
    }

    private fun mineCount(thisSit: Side, second: Side): Int =
        (thisSit.radios + second.radios).distinctBy { it.key }.count { it.mine }

    private fun compareTakeaway(onlyThis: Int, onlySecond: Int, both: Int, mine: Int): String {
        val counts = "$onlyThis 个仅在此监测中 · $onlySecond 个仅在第二个监测中 · $both 个两者皆有。"
        return if (mine > 0) "$counts · $mine 个标记为我的。" else counts
    }

    private fun floodSection(thisSit: Side, second: Side): String? {
        if (thisSit.floods.isEmpty() && second.floods.isEmpty()) return null
        fun StringBuilder.linesFor(side: Side) {
            if (side.floods.isEmpty()) return
            appendLine(side.name)
            side.floods.sortedBy { it.at }.forEach { burst ->
                appendLine("  ${burst.reportLine(FloodBurst.clock(burst.at))}")
            }
        }
        return buildString {
            appendLine(FloodBurst.intro(thisSit.floods + second.floods))
            appendLine()
            linesFor(thisSit)
            if (thisSit.floods.isNotEmpty() && second.floods.isNotEmpty()) appendLine()
            linesFor(second)
        }.trimEnd()
    }

    private fun markedMineSection(thisSit: Side, second: Side): String? {
        fun where(key: String): String = when {
            key in thisSit.keys && key in second.keys -> "both"
            key in thisSit.keys -> "this sit"
            else -> "second sit"
        }
        val rows = (thisSit.radios + second.radios).distinctBy { it.key }.filter { it.mine }
        if (rows.isEmpty()) return null
        return buildString {
            appendLine("你标记为我的设备。在两个窗口中都可能侦听到。仍会列出。")
            rows.sortedWith(
                compareBy<Radio> { where(it.key) }.thenBy { it.mac },
            ).forEach { r ->
                val kind = if (r.kind == RadioKind.WIFI) "WIFI" else "BLE"
                val label = r.name.trim().takeIf { it.isNotEmpty() && !it.equals(r.mac, ignoreCase = true) }
                append("  · $kind  ${r.mac}")
                if (label != null) append("  ").append(label)
                append("  ").append(where(r.key))
                appendLine()
            }
        }.trimEnd()
    }

    private fun observerNotesSection(thisSit: Side, second: Side): String? {
        fun where(key: String): String = when {
            key in thisSit.keys && key in second.keys -> "both"
            key in thisSit.keys -> "this sit"
            else -> "second sit"
        }
        val rows = (thisSit.radios + second.radios)
            .distinctBy { it.key }
            .mapNotNull { r ->
                val note = r.observerNotes.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                r to note
            }
        if (rows.isEmpty()) return null
        return buildString {
            appendLine("你对任一窗口中侦听到的设备所加的说明。与已命名设备使用相同的类型+MAC。不是目录备注。")
            rows.sortedWith(
                compareBy<Pair<Radio, String>> { where(it.first.key) }.thenBy { it.first.mac },
            ).forEach { (r, note) ->
                val kind = if (r.kind == RadioKind.WIFI) "WIFI" else "BLE"
                val label = r.name.trim().takeIf { it.isNotEmpty() && !it.equals(r.mac, ignoreCase = true) }
                append("  · $kind  ${r.mac}")
                if (label != null) append("  ").append(label)
                append("  ").append(where(r.key))
                appendLine()
                appendLine("    $note")
            }
        }.trimEnd()
    }
}
