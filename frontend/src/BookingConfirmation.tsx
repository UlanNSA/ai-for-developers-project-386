import { Link } from 'react-router';
import type { Booking, MeetingType } from './api/generated';
import { LOCALE } from './SlotPicker';

/** Confirmation of a created booking, shown on the booking page itself; times are in the guest's time zone. */
export function BookingConfirmation({ booking, meetingType }: { booking: Booking; meetingType: MeetingType }) {
  const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const start = new Date(booking.startAt);
  const end = new Date(booking.endAt);
  const date = new Intl.DateTimeFormat(LOCALE, {
    timeZone,
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });
  const time = new Intl.DateTimeFormat(LOCALE, {
    timeZone,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  });

  return (
    <section>
      <h1>Вы записаны</h1>
      <dl className="confirmation">
        <dt>Тип встречи</dt>
        <dd>{meetingType.title}</dd>
        <dt>Дата</dt>
        <dd>{date.format(start)}</dd>
        <dt>Время</dt>
        <dd>
          {time.format(start)}–{time.format(end)} (ваш часовой пояс: {timeZone})
        </dd>
        <dt>Длительность</dt>
        <dd>{meetingType.durationMinutes} мин</dd>
        <dt>Имя</dt>
        <dd>{booking.guestName}</dd>
        <dt>Email</dt>
        <dd>{booking.guestEmail}</dd>
      </dl>
      <Link to="/" className="button">
        Вернуться на главную
      </Link>
    </section>
  );
}
