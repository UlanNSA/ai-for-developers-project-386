package calendar

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ErrorHandlingTest {
    @Test
    fun unhandledExceptionBecomesInternalErrorWithoutDetails() =
        testApplication {
            application {
                module(AppConfig.fromEnvironment(emptyMap()))
                routing { get("/api/boom") { error("secret internal detail") } }
            }

            val response = client.get("/api/boom")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val body = response.bodyAsText()
            assertEquals("""{"code":"INTERNAL_ERROR","message":"Внутренняя ошибка сервера"}""", body)
            assertFalse(body.contains("secret"))
        }
}
