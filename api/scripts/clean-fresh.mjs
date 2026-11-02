// Removes the fixed scratch directory used by `api:build` so a drift check
// never sees leftovers from an earlier run.
import { rmSync } from 'node:fs';

rmSync(new URL('../.tmp/openapi', import.meta.url), { recursive: true, force: true });
