import { useEffect, useState } from 'react';
import { getHealth } from './api/generated';

type Status = 'loading' | 'ok' | 'error';

export function App() {
  const [status, setStatus] = useState<Status>('loading');

  useEffect(() => {
    let cancelled = false;
    getHealth().then(({ data }) => {
      if (!cancelled) setStatus(data?.status === 'ok' ? 'ok' : 'error');
    });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <main>
      <h1>Календарь звонков</h1>
      <p>Статус backend: {status}</p>
    </main>
  );
}
