package calendar

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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

/** Number of Unicode code points after trimming. */
fun trimmedLength(text: String): Int = text.trim().let { it.codePointCount(0, it.length) }

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
    if (title != null && trimmedLength(title) !in Limits.TITLE_MIN..Limits.TITLE_MAX) {
        reader.fail("title", "Название: от ${Limits.TITLE_MIN} до ${Limits.TITLE_MAX} символов")
    }

    val description = reader.text("description", required = false)
    if (description != null && trimmedLength(description) > Limits.DESCRIPTION_MAX) {
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
