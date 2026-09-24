import { ArgumentsHost, ExceptionFilter } from '@nestjs/common';
export declare class AllExceptionsFilter implements ExceptionFilter {
    private readonly log;
    catch(exception: unknown, host: ArgumentsHost): void;
    private messageOf;
}
