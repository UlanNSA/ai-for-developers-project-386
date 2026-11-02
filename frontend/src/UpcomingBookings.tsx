import { useState } from 'react';
import { type Booking, getOwner, listBookings, type MeetingType } from './api/generated';
import { dateFormatter, timeFormatter } from './format';
import { type ResourceState, useApiResource } from './useApiResource';

interface Props {
  /** Meeting types already loaded by the page; null while they are loading or failed. */
  meetingTypes: MeetingType[] | null;
}

function formatters(timeZone: string) {
  return {
    date: dateFormatter(timeZone, { weekday: 'short', year: 'numeric' }),
    time: timeFormatter(timeZone),
  };
}

function BookingList({
  bookings,
  timeZone,
  meetingTypes,
}: {
  bookings: Booking[];
  timeZone: string;
  meetingTypes: MeetingType[] | null;
}) {
  if (bookings.length === 0) {
    return <p>Предстоящих встреч нет</p>;
  }
  const format = formatters(timeZone);
  const titleOf = (id: string) => meetingTypes?.find((type) => type.id === id)?.title ?? 'Неизвестный тип встречи';
  return (
    <>
      <p className="timezone-note">Время указано в часовом поясе Владельца: {timeZone}</p>
      <ul className="owner-bookings">
        {bookings.map((booking) => (
          <li key={booking.id}>
            <strong>{titleOf(booking.meetingTypeId)}</strong>
            <div>
              {format.date.format(new Date(booking.startAt))}, {format.time.format(new Date(booking.startAt))}–
              {format.time.format(new Date(booking.endAt))}
            </div>
            <div>
              {booking.guestName}, {booking.guestEmail}
            </div>
          </li>
        ))}
      </ul>
    </>
  );
}

function Content({
  bookings,
  timeZone,
  meetingTypes,
}: {
  bookings: ResourceState<Booking[]>;
  timeZone: ResourceState<string>;
  meetingTypes: MeetingType[] | null;
}) {
  if (bookings.status === 'loading' || timeZone.status === 'loading') {
    return <p>Загрузка…</p>;
  }
  if (bookings.status === 'error' || timeZone.status === 'error') {
    return <p role="alert">Не удалось загрузить предстоящие встречи. Попробуйте обновить позже.</p>;
  }
  return <BookingList bookings={bookings.data} timeZone={timeZone.data} meetingTypes={meetingTypes} />;
}

/** Owner's list of upcoming bookings; reloads only on the "Обновить" button (no polling). */
export function UpcomingBookings({ meetingTypes }: Props) {
  const [version, setVersion] = useState(0);
  const bookings = useApiResource(() => listBookings(), `upcoming-bookings-${version}`);
  const owner = useApiResource(() => getOwner(), 'upcoming-owner');
  const zone: ResourceState<string> = owner.status === 'ready' ? { status: 'ready', data: owner.data.timeZone } : owner;

  return (
    <section>
      <h2>Предстоящие встречи</h2>
      <button
        className="button"
        type="button"
        disabled={bookings.status === 'loading'}
        onClick={() => setVersion((v) => v + 1)}
      >
        Обновить
      </button>
      <Content bookings={bookings} timeZone={zone} meetingTypes={meetingTypes} />
    </section>
  );
}
