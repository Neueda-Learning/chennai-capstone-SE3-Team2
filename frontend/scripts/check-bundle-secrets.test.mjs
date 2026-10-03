// node --test scripts/   (npm run test:scripts)
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, test } from 'node:test';
import { scan, secretsFrom } from './check-bundle-secrets.mjs';

const dirs = [];
function bundle(files) {
  const dir = mkdtempSync(join(tmpdir(), 'bundle-'));
  dirs.push(dir);
  for (const [name, text] of Object.entries(files)) writeFileSync(join(dir, name), text);
  return dir;
}
after(() => dirs.forEach((dir) => rmSync(dir, { recursive: true, force: true })));

test('a clean bundle has no findings', () => {
  const dir = bundle({ 'main.js': 'const tradeApiUrl="http://localhost:8080";headers.Authorization="Bearer "+t;' });
  assert.deepEqual(scan(dir), []);
});

test('finds each pattern the brief names', () => {
  const cases = {
    'a.js': 'h["X-API-KEY"]=k',
    'b.js': 'base="https://abc123.execute-api.eu-west-2.amazonaws.com/v1"',
    'c.js': 'const jwt_secret=1',
    'd.js': 'const signingSecret="a-very-long-literal-value-here"',
    'e.js': 'const t="eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U"',
    'f.js': 'title="Fauxnance quotes"',
  };
  const found = scan(bundle(cases)).map((f) => f.file).sort();
  assert.deepEqual(found, Object.keys(cases));
});

test('finds a secret by its literal value under any name, and reports the name, not the value', () => {
  const secrets = secretsFrom('JWT_SECRET=s3cr3t-value-of-some-length\nLOG_LEVEL=INFO\n# FAUXNANCE_API_KEY=commented-out-value\n');
  assert.deepEqual([...secrets.keys()], ['JWT_SECRET']);

  const findings = scan(bundle({ 'main.js': 'const harmless="s3cr3t-value-of-some-length"' }), secrets);
  assert.equal(findings.length, 1);
  assert.equal(findings[0].what, 'the literal value of JWT_SECRET');
  assert.ok(!JSON.stringify(findings).includes('s3cr3t'));
});

test('flags a source map shipped in the bundle', () => {
  const findings = scan(bundle({ 'main.js.map': '{"sources":["src/app.ts"]}' }));
  assert.equal(findings[0].what, 'a source map: the source, back again');
});
