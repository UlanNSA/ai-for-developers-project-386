import type { MeetingType, Owner } from './api/generated';

// Typed against the generated SDK: a contract change breaks type-checking here.
export const owner: Owner = { name: 'Анна Петрова', bio: 'Консультирую по продукту', timeZone: 'Asia/Almaty' };

export const meetingTypes: MeetingType[] = [
  {
    id: '11111111-1111-4111-8111-111111111111',
    title: 'Знакомство',
    description: 'Короткий разговор.',
    durationMinutes: 15,
  },
  { id: '22222222-2222-4222-8222-222222222222', title: 'Консультация', durationMinutes: 30 },
];

const notFound = () =>
  Response.json({ code: 'MEETING_TYPE_NOT_FOUND', message: 'Тип встречи не найден' }, { status: 404 });

/** Fetch stub that routes the SDK's requests by path like the real backend would. */
export function backendStub(items: MeetingType[] = meetingTypes) {
  return async (input: Request | string | URL) => {
    const path = new URL(input instanceof Request ? input.url : String(input)).pathname;
    if (path === '/api/owner') return Response.json(owner);
    if (path === '/api/meeting-types') return Response.json(items);
    const id = /^\/api\/meeting-types\/(.+)$/.exec(path)?.[1];
    const found = items.find((item) => item.id === id);
    return found ? Response.json(found) : notFound();
  };
}
