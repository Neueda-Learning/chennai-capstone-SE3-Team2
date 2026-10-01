import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { ActivationPageController } from './activation-page.controller';
import { ActivationTokenRepository } from './activation-token.repository';
import { ActivationTokenService } from './activation-token.service';
import { InternalController } from './internal.controller';
import { InternalSecretGuard } from './internal-secret.guard';

@Module({
  imports: [AuthModule],
  controllers: [InternalController, ActivationPageController],
  providers: [ActivationTokenService, ActivationTokenRepository, InternalSecretGuard],
})
export class ActivationModule {}
