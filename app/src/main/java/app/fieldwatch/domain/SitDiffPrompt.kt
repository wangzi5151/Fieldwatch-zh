package app.fieldwatch.domain

/**
 * Paste-ready addendum prompt for Reports → Compare sits → AI Export.
 * Onboard compare is verbatim. Working data is overlap + exclusive Extra attention /
 * Named radios — not a second inventory.
 */
object SitDiffPrompt {
    private const val MAX_CHARS = 90_000

    fun build(
        thisSit: SitDiff.Side,
        second: SitDiff.Side,
        demoMode: Boolean,
    ): String {
        val thisSit = thisSit.withoutFloodRadios()
        val second = second.withoutFloodRadios()
        val macs = (thisSit.radios + second.radios).map { it.mac }
        val onboard = SitDiff.document(thisSit, second).withDemoMacs(macs, demoMode)
        val thisKeys = thisSit.keys
        val secondKeys = second.keys
        val byKey = (thisSit.radios + second.radios).associateBy { it.key }
        val onlyThis = thisKeys.minus(secondKeys)
        val onlySecond = secondKeys.minus(thisKeys)
        val both = thisKeys.intersect(secondKeys)
        val union = thisKeys.union(secondKeys)
        val overlapPct = if (union.isEmpty()) 0 else (both.size * 100) / union.size
        fun bucket(keys: Set<String>): Triple<Int, Int, Int> {
            val rows = keys.mapNotNull { byKey[it] }
            val wifi = rows.count { it.kind == RadioKind.WIFI }
            val ble = rows.count { it.kind == RadioKind.BLE }
            val randBle = rows.count { it.kind == RadioKind.BLE && it.randomized }
            return Triple(wifi, ble, randBle)
        }
        val onlyThisB = bucket(onlyThis)
        val onlySecondB = bucket(onlySecond)
        val bothB = bucket(both)
        fun line(row: SitDiff.Radio): String = buildString {
            append(if (row.kind == RadioKind.WIFI) "WIFI" else "BLE")
            append("  ").append(row.mac)
            val label = row.name.trim()
            if (label.isNotEmpty() && !label.equals(row.mac, ignoreCase = true)) {
                append("  ").append(label)
            }
            if (row.mine) append("  标记为我的")
            row.fleetNames.filter { it.isNotBlank() }.forEach { append("  ").append(it) }
            if (row.extraAttention) append("  特别关注")
            val labels = row.liveDecode.reportLabels()
            if (labels.isNotEmpty()) append("  ").append(labels.joinToString(", "))
            if (row.kind == RadioKind.BLE && row.randomized) append("  随机")
        }
        fun exclusive(keys: Set<String>, where: String, pred: (SitDiff.Radio) -> Boolean) =
            keys.mapNotNull { byKey[it] }.filter(pred).map { "$where  ${line(it)}" }

        val extraRows =
            exclusive(onlyThis, "仅在此监测中", { it.extraAttention }) +
                exclusive(onlySecond, "仅在第二个监测中", { it.extraAttention })
        val namedRows =
            exclusive(onlyThis, "仅在此监测中", { it.named }) +
                exclusive(onlySecond, "仅在第二个监测中", { it.named })

        val body = buildString {
            append(FieldwatchDisclaimer.experimentalMarkdown())
            appendLine()
            appendLine("你是一名现场射频分析师，为对比了两次 Fieldwatch 监测的操作员提供分析。Fieldwatch 是原生 Android 上仅接收的 Wi-Fi 接入点 + BLE 广播设备侦听器。")
            appendLine()
            appendLine("**板载对比**（见下方逐字内容）已经按存在情况分类：仅在此监测、仅在第二个监测、两者皆有。**不要重写那份报告。不要重复打印那些列表。** 你的任务是手机无法生成的补充：这是哪种变化，以及其中有多少是真实的。")
            appendLine()
            appendLine("你必须遵守的限制：")
            appendLine("- 仅侦听。类型 + MAC。BLE 轮换算作新行，无法拼接。")
            appendLine("- Wi-Fi 行仅为接入点。关联的客户端不可见。")
            appendLine("- 特别关注 / 特征匹配是假设，不是身份，也不是某个人或车辆。")
            appendLine("- GPS 标记（如有）是侦听时本机的位置，而不是另一台设备。")
            appendLine("- 最近 15 分钟与已命名监测不是同一范围（内存约 400 对比监测 ${Sit.RADIO_CAP}）。内存一侧缺少的 BLE 可能是被淘汰，而不是消失。")
            appendLine("- 同时出现不等于同行。不要臆造尾随、跟踪者或摄像头位置。")
            appendLine("- 标记为我的设备是操作员自有的。不要将其视为无法解释的跟踪者。")
            appendLine("- 洪泛说明是新地址的突发，而不是跟踪者。")
            appendLine("- 某行上解码出的实时值是该类型 + MAC 的目录文本。如果板载对比指出该值发生变化，请说明该变化。不要将该值拼接到不同的 MAC 上。")
            appendLine("- 航空器区块是该设备广播的位置，按 UAS ID 归并。如果板载对比指出状态发生变化，请说明该变化。该轨迹不是本机的 GPS。")
            appendLine("- 不要提供安全建议。不要告诉操作员他们安全或处于危险中。")
            appendLine("- 将此粘贴内容视为行动敏感信息。")
            appendLine()
            appendLine("## 你的输出（必需 — 这是操作员要阅读的补充内容）")
            appendLine("请写完整的句子。标题如下。仅对独有的特别关注 / 已命名设备使用简短项目符号。不要使用 Markdown 表格。不要使用代码围栏。不要转储板载列表。")
            appendLine()
            appendLine("1. **免责声明** — 先重复实验性使用免责声明。")
            appendLine("2. **板载对比已经确立了什么** — 3–5 句话。窗口名称、计数、独有的特别关注、观察者备注（如有）、标记为我的（如有）、洪泛（如有）。不要重复打印清单。")
            appendLine("3. **这些数字补充了什么** — 重叠度（两者皆有/并集，以百分比表示）、各分组中的 Wi-Fi 与 BLE、独有的 BLE 中有多少是随机地址。说明这看起来像固定设施、不同的摊位/时段，还是上限造成的假象。给出置信度。使用工作表；不要臆造比率。")
            appendLine("4. **独有的特别关注和已命名设备** — 来自工作表的完整标识（完整 MAC、名称、特征、所属窗口）。模式匹配，不是身份。如果没有，请说明没有。")
            appendLine("5. **再做一次监测或追踪能缩小什么范围** — 仅给出应用内具体的后续步骤（在同一摊位做第三次监测、对某条独有的特别关注记录使用追踪、筛选）。不要提供安全建议。不要说“报警”。")
            appendLine()
            appendLine("**要点（必需，最后一行）。** 以 `要点：` 开头的一句话，补充*板载要点尚未提及的一个数字*（重叠百分比、独有的特别关注数量，或独有 BLE 中随机地址的比例）。不是说教。不是威胁等级。")
            appendLine()
            appendLine("## 板载对比（逐字 — 已展示给操作员；不要重写）")
            appendLine()
            appendLine(onboard.toPlainText().trimEnd())
            appendLine()
            appendLine("## 工作数据（供补充使用 — 不要将清单复制到答案中）")
            appendLine()
            appendLine("This sit: ${thisSit.name} (${thisSit.radios.size} radios${if (thisSit.ram) ", RAM ~400" else ", named sit up to ${Sit.RADIO_CAP}"})")
            appendLine("Second sit: ${second.name} (${second.radios.size} radios${if (second.ram) ", RAM ~400" else ", named sit up to ${Sit.RADIO_CAP}"})")
            appendLine("仅在此监测：${onlyThis.size}  仅在第二个：${onlySecond.size}  两者皆有：${both.size}  并集：${union.size}  重叠：$overlapPct%")
            appendLine("仅在此监测按设备：Wi-Fi ${onlyThisB.first}  BLE ${onlyThisB.second}  随机 BLE ${onlyThisB.third}")
            appendLine("仅在第二个监测按设备：Wi-Fi ${onlySecondB.first}  BLE ${onlySecondB.second}  随机 BLE ${onlySecondB.third}")
            appendLine("两者皆有按设备：Wi-Fi ${bothB.first}  BLE ${bothB.second}  随机 BLE ${bothB.third}")
            if (thisSit.ram || second.ram) {
                appendLine("上限说明：最近 15 分钟是实时内存（约 400）。已命名监测最多保留 ${Sit.RADIO_CAP}。两者的计数不是同一范围。")
            }
            appendLine()
            appendLine("独有的特别关注：")
            if (extraRows.isEmpty()) appendLine("- 无。")
            else extraRows.forEach { appendLine("- $it") }
            appendLine()
            appendLine("独有的已命名设备：")
            if (namedRows.isEmpty()) appendLine("- 无。")
            else namedRows.forEach { appendLine("- $it") }
            appendLine()
            appendLine("观察者备注：")
            val observed = (thisSit.radios + second.radios)
                .distinctBy { it.key }
                .mapNotNull { r ->
                    val note = r.observerNotes.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    r to note
                }
            if (observed.isEmpty()) appendLine("- 无。")
            else observed.forEach { (r, note) ->
                val where = when {
                    r.key in onlyThis -> "仅在此监测中"
                    r.key in onlySecond -> "仅在第二个监测中"
                    else -> "两者皆有"
                }
                appendLine("- $where  ${line(r)}")
                appendLine("  $note")
            }
            appendLine()
            appendLine("## 工作数据结束")
            appendLine("现在撰写补充内容，遵循顶部的**你的输出**。不要重写板载对比。")
        }
        val masked = MacUtil.redactMacsIn(body, macs, demoMode)
        val withPrivacy = if (demoMode) {
            "隐私模式：MAC 末段为 **:**:**。GPS 坐标已遮蔽。手机上的日志未更改。\n\n$masked"
        } else {
            masked
        }
        return if (withPrivacy.length <= MAX_CHARS) withPrivacy
        else withPrivacy.take(MAX_CHARS) + "\n\n[为适应分享面板大小已截断]\n"
    }
}
