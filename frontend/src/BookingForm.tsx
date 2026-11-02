import type { FormEvent } from 'react';

export interface GuestValues {
  name: string;
  email: string;
}

export type GuestField = keyof GuestValues;

/** API field names that have an input on the form. */
export const FIELD_BY_API_NAME: Record<string, GuestField | undefined> = {
  guestName: 'name',
  guestEmail: 'email',
};

interface BookingFormProps {
  values: GuestValues;
  onChange: (values: GuestValues) => void;
  fieldErrors: Partial<Record<GuestField, string>>;
  generalError: string | null;
  submitting: boolean;
  onSubmit: () => void;
}

export function BookingForm({ values, onChange, fieldErrors, generalError, submitting, onSubmit }: BookingFormProps) {
  const submit = (event: FormEvent) => {
    event.preventDefault();
    onSubmit();
  };

  const errorProps = (field: GuestField) => ({
    'aria-invalid': fieldErrors[field] ? true : undefined,
    'aria-describedby': fieldErrors[field] ? `${field}-error` : undefined,
  });
  const errorText = (field: GuestField) =>
    fieldErrors[field] && (
      <span id={`${field}-error`} className="field-error">
        {fieldErrors[field]}
      </span>
    );

  return (
    <form className="form" onSubmit={submit} noValidate>
      <label>
        Имя
        <input
          autoComplete="name"
          value={values.name}
          onChange={(event) => {
            onChange({ ...values, name: event.target.value });
          }}
          {...errorProps('name')}
        />
      </label>
      {errorText('name')}
      <label>
        Email
        <input
          type="email"
          autoComplete="email"
          value={values.email}
          onChange={(event) => {
            onChange({ ...values, email: event.target.value });
          }}
          {...errorProps('email')}
        />
      </label>
      {errorText('email')}
      {generalError && <p role="alert">{generalError}</p>}
      <button className="button" type="submit" disabled={submitting}>
        {submitting ? 'Запись…' : 'Записаться'}
      </button>
    </form>
  );
}
