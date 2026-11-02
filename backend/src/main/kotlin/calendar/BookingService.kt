package calendar

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** A confirmed booking; `endAt` is always `startAt + durationMinutes` of its meeting type. */
data class Booking(
    val id: UUID,
    val meetingTypeId: UUID,
    val startAt: Instant,
    val endAt: Instant,
    val guestName: String,
    val guestEmail: String,
)

/** The state seen and changed inside one critical section of the repository. */
interface BookingTransaction {
    /** Time ranges of all confirmed bookings, of any meeting type. */
    val busy: List<TimeRange>

    fun add(booking: Booking)
}

/** Booking storage. Its [BusyTimeSource] side feeds slot calculation, so booked intervals are not offered. */
interface BookingRepository : BusyTimeSource {
    fun list(): List<Booking>

    /**
     * Runs [block] while holding the repository's single lock: everything it reads through the
     * transaction and every [BookingTransaction.add] is atomic with respect to all other callers.
     */
    fun <R> inCriticalSection(block: (BookingTransaction) -> R): R
}

/** In-memory storage; data is lost on restart. One lock guards every read and write. */
class InMemoryBookingRepository : BookingRepository {
    private val lock = ReentrantLock()
    private val items = mutableListOf<Booking>()

    override fun list(): List<Booking> = lock.withLock { items.toList() }

    override fun busyRanges(): List<TimeRange> = lock.withLock { items.map { TimeRange(it.startAt, it.endAt) } }

    override fun <R> inCriticalSection(block: (BookingTransaction) -> R): R =
        lock.withLock {
            block(
                object : BookingTransaction {
                    override val busy: List<TimeRange> get() = items.map { TimeRange(it.startAt, it.endAt) }

                    override fun add(booking: Booking) {
                        items += booking
                    }
                },
            )
        }
}

sealed interface BookingOutcome {
    data class Created(
        val booking: Booking,
    ) : BookingOutcome

    /** The start is not one the schedule, grid, horizon or current time would ever offer. */
    data object InvalidSlot : BookingOutcome

    /** The start would be offered but overlaps an existing booking. */
    data object SlotTaken : BookingOutcome
}

/** Creates bookings; the backend is the only source of truth about availability. */
class BookingService(
    private val clock: Clock,
    private val schedule: Schedule,
    private val bookings: BookingRepository,
) {
    /**
     * Re-checks availability and inserts in ONE critical section ([BookingRepository.inCriticalSection]);
     * the clock is read inside it (by [SlotCalculator.calculate]). Classification: not offered even with
     * no bookings -> [BookingOutcome.InvalidSlot]; offered but overlapping any booking -> [BookingOutcome.SlotTaken].
     */
    fun create(
        meetingType: MeetingType,
        startAt: Instant,
        guestName: String,
        guestEmail: String,
    ): BookingOutcome =
        bookings.inCriticalSection { tx ->
            val offered =
                SlotCalculator
                    .calculate(clock, schedule, meetingType.durationMinutes, emptyList())
                    .any { it.startAt == startAt }
            if (!offered) return@inCriticalSection BookingOutcome.InvalidSlot
            val endAt = startAt.plus(Duration.ofMinutes(meetingType.durationMinutes.toLong()))
            if (tx.busy.any { startAt < it.end && it.start < endAt }) return@inCriticalSection BookingOutcome.SlotTaken
            val booking = Booking(UUID.randomUUID(), meetingType.id, startAt, endAt, guestName, guestEmail)
            tx.add(booking)
            BookingOutcome.Created(booking)
        }
}
