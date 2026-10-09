import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { sharedConfig } from './config/shared-config';
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
      // Services/auth-service/.env, then the repository-root .env every service shares.
      // The process environment wins over both, so compose's values stand in a
      // container, where neither file exists. Never in tests.
      envFilePath: ['.env', '../../.env'],
      ignoreEnvFile: process.env.NODE_ENV === 'test',
      // Hosts and ports from the repository's shared Config/application.yml,
      // under both of the above. Never in tests, like the .env files.
      load: process.env.NODE_ENV === 'test' ? [] : [() => sharedConfig()],
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
