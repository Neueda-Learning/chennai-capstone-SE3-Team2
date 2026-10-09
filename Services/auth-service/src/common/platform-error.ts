import { HttpException, HttpStatus } from '@nestjs/common';
import { ErrorCode, UNAUTHORISED_MESSAGE } from './error-codes';

/** An error that leaves in the platform envelope: { errorCode, message }. */
export class PlatformError extends HttpException {
  constructor(readonly errorCode: string, message: string, status: HttpStatus) {
    super({ errorCode, message }, status);
  }

  /** Every authentication failure, whatever its cause, answers identically. */
  static unauthorised(): PlatformError {
    return new PlatformError(ErrorCode.AUTH_401, UNAUTHORISED_MESSAGE, HttpStatus.UNAUTHORIZED);
  }

  static usernameTaken(): PlatformError {
    return new PlatformError(ErrorCode.AUTH_409, 'Username already registered', HttpStatus.CONFLICT);
  }

  static invalidInput(message: string): PlatformError {
    return new PlatformError(ErrorCode.VAL_422, message, HttpStatus.UNPROCESSABLE_ENTITY);
  }

  /** Internal callers only, which is why the answer can be specific. */
  static notProvisioned(): PlatformError {
    return new PlatformError(ErrorCode.ACT_404, 'Account not provisioned', HttpStatus.NOT_FOUND);
  }

  /** Internal callers only: no login is bound to the account, so nobody can be acted for. */
  static noLogin(): PlatformError {
    return new PlatformError(ErrorCode.ACT_404, 'Account has no login', HttpStatus.NOT_FOUND);
  }

  static alreadyClaimed(): PlatformError {
    return new PlatformError(ErrorCode.ACT_409, 'Account already has a login', HttpStatus.CONFLICT);
  }
}
