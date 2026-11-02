// Byte-for-byte comparison of the freshly compiled contract (api/.tmp/openapi)
// with the committed one (api/openapi). Independent of Git state: only files
// on disk are compared.
import { readFileSync } from 'node:fs';

const fresh = new URL('../.tmp/openapi/openapi.yaml', import.meta.url);
const committed = new URL('../openapi/openapi.yaml', import.meta.url);

const a = readFileSync(committed);
const b = readFileSync(fresh);

if (a.equals(b)) {
  console.log('OpenAPI contract is up to date (committed file == fresh build).');
  process.exit(0);
}

const left = a.toString('utf8').split('\n');
const right = b.toString('utf8').split('\n');
let line = 0;
while (line < left.length && line < right.length && left[line] === right[line]) line++;

console.error('API DRIFT: api/openapi/openapi.yaml does not match a fresh build of api/*.tsp.');
console.error(`First difference at line ${line + 1}:`);
console.error(`  committed: ${JSON.stringify(left[line] ?? '<end of file>')}`);
console.error(`  fresh:     ${JSON.stringify(right[line] ?? '<end of file>')}`);
console.error('Full diff: git diff --no-index api/openapi/openapi.yaml api/.tmp/openapi/openapi.yaml');
console.error('Fix: run `mise run api:update` and commit api/openapi/openapi.yaml together with the .tsp change.');
process.exit(1);
