import { Body, Controller, HttpCode, HttpStatus, Post, UseGuards } from '@nestjs/common';
import { ApiExcludeController } from '@nestjs/swagger';
import { InternalSecretGuard } from '../activation/internal-secret.guard';
import { PlatformError } from '../common/platform-error';
import { CredentialRepository } from '../credentials/credential.repository';
import { AccessTokenService } from '../tokens/access-token.service';
import { STRATEGY_TOKEN_SECONDS } from '../tokens/claims';
import { MintStrategyTokenDto, StrategyTokenResponseDto } from './strategy-token.dto';

/**
 * Sprint 10, decision log 0012: a strategy fires when nobody is signed in, so
 * the Trade REST API asks here, behind the service secret, for an access
 * token five minutes long for the strategy's account. It then places the
 * order through POST /api/v1/orders like any customer: the route's checks
 * stand between a strategy bug and a position. Not in Contracts/API Schemas/auth-api.yaml,
 * and no browser should call it.
 */
@ApiExcludeController()
@Controller('internal')
@UseGuards(InternalSecretGuard)
export class StrategyTokenController {
  constructor(
    private readonly credentials: CredentialRepository,
    private readonly tokens: AccessTokenService,
  ) {}

  @Post('strategy-tokens')
  @HttpCode(HttpStatus.CREATED)
  async mint(@Body() dto: MintStrategyTokenDto): Promise<StrategyTokenResponseDto> {
    const credential = await this.credentials.findByAccountId(dto.accountId);
    if (!credential) {
      throw PlatformError.noLogin();
    }
    return { accessToken: await this.tokens.issueForStrategy(credential), tokenType: 'Bearer', expiresIn: STRATEGY_TOKEN_SECONDS };
  }
}
