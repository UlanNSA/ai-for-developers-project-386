package calendar

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TEST_HEADER = "X-Calendar-Test"

class TestModeTest {
    private val testEnv = mapOf("CALENDAR_TEST_MODE" to "true", "CALENDAR_FIXED_NOW" to "2026-11-02T06:00:00Z")

    private suspend fun ApplicationTestBuilder.typeTitles(): List<String> =
        Json
            .parseToJsonElement(client.get("/api/meeting-types").bodyAsText())
            .jsonArray
            .map { it.jsonObject["title"]!!.jsonPrimitive.content }

    @Test
    fun resetRouteDoesNotExistInNormalMode() =
        testApplication {
            application { module(AppConfig.fromEnvironment(emptyMap())) }

            assertEquals(HttpStatusCode.NotFound, client.post("/__test__/reset").status)
        }

    @Test
    fun resetRouteIsNotInTheContract() {
        val contract = java.io.File(requireNotNull(System.getProperty("openapi.spec"))).readText()

        assertTrue("__test__" !in contract)
    }

    @Test
    fun resetRestoresSeededTypesAndClearsBookings() =
        testApplication {
            application { module(AppConfig.fromEnvironment(testEnv)) }
            val types = Json.parseToJsonElement(client.get("/api/meeting-types").bodyAsText()).jsonArray
            val typeId =
                types
                    .first()
                    .jsonObject["id"]!!
                    .jsonPrimitive.content
            val slots = Json.parseToJsonElement(client.get("/api/meeting-types/$typeId/slots").bodyAsText()).jsonArray
            val slotStart =
                slots
                    .first()
                    .jsonObject["startAt"]!!
                    .jsonPrimitive.content
            val booked =
                client.post("/api/bookings") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"meetingTypeId":"$typeId","startAt":"$slotStart","guestName":"Гость","guestEmail":"g@example.com"}""")
                }
            assertEquals(HttpStatusCode.Created, booked.status)
            val created =
                client.post("/api/meeting-types") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"title":"Лишний","durationMinutes":45}""")
                }
            assertEquals(HttpStatusCode.Created, created.status)
            assertEquals(3, typeTitles().size)
            assertEquals(1, Json.parseToJsonElement(client.get("/api/bookings").bodyAsText()).jsonArray.size)

            val reset = resetState()

            assertEquals(HttpStatusCode.NoContent, reset.status)
            assertEquals(listOf("Знакомство", "Консультация"), typeTitles())
            assertEquals(0, Json.parseToJsonElement(client.get("/api/bookings").bodyAsText()).jsonArray.size)
            // Seeded types get fresh ids; the earlier booking no longer blocks the first slot.
            val newTypeId =
                Json
                    .parseToJsonElement(client.get("/api/meeting-types").bodyAsText())
                    .jsonArray
                    .first()
                    .jsonObject["id"]!!
                    .jsonPrimitive.content
            val slotsAfter =
                Json.parseToJsonElement(client.get("/api/meeting-types/$newTypeId/slots").bodyAsText()).jsonArray
            assertEquals(
                slotStart,
                slotsAfter
                    .first()
                    .jsonObject["startAt"]!!
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun fixedNowIsAppliedToSlots() =
        testApplication {
            // Monday 2026-11-02 11:00 in Asia/Almaty (UTC+5); the Owner works 09:00-18:00.
            application { module(AppConfig.fromEnvironment(testEnv)) }
            val typeId =
                Json
                    .parseToJsonElement(client.get("/api/meeting-types").bodyAsText())
                    .jsonArray
                    .first()
                    .jsonObject["id"]!!
                    .jsonPrimitive.content

            val slots = Json.parseToJsonElement(client.get("/api/meeting-types/$typeId/slots").bodyAsText()).jsonArray

            assertEquals(
                "2026-11-02T06:15:00Z",
                slots
                    .first()
                    .jsonObject["startAt"]!!
                    .jsonPrimitive.content,
            )
        }

    private suspend fun ApplicationTestBuilder.firstTypeId(): String =
        Json
            .parseToJsonElement(client.get("/api/meeting-types").bodyAsText())
            .jsonArray
            .first()
            .jsonObject["id"]!!
            .jsonPrimitive.content

    private suspend fun ApplicationTestBuilder.firstSlotStart(): String =
        Json
            .parseToJsonElement(client.get("/api/meeting-types/${firstTypeId()}/slots").bodyAsText())
            .jsonArray
            .first()
            .jsonObject["startAt"]!!
            .jsonPrimitive.content

    private suspend fun ApplicationTestBuilder.setClock(
        body: String,
        withHeader: Boolean = true,
    ) = client.post("/__test__/clock") {
        if (withHeader) header(TEST_HEADER, "1")
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun ApplicationTestBuilder.resetState(withHeader: Boolean = true) =
        client.post("/__test__/reset") {
            if (withHeader) header(TEST_HEADER, "1")
        }

    @Test
    fun testRoutesRejectRequestsWithoutTheCustomHeader() =
        testApplication {
            application { module(AppConfig.fromEnvironment(testEnv)) }
            val before = typeTitles()
            client.post("/api/meeting-types") {
                contentType(ContentType.Application.Json)
                setBody("""{"title":"Лишний","durationMinutes":45}""")
            }

            // A cross-origin "simple" POST from a web page cannot add a custom header, so it is refused.
            val reset = resetState(withHeader = false)
            val clock = setClock("""{"now":"2026-11-03T06:00:00Z"}""", withHeader = false)

            assertEquals(HttpStatusCode.Forbidden, reset.status)
            assertEquals(HttpStatusCode.Forbidden, clock.status)
            assertTrue(reset.bodyAsText().contains(TEST_HEADER))
            assertEquals(before.size + 1, typeTitles().size)
            assertEquals("2026-11-02T06:15:00Z", firstSlotStart())
        }

    @Test
    fun testRoutesAreStillMissingInNormalModeEvenWithTheHeader() =
        testApplication {
            application { module(AppConfig.fromEnvironment(emptyMap())) }

            assertEquals(HttpStatusCode.NotFound, resetState().status)
            assertEquals(HttpStatusCode.NotFound, setClock("""{"now":"2026-11-03T06:00:00Z"}""").status)
        }

    @Test
    fun clockRouteDoesNotExistInNormalMode() =
        testApplication {
            application { module(AppConfig.fromEnvironment(emptyMap())) }

            assertEquals(HttpStatusCode.NotFound, setClock("""{"now":"2026-11-03T06:00:00Z"}""").status)
        }

    @Test
    fun clockRouteIsNotInTheContract() {
        val contract = java.io.File(requireNotNull(System.getProperty("openapi.spec"))).readText()

        assertTrue("/clock" !in contract)
    }

    @Test
    fun clockRouteMovesTheFixedNowAndResetRestoresIt() =
        testApplication {
            application { module(AppConfig.fromEnvironment(testEnv)) }
            assertEquals("2026-11-02T06:15:00Z", firstSlotStart())

            // Tuesday 2026-11-03 11:00 Almaty: the first slot is 11:15 that day.
            assertEquals(HttpStatusCode.NoContent, setClock("""{"now":"2026-11-03T06:00:00Z"}""").status)
            assertEquals("2026-11-03T06:15:00Z", firstSlotStart())

            assertEquals(HttpStatusCode.NoContent, resetState().status)
            assertEquals("2026-11-02T06:15:00Z", firstSlotStart())
        }

    @Test
    fun clockRouteRejectsInvalidInputAndKeepsTheClock() =
        testApplication {
            application { module(AppConfig.fromEnvironment(testEnv)) }

            listOf("""{"now":"yesterday"}""", """{}""", """{"now":42}""", "not json").forEach { body ->
                assertEquals(HttpStatusCode.BadRequest, setClock(body).status, body)
            }
            assertEquals("2026-11-02T06:15:00Z", firstSlotStart())
        }

    @Test
    fun movedClockHidesAStartedBookingFromTheOwnersList() =
        testApplication {
            application { module(AppConfig.fromEnvironment(testEnv)) }
            val start = firstSlotStart()
            client.post("/api/bookings") {
                contentType(ContentType.Application.Json)
                setBody("""{"meetingTypeId":"${firstTypeId()}","startAt":"$start","guestName":"Гость","guestEmail":"g@example.com"}""")
            }
            assertEquals(1, Json.parseToJsonElement(client.get("/api/bookings").bodyAsText()).jsonArray.size)

            setClock("""{"now":"$start"}""")

            assertEquals(0, Json.parseToJsonElement(client.get("/api/bookings").bodyAsText()).jsonArray.size)
        }

    @Test
    fun testModeBindsToLoopbackOnly() {
        assertEquals("127.0.0.1", AppConfig.fromEnvironment(testEnv).bindHost)
        assertEquals("0.0.0.0", AppConfig.fromEnvironment(emptyMap()).bindHost)
    }

    @Test
    fun normalModeHasNoFixedClock() {
        assertNull(AppConfig.fromEnvironment(emptyMap()).testMode)
        assertNull(AppConfig.fromEnvironment(mapOf("CALENDAR_TEST_MODE" to "false")).testMode)
    }

    @Test
    fun testModeWithoutFixedNowFailsFast() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                AppConfig.fromEnvironment(mapOf("CALENDAR_TEST_MODE" to "true"))
            }

        assertTrue(error.message!!.contains("CALENDAR_FIXED_NOW"))
    }

    @Test
    fun testModeWithInvalidFixedNowFailsFast() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                AppConfig.fromEnvironment(mapOf("CALENDAR_TEST_MODE" to "true", "CALENDAR_FIXED_NOW" to "yesterday"))
            }

        assertTrue(error.message!!.contains("CALENDAR_FIXED_NOW"))
        assertTrue(error.message!!.contains("yesterday"))
    }

    @Test
    fun fixedNowWithoutTestModeIsRejected() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                AppConfig.fromEnvironment(mapOf("CALENDAR_FIXED_NOW" to "2026-11-02T06:00:00Z"))
            }

        assertTrue(error.message!!.contains("CALENDAR_TEST_MODE"))
    }
}
