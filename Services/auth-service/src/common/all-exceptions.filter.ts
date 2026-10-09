import { ArgumentsHost, Catch, ExceptionFilter, HttpException, HttpStatus, Logger } from '@nestjs/common';
import { Response } from 'express';
import { ErrorCode } from './error-codes';

/**
 * The only way a failure leaves this service. Nest's default shape
 * ({ statusCode, message, error }) would give Sprint 9 a second envelope to handle.
 */
@Catch()
export class AllExceptionsFilter implements ExceptionFilter {
  private readonly log = new Logger(AllExceptionsFilter.name);

  catch(exception: unknown, host: ArgumentsHost): void {
    const response = host.switchToHttp().getResponse<Response>();

    if (exception instanceof HttpException) {
      const status = exception.getStatus();
      const body = exception.getResponse();

      // Already ours.
      if (typeof body === 'object' && body !== null && 'errorCode' in body) {
        response.status(status).json(body);
        return;
      }

      // Nest's own shape, including the validation pipe's array of messages.
      response.status(status).json({
        errorCode: status === HttpStatus.UNPROCESSABLE_ENTITY ? ErrorCode.VAL_422 : ErrorCode.AUTH_401,
        message: this.messageOf(body),
      });
      return;
    }

    // Never leak an internal message to a caller.
    this.log.error('unhandled exception', exception instanceof Error ? exception.stack : String(exception));
    response.status(HttpStatus.INTERNAL_SERVER_ERROR).json({
      errorCode: 'SRV-500',
      message: 'Internal server error',
    });
  }

  private messageOf(body: unknown): string {
    if (typeof body === 'string') return body;
    if (typeof body === 'object' && body !== null && 'message' in body) {
      const message = (body as { message: unknown }).message;
      return Array.isArray(message) ? message.join('; ') : String(message);
    }
    return 'Request failed';
  }
}
