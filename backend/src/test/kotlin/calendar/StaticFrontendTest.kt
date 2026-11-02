package calendar

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StaticFrontendTest {
    private val indexHtml = "<!doctype html><html><body><div id=\"root\">INDEX-MARKER</div></body></html>"
    private val appJs = "console.log('APP-JS-MARKER');"

    private fun frontendDir(): Path {
        val dir = createTempDirectory("frontend-dist")
        dir.resolve("index.html").writeText(indexHtml)
        dir.resolve("assets").createDirectories()
        dir.resolve("assets/app.js").writeText(appJs)
        return dir
    }

    private fun config(staticDir: Path?) =
        AppConfig.fromEnvironment(
            buildMap { if (staticDir != null) put("STATIC_DIR", staticDir.toString()) },
        )

    @Test
    fun rootServesTheBuiltIndexPage() =
        testApplication {
            application { module(config(frontendDir())) }

            val response = client.get("/")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
            assertEquals(indexHtml, response.bodyAsText())
        }

    @Test
    fun clientRoutesServeTheIndexPageOnDirectNavigationAndRefresh() =
        testApplication {
            application { module(config(frontendDir())) }

            for (path in listOf("/owner", "/book/5b2f6b3a-1c4d-4e8a-9a55-0d1e2f3a4b5c", "/no/such/page")) {
                val response = client.get(path)

                assertEquals(HttpStatusCode.OK, response.status, path)
                assertEquals(indexHtml, response.bodyAsText(), path)
            }
        }

    @Test
    fun buildAssetsAreServedAsFilesNotAsTheIndexPage() =
        testApplication {
            application { module(config(frontendDir())) }

            val response = client.get("/assets/app.js")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(appJs, response.bodyAsText())
            assertTrue(response.contentType()?.match(ContentType.Text.JavaScript) == true, "got ${response.contentType()}")
        }

    @Test
    fun apiRoutesKeepWorkingNextToTheFrontend() =
        testApplication {
            application { module(config(frontendDir())) }

            val health = client.get("/api/health")
            val owner = client.get("/api/owner")

            assertEquals(HttpStatusCode.OK, health.status)
            assertEquals("""{"status":"ok"}""", health.bodyAsText())
            assertEquals(HttpStatusCode.OK, owner.status)
            assertTrue(owner.bodyAsText().contains("Владелец календаря"))
        }

    @Test
    fun unknownApiPathsAreNotFoundAndNeverReturnHtml() =
        testApplication {
            application { module(config(frontendDir())) }

            for (path in listOf("/api/unknown", "/api/meeting-types/unknown/extra", "/api")) {
                val response = client.get(path)

                assertEquals(HttpStatusCode.NotFound, response.status, path)
                assertFalse(response.bodyAsText().contains("INDEX-MARKER"), path)
                assertFalse(response.contentType()?.match(ContentType.Text.Html) == true, path)
            }
            val post = client.post("/api/unknown")
            assertEquals(HttpStatusCode.NotFound, post.status)
            assertFalse(post.bodyAsText().contains("INDEX-MARKER"))
        }

    @Test
    fun testRoutesStayUnreachableInNormalModeEvenWithTheFrontend() =
        testApplication {
            application { module(config(frontendDir())) }

            val reset = client.post("/__test__/reset")

            assertEquals(HttpStatusCode.NotFound, reset.status)
            assertFalse(reset.bodyAsText().contains("INDEX-MARKER"))
        }

    @Test
    fun withoutStaticDirTheRootIsNotServed() =
        testApplication {
            application { module(config(null)) }

            assertEquals(HttpStatusCode.NotFound, client.get("/").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/owner").status)
            assertEquals(HttpStatusCode.OK, client.get("/api/health").status)
        }

    @Test
    fun staticDirIsReadFromTheEnvironment() {
        val dir = frontendDir()

        assertEquals(dir.toString(), config(dir).staticDir)
        assertNull(config(null).staticDir)
    }

    @Test
    fun missingStaticDirFailsFastWithAClearMessage() {
        val missing = createTempDirectory("missing-parent").resolve("nope")

        val error = assertFailsWith<IllegalArgumentException> { config(missing) }

        assertNotNull(error.message)
        assertTrue(error.message!!.contains("STATIC_DIR"), error.message)
    }

    @Test
    fun staticDirWithoutIndexPageFailsFastWithAClearMessage() {
        val empty = createTempDirectory("empty-dist")
        assertTrue(Files.list(empty).use { it.findFirst().isEmpty })

        val error = assertFailsWith<IllegalArgumentException> { config(empty) }

        assertTrue(error.message!!.contains("index.html"), error.message)
    }
}
