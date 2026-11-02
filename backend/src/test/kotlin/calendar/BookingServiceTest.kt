package calendar

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class BookingServiceTest {
    private val now = Instant.parse("2026-11-02T06:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val typeId = UUID.randomUUID()

    private fun booking(start: String): Booking {
        val startAt = Instant.parse(start)
        return Booking(UUID.randomUUID(), typeId, startAt, startAt.plusSeconds(15 * 60), "Гость", "g@example.com")
    }

    private fun service(vararg stored: Booking): BookingService {
        val repository = InMemoryBookingRepository()
        // Insertion order is deliberately not chronological.
        stored.forEach { b -> repository.inCriticalSection { it.add(b) } }
        return BookingService(clock, Schedule.workWeek(ZoneId.of("Asia/Almaty")), repository)
    }

    @Test
    fun upcomingKeepsOnlyBookingsStartingStrictlyAfterNowSortedByStart() {
        val late = booking("2026-11-04T04:00:00Z")
        val soon = booking("2026-11-02T06:00:01Z")
        val past = booking("2026-11-02T05:00:00Z")
        val startingNow = booking("2026-11-02T06:00:00Z")
        val middle = booking("2026-11-03T04:00:00Z")

        val upcoming = service(late, past, startingNow, soon, middle).upcoming()

        assertEquals(listOf(soon, middle, late), upcoming)
    }

    @Test
    fun upcomingIsEmptyWithoutBookings() {
        assertEquals(emptyList(), service().upcoming())
    }
}
