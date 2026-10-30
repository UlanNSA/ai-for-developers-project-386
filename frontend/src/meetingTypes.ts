// Static data until the backend exposes meeting types (see docs/domain-rules.md).
export interface MeetingType {
  id: string;
  title: string;
  description?: string;
  durationMinutes: number;
}

export const meetingTypes: MeetingType[] = [
  {
    id: 'intro-call',
    title: 'Знакомство',
    description: 'Короткий разговор, чтобы познакомиться и обсудить задачу.',
    durationMinutes: 15,
  },
  {
    id: 'consultation',
    title: 'Консультация',
    description: 'Подробный разбор вашего вопроса с рекомендациями.',
    durationMinutes: 30,
  },
  {
    id: 'workshop',
    title: 'Рабочая сессия',
    durationMinutes: 60,
  },
];

export function findMeetingType(id: string | undefined): MeetingType | undefined {
  return meetingTypes.find((meetingType) => meetingType.id === id);
}
