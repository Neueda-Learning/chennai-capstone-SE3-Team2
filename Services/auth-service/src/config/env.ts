import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

/** Typed access to the environment. Secrets have no defaults. */
@Injectable()
export class Env {
  constructor(private readonly config: ConfigService) {}

  /** Required: the service refuses to start rather than signing with a guess. */
  private required(key: string): string {
    const value = this.config.get<string>(key);
    if (!value) {
      throw new Error(`${key} is not set`);
    }
    return value;
  }

  /** Called once at bootstrap, so a missing variable stops the process before it listens. */
  assertRequired(): void {
    void this.jwtSecret;
    void this.databaseUrl;
    void this.kafkaBrokers;
    void this.activationInternalSecret;
  }

  get jwtSecret(): string { return this.required('JWT_SECRET'); }
  get jwtIssuer(): string { return this.config.get('JWT_ISSUER') ?? 'auth-service'; }
  /** From Config/application.yml (services.auth.port), or PORT. */
  get port(): number { return Number(this.required('PORT')); }

  /**
   * AUTH_DATABASE_URL when given (compose builds one). Otherwise built from
   * the parts the repository-root .env already holds for compose and the
   * Windows setup, so the auth password is written down once.
   */
  get databaseUrl(): string {
    const url = this.config.get<string>('AUTH_DATABASE_URL');
    if (url) {
      return url;
    }
    const password = this.config.get<string>('AUTH_DB_PASSWORD');
    if (!password) {
      throw new Error('AUTH_DATABASE_URL is not set, nor AUTH_DB_PASSWORD to build it from');
    }
    const user = this.config.get<string>('AUTH_DB_USER') || 'auth_app';
    const host = this.required('DB_HOST');
    const port = this.required('DB_PORT');
    const name = this.config.get<string>('AUTH_DB_NAME') || 'auth';
    return `postgresql://${encodeURIComponent(user)}:${encodeURIComponent(password)}@${host}:${port}/${name}`;
  }

  get kafkaBrokers(): string[] {
    return this.required('KAFKA_BROKERS').split(',').map((b) => b.trim()).filter(Boolean);
  }

  /** Shared with the Trade REST API's activation package; guards the token-minting route. */
  get activationInternalSecret(): string { return this.required('ACTIVATION_INTERNAL_SECRET'); }

  /** Where a customer lands after registering from the activation link. */
  get activationHomeUrl(): string { return this.required('ACTIVATION_HOME_URL'); }

  /**
   * The login throttle's shared counter. Optional: without it, or with Redis
   * down, the throttle counts in memory and the limit is per instance.
   */
  get redisUrl(): string | undefined { return this.config.get('REDIS_URL') || undefined; }

  get outboxPollMs(): number { return Number(this.config.get('OUTBOX_POLL_MS') ?? 2000); }

  /**
   * Browser origins allowed to call this service: the trading UI. Exact
   * origins, comma-separated -- an allow list, never a pattern.
   */
  get corsAllowedOrigins(): string[] {
    return this.required('CORS_ALLOWED_ORIGINS')
      .split(',')
      .map((origin) => origin.trim())
      .filter(Boolean);
  }
}
