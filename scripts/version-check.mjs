// Checks that the single monorepo version is identical everywhere release-please writes it:
// version.txt, package.json and package-lock.json of api/frontend/e2e, backend/build.gradle.kts.
// Usage: node scripts/version-check.mjs [repoRoot]   (exit 1 with a message on any mismatch)
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const PACKAGES = ['api', 'frontend', 'e2e'];
const GRADLE_FILE = 'backend/build.gradle.kts';

/** Returns { version, errors }; never throws for missing or malformed files. */
export function checkVersions(root) {
  const errors = [];
  const found = []; // { label, version }

  const read = (rel) => {
    try {
      return readFileSync(join(root, rel), 'utf8');
    } catch {
      errors.push(`${rel}: file is missing or cannot be read`);
      return undefined;
    }
  };
  const readJson = (rel) => {
    const text = read(rel);
    if (text === undefined) return undefined;
    try {
      return JSON.parse(text);
    } catch {
      errors.push(`${rel}: invalid JSON`);
      return undefined;
    }
  };
  const record = (label, value) => {
    if (typeof value === 'string' && value !== '') found.push({ label, version: value });
    else errors.push(`${label}: version field not found`);
  };

  const txt = read('version.txt');
  if (txt !== undefined) record('version.txt', txt.trim());

  for (const pkg of PACKAGES) {
    const manifest = readJson(`${pkg}/package.json`);
    if (manifest) record(`${pkg}/package.json (version)`, manifest.version);
    const lockfile = readJson(`${pkg}/package-lock.json`);
    if (lockfile) {
      record(`${pkg}/package-lock.json (version)`, lockfile.version);
      record(`${pkg}/package-lock.json (packages[""].version)`, lockfile.packages?.['']?.version);
    }
  }

  const gradle = read(GRADLE_FILE);
  if (gradle !== undefined) {
    const line = gradle.split('\n').find((l) => /^version\s*=\s*"/.test(l));
    const match = line?.match(/^version\s*=\s*"([^"]+)"\s*\/\/\s*x-release-please-version\s*$/);
    if (match) record(`${GRADLE_FILE} (version)`, match[1]);
    else if (line)
      errors.push(
        `${GRADLE_FILE}: version line must be \`version = "X.Y.Z" // x-release-please-version\`, got: ${line.trim()}`,
      );
    else errors.push(`${GRADLE_FILE}: version line not found`);
  }

  // The reference is version.txt (the release-please `simple` source of truth), else the first found.
  const reference = found.find((f) => f.label === 'version.txt') ?? found[0];
  if (reference) {
    for (const f of found) {
      if (f.version !== reference.version)
        errors.push(`${f.label}: ${f.version} differs from ${reference.label}: ${reference.version}`);
    }
  }
  return { version: reference?.version, errors };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = process.argv[2]
    ? resolve(process.argv[2])
    : resolve(dirname(fileURLToPath(import.meta.url)), '..');
  const { version, errors } = checkVersions(root);
  if (errors.length > 0) {
    console.error('Version check failed:');
    for (const e of errors) console.error(`  - ${e}`);
    process.exit(1);
  }
  console.log(`Version check OK: all versions are ${version}`);
}
