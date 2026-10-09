import { NestFactory } from '@nestjs/core';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { AppModule } from './app.module';
import { configureApp } from './app-setup';
import { Env } from './config/env';
import { corsOptions } from './common/cors';
import { RedactingLogger } from './common/redacting-logger';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create(AppModule, { logger: new RedactingLogger() });

  // Before anything listens: a missing secret stops the process, naming the variable.
  app.get(Env).assertRequired();

  configureApp(app);

  // The trading UI calls this service from another origin. Only the origins
  // listed are answered; every other browser origin is refused.
  app.enableCors(corsOptions(app.get(Env).corsAllowedOrigins));

  // Generated from the decorators on the controller and the DTOs. A YAML file
  // maintained by hand beside the code drifts within a fortnight; this is the
  // evidence that what is deployed still matches Contracts/API Schemas/auth-api.yaml.
  const document = SwaggerModule.createDocument(
    app,
    new DocumentBuilder()
      .setTitle('Auth service')
      .setDescription('Registration, login, refresh and the protected profile route.')
      .setVersion('1.0.0')
      .addBearerAuth()
      .build(),
  );
  SwaggerModule.setup('docs', app, document, { jsonDocumentUrl: 'docs/json' });

  const env = app.get(Env);
  await app.listen(env.port);
}

void bootstrap();
