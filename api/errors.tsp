import "@typespec/http";

using Http;

namespace CalendarApi;

model FieldError {
  field: string;
  message: string;
}

model ApiError {
  code: string;
  message: string;
  fieldErrors?: FieldError[];
}

/** MALFORMED_REQUEST (not valid JSON) or VALIDATION_ERROR (rules, unknown fields; with fieldErrors). */
model BadRequestResponse {
  @statusCode _: 400;
  @body body: ApiError;
}

/** Unhandled server failure; the body never contains internal details. */
model InternalErrorResponse {
  @statusCode _: 500;
  @body body: ApiError;
}
