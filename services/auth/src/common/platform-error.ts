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
}
