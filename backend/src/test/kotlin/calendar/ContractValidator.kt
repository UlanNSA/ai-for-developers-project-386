package calendar

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.Request
import com.atlassian.oai.validator.model.SimpleRequest
import com.atlassian.oai.validator.model.SimpleResponse
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import java.io.File
import kotlin.test.fail

/**
 * Shared contract check for API tests: validates the request and the response against the generated
 * `api/openapi/openapi.yaml` (status, headers, content type, body schema, `uuid`/`date-time` formats).
 * The spec path comes from the `openapi.spec` system property set in `build.gradle.kts`.
 */
object ContractValidator {
    private val specUri =
        File(requireNotNull(System.getProperty("openapi.spec")) { "openapi.spec is not set" }).toURI().toString()

    private val validator: OpenApiInteractionValidator = OpenApiInteractionValidator.createFor(specUri).build()

    /** Fails the test with all contract violations of [response] and of the request that produced it. */
    suspend fun assertMatchesContract(
        response: HttpResponse,
        requestBody: String? = null,
    ) {
        val call = response.request
        val request =
            SimpleRequest
                .Builder(Request.Method.valueOf(call.method.value), call.url.encodedPath)
                .apply {
                    call.url.parameters.names().forEach { name ->
                        call.url.parameters
                            .getAll(name)
                            ?.forEach { value -> withQueryParam(name, value) }
                    }
                    call.headers.names().forEach { name ->
                        call.headers.getAll(name)?.forEach { value -> withHeader(name, value) }
                    }
                    requestBody?.let { withBody(it) }
                }.build()
        val simpleResponse =
            SimpleResponse
                .Builder(response.status.value)
                .apply {
                    response.headers.names().forEach { name ->
                        response.headers.getAll(name)?.forEach { value -> withHeader(name, value) }
                    }
                    response.headers[HttpHeaders.ContentType]?.let { withContentType(it) }
                    withBody(response.bodyAsText())
                }.build()
        val report = validator.validate(request, simpleResponse)
        if (report.hasErrors()) {
            val details = report.messages.joinToString("\n") { "- ${it.message}" }
            fail("${call.method.value} ${call.url.encodedPath} violates the contract:\n$details")
        }
    }
}

suspend fun HttpResponse.assertMatchesContract(requestBody: String? = null) = ContractValidator.assertMatchesContract(this, requestBody)
