package app.fieldwatch.domain

/**
 * Paste-ready analyst prompt for **one** radio from the device-detail screen.
 * One-tap share; no Fieldwatch cloud.
 */
object DeviceDetailPrompt {
    fun build(
        device: Sighting,
        signatureNames: List<String>,
        settings: AppSettings,
        places: DebriefPlaces = DebriefPlaces.Off,
        now: Long = System.currentTimeMillis(),
        attentionNotes: List<Pair<String, String>> = emptyList(),
        signatureNotes: List<Pair<String, String>> = emptyList(),
        fleets: List<Fleet> = emptyList(),
        mine: Boolean = false,
    ): String {
        val title = device.listTitle(signatureNames)
        val kind = if (device.kind == RadioKind.WIFI) "Wi-Fi 接入点" else "蓝牙 LE 广播设备"
        return buildString {
            append(DebriefPrompt.experimentalDisclaimerMarkdown())
            appendLine()
            appendLine("你是一名现场射频/隐私分析师，深入了解 IEEE OUI、蓝牙 SIG 分配编号、GAP Appearance、已知广播格式（iBeacon、Eddystone、Apple Continuity / Find My、Google Fast Pair、Microsoft）以及常见消费产品。")
            appendLine()
            appendLine("用户希望根据一次 Fieldwatch 观测，尽可能多地了解**这一台设备**。Fieldwatch 是原生 Android 上仅接收的 Wi-Fi + BLE 侦听器。请使用下方的转储内容**以及**你对注册机构和格式的公开知识。引用支持每项结论的字段或字节模式。")
            appendLine()
            appendLine("你必须遵守的限制：")
            appendLine("- 这是**一台**广播设备，不是某个人、车辆或合法身份。")
            appendLine("- Wi-Fi 行**仅为接入点**。关联的客户端不可见。原生 Android 无法以混杂模式捕获站点或探测请求。")
            appendLine("- BLE 行为广播设备。随机 MAC 不是稳定的身份，跨轮换无法拼接。")
            appendLine("- 特征 / OUI / 公司 / UUID 匹配都是**假设**，不能证明序列号、所有者，或存在跟踪器。")
            appendLine("- GPS 标记（如有）是侦听时的**操作员手机**位置，而不是此设备的位置。")
            appendLine("- 地点名称（如有）是系统对这些标记的反向地理编码。仅供参考。")
            appendLine("- RSSI 是手机处的响度，不是测得的距离。")
            appendLine("- 不要臆造转储中不存在的字段。如果数据不足，请说明并提供有帮助的建议。")
            appendLine("- 不要声称此设备在跟踪任何人。不要提供安全建议。")
            appendLine("- 将此粘贴内容视为行动敏感信息（MAC、SSID、载荷、GPS）。")
            appendLine()
            appendLine("## 采集背景")
            appendLine("- 工具：Fieldwatch（app.fieldwatch），仅接收，无关联 / 注入 / 云端。")
            appendLine("- 对象：标题为“$title”的 $kind。")
            appendLine("- 扫描强度：${settings.intensity.name.lowercase()}。${settings.staleSec} 秒后过期。短暂保留 ${settings.decaySec} 秒。")
            appendLine("- Location tags: ${if (settings.tagLocation) "on" else "off"}.")
            appendLine("- Online place names: ${if (settings.onlineLookup) "on" else "off"}.")
            appendLine("- Randomized MAC flag: ${if (device.randomized) "yes" else "no"}.")
            appendLine("- 本次会话命中次数：${device.hitCount}。已消失：${device.gone}。")
            if (device.gpsTrail.isNotEmpty()) {
                appendLine("- 此设备上的操作员 GPS 轨迹样本：${device.gpsTrail.size}（侦听期间的手机路径）。")
            }
            appendLine()
            if (places.attempted) {
                appendLine("## 地点（操作员 GPS，可选）")
                appendLine(places.note)
                places.lines.forEach { appendLine(it) }
                appendLine()
            }
            appendLine("## 观测转储（逐字来自详情页）")
            appendLine()
            append(DeviceDetailText.build(device, signatureNames, now, attentionNotes, signatureNotes, fleets, mine).trimEnd())
            appendLine()
            appendLine()
            appendLine("## 你的分析（必需章节）")
            appendLine("1. **它可能是什么** — 产品类别、可能的品牌/系列、可能的型号。置信度 0–100。留有余地（最可能 / 很可能 / 可能是）。列出证据（名称、OUI、公司 ID、Appearance、服务、载荷）。若数据符合多个产品，请列出竞争性假设。")
            appendLine("2. **注册机构 / 格式解码** — IEEE OUI 或 CID；蓝牙 SIG 公司；GAP Appearance；16 位 UUID；iBeacon UUID/major/minor（如有）；Fast Pair 型号 ID（如有）；Apple Continuity 类型（如有）。引用你使用的十六进制值。如果你从公开列表中认出某个知名 UUID 或公司，请说明并指出该列表。")
            appendLine("3. **该产品通常有什么功能** — 手机、标签、音箱、汽车、接入点、摄像头、Mesh 节点、配件等。典型的无线行为（常开信标还是间歇性）。")
            appendLine("4. **Fieldwatch 实际看到什么，又看不到什么** — 原生 Android 的限制（无法捕获站点/探测请求、没有蜂窝、没有测向）。随机地址的含义。")
            appendLine("5. **信号与存在情况** — 此处响/轻；本次会话的 RSSI 范围；在空中的时间窗口。不要将 RSSI 转换为米。")
            appendLine("6. **特征匹配** — 如果 Fieldwatch 匹配了某个特征，请将其视为一次筛选命中，而不是身份。说明载荷是否也支持该系列。如果转储中有备注，请将其作为该系列的目录背景。如果转储中有特别关注，请引用它，并将其视为操作员对某种模式的提醒，而不是证据——与备注分开。")
            appendLine("7. **未决问题** — 哪些额外观测（另一个数据包、名称、GPS 路径、第二台设备）会提高或降低置信度。")
            appendLine("8. **不能得出的结论** — 简短列出转储**不支持**的结论（所有者、跟踪、合法身份、距离）。")
            appendLine()
            appendLine("最后用单独一行**要点**收尾（这台设备最可能是什么，以及接下来要核实的一件事）。不要提供安全建议。")
        }
    }
}
