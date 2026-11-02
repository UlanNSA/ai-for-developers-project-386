import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeAll, expect, test, vi } from 'vitest';
import type { Slot } from './api/generated';
import { client } from './api/generated/client.gen';
import { SlotPicker } from './SlotPicker';
import { meetingTypes } from './testApi';

// The guest time zone is fixed by `test.env.TZ` in vite.config.ts (Asia/Tbilisi, UTC+4, no DST).

beforeAll(() => {
  client.setConfig({ baseUrl: 'http://localhost' });
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const monday: Slot = { startAt: '2026-11-02T04:00:00Z', endAt: '2026-11-02T04:15:00Z' };
const tuesday: Slot = { startAt: '2026-11-03T04:00:00Z', endAt: '2026-11-03T04:15:00Z' };

/** Serves the given slot lists one per request, the last one repeating. */
function stubSlotResponses(...responses: Slot[][]) {
  let call = 0;
  vi.stubGlobal('fetch', async () => Response.json(responses[Math.min(call++, responses.length - 1)]));
}

function renderPicker(reloadKey: number, selectedSlot: string | null, onSelect = vi.fn()) {
  const element = (key: number, slot: string | null) => (
    <SlotPicker meetingTypeId={meetingTypes[0].id} reloadKey={key} selectedSlot={slot} onSelect={onSelect} />
  );
  const view = render(element(reloadKey, selectedSlot));
  return {
    onSelect,
    reload: (key: number, slot: string | null = null) => {
      view.rerender(element(key, slot));
    },
  };
}

const timeHeading = () => screen.queryByRole('heading', { name: 'Выберите время' });

test('a selected day that vanishes after a reload is forgotten, not revived when it returns', async () => {
  stubSlotResponses([monday, tuesday], [tuesday], [monday, tuesday]);
  const picker = renderPicker(0, null);
  fireEvent.click(await screen.findByRole('button', { name: 'пн, 2 ноября' }));
  expect(timeHeading()).not.toBeNull();

  picker.reload(1);
  await waitFor(() => {
    expect(screen.queryByRole('button', { name: 'пн, 2 ноября' })).toBeNull();
  });
  expect(await screen.findByRole('button', { name: 'вт, 3 ноября' })).toBeTruthy();
  expect(timeHeading()).toBeNull();

  picker.reload(2);
  const returned = await screen.findByRole('button', { name: 'пн, 2 ноября' });
  expect(returned.getAttribute('aria-pressed')).toBe('false');
  expect(timeHeading()).toBeNull();
});

test('a selected slot that is gone after a reload is cleared', async () => {
  stubSlotResponses([monday, tuesday], [tuesday]);
  const picker = renderPicker(0, monday.startAt);
  await screen.findByRole('button', { name: 'пн, 2 ноября' });

  picker.reload(1, monday.startAt);

  await waitFor(() => {
    expect(picker.onSelect).toHaveBeenCalledWith(null);
  });
});
