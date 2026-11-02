package calendar

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MeetingTypesTest {
    private val notFoundBody = """{"code":"MEETING_TYPE_NOT_FOUND","message":"Тип встречи не найден"}"""

    private fun io.ktor.server.testing.ApplicationTestBuilder.startDefault() {
        application { module(AppConfig.fromEnvironment(emptyMap())) }
    }

    @Test
    fun listReturnsTwoSeededTypesOf15And30MinutesInCreationOrder() =
        testApplication {
            startDefault()

            val response = client.get("/api/meeting-types")

            assertEquals(HttpStatusCode.OK, response.status)
            response.assertMatchesContract()
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
            val items: JsonArray = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            assertEquals(
                listOf(15, 30),
                items.map {
                    it.jsonObject["durationMinutes"]!!
                        .jsonPrimitive.content
                        .toInt()
                },
            )
        }

    @Test
    fun listItemsHaveContractShapeWithUuidIds() =
        testApplication {
            startDefault()

            val response = client.get("/api/meeting-types")
            response.assertMatchesContract()
            val items = Json.parseToJsonElement(response.bodyAsText()).jsonArray

            for (item in items) {
                val obj: JsonObject = item.jsonObject
                assertEquals(setOf("id", "title", "description", "durationMinutes"), obj.keys)
                UUID.fromString(obj["id"]!!.jsonPrimitive.content)
                assert(obj["title"]!!.jsonPrimitive.content.isNotBlank())
            }
            assertEquals(2, items.map { it.jsonObject["id"] }.toSet().size)
        }

    @Test
    fun getReturnsOneTypeById() =
        testApplication {
            startDefault()
            val first = Json.parseToJsonElement(client.get("/api/meeting-types").bodyAsText()).jsonArray[1].jsonObject
            val id = first["id"]!!.jsonPrimitive.content

            val response = client.get("/api/meeting-types/$id")

            assertEquals(HttpStatusCode.OK, response.status)
            response.assertMatchesContract()
            assertEquals(first, Json.parseToJsonElement(response.bodyAsText()).jsonObject)
        }

    @Test
    fun getUnknownUuidIsNotFoundByContract() =
        testApplication {
            startDefault()

            val response = client.get("/api/meeting-types/${UUID.randomUUID()}")

            assertEquals(HttpStatusCode.NotFound, response.status)
            response.assertMatchesContract()
            assertEquals(notFoundBody, response.bodyAsText())
        }

    @Test
    fun getMalformedIdIsNotFoundByContract() =
        testApplication {
            startDefault()

            val response = client.get("/api/meeting-types/not-a-uuid")

            assertEquals(HttpStatusCode.NotFound, response.status)
            response.assertMatchesContract()
            assertEquals(notFoundBody, response.bodyAsText())
        }

    @Test
    fun descriptionIsOmittedWhenAbsent() =
        testApplication {
            val repository = InMemoryMeetingTypeRepository()
            val created = repository.add(title = "Без описания", description = null, durationMinutes = 45)
            application { module(AppConfig.fromEnvironment(emptyMap()), repository) }

            val response = client.get("/api/meeting-types/${created.id}")
            response.assertMatchesContract()
            val obj = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            assertNull(obj["description"])
            assertEquals(setOf("id", "title", "durationMinutes"), obj.keys)
        }

    @Test
    fun repositoryKeepsCreationOrderAndFindsById() {
        val repository = InMemoryMeetingTypeRepository()
        val a = repository.add("A", null, 15)
        val b = repository.add("B", "desc", 30)

        assertEquals(listOf(a, b), repository.list())
        assertEquals(b, repository.find(b.id))
        assertNotNull(repository.find(a.id))
        assertNull(repository.find(UUID.randomUUID()))
    }
}
