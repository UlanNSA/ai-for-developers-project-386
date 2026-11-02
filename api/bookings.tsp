import "@typespec/http";
import "./errors.tsp";
import "./meeting-types.tsp";

using Http;

namespace CalendarApi;

/** A confirmed booking. `endAt` is computed by the backend as `startAt + durationMinutes`. */
model Booking {
  @format("uuid")
  id: string;

  @format("uuid")
  meetingTypeId: string;

  @format("date-time")
  startAt: string;

  @format("date-time")
  endAt: string;

  guestName: string;
  guestEmail: string;
}

/**
 * Body of POST /api/bookings. Unknown fields (including a client `endAt`) and a `startAt` that is not
 * UTC with a trailing `Z` are rejected with 400. Name length is counted after trim in Unicode code points.
 */
model CreateBookingRequest {
  @format("uuid")
  meetingTypeId: string;

  /** UTC instant ending with `Z`, a start the schedule offers for the meeting type. */
  @format("date-time")
  startAt: string;

  /** 1 to 100 characters after trim. */
  @minLength(1)
  @maxLength(100)
  guestName: string;

  /** Up to 254 characters; exactly one `@`, a non-empty local part, a domain with a dot. Case is preserved. */
  @maxLength(254)
  guestEmail: string;
}

/** The booking was created. */
model BookingCreatedResponse {
  @statusCode _: 201;
  @body body: Booking;
}

/** The start is not one the schedule, grid, horizon or current time would ever offer: INVALID_SLOT. */
model InvalidSlotResponse {
  @statusCode _: 422;
  @body body: ApiError;
}

/** The start is valid but overlaps a confirmed booking of any meeting type: SLOT_TAKEN. */
model SlotTakenResponse {
  @statusCode _: 409;
  @body body: ApiError;
}

/** Creates a confirmed booking. Checks run in the order 400, 404, 422, 409. */
@post
@route("/api/bookings")
op createBooking(@body body: CreateBookingRequest):
  | BookingCreatedResponse
  | BadRequestResponse
  | MeetingTypeNotFoundResponse
  | InvalidSlotResponse
  | SlotTakenResponse
  | InternalErrorResponse;

/**
 * Upcoming bookings of ALL meeting types: `startAt` strictly after the current moment, ascending by
 * `startAt`. No pagination. Started and past bookings are not returned.
 */
@get
@route("/api/bookings")
op listBookings(): Booking[] | InternalErrorResponse;
