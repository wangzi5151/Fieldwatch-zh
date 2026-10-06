package app.fieldwatch.domain

/** Bluetooth Class of Device (Assigned Numbers). 24-bit CoD. */
object CodDecoder {
    data class Decoded(
        val major: String,
        val minor: String,
        val services: List<String>,
        val raw: Int,
    ) {
        fun summary(): String = buildString {
            append(major)
            if (minor.isNotBlank() && minor != "未分类") {
                append(" / ")
                append(minor)
            }
            if (services.isNotEmpty()) {
                append(" · ")
                append(services.joinToString(", "))
            }
        }
    }

    fun decode(cod: Int): Decoded {
        val format = cod and 0x3
        val minorBits = (cod shr 2) and 0x3F
        val majorBits = (cod shr 8) and 0x1F
        val serviceBits = (cod shr 13) and 0x7FF
        val major = majorName(majorBits)
        val minor = if (format != 0) "format $format" else minorName(majorBits, minorBits)
        return Decoded(major, minor, serviceNames(serviceBits), cod and 0xFFFFFF)
    }

    fun decodeOrNull(cod: Int?): Decoded? {
        if (cod == null || cod == 0) return null
        return decode(cod)
    }

    private fun majorName(major: Int): String = when (major) {
        0x00 -> "其他"
        0x01 -> "计算机"
        0x02 -> "电话"
        0x03 -> "LAN / 网络接入点"
        0x04 -> "音频/视频"
        0x05 -> "外设"
        0x06 -> "成像"
        0x07 -> "可穿戴设备"
        0x08 -> "玩具"
        0x09 -> "健康"
        0x1F -> "未分类"
        else -> "主类别 0x%02X".format(major)
    }

    private fun minorName(major: Int, minor: Int): String = when (major) {
        0x01 -> when (minor) {
            0x00 -> "未分类"
            0x01 -> "台式机"
            0x02 -> "服务器"
            0x03 -> "笔记本电脑"
            0x04 -> "掌上电脑/PDA"
            0x05 -> "掌上型 PDA"
            0x06 -> "可穿戴计算机"
            0x07 -> "平板电脑"
            else -> "计算机 0x%02X".format(minor)
        }
        0x02 -> when (minor) {
            0x00 -> "未分类"
            0x01 -> "蜂窝"
            0x02 -> "无绳"
            0x03 -> "智能手机"
            0x04 -> "有线调制解调器/语音网关"
            0x05 -> "公共 ISDN 接入"
            else -> "电话 0x%02X".format(minor)
        }
        0x03 -> when ((minor shr 3) and 0x7) {
            0 -> "完全可用"
            1 -> "已使用 1–17%"
            2 -> "已使用 17–33%"
            3 -> "已使用 33–50%"
            4 -> "已使用 50–67%"
            5 -> "已使用 67–83%"
            6 -> "已使用 83–99%"
            else -> "无可用服务"
        }
        0x04 -> when (minor) {
            0x00 -> "未分类"
            0x01 -> "可穿戴耳机"
            0x02 -> "免提"
            0x04 -> "麦克风"
            0x05 -> "扬声器"
            0x06 -> "头戴式耳机"
            0x07 -> "便携音频"
            0x08 -> "车载音频"
            0x09 -> "机顶盒"
            0x0A -> "高保真音频"
            0x0B -> "录像机"
            0x0C -> "摄像机"
            0x0D -> "便携摄像机"
            0x0E -> "视频监视器"
            0x0F -> "视频显示器和扬声器"
            0x10 -> "视频会议"
            0x12 -> "游戏/玩具"
            else -> "音视频 0x%02X".format(minor)
        }
        0x05 -> {
            val sense = minor and 0x0F
            val hid = (minor shr 4) and 0x3
            val kind = when (sense) {
                0x00 -> "未分类"
                0x01 -> "操纵杆"
                0x02 -> "游戏手柄"
                0x03 -> "遥控器"
                0x04 -> "传感设备"
                0x05 -> "数字化仪平板"
                0x06 -> "读卡器"
                0x07 -> "数字笔"
                0x08 -> "手持扫描仪"
                0x09 -> "手持手势输入"
                else -> "外设 0x%X".format(sense)
            }
            val extra = when (hid) {
                1 -> "keyboard"
                2 -> "pointing"
                3 -> "keyboard/pointing"
                else -> null
            }
            if (extra == null) kind else if (sense == 0) extra.replaceFirstChar { it.uppercase() } else "$kind + $extra"
        }
        0x06 -> buildList {
            if (minor and 0x08 != 0) add("显示")
            if (minor and 0x04 != 0) add("摄像头")
            if (minor and 0x02 != 0) add("扫描仪")
            if (minor and 0x01 != 0) add("打印机")
        }.joinToString(" + ").ifBlank { "未分类" }
        0x07 -> when (minor) {
            0x01 -> "手表"
            0x02 -> "寻呼机"
            0x03 -> "夹克"
            0x04 -> "头盔"
            0x05 -> "眼镜"
            else -> "可穿戴设备 0x%02X".format(minor)
        }
        0x08 -> when (minor) {
            0x01 -> "机器人"
            0x02 -> "车辆"
            0x03 -> "玩偶/可动人偶"
            0x04 -> "控制器"
            0x05 -> "游戏"
            else -> "玩具 0x%02X".format(minor)
        }
        0x09 -> when (minor) {
            0x01 -> "血压计"
            0x02 -> "体温计"
            0x03 -> "体重秤"
            0x04 -> "血糖仪"
            0x05 -> "脉搏血氧仪"
            0x06 -> "心率/脉搏监测器"
            0x07 -> "健康数据显示器"
            0x08 -> "计步器"
            0x09 -> "体成分分析仪"
            0x0A -> "峰值流量计"
            0x0B -> "用药监测器"
            0x0C -> "膝关节假体"
            0x0D -> "踝关节假体"
            0x0E -> "通用健康管理器"
            0x0F -> "个人移动设备"
            else -> "健康 0x%02X".format(minor)
        }
        else -> if (minor == 0) "" else "0x%02X".format(minor)
    }

    private fun serviceNames(bits: Int): List<String> = buildList {
        if (bits and 0x001 != 0) add("有限可发现")
        if (bits and 0x008 != 0) add("定位")
        if (bits and 0x010 != 0) add("网络")
        if (bits and 0x020 != 0) add("渲染")
        if (bits and 0x040 != 0) add("采集")
        if (bits and 0x080 != 0) add("对象传输")
        if (bits and 0x100 != 0) add("音频")
        if (bits and 0x200 != 0) add("电话")
        if (bits and 0x400 != 0) add("信息")
    }
}
