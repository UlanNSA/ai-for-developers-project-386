import { Link, useParams } from 'react-router';
import { findMeetingType } from './meetingTypes';

export function BookingPage() {
  const { meetingTypeId } = useParams();
  const meetingType = findMeetingType(meetingTypeId);

  if (!meetingType) {
    return (
      <main className="page">
        <h1>Тип встречи не найден</h1>
        <Link to="/">Вернуться на главную</Link>
      </main>
    );
  }

  return (
    <main className="page">
      <h1>{meetingType.title}</h1>
      <p className="duration">{meetingType.durationMinutes} мин</p>
      <p>Выбор времени будет доступен позже</p>
      <Link to="/">Вернуться на главную</Link>
    </main>
  );
}
