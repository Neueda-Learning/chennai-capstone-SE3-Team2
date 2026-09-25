import { Module } from '@nestjs/common';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { JwtAuthGuard } from './jwt-auth.guard';
import { LoginFailure } from './login-failure';
import { LoginThrottleGuard } from './login-throttle.guard';
import { CredentialsModule } from '../credentials/credentials.module';
import { TokensModule } from '../tokens/tokens.module';

@Module({
  imports: [CredentialsModule, TokensModule],
  controllers: [AuthController],
  providers: [AuthService, JwtAuthGuard, LoginFailure, LoginThrottleGuard],
})
export class AuthModule {}
