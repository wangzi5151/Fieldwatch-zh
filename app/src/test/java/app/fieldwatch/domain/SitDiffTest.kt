package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SitDiffTest {
    private val axon = Fleet(
        id = "fleet-axon",
        name = "Axon",
        kind = SignatureClass.LAW_ENFORCEMENT,
        attentionNote = "Body-worn, in-car, dock, or TASER.",
    )
    private val fleets = listOf(axon)

    @Test
    fun secondSitSkipsThisSavedAndDefaultsToNewestOther() {
        val a = SitSummary("a", "plaza today", 30L)
        val b = SitSummary("b", "plaza last week", 20L)
        val c = SitSummary("c", "lot", 10L)
        val closed = listOf(a, b, c)
        assertEquals(listOf(b, c), SitDiff.secondSitChoices(closed, "a"))
        assertEquals("b", SitDiff.defaultSecondSitId(closed, "a"))
        assertEquals("a", SitDiff.defaultSecondSitId(closed, null))
        assertNull(SitDiff.defaultSecondSitId(emptyList(), null))
        assertNull(SitDiff.thisSavedId(SitSummary("open", "now", 1L), "a"))
        assertEquals("a", SitDiff.thisSavedId(null, "a"))
    }

    @Test
    fun presenceSplitsOnlyThisOnlySecondAndBoth() {
        val named = setOf("WIFI:AA:AA:AA:AA:AA:03")
        val thisSit = SitDiff.Side(
            name = "today",
            ram = false,
            radios = listOf(
                radio("WIFI:AA:AA:AA:AA:AA:01", "cam", extra = true),
                radio("WIFI:AA:AA:AA:AA:AA:02", "ap"),
                radio("WIFI:AA:AA:AA:AA:AA:03", "van", named = true),
            ),
        )
        val second = SitDiff.Side(
            name = "last week",
            ram = false,
            radios = listOf(
                radio("WIFI:AA:AA:AA:AA:AA:02", "ap"),
                radio("WIFI:AA:AA:AA:AA:AA:03", "van", named = true),
                radio("WIFI:AA:AA:AA:AA:AA:04", "new"),
            ),
        )
        val text = SitDiff.report(thisSit, second, demoMode = false)
        assertTrue(text.contains("ONLY IN THIS SIT (1)"))
        assertTrue(text.contains("AA:AA:AA:AA:AA:01"))
        assertTrue(text.contains("cam"))
        assertTrue(text.contains("特别关注"))
        assertTrue(text.contains("ONLY IN SECOND SIT (1)"))
        assertTrue(text.contains("Unmatched  1"))
        assertFalse(text.contains("AA:AA:AA:AA:AA:04"))
        val full = SitDiff.report(thisSit, second, demoMode = false, showAllRadios = true)
        assertTrue(full.contains("AA:AA:AA:AA:AA:04"))
        assertTrue(text.contains("IN BOTH (2)"))
        assertTrue(text.contains("van"))
        assertTrue(text.contains("This sit: today"))
        assertTrue(text.contains("Second sit: last week"))
        assertFalse(text.contains("Last 15 minutes is the Live RAM set"))
        assertFalse(text.contains("marked mine"))
    }

    @Test
    fun observerNotesPrintOnExclusiveLine() {
        val thisSit = SitDiff.Side(
            name = "today",
            ram = false,
            radios = listOf(
                radio("WIFI:AA:AA:AA:AA:AA:01", "van", named = true, observer = "fleet van, lot B"),
            ),
        )
        val second = SitDiff.Side(name = "week", ram = false, radios = emptyList())
        val text = SitDiff.report(thisSit, second, demoMode = false)
        assertTrue(text.contains("OBSERVER NOTES"))
        assertTrue(text.contains("van"))
        assertTrue(text.contains("fleet van, lot B"))
        assertTrue(text.contains("this sit"))
        assertFalse(text.contains("Observer: fleet van, lot B"))
    }

    @Test
    fun markedMineNamesTheWindowAndTheLines() {
        val thisSit = SitDiff.Side(
            name = "today",
            ram = false,
            radios = listOf(
                radio("WIFI:AA:AA:AA:AA:AA:01", "van", extra = true, mine = true),
                radio("WIFI:AA:AA:AA:AA:AA:02", "ap"),
            ),
        )
        val second = SitDiff.Side(
            name = "week",
            ram = false,
            radios = listOf(
                radio("WIFI:AA:AA:AA:AA:AA:01", "van", extra = true, mine = true),
                radio("WIFI:AA:AA:AA:AA:AA:03", "new", mine = true),
            ),
        )
        val doc = SitDiff.document(thisSit, second)
        val titles = doc.sections.map { it.title }
        val mineAt = titles.indexOf("标记为我的")
        assertTrue(mineAt > titles.indexOf("窗口"))
        assertTrue(titles.indexOf("特别关注") == -1 || mineAt < titles.indexOf("特别关注"))
        val section = doc.sections.single { it.title == "标记为我的" }
        assertFalse(section.alert)
        assertTrue(section.body.contains("AA:AA:AA:AA:AA:01"))
        assertTrue(section.body.contains("van"))
        assertTrue(section.body.contains("both"))
        assertTrue(section.body.contains("AA:AA:AA:AA:AA:03"))
        assertTrue(section.body.contains("second sit"))
        val text = doc.toPlainText()
        assertTrue(text.contains("van  Marked mine"))
        assertTrue(text.contains("new  Marked mine"))
        assertTrue(doc.takeaway.endsWith("· 2 marked mine."))
        val onlySecond = doc.sections.single { it.title.startsWith("Only in second") }
        assertTrue(onlySecond.after.contains("标记为我的"))
    }

    @Test
    fun ramCapNoteWhenThisSitIsLastFifteen() {
        val thisSit = SitDiff.Side(
            name = "最近 15 分钟",
            ram = true,
            radios = listOf(radio("BLE:AA:AA:AA:AA:AA:01", "")),
        )
        val second = SitDiff.Side(
            name = "plaza",
            ram = false,
            radios = emptyList(),
        )
        val text = SitDiff.report(thisSit, second, demoMode = false)
        assertTrue(text.contains("Last 15 minutes is the Live RAM set"))
        assertTrue(text.contains("${Sit.RADIO_CAP}"))
        assertTrue(text.contains("ONLY IN THIS SIT (1)"))
        assertTrue(text.contains("ONLY IN SECOND SIT (0)"))
        assertTrue(text.contains("(none)"))
    }

    @Test
    fun privacyModeMasksMacTails() {
        val thisSit = SitDiff.Side(
            name = "today",
            ram = false,
            radios = listOf(radio("WIFI:AA:BB:CC:11:22:33", "Cafe")),
        )
        val second = SitDiff.Side(name = "week", ram = false, radios = emptyList())
        val text = SitDiff.report(thisSit, second, demoMode = true, showAllRadios = true)
        assertTrue(text.contains("隐私"))
        assertTrue(text.contains("AA:BB:CC:**:**:**"))
        assertFalse(text.contains("11:22:33"))
        assertTrue(text.contains("FIELDWATCH 监测对比"))
    }

    @Test
    fun extraAttentionFromFleetNote() {
        val device = Sighting(
            key = "BLE:AA:BB:CC:DD:EE:01",
            kind = RadioKind.BLE,
            mac = "AA:BB:CC:DD:EE:01",
            name = "dock",
            rssi = -50,
            rssiMin = -50,
            rssiMax = -50,
            channel = 0,
            frequencyMhz = 2402,
            vendor = null,
            randomized = true,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 1L,
            lastSeen = 1L,
            hitCount = 1,
            fleetIds = setOf("fleet-axon"),
            rssiHistory = emptyList(),
            presence = emptyList(),
        )
        val row = SitDiff.fromSighting(device, fleets, customNames = emptyMap())
        assertTrue(row.extraAttention)
        assertEquals(listOf("Axon"), row.fleetNames)
        assertFalse(row.named)
        assertTrue(row.randomized)
    }

    @Test
    fun aiExportIsAddendumNotASecondRoster() {
        val thisSit = SitDiff.Side(
            name = "today",
            ram = true,
            radios = listOf(
                radio("WIFI:AA:AA:AA:AA:AA:01", "cam", extra = true),
                radio("BLE:BB:BB:BB:BB:BB:02", "tag", named = true, ble = true, rand = true),
                radio("WIFI:AA:AA:AA:AA:AA:02", "ap"),
            ),
        )
        val second = SitDiff.Side(
            name = "last week",
            ram = false,
            radios = listOf(
                radio("WIFI:AA:AA:AA:AA:AA:02", "ap"),
                radio("WIFI:AA:AA:AA:AA:AA:04", "new"),
            ),
        )
        val text = SitDiffPrompt.build(thisSit, second, demoMode = false)
        assertTrue(text.contains("Do not rewrite that report"))
        assertTrue(text.contains("Overlap:"))
        assertTrue(text.contains("独有的特别关注："))
        assertTrue(text.contains("cam"))
        assertTrue(text.contains("独有的已命名设备："))
        assertTrue(text.contains("tag"))
        assertTrue(text.contains("RAND BLE"))
        assertTrue(text.contains("Cap note:"))
        assertTrue(text.contains("Takeaway:"))
        assertTrue(text.contains("decoded live value"))
        assertTrue(text.contains("A flood note is a burst of new addresses, not a follower."))
        assertTrue(text.contains("Flood if any"))
        assertTrue(text.contains("FIELDWATCH 监测对比"))
        assertFalse(text.contains("Full Wi-Fi inventory"))
    }

    @Test
    fun compareNamesTheSitThatHadTheFlood() {
        val burst = FloodBurst(
            at = 1_700_000_000_000L,
            popupCount = 6,
            nameCount = 0,
            families = listOf("Fast Pair"),
            medianRssi = -40,
        )
        val morning = SitDiff.Side("morning", ram = false, radios = emptyList(), floods = listOf(burst))
        val stall = SitDiff.Side("stall", ram = false, radios = emptyList())
        val doc = SitDiff.document(morning, stall)
        val section = doc.sections.single { it.title == "洪泛" }
        assertTrue(section.body.contains(FloodBurst.INTRO))
        assertTrue(section.body.contains("morning"))
        assertTrue(section.body.contains("配对洪泛"))
        assertTrue(section.body.contains("6 new addresses: Fast Pair"))
        assertFalse(section.body.contains("stall"))
        assertFalse(section.alert)
        assertFalse(doc.trackingAlert)
        val titles = doc.sections.map { it.title }
        assertTrue(titles.indexOf("洪泛") < titles.indexOf("Only in this sit (0)"))
        assertFalse(doc.takeaway.contains("marked mine"))
        val text = SitDiff.report(morning, stall, demoMode = false)
        assertTrue(text.contains("FLOOD"))
        assertTrue(text.contains("morning"))
    }

    @Test
    fun compareUsesTheWifiFloodIntro() {
        val burst = FloodBurst(
            at = 1_700_000_000_000L,
            popupCount = 15,
            nameCount = 0,
            medianRssi = -34,
            keys = listOf("WIFI:02:00:00:00:00:02"),
            wifi = true,
        )
        val morning = SitDiff.Side("morning", ram = false, radios = emptyList(), floods = listOf(burst))
        val stall = SitDiff.Side("stall", ram = false, radios = emptyList())
        val section = SitDiff.document(morning, stall).sections.single { it.title == "洪泛" }
        assertTrue(section.body.contains(FloodBurst.WIFI_INTRO))
        assertFalse(section.body.contains("new Bluetooth addresses"))
        assertTrue(section.body.contains("Wi-Fi 信标洪泛"))
        assertTrue(section.body.contains("15 new names"))
    }

    @Test
    fun compareLeavesFloodAddressesOutOfPresence() {
        val kept = "WIFI:AA:AA:AA:AA:AA:01"
        val floodA = "BLE:02:00:00:00:00:0A"
        val floodB = "BLE:02:00:00:00:00:0B"
        val burst = FloodBurst(
            at = 1_700_000_000_000L,
            popupCount = 6,
            nameCount = 0,
            families = listOf("Fast Pair"),
            keys = listOf(floodA, floodB),
        )
        val morning = SitDiff.Side(
            name = "morning",
            ram = false,
            radios = listOf(
                radio(kept, "ap"),
                radio(floodA, "spam", ble = true, rand = true),
                radio(floodB, "spam", ble = true, rand = true),
            ),
            floods = listOf(burst),
        )
        val stall = SitDiff.Side(
            name = "stall",
            ram = false,
            radios = listOf(radio(kept, "ap")),
        )
        val doc = SitDiff.document(morning, stall, showAllRadios = true)
        assertEquals("1", doc.meta.first { it.first == "此监测设备数" }.second)
        assertEquals("1", doc.meta.first { it.first == "第二监测设备数" }.second)
        assertTrue(doc.sections.any { it.title == "Only in this sit (0)" })
        assertTrue(doc.sections.any { it.title == "In both (1)" })
        val text = doc.toPlainText()
        assertTrue(text.contains("2 addresses from this burst are left out of the counts and lists below."))
        assertTrue(text.contains("AA:AA:AA:AA:AA:01"))
        assertFalse(text.contains("02:00:00:00:00:0A"))
        assertFalse(text.contains("02:00:00:00:00:0B"))
        val prompt = SitDiffPrompt.build(morning, stall, demoMode = false)
        assertTrue(prompt.contains("Only in this sit: 0"))
        assertTrue(prompt.contains("In both: 1"))
    }

    @Test
    fun aiExportPrivacyMasksExclusiveMacs() {
        val thisSit = SitDiff.Side(
            name = "today",
            ram = false,
            radios = listOf(radio("WIFI:AA:BB:CC:11:22:33", "Cafe", extra = true)),
        )
        val second = SitDiff.Side(name = "week", ram = false, radios = emptyList())
        val text = SitDiffPrompt.build(thisSit, second, demoMode = true)
        assertTrue(text.contains("隐私模式"))
        assertTrue(text.contains("AA:BB:CC:**:**:**"))
        assertFalse(text.contains("11:22:33"))
    }

    @Test
    fun bothStatesDecodedValueChangeAndQuotesAMatchingNoteOnce() {
        val day = "已与物主分离。该地址可能静止约一天。"
        val separated = LiveDecodeChip("separated", emphasis = true, note = day)
        val near = LiveDecodeChip("near owner", emphasis = false, note = "Near its owner.")
        val changedA = radio("BLE:AA:AA:AA:AA:AA:01", "tag", ble = true).copy(liveDecode = listOf(separated))
        val changedB = radio("BLE:AA:AA:AA:AA:AA:01", "tag", ble = true).copy(liveDecode = listOf(near))
        val held = radio("BLE:BB:BB:BB:BB:BB:02", "held", ble = true).copy(liveDecode = listOf(separated))
        val text = SitDiff.report(
            SitDiff.Side("today", ram = false, radios = listOf(changedA, held)),
            SitDiff.Side("week", ram = false, radios = listOf(changedB, held)),
            demoMode = false,
        )
        val change = text.lineSequence().first { it.contains("decoded value changed") }
        assertTrue(change.contains("Separated → Near owner"))
        assertFalse(change.contains("about a day"))
        val full = SitDiff.report(
            SitDiff.Side("today", ram = false, radios = listOf(changedA, held)),
            SitDiff.Side("week", ram = false, radios = listOf(changedB, held)),
            demoMode = false,
            showAllRadios = true,
        )
        val heldLine = full.lineSequence().first { it.contains("BB:BB:BB:BB:BB:02") && it.contains("Separated") }
        assertEquals(1, heldLine.split("about a day").size - 1)
    }

    private fun radio(
        key: String,
        name: String,
        extra: Boolean = false,
        named: Boolean = false,
        ble: Boolean = false,
        rand: Boolean = false,
        observer: String = "",
        mine: Boolean = false,
    ) = SitDiff.Radio(
        key = key,
        kind = if (ble) RadioKind.BLE else RadioKind.WIFI,
        mac = key.substringAfter(':'),
        name = name,
        extraAttention = extra,
        named = named,
        fleetNames = if (extra) listOf("Axon") else emptyList(),
        randomized = rand,
        observerNotes = observer,
        mine = mine,
    )
}
