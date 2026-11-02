package calendar

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId

/** A local availability interval `[start, end)` within one day of the week, in the Schedule's zone. */
data class LocalInterval(
    val start: LocalTime,
    val end: LocalTime,
) {
    init {
        require(start < end) { "Interval start must be before its end: $start-$end" }
    }
}

/**
 * Weekly availability stored as local days and intervals together with an IANA zone (ADR 0001),
 * never as fixed UTC intervals.
 */
data class Schedule(
    val zone: ZoneId,
    val intervals: Map<DayOfWeek, List<LocalInterval>>,
) {
    companion object {
        /** Monday to Friday, 09:00-12:00 and 13:00-18:00; weekends are unavailable. */
        fun workWeek(zone: ZoneId): Schedule {
            val workDay =
                listOf(
                    LocalInterval(LocalTime.of(9, 0), LocalTime.of(12, 0)),
                    LocalInterval(LocalTime.of(13, 0), LocalTime.of(18, 0)),
                )
            val workDays =
                listOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY,
                )
            return Schedule(zone, workDays.associateWith { workDay })
        }
    }
}
