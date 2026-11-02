package calendar

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("calendar.Errors")

@Serializable
data class FieldError(
    val field: String,
    val message: String,
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val fieldErrors: List<FieldError>? = null,
)

/** The `code` values of [ApiError] on the wire; keep in sync with the TypeSpec contract and the frontend. */
object ErrorCodes {
    const val MALFORMED_REQUEST = "MALFORMED_REQUEST"
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
    const val MEETING_TYPE_NOT_FOUND = "MEETING_TYPE_NOT_FOUND"
    const val INVALID_SLOT = "INVALID_SLOT"
    const val SLOT_TAKEN = "SLOT_TAKEN"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
    const val FORBIDDEN = "FORBIDDEN"
}

const val MALFORMED_JSON_MESSAGE = "Некорректный JSON в теле запроса"

suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    code: String,
    message: String,
    fieldErrors: List<FieldError>? = null,
) = respond(status, ApiError(code, message, fieldErrors))

suspend fun ApplicationCall.respondMeetingTypeNotFound() =
    respondError(HttpStatusCode.NotFound, ErrorCodes.MEETING_TYPE_NOT_FOUND, "Тип встречи не найден")

/**
 * Parses the request body as JSON. Responds 400 MALFORMED_REQUEST with [malformedMessage] and returns null
 * when it is not JSON; the caller must then stop handling the request.
 */
suspend fun ApplicationCall.receiveJsonOrRespond(malformedMessage: String = MALFORMED_JSON_MESSAGE): JsonElement? =
    try {
        Json.parseToJsonElement(receiveText())
    } catch (e: SerializationException) {
        log.debug("Malformed JSON in request body", e)
        respondError(HttpStatusCode.BadRequest, ErrorCodes.MALFORMED_REQUEST, malformedMessage)
        null
    }

/** The value of a valid result; for an invalid one responds 400 VALIDATION_ERROR and returns null. */
suspend fun <T> ApplicationCall.validOrRespond(result: Validated<T>): T? =
    when (result) {
        is Validated.Valid -> {
            result.value
        }

        is Validated.Invalid -> {
            respondError(HttpStatusCode.BadRequest, ErrorCodes.VALIDATION_ERROR, "Некорректные данные запроса", result.errors)
            null
        }
    }
