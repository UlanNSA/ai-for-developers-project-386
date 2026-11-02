package calendar

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.DateTimeException
import java.time.Instant
import java.util.UUID

/**
 * The single place for request validation rules: every bound and rule lives here, nothing is repeated in routes.
 * Lengths are counted after trim in Unicode code points (not UTF-16 units). Extend this file for new inputs.
 */
object Limits {
    const val TITLE_MIN = 1
    const val TITLE_MAX = 100
    const val DESCRIPTION_MAX = 500
    const val DURATION_MIN = 15
    const val DURATION_MAX = 240
    const val DURATION_STEP = 15
    const val GUEST_NAME_MIN = 1
    const val GUEST_NAME_MAX = 100
    const val EMAIL_MAX = 254
}

sealed interface Validated<out T> {
    data class Valid<T>(
        val value: T,
    ) : Validated<T>

    data class Invalid(
        val errors: List<FieldError>,
    ) : Validated<Nothing>
}

/** A validated, normalized meeting type ready to be stored (text trimmed, blank description absent). */
data class MeetingTypeDraft(
    val title: String,
    val description: String?,
    val durationMinutes: Int,
)

/** A validated booking request; `startAt` is a UTC instant, name and email are trimmed (email case preserved). */
data class BookingDraft(
    val meetingTypeId: UUID,
    val startAt: Instant,
    val guestName: String,
    val guestEmail: String,
)

/** Number of Unicode code points (not UTF-16 units) of [text], which [ObjectReader.text] has already trimmed. */
private fun codePointLength(text: String): Int = text.codePointCount(0, text.length)

/** Number of Unicode code points after trimming, for text that has not been trimmed yet. */
fun trimmedLength(text: String): Int = codePointLength(text.trim())

/** Collects field errors while reading typed fields from a strictly parsed JSON object. */
private class ObjectReader(
    private val obj: JsonObject,
) {
    val errors = mutableListOf<FieldError>()

    fun rejectUnknown(known: Set<String>) {
        obj.keys.filter { it !in known }.forEach { errors += FieldError(it, "Неизвестное поле") }
    }

    fun fail(
        field: String,
        message: String,
    ) {
        errors += FieldError(field, message)
    }

    /** Returns the trimmed string, or null when absent/null (reported if [required]) or of the wrong type. */
    fun text(
        field: String,
        required: Boolean,
    ): String? {
        val element: JsonElement? = obj[field]
        if (element == null || element is JsonNull) {
            if (required) fail(field, "Обязательное поле")
            return null
        }
        if (element !is JsonPrimitive || !element.isString) {
            fail(field, "Должно быть строкой")
            return null
        }
        return element.content.trim()
    }

    fun integer(field: String): Int? {
        val element = obj[field]
        if (element == null || element is JsonNull) {
            fail(field, "Обязательное поле")
            return null
        }
        val value = (element as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
        if (value == null) fail(field, "Должно быть целым числом")
        return value
    }
}

fun validateMeetingTypeInput(body: JsonElement): Validated<MeetingTypeDraft> {
    if (body !is JsonObject) {
        return Validated.Invalid(listOf(FieldError("body", "Ожидается JSON-объект")))
    }
    val reader = ObjectReader(body)
    reader.rejectUnknown(setOf("title", "description", "durationMinutes"))

    val title = reader.text("title", required = true)
    if (title != null && codePointLength(title) !in Limits.TITLE_MIN..Limits.TITLE_MAX) {
        reader.fail("title", "Название: от ${Limits.TITLE_MIN} до ${Limits.TITLE_MAX} символов")
    }

    val description = reader.text("description", required = false)
    if (description != null && codePointLength(description) > Limits.DESCRIPTION_MAX) {
        reader.fail("description", "Описание: не более ${Limits.DESCRIPTION_MAX} символов")
    }

    val duration = reader.integer("durationMinutes")
    if (duration != null && (duration !in Limits.DURATION_MIN..Limits.DURATION_MAX || duration % Limits.DURATION_STEP != 0)) {
        reader.fail(
            "durationMinutes",
            "Длительность: целое число минут, кратное ${Limits.DURATION_STEP}, от ${Limits.DURATION_MIN} до ${Limits.DURATION_MAX}",
        )
    }

    if (reader.errors.isNotEmpty() || title == null || duration == null) return Validated.Invalid(reader.errors)
    return Validated.Valid(MeetingTypeDraft(title, description?.ifEmpty { null }, duration))
}

/**
 * Simple email format: no whitespace anywhere, exactly one `@`, a non-empty local part and a domain of
 * non-empty labels separated by dots with at least one dot (so no leading, trailing or doubled dot).
 */
fun isSimpleEmail(email: String): Boolean {
    if (email.any { Character.isWhitespace(it) || Character.isSpaceChar(it) }) return false
    val parts = email.split('@')
    if (parts.size != 2 || parts[0].isEmpty()) return false
    val labels = parts[1].split('.')
    return labels.size >= 2 && labels.none { it.isEmpty() }
}

private val CANONICAL_UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

/** Strict UUID parsing: only the canonical 8-4-4-4-12 hex form (`UUID.fromString` also accepts `1-1-1-1-1`). */
fun parseUuid(text: String?): UUID? = text?.takeIf { CANONICAL_UUID.matches(it) }?.let(UUID::fromString)

/** Strict: only an instant written in UTC with a trailing `Z`; offsets are rejected. */
private fun parseUtcInstant(text: String): Instant? =
    if (!text.endsWith("Z")) {
        null
    } else {
        try {
            Instant.parse(text)
        } catch (e: DateTimeException) {
            null
        }
    }

fun validateBookingInput(body: JsonElement): Validated<BookingDraft> {
    if (body !is JsonObject) {
        return Validated.Invalid(listOf(FieldError("body", "Ожидается JSON-объект")))
    }
    val reader = ObjectReader(body)
    reader.rejectUnknown(setOf("meetingTypeId", "startAt", "guestName", "guestEmail"))

    val meetingTypeId =
        reader.text("meetingTypeId", required = true)?.let {
            parseUuid(it) ?: run {
                reader.fail("meetingTypeId", "Должно быть UUID")
                null
            }
        }

    val startAt =
        reader.text("startAt", required = true)?.let {
            parseUtcInstant(it) ?: run {
                reader.fail("startAt", "Время начала: момент в UTC в формате ISO 8601 с завершающим Z")
                null
            }
        }

    val name = reader.text("guestName", required = true)
    if (name != null && codePointLength(name) !in Limits.GUEST_NAME_MIN..Limits.GUEST_NAME_MAX) {
        reader.fail("guestName", "Имя: от ${Limits.GUEST_NAME_MIN} до ${Limits.GUEST_NAME_MAX} символов")
    }

    val email = reader.text("guestEmail", required = true)
    if (email != null && (codePointLength(email) > Limits.EMAIL_MAX || !isSimpleEmail(email))) {
        reader.fail("guestEmail", "Email: адрес вида name@example.com, не более ${Limits.EMAIL_MAX} символов")
    }

    if (reader.errors.isNotEmpty() || meetingTypeId == null || startAt == null || name == null || email == null) {
        return Validated.Invalid(reader.errors)
    }
    return Validated.Valid(BookingDraft(meetingTypeId, startAt, name, email))
}
