import { Module } from '@nestjs/common';
import { JwtModule } from '@nestjs/jwt';
import { AccessTokenService } from './access-token.service';
import { RefreshTokenRepository } from './refresh-token.repository';
import { RefreshTokenService } from './refresh-token.service';

@Module({
  imports: [JwtModule.register({})],
  providers: [AccessTokenService, RefreshTokenService, RefreshTokenRepository],
  exports: [AccessTokenService, RefreshTokenService, JwtModule],
})
export class TokensModule {}
