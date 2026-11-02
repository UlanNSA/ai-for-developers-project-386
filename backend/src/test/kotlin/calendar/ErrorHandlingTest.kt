package calendar

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ErrorHandlingTest {
    private class FailingRepository : MeetingTypeRepository {
        override fun list(): List<MeetingType> = error("secret internal detail")

        override fun find(id: UUID): MeetingType? = error("secret internal detail")

        override fun add(
            title: String,
            description: String?,
            durationMinutes: Int,
        ): MeetingType = error("secret internal detail")
    }

    @Test
    fun unhandledExceptionBecomesInternalErrorWithoutDetails() =
        testApplication {
            application { module(AppConfig.fromEnvironment(emptyMap()), FailingRepository()) }

            val response = client.get("/api/meeting-types")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            response.assertMatchesContract()
            val body = response.bodyAsText()
            assertEquals("""{"code":"INTERNAL_ERROR","message":"Внутренняя ошибка сервера"}""", body)
            assertFalse(body.contains("secret"))
        }
}
