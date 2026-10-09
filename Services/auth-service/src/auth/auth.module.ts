import { Module } from '@nestjs/common';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { JwtAuthGuard } from './jwt-auth.guard';
import { LoginFailure } from './login-failure';
import { LoginThrottleGuard } from './login-throttle.guard';
import { LoginAttempts } from './login-attempts';
import { ATTEMPT_STORE } from './attempt-store';
import { attemptStoreFor } from './fallback-attempt-store';
import { Env } from '../config/env';
import { CredentialsModule } from '../credentials/credentials.module';
import { TokensModule } from '../tokens/tokens.module';

@Module({
  imports: [CredentialsModule, TokensModule],
  controllers: [AuthController],
  providers: [
    AuthService, JwtAuthGuard, LoginFailure, LoginThrottleGuard, LoginAttempts,
    // Redis when REDIS_URL is set, so the limit is shared by every instance.
    { provide: ATTEMPT_STORE, inject: [Env], useFactory: (env: Env) => attemptStoreFor(env.redisUrl) },
  ],
  exports: [AuthService],
})
export class AuthModule {}
