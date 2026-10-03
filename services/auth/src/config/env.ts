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
  get port(): number { return Number(this.config.get('PORT') ?? 3000); }

  get databaseUrl(): string { return this.required('AUTH_DATABASE_URL'); }

  get kafkaBrokers(): string[] {
    return this.required('KAFKA_BROKERS').split(',').map((b) => b.trim()).filter(Boolean);
  }

  /** Shared with the Trade REST API's activation package; guards the token-minting route. */
  get activationInternalSecret(): string { return this.required('ACTIVATION_INTERNAL_SECRET'); }

  /** Where a customer lands after registering from the activation link. */
  get activationHomeUrl(): string { return this.config.get('ACTIVATION_HOME_URL') ?? 'http://localhost:4200/'; }

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
    return (this.config.get<string>('CORS_ALLOWED_ORIGINS') ?? 'http://localhost:4200')
      .split(',')
      .map((origin) => origin.trim())
      .filter(Boolean);
  }
}
