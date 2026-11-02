import { Link } from 'react-router';
import { getOwner, listMeetingTypes, type MeetingType, type Owner } from './api/generated';
import { type ResourceState, useApiResource } from './useApiResource';

function OwnerBlock({ state }: { state: ResourceState<Owner> }) {
  if (state.status === 'loading') {
    return <p>Загрузка…</p>;
  }
  if (state.status === 'error') {
    return <p role="alert">Не удалось загрузить данные владельца. Попробуйте обновить страницу позже.</p>;
  }
  return (
    <>
      <h2 className="owner-name">{state.data.name}</h2>
      <p>{state.data.bio}</p>
    </>
  );
}

function MeetingTypeCards({ state }: { state: ResourceState<MeetingType[]> }) {
  if (state.status === 'loading') {
    return <p>Загрузка…</p>;
  }
  if (state.status === 'error') {
    return <p role="alert">Не удалось загрузить типы встреч. Попробуйте обновить страницу позже.</p>;
  }
  if (state.data.length === 0) {
    return <p>Типов встреч пока нет</p>;
  }
  return (
    <div className="cards">
      {state.data.map((meetingType) => (
        <article key={meetingType.id} className="card">
          <h2>{meetingType.title}</h2>
          {meetingType.description && <p>{meetingType.description}</p>}
          <p className="duration">{meetingType.durationMinutes} мин</p>
          <Link className="button" to={`/book/${meetingType.id}`}>
            Выбрать время
          </Link>
        </article>
      ))}
    </div>
  );
}

export function HomePage() {
  const ownerState = useApiResource(() => getOwner(), 'owner');
  const meetingTypesState = useApiResource(() => listMeetingTypes(), 'meeting-types');

  return (
    <main className="page">
      <h1>Календарь звонков</h1>
      <section className="owner">
        <OwnerBlock state={ownerState} />
      </section>
      <section>
        <MeetingTypeCards state={meetingTypesState} />
      </section>
    </main>
  );
}
