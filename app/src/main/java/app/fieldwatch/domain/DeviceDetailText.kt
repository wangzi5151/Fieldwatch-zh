package app.fieldwatch.domain

import app.fieldwatch.radio.BleAdParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plain-text dump of the device-detail screen. Same fields, no sparkline/presence art.
 * Not a legal identity.
 */
object DeviceDetailText {
    fun build(
        device: Sighting,
        signatureNames: List<String>,
        now: Long = System.currentTimeMillis(),
        attentionNotes: List<Pair<String, String>> = emptyList(),
        signatureNotes: List<Pair<String, String>> = emptyList(),
        fleets: List<Fleet> = emptyList(),
        mine: Boolean = false,
    ): String {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        val iso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)
        val facts = device.facts
        val title = device.listTitle(signatureNames)
        val guess = DeviceExplain.guess(device, signatureNames)
        val out = StringBuilder()

        fun line(label: String, value: String) {
            out.append(label).append(": ").append(value.trim()).append('\n')
        }
        fun section(title: String) {
            out.append('\n').append("## ").append(title).append('\n')
        }

        out.append("Fieldwatch 设备详情\n")
        out.append(iso.format(Date(now))).append('\n')
        out.append(
            "实验性功能。并非合法身份。原生 Android 无线设备 — 这是操作系统 " +
                "所暴露的信息，并不保证存在跟踪器或摄像头。\n",
        )
        out.append('\n')
        out.append(title).append('\n')
        line("MAC", device.mac)
        if (device.name.isNotBlank()) line("广播名称", device.name)
        if (mine) line("标记为我的", "开启期间不发出提示音。仍会列出。")

        out.append('\n')
        out.append("看起来像： ").append(guess.headline).append('\n')
        out.append(guess.because).append('\n')
        if (attentionNotes.isNotEmpty()) {
            section("特别关注")
            attentionNotes.forEach { (name, note) ->
                out.append("特别关注（$name）： ").append(note.trim()).append('\n')
            }
            out.append("模式匹配，不是身份。也不是安全结论。\n")
        }
        if (signatureNotes.isNotEmpty()) {
            section("备注")
            signatureNotes.forEach { (name, note) ->
                out.append(name).append(": ").append(note.trim()).append('\n')
            }
        }

        section("标识")
        line(
            "无线设备",
            if (device.kind == RadioKind.WIFI) {
                "Wi-Fi 接入点（正在广播网络）"
            } else {
                "低功耗蓝牙广播设备"
            },
        )
        line("地址", DeviceExplain.addressExplain(device))
        vendorLine(device)?.let { line("制造商", it.replace('\n', ' ')) }
            ?: line("OUI（厂商前缀）", "${device.oui} — 无 IEEE 匹配；随机化地址通常没有。")
        if (device.hiddenSsid) {
            line("网络名称（SSID）", "隐藏 — 接入点正在广播但未发布名称")
        }

