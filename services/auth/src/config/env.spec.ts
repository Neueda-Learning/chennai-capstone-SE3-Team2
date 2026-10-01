import { ConfigService } from '@nestjs/config';
import { Env } from './env';

const complete = {
  JWT_SECRET: 'a-test-secret-of-at-least-32-bytes-length',
  AUTH_DATABASE_URL: 'postgresql://auth:x@localhost:5433/auth',
  KAFKA_BROKERS: 'kafka:29092',
  ACTIVATION_INTERNAL_SECRET: 'an-internal-test-secret-of-32-plus-bytes',
};

const envWith = (values: Record<string, string | undefined>) =>
  new Env({ get: (key: string) => values[key] } as unknown as ConfigService);

describe('Env', () => {
  it('starts when every required variable is present', () => {
    expect(() => envWith(complete).assertRequired()).not.toThrow();
  });

  it.each(Object.keys(complete))('refuses to start without %s, and names it', (missing) => {
    expect(() => envWith({ ...complete, [missing]: undefined }).assertRequired()).toThrow(`${missing} is not set`);
  });

  it('reads a comma-separated broker list', () => {
    expect(envWith({ ...complete, KAFKA_BROKERS: 'a:9092, b:9092' }).kafkaBrokers).toEqual(['a:9092', 'b:9092']);
  });
});
