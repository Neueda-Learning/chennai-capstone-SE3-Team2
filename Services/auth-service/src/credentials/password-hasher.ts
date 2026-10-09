import { Injectable } from '@nestjs/common';
import * as argon2 from 'argon2';

/**
 * argon2id, with cost parameters chosen against a measurement rather than a
 * default. A general-purpose digest is built to be fast, and fast is the one
 * property a password hash must not have.
 *
 * Measured on the deployment hardware, 5 verifications per setting:
 *
 *    19 MiB t=2 p=1   21.7 ms   OWASP floor, faster than we want
 *    64 MiB t=3 p=4   38.3 ms   the library default
 *    64 MiB t=4 p=1  135.4 ms   chosen
 *   128 MiB t=3 p=1  202.2 ms   login becomes the cheapest thing to flood
 *
 * parallelism = 1 is deliberate. At p=4 a single login competes for four
 * cores, so concurrent logins fight each other; at p=1 they scale across them.
 */
export const HASH_OPTIONS: argon2.Options = {
  type: argon2.argon2id,
  memoryCost: 65536,
  timeCost: 4,
  parallelism: 1,
};

@Injectable()
export class PasswordHasher {
  hash(plaintext: string): Promise<string> {
    return argon2.hash(plaintext, HASH_OPTIONS);
  }

  async verify(hash: string, plaintext: string): Promise<boolean> {
    try {
      return await argon2.verify(hash, plaintext);
    } catch {
      // A malformed stored hash must not crash the login path.
      return false;
    }
  }
}
