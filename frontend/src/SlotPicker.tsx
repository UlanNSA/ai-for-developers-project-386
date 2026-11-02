import { useEffect, useState } from 'react';
import { listSlots, type Slot } from './api/generated';
import { useApiResource } from './useApiResource';

export const LOCALE = 'ru-RU';

interface Day {
  key: string;
  label: string;
  slots: { startAt: string; time: string }[];
}

/** Groups slots (already sorted by the API) by the calendar date in [timeZone]; days without slots never appear. */
function groupByDay(slots: Slot[], timeZone: string): Day[] {
  const dateKey = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
  const dayLabel = new Intl.DateTimeFormat(LOCALE, {
    timeZone,
    weekday: 'short',
    day: 'numeric',
    month: 'long',
  });
  const time = new Intl.DateTimeFormat(LOCALE, {
    timeZone,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  });
  const days = new Map<string, Day>();
  for (const slot of slots) {
    const start = new Date(slot.startAt);
    const key = dateKey.format(start);
    const day = days.get(key) ?? {
      key,
      label: dayLabel.format(start),
      slots: [],
    };
    day.slots.push({ startAt: slot.startAt, time: time.format(start) });
    days.set(key, day);
  }
  return [...days.values()];
}

interface SlotPickerProps {
  meetingTypeId: string;
  /** Changing it reloads the slots (e.g. after a conflict). */
  reloadKey: number;
  selectedSlot: string | null;
  onSelect: (startAt: string | null) => void;
}

export function SlotPicker({ meetingTypeId, reloadKey, selectedSlot, onSelect }: SlotPickerProps) {
  const state = useApiResource(() => listSlots({ path: { id: meetingTypeId } }), `${meetingTypeId}:${reloadKey}`);
  const [selectedDay, setSelectedDay] = useState<string | null>(null);

  // A selected slot that is no longer offered (e.g. after a conflict reload) is cleared.
  const loadedSlots = state.status === 'ready' ? state.data : null;
  useEffect(() => {
    if (loadedSlots && selectedSlot !== null && !loadedSlots.some((slot) => slot.startAt === selectedSlot)) {
      onSelect(null);
    }
  }, [loadedSlots, selectedSlot, onSelect]);

  if (state.status === 'loading') return <p>Загрузка свободного времени…</p>;
  if (state.status === 'error') {
    return <p role="alert">Не удалось загрузить свободное время. Попробуйте обновить страницу позже.</p>;
  }

  const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const days = groupByDay(state.data, timeZone);
  // A selected day that disappeared from the reloaded slots is forgotten, so it is not revived if it returns.
  if (selectedDay !== null && !days.some((item) => item.key === selectedDay)) setSelectedDay(null);
  if (days.length === 0) return <p>Свободного времени нет</p>;

  const day = days.find((item) => item.key === selectedDay);
  return (
    <section>
      <p className="timezone">Время указано в вашем часовом поясе: {timeZone}</p>
      <h2>Выберите день</h2>
      <ul className="day-list">
        {days.map((item) => (
          <li key={item.key}>
            <button
              type="button"
              aria-pressed={item.key === selectedDay}
              onClick={() => {
                setSelectedDay(item.key);
                onSelect(null);
              }}
            >
              {item.label}
            </button>
          </li>
        ))}
      </ul>
      {day && (
        <>
          <h2>Выберите время</h2>
          <ul className="time-list">
            {day.slots.map((slot) => (
              <li key={slot.startAt}>
                <button
                  type="button"
                  aria-pressed={slot.startAt === selectedSlot}
                  onClick={() => {
                    onSelect(slot.startAt);
                  }}
                >
                  {slot.time}
                </button>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}
