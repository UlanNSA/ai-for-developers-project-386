import type { FieldError } from './api/generated';

export type FieldMessages<F extends string> = Partial<Record<F, string>>;

/**
 * Maps the API `fieldErrors` to messages of the form's own fields. `needsGeneralMessage` is true when nothing
 * could be shown at a field or when some error names a field the form has no input for.
 */
export function placeFieldErrors<F extends string>(
  fieldErrors: FieldError[] | undefined,
  fieldOf: (apiName: string) => F | undefined,
): { messages: FieldMessages<F>; needsGeneralMessage: boolean } {
  const messages: FieldMessages<F> = {};
  let unplaced = false;
  for (const fieldError of fieldErrors ?? []) {
    const field = fieldOf(fieldError.field);
    if (field) {
      messages[field] = fieldError.message;
    } else {
      unplaced = true;
    }
  }
  return { messages, needsGeneralMessage: Object.keys(messages).length === 0 || unplaced };
}

/** Accessibility attributes for an input and the message element they point to, per field. */
export function fieldErrorView<F extends string>(messages: FieldMessages<F>) {
  return {
    inputProps: (field: F) => ({
      'aria-invalid': messages[field] ? true : undefined,
      'aria-describedby': messages[field] ? `${field}-error` : undefined,
    }),
    message: (field: F) =>
      messages[field] && (
        <span id={`${field}-error`} className="field-error">
          {messages[field]}
        </span>
      ),
  };
}
