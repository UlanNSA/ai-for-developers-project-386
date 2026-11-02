package calendar

import io.ktor.client.request.get
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

            val reset = client.post("/__test__/reset")

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
