package calendar

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookingsTest {
    // Monday 2026-11-02 08:00 in Asia/Almaty (UTC+5); the first slot starts at 04:00Z (09:00).
    private val clock = Clock.fixed(Instant.parse("2026-11-02T03:00:00Z"), ZoneOffset.UTC)

    private class Env(
        val shortId: UUID,
        val longId: UUID,
        val bookings: InMemoryBookingRepository,
    )

    private fun ApplicationTestBuilder.start(): Env {
        val types = InMemoryMeetingTypeRepository.withInitialTypes()
        val bookings = InMemoryBookingRepository()
        application { module(AppConfig.fromEnvironment(emptyMap()), types, clock, bookings) }
        val (short, long) = types.list()
        return Env(short.id, long.id, bookings)
    }

    private fun body(
        meetingTypeId: Any,
        startAt: String,
        name: String = "Анна",
        email: String = "Anna@Example.com",
    ) = """{"meetingTypeId":"$meetingTypeId","startAt":"$startAt","guestName":"$name","guestEmail":"$email"}"""

    /**
     * Every booking response is checked against the contract here. A 400 answers a request that is invalid on
     * purpose (bad bounds, broken JSON), so for it only the response is validated.
     */
    private suspend fun HttpClient.book(json: String): HttpResponse {
        val response =
            post("/api/bookings") {
                contentType(ContentType.Application.Json)
                setBody(json)
            }
        response.assertMatchesContract(json, validateRequest = response.status != HttpStatusCode.BadRequest)
        return response
    }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private fun JsonObject.text(name: String) = this[name]!!.jsonPrimitive.content

    private suspend fun HttpResponse.assertError(
        status: HttpStatusCode,
        code: String,
        request: String,
    ) {
        assertEquals(status, this.status)
        assertMatchesContract(request, validateRequest = status != HttpStatusCode.BadRequest)
        assertEquals(code, json().text("code"))
    }

    @Test
    fun createsABookingAndReturns201WithTheContractShape() =
        testApplication {
            val env = start()
            val request = body(env.longId, "2026-11-02T04:00:00Z", name = "  Анна  ")

            val response = client.book(request)

            assertEquals(HttpStatusCode.Created, response.status)
            response.assertMatchesContract(request)
            val created = response.json()
            assertEquals(setOf("id", "meetingTypeId", "startAt", "endAt", "guestName", "guestEmail"), created.keys)
            UUID.fromString(created.text("id"))
            assertEquals(env.longId.toString(), created.text("meetingTypeId"))
            assertEquals("2026-11-02T04:00:00Z", created.text("startAt"))
            assertEquals("2026-11-02T04:30:00Z", created.text("endAt"))
            assertEquals("Анна", created.text("guestName"))
            assertEquals("Anna@Example.com", created.text("guestEmail"))
        }

    @Test
    fun oneEmailMayHaveSeveralBookings() =
        testApplication {
            val env = start()

            val first = client.book(body(env.shortId, "2026-11-02T04:00:00Z"))
            val second = client.book(body(env.shortId, "2026-11-02T04:15:00Z"))

            assertEquals(HttpStatusCode.Created, first.status)
            assertEquals(HttpStatusCode.Created, second.status)
            assertEquals(2, env.bookings.list().size)
        }

    @Test
    fun bookedSlotDisappearsFromSlotsWhileTouchingNeighboursStay() =
        testApplication {
            val env = start()
            val before = client.get("/api/meeting-types/${env.shortId}/slots")
            before.assertMatchesContract()
            val beforeStarts = Json.parseToJsonElement(before.bodyAsText()).jsonArray.map { it.jsonObject.text("startAt") }
            assertTrue("2026-11-02T04:15:00Z" in beforeStarts)

            client.book(body(env.shortId, "2026-11-02T04:15:00Z")).also { assertEquals(HttpStatusCode.Created, it.status) }

            val after = client.get("/api/meeting-types/${env.shortId}/slots")
            after.assertMatchesContract()
            val afterStarts = Json.parseToJsonElement(after.bodyAsText()).jsonArray.map { it.jsonObject.text("startAt") }
            assertEquals(beforeStarts - "2026-11-02T04:15:00Z", afterStarts)
            assertTrue("2026-11-02T04:00:00Z" in afterStarts)
            assertTrue("2026-11-02T04:30:00Z" in afterStarts)
        }

    @Test
    fun aBookingOfOneTypeBlocksOverlappingStartsOfAnotherType() =
        testApplication {
            val env = start()
            client.book(body(env.longId, "2026-11-02T04:00:00Z")) // 04:00-04:30

            val overlap = client.book(body(env.shortId, "2026-11-02T04:15:00Z")) // 04:15-04:30
            val touching = client.book(body(env.shortId, "2026-11-02T04:30:00Z")) // 04:30-04:45

            overlap.assertError(HttpStatusCode.Conflict, "SLOT_TAKEN", body(env.shortId, "2026-11-02T04:15:00Z"))
            assertEquals(HttpStatusCode.Created, touching.status)
        }

    @Test
    fun slotTakenHasTheAgreedBody() =
        testApplication {
            val env = start()
            val request = body(env.shortId, "2026-11-02T04:00:00Z")
            client.book(request)

            val response = client.book(request)

            response.assertError(HttpStatusCode.Conflict, "SLOT_TAKEN", request)
            assertEquals(setOf("code", "message"), response.json().keys)
        }

    @Test
    fun startsTheScheduleNeverOffersAreInvalidSlot() =
        testApplication {
            val env = start()
            val starts =
                listOf(
                    "2026-11-02T04:10:00Z", // off the 15-minute grid
                    "2026-11-02T04:00:30Z", // seconds
                    "2026-11-02T02:00:00Z", // 07:00 local, before the schedule
                    "2026-11-02T07:00:00Z", // 12:00 local, the lunch break
                    "2026-11-02T12:45:00Z", // 17:45 local, a 30 minute meeting would end after 18:00
                    "2026-11-07T04:00:00Z", // Saturday
                    "2026-11-02T03:00:00Z", // now (not strictly later)
                    "2026-11-01T04:00:00Z", // the past
                    "2026-11-16T04:00:00Z", // beyond the 14 day horizon
                )
            for (startAt in starts) {
                val request = body(env.longId, startAt)
                client.book(request).assertError(HttpStatusCode.UnprocessableEntity, "INVALID_SLOT", request)
            }
            assertTrue(env.bookings.list().isEmpty())
        }

    @Test
    fun aSlotOutsideTheScheduleIsInvalidEvenWhenNothingElseIsWrong() =
        testApplication {
            val env = start()
            val request = body(env.shortId, "2026-11-02T04:10:00Z")

            val response = client.book(request)

            response.assertError(HttpStatusCode.UnprocessableEntity, "INVALID_SLOT", request)
            assertEquals(setOf("code", "message"), response.json().keys)
        }

    @Test
    fun unknownMeetingTypeIsNotFound() =
        testApplication {
            start()
            val request = body(UUID.randomUUID(), "2026-11-02T04:00:00Z")

            client.book(request).assertError(HttpStatusCode.NotFound, "MEETING_TYPE_NOT_FOUND", request)
        }

    @Test
    fun validationErrorsGive400WithFieldErrors() =
        testApplication {
            start()
            val request = """{"meetingTypeId":"not-a-uuid","startAt":"2026-11-02T09:00:00+05:00","guestName":"  ","guestEmail":"a@b"}"""

            val response = client.book(request)

            response.assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR", request)
            val fields = response.json()["fieldErrors"]!!.jsonArray.map { it.jsonObject.text("field") }
            assertEquals(listOf("meetingTypeId", "startAt", "guestName", "guestEmail"), fields)
        }

    @Test
    fun clientEndAtAndUnknownFieldsAreRejected() =
        testApplication {
            val env = start()
            val withEnd =
                """{"meetingTypeId":"${env.shortId}","startAt":"2026-11-02T04:00:00Z","endAt":"2026-11-02T04:15:00Z",""" +
                    """"guestName":"Анна","guestEmail":"a@b.c","extra":1}"""

            val response = client.book(withEnd)

            response.assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR", withEnd)
            val fields = response.json()["fieldErrors"]!!.jsonArray.map { it.jsonObject.text("field") }
            assertEquals(listOf("endAt", "extra"), fields)
            assertTrue(env.bookings.list().isEmpty())
        }

    @Test
    fun malformedJsonIs400() =
        testApplication {
            start()

            client.book("{nope").assertError(HttpStatusCode.BadRequest, "MALFORMED_REQUEST", "{nope")
        }

    @Test
    fun checksRunInTheOrder400Then404Then422Then409() =
        testApplication {
            val env = start()
            val unknown = UUID.randomUUID()
            client.book(body(env.shortId, "2026-11-02T04:00:00Z")) // takes 04:00

            // 400 beats 404 and 422.
            val bad = body(unknown, "2026-11-02T04:10:00Z", name = "")
            client.book(bad).assertError(HttpStatusCode.BadRequest, "VALIDATION_ERROR", bad)
            // 404 beats 422.
            val missing = body(unknown, "2026-11-02T04:10:00Z")
            client.book(missing).assertError(HttpStatusCode.NotFound, "MEETING_TYPE_NOT_FOUND", missing)
            // 422 beats 409: 04:10 is off the grid although it overlaps the taken 04:00-04:15.
            val offGrid = body(env.shortId, "2026-11-02T04:10:00Z")
            client.book(offGrid).assertError(HttpStatusCode.UnprocessableEntity, "INVALID_SLOT", offGrid)
            // Finally 409.
            val taken = body(env.shortId, "2026-11-02T04:00:00Z")
            client.book(taken).assertError(HttpStatusCode.Conflict, "SLOT_TAKEN", taken)
        }

    @Test
    fun parallelHttpRequestsForTheSameSlotCreateExactlyOneBooking() =
        testApplication {
            val env = start()
            val request = body(env.shortId, "2026-11-02T04:00:00Z")

            val statuses =
                coroutineScope { (1..PARALLEL).map { async(Dispatchers.Default) { client.book(request).status } }.awaitAll() }

            assertEquals(1, statuses.count { it == HttpStatusCode.Created })
            assertEquals(PARALLEL - 1, statuses.count { it == HttpStatusCode.Conflict })
            assertEquals(1, env.bookings.list().size)
        }

    @Test
    fun parallelOverlappingRequestsOfDifferentStartsAndTypesCreateAtMostOneBooking() =
        testApplication {
            val env = start()
            // All of these overlap pairwise around 04:15-04:30: 30 min from 04:00, 15 min from 04:15, 30 min from 04:15,
            // 30 min from 04:30 does not overlap the 15 min one but overlaps the 04:15 30 min one.
            val requests =
                listOf(
                    body(env.longId, "2026-11-02T04:00:00Z"), // 04:00-04:30
                    body(env.shortId, "2026-11-02T04:15:00Z"), // 04:15-04:30
                    body(env.longId, "2026-11-02T04:15:00Z"), // 04:15-04:45
                    body(env.longId, "2026-11-02T04:00:00Z"),
                    body(env.shortId, "2026-11-02T04:15:00Z"),
                    body(env.longId, "2026-11-02T04:15:00Z"),
                )

            // Every request overlaps 04:15-04:30, so at most one may win.
            val statuses = coroutineScope { requests.map { async(Dispatchers.Default) { client.book(it).status } }.awaitAll() }

            assertEquals(1, statuses.count { it == HttpStatusCode.Created })
            assertEquals(statuses.size - 1, statuses.count { it == HttpStatusCode.Conflict })
            val stored = env.bookings.list()
            assertEquals(1, stored.size)
        }

    @Test
    fun serviceLevelParallelRequestsForOneSlotCreateExactlyOneBooking() {
        val types = InMemoryMeetingTypeRepository.withInitialTypes()
        val bookings = InMemoryBookingRepository()
        val service = BookingService(clock, AppConfig.fromEnvironment(emptyMap()).schedule, bookings)
        val type = types.list().first()

        val outcomes = runInParallel(PARALLEL) { service.create(type, Instant.parse("2026-11-02T04:00:00Z"), "Анна", "a@b.c") }

        assertEquals(1, outcomes.count { it is BookingOutcome.Created })
        assertEquals(PARALLEL - 1, outcomes.count { it == BookingOutcome.SlotTaken })
        assertEquals(1, bookings.list().size)
    }

    @Test
    fun serviceLevelOverlappingBookingsOfDifferentTypesNeverBothSucceed() {
        val types = InMemoryMeetingTypeRepository.withInitialTypes()
        val (short, long) = types.list()
        val bookings = InMemoryBookingRepository()
        val service = BookingService(clock, AppConfig.fromEnvironment(emptyMap()).schedule, bookings)
        val attempts =
            listOf(
                long to "2026-11-02T04:00:00Z", // 04:00-04:30
                short to "2026-11-02T04:15:00Z", // 04:15-04:30
                long to "2026-11-02T04:15:00Z", // 04:15-04:45
                short to "2026-11-02T04:00:00Z", // 04:00-04:15 overlaps only the first
            )

        val outcomes =
            runInParallel(attempts.size * 8) { index ->
                val (type, start) = attempts[index % attempts.size]
                service.create(type, Instant.parse(start), "Анна", "a@b.c")
            }

        val stored = bookings.busyRanges().sortedBy { it.start }
        assertEquals(outcomes.count { it is BookingOutcome.Created }, stored.size)
        stored.zipWithNext().forEach { (a, b) -> assertTrue(a.end <= b.start, "overlap: $a and $b") }
        assertTrue(stored.isNotEmpty())
    }

    /** Starts [count] threads at the same instant (latch) and collects their results. */
    private fun <T> runInParallel(
        count: Int,
        action: (Int) -> T,
    ): List<T> {
        val ready = java.util.concurrent.CountDownLatch(count)
        val go = java.util.concurrent.CountDownLatch(1)
        val pool =
            java.util.concurrent.Executors
                .newFixedThreadPool(count)
        try {
            val futures =
                (0 until count).map { index ->
                    pool.submit<T> {
                        ready.countDown()
                        go.await()
                        action(index)
                    }
                }
            ready.await()
            go.countDown()
            return futures.map { it.get() }
        } finally {
            pool.shutdownNow()
        }
    }

    private companion object {
        const val PARALLEL = 24
    }
}
