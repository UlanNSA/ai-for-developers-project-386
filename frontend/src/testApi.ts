import type { MeetingType, Owner, Slot } from './api/generated';

// Typed against the generated SDK: a contract change breaks type-checking here.
export const owner: Owner = {
  name: 'Анна Петрова',
  bio: 'Консультирую по продукту',
  timeZone: 'Asia/Almaty',
};

export const meetingTypes: MeetingType[] = [
  {
    id: '11111111-1111-4111-8111-111111111111',
    title: 'Знакомство',
    description: 'Короткий разговор.',
    durationMinutes: 15,
  },
  {
    id: '22222222-2222-4222-8222-222222222222',
    title: 'Консультация',
    durationMinutes: 30,
  },
];

export const slots: Slot[] = [
  { startAt: '2026-11-02T04:00:00Z', endAt: '2026-11-02T04:15:00Z' },
  { startAt: '2026-11-02T04:15:00Z', endAt: '2026-11-02T04:30:00Z' },
  { startAt: '2026-11-03T04:00:00Z', endAt: '2026-11-03T04:15:00Z' },
];

const notFound = () =>
  Response.json({ code: 'MEETING_TYPE_NOT_FOUND', message: 'Тип встречи не найден' }, { status: 404 });

/** Fetch stub that routes the SDK's requests by path like the real backend would. */
export function backendStub(
  items: MeetingType[] = meetingTypes,
  freeSlots: Slot[] = slots,
  onCreateBooking: (request: Request) => Response | Promise<Response> = () => new Response(null, { status: 500 }),
) {
  return async (input: Request | string | URL) => {
    const path = new URL(input instanceof Request ? input.url : String(input)).pathname;
    if (path === '/api/bookings' && input instanceof Request) return onCreateBooking(input);
    if (path === '/api/owner') return Response.json(owner);
    if (path === '/api/meeting-types') return Response.json(items);
    const match = /^\/api\/meeting-types\/([^/]+)(\/slots)?$/.exec(path);
    const found = items.find((item) => item.id === match?.[1]);
    if (!found) return notFound();
    return Response.json(match?.[2] ? freeSlots : found);
  };
}
