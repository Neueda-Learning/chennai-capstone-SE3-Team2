import { Module } from '@nestjs/common';
import { InternalSecretGuard } from '../activation/internal-secret.guard';
import { CredentialsModule } from '../credentials/credentials.module';
import { TokensModule } from '../tokens/tokens.module';
import { StrategyTokenController } from './strategy-token.controller';

/** The internal route that mints a strategy's token (Sprint 10, decision log 0012). */
@Module({
  imports: [CredentialsModule, TokensModule],
  controllers: [StrategyTokenController],
  providers: [InternalSecretGuard],
})
export class StrategyTokenModule {}
