// Regenerates every client in src/generated/ from openapitools.json.
//
//   npm run generate:api
//
// Specs are staged to a space-free path first (see stage-specs.mjs). Needs Java.
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { stageSpecs } from './stage-specs.mjs';

const root = resolve(import.meta.dirname, '..');
const config = JSON.parse(readFileSync(join(root, 'openapitools.json'), 'utf8'));
const scratch = mkdtempSync(join(tmpdir(), 'generate-api-'));

try {
  const generators = config['generator-cli'].generators;
  for (const generator of Object.values(generators)) {
    generator.output = resolve(root, generator.output);
  }
  stageSpecs(generators, root, scratch);
  delete config.$schema;
  const scratchConfig = join(scratch, 'openapitools.json');
  writeFileSync(scratchConfig, JSON.stringify(config, null, 2));
  execFileSync('npx', ['openapi-generator-cli', '--openapitools', scratchConfig, 'generate'], { cwd: root, stdio: 'inherit' });
} finally {
  rmSync(scratch, { recursive: true, force: true });
}
