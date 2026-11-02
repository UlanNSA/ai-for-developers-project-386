package calendar

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeParseException

private val log = LoggerFactory.getLogger("calendar.Application")

/**
 * Custom header every test-mode request must carry. A web page cannot add it to a cross-origin "simple"
 * request, so a page the developer visits cannot reset the state or move the clock.
 */
const val TEST_REQUEST_HEADER = "X-Calendar-Test"

private const val CLOCK_REQUEST_MESSAGE = "Ожидается JSON {\"now\": \"<ISO-8601 UTC instant>\"}"

/** Responds 403 and returns false when the test-mode header is missing. */
private suspend fun ApplicationCall.requireTestHeader(): Boolean {
    if (request.headers[TEST_REQUEST_HEADER] != null) return true
    respondError(HttpStatusCode.Forbidden, ErrorCodes.FORBIDDEN, "Test routes require the $TEST_REQUEST_HEADER header")
    return false
}

/** A malformed id is indistinguishable from an unknown one for the client. */
private fun MeetingTypeRepository.findByParameter(raw: String?): MeetingType? = parseUuid(raw)?.let { find(it) }

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
            call.respondError(HttpStatusCode.InternalServerError, ErrorCodes.INTERNAL_ERROR, "Внутренняя ошибка сервера")
        }
    }
    routing {
        config.testMode?.let { testMode ->
            // Not part of the API contract: state isolation and time control for browser E2E tests.
            post("/__test__/reset") {
                if (!call.requireTestHeader()) return@post
                listOf(meetingTypes, bookings, testMode.clock).filterIsInstance<Resettable>().forEach { it.reset() }
                call.respond(HttpStatusCode.NoContent)
            }
            post("/__test__/clock") {
                if (!call.requireTestHeader()) return@post
                val body = call.receiveJsonOrRespond(CLOCK_REQUEST_MESSAGE) ?: return@post
                val text = (body as? JsonObject)?.get("now")
                val now =
                    try {
                        (text as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(Instant::parse)
                    } catch (e: DateTimeParseException) {
                        log.debug("Invalid instant in clock request", e)
                        null
                    }
                if (now == null) {
                    call.respondError(HttpStatusCode.BadRequest, ErrorCodes.MALFORMED_REQUEST, CLOCK_REQUEST_MESSAGE)
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
            val body = call.receiveJsonOrRespond() ?: return@post
            val draft = call.validOrRespond(validateMeetingTypeInput(body)) ?: return@post
            val created = meetingTypes.add(draft.title, draft.description, draft.durationMinutes)
            call.respond(HttpStatusCode.Created, created.toResponse())
        }
        get("/api/bookings") {
            call.respond(bookingService.upcoming().map { it.toResponse() })
        }
        post("/api/bookings") {
            val body = call.receiveJsonOrRespond() ?: return@post
            val draft = call.validOrRespond(validateBookingInput(body)) ?: return@post
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
                    call.respondError(HttpStatusCode.UnprocessableEntity, ErrorCodes.INVALID_SLOT, "Это время недоступно для записи")
                }

                BookingOutcome.SlotTaken -> {
                    call.respondError(HttpStatusCode.Conflict, ErrorCodes.SLOT_TAKEN, "Это время уже занято")
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
                call.respond(slots.map { it.toResponse() })
            }
        }
        // The built React app: files as they are, every other path gets index.html so client routes
        // (/owner, /book/:id) survive a direct visit or refresh.
        config.staticDir?.let { dir ->
            // /api stays API-only: an unknown API path is a plain 404, never the index page. These routes are
            // more specific than the SPA catch-all, while the exact API routes above are more specific still.
            route("/api") { handle { call.respond(HttpStatusCode.NotFound) } }
            route("/api/{...}") { handle { call.respond(HttpStatusCode.NotFound) } }
            singlePageApplication {
                filesPath = dir
                defaultPage = "index.html"
            }
        }
    }
}
