package calendar

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

@Serializable
data class HealthResponse(
    val status: String,
)

@Serializable
data class OwnerResponse(
    val name: String,
    val bio: String,
    val timeZone: String,
)

@Serializable
data class MeetingTypeResponse(
    val id: String,
    val title: String,
    val description: String? = null,
    val durationMinutes: Int,
)

@Serializable
data class BookingResponse(
    val id: String,
    val meetingTypeId: String,
    val startAt: String,
    val endAt: String,
    val guestName: String,
    val guestEmail: String,
)

private fun Booking.toResponse() =
    BookingResponse(id.toString(), meetingTypeId.toString(), startAt.toString(), endAt.toString(), guestName, guestEmail)

private fun MeetingType.toResponse() = MeetingTypeResponse(id.toString(), title, description, durationMinutes)

/** Both instants are UTC ISO 8601 with a trailing `Z`. */
@Serializable
data class SlotResponse(
    val startAt: String,
    val endAt: String,
)

/** A malformed id is indistinguishable from an unknown one for the client. */
private fun MeetingTypeRepository.findByParameter(raw: String?): MeetingType? =
    runCatching { UUID.fromString(raw) }.getOrNull()?.let { find(it) }

private suspend fun ApplicationCall.respondMeetingTypeNotFound() =
    respond(HttpStatusCode.NotFound, ApiError(code = "MEETING_TYPE_NOT_FOUND", message = "Тип встречи не найден"))

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

private val log = LoggerFactory.getLogger("calendar.Application")

fun main() {
    val config = AppConfig.fromEnvironment()
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, host = config.bindHost, port = port) { module(config) }.start(wait = true)
}

@OptIn(ExperimentalSerializationApi::class)
fun Application.module(
    config: AppConfig,
    meetingTypes: MeetingTypeRepository = InMemoryMeetingTypeRepository.withInitialTypes(),
    clock: Clock = config.clock,
    bookings: BookingRepository = InMemoryBookingRepository(),
    // Slot calculation reads busy time from here; by default the bookings themselves (tests may override).
    busyTimes: BusyTimeSource = bookings,
) {
    val bookingService = BookingService(clock, config.schedule, bookings)
    install(ContentNegotiation) {
        // Optional contract fields (e.g. ApiError.fieldErrors) must be absent, not null.
        json(Json { explicitNulls = false })
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "INTERNAL_ERROR", message = "Внутренняя ошибка сервера"),
            )
        }
    }
    routing {
        config.testMode?.let { testMode ->
            // Not part of the API contract: state isolation and time control for browser E2E tests.
            post("/__test__/reset") {
                listOf(meetingTypes, bookings, testMode.clock).filterIsInstance<Resettable>().forEach { it.reset() }
                call.respond(HttpStatusCode.NoContent)
            }
            post("/__test__/clock") {
                val now =
                    try {
                        val text = (Json.parseToJsonElement(call.receiveText()) as? JsonObject)?.get("now")
                        (text as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(Instant::parse)
                    } catch (e: SerializationException) {
                        log.debug("Malformed JSON in clock request", e)
                        null
                    } catch (e: DateTimeParseException) {
                        log.debug("Invalid instant in clock request", e)
                        null
                    }
                if (now == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "MALFORMED_REQUEST", message = "Ожидается JSON {\"now\": \"<ISO-8601 UTC instant>\"}"),
                    )
                } else {
                    testMode.clock.moveTo(now)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
        get("/api/health") {
            call.respond(HealthResponse(status = "ok"))
        }
        get("/api/owner") {
            val owner = config.owner
            call.respond(OwnerResponse(name = owner.name, bio = owner.bio, timeZone = owner.timeZone))
        }
        get("/api/meeting-types") {
            call.respond(meetingTypes.list().map { it.toResponse() })
        }
        post("/api/meeting-types") {
            val body =
                try {
                    Json.parseToJsonElement(call.receiveText())
                } catch (e: SerializationException) {
                    log.debug("Malformed JSON in request body", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "MALFORMED_REQUEST", message = "Некорректный JSON в теле запроса"),
                    )
                    return@post
                }
            when (val result = validateMeetingTypeInput(body)) {
                is Validated.Invalid -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            code = "VALIDATION_ERROR",
                            message = "Некорректные данные запроса",
                            fieldErrors = result.errors,
                        ),
                    )
                }

                is Validated.Valid -> {
                    val draft = result.value
                    val created = meetingTypes.add(draft.title, draft.description, draft.durationMinutes)
                    call.respond(HttpStatusCode.Created, created.toResponse())
                }
            }
        }
        get("/api/bookings") {
            val now = clock.instant()
            val upcoming = bookings.list().filter { it.startAt > now }.sortedBy { it.startAt }
            call.respond(upcoming.map { it.toResponse() })
        }
        post("/api/bookings") {
            val body =
                try {
                    Json.parseToJsonElement(call.receiveText())
                } catch (e: SerializationException) {
                    log.debug("Malformed JSON in request body", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "MALFORMED_REQUEST", message = "Некорректный JSON в теле запроса"),
                    )
                    return@post
                }
            val draft =
                when (val result = validateBookingInput(body)) {
                    is Validated.Invalid -> {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiError(
                                code = "VALIDATION_ERROR",
                                message = "Некорректные данные запроса",
                                fieldErrors = result.errors,
                            ),
                        )
                        return@post
                    }

                    is Validated.Valid -> {
                        result.value
                    }
                }
            val meetingType = meetingTypes.find(draft.meetingTypeId)
            if (meetingType == null) {
                call.respondMeetingTypeNotFound()
                return@post
            }
            when (val outcome = bookingService.create(meetingType, draft.startAt, draft.guestName, draft.guestEmail)) {
                is BookingOutcome.Created -> {
                    call.respond(HttpStatusCode.Created, outcome.booking.toResponse())
                }

                BookingOutcome.InvalidSlot -> {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiError(code = "INVALID_SLOT", message = "Это время недоступно для записи"),
                    )
                }

                BookingOutcome.SlotTaken -> {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiError(code = "SLOT_TAKEN", message = "Это время уже занято"),
                    )
                }
            }
        }
        get("/api/meeting-types/{id}") {
            val meetingType = meetingTypes.findByParameter(call.parameters["id"])
            if (meetingType == null) {
                call.respondMeetingTypeNotFound()
            } else {
                call.respond(meetingType.toResponse())
            }
        }
        get("/api/meeting-types/{id}/slots") {
            val meetingType = meetingTypes.findByParameter(call.parameters["id"])
            if (meetingType == null) {
                call.respondMeetingTypeNotFound()
            } else {
                val slots =
                    SlotCalculator.calculate(clock, config.schedule, meetingType.durationMinutes, busyTimes.busyRanges())
                call.respond(slots.map { SlotResponse(it.startAt.toString(), it.endAt.toString()) })
            }
        }
    }
}
