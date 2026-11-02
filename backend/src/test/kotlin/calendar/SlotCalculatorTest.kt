package calendar

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SlotCalculatorTest {
    private val almaty = ZoneId.of("Asia/Almaty")
    private val berlin = ZoneId.of("Europe/Berlin")

    // 2026-11-02 is a Monday; Asia/Almaty is UTC+5 all year.
    private val mondayMorning = clockAt("2026-11-02T03:00:00Z") // 08:00 in Almaty

    private fun clockAt(instant: String): Clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)

    private fun interval(
        from: String,
        to: String,
    ) = LocalInterval(LocalTime.parse(from), LocalTime.parse(to))

    private fun everyDay(vararg intervals: LocalInterval) = DayOfWeek.entries.associateWith { intervals.toList() }

    private fun starts(slots: List<Slot>) = slots.map { it.startAt.toString() }

    private fun calculate(
        clock: Clock,
        schedule: Schedule,
        duration: Int = 15,
        busy: List<TimeRange> = emptyList(),
    ) = SlotCalculator.calculate(clock, schedule, duration, busy)

    @Test
    fun almatyWorkWeekGivesTenWorkingDaysOfThirtyTwoQuarterHourSlots() {
        val slots = calculate(mondayMorning, Schedule.workWeek(almaty))

        // Monday 2 Nov .. Sunday 15 Nov: 10 working days, 12 + 20 starts each.
        assertEquals(320, slots.size)
        assertEquals("2026-11-02T04:00:00Z", slots.first().startAt.toString()) // 09:00 Almaty
        assertEquals("2026-11-13T12:45:00Z", slots.last().startAt.toString()) // Friday 17:45 Almaty
        assertEquals("2026-11-13T13:00:00Z", slots.last().endAt.toString())
    }

    @Test
    fun weekendsAndTheGapBetweenIntervalsHaveNoSlots() {
        val slots = calculate(mondayMorning, Schedule.workWeek(almaty))

        val localStarts = slots.map { it.startAt.atZone(almaty) }
        assertTrue(localStarts.none { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY })
        assertTrue(localStarts.none { it.hour == 12 })
        assertTrue(localStarts.all { it.minute % 15 == 0 })
    }

    @Test
    fun slotsAreSortedByStartEvenWhenIntervalsAreListedOutOfOrder() {
        val schedule = Schedule(almaty, everyDay(interval("13:00", "14:00"), interval("09:00", "10:00")))

        val slots = calculate(mondayMorning, schedule)

        assertEquals(slots.sortedBy { it.startAt }, slots)
    }

    @Test
    fun horizonIsTodayAndThirteenFollowingCalendarDaysOfTheOwner() {
        val schedule = Schedule(almaty, everyDay(interval("09:00", "10:00")))

        val days =
            calculate(mondayMorning, schedule)
                .map {
                    it.startAt
                        .atZone(almaty)
                        .toLocalDate()
                        .toString()
                }.distinct()

        assertEquals(14, days.size)
        assertEquals("2026-11-02", days.first())
        assertEquals("2026-11-15", days.last())
    }

    @Test
    fun horizonDayIsTheOwnersDayNotUtc() {
        val schedule = Schedule(almaty, everyDay(interval("09:00", "10:00")))
        // 20:00Z on 2 Nov is already 01:00 on Tuesday 3 Nov in Almaty.
        val clock = clockAt("2026-11-02T20:00:00Z")

        val days =
            calculate(clock, schedule)
                .map {
                    it.startAt
                        .atZone(almaty)
                        .toLocalDate()
                        .toString()
                }.distinct()

        assertEquals("2026-11-03", days.first())
        assertEquals("2026-11-16", days.last())
    }

    @Test
    fun slotStartMustBeStrictlyAfterNow() {
        val schedule = Schedule(almaty, everyDay(interval("09:00", "10:00")))

        val atStart = calculate(clockAt("2026-11-02T04:00:00Z"), schedule) // exactly 09:00
        val justBefore = calculate(clockAt("2026-11-02T03:59:59Z"), schedule)

        assertEquals("2026-11-02T04:15:00Z", starts(atStart).first())
        assertEquals("2026-11-02T04:00:00Z", starts(justBefore).first())
    }

    @Test
    fun nothingIsOfferedTodayAfterTheLastIntervalEnds() {
        val schedule = Schedule(almaty, everyDay(interval("09:00", "10:00")))

        val slots = calculate(clockAt("2026-11-02T05:00:00Z"), schedule) // 10:00 Almaty

        assertEquals("2026-11-03T04:00:00Z", starts(slots).first())
    }

    @Test
    fun slotMustFitInsideOneIntervalAndMayEndExactlyOnItsBoundary() {
        val schedule = Schedule(almaty, everyDay(interval("09:00", "12:00"), interval("13:00", "18:00")))

        val today =
            calculate(mondayMorning, schedule, duration = 30)
                .filter { it.startAt.atZone(almaty).dayOfMonth == 2 }
                .map {
                    it.startAt
                        .atZone(almaty)
                        .toLocalTime()
                        .toString()
                }

        assertTrue("11:30" in today) // ends exactly at 12:00
        assertTrue("11:45" !in today) // would cross into the break
        assertTrue("12:00" !in today && "12:30" !in today)
        assertTrue("13:00" in today)
        assertTrue("17:30" in today && "17:45" !in today)
    }

    @Test
    fun differentDurationsChangeOnlyTheLatestStartAndTheEnd() {
        val schedule = Schedule(almaty, everyDay(interval("09:00", "10:00")))
        val clock = clockAt("2026-11-02T03:00:00Z")

        fun today(duration: Int) = calculate(clock, schedule, duration).filter { it.startAt.atZone(almaty).dayOfMonth == 2 }

        assertEquals(4, today(15).size) // 09:00, :15, :30, :45
        assertEquals(3, today(30).size) // last start 09:30
        assertEquals(1, today(60).size) // only 09:00
        assertEquals(0, today(75).size)
        assertEquals(Instant.parse("2026-11-02T05:00:00Z"), today(60).single().endAt)
        assertEquals(2, today(45).size) // 09:00 and 09:15
        assertEquals(Instant.parse("2026-11-02T05:00:00Z"), today(45).last().endAt)
    }

    @Test
    fun anExistingBookingRemovesEveryOverlappingStartButTouchingBoundsStayFree() {
        val schedule = Schedule(almaty, everyDay(interval("09:00", "12:00")))
        // Existing booking 10:00-10:30 Almaty = 05:00Z-05:30Z.
        val busy = listOf(TimeRange(Instant.parse("2026-11-02T05:00:00Z"), Instant.parse("2026-11-02T05:30:00Z")))

        val today =
            calculate(mondayMorning, schedule, duration = 30, busy = busy)
                .filter { it.startAt.atZone(almaty).dayOfMonth == 2 }
                .map {
                    it.startAt
                        .atZone(almaty)
                        .toLocalTime()
                        .toString()
                }

        assertEquals(listOf("09:00", "09:15", "09:30", "10:30", "10:45", "11:00", "11:15", "11:30"), today)
        // 09:30-10:00 touches the booking start, 10:30-11:00 touches its end: both free.
    }

    @Test
    fun aBookingInsideALongerSlotOrPartiallyOverlappingBlocksIt() {
        val schedule = Schedule(almaty, everyDay(interval("09:00", "12:00")))
        // Booking 10:10-10:20 does not sit on the grid but still blocks everything it overlaps.
        val busy = listOf(TimeRange(Instant.parse("2026-11-02T05:10:00Z"), Instant.parse("2026-11-02T05:20:00Z")))

        val today =
            calculate(mondayMorning, schedule, duration = 60, busy = busy)
                .filter { it.startAt.atZone(almaty).dayOfMonth == 2 }
                .map {
                    it.startAt
                        .atZone(almaty)
                        .toLocalTime()
                        .toString()
                }

        // 09:00-10:00 is free; every start from 09:15 to 10:15 overlaps the booking; 10:30 onwards is free.
        assertEquals(listOf("09:00", "10:30", "10:45", "11:00"), today)
    }

    @Test
    fun berlinSpringForwardNonexistentLocalTimesGiveNoSlotAndRealMinutesAreUsed() {
        // 2026-03-29: 02:00 CET jumps to 03:00 CEST, local 02:00-02:59 does not exist.
        val schedule = Schedule(berlin, everyDay(interval("01:00", "04:00")))
        val clock = clockAt("2026-03-28T22:00:00Z") // Saturday 23:00 CET; horizon starts 28 Mar

        val sunday = calculate(clock, schedule, duration = 30).filter { it.startAt.atZone(berlin).dayOfMonth == 29 }

        assertEquals(
            listOf(
                "2026-03-29T00:00:00Z", // 01:00 CET
                "2026-03-29T00:15:00Z",
                "2026-03-29T00:30:00Z",
                "2026-03-29T00:45:00Z", // 01:45 CET
                // 02:00..02:45 do not exist
                "2026-03-29T01:00:00Z", // 03:00 CEST
                "2026-03-29T01:15:00Z",
                "2026-03-29T01:30:00Z",
            ),
            starts(sunday),
        )
        // 01:45 CET + 30 real minutes = 03:15 CEST (a wall-clock addition would give 02:15, which does not exist).
        assertEquals(Instant.parse("2026-03-29T01:15:00Z"), sunday[3].endAt)
        // The interval ends at 04:00 CEST = 02:00Z: last 30-minute start is 03:30 CEST = 01:30Z.
        assertEquals("2026-03-29T01:30:00Z", sunday.last().startAt.toString())
    }

    @Test
    fun berlinSpringForwardIntervalEndInsideTheGapIsTheTransitionInstant() {
        // Interval 01:00-02:30 ends at a nonexistent local time: it ends when the clock jumps (01:00Z).
        val schedule = Schedule(berlin, everyDay(interval("01:00", "02:30")))
        val clock = clockAt("2026-03-28T22:00:00Z")

        val sunday = calculate(clock, schedule, duration = 15).filter { it.startAt.atZone(berlin).dayOfMonth == 29 }

        assertEquals(
            listOf("2026-03-29T00:00:00Z", "2026-03-29T00:15:00Z", "2026-03-29T00:30:00Z", "2026-03-29T00:45:00Z"),
            starts(sunday),
        )
        assertEquals("2026-03-29T01:00:00Z", sunday.last().endAt.toString())
    }

    @Test
    fun berlinFallBackRepeatedLocalTimesUseTheFirstOccurrence() {
        // 2026-10-25: 03:00 CEST falls back to 02:00 CET, local 02:00-02:59 occurs twice.
        val schedule = Schedule(berlin, everyDay(interval("01:00", "04:00")))
        val clock = clockAt("2026-10-24T22:00:00Z") // Sunday 00:00 CEST

        val sunday =
            calculate(clock, schedule, duration = 15).filter {
                it.startAt
                    .atZone(berlin)
                    .toLocalDate()
                    .dayOfMonth == 25
            }

        assertEquals(12, sunday.size) // one slot per local grid time 01:00..03:45
        assertEquals("2026-10-24T23:00:00Z", starts(sunday)[0]) // 01:00 CEST
        assertEquals("2026-10-25T00:00:00Z", starts(sunday)[4]) // 02:00 CEST: first occurrence
        assertEquals("2026-10-25T00:45:00Z", starts(sunday)[7]) // 02:45 CEST
        assertEquals("2026-10-25T02:00:00Z", starts(sunday)[8]) // 03:00 CET (the second 02:xx is skipped)
        assertEquals("2026-10-25T02:45:00Z", starts(sunday).last()) // 03:45 CET
        assertEquals("2026-10-25T03:00:00Z", sunday.last().endAt.toString()) // interval ends 04:00 CET
    }

    @Test
    fun berlinFallBackDurationIsRealMinutes() {
        val schedule = Schedule(berlin, everyDay(interval("01:00", "04:00")))
        val clock = clockAt("2026-10-24T22:00:00Z")

        val sunday =
            calculate(clock, schedule, duration = 60).filter {
                it.startAt
                    .atZone(berlin)
                    .toLocalDate()
                    .dayOfMonth == 25
            }

        // 02:30 CEST (00:30Z) + 60 real minutes = 01:30Z, which is 02:30 CET on the wall clock.
        val slot = sunday.single { it.startAt == Instant.parse("2026-10-25T00:30:00Z") }
        assertEquals(Instant.parse("2026-10-25T01:30:00Z"), slot.endAt)
        // The last 60-minute start is 03:00 CET = 02:00Z (ends at 04:00 CET = 03:00Z).
        assertEquals("2026-10-25T02:00:00Z", sunday.last().startAt.toString())
    }

    @Test
    fun almatyHasNoDstSoUtcOffsetIsConstant() {
        val schedule = Schedule.workWeek(almaty)

        val slots = calculate(clockAt("2026-03-25T00:00:00Z"), schedule) // spans late March, when Europe shifts

        assertTrue(slots.all { it.startAt.atZone(almaty).offset == ZoneOffset.ofHours(5) })
        assertEquals("2026-03-25T04:00:00Z", starts(slots).first()) // 09:00 +05:00
    }

    @Test
    fun anEmptyScheduleGivesNoSlots() {
        assertEquals(emptyList(), calculate(mondayMorning, Schedule(almaty, emptyMap())))
    }
}
