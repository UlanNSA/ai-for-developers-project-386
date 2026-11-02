import { useRef, useState } from 'react';
import { Link, useParams } from 'react-router';
import { type Booking, createBooking, getMeetingType, type MeetingType } from './api/generated';
import { BookingConfirmation } from './BookingConfirmation';
import { BookingForm, FIELD_BY_API_NAME, type GuestField, type GuestValues } from './BookingForm';
import { type FieldMessages, placeFieldErrors } from './fieldErrors';
import { SlotPicker } from './SlotPicker';
import { useApiResource } from './useApiResource';

const CONFLICT_MESSAGE = 'Это время уже занято, выберите другое';
const INVALID_SLOT_MESSAGE = 'Это время больше недоступно, выберите другое';
const GENERAL_ERROR = 'Не удалось записаться. Попробуйте ещё раз.';

/** Slot choice, guest form and confirmation of one meeting type. Typed name and email survive a conflict. */
function Booker({ meetingType }: { meetingType: MeetingType }) {
  const [selectedSlot, setSelectedSlot] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [guest, setGuest] = useState<GuestValues>({ name: '', email: '' });
  const [submitting, setSubmitting] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FieldMessages<GuestField>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [slotMessage, setSlotMessage] = useState<string | null>(null);
  const [booking, setBooking] = useState<Booking | null>(null);
  const inFlight = useRef(false);

  if (booking) return <BookingConfirmation booking={booking} meetingType={meetingType} />;

  const select = (startAt: string | null) => {
    setSelectedSlot(startAt);
    setSlotMessage(null);
    setFieldErrors({});
    setGeneralError(null);
  };

  const submit = async () => {
    if (inFlight.current || selectedSlot === null) return;
    inFlight.current = true;
    setSubmitting(true);
    setFieldErrors({});
    setGeneralError(null);
    setSlotMessage(null);
    try {
      const { data, error, response } = await createBooking({
        body: {
          meetingTypeId: meetingType.id,
          startAt: selectedSlot,
          guestName: guest.name,
          guestEmail: guest.email,
        },
      });
      if (data !== undefined) {
        setBooking(data);
        return;
      }
      if (response?.status === 409 || response?.status === 422) {
        // The slot is gone: forget it, refresh the list, keep the typed name and email.
        setSlotMessage(response.status === 409 ? CONFLICT_MESSAGE : INVALID_SLOT_MESSAGE);
        setSelectedSlot(null);
        setReloadKey((key) => key + 1);
        return;
      }
      const { messages, needsGeneralMessage } = placeFieldErrors(error?.fieldErrors, (name) => FIELD_BY_API_NAME[name]);
      setFieldErrors(messages);
      if (needsGeneralMessage) setGeneralError(GENERAL_ERROR);
    } catch {
      setGeneralError(GENERAL_ERROR);
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  };

  return (
    <>
      <h1>{meetingType.title}</h1>
      <p className="duration">{meetingType.durationMinutes} мин</p>
      <SlotPicker meetingTypeId={meetingType.id} reloadKey={reloadKey} selectedSlot={selectedSlot} onSelect={select} />
      {slotMessage && <p role="alert">{slotMessage}</p>}
      {selectedSlot !== null && (
        <section>
          <h2>Ваши данные</h2>
          <BookingForm
            values={guest}
            onChange={setGuest}
            fieldErrors={fieldErrors}
            generalError={generalError}
            submitting={submitting}
            onSubmit={() => void submit()}
          />
        </section>
      )}
      <Link to="/">Вернуться на главную</Link>
    </>
  );
}

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

  return (
    <main className="page">
      <Booker meetingType={state.data} />
    </main>
  );
}
