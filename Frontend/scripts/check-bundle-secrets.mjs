// Searches the production bundle for anything that must never reach a browser.
//
//   npm run check:bundle      builds for production, then scans dist/
//   exit 0: clean             exit 1: something was found -- see below
//
// There is no private part of a front-end build: every file in dist/ is
// downloaded by every browser that opens the application. Minification is not
// obfuscation, and a source map is the source back again.
//
// Two searches:
//   1. Patterns: an API-key header or name, the market-data service's name or
//      host, a signing-secret name, a secret assigned a long literal, a JWT.
//   2. Literal values: every secret-looking value in the repository's .env
//      (and the same names in the environment), so a key pasted in under a
//      name no pattern would match is still caught. A hit names the variable,
//      never prints the value.
//
// A key that reached a bundle has been published. Revoke it; deleting the
// line does not unpublish it.
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export const PATTERNS = [
  { name: 'an API-key header or name (x-api-key, api_key, api-key)', regex: /x-api-key|api_key|api-key/i },
  { name: 'the market-data service by name (fauxnance)', regex: /fauxnance/i },
  { name: 'an AWS API Gateway host (execute-api.<region>.amazonaws.com)', regex: /execute-api\.[a-z0-9-]+\.amazonaws\.com/i },
  { name: 'a signing-secret name (jwt_secret)', regex: /jwt[_-]?secret/i },
  { name: 'a secret assigned a long literal', regex: /secret["']?\s*[:=]\s*["'`][^"'`\s]{16,}["'`]/i },
  { name: 'a three-part JWT written into source', regex: /eyJ[\w-]{10,}\.eyJ[\w-]{10,}\.[\w-]{10,}/ },
];

/** .env names whose values are secrets worth searching for literally. */
const SECRET_NAME = /SECRET|PASSWORD|KEY|TOKEN|REDIS_URL|DATABASE_URL/i;

/** name -> value, for every secret-looking variable in an env file. */
export function secretsFrom(envFileText) {
  const secrets = new Map();
  for (const line of envFileText.split(/\r?\n/)) {
    const match = /^\s*([A-Za-z_][\w.-]*)\s*=\s*(.*)\s*$/.exec(line);
    if (!match || line.trimStart().startsWith('#')) continue;
    const [, name, raw] = match;
    const value = raw.replace(/^(['"])(.*)\1$/, '$2');
    if (SECRET_NAME.test(name) && value.length >= 8) secrets.set(name, value);
  }
  return secrets;
}

function filesUnder(dir) {
  return readdirSync(dir, { recursive: true, withFileTypes: true })
    .filter((entry) => entry.isFile())
    .map((entry) => join(entry.parentPath, entry.name));
}

/** Every finding in the bundle at `dir`: { file, what }. Never includes a secret's value. */
export function scan(dir, secrets = new Map()) {
  const findings = [];
  for (const file of filesUnder(dir)) {
    const shown = relative(dir, file);
    if (file.endsWith('.map')) {
      findings.push({ file: shown, what: 'a source map: the source, back again' });
    }
    const text = readFileSync(file, 'latin1');
    for (const { name, regex } of PATTERNS) {
      if (regex.test(text)) findings.push({ file: shown, what: name });
    }
    for (const [name, value] of secrets) {
      if (text.includes(value)) findings.push({ file: shown, what: `the literal value of ${name}` });
    }
  }
  return findings;
}

function main() {
  const root = resolve(import.meta.dirname, '..');
  const dist = join(root, 'dist');
  if (!existsSync(dist)) {
    console.error('No dist/ to scan. Run `npm run build` first (or `npm run check:bundle`, which does).');
    process.exit(1);
  }

  const envFile = process.env.BUNDLE_SECRETS_ENV_FILE ?? join(root, '..', '.env');
  const shownEnv = process.env.BUNDLE_SECRETS_ENV_FILE ?? relative(root, envFile);
  const secrets = existsSync(envFile) ? secretsFrom(readFileSync(envFile, 'utf8')) : new Map();
  for (const [name, value] of Object.entries(process.env)) {
    if (SECRET_NAME.test(name) && value && value.length >= 8 && !name.startsWith('npm_')) secrets.set(name, value);
  }

  const files = filesUnder(dist).length;
  const findings = scan(dist, secrets);
  if (findings.length > 0) {
    console.error(`FOUND in the production bundle (${files} files scanned):`);
    for (const { file, what } of findings) console.error(`  ${file}: ${what}`);
    console.error('A key or secret that reached a bundle is published. Revoke it -- deleting the line does not unpublish it.');
    process.exit(1);
  }
  console.log(
    `Clean: ${files} files in dist/ searched for ${PATTERNS.length} patterns and the literal values of ` +
      `${secrets.size} secrets${existsSync(envFile) ? ` from ${shownEnv} and the environment` : ' from the environment'}.`,
  );
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  main();
}
