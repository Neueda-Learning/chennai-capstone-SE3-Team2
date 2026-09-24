import { HttpException, HttpStatus } from '@nestjs/common';
export declare class PlatformError extends HttpException {
    readonly errorCode: string;
    constructor(errorCode: string, message: string, status: HttpStatus);
    static unauthorised(): PlatformError;
    static usernameTaken(): PlatformError;
    static invalidInput(message: string): PlatformError;
}
