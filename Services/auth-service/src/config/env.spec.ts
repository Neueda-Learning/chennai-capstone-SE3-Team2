import { ConfigService } from '@nestjs/config';
import { Env } from './env';

const complete = {
  JWT_SECRET: 'a-test-secret-of-at-least-32-bytes-length',
  AUTH_DATABASE_URL: 'postgresql://auth_app:x@localhost:5432/auth',
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

  it('takes the allowed origins from configuration as a list, and never guesses one', () => {
    expect(() => envWith(complete).corsAllowedOrigins).toThrow('CORS_ALLOWED_ORIGINS is not set');
    expect(
      envWith({ ...complete, CORS_ALLOWED_ORIGINS: 'http://localhost:4200, https://ui.example' }).corsAllowedOrigins,
    ).toEqual(['http://localhost:4200', 'https://ui.example']);
  });

  it('builds the database URL from the shared .env parts when AUTH_DATABASE_URL is not set', () => {
    const parts = { ...complete, AUTH_DATABASE_URL: undefined, AUTH_DB_PASSWORD: 'p@ss:w/rd', DB_HOST: 'localhost', DB_PORT: '5432' };
    expect(envWith(parts).databaseUrl).toBe('postgresql://auth_app:p%40ss%3Aw%2Frd@localhost:5432/auth');
    expect(envWith({ ...parts, AUTH_DB_USER: 'other', AUTH_DB_NAME: 'auth2' }).databaseUrl).toBe(
      'postgresql://other:p%40ss%3Aw%2Frd@localhost:5432/auth2',
    );
  });

  it('prefers a full AUTH_DATABASE_URL over the parts', () => {
    expect(envWith({ ...complete, AUTH_DB_PASSWORD: 'ignored' }).databaseUrl).toBe(complete.AUTH_DATABASE_URL);
  });

  it('reads a comma-separated broker list', () => {
    expect(envWith({ ...complete, KAFKA_BROKERS: 'a:9092, b:9092' }).kafkaBrokers).toEqual(['a:9092', 'b:9092']);
  });
});
