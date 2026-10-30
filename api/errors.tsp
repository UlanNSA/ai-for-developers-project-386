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
