package calendar

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** A free time range, `[startAt, endAt)`. */
data class Slot(
    val startAt: Instant,
    val endAt: Instant,
)

/** An occupied time range, `[start, end)`; the calculator's only view of existing bookings. */
data class TimeRange(
    val start: Instant,
    val end: Instant,
)

/** Supplies the time ranges already taken by confirmed bookings. */
fun interface BusyTimeSource {
    fun busyRanges(): List<TimeRange>
}

object SlotCalculator {
    /** Today and the next 13 calendar days in the Owner's zone. */
    const val HORIZON_DAYS = 14
    const val GRID_MINUTES = 15L

    /**
     * Free slots of [durationMinutes] for the [schedule] inside the booking horizon, sorted by start.
     *
     * A pure function of its arguments. Slot starts lie on the Owner's wall-clock 15-minute grid, are
     * strictly after `clock.instant()`, and the whole slot (real minutes between UTC instants) fits
     * inside one availability interval and overlaps no range of [busy]. All ranges are half-open.
     *
     * DST is handled explicitly (ADR 0001): a local start that does not exist yields no slot, a local
     * time that occurs twice uses its first occurrence; an interval end inside a gap is the instant of
     * the transition.
     */
    fun calculate(
        clock: Clock,
        schedule: Schedule,
        durationMinutes: Int,
        busy: List<TimeRange>,
    ): List<Slot> {
        val now = clock.instant()
        val zone = schedule.zone
        val today = now.atZone(zone).toLocalDate()
        val duration = Duration.ofMinutes(durationMinutes.toLong())
        val slots = mutableListOf<Slot>()
        for (dayOffset in 0 until HORIZON_DAYS) {
            val date = today.plusDays(dayOffset.toLong())
            for (interval in schedule.intervals[date.dayOfWeek].orEmpty()) {
                val intervalEnd = intervalEnd(date, interval.end, zone)
                var local = interval.start
                while (local < interval.end) {
                    val start = startInstant(date, local, zone)
                    if (start != null && start > now) {
                        val end = start.plus(duration)
                        if (end <= intervalEnd && busy.none { start < it.end && it.start < end }) {
                            slots += Slot(start, end)
                        }
                    }
                    local = local.plusMinutes(GRID_MINUTES)
                    if (local == LocalTime.MIDNIGHT) break
                }
            }
        }
        return slots.sortedBy { it.startAt }
    }

    /** First occurrence of [time] on [date]; null when that local time does not exist (DST gap). */
    private fun startInstant(
        date: LocalDate,
        time: LocalTime,
        zone: ZoneId,
    ): Instant? {
        val local = LocalDateTime.of(date, time)
        // In an overlap the list holds the offset in force before the transition first.
        val offset = zone.rules.getValidOffsets(local).firstOrNull() ?: return null
        return local.toInstant(offset)
    }

    /** Like [startInstant], but an end inside a gap resolves to the instant of the transition. */
    private fun intervalEnd(
        date: LocalDate,
        time: LocalTime,
        zone: ZoneId,
    ): Instant =
        startInstant(date, time, zone)
            ?: requireNotNull(zone.rules.getTransition(LocalDateTime.of(date, time))).instant
}
