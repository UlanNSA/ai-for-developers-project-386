import { Link, useParams } from 'react-router';
import { getMeetingType } from './api/generated';
import { SlotPicker } from './SlotPicker';
import { useApiResource } from './useApiResource';

export function BookingPage() {
  const { meetingTypeId = '' } = useParams();
  const state = useApiResource(() => getMeetingType({ path: { id: meetingTypeId } }), meetingTypeId);

  if (state.status === 'loading') {
    return (
      <main className="page">
        <p>Загрузка…</p>
      </main>
    );
  }

  if (state.status === 'error') {
    return (
      <main className="page">
        {state.notFound ? (
          <h1>Тип встречи не найден</h1>
        ) : (
          <p role="alert">Не удалось загрузить тип встречи. Попробуйте обновить страницу позже.</p>
        )}
        <Link to="/">Вернуться на главную</Link>
      </main>
    );
  }

  const meetingType = state.data;
  return (
    <main className="page">
      <h1>{meetingType.title}</h1>
      <p className="duration">{meetingType.durationMinutes} мин</p>
      <SlotPicker meetingTypeId={meetingTypeId} />
      <Link to="/">Вернуться на главную</Link>
    </main>
  );
}
