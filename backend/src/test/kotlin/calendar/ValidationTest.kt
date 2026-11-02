package calendar

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidationTest {
    private val smile = "😀" // one code point, two UTF-16 units (outside the BMP)

    private fun validate(json: String) = validateMeetingTypeInput(Json.parseToJsonElement(json))

    private fun title(value: String) = """{"title":"$value","durationMinutes":30}"""

    private fun assertValid(json: String): MeetingTypeDraft {
        val result = validate(json)
        assertTrue(result is Validated.Valid, "expected valid but was $result")
        return result.value
    }

    private fun assertInvalidFields(
        json: String,
        vararg fields: String,
    ) {
        val result = validate(json)
        assertTrue(result is Validated.Invalid, "expected invalid but was $result")
        assertEquals(fields.toList(), result.errors.map { it.field })
    }

    @Test
    fun titleBounds() {
        assertInvalidFields(title(""), "title")
        assertEquals("a", assertValid(title("a")).title)
        assertEquals(100, assertValid(title("a".repeat(100))).title.length)
        assertInvalidFields(title("a".repeat(101)), "title")
    }

    @Test
    fun titleIsTrimmedBeforeCounting() {
        assertInvalidFields(title("   "), "title")
        assertEquals("a", assertValid(title("  a  ")).title)
        assertEquals(100, assertValid(title(" " + "a".repeat(100) + " ")).title.length)
        assertInvalidFields(title(" " + "a".repeat(101) + " "), "title")
    }

    @Test
    fun titleLengthCountsCodePointsNotUtf16Units() {
        // 100 emoji are 200 UTF-16 units but 100 code points: valid. 101 are invalid.
        assertValid(title(smile.repeat(100)))
        assertInvalidFields(title(smile.repeat(101)), "title")
        assertValid(title(smile))
    }

    @Test
    fun descriptionBounds() {
        fun body(description: String) = """{"title":"t","description":"$description","durationMinutes":30}"""
        assertEquals(null, assertValid(body("")).description)
        assertEquals(null, assertValid(body("   ")).description)
        assertEquals("d", assertValid(body("d")).description)
        assertEquals(500, assertValid(body("d".repeat(500))).description?.length)
        assertInvalidFields(body("d".repeat(501)), "description")
        assertValid(body(smile.repeat(500)))
        assertInvalidFields(body(smile.repeat(501)), "description")
        assertEquals(null, assertValid("""{"title":"t","description":null,"durationMinutes":30}""").description)
        assertEquals(null, assertValid("""{"title":"t","durationMinutes":30}""").description)
    }

    @Test
    fun durationBoundsAndStep() {
        fun body(minutes: String) = """{"title":"t","durationMinutes":$minutes}"""
        for (invalid in listOf("0", "1", "14", "16", "29", "241", "255", "-15", "300")) {
            assertInvalidFields(body(invalid), "durationMinutes")
        }
        for (valid in listOf(15, 30, 45, 120, 225, 240)) {
            assertEquals(valid, assertValid(body("$valid")).durationMinutes)
        }
    }

    @Test
    fun durationMustBeAnInteger() {
        for (invalid in listOf("30.5", "30.0", "\"30\"", "true", "null", "[30]", "{}")) {
            assertInvalidFields("""{"title":"t","durationMinutes":$invalid}""", "durationMinutes")
        }
    }

    @Test
    fun wrongTypesAndMissingFields() {
        assertInvalidFields("""{"durationMinutes":30}""", "title")
        assertInvalidFields("""{"title":5,"durationMinutes":30}""", "title")
        assertInvalidFields("""{"title":"t","description":5,"durationMinutes":30}""", "description")
        assertInvalidFields("{}", "title", "durationMinutes")
        assertInvalidFields("[]", "body")
        assertInvalidFields("\"text\"", "body")
    }

    @Test
    fun unknownFieldsAreRejected() {
        assertInvalidFields("""{"title":"t","durationMinutes":30,"id":"x","extra":1}""", "id", "extra")
    }

    @Test
    fun allErrorsAreReportedTogether() {
        assertInvalidFields(
            """{"title":"","description":"${"d".repeat(501)}","durationMinutes":7}""",
            "title",
            "description",
            "durationMinutes",
        )
    }

    @Test
    fun trimmedLengthCountsCodePointsAfterTrim() {
        assertEquals(0, trimmedLength("  "))
        assertEquals(1, trimmedLength(" $smile "))
        assertEquals(3, trimmedLength("a${smile}b"))
    }

    private val typeId = "11111111-1111-4111-8111-111111111111"

    private fun booking(
        name: String = "Анна",
        email: String = "a@b.c",
        startAt: String = "2026-11-02T04:00:00Z",
    ) = validateBookingInput(
        Json.parseToJsonElement(
            """{"meetingTypeId":"$typeId","startAt":"$startAt","guestName":"$name","guestEmail":"$email"}""",
        ),
    )

    private fun assertBookingInvalid(
        result: Validated<BookingDraft>,
        field: String,
    ) {
        assertTrue(result is Validated.Invalid, "expected invalid but was $result")
        assertEquals(listOf(field), result.errors.map { it.field })
    }

    @Test
    fun guestNameBoundsAreCodePointsAfterTrim() {
        assertBookingInvalid(booking(name = "   "), "guestName")
        assertTrue(booking(name = " x ") is Validated.Valid)
        assertTrue(booking(name = smile.repeat(100)) is Validated.Valid)
        assertBookingInvalid(booking(name = smile.repeat(101)), "guestName")
    }

    @Test
    fun emailFormatAndLength() {
        for (bad in listOf("", "ab.c", "@b.c", "a@", "a@b", "a@@b.c", "a@b@c.d")) {
            assertBookingInvalid(booking(email = bad), "guestEmail")
        }
        val local = "a".repeat(254 - "@b.c".length)
        assertTrue(booking(email = "$local@b.c") is Validated.Valid)
        assertBookingInvalid(booking(email = "a$local@b.c"), "guestEmail")
    }

    @Test
    fun emailCaseIsPreservedAndSurroundingSpacesTrimmed() {
        val result = booking(email = "  Anna@Example.COM ")
        assertTrue(result is Validated.Valid)
        assertEquals("Anna@Example.COM", result.value.guestEmail)
    }

    @Test
    fun startAtMustBeUtcWithZ() {
        assertTrue(booking(startAt = "2026-11-02T04:00:00Z") is Validated.Valid)
        assertBookingInvalid(booking(startAt = "2026-11-02T09:00:00+05:00"), "startAt")
        assertBookingInvalid(booking(startAt = "2026-11-02T04:00:00+00:00"), "startAt")
        assertBookingInvalid(booking(startAt = "2026-11-02"), "startAt")
        assertBookingInvalid(booking(startAt = "garbage Z"), "startAt")
    }
}
