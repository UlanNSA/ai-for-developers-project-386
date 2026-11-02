package calendar

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeRangeTest {
    private fun range(
        start: String,
        end: String,
    ) = TimeRange(Instant.parse("2026-11-02T$start:00Z"), Instant.parse("2026-11-02T$end:00Z"))

    private val base = range("10:00", "11:00")

    @Test
    fun touchingRangesDoNotOverlap() {
        assertFalse(base.overlaps(range("11:00", "12:00")))
        assertFalse(base.overlaps(range("09:00", "10:00")))
    }

    @Test
    fun partialOverlapIsSymmetric() {
        assertTrue(base.overlaps(range("10:30", "11:30")))
        assertTrue(range("10:30", "11:30").overlaps(base))
        assertTrue(base.overlaps(range("09:30", "10:30")))
    }

    @Test
    fun containingAndIdenticalRangesOverlap() {
        assertTrue(base.overlaps(range("10:15", "10:45")))
        assertTrue(range("10:15", "10:45").overlaps(base))
        assertTrue(base.overlaps(base))
    }

    @Test
    fun disjointRangesDoNotOverlap() {
        assertFalse(base.overlaps(range("12:00", "13:00")))
    }

    @Test
    fun bookingRangeIsItsStartAndEnd() {
        val booking = Booking(UUID.randomUUID(), UUID.randomUUID(), base.start, base.end, "Гость", "g@example.com")

        assertEquals(base, booking.range)
    }
}
