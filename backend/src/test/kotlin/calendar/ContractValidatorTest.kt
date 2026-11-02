package calendar

import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Proves the shared helper really rejects responses that diverge from the contract. */
class ContractValidatorTest {
    private val someUuid = "5a2a1c1e-0000-4000-8000-000000000000"

    private fun ApplicationTestBuilder.respondWith(
        route: String,
        body: String,
    ) {
        application {
            routing { get(route) { call.respondText(body, ContentType.Application.Json) } }
        }
    }

    @Test
    fun renamedFieldIsRejected() =
        testApplication {
            respondWith("/api/health", """{"state":"ok"}""")

            val error = assertFailsWith<AssertionError> { client.get("/api/health").assertMatchesContract() }

            assertTrue(error.message!!.contains("violates the contract"), error.message)
        }

    @Test
    fun wrongTypeIsRejected() =
        testApplication {
            respondWith("/api/health", """{"status":1}""")

            assertFailsWith<AssertionError> { client.get("/api/health").assertMatchesContract() }
        }

    @Test
    fun malformedUuidIsRejected() =
        testApplication {
            respondWith("/api/meeting-types/{id}", """{"id":"not-a-uuid","title":"t","durationMinutes":15}""")

            assertFailsWith<AssertionError> { client.get("/api/meeting-types/$someUuid").assertMatchesContract() }
        }

    @Test
    fun undeclaredExtraFieldIsRejected() =
        testApplication {
            respondWith("/api/health", """{"status":"ok","extra":true}""")

            assertFailsWith<AssertionError> { client.get("/api/health").assertMatchesContract() }
        }
}
