// Static data until the backend exposes meeting types (see docs/domain-rules.md).
export interface MeetingType {
  id: string;
  title: string;
  description?: string;
  durationMinutes: number;
}

export interface Owner {
  name: string;
  bio: string;
}

export const owner: Owner = {
  name: 'Алексей Иванов',
  bio: 'Помогаю командам с архитектурой и разработкой. Выберите формат встречи и запишитесь в удобное время.',
};

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
