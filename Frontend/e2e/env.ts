/**
 * Every address and credential the journeys use, from the environment under
 * the declared names and nowhere else, so every spec stays on one set and no
 * credential is written into a file.
 */
const NAMES = [
  'E2E_BASE_URL',
  'E2E_TRADE_API',
  'E2E_AUTH_API',
  'E2E_USERNAME',
  'E2E_PASSWORD',
  'E2E_ACCOUNT_ID',
  'E2E_SYMBOL',
] as const;

type Name = (typeof NAMES)[number];

function read(): Record<Name, string> {
  const missing = NAMES.filter((name) => !process.env[name]);
  if (missing.length > 0) {
    throw new Error(`Set ${missing.join(', ')} before running the journeys; see Frontend/README.md, "End-to-end journeys".`);
  }
  return Object.fromEntries(NAMES.map((name) => [name, process.env[name]!.trim()])) as Record<Name, string>;
}

const values = read();

export const env = {
  baseUrl: values.E2E_BASE_URL,
  tradeApi: new URL(values.E2E_TRADE_API).origin,
  authApi: new URL(values.E2E_AUTH_API).origin,
  username: values.E2E_USERNAME,
  password: values.E2E_PASSWORD,
  accountId: values.E2E_ACCOUNT_ID,
  symbol: values.E2E_SYMBOL,
} as const;
