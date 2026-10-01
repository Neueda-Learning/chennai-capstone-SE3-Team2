import { INestApplication, ValidationPipe } from '@nestjs/common';
import { AllExceptionsFilter } from './common/all-exceptions.filter';
import { PlatformError } from './common/platform-error';

/** The request pipeline, shared by main.ts and the HTTP-level tests so both run the same one. */
export function configureApp(app: INestApplication): void {
  app.useGlobalFilters(new AllExceptionsFilter());
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
      // A field failure is VAL-422, not Nest's default 400.
      exceptionFactory: (errors) =>
        PlatformError.invalidInput(
          errors.map((e) => Object.values(e.constraints ?? {}).join(', ')).join('; '),
        ),
    }),
  );
}
