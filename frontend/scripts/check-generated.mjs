// Regenerates both clients into a scratch directory and diffs them against the
// committed tree. A contract that has moved ahead of src/generated/ is a stale
// client, whatever the build says.
//
//   npm run check:api        exit 0: committed output matches the contracts
//                            exit 1: it does not; run `npm run generate:api` and commit
//
// Needs Java, like generation itself. The build never does.
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, relative, resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const config = JSON.parse(readFileSync(join(root, 'openapitools.json'), 'utf8'));
const scratch = mkdtempSync(join(tmpdir(), 'check-generated-'));

function files(dir) {
  return readdirSync(dir, { recursive: true, withFileTypes: true })
    .filter((entry) => entry.isFile())
    .map((entry) => relative(dir, join(entry.parentPath, entry.name)))
    .sort();
}

try {
  const generators = config['generator-cli'].generators;
  const committed = {};
  for (const [key, generator] of Object.entries(generators)) {
    committed[key] = resolve(root, generator.output);
    generator.inputSpec = resolve(root, generator.inputSpec);
    generator.output = join(scratch, key);
  }
  delete config.$schema;
  const scratchConfig = join(scratch, 'openapitools.json');
  writeFileSync(scratchConfig, JSON.stringify(config, null, 2));

  execFileSync('npx', ['openapi-generator-cli', '--openapitools', scratchConfig, 'generate'], {
    cwd: root,
    stdio: ['ignore', 'ignore', 'inherit'],
  });

  const problems = [];
  for (const [key, committedDir] of Object.entries(committed)) {
    const fresh = join(scratch, key);
    const want = files(fresh);
    const have = files(committedDir);
    for (const file of want.filter((f) => !have.includes(f))) problems.push(`missing:  ${key}/${file}`);
    for (const file of have.filter((f) => !want.includes(f))) problems.push(`extra:    ${key}/${file} (the generator did not write this)`);
    for (const file of want.filter((f) => have.includes(f))) {
      if (readFileSync(join(fresh, file), 'utf8') !== readFileSync(join(committedDir, file), 'utf8')) {
        problems.push(`changed:  ${key}/${file}`);
      }
    }
  }

  if (problems.length) {
    console.error('src/generated/ does not match the contracts:\n  ' + problems.join('\n  '));
    console.error('Run `npm run generate:api` and commit the result with the code that adapts to it.');
    process.exitCode = 1;
  } else {
    console.log('src/generated/ matches contracts/ exactly.');
  }
} finally {
  rmSync(scratch, { recursive: true, force: true });
}
