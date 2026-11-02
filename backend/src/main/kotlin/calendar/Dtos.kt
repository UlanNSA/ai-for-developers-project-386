package calendar

import kotlinx.serialization.Serializable

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
data class MeetingTypeResponse(
    val id: String,
    val title: String,
    val description: String? = null,
    val durationMinutes: Int,
)

@Serializable
data class BookingResponse(
    val id: String,
    val meetingTypeId: String,
    val startAt: String,
    val endAt: String,
    val guestName: String,
    val guestEmail: String,
)

/** Both instants are UTC ISO 8601 with a trailing `Z`. */
@Serializable
data class SlotResponse(
    val startAt: String,
    val endAt: String,
)

fun Booking.toResponse() =
    BookingResponse(id.toString(), meetingTypeId.toString(), startAt.toString(), endAt.toString(), guestName, guestEmail)

fun MeetingType.toResponse() = MeetingTypeResponse(id.toString(), title, description, durationMinutes)

fun Slot.toResponse() = SlotResponse(startAt.toString(), endAt.toString())
