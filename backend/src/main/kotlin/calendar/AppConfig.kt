package calendar

import java.time.DateTimeException
import java.time.ZoneId

data class OwnerConfig(
    val name: String,
    val bio: String,
    val timeZone: String,
)

data class AppConfig(
    val owner: OwnerConfig,
) {
    companion object {
        const val DEFAULT_OWNER_NAME = "Владелец календаря"
        const val DEFAULT_OWNER_BIO = "Выберите удобное время для встречи"
        const val DEFAULT_OWNER_TIME_ZONE = "Asia/Almaty"

        /** Reads OWNER_* variables; fails fast on an invalid OWNER_TIME_ZONE. */
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
            )
        }
    }
}
