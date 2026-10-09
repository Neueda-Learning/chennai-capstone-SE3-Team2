import { Injectable } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { Credential } from '../credentials/credential.repository';
import { Env } from '../config/env';
import { AccessTokenClaims, ACCESS_TOKEN_SECONDS, STRATEGY_ROLE, STRATEGY_TOKEN_SECONDS } from './claims';

@Injectable()
export class AccessTokenService {
  constructor(private readonly jwt: JwtService, private readonly env: Env) {}

  /** HS256, and the account comes from the credential rather than from any request. */
  issue(credential: Credential): Promise<string> {
    return this.jwt.signAsync(
      {
        sub: credential.id,
        accountId: credential.accountId,
        roles: credential.roles,
      },
      {
        secret: this.env.jwtSecret,
        algorithm: 'HS256',
        issuer: this.env.jwtIssuer,
        expiresIn: ACCESS_TOKEN_SECONDS,
      },
    );
  }

  /**
   * Sprint 10: a token for a strategy acting for this credential's account,
   * five minutes long, marked with the STRATEGY role. The same claims, the
   * same key, the same issuer: the order route checks it like any other.
   */
  issueForStrategy(credential: Credential): Promise<string> {
    return this.jwt.signAsync(
      {
        sub: credential.id,
        accountId: credential.accountId,
        roles: [...credential.roles, STRATEGY_ROLE],
      },
      {
        secret: this.env.jwtSecret,
        algorithm: 'HS256',
        issuer: this.env.jwtIssuer,
        expiresIn: STRATEGY_TOKEN_SECONDS,
      },
    );
  }

  /**
   * Verifies the signature and the clock before any claim is read. Throws on
   * an expired, tampered or malformed token; the caller turns that into
   * AUTH-401 without saying which it was.
   */
  verify(token: string): Promise<AccessTokenClaims> {
    return this.jwt.verifyAsync<AccessTokenClaims>(token, {
      secret: this.env.jwtSecret,
      algorithms: ['HS256'],
    });
  }
}
