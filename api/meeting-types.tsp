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
