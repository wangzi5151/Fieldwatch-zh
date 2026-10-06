package app.fieldwatch.domain

/**
 * Plain-language decode of advertised identity. Guesses are what the radio
 * is broadcasting, not a visual identification.
 */
object DeviceExplain {
    data class Guess(
        val headline: String,
        val because: String,
        val confidence: Confidence,
    )

    enum class Confidence { HIGH, MEDIUM, LOW }

    fun guess(device: Sighting, signatureNames: List<String>): Guess {
        val hints = ArrayList<Hint>(8)
        val appearance = device.facts.appearance?.let { RadioDb.appearance(it) }
        appearanceHint(appearance)?.let { hints += it }
        CodDecoder.decodeOrNull(device.facts.deviceClass)?.let { codHint(it)?.let { h -> hints += h } }
        hints += uuidHints(device.serviceUuids + device.facts.serviceData.map { it.uuid })
        hints += AdvPayloadDecoder.roleHints(device).map {
            Hint(it.bucket, it.label, it.reason, it.weight)
        }
        hints += signatureHints(signatureNames)
        if (device.kind == RadioKind.WIFI) hints += wifiHints(device, signatureNames)

        if (hints.isEmpty()) {
            return Guess(
                headline = if (device.kind == RadioKind.WIFI) {
                    "Wi-Fi 接入点"
                } else {
                    "蓝牙 LE 广播设备"
                },
                because = "It is on the air, but it did not advertise a product class " +
                    "(no Appearance, Class of Device, or well-known service that names a type).",
                confidence = Confidence.LOW,
            )
        }
        val grouped = LinkedHashMap<String, Hint>()
        for (hint in hints.sortedByDescending { it.weight }) {
            val key = hint.bucket
            val prev = grouped[key]
            if (prev == null || hint.weight > prev.weight) grouped[key] = hint
        }
        val best = grouped.values.maxBy { it.weight }
        val support = grouped.values
            .filter { it.bucket == best.bucket || it.weight >= 3 }
            .map { it.reason }
            .distinct()
        val confidence = when {
            best.weight >= 6 -> Confidence.HIGH
            best.weight >= 3 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        val hedge = when (confidence) {
            Confidence.HIGH -> "最可能"
            Confidence.MEDIUM -> "很可能"
            Confidence.LOW -> "可能是"
        }
        return Guess(
            headline = "$hedge ${best.label}",
            because = support.joinToString(" ") +
                " 这是该设备正在广播的内容，并非视觉识别。",
            confidence = confidence,
        )
    }

    /**
     * Compact Live-row title from the same guess as detail. Null if we only
     * know it is an unnamed advertiser — caller may fall back to vendor.
     */
    fun listLabel(device: Sighting, signatureNames: List<String> = emptyList()): String? {
        val guess = guess(device, signatureNames)
        val generic = guess.headline.contains("蓝牙 LE 广播设备", ignoreCase = true) ||
            guess.headline.contains("Wi-Fi 接入点", ignoreCase = true)
        val core = if (generic) null else tidyHeadline(guess.headline)
        val vendor = device.vendor?.trim()?.takeIf { it.isNotBlank() && it.length <= 24 }
        if (core != null) {
            return if (vendor != null && !core.contains(vendor, ignoreCase = true)) {
                "$vendor · $core"
            } else {
                core
            }
        }
        if (vendor != null) return "$vendor 设备"
        return null
    }

    private fun tidyHeadline(headline: String): String {
        var s = headline
            .removePrefix("Most likely ")
            .removePrefix("Probably ")
            .removePrefix("Could be ")
            .trim()
        s = s.replace(Regex("""\s*\([^)]*\)"""), "").trim()
        s = s.removePrefix("an ").removePrefix("a ").trim()
        if (s.isEmpty()) return headline
        return s.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun flagsExplain(flags: Int): String = buildList {
        if (flags and 0x01 != 0) {
            add("有限可发现：短暂寻找附近连接。")
        }
        if (flags and 0x02 != 0) {
            add("可发现：其他 BLE 设备可以找到它。")
        }
        if (flags and 0x04 != 0) {
            add("仅 BLE：没有经典蓝牙（耳机/文件传输无线电）。")
        } else {
            add("除 BLE 外，可能还支持经典蓝牙（BR/EDR）。")
        }
        if (flags and 0x08 != 0 || flags and 0x10 != 0) {
            add("双模芯片：BLE 与经典蓝牙可同时运行。")
        }
    }.joinToString(" ")

    fun phyExplain(label: String): String = when {
        label.contains("Coded") -> "$label — 长距离 BLE（较慢、较远）"
        label.contains("2M") -> "$label — 更快的 BLE（Bluetooth 5）"
        label.contains("1M") -> "$label — 标准 BLE 无线电"
        else -> label
    }

    fun addressExplain(device: Sighting): String {
        val type = device.facts.addressType
        return when {
            device.kind == RadioKind.WIFI && device.randomized ->
                "本地管理的 BSSID。车辆、Mesh 和访客接入点通常保留此地址。不是轮换的手机 MAC。"
            MacUtil.isLocallyAdministered(device.mac) && !device.randomized ->
                "本地管理的地址。本地位已置位，因此这不是 IEEE 出厂分配。"
            type.equals("Public", true) && !device.randomized ->
                "公共出厂地址（稳定，由 IEEE 分配）。"
            type.equals("Random", true) || device.randomized ->
                "随机/隐私地址。MAC 可能变化，因此这不是持久的身份。"
            type.equals("Anonymous", true) ->
                "匿名：协议栈隐藏了地址。"
            else ->
                listOfNotNull(type, "通用 IEEE 地址（稳定的 OUI）。").joinToString(" · ")
        }
    }

    fun rssiBand(rssi: Int): String = when {
        !Rssi.measured(rssi) -> "not available"
        rssi >= -45 -> "very strong"
        rssi >= -60 -> "strong"
        rssi >= -75 -> "medium"
        rssi >= -88 -> "weak"
        else -> "very weak"
    }

    fun rssiExplain(rssi: Int): String =
        if (!Rssi.measured(rssi)) "不可用"
        else "%d dBm · %s".format(rssi, rssiBand(rssi))

    fun wifiSecurityExplain(raw: String): String {
        val bits = ArrayList<String>(4)
        val u = raw.uppercase()
        when {
            "SAE" in u || "WPA3" in u -> bits += "WPA3 密码（SAE 握手）"
            "OWE" in u -> bits += "增强开放（加密，无密码）"
            "PSK" in u && "WPA2" in u -> bits += "WPA2 密码（PSK）"
            "PSK" in u || "WPA" in u -> bits += "Wi-Fi 密码（WPA/PSK）"
            "802.1X" in u || "EAP" in u -> bits += "企业登录（802.1X）"
            "WEP" in u -> bits += "WEP（旧式，弱）"
            "ESS" in u && bits.isEmpty() -> bits += "开放或加密未解析"
        }
        when {
            "CCMP" in u || "GCMP" in u -> bits += "AES 加密"
            "TKIP" in u -> bits += "TKIP（较旧、较弱的加密算法）"
        }
        if ("WPS" in u) bits += "已启用 WPS 设置"
        if ("MESH" in u) bits += "mesh node"
        if ("IBSS" in u) bits += "ad-hoc network"
        if ("ESS" in u) bits += "infrastructure access point"
        return if (bits.isEmpty()) raw else bits.distinct().joinToString(". ") + "."
    }

    fun uuidGloss(uuid: String): String? {
        val name = RadioDb.serviceUuid(uuid)
        val short = uuid16(uuid) ?: return name
        val extra = when (short) {
            0x1800 -> "connection basics"
            0x1801 -> "attribute protocol"
            0x180A -> "model / serial / firmware"
            0x180F -> "battery level"
            0x1812 -> "键盘、鼠标或游戏手柄"
            0x180D -> "heart-rate sensor"
            0x1810 -> "blood-pressure sensor"
            0x181A -> "temperature / humidity style sensor"
            0x1844, 0x1845, 0x1846 -> "LE 骑行功率/速度"
            0x1850, 0x184E, 0x184F -> "LE Audio"
            0xFE2C -> "Google Fast Pair（通常是耳机/音箱）"
            0xFD5A -> "Samsung SmartTag"
            0xFD44 -> "Apple Find My 相关"
            0xFEED, 0xFEDD -> "Tile 追踪器"
            0xFD50 -> "Tuya IoT"
            0xFEBE, 0xFE21 -> "Bose"
            0xFE78 -> "HP 打印机"
            0xFE07 -> "Sonos 音箱"
            0xFEAF, 0xFEB0 -> "Nest Weave"
            0xFCBF -> "ASSA ABLOY Opening Solutions"
            0xFE24 -> "August Home 门锁"
            0xFCF4 -> "Allegion / Schlage"
            0xFCB2 -> "Apple（非 ASSA ABLOY）"
            else -> null
        }
        return when {
            name != null && extra != null -> "$name — $extra"
            name != null -> name
            extra != null -> extra
            else -> null
        }
    }

    private data class Hint(
        val bucket: String,
        val label: String,
        val reason: String,
        val weight: Int,
    )

    private fun appearanceHint(name: String?): Hint? {
        if (name.isNullOrBlank() || name.equals("Unknown", true)) return null
        val n = name.lowercase()
        val (bucket, label, w) = when {
            "ear" in n || "headphone" in n || "headset" in n || "hearable" in n || "hearing" in n ->
                Triple("audio-personal", "earbuds or headphones", 7)
            "speaker" in n || "loudspeaker" in n || "hifi" in n ->
                Triple("audio-speaker", "a speaker", 7)
            "mouse" in n -> Triple("mouse", "a mouse", 8)
            "keyboard" in n -> Triple("keyboard", "a keyboard", 8)
            "gamepad" in n || "joystick" in n -> Triple("gamepad", "a game controller", 7)
            "watch" in n -> Triple("watch", "a watch or wrist wearable", 7)
            "phone" in n -> Triple("phone", "a phone", 6)
            "laptop" in n || "computer" in n || "desktop" in n || "tablet" in n ->
                Triple("computer", "a computer or tablet", 6)
            "tag" in n || "keyring" in n -> Triple("tag", "a finder tag / tracker", 6)
            "remote" in n -> Triple("remote", "a remote control", 6)
            "hid" in n -> Triple("hid", "一个输入设备（键盘、鼠标或类似设备）", 4)
            "heart" in n -> Triple("health", "a heart-rate monitor", 7)
            "glucose" in n || "oximeter" in n || "blood pressure" in n || "thermometer" in n ->
                Triple("health", "a health sensor", 6)
            "display" in n || "monitor" in n -> Triple("display", "一个显示器或电视棒", 4)
            "clock" in n -> Triple("clock", "a clock", 5)
            "glasses" in n -> Triple("glasses", "smart glasses", 6)
            else -> Triple("other", name, 3)
        }
        return Hint(bucket, label, "它通告的 Appearance 为 $name。", w)
    }

    private fun codHint(cod: CodDecoder.Decoded): Hint? {
        val minor = cod.minor.lowercase()
        val major = cod.major.lowercase()
        val (bucket, label, w) = when {
            "headphone" in minor || "headset" in minor || "hands-free" in minor ->
                Triple("audio-personal", "earbuds or a headset", 6)
            "loudspeaker" in minor || "portable audio" in minor || "hifi" in minor || "car audio" in minor ->
                Triple("audio-speaker", "a speaker", 6)
            "pointing" in minor || minor == "mouse" -> Triple("mouse", "a mouse", 7)
            "keyboard" in minor -> Triple("keyboard", "a keyboard", 7)
            "gamepad" in minor || "joystick" in minor -> Triple("gamepad", "a game controller", 6)
            "smartphone" in minor || (major == "phone" && "uncategorized" !in minor) ->
                Triple("phone", "a phone", 5)
            "laptop" in minor || "tablet" in minor || "desktop" in minor ->
                Triple("computer", "a computer", 5)
            "wristwatch" in minor -> Triple("watch", "a watch", 6)
            "heart" in minor || "pulse" in minor || "glucose" in minor || "oximeter" in minor ->
                Triple("health", "a health sensor", 6)
            "audio" in major -> Triple("audio-personal", "an audio device", 3)
            "peripheral" in major -> Triple("hid", "an input accessory", 3)
            "uncategorized" in major || "miscellaneous" in major -> return null
            else -> return null
        }
        val shown = if (cod.minor.isNotBlank() && cod.minor != "未分类") {
            "${cod.major} / ${cod.minor}"
        } else {
            cod.major
        }
        return Hint(bucket, label, "Class of Device 显示 $shown。", w)
    }

    private fun uuidHints(uuids: List<String>): List<Hint> {
        val out = ArrayList<Hint>(4)
        for (uuid in uuids) {
            val id = uuid16(uuid) ?: continue
            when (id) {
                0x1812 -> out += Hint("hid", "一个键盘、鼠标或游戏手柄", "它提供 HID（人机接口）服务。", 5)
                0x1108, 0x1112, 0x111E, 0x110B, 0x110A, 0x1131, 0x1203 ->
                    out += Hint("audio-personal", "耳机、头戴式耳机或音箱", "它提供经典音频/耳机服务。", 5)
                0x184E, 0x184F, 0x1850, 0x1851 ->
                    out += Hint("audio-personal", "LE Audio 耳机或音箱", "它提供 Bluetooth LE Audio 服务。", 6)
                0x180D -> out += Hint("health", "a heart-rate monitor", "它提供心率服务。", 6)
                0x1810 -> out += Hint("health", "a blood-pressure monitor", "它提供血压服务。", 6)
                0x181A -> out += Hint("sensor", "an environmental sensor", "它提供环境感测。", 4)
                0xFE2C -> out += Hint("audio-personal", "earbuds or a speaker", "存在 Google Fast Pair（常见于耳机和音箱）。", 4)
                0xFD5A -> out += Hint("tag", "一个 Samsung SmartTag", "SmartTag 服务 UUID。", 7)
                0xFD44 -> out += Hint("tag", "一个 Apple Find My 配件", "Find My 相关 UUID。", 6)
                0xFEED, 0xFEDD -> out += Hint("tag", "一个 Tile 追踪器", "Tile 服务 UUID。", 7)
            }
        }
        return out
    }

    private fun signatureHints(names: List<String>): List<Hint> {
        return names.mapNotNull { raw ->
            if (isGenericSignatureName(raw)) return@mapNotNull null
            val n = raw.lowercase()
            when {
                "airtag" in n || n == "find my" || "find hub" in n || "dult" in n ->
                    Hint(
                        "tag",
                        when {
                            "dult" in n -> "一个 DULT 查找标签"
                            "find hub" in n -> "Google Find Hub 标签"
                            else -> "一个 Apple AirTag / Find My 标签"
                        },
                        "匹配到特征 $raw。",
                        8,
                    )
                "apple device" in n ->
                    Hint("phone", "一台 iPhone、iPad 或 Mac", "匹配到特征 $raw。", 7)
                "apple audio" in n ->
                    Hint("audio-personal", "AirPods、Beats 或 AirPlay", "匹配到特征 $raw。", 7)
                "microsoft" in n ->
                    Hint("computer", "一个 Windows / Surface / Xbox 无线设备", "匹配到特征 $raw。", 6)
                n == "tesla tstpms" ->
                    Hint("vehicle", "一个 Tesla BLE 轮胎传感器", "匹配到特征 $raw。", 7)
                "tpms" in n || n == "tirecheck" || n == "sytpms" ->
                    Hint("vehicle", "一个 BLE 胎压传感器", "匹配到特征 $raw。", 7)
                n == "vuzix" ->
                    Hint("glasses", "Vuzix 智能眼镜", "匹配到特征 $raw。", 7)
                n == "tesla" ->
                    Hint("vehicle", "一辆 Tesla 车辆（含 Cybertruck）或手机钥匙", "匹配到特征 $raw。", 7)
                n == "google" ->
                    Hint("phone", "一个 Pixel 或其他 Google 无线设备", "匹配到特征 $raw。", 6)
                n == "sony" ->
                    Hint("audio-personal", "Sony 耳机、电视或相机", "匹配到特征 $raw。", 6)
                n == "bose" ->
                    Hint("audio-personal", "Bose 耳机或音箱", "匹配到特征 $raw。", 7)
                n == "garmin" ->
                    Hint("watch", "一块 Garmin 手表或 inReach", "匹配到特征 $raw。", 7)
                n == "amazon" ->
                    Hint("speaker", "一个 Echo、Fire 或其他 Amazon 无线设备", "匹配到特征 $raw。", 6)
                n == "fitbit" ->
                    Hint("watch", "一个 Fitbit", "匹配到特征 $raw。", 7)
                n == "oura" ->
                    Hint("wearable", "一枚 Oura 戒指", "匹配到特征 $raw。", 7)
                n == "logitech" ->
                    Hint("hid", "一个 Logitech 鼠标、键盘或网络摄像头", "匹配到特征 $raw。", 6)
                "jbl" in n || n == "harman" ->
                    Hint("audio-personal", "JBL 或 Harman 音频", "匹配到特征 $raw。", 6)
                n == "sonos" ->
                    Hint("audio-speaker", "一个 Sonos 音箱", "匹配到特征 $raw。", 7)
                n == "gopro" ->
                    Hint("camera", "一个 GoPro", "匹配到特征 $raw。", 7)
                n == "osmo" ->
                    Hint("camera", "一台 DJI Osmo 运动相机", "匹配到特征 $raw。", 7)
                n == "insta360" ->
                    Hint("camera", "一台 Insta360 相机", "匹配到特征 $raw。", 7)
                n == "dji power" ->
                    Hint("iot", "一个 DJI Power 电源站", "匹配到特征 $raw。", 7)
                n == "dji" ->
                    Hint("drone", "一架 DJI 无人机或遥控器", "匹配到特征 $raw。", 7)
                n == "remote id" ->
                    Hint("drone", "一架广播 ASTM Remote ID 的无人机", "匹配到特征 $raw。", 8)
                n == "skydio" ->
                    Hint("drone", "一架 Skydio 无人机", "匹配到特征 $raw。", 7)
                n == "autel" ->
                    Hint("drone", "一架 Autel 无人机", "匹配到特征 $raw。", 7)
                n == "parrot" ->
                    Hint("drone", "一架 Parrot ANAFI 或 Bebop 无人机", "匹配到特征 $raw。", 7)
                n == "hoverair" ->
                    Hint("drone", "一台 HOVERAir 飞行相机", "匹配到特征 $raw。", 7)
                n == "netgear" || n == "orbi" ->
                    Hint("ap", "一个 NETGEAR 或 Orbi 接入点", "匹配到特征 $raw。", 6)
                n == "tp-link" ->
                    Hint("ap", "一个 TP-Link 接入点", "匹配到特征 $raw。", 6)
                n == "asus" ->
                    Hint("ap", "一个 ASUS 接入点", "匹配到特征 $raw。", 6)
                n == "linksys" ->
                    Hint("ap", "一个 Linksys 或 Velop 接入点", "匹配到特征 $raw。", 6)
                n == "eero" ->
                    Hint("ap", "一个 Eero Mesh 节点", "匹配到特征 $raw。", 6)
                n == "google wifi" ->
                    Hint("ap", "一个 Google Wifi 或 Nest Wifi 节点", "匹配到特征 $raw。", 6)
                n == "d-link" ->
                    Hint("ap", "一个 D-Link 接入点", "匹配到特征 $raw。", 6)
                n == "belkin" ->
                    Hint("ap", "一个 Belkin 接入点", "匹配到特征 $raw。", 6)
                n == "xfinity" ->
                    Hint("ap", "一个 Xfinity 网关或热点", "匹配到特征 $raw。", 6)
                n == "spectrum" ->
                    Hint("ap", "一个 Spectrum 网关或 Spectrum Mobile 热点", "匹配到特征 $raw。", 6)
                n == "at&t" ->
                    Hint("ap", "一个 AT&T 网关或 attwifi 热点", "匹配到特征 $raw。", 6)
                n == "verizon" ->
                    Hint("ap", "一个 Verizon 或 Fios 网关", "匹配到特征 $raw。", 6)
                n == "starlink" ->
                    Hint("ap", "一个 Starlink 路由器", "匹配到特征 $raw。", 7)
                n == "meraki" ->
                    Hint("ap", "一个 Cisco Meraki 接入点", "匹配到特征 $raw。", 7)
                n == "cisco" ->
                    Hint("ap", "一个 Cisco Aironet、Catalyst、Business、RV 或 SPVTG 接入点", "匹配到特征 $raw。", 7)
                n == "mist" ->
                    Hint("ap", "一个 Juniper Mist 接入点", "匹配到特征 $raw。", 7)
                n == "t-mobile" ->
                    Hint("ap", "一个 T-Mobile Home Internet 网关或热点", "匹配到特征 $raw。", 6)
                n == "humax" ->
                    Hint("ap", "一个 HUMAX 网关（通常是 T-Mobile Home Internet）", "匹配到特征 $raw。", 6)
                n == "sagemcom" ->
                    Hint("ap", "一个 Sagemcom ISP 网关", "匹配到特征 $raw。", 6)
                n == "arcadyan" ->
                    Hint("ap", "一个 Arcadyan ISP 网关", "匹配到特征 $raw。", 6)
                n == "askey" ->
                    Hint("ap", "一个 Askey ISP / 5G 网关", "匹配到特征 $raw。", 6)
                n == "calix" ->
                    Hint("ap", "一个 Calix 光纤网关", "匹配到特征 $raw。", 6)
                n == "nokia" ->
                    Hint("ap", "一个 Nokia Solutions and Networks 网关", "匹配到特征 $raw。", 6)
                n == "airties" ->
                    Hint("ap", "一个 AirTies ISP Mesh 节点", "匹配到特征 $raw。", 6)
                n == "tenda" ->
                    Hint("ap", "一个 Tenda 接入点", "匹配到特征 $raw。", 6)
                n == "ruijie" ->
                    Hint("ap", "一个 Ruijie 或 Reyee 接入点", "匹配到特征 $raw。", 6)
                n == "dwnet" ->
                    Hint("ap", "一个 DWnet 接入点", "匹配到特征 $raw。", 6)
                n == "wavlink" ->
                    Hint("ap", "一个 WAVLINK 接入点", "匹配到特征 $raw。", 6)
                n == "sercomm" ->
                    Hint("ap", "一个 Sercomm ISP 网关", "匹配到特征 $raw。", 6)
                n == "luxul" ->
                    Hint("ap", "一个 Luxul 接入点", "匹配到特征 $raw。", 6)
                n == "sophos" ->
                    Hint("ap", "一个 Sophos 防火墙或接入点", "匹配到特征 $raw。", 7)
                n == "aumovio" ->
                    Hint("hotspot", "一个 AUMOVIO / Continental 车载 Wi-Fi 无线设备", "匹配到特征 $raw。", 6)
                n == "centurylink" ->
                    Hint("ap", "一个 CenturyLink 网关", "匹配到特征 $raw。", 6)
                n == "gm hotspot" ->
                    Hint("hotspot", "一个 GM 车载热点（Cadillac / GMC / Buick / Chevrolet）", "匹配到特征 $raw。", 6)
                n == "audi mmi" ->
                    Hint("hotspot", "一个 Audi MMI 车载热点", "匹配到特征 $raw。", 6)
                n == "extreme" ->
                    Hint("ap", "一个 Extreme Networks 接入点", "匹配到特征 $raw。", 7)
                n == "adtran" ->
                    Hint("ap", "一个 Adtran 光纤网关（通常是 CenturyLink / Quantum Fiber 的 OEM）", "匹配到特征 $raw。", 6)
                n == "cambium" ->
                    Hint("ap", "一个 Cambium 或 IgniteNet 接入点", "匹配到特征 $raw。", 6)
                n == "trendnet" ->
                    Hint("ap", "一个 TRENDnet 接入点", "匹配到特征 $raw。", 6)
                n == "cudy" ->
                    Hint("ap", "一个 Cudy 便携或家用路由器", "匹配到特征 $raw。", 6)
                n == "snapav" ->
                    Hint("ap", "一个 SnapAV / Control4 / Wattbox 接入点", "匹配到特征 $raw。", 6)
                n == "arlo" ->
                    Hint("camera", "一个 Arlo 摄像头或 VMB 基站", "匹配到特征 $raw。", 6)
                n == "vantiva" ->
                    Hint("ap", "一个 Vantiva 或 Technicolor ISP 网关", "匹配到特征 $raw。", 6)
                n == "hitron" ->
                    Hint("ap", "一个 Hitron 有线网关（通常是 Xfinity 的 OEM）", "匹配到特征 $raw。", 6)
                n == "actiontec" ->
                    Hint("ap", "一个 Actiontec FiOS 或 Frontier 网关", "匹配到特征 $raw。", 6)
                n == "buffalo" ->
                    Hint("ap", "一个 Buffalo AirStation 或路由器", "匹配到特征 $raw。", 6)
                n == "grandstream" ->
                    Hint("ap", "一个 Grandstream GWN 接入点", "匹配到特征 $raw。", 6)
                n == "edgecore" ->
                    Hint("ap", "一个 Edgecore 接入点", "匹配到特征 $raw。", 7)
                n == "watchguard ap" ->
                    Hint("ap", "一个 WatchGuard 防火墙或接入点", "匹配到特征 $raw。", 7)
                n == "mojo" ->
                    Hint("ap", "一个 Mojo Networks / Arista Cognitive Wi-Fi 接入点", "匹配到特征 $raw。", 7)
                n == "winegard" ->
                    Hint("hotspot", "一个 Winegard 房车或船用 Wi-Fi 无线设备", "匹配到特征 $raw。", 6)
                n == "inseego" ->
                    Hint("ap", "一个 Inseego 5G 或 MiFi 热点", "匹配到特征 $raw。", 6)
                n == "franklin" ->
                    Hint("ap", "一个 Franklin Technology 5G 家庭互联网网关（RG3100 级别）", "匹配到特征 $raw。", 6)
                n == "synology" ->
                    Hint("ap", "一个 Synology NAS 或路由器接入点", "匹配到特征 $raw。", 6)
                n == "aruba" ->
                    Hint("ap", "一个 HPE Aruba Instant 或 Instant On 接入点", "匹配到特征 $raw。", 7)
                n == "ruckus" ->
                    Hint("ap", "一个 RUCKUS 接入点", "匹配到特征 $raw。", 7)
                n == "fortinet" ->
                    Hint("ap", "一个 Fortinet FortiAP 或 FortiWiFi", "匹配到特征 $raw。", 7)
                n == "mikrotik" ->
                    Hint("ap", "一个 MikroTik 路由器或接入点", "匹配到特征 $raw。", 6)
                n == "engenius" ->
                    Hint("ap", "一个 EnGenius 接入点", "匹配到特征 $raw。", 6)
                n == "zyxel" ->
                    Hint("ap", "一个 Zyxel 网关或接入点", "匹配到特征 $raw。", 6)
                n == "peplink" ->
                    Hint("ap", "一个 Peplink 或 Pepwave 路由器", "匹配到特征 $raw。", 6)
                n == "openwrt" ->
                    Hint("ap", "一个 OpenWrt 路由器", "匹配到特征 $raw。", 6)
                n == "arris" ->
                    Hint("ap", "一个 Arris 或 SURFboard 有线网关", "匹配到特征 $raw。", 6)
                n == "unifi ap" ->
                    Hint("ap", "一个 Ubiquiti UniFi 接入点", "匹配到特征 $raw。", 7)
                n == "unifi protect" ->
                    Hint("camera", "一台 UniFi Protect Instant 摄像头", "匹配到特征 $raw。", 7)
                n == "unifi" ->
                    Hint("ap", "一个 UniFi / Ubiquiti 名称", "匹配到特征 $raw。", 5)
                n == "ecobee" ->
                    Hint("thermostat", "an ecobee thermostat", "匹配到特征 $raw。", 7)
                n == "sensi" ->
                    Hint("thermostat", "一个 Sensi 温控器", "匹配到特征 $raw。", 6)
                n == "honeywell home" ->
                    Hint("thermostat", "一个 Honeywell Home 或 Lyric 温控器", "匹配到特征 $raw。", 6)
                "honeywell xenon" in n ->
                    Hint("health", "一台 Honeywell Xenon 医疗条码扫描仪", "匹配到特征 $raw。", 7)
                n == "omron" ->
                    Hint("health", "一个 Omron 血压计或体重秤", "匹配到特征 $raw。", 7)
                n == "withings" ->
                    Hint("health", "一个 Withings 体重秤或血压监测器", "匹配到特征 $raw。", 7)
                n == "dexcom" ->
                    Hint("health", "一个 Dexcom 血糖传感器", "匹配到特征 $raw。", 7)
                n == "nest thermostat" ->
                    Hint("thermostat", "一个 Nest 温控器或 Nest Labs BLE 传感器", "匹配到特征 $raw。", 6)
                n == "nest weave" ->
                    Hint("sensor", "一个 Nest Protect、摄像头或其他 Weave BLE 设备", "匹配到特征 $raw。", 7)
                n == "haiku fan" || n == "haiku" ->
                    Hint("fan", "一台 Haiku 或 Mammoth 吊扇", "匹配到特征 $raw。", 7)
                n == "tuya" ->
                    Hint("iot", "一个 Tuya BLE 小设备（插座、灯、摄像头、传感器）", "匹配到特征 $raw。", 6)
                n == "seos" || n == "assa abloy" ->
                    Hint("access", "一个 ASSA ABLOY 锁、Yale 锁、HID 读卡器或 Seos 凭证", "匹配到特征 $raw。", 7)
                n == "august" ->
                    Hint("lock", "一个 August 智能锁", "匹配到特征 $raw。", 7)
                n == "schlage" ->
                    Hint("lock", "一个 Schlage 或 Allegion 锁", "匹配到特征 $raw。", 7)
                n == "nuki" ->
                    Hint("lock", "一个 Nuki 锁或开门器", "匹配到特征 $raw。", 7)
                n == "salto" ->
                    Hint("access", "一个 SALTO 门禁读卡器或锁", "匹配到特征 $raw。", 7)
                n == "dormakaba" ->
                    Hint("access", "一个 dormakaba、Saflok 或 Oracode 锁", "匹配到特征 $raw。", 7)
                n == "lockly" ->
                    Hint("lock", "一个 Lockly 智能锁", "匹配到特征 $raw。", 6)
                n == "kevo" ->
                    Hint("lock", "一个 Kwikset Kevo 或 Unikey 锁", "匹配到特征 $raw。", 7)
                n == "master lock" ->
                    Hint("lock", "一把 Master Lock 挂锁", "匹配到特征 $raw。", 7)
                n == "igloohome" ->
                    Hint("lock", "an igloohome lock or keybox", "匹配到特征 $raw。", 7)
                n == "tedee" ->
                    Hint("lock", "一个 Tedee 智能锁", "匹配到特征 $raw。", 7)
                n == "paxton" ->
                    Hint("access", "一个 Paxton 读卡器或 Net2 接入点", "匹配到特征 $raw。", 7)
                n == "kwikset" ->
                    Hint("lock", "一个 Kwikset 锁", "匹配到特征 $raw。", 6)
                n == "myq" ->
                    Hint("garage", "一个 Chamberlain myQ 车库中枢", "匹配到特征 $raw。", 7)
                n == "chevrolet hotspot" ->
                    Hint("hotspot", "一个 Chevrolet 车载 Wi-Fi 热点", "匹配到特征 $raw。", 7)
                n == "rivian" ->
                    Hint("vehicle", "一辆 Rivian 车辆、手机钥匙或传感器", "匹配到特征 $raw。", 7)
                n == "ford" ->
                    Hint("vehicle", "一辆 Ford 或 Lincoln 车辆或手机钥匙", "匹配到特征 $raw。", 7)
                n == "honda" ->
                    Hint("vehicle", "一辆 Honda 或 Acura 车辆或手机钥匙", "匹配到特征 $raw。", 7)
                n == "hyundai" ->
                    Hint("vehicle", "一辆 Hyundai 或 Genesis 车辆或手机钥匙", "匹配到特征 $raw。", 7)
                n == "toyota" ->
                    Hint("vehicle", "一辆 Toyota 或 Lexus 车辆或手机钥匙", "匹配到特征 $raw。", 7)
                n == "nissan" ->
                    Hint("vehicle", "一辆 Nissan 或 Infiniti 车辆或手机钥匙", "匹配到特征 $raw。", 7)
                n == "subaru" ->
                    Hint("vehicle", "一辆 Subaru 车辆或手机钥匙", "匹配到特征 $raw。", 7)
                n == "bmw" ->
                    Hint("vehicle", "一辆 BMW 车辆、手机钥匙或原厂热点", "匹配到特征 $raw。", 7)
                n == "volkswagen" ->
                    Hint("vehicle", "一辆 Volkswagen 车辆或手机钥匙", "匹配到特征 $raw。", 7)
                n == "porsche" ->
                    Hint("vehicle", "一辆 Porsche 车辆或手机钥匙", "匹配到特征 $raw。", 7)
                n == "jaguar land rover" ->
                    Hint("vehicle", "一辆 Jaguar、Land Rover 或 Range Rover", "匹配到特征 $raw。", 7)
                n == "byd" ->
                    Hint("vehicle", "一辆 BYD 车辆或手机钥匙", "匹配到特征 $raw。", 7)
                n == "govee" ->
                    Hint("light", "一个 Govee 灯或传感器", "匹配到特征 $raw。", 6)
                n == "hp" ->
                    Hint("printer", "一台 HP 打印机", "匹配到特征 $raw。", 6)
                n == "epson" ->
                    Hint("printer", "一台 Epson EcoTank 或 WorkForce 打印机", "匹配到特征 $raw。", 6)
                n == "lg webos tv" ->
                    Hint("tv", "一台 LG webOS 电视", "匹配到特征 $raw。", 7)
                n == "roku" ->
                    Hint("tv", "一个 Roku 流媒体棒或 Roku 电视（常为隐藏的 Wi-Fi Direct 遥控接入点）", "匹配到特征 $raw。", 7)
                n == "samsung appliance" ->
                    Hint("iot", "一台 Samsung 冰箱、灶具、烤箱或电磁炉（设置用 AP）", "匹配到特征 $raw。", 6)
                n == "ecowater" ->
                    Hint("iot", "一台 EcoWater 软水机（设置用 AP）", "匹配到特征 $raw。", 6)
                n == "nespresso" ->
                    Hint("iot", "一台 Nespresso 咖啡机", "匹配到特征 $raw。", 7)
                n == "radiacode" ->
                    Hint("sensor", "一个 RadiaCode 辐射探测器", "匹配到特征 $raw。", 7)
                n == "shokz" ->
                    Hint("audio-personal", "Shokz OpenRun 或 OpenFit 耳机", "匹配到特征 $raw。", 7)
                n == "mercedes mbux" ->
                    Hint("hotspot", "一个 Mercedes MBUX 车载热点", "匹配到特征 $raw。", 7)
                n == "motive" ->
                    Hint("hotspot", "一个 Motive / KeepTruckin 车队 ELD 热点", "匹配到特征 $raw。", 6)
                n == "peoplenet" ->
                    Hint("hotspot", "一个 PeopleNet 车队 ELD 热点", "匹配到特征 $raw。", 6)
                n == "uconnect" ->
                    Hint("hotspot", "一个 Uconnect 车载热点", "匹配到特征 $raw。", 6)
                n == "carplay" ->
                    Hint("hotspot", "一个 CarPlay 车载热点", "匹配到特征 $raw。", 6)
                n == "cradlepoint" ->
                    Hint("hotspot", "一个 Cradlepoint 车载路由器（常用于公共安全/车队）", "匹配到特征 $raw。", 7)
                n == "airlink" ->
                    Hint("hotspot", "一个 Sierra Wireless AirLink 车载网关", "匹配到特征 $raw。", 7)
                n == "compex" ->
                    Hint("hotspot", "一个 Compex 接入点（有时用于公共安全/车队）", "匹配到特征 $raw。", 6)
                n == "novatel wireless" ->
                    Hint("hotspot", "一个 Novatel Wireless / Inseego 车载无线设备", "匹配到特征 $raw。", 6)
                n == "utility inc" ->
                    Hint("hotspot", "一个 Utility, Inc 车辆或公共安全无线设备", "匹配到特征 $raw。", 6)
                "gl.inet" in n || n == "glinet" ->
                    Hint("ap", "一个 GL.iNet 便携路由器", "匹配到特征 $raw。", 6)
                "smarttag" in n ->
                    Hint("tag", "一个 Samsung SmartTag", "匹配到特征 $raw。", 8)
                "tile" in n ->
                    Hint("tag", "一个 Tile 追踪器", "匹配到特征 $raw。", 8)
                n == "ibeacon" ->
                    Hint("beacon", "一个 iBeacon", "匹配到特征 $raw。", 7)
                "atrius" in n ->
                    Hint("beacon", "Atrius 购物车标签", "匹配到特征 $raw。", 8)
                n == "minew" ->
                    Hint("beacon", "一个 Minew BLE 信标或传感器", "匹配到特征 $raw。", 7)
                n == "estimote" ->
                    Hint("beacon", "一个 Estimote 信标", "匹配到特征 $raw。", 7)
                n == "kontakt.io" || n == "kontakt" ->
                    Hint("beacon", "一个 Kontakt.io 信标", "匹配到特征 $raw。", 7)
                "bluetoad" in n ->
                    Hint(
                        "roadside",
                        "一个 Iteris BlueTOAD / Vantage Velocity 路侧蓝牙行程时间读取器",
                        "匹配到特征 $raw。",
                        7,
                    )
                "bliptrack" in n ->
                    Hint(
                        "roadside",
                        "一个 BLIP Systems BlipTrack 路侧行程时间传感器",
                        "匹配到特征 $raw。",
                        7,
                    )
                "raven" in n || "shotspotter" in n || "soundthinking" in n ->
                    Hint(
                        "acoustic",
                        "一个 Flock Raven 或 ShotSpotter 声学枪声传感器",
                        "匹配到特征 $raw。",
                        8,
                    )
                "digital ally" in n ->
                    Hint("camera", "一台 Digital Ally 随身或车载摄像头", "匹配到特征 $raw。", 8)
                "reveal media" in n || "bodyworn" in n ->
                    Hint("camera", "一台 Reveal Media 随身摄像头", "匹配到特征 $raw。", 8)
                n == "wolfcom" ->
                    Hint("camera", "一台 Wolfcom 随身或车载摄像头", "匹配到特征 $raw。", 8)
                "i-pro" in n || "arbitrator" in n ->
                    Hint("camera", "一台 Panasonic i-PRO 摄像头或 Arbitrator 车载系统", "匹配到特征 $raw。", 8)
                "limitless" in n ->
                    Hint("wearable", "一个 Limitless Pendant 对话录音器", "匹配到特征 $raw。", 8)
                n == "bee pendant" || "bee pioneer" in n ->
                    Hint("wearable", "一个 Bee Pioneer 可穿戴录音器", "匹配到特征 $raw。", 8)
                n == "omi" || "openglass" in n ->
                    Hint("wearable", "一个 Omi 吊坠或 OpenGlass 摄像眼镜", "匹配到特征 $raw。", 8)
                "friend pendant" in n ->
                    Hint("wearable", "一条 Friend Pendant 项链", "匹配到特征 $raw。", 8)
                "brilliant frame" in n ->
                    Hint("glasses", "Brilliant Labs Frame AR 眼镜", "匹配到特征 $raw。", 8)
                n == "even g1" ->
                    Hint("glasses", "Even Realities G1 眼镜", "匹配到特征 $raw。", 8)
                "hayden" in n ->
                    Hint("camera", "一台 Hayden AI 公交车或车载摄像头", "匹配到特征 $raw。", 8)
                "miovision" in n ->
                    Hint("camera", "一台 Miovision 路口交通摄像头", "匹配到特征 $raw。", 8)
                n == "tattile" ->
                    Hint("camera", "一个 Tattile 车牌读取器", "匹配到特征 $raw。", 8)
                "lvt" in n || "liveview" in n ->
                    Hint("camera", "一辆 LVT / LiveView 太阳能监控拖车", "匹配到特征 $raw。", 8)
                "hanwha" in n || "wisenet" in n ->
                    Hint("camera", "一台 Hanwha Vision / Wisenet 摄像头", "匹配到特征 $raw。", 7)
                n == "uniview" ->
                    Hint("camera", "一台 Uniview / UNV 摄像头", "匹配到特征 $raw。", 7)
                n == "rhombus" ->
                    Hint("camera", "一台 Rhombus 云摄像头", "匹配到特征 $raw。", 7)
                n == "meshcore" ->
                    Hint("mesh", "一个 MeshCore LoRa 伴随无线设备", "匹配到特征 $raw。", 7)
                "gotenna" in n ->
                    Hint("mesh", "一个 goTenna Mesh 或 Pro 无线设备", "匹配到特征 $raw。", 7)
                n == "sensecap" ->
                    Hint("mesh", "一个 SenseCAP LoRaWAN / Helium 网关", "匹配到特征 $raw。", 7)
                "wisgate" in n || n == "rak wisgate" ->
                    Hint("mesh", "一个 RAK WisGate LoRaWAN 网关", "匹配到特征 $raw。", 7)
                n == "ghostesp" ->
                    Hint("pentest", "一个 GhostESP ESP32 审计板", "匹配到特征 $raw。", 7)
                n == "bruce" ->
                    Hint("pentest", "一个 Bruce ESP32 渗透测试板", "匹配到特征 $raw。", 7)
                n == "liteon camera radio" ->
                    Hint(
                        "module",
                        "一个摄像头模块无线设备（LiteOn 或类似）",
                        "匹配到特征 $raw。",
                        3,
                    )
                "chipolo" in n || "pebblebee" in n || "moto tag" in n ->
                    Hint("tag", "a finder tag", "匹配到特征 $raw。", 7)
                "airpods" in n ->
                    Hint("audio-personal", "AirPods", "匹配到特征 $raw。", 8)
                else -> Hint("named", raw, "匹配到特征 $raw。", 7)
            }
        }
    }

    private fun isGenericSignatureName(name: String): Boolean {
        val n = name.trim()
        return n.equals("Unknown Signature", ignoreCase = true) ||
            n.equals("Unknown Fleet", ignoreCase = true)
    }

    private fun wifiHints(device: Sighting, signatureNames: List<String>): List<Hint> {
        val name = device.name
        val caps = (device.facts.capabilities ?: "").uppercase()
        val specific = signatureNames.any { !isGenericSignatureName(it) }
        val out = ArrayList<Hint>(2)
        when {
            name.startsWith("DIRECT-", true) ->
                out += if (specific) {
                    Hint("wifi-direct", "一个 Wi-Fi Direct 接入点", "SSID 以 DIRECT- 开头。", 4)
                } else {
                    Hint("wifi-direct", "一台使用 Wi-Fi Direct 的手机或电视", "SSID 以 DIRECT- 开头。", 6)
                }
            name.startsWith("ANDROID-", true) || name.contains("hotspot", true) ->
                if (!specific) {
                    out += Hint("hotspot", "a phone hotspot", "SSID 看起来像手机热点。", 6)
                }
            "MESH" in caps ->
                out += Hint("mesh", "一个 Mesh Wi-Fi 节点", "能力列表包含 mesh。", 5)
            device.hiddenSsid ->
                out += Hint("ap", "一个隐藏的 Wi-Fi 接入点", "SSID 已隐藏；该无线设备仍在发送信标。", 4)
            else ->
                if (!specific) {
                    out += Hint("ap", "一个 Wi-Fi 接入点", "原生 Android 仅报告正在发送信标的 AP。", 3)
                }
        }
        return out
    }

    private fun uuid16(uuid: String): Int? {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return when {
            hex.length == 4 -> hex.toIntOrNull(16)
            hex.length == 32 && hex.startsWith("0000") && hex.endsWith("00001000800000805F9B34FB") ->
                hex.substring(4, 8).toIntOrNull(16)
            hex.length == 8 -> hex.takeLast(4).toIntOrNull(16)
            else -> null
        }
    }
}
