package calendar

import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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

class ListBookingsTest {
    private val now = Instant.parse("2026-11-02T06:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val shortId = UUID.randomUUID()
    private val longId = UUID.randomUUID()

    private fun booking(
        typeId: UUID,
        start: String,
        minutes: Long = 15,
        guest: String = "Гость",
    ): Booking {
        val startAt = Instant.parse(start)
        return Booking(UUID.randomUUID(), typeId, startAt, startAt.plusSeconds(minutes * 60), guest, "g@example.com")
    }

    private fun ApplicationTestBuilder.start(vararg stored: Booking) {
        val repository = InMemoryBookingRepository()
        // Insertion order is deliberately not chronological.
        stored.forEach { b -> repository.inCriticalSection { it.add(b) } }
        application {
            module(AppConfig.fromEnvironment(emptyMap()), InMemoryMeetingTypeRepository.withInitialTypes(), clock, repository)
        }
    }

    private suspend fun HttpResponse.items(): JsonArray {
        assertEquals(HttpStatusCode.OK, status)
        assertMatchesContract()
        return Json.parseToJsonElement(bodyAsText()).jsonArray
    }

    private fun JsonArray.starts() = map { it.jsonObject["startAt"]!!.jsonPrimitive.content }

    @Test
    fun noBookingsGivesAnEmptyArray() =
        testApplication {
            start()

            val response = client.get("/api/bookings")

            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
            assertEquals(0, response.items().size)
        }

    @Test
    fun returnsOnlyBookingsStartingStrictlyAfterNowAscendingAcrossMeetingTypes() =
        testApplication {
            start(
                booking(longId, "2026-11-04T04:00:00Z", 30),
                booking(shortId, "2026-11-02T05:00:00Z"),
                booking(shortId, "2026-11-02T05:45:00Z", 30),
                booking(shortId, "2026-11-02T06:00:00Z"),
                booking(shortId, "2026-11-02T06:00:01Z"),
                booking(longId, "2026-11-03T04:00:00Z", 30),
            )

            val items = client.get("/api/bookings").items()

            assertEquals(
                listOf("2026-11-02T06:00:01Z", "2026-11-03T04:00:00Z", "2026-11-04T04:00:00Z"),
                items.starts(),
            )
            assertEquals(
                listOf(shortId, longId, longId).map { it.toString() },
                items.map { it.jsonObject["meetingTypeId"]!!.jsonPrimitive.content },
            )
        }

    @Test
    fun anAlreadyStartedMeetingIsExcludedEvenIfItIsStillRunning() =
        testApplication {
            start(booking(longId, "2026-11-02T05:45:00Z", 30), booking(longId, "2026-11-02T06:00:00Z", 30))

            assertEquals(0, client.get("/api/bookings").items().size)
        }

    @Test
    fun itemsHaveTheContractShape() =
        testApplication {
            val stored = booking(longId, "2026-11-03T04:00:00Z", 30, guest = "Анна")
            start(stored)

            val item =
                client
                    .get("/api/bookings")
                    .items()
                    .single()
                    .jsonObject

            assertEquals(setOf("id", "meetingTypeId", "startAt", "endAt", "guestName", "guestEmail"), item.keys)
            assertEquals(stored.id.toString(), item["id"]!!.jsonPrimitive.content)
            assertEquals("2026-11-03T04:30:00Z", item["endAt"]!!.jsonPrimitive.content)
            assertEquals("Анна", item["guestName"]!!.jsonPrimitive.content)
            assertEquals("g@example.com", item["guestEmail"]!!.jsonPrimitive.content)
        }
}
