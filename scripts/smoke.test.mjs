import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { after, before, describe, test } from 'node:test';
import { runSmoke } from './smoke.mjs';

const INDEX = '<!doctype html><html><body><div id="root"></div></body></html>';

/** A tiny in-memory imitation of the app, enough to drive the smoke checks. `broken` switches faults on. */
function fakeApp(broken = {}) {
  const types = [];
  const bookings = [];
  const slotStart = '2026-11-03T03:00:00Z';
  return createServer((req, res) => {
    let body = '';
    req.on('data', (chunk) => (body += chunk));
    req.on('end', () => {
      const json = (status, value) => {
        res.writeHead(status, { 'content-type': 'application/json' });
        res.end(JSON.stringify(value));
      };
      const html = () => {
        res.writeHead(200, { 'content-type': 'text/html' });
        res.end(INDEX);
      };
      const url = req.url ?? '/';
      if (req.method === 'GET' && url === '/') return broken.root ? json(404, {}) : html();
      if (req.method === 'GET' && (url === '/owner' || url.startsWith('/book/'))) return html();
      if (req.method === 'GET' && url === '/api/health') return json(200, { status: 'ok' });
      if (url.startsWith('/api/unknown')) return broken.apiHtml ? html() : (res.writeHead(404), res.end());
      if (req.method === 'GET' && url === '/api/owner') return json(200, { name: 'Owner', bio: 'Bio', timeZone: 'Asia/Almaty' });
      if (req.method === 'GET' && url === '/api/meeting-types') return json(200, types);
      if (req.method === 'POST' && url === '/api/meeting-types') {
        const type = { id: `00000000-0000-4000-8000-00000000000${types.length + 1}`, ...JSON.parse(body) };
        types.push(type);
        return json(201, type);
      }
      if (req.method === 'GET' && /^\/api\/meeting-types\/[^/]+\/slots$/.test(url)) {
        const free = bookings.some((b) => b.startAt === slotStart) ? [] : [{ startAt: slotStart, endAt: '2026-11-03T03:15:00Z' }];
        // One slot only: after it is booked the list is empty (the booking scenario then asks for a fresh type).
        return json(200, broken.slotsNeverShrink ? [{ startAt: slotStart, endAt: '2026-11-03T03:15:00Z' }] : free);
      }
      if (req.method === 'POST' && url === '/api/bookings') {
        const draft = JSON.parse(body);
        if (!broken.noConflict && bookings.some((b) => b.startAt === draft.startAt)) {
          return json(409, { code: 'SLOT_TAKEN', message: 'taken' });
        }
        const booking = {
          id: `10000000-0000-4000-8000-00000000000${bookings.length + 1}`,
          endAt: '2026-11-03T03:15:00Z',
          ...draft,
        };
        bookings.push(booking);
        return json(201, booking);
      }
      if (req.method === 'GET' && url === '/api/bookings') return json(200, bookings);
      return json(404, {});
    });
  });
}

describe('smoke', () => {
  const servers = [];
  const start = async (broken) => {
    const server = fakeApp(broken);
    servers.push(server);
    await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
    return `http://127.0.0.1:${server.address().port}`;
  };
  before(() => {});
  after(() => servers.forEach((server) => server.close()));

  test('read-only checks pass and create no data', async () => {
    const baseUrl = await start();
    const report = await runSmoke({ baseUrl, log: () => {} });
    assert.equal(report.ok, true, JSON.stringify(report.results));
    assert.ok(report.results.every((r) => !r.name.includes('booking')));
  });

  test('the booking scenario creates a booking and sees the second one conflict', async () => {
    const baseUrl = await start();
    const report = await runSmoke({ baseUrl, booking: true, log: () => {} });
    assert.equal(report.ok, true, JSON.stringify(report.results));
    const names = report.results.map((r) => r.name).join('|');
    assert.match(names, /second booking .*409/i);
    assert.match(names, /owner list/i);
  });

  test('a missing root page fails the run', async () => {
    const baseUrl = await start({ root: true });
    const report = await runSmoke({ baseUrl, log: () => {} });
    assert.equal(report.ok, false);
    assert.ok(report.results.some((r) => !r.ok && r.name.includes('GET /')));
  });

  test('an unknown /api path answering with HTML fails the run', async () => {
    const baseUrl = await start({ apiHtml: true });
    const report = await runSmoke({ baseUrl, log: () => {} });
    assert.equal(report.ok, false);
  });

  test('a conflict that is not reported fails the booking scenario', async () => {
    const baseUrl = await start({ noConflict: true });
    const report = await runSmoke({ baseUrl, booking: true, log: () => {} });
    assert.equal(report.ok, false);
  });

  test('a booked slot that is still offered fails the booking scenario', async () => {
    const baseUrl = await start({ slotsNeverShrink: true });
    const report = await runSmoke({ baseUrl, booking: true, log: () => {} });
    assert.equal(report.ok, false);
    assert.ok(report.results.some((r) => !r.ok && r.name.includes('no longer offered')));
  });

  test('an unreachable server fails instead of hanging', async () => {
    const report = await runSmoke({ baseUrl: 'http://127.0.0.1:1', waitSeconds: 1, log: () => {} });
    assert.equal(report.ok, false);
  });
});