        section("信号")
        if (device.gone) {
            line("此处信号强度（RSSI）", "不可用")
            val last = Rssi.lastMeasured(device.rssi, device.rssiHistory)
            line("最后侦听到", last?.let { "$it dBm" } ?: "不可用")
        } else {
            line("此处信号强度（RSSI）", DeviceExplain.rssiExplain(device.rssi))
            out.append("越接近 0 dBm 表示此处越响，而不是距离。\n")
        }
        line("本次会话侦听到的范围", Rssi.sessionRange(device.rssiMin, device.rssiMax, device.rssiHistory))
        facts.txPowerDbm?.let {
            line("声称的发射功率", "$it dBm — 它声称的发射强度，并不代表距离")
        }
        if (device.channel != 0 || device.frequencyMhz != 0) {
            line(
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
        facts.wifiStandard?.let { line("Wi-Fi 代际", it) }
        if (facts.centerFreq0 != null || facts.centerFreq1 != null) {
            line(
                "中心频率",
                listOfNotNull(
                    facts.centerFreq0?.let { "$it MHz" },
                    facts.centerFreq1?.let { "$it MHz" },
                ).joinToString("  ·  "),
            )
        }
        val rssiTail = device.rssiHistory.filter { Rssi.measured(it.rssi) }.takeLast(24)
        if (rssiTail.isNotEmpty()) {
            line(
                "最近的 RSSI（最旧 → 最新）",
                rssiTail.joinToString(", ") { it.rssi.toString() },
            )
        }

        if (device.kind == RadioKind.BLE) {
            section("蓝牙广播")
            facts.primaryPhy?.let {
                val phys = listOfNotNull(it, facts.secondaryPhy).distinct()
                line("无线 PHY", phys.joinToString(" / ") { phy -> DeviceExplain.phyExplain(phy) })
            }
            facts.connectable?.let {
                line(
                    "可连接",
                    if (it) "是 — 手机可以建立 BLE 连接"
                    else "否 — 仅广播（你能侦听到它，但无法通过本次扫描加入）",
                )
            }
            facts.advertisingIntervalMs?.let {
                line("广播频率", "%.0f ms 每次广播间隔（越小 = 空中越频繁）".format(it))
            }
            facts.periodicIntervalMs?.let { line("周期性广播", "%.0f ms".format(it)) }
            facts.advFlags?.let { flags ->
                line("可发现性", DeviceExplain.flagsExplain(flags))
                line("标志（原始）", "0x%02X".format(flags))
            }
            facts.appearance?.let { value ->
                val name = RadioDb.appearance(value)
                line(
                    "它自称的类型（Appearance）",
                    name ?: "未列出的外观 0x%04X".format(value),
                )
                line("外观代码", "0x%04X".format(value))
            }
            CodDecoder.decodeOrNull(facts.deviceClass)?.let { cod ->
                line(
                    "经典蓝牙类别",
                    buildString {
                        append(cod.major)
                        if (cod.minor.isNotBlank()) append(" / ").append(cod.minor)
                        if (cod.services.isNotEmpty()) {
                            append("。还提供： ")
                            append(cod.services.joinToString(", "))
                        }
                    },
                )
            }
        }

        if (device.kind == RadioKind.WIFI) {
            section("Wi-Fi 接入点")
            facts.security?.let {
                line("加密 / 登录", DeviceExplain.wifiSecurityExplain(it))
                if (it.isNotBlank()) line("安全字符串", it)
            }
            facts.supportedRates?.let { line("支持的速率", "$it Mbps  （* = 必需基础速率）") }
            facts.capabilities?.takeIf { it.isNotBlank() && it != facts.security }?.let {
                line("能力字符串", it)
            }
        }

        device.payloadAircraft?.trim()?.takeIf { it.isNotEmpty() }?.let { line("飞行器", it) }

        if (fleets.isNotEmpty() && (device.kind == RadioKind.BLE || device.kind == RadioKind.WIFI)) {
            val decoded = SignatureFieldDecoder.decodeSighting(device, fleets)
            if (decoded.isNotEmpty()) {
                section("已解码字段")
                decoded.forEach { row ->
                    line(row.label, row.display)
                    if (row.note.isNotBlank()) line("备注", row.note)
                }
            }
        }

        if (device.serviceUuids.isNotEmpty()) {
            section("它提供的服务")
            line(
                "服务 ID",
                device.serviceUuids.joinToString("; ") { uuid ->
                    DeviceExplain.uuidGloss(uuid)?.let { "$uuid  ·  $it" } ?: uuid
                },
            )
        }
        if (facts.serviceData.isNotEmpty()) {
            facts.serviceData.forEach { sd ->
                val named = RadioDb.serviceUuid(sd.uuid)?.let { " （$it）" } ?: ""
                AdvPayloadDecoder.decodeService(sd).forEach { field -> line(field.label, field.value) }
                line(
                    "服务数据 ${uuidShort(sd.uuid)}$named",
                    sd.dataHex.hexSpaced().ifBlank { "（空）" },
                )
            }
        }

        val mfg = facts.mfgRecords.ifEmpty {
            device.manufacturerId?.let {
                listOf(MfgRecord(it, device.manufacturerDataHex))
            } ?: emptyList()
        }
        if (mfg.isNotEmpty()) {
            section("广告中的厂商数据")
            mfg.forEach { rec ->
                val company = RadioDb.company(rec.companyId) ?: "不在蓝牙公司列表中"
                line("Bluetooth 公司 0x%04X".format(rec.companyId), company)
                BleAdParser.mfgDecodedFields(rec).forEach { (k, v) -> line(k, v) }
                if (rec.dataHex.isNotBlank()) {
                    line("原始负载（${rec.dataHex.length / 2} 字节）", rec.dataHex.hexSpaced())
                }
            }
        }

        if (facts.vendorIes.isNotEmpty() || device.vendorIeOuis.isNotEmpty()) {
            section("Wi-Fi 厂商标签")
            val rows = facts.vendorIes.ifEmpty {
                device.vendorIeOuis.map { VendorIeRecord(it, -1, "") }
            }
            rows.forEach { ie ->
                val org = RadioDb.vendorForOui24(ie.oui)
                val type = if (ie.type >= 0) " type %d".format(ie.type) else ""
                line(
                    "厂商 OUI ${ie.oui}$type",
                    buildString {
                        append(org ?: "未知 IEEE OUI")
                        append(" — 额外的接入点信息元素，不是 SSID。")
                        if (ie.dataHex.isNotBlank()) {
                            append(" ")
                            append(ie.dataHex.hexSpaced())
                        }
                    },
                )
            }
        }

        section("会话")
        line("首次侦听到", fmt.format(Date(device.firstSeen)))
        line("最后侦听到", fmt.format(Date(device.lastSeen)))
        line("命中次数", device.hitCount.toString())
        Geo.screenCoord(device.latitude, device.longitude, false)?.let {
            line("最后定位", it)
            out.append("最后定位是侦听时手机的 GPS，而不是对该设备的定位。\n")
        }
        if (device.fleetIds.isNotEmpty()) {
            line("匹配的特征", signatureNames.joinToString("; ").ifBlank {
                device.fleetIds.joinToString("; ")
            })
        }
        if (device.rawHex.isNotBlank() && device.kind == RadioKind.BLE) {
            line("原始广播", device.rawHex.hexSpaced())
        }
        presenceLine(device, now, fmt)?.let { line("存在状态（15 分钟）", it) }
        return out.toString().trimEnd() + "\n"
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
        return parts.joinToString(" ").ifBlank { null }
    }

    private fun uuidShort(uuid: String): String {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return if (hex.length >= 8 && hex.startsWith("0000")) hex.substring(4, 8) else uuid.take(8)
    }

    private fun presenceLine(device: Sighting, now: Long, fmt: SimpleDateFormat): String? {
        if (device.presence.isEmpty()) return null
        val from = now - 15 * 60 * 1000L
        val spans = device.presence.filter { (it.end ?: now) >= from }
        if (spans.isEmpty()) return null
        return spans.joinToString("; ") { span ->
            val start = fmt.format(Date(span.start.coerceAtLeast(from)))
            val end = span.end?.let { fmt.format(Date(it)) } ?: "now"
            "$start–$end"
        }
    }
}
