import "@typespec/http";
import "./errors.tsp";

using Http;

namespace CalendarApi;

model MeetingType {
  @format("uuid")
  id: string;

  title: string;
  description?: string;

  /** Duration in minutes (a multiple of 15, 15 to 240). */
  durationMinutes: int32;
}

/** A free time range for a meeting type; both instants are UTC. */
model Slot {
  @format("date-time")
  startAt: string;

  @format("date-time")
  endAt: string;
}

/** The meeting type does not exist (an unknown or malformed id gives the same answer). */
model MeetingTypeNotFoundResponse {
  @statusCode _: 404;
  @body body: ApiError;
}

/** All meeting types in creation order. */
@get
@route("/api/meeting-types")
op listMeetingTypes(): MeetingType[] | InternalErrorResponse;

@get
@route("/api/meeting-types/{id}")
op getMeetingType(@path id: string):
  | MeetingType
  | MeetingTypeNotFoundResponse
  | InternalErrorResponse;

/** Free slots within the booking horizon, sorted by `startAt`. */
@get
@route("/api/meeting-types/{id}/slots")
op listSlots(@path id: string):
  | Slot[]
  | MeetingTypeNotFoundResponse
  | InternalErrorResponse;
