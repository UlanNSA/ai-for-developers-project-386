// Smoke test of a running Calendar app (container, Render, local). No dependencies: Node 24 fetch only.
//
//   node scripts/smoke.mjs <base-url> [--booking] [--wait <seconds>]
//
// Default: read-only checks that create nothing (safe against a public deployment).
// --booking: also runs the booking scenario. It CREATES data (one meeting type and one booking, both
// with obviously fictional values) and cannot delete it; the in-memory data disappears on restart.
// Never run --booking automatically against the public deployment; run it by hand when you need it.
import { pathToFileURL } from 'node:url';

const FICTIONAL_TYPE = { title: 'Smoke test meeting (fictional)', description: 'Created by scripts/smoke.mjs', durationMinutes: 15 };
const FICTIONAL_GUEST = { guestName: 'Smoke Test Guest', guestEmail: 'smoke-guest@example.invalid' };
const UNKNOWN_TYPE_ID = '00000000-0000-4000-8000-000000000000';

const isHtml = (response) => (response.headers.get('content-type') ?? '').includes('text/html');
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function waitForHealth(baseUrl, waitSeconds) {
  const deadline = Date.now() + waitSeconds * 1000;
  let lastError = 'no response';
  do {
    try {
      const response = await fetch(`${baseUrl}/api/health`, { signal: AbortSignal.timeout(5000) });
      if (response.ok) return null;
      lastError = `HTTP ${response.status}`;
    } catch (error) {
      lastError = String(error?.cause?.code ?? error?.message ?? error);
    }
    await sleep(1000);
  } while (Date.now() < deadline);
  return lastError;
}

export async function runSmoke({ baseUrl, booking = false, waitSeconds = 0, log = console.log }) {
  const base = baseUrl.replace(/\/+$/, '');
  const results = [];
  const check = async (name, fn) => {
    try {
      const detail = await fn();
      results.push({ name, ok: true });
      log(`ok    ${name}${detail ? ` (${detail})` : ''}`);
    } catch (error) {
      results.push({ name, ok: false, error: String(error?.message ?? error) });
      log(`FAIL  ${name}: ${error?.message ?? error}`);
    }
  };
  const expect = (condition, message) => {
    if (!condition) throw new Error(message);
  };
  const api = async (method, path, body) => {
    const response = await fetch(`${base}${path}`, {
      method,
      headers: body === undefined ? {} : { 'content-type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(15000),
    });
    const text = await response.text();
    let json;
    try {
      json = JSON.parse(text);
    } catch {
      json = undefined;
    }
    return { response, text, json };
  };

  const unreachable = await waitForHealth(base, waitSeconds);
  if (unreachable !== null) {
    results.push({ name: 'GET /api/health is reachable', ok: false, error: unreachable });
    log(`FAIL  GET /api/health is reachable: ${unreachable}`);
    return { ok: false, results };
  }

  await check('GET /api/health is {"status":"ok"}', async () => {
    const { json } = await api('GET', '/api/health');
    expect(json?.status === 'ok', `got ${JSON.stringify(json)}`);
  });
  for (const path of ['/', '/owner', `/book/${UNKNOWN_TYPE_ID}`]) {
    await check(`GET ${path} serves the app page (200, HTML)`, async () => {
      const { response, text } = await api('GET', path);
      expect(response.status === 200, `status ${response.status}`);
      expect(isHtml(response), `content-type ${response.headers.get('content-type')}`);
      expect(text.includes('id="root"'), 'no <div id="root"> in the page');
    });
  }
  await check('GET /api/unknown is 404 and not HTML', async () => {
    const { response, text } = await api('GET', '/api/unknown');
    expect(response.status === 404, `status ${response.status}`);
    expect(!isHtml(response) && !text.includes('id="root"'), 'answered with the HTML page');
  });
  await check('GET /api/owner returns the owner', async () => {
    const { response, json } = await api('GET', '/api/owner');
    expect(response.status === 200 && typeof json?.name === 'string' && typeof json?.timeZone === 'string', `got ${response.status}`);
  });
  await check('GET /api/meeting-types returns a list', async () => {
    const { response, json } = await api('GET', '/api/meeting-types');
    expect(response.status === 200 && Array.isArray(json), `got ${response.status}`);
    return `${json.length} types`;
  });

  if (booking) {
    let type;
    let slot;
    let created;
    await check('booking: create a fictional meeting type', async () => {
      const { response, json } = await api('POST', '/api/meeting-types', FICTIONAL_TYPE);
      expect(response.status === 201 && json?.id, `status ${response.status}`);
      type = json;
    });
    await check('booking: the type has free slots', async () => {
      expect(type, 'no meeting type');
      const { response, json } = await api('GET', `/api/meeting-types/${type.id}/slots`);
      expect(response.status === 200 && Array.isArray(json) && json.length > 0, `status ${response.status}, slots ${json?.length}`);
      slot = json[0];
      return slot.startAt;
    });
    await check('booking: first booking is created (201)', async () => {
      expect(type && slot, 'no slot to book');
      const { response, json } = await api('POST', '/api/bookings', { meetingTypeId: type.id, startAt: slot.startAt, ...FICTIONAL_GUEST });
      expect(response.status === 201 && json?.id && json.endAt, `status ${response.status}`);
      created = json;
    });
    await check('booking: second booking of the same slot is a conflict (409 SLOT_TAKEN)', async () => {
      expect(type && slot, 'no slot to book');
      const { response, json } = await api('POST', '/api/bookings', { meetingTypeId: type.id, startAt: slot.startAt, ...FICTIONAL_GUEST });
      expect(response.status === 409 && json?.code === 'SLOT_TAKEN', `status ${response.status}, body ${JSON.stringify(json)}`);
    });
    await check('booking: the booked slot is no longer offered', async () => {
      expect(type && slot, 'no slot');
      const { json } = await api('GET', `/api/meeting-types/${type.id}/slots`);
      expect(!json.some((s) => s.startAt === slot.startAt), 'the slot is still offered');
    });
    await check('booking: the booking is in the owner list (GET /api/bookings)', async () => {
      expect(created, 'no booking was created');
      const { json } = await api('GET', '/api/bookings');
      expect(Array.isArray(json) && json.some((b) => b.id === created.id), 'booking not listed');
    });
  }

  return { ok: results.every((r) => r.ok), results };
}

const isMain = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMain) {
  const args = process.argv.slice(2);
  const baseUrl = args.find((arg) => !arg.startsWith('--'));
  const waitIndex = args.indexOf('--wait');
  const waitSeconds = waitIndex >= 0 ? Number(args[waitIndex + 1]) : 0;
  if (!baseUrl || Number.isNaN(waitSeconds)) {
    console.error('Usage: node scripts/smoke.mjs <base-url> [--booking] [--wait <seconds>]');
    process.exit(2);
  }
  const report = await runSmoke({ baseUrl, booking: args.includes('--booking'), waitSeconds });
  const failed = report.results.filter((r) => !r.ok).length;
  console.log(report.ok ? `\nSmoke OK: ${report.results.length} checks` : `\nSmoke FAILED: ${failed} of ${report.results.length} checks`);
  process.exit(report.ok ? 0 : 1);
}
