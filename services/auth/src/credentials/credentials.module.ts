import { Module } from '@nestjs/common';
import { CredentialRepository } from './credential.repository';

@Module({
  providers: [CredentialRepository],
  exports: [CredentialRepository],
})
export class CredentialsModule {}
