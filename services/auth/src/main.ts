import { ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import { AllExceptionsFilter } from './common/all-exceptions.filter';
import { PlatformError } from './common/platform-error';
import { Env } from './config/env';
import { RedactingLogger } from './common/redacting-logger';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create(AppModule, { logger: new RedactingLogger() });

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

  const env = app.get(Env);
  await app.listen(env.port);
}

void bootstrap();
