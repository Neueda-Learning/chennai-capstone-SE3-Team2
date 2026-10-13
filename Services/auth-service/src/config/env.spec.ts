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
    const parts = {
      ...complete, AUTH_DATABASE_URL: undefined, AUTH_DB_PASSWORD: 'p@ss:w/rd',
      DB_HOST: 'localhost', DB_PORT: '5432', DB_NAME: 'trading_system_db',
    };
    // One database for the platform; auth lives in its own schema inside it.
    expect(envWith(parts).databaseUrl).toBe('postgresql://auth_app:p%40ss%3Aw%2Frd@localhost:5432/trading_system_db');
    expect(envWith({ ...parts, AUTH_DB_USER: 'other', AUTH_DB_NAME: 'auth2' }).databaseUrl).toBe(
      'postgresql://other:p%40ss%3Aw%2Frd@localhost:5432/auth2',
    );
  });

  it('asks for encryption when DB_SSLMODE says so: require encrypts without verifying, as libpq does', () => {
    const parts = {
      ...complete, AUTH_DATABASE_URL: undefined, AUTH_DB_PASSWORD: 'pw',
      DB_HOST: 'db.example', DB_PORT: '5432', DB_NAME: 'trading_system_db',
    };
    expect(envWith({ ...parts, DB_SSLMODE: 'require' }).databaseUrl).toBe(
      'postgresql://auth_app:pw@db.example:5432/trading_system_db?sslmode=no-verify',
    );
    expect(envWith({ ...parts, DB_SSLMODE: 'verify-full' }).databaseUrl).toMatch(/\?sslmode=verify-full$/);
    expect(envWith({ ...parts, DB_SSLMODE: 'disable' }).databaseUrl).not.toContain('sslmode');
    expect(envWith(parts).databaseUrl).not.toContain('sslmode');
  });

  it('works in the auth schema by default, and refuses a schema name that is not a plain identifier', () => {
    expect(envWith(complete).databaseSchema).toBe('auth');
    expect(envWith({ ...complete, AUTH_DB_SCHEMA: 'auth_v2' }).databaseSchema).toBe('auth_v2');
    expect(() => envWith({ ...complete, AUTH_DB_SCHEMA: 'auth; drop table x' }).databaseSchema).toThrow('AUTH_DB_SCHEMA');
  });

  it('prefers a full AUTH_DATABASE_URL over the parts', () => {
    expect(envWith({ ...complete, AUTH_DB_PASSWORD: 'ignored' }).databaseUrl).toBe(complete.AUTH_DATABASE_URL);
  });

  it('trusts a proxy only when TRUST_PROXY names one', () => {
    expect(envWith(complete).trustProxy).toBeUndefined();
    expect(envWith({ ...complete, TRUST_PROXY: 'uniquelocal' }).trustProxy).toBe('uniquelocal');
  });

  it('reads a comma-separated broker list', () => {
    expect(envWith({ ...complete, KAFKA_BROKERS: 'a:9092, b:9092' }).kafkaBrokers).toEqual(['a:9092', 'b:9092']);
  });
});
