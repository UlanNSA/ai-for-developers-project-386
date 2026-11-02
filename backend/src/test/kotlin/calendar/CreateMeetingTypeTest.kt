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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CreateMeetingTypeTest {
    private fun ApplicationTestBuilder.startDefault() {
        application { module(AppConfig.fromEnvironment(emptyMap())) }
    }

    private suspend fun ApplicationTestBuilder.post(body: String) =
        client.post("/api/meeting-types") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private fun JsonObject.field(name: String) = this[name]!!.jsonPrimitive.content

    @Test
    fun createsATypeAndReturns201WithTheContractShape() =
        testApplication {
            startDefault()
            val request = """{"title":"Интервью","description":"Разговор","durationMinutes":45}"""

            val response = post(request)

            assertEquals(HttpStatusCode.Created, response.status)
            response.assertMatchesContract(request)
            val created = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            UUID.fromString(created.field("id"))
            assertEquals("Интервью", created.field("title"))
            assertEquals("Разговор", created.field("description"))
            assertEquals("45", created.field("durationMinutes"))
        }

    @Test
    fun createdTypeIsListedLastAndFoundById() =
        testApplication {
            startDefault()
            val created = Json.parseToJsonElement(post("""{"title":"Новый","durationMinutes":60}""").bodyAsText()).jsonObject

            val list = client.get("/api/meeting-types")
            list.assertMatchesContract()
            val items = Json.parseToJsonElement(list.bodyAsText()).jsonArray
            assertEquals(3, items.size)
            assertEquals(created, items.last().jsonObject)
            assertEquals(HttpStatusCode.OK, client.get("/api/meeting-types/${created.field("id")}").status)
        }

    @Test
    fun titlesMayRepeat() =
        testApplication {
            startDefault()
            val first = post("""{"title":"Знакомство","durationMinutes":15}""")
            val second = post("""{"title":"Знакомство","durationMinutes":15}""")

            assertEquals(HttpStatusCode.Created, first.status)
            assertEquals(HttpStatusCode.Created, second.status)
            val ids = listOf(first, second).map { Json.parseToJsonElement(it.bodyAsText()).jsonObject.field("id") }
            assertEquals(2, ids.toSet().size)
        }

    @Test
    fun textIsTrimmedAndBlankDescriptionIsAbsent() =
        testApplication {
            startDefault()
            val request = """{"title":"  Звонок  ","description":"   ","durationMinutes":30}"""

            val response = post(request)

            assertEquals(HttpStatusCode.Created, response.status)
            response.assertMatchesContract(request)
            val created = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("Звонок", created.field("title"))
            assertNull(created["description"])
        }

    @Test
    fun codePointsOutsideTheBmpAreCountedAsOneCharacter() =
        testApplication {
            startDefault()
            val hundredEmoji = "😀".repeat(100)

            val ok = post("""{"title":"$hundredEmoji","durationMinutes":30}""")
            val tooLong = post("""{"title":"${hundredEmoji}x","durationMinutes":30}""")

            assertEquals(HttpStatusCode.Created, ok.status)
            assertEquals(HttpStatusCode.BadRequest, tooLong.status)
            tooLong.assertMatchesContract(validateRequest = false)
        }

    @Test
    fun invalidValuesGiveValidationErrorWithFieldErrors() =
        testApplication {
            startDefault()

            val response = post("""{"title":"   ","description":"${"d".repeat(501)}","durationMinutes":14}""")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            response.assertMatchesContract(validateRequest = false)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("VALIDATION_ERROR", body.field("code"))
            val fields = body["fieldErrors"]!!.jsonArray.map { it.jsonObject.field("field") }
            assertEquals(listOf("title", "description", "durationMinutes"), fields)
        }

    @Test
    fun durationBoundariesAreEnforcedByTheApi() =
        testApplication {
            startDefault()
            for ((minutes, status) in listOf(0 to 400, 14 to 400, 15 to 201, 240 to 201, 255 to 400)) {
                assertEquals(
                    HttpStatusCode.fromValue(status),
                    post("""{"title":"t","durationMinutes":$minutes}""").status,
                    "durationMinutes=$minutes",
                )
            }
        }

    @Test
    fun unknownJsonFieldsAreRejected() =
        testApplication {
            startDefault()

            val response = post("""{"title":"t","durationMinutes":30,"id":"x","guestName":"y"}""")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            response.assertMatchesContract(validateRequest = false)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("VALIDATION_ERROR", body.field("code"))
            assertEquals(listOf("id", "guestName"), body["fieldErrors"]!!.jsonArray.map { it.jsonObject.field("field") })
        }

    @Test
    fun rejectedRequestsCreateNothing() =
        testApplication {
            startDefault()
            post("""{"title":"","durationMinutes":30}""")
            post("{broken")

            val items = Json.parseToJsonElement(client.get("/api/meeting-types").bodyAsText()).jsonArray
            assertEquals(2, items.size)
        }

    @Test
    fun malformedJsonGivesMalformedRequest() =
        testApplication {
            startDefault()
            for (broken in listOf("{broken", "", "{\"title\":", "not json")) {
                val response = post(broken)

                assertEquals(HttpStatusCode.BadRequest, response.status, "body=$broken")
                response.assertMatchesContract(validateRequest = false)
                assertEquals(
                    """{"code":"MALFORMED_REQUEST","message":"Некорректный JSON в теле запроса"}""",
                    response.bodyAsText(),
                )
            }
        }

    @Test
    fun serverFailureGives500WithoutDetails() =
        testApplication {
            application { module(AppConfig.fromEnvironment(emptyMap()), ErrorHandlingTest.FailingRepository()) }

            val response = post("""{"title":"t","durationMinutes":30}""")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            response.assertMatchesContract("""{"title":"t","durationMinutes":30}""")
            assertFalse(response.bodyAsText().contains("secret"))
            assertEquals("""{"code":"INTERNAL_ERROR","message":"Внутренняя ошибка сервера"}""", response.bodyAsText())
        }
}
