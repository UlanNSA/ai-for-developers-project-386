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

/** Unhandled server failure; the body never contains internal details. */
model InternalErrorResponse {
  @statusCode _: 500;
  @body body: ApiError;
}
