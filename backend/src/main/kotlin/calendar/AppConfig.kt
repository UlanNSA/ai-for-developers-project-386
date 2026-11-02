package calendar

import java.io.File
import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

data class OwnerConfig(
    val name: String,
    val bio: String,
    val timeZone: String,
)

/** A clock frozen at [initial]; test mode may move it (`POST /__test__/clock`), reset puts it back. */
class AdjustableClock(
    private val initial: Instant,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock(),
    Resettable {
    @Volatile private var now: Instant = initial

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = AdjustableClock(initial, zone).also { it.now = now }

    override fun instant(): Instant = now

    fun moveTo(instant: Instant) {
        now = instant
    }

    override fun reset() {
        now = initial
    }
}

/** Settings that exist only when `CALENDAR_TEST_MODE=true` (browser E2E): an adjustable frozen clock and test routes. */
class TestMode(
    fixedNow: Instant,
) {
    val clock = AdjustableClock(fixedNow)
}

data class AppConfig(
    val owner: OwnerConfig,
    /** The Owner's weekly availability; its zone is the Owner's time zone. */
    val schedule: Schedule = Schedule.workWeek(ZoneId.of(owner.timeZone)),
    /** Null in normal operation: the system clock, no reset route, all network interfaces. */
    val testMode: TestMode? = null,
    /** Directory of the built frontend (`STATIC_DIR`); null serves the API only (local development). */
    val staticDir: String? = null,
) {
    val clock: Clock get() = testMode?.clock ?: Clock.systemUTC()

    /** Test mode listens on loopback only, so the reset route is never reachable from the network. */
    val bindHost: String get() = if (testMode != null) "127.0.0.1" else "0.0.0.0"

    companion object {
        const val DEFAULT_OWNER_NAME = "Владелец календаря"
        const val DEFAULT_OWNER_BIO = "Выберите удобное время для встречи"
        const val DEFAULT_OWNER_TIME_ZONE = "Asia/Almaty"

        /**
         * Reads OWNER_*, CALENDAR_* and STATIC_DIR variables; fails fast on an invalid OWNER_TIME_ZONE, on test
         * mode without a valid CALENDAR_FIXED_NOW, on CALENDAR_FIXED_NOW without test mode and on a STATIC_DIR
         * that is not a directory with an index.html.
         */
        fun fromEnvironment(env: Map<String, String> = System.getenv()): AppConfig {
            val timeZone = env["OWNER_TIME_ZONE"] ?: DEFAULT_OWNER_TIME_ZONE
            try {
                ZoneId.of(timeZone)
            } catch (e: DateTimeException) {
                throw IllegalArgumentException(
                    "OWNER_TIME_ZONE has invalid IANA time zone id: '$timeZone' (example: Asia/Almaty)",
                    e,
                )
            }
            return AppConfig(
                owner =
                    OwnerConfig(
                        name = env["OWNER_NAME"] ?: DEFAULT_OWNER_NAME,
                        bio = env["OWNER_BIO"] ?: DEFAULT_OWNER_BIO,
                        timeZone = timeZone,
                    ),
                testMode = testModeFromEnvironment(env),
                staticDir = staticDirFromEnvironment(env),
            )
        }

        private fun staticDirFromEnvironment(env: Map<String, String>): String? {
            val dir = env["STATIC_DIR"]?.takeIf { it.isNotBlank() } ?: return null
            val directory = File(dir)
            require(directory.isDirectory) { "STATIC_DIR is not a directory: '$dir'" }
            require(File(directory, "index.html").isFile) { "STATIC_DIR '$dir' has no index.html (is the frontend built?)" }
            return dir
        }

        private fun testModeFromEnvironment(env: Map<String, String>): TestMode? {
            val fixedNow = env["CALENDAR_FIXED_NOW"]
            if (env["CALENDAR_TEST_MODE"] != "true") {
                require(fixedNow == null) {
                    "CALENDAR_FIXED_NOW is set but CALENDAR_TEST_MODE is not 'true'; refusing to start"
                }
                return null
            }
            requireNotNull(fixedNow) {
                "CALENDAR_TEST_MODE=true requires CALENDAR_FIXED_NOW (ISO-8601 instant, e.g. 2026-11-02T06:00:00Z)"
            }
            try {
                return TestMode(Instant.parse(fixedNow))
            } catch (e: DateTimeParseException) {
                throw IllegalArgumentException(
                    "CALENDAR_FIXED_NOW is not an ISO-8601 instant: '$fixedNow' (example: 2026-11-02T06:00:00Z)",
                    e,
                )
            }
        }
    }
}
