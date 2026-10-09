import { ConsoleLogger, LogLevel } from '@nestjs/common';

/** Key names whose values never reach a log, at any depth. */
const SECRET_KEYS = [
  'password', 'passwordhash', 'newpassword', 'currentpassword',
  'token', 'accesstoken', 'refreshtoken', 'authorization',
  'secret', 'jwtsecret', 'apikey',
];

export const REDACTED = '[REDACTED]';

/**
 * The only logger this service uses.
 *
 * The direct route to a leak is easy to find by searching for a log call that
 * names a credential field. The routes that matter are indirect: an error
 * serialised whole, a DTO printed in a stack trace, an interceptor dumping a
 * request body. Redacting by key name at any depth covers all of them.
 */
export class RedactingLogger extends ConsoleLogger {
  protected printMessages(messages: unknown[], context?: string, logLevel?: LogLevel, ...rest: any[]): void {
    super.printMessages(messages.map((m) => redact(m)), context, logLevel, ...rest);
  }
}

export function redact(value: unknown, seen = new WeakSet<object>()): unknown {
  if (typeof value === 'string' || value === null || value === undefined) {
    return value;
  }
  if (typeof value !== 'object') {
    return value;
  }
  if (seen.has(value as object)) {
    return '[Circular]';
  }
  seen.add(value as object);

  if (Array.isArray(value)) {
    return value.map((item) => redact(item, seen));
  }

  // An Error carries its message and stack, and a DTO may be attached to it.
  if (value instanceof Error) {
    return { name: value.name, message: value.message };
  }

  const out: Record<string, unknown> = {};
  for (const [key, inner] of Object.entries(value)) {
    out[key] = SECRET_KEYS.includes(key.toLowerCase()) ? REDACTED : redact(inner, seen);
  }
  return out;
}
