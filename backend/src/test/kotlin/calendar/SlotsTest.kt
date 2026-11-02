package calendar

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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

class SlotsTest {
    // Monday 2026-11-02 08:00 in Asia/Almaty (UTC+5).
    private val clock = Clock.fixed(Instant.parse("2026-11-02T03:00:00Z"), ZoneOffset.UTC)
    private val notFoundBody = """{"code":"MEETING_TYPE_NOT_FOUND","message":"Тип встречи не найден"}"""

    private fun ApplicationTestBuilder.start(busy: List<TimeRange> = emptyList()): Pair<UUID, UUID> {
        val repository = InMemoryMeetingTypeRepository.withInitialTypes()
        application {
            module(AppConfig.fromEnvironment(emptyMap()), repository, clock) { busy }
        }
        val (short, long) = repository.list()
        return short.id to long.id
    }

    private suspend fun ApplicationTestBuilder.slots(id: Any): JsonArray {
        val response = client.get("/api/meeting-types/$id/slots")
        assertEquals(HttpStatusCode.OK, response.status)
        response.assertMatchesContract()
        return Json.parseToJsonElement(response.bodyAsText()).jsonArray
    }

    private fun JsonArray.starts() = map { it.jsonObject["startAt"]!!.jsonPrimitive.content }

    @Test
    fun returnsUtcSlotsWithZSortedByStartInTheContractShape() =
        testApplication {
            val (shortId, _) = start()

            val items = slots(shortId)

            assertEquals(320, items.size)
            assertEquals(setOf("startAt", "endAt"), items.first().jsonObject.keys)
            assertEquals(
                "2026-11-02T04:00:00Z",
                items
                    .first()
                    .jsonObject["startAt"]!!
                    .jsonPrimitive.content,
            )
            assertEquals(
                "2026-11-02T04:15:00Z",
                items
                    .first()
                    .jsonObject["endAt"]!!
                    .jsonPrimitive.content,
            )
            assertTrue(items.starts().all { it.endsWith("Z") })
            assertEquals(items.starts().sorted(), items.starts())
        }

    @Test
    fun endIsStartPlusTheDurationOfTheMeetingType() =
        testApplication {
            val (_, longId) = start()

            val first = slots(longId).first().jsonObject

            assertEquals("2026-11-02T04:00:00Z", first["startAt"]!!.jsonPrimitive.content)
            assertEquals("2026-11-02T04:30:00Z", first["endAt"]!!.jsonPrimitive.content)
        }

    @Test
    fun existingBookingsRemoveOverlappingSlots() =
        testApplication {
            val busy = listOf(TimeRange(Instant.parse("2026-11-02T04:00:00Z"), Instant.parse("2026-11-02T05:00:00Z")))
            val (shortId, _) = start(busy)

            val items = slots(shortId)

            assertEquals(
                "2026-11-02T05:00:00Z",
                items
                    .first()
                    .jsonObject["startAt"]!!
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun unknownMeetingTypeIsNotFoundByContract() =
        testApplication {
            start()

            val response = client.get("/api/meeting-types/${UUID.randomUUID()}/slots")

            assertEquals(HttpStatusCode.NotFound, response.status)
            response.assertMatchesContract()
            assertEquals(notFoundBody, response.bodyAsText())
        }

    @Test
    fun malformedMeetingTypeIdIsNotFoundByContract() =
        testApplication {
            start()

            val response = client.get("/api/meeting-types/not-a-uuid/slots")

            assertEquals(HttpStatusCode.NotFound, response.status)
            response.assertMatchesContract()
            assertEquals(notFoundBody, response.bodyAsText())
        }
}
