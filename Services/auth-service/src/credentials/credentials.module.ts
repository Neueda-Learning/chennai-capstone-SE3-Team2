import { Module } from '@nestjs/common';
import { CredentialRepository } from './credential.repository';
import { PasswordHasher } from './password-hasher';

@Module({
  providers: [CredentialRepository, PasswordHasher],
  exports: [CredentialRepository, PasswordHasher],
})
export class CredentialsModule {}
