package app.fieldwatch.domain

/** Operator-facing disclaimer. First-run, Debrief, and AI Export share the same core. */
object FieldwatchDisclaimer {
    const val HOBBY =
        "这是一个业余项目，按 MIT 许可证“原样”提供。使用风险自负。"

    const val HYPOTHESES =
        "Detections, pattern matches, “Moving with you” / “possible tail,” Debrief text, " +
            "and AI Export are hypotheses — not identity, not a legal finding, and not a " +
            "complete RF capture. Radios that are off, asleep, randomized, cellular-only, " +
            "or hidden by the OS will not appear."

    const val LIABILITY =
        "You are solely responsible for how you use this app and for following local law. " +
            "To the maximum extent permitted by law, Off Grid Pete LLC is not liable for " +
            "indirect, incidental, special, consequential, or punitive damages arising from its use."

    const val LOCATION =
        "GPS stamps are this phone at hear-time, not the other radio, unless a decode map " +
            "advertises its own latitude/longitude (Remote ID Location). Sharing a Debrief, " +
            "sit compare, AI Export (sit or one radio), radio-detail Share as text, or log can take that " +
            "path off the device. A TAK/CoT feed, when you turn it on, sends markers onto " +
            "the LAN; that is on the operator."

    const val ACCEPT =
        "勾选此框并继续，即表示你接受这些条款和 MIT 许可证。"

    const val LICENSE_TITLE = "MIT 许可证"

    /** Body of LICENSE in the repository, without the title line. */
    const val LICENSE_BODY =
        "Copyright (c) 2026 Off Grid Pete LLC\n" +
            "\n" +
            "Permission is hereby granted, free of charge, to any person obtaining a copy " +
            "of this software and associated documentation files (the \"Software\"), to deal " +
            "in the Software without restriction, including without limitation the rights " +
            "to use, copy, modify, merge, publish, distribute, sublicense, and/or sell " +
            "copies of the Software, and to permit persons to whom the Software is " +
            "furnished to do so, subject to the following conditions:\n" +
            "\n" +
            "The above copyright notice and this permission notice shall be included in all " +
            "copies or substantial portions of the Software.\n" +
            "\n" +
            "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR " +
            "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, " +
            "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE " +
            "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER " +
            "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, " +
            "OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE " +
            "SOFTWARE."

    val LICENSE_TEXT = "$LICENSE_TITLE\n\n$LICENSE_BODY"

    val firstRunDisclaimer: String = "$HOBBY\n\n$HYPOTHESES\n\n$LIABILITY"

    val firstRun: String =
        "$firstRunDisclaimer\n\n$LICENSE_TEXT\n\n$ACCEPT"

    /** Debrief text / PDF. Same core as first-run, without the click-through line. */
    fun report(window: DebriefWindow? = null): String {
        val source = if (window?.sitName != null) {
            "已命名监测“${window.sitName}”（该窗口内侦听到的设备；观察时仍应用实时列表上限）。"
        } else {
            "内存中的实时集合（最近 15 分钟，上限约 400）。"
        }
        return "$HOBBY\n\n$HYPOTHESES\n\n$LIABILITY\n\n$LOCATION\n\n" +
            "本监测报告来自 $source" +
            "切勿在任何涉及安全的情形中使用它。"
    }

    fun compare(): String =
        "$HOBBY\n\n$HYPOTHESES\n\n$LIABILITY\n\n$LOCATION\n\n" +
            "本对比是本手机侦听到的两个设备窗口（类型 + MAC）。" +
            "BLE 轮换会作为新行。切勿在任何涉及安全的情形中使用它。"

    fun experimentalMarkdown(): String = buildString {
        appendLine("## 免责声明（在你的回答中重复此内容）")
        appendLine(HOBBY)
        appendLine(HYPOTHESES)
        appendLine(LIABILITY)
        appendLine(LOCATION)
        appendLine("切勿在任何涉及安全的情形中使用 Fieldwatch、此粘贴内容或你的分析。")
        appendLine("以本免责声明作为回复的开头。不要提供安全建议。")
    }
}
