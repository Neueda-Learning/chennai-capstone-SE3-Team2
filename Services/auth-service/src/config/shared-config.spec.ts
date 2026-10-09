import { mkdtempSync, writeFileSync } from 'fs';
import { tmpdir } from 'os';
import { join } from 'path';
import { sharedConfig } from './shared-config';

describe('sharedConfig', () => {
  const file = (yaml: string) => {
    const path = join(mkdtempSync(join(tmpdir(), 'shared-config-')), 'application.yml');
    writeFileSync(path, yaml);
    return path;
  };

  it('reads the hosts and ports this service needs from Config/application.yml', () => {
    const path = file(`
frontend: { url: http://localhost, port: 4200 }
services: { auth: { url: http://localhost, port: 3000 } }
database: { host: localhost, port: 5432 }
kafka: { host: localhost, port: 9092 }
`);
    expect(sharedConfig([path])).toEqual({
      PORT: '3000',
      DB_HOST: 'localhost',
      DB_PORT: '5432',
      KAFKA_BROKERS: 'localhost:9092',
      CORS_ALLOWED_ORIGINS: 'http://localhost:4200',
      ACTIVATION_HOME_URL: 'http://localhost:4200/',
    });
  });

  it('gives nothing when no shared file exists, so the environment alone decides', () => {
    expect(sharedConfig([join(tmpdir(), 'no-such-dir', 'application.yml')])).toEqual({});
  });
});
