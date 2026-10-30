import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { getOwner, type Owner } from './api/generated';
import { meetingTypes } from './meetingTypes';

type OwnerState = { status: 'loading' } | { status: 'error' } | { status: 'ready'; owner: Owner };

function useOwner(): OwnerState {
  const [state, setState] = useState<OwnerState>({ status: 'loading' });

  useEffect(() => {
    let cancelled = false;
    getOwner()
      .then(({ data }) => {
        if (!cancelled) setState(data ? { status: 'ready', owner: data } : { status: 'error' });
      })
      .catch(() => {
        if (!cancelled) setState({ status: 'error' });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return state;
}

function OwnerBlock({ state }: { state: OwnerState }) {
  if (state.status === 'loading') {
    return <p>Загрузка…</p>;
  }
  if (state.status === 'error') {
    return <p role="alert">Не удалось загрузить данные владельца. Попробуйте обновить страницу позже.</p>;
  }
  return (
    <>
      <h2 className="owner-name">{state.owner.name}</h2>
      <p>{state.owner.bio}</p>
    </>
  );
}

export function HomePage() {
  const ownerState = useOwner();

  return (
    <main className="page">
      <h1>Календарь звонков</h1>
      <section className="owner">
        <OwnerBlock state={ownerState} />
      </section>
      <section className="cards">
        {meetingTypes.map((meetingType) => (
          <article key={meetingType.id} className="card">
            <h2>{meetingType.title}</h2>
            {meetingType.description && <p>{meetingType.description}</p>}
            <p className="duration">{meetingType.durationMinutes} мин</p>
            <Link className="button" to={`/book/${meetingType.id}`}>
              Выбрать время
            </Link>
          </article>
        ))}
      </section>
    </main>
  );
}
