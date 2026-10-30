package calendar

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OwnerTest {
    @Test
    fun ownerReturnsDefaultsWhenEnvironmentIsEmpty() =
        testApplication {
            application { module(AppConfig.fromEnvironment(emptyMap())) }

            val response = client.get("/api/owner")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                """{"name":"Владелец календаря","bio":"Выберите удобное время для встречи","timeZone":"Asia/Almaty"}""",
                response.bodyAsText(),
            )
        }

    @Test
    fun ownerReturnsInjectedConfig() =
        testApplication {
            val config =
                AppConfig(owner = OwnerConfig(name = "Анна", bio = "Привет", timeZone = "Europe/Moscow"))
            application { module(config) }

            val response = client.get("/api/owner")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"name":"Анна","bio":"Привет","timeZone":"Europe/Moscow"}""", response.bodyAsText())
        }

    @Test
    fun configIsReadFromOwnerEnvironmentVariables() {
        val config =
            AppConfig.fromEnvironment(
                mapOf("OWNER_NAME" to "Борис", "OWNER_BIO" to "О себе", "OWNER_TIME_ZONE" to "UTC"),
            )

        assertEquals(OwnerConfig(name = "Борис", bio = "О себе", timeZone = "UTC"), config.owner)
    }

    @Test
    fun invalidTimeZoneFailsFastWithClearMessage() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                AppConfig.fromEnvironment(mapOf("OWNER_TIME_ZONE" to "Mars/Base"))
            }

        assertTrue(error.message!!.contains("OWNER_TIME_ZONE"))
        assertTrue(error.message!!.contains("Mars/Base"))
    }
}
