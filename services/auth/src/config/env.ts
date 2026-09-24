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

  get jwtSecret(): string { return this.required('JWT_SECRET'); }
  get jwtIssuer(): string { return this.config.get('JWT_ISSUER') ?? 'auth-service'; }
  get port(): number { return Number(this.config.get('PORT') ?? 3000); }

  get databaseUrl(): string { return this.required('AUTH_DATABASE_URL'); }
}
