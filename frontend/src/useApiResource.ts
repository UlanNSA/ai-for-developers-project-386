import { useEffect, useState } from 'react';

export type ResourceState<T> =
  | { status: 'loading' }
  | { status: 'error'; notFound: boolean }
  | { status: 'ready'; data: T };

interface SdkResult<T> {
  data?: T;
  response?: Response;
}

/** Loads one SDK resource: loading until it settles, then ready or error (a 404 is flagged). */
export function useApiResource<T>(load: () => Promise<SdkResult<T>>, key: string): ResourceState<T> {
  const [state, setState] = useState<{ key: string; value: ResourceState<T> } | null>(null);

  useEffect(() => {
    let cancelled = false;
    const finish = (value: ResourceState<T>) => {
      if (!cancelled) setState({ key, value });
    };
    load()
      .then(({ data, response }) => {
        finish(
          data === undefined
            ? { status: 'error', notFound: response?.status === 404 }
            : { status: 'ready', data },
        );
      })
      .catch(() => {
        finish({ status: 'error', notFound: false });
      });
    return () => {
      cancelled = true;
    };
    // `load` is recreated on every render; `key` identifies the request.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  return state?.key === key ? state.value : { status: 'loading' };
}
