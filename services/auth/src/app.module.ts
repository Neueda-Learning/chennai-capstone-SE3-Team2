import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { ActivationModule } from './activation/activation.module';
import { AuthModule } from './auth/auth.module';
import { DatabaseModule } from './database/database.module';
import { MessagingModule } from './messaging/messaging.module';
import { ProvisioningModule } from './provisioning/provisioning.module';
import { StrategyTokenModule } from './strategy/strategy-token.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      // services/auth/.env, then the repository-root .env every service shares.
      // The process environment wins over both, so compose's values stand in a
      // container, where neither file exists. Never in tests.
      envFilePath: ['.env', '../../.env'],
      ignoreEnvFile: process.env.NODE_ENV === 'test',
    }),
    DatabaseModule,
    MessagingModule,
    AuthModule,
    ProvisioningModule,
    ActivationModule,
    StrategyTokenModule,
  ],
})
export class AppModule {}
