import { Injectable, OnModuleInit } from '@nestjs/common';
import { PasswordHasher } from '../credentials/password-hasher';
import { PlatformError } from '../common/platform-error';

/**
 * Makes an unknown username cost the same as a wrong password.
 *
 * If the unknown-user path returns as soon as the lookup misses, it answers in
 * a millisecond while the wrong-password path spends a tenth of a second
 * verifying a hash. An attacker with a username list and a stopwatch then reads
 * the customer base off the response times without guessing a password.
 *
 * So the miss verifies the supplied password against a dummy hash of the same
 * algorithm and the same parameters, discards the result, and fails identically.
 */
@Injectable()
export class LoginFailure implements OnModuleInit {
  /** Hashed once at startup, not per request: hashing it each time doubles the cost. */
  private dummyHash: string;

  constructor(private readonly hasher: PasswordHasher) {}

  async onModuleInit(): Promise<void> {
    this.dummyHash = await this.hasher.hash('a password nobody has, used only to burn the same time');
  }

  /** The username was not found. Do the work anyway, then fail. */
  async unknownUser(suppliedPassword: string): Promise<never> {
    await this.hasher.verify(this.dummyHash, suppliedPassword);
    throw PlatformError.unauthorised();
  }

  /** The username was found and the password did not match. */
  wrongPassword(): never {
    throw PlatformError.unauthorised();
  }
}
