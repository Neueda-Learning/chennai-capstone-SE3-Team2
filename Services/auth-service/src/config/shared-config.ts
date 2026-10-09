import { existsSync, readFileSync } from 'fs';
import { load } from 'js-yaml';

/**
 * Where the repository's shared Config/application.yml is found: from the
 * repository root, from this service's folder, or mounted at /config in a
 * container. The first that exists is read.
 */
export const SHARED_CONFIG_PATHS = ['Config/application.yml', '../../Config/application.yml', '/config/application.yml'];

interface Endpoint {
  url?: string;
  host?: string;
  port?: number | string;
}

interface SharedConfig {
  frontend?: Endpoint;
  services?: { auth?: Endpoint };
  database?: Endpoint;
  kafka?: Endpoint;
}

/**
 * The shared file, as the variables this service reads. Only hosts and
 * ports: no secret is ever in that file. ConfigModule looks in the process
 * environment (and .env) first, so a variable of the same name always wins.
 */
export function sharedConfig(paths: string[] = SHARED_CONFIG_PATHS): Record<string, string> {
  const path = paths.find((candidate) => existsSync(candidate));
  if (!path) {
    return {};
  }
  const config = (load(readFileSync(path, 'utf8')) ?? {}) as SharedConfig;
  const values: Record<string, string> = {};
  const set = (key: string, value: unknown) => {
    if (value !== undefined && value !== null && `${value}` !== '') {
      values[key] = String(value);
    }
  };

  set('PORT', config.services?.auth?.port);
  set('DB_HOST', config.database?.host);
  set('DB_PORT', config.database?.port);
  if (config.kafka?.host && config.kafka?.port) {
    set('KAFKA_BROKERS', `${config.kafka.host}:${config.kafka.port}`);
  }
  if (config.frontend?.url && config.frontend?.port) {
    const ui = `${config.frontend.url}:${config.frontend.port}`;
    set('CORS_ALLOWED_ORIGINS', ui);
    set('ACTIVATION_HOME_URL', `${ui}/`);
  }
  return values;
}
