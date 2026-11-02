import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { after, test } from 'node:test';
import { fileURLToPath } from 'node:url';

import { checkVersions } from './version-check.mjs';

const scriptPath = join(dirname(fileURLToPath(import.meta.url)), 'version-check.mjs');
const tmpRoots = [];

after(() => {
  for (const dir of tmpRoots) rmSync(dir, { recursive: true, force: true });
});

/** Builds a fake repository where every version is `version`, then applies `overrides`. */
function makeRepo(overrides = {}) {
  const version = overrides.version ?? '0.1.0';
  const root = mkdtempSync(join(tmpdir(), 'version-check-'));
  tmpRoots.push(root);
  const write = (rel, content) => {
    mkdirSync(dirname(join(root, rel)), { recursive: true });
    writeFileSync(join(root, rel), content);
  };
  write('version.txt', overrides['version.txt'] ?? `${version}\n`);
  for (const pkg of ['api', 'frontend', 'e2e']) {
    write(
      `${pkg}/package.json`,
      overrides[`${pkg}/package.json`] ?? JSON.stringify({ name: pkg, version }, null, 2),
    );
    write(
      `${pkg}/package-lock.json`,
      overrides[`${pkg}/package-lock.json`] ??
        JSON.stringify({ name: pkg, version, packages: { '': { name: pkg, version } } }, null, 2),
    );
  }
  write(
    'backend/build.gradle.kts',
    overrides['backend/build.gradle.kts'] ??
      `group = "calendar"\nversion = "${version}" // x-release-please-version\n`,
  );
  return root;
}

const lock = (version, rootVersion) =>
  JSON.stringify({ version, packages: { '': { version: rootVersion } } });

test('all versions match', () => {
  const result = checkVersions(makeRepo());
  assert.deepEqual(result.errors, []);
  assert.equal(result.version, '0.1.0');
});

test('version.txt mismatch is reported', () => {
  const { errors } = checkVersions(makeRepo({ 'version.txt': '0.1.1\n' }));
  // version.txt is the reference, so every other file is reported as differing from it
  assert.equal(errors.length, 10);
  assert.ok(errors.every((e) => /version\.txt/.test(e)));
});

test('package.json mismatch is reported', () => {
  const { errors } = checkVersions(
    makeRepo({ 'frontend/package.json': JSON.stringify({ version: '0.1.1' }) }),
  );
  assert.equal(errors.length, 1);
  assert.match(errors[0], /frontend\/package\.json/);
  assert.match(errors[0], /0\.1\.1/);
});

test('package-lock.json top-level version mismatch is reported', () => {
  const { errors } = checkVersions(makeRepo({ 'api/package-lock.json': lock('0.2.0', '0.1.0') }));
  assert.equal(errors.length, 1);
  assert.match(errors[0], /api\/package-lock\.json/);
});

test('package-lock.json packages[""] version mismatch is reported', () => {
  const { errors } = checkVersions(makeRepo({ 'e2e/package-lock.json': lock('0.1.0', '0.2.0') }));
  assert.equal(errors.length, 1);
  assert.match(errors[0], /e2e\/package-lock\.json/);
  assert.match(errors[0], /packages/);
});

test('Gradle version mismatch is reported', () => {
  const { errors } = checkVersions(
    makeRepo({ 'backend/build.gradle.kts': 'version = "0.3.0" // x-release-please-version\n' }),
  );
  assert.equal(errors.length, 1);
  assert.match(errors[0], /build\.gradle\.kts/);
});

test('Gradle version line without the release-please annotation is reported', () => {
  const { errors } = checkVersions(
    makeRepo({ 'backend/build.gradle.kts': 'version = "0.1.0"\n' }),
  );
  assert.equal(errors.length, 1);
  assert.match(errors[0], /x-release-please-version/);
});

test('missing files are reported, not thrown', () => {
  const root = makeRepo();
  rmSync(join(root, 'version.txt'));
  rmSync(join(root, 'backend/build.gradle.kts'));
  const { errors } = checkVersions(root);
  assert.equal(errors.length, 2);
  assert.ok(errors.some((e) => /version\.txt/.test(e) && /missing|cannot read/i.test(e)));
  assert.ok(errors.some((e) => /build\.gradle\.kts/.test(e)));
});

test('invalid JSON is reported', () => {
  const { errors } = checkVersions(makeRepo({ 'api/package.json': '{ nope' }));
  assert.equal(errors.length, 1);
  assert.match(errors[0], /api\/package\.json/);
});

test('CLI exits 0 on match and non-zero with a readable message on mismatch', () => {
  const ok = spawnSync(process.execPath, [scriptPath, makeRepo()], { encoding: 'utf8' });
  assert.equal(ok.status, 0);
  const bad = spawnSync(
    process.execPath,
    [scriptPath, makeRepo({ 'version.txt': '9.9.9\n' })],
    { encoding: 'utf8' },
  );
  assert.equal(bad.status, 1);
  assert.match(bad.stderr, /version\.txt/);
});
