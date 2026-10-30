package calendar

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

@Serializable
data class HealthResponse(
    val status: String,
)

@Serializable
data class OwnerResponse(
    val name: String,
    val bio: String,
    val timeZone: String,
)

@Serializable
data class FieldError(
    val field: String,
    val message: String,
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val fieldErrors: List<FieldError>? = null,
)

private val log = LoggerFactory.getLogger("calendar.Application")

fun main() {
    val config = AppConfig.fromEnvironment()
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port) { module(config) }.start(wait = true)
}

@OptIn(ExperimentalSerializationApi::class)
fun Application.module(config: AppConfig) {
    install(ContentNegotiation) {
        // Optional contract fields (e.g. ApiError.fieldErrors) must be absent, not null.
        json(Json { explicitNulls = false })
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "INTERNAL_ERROR", message = "Внутренняя ошибка сервера"),
            )
        }
    }
    routing {
        get("/api/health") {
            call.respond(HealthResponse(status = "ok"))
        }
        get("/api/owner") {
            val owner = config.owner
            call.respond(OwnerResponse(name = owner.name, bio = owner.bio, timeZone = owner.timeZone))
        }
    }
}
