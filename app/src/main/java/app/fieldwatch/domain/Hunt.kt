package app.fieldwatch.domain

/**
 * Relative-loudness hunt for one BLE advertiser. RSSI is not distance
 * and not a bearing. Quiet / gone is as important as closer / further.
 */
enum class HuntCue {
    VERY_CLOSE,
    CLOSER,
    FURTHER,
    SAME,
    WAITING,
    QUIET,
    GONE,
}

object Hunt {
    const val RECENT_MS = 2_000L
    const val EARLIER_FROM_MS = 8_000L
    const val EARLIER_TO_MS = 3_500L
    const val STEP_DB = 3.0
    const val QUIET_MS = 8_000L
    /** Same floor as DeviceExplain “very strong.” Pocket / in-hand / same bag, not meters. */
    const val VERY_CLOSE_DBM = -45.0
    /** Geiger tick: last-heard RSSI mapped to interval. Loud end is faster than Very Close. */
    const val TICK_LOUD_DBM = -40
    const val TICK_QUIET_DBM = -90
    const val TICK_FAST_MS = 90L
    const val TICK_SLOW_MS = 1_400L

    fun cue(
        samples: List<RssiSample>,
        now: Long,
        lastSeen: Long?,
        missing: Boolean,
    ): HuntCue {
        if (missing) return HuntCue.GONE
        if (lastSeen == null) return HuntCue.WAITING
        if (now - lastSeen > QUIET_MS) return HuntCue.QUIET
        val usable = samples.filter { Rssi.measured(it.rssi) }
        val recent = usable.filter { it.at >= now - RECENT_MS }
        val loud = if (recent.isNotEmpty()) {
            recent.map { it.rssi }.average()
        } else {
            usable.lastOrNull { now - it.at <= QUIET_MS }?.rssi?.toDouble()
        }
        if (loud != null && loud >= VERY_CLOSE_DBM) return HuntCue.VERY_CLOSE
        val earlier = usable.filter { it.at in (now - EARLIER_FROM_MS)..(now - EARLIER_TO_MS) }
        if (recent.size < 2 || earlier.size < 2) return HuntCue.WAITING
        val delta = recent.map { it.rssi }.average() - earlier.map { it.rssi }.average()
        return when {
            delta >= STEP_DB -> HuntCue.CLOSER
            delta <= -STEP_DB -> HuntCue.FURTHER
            else -> HuntCue.SAME
        }
    }

    fun label(cue: HuntCue): String = when (cue) {
        HuntCue.VERY_CLOSE -> "非常近"
        HuntCue.CLOSER -> "更近了"
        HuntCue.FURTHER -> "更远了"
        HuntCue.SAME -> "大致相同"
        HuntCue.WAITING -> "聆听中…"
        HuntCue.QUIET -> "安静"
        HuntCue.GONE -> "已消失"
    }

    fun hint(cue: HuntCue): String = when (cue) {
        HuntCue.VERY_CLOSE -> "这里信号极强。环顾四周 — 通常就在手中、口袋里或同一个包里。不是以米计。"
        HuntCue.CLOSER -> "比几秒前更响。继续朝那个方向走。"
        HuntCue.FURTHER -> "比几秒前更轻。转身或后退。"
        HuntCue.SAME -> "尚无明显变化。放慢速度；保持手机不动。"
        HuntCue.WAITING -> "需要几秒的数据包才能比较。"
        HuntCue.QUIET -> "几秒内没有数据包。可能是静默，或被墙挡住。"
        HuntCue.GONE -> "已离开实时集合。随机 BLE 常在追踪途中消失。"
    }

    /**
     * Interval between Hunt ticks, or null to stay silent.
     * Quiet / Gone (and no live RSSI) do not tick. Waiting still ticks if a packet is on the screen.
     */
    fun tickIntervalMs(rssi: Int?, cue: HuntCue): Long? {
        if (cue == HuntCue.QUIET || cue == HuntCue.GONE) return null
        val r = rssi ?: return null
        val span = (TICK_LOUD_DBM - TICK_QUIET_DBM).toDouble()
        val t = ((r - TICK_QUIET_DBM) / span).coerceIn(0.0, 1.0)
        return (TICK_SLOW_MS + (TICK_FAST_MS - TICK_SLOW_MS) * t).toLong()
    }
}
