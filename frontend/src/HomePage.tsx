import { Link } from 'react-router';
import { meetingTypes, owner } from './meetingTypes';

export function HomePage() {
  return (
    <main className="page">
      <h1>Календарь звонков</h1>
      <section className="owner">
        <h2 className="owner-name">{owner.name}</h2>
        <p>{owner.bio}</p>
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
