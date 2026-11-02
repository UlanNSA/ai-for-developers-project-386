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

/** Body of POST /api/meeting-types. Lengths are counted after trim in Unicode code points. */
model CreateMeetingTypeRequest {
  /** 1 to 100 characters after trim. */
  @minLength(1)
  @maxLength(100)
  title: string;

  /** Up to 500 characters after trim; blank counts as absent. */
  @maxLength(500)
  description?: string;

  /** Whole minutes, a multiple of 15, 15 to 240. */
  @minValue(15)
  @maxValue(240)
  durationMinutes: int32;
}

/** The meeting type was created. */
model MeetingTypeCreatedResponse {
  @statusCode _: 201;
  @body body: MeetingType;
}

/** MALFORMED_REQUEST (not valid JSON) or VALIDATION_ERROR (rules, unknown fields; with fieldErrors). */
model BadRequestResponse {
  @statusCode _: 400;
  @body body: ApiError;
}

/** Creates a meeting type; titles may repeat. */
@post
@route("/api/meeting-types")
op createMeetingType(@body body: CreateMeetingTypeRequest):
  | MeetingTypeCreatedResponse
  | BadRequestResponse
  | InternalErrorResponse;
