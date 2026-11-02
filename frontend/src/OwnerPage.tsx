import { type FormEvent, useState } from 'react';
import { createMeetingType, listMeetingTypes, type MeetingType } from './api/generated';
import { fieldErrorView, type FieldMessages, placeFieldErrors } from './fieldErrors';
import { type ResourceState, useApiResource } from './useApiResource';
import { UpcomingBookings } from './UpcomingBookings';

const WARNING =
  'Учебная страница без авторизации. Адрес доступен всем, данные Гостей видны любому. Используйте только тестовые данные';

const FIELDS = ['title', 'description', 'durationMinutes'] as const;
type Field = (typeof FIELDS)[number];

function MeetingTypeList({ state }: { state: ResourceState<MeetingType[]> }) {
  if (state.status === 'loading') {
    return <p>Загрузка…</p>;
  }
  if (state.status === 'error') {
    return <p role="alert">Не удалось загрузить типы встреч. Попробуйте обновить страницу позже.</p>;
  }
  if (state.data.length === 0) {
    return <p>Типов встреч пока нет</p>;
  }
  return (
    <ul className="owner-types">
      {state.data.map((meetingType) => (
        <li key={meetingType.id}>
          <strong>{meetingType.title}</strong> <span className="duration">{meetingType.durationMinutes} мин</span>
          {meetingType.description && <p>{meetingType.description}</p>}
        </li>
      ))}
    </ul>
  );
}

function isField(name: string): name is Field {
  return (FIELDS as readonly string[]).includes(name);
}

interface CreateFormProps {
  onCreated: () => void;
}

function CreateMeetingTypeForm({ onCreated }: CreateFormProps) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [duration, setDuration] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FieldMessages<Field>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setFieldErrors({});
    setGeneralError(null);
    try {
      const { data, error } = await createMeetingType({
        body: {
          title,
          ...(description === '' ? {} : { description }),
          durationMinutes: duration === '' ? 0 : Number(duration),
        },
      });
      if (data !== undefined) {
        setTitle('');
        setDescription('');
        setDuration('');
        onCreated();
        return;
      }
      const { messages, needsGeneralMessage } = placeFieldErrors(error?.fieldErrors, (name) =>
        isField(name) ? name : undefined,
      );
      setFieldErrors(messages);
      if (needsGeneralMessage) {
        setGeneralError('Не удалось создать тип встречи. Проверьте данные и попробуйте ещё раз.');
      }
    } catch {
      setGeneralError('Не удалось создать тип встречи. Попробуйте ещё раз позже.');
    } finally {
      setSubmitting(false);
    }
  };

  const { inputProps: errorProps, message: errorText } = fieldErrorView(fieldErrors);

  return (
    <form className="form" onSubmit={(event) => void submit(event)} noValidate>
      <label>
        Название
        <input value={title} onChange={(event) => setTitle(event.target.value)} {...errorProps('title')} />
      </label>
      {errorText('title')}
      <label>
        Описание
        <textarea
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          {...errorProps('description')}
        />
      </label>
      {errorText('description')}
      <label>
        Длительность, мин
        <input
          type="number"
          step={15}
          min={15}
          value={duration}
          onChange={(event) => setDuration(event.target.value)}
          {...errorProps('durationMinutes')}
        />
      </label>
      {errorText('durationMinutes')}
      {generalError && <p role="alert">{generalError}</p>}
      <button className="button" type="submit" disabled={submitting}>
        {submitting ? 'Создание…' : 'Создать'}
      </button>
    </form>
  );
}

export function OwnerPage() {
  const [version, setVersion] = useState(0);
  const state = useApiResource(() => listMeetingTypes(), `owner-meeting-types-${version}`);

  return (
    <main className="page">
      <p className="warning" role="note">
        {WARNING}
      </p>
      <h1>Страница владельца</h1>
      <UpcomingBookings meetingTypes={state.status === 'ready' ? state.data : null} />
      <section>
        <h2>Типы встреч</h2>
        <MeetingTypeList state={state} />
        <h3>Новый тип встречи</h3>
        <CreateMeetingTypeForm onCreated={() => setVersion((current) => current + 1)} />
      </section>
    </main>
  );
}
