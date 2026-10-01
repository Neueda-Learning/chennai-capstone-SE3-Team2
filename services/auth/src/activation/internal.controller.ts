import { Body, Controller, HttpCode, HttpStatus, Post, UseGuards } from '@nestjs/common';
import { ApiExcludeController } from '@nestjs/swagger';
import { ActivationTokenService } from './activation-token.service';
import { ActivationTokenResponseDto, MintActivationTokenDto } from './dto/mint-activation-token.dto';
import { InternalSecretGuard } from './internal-secret.guard';

/**
 * Service-to-service routes. Excluded from the public OpenAPI document: they
 * are not part of contracts/auth-api.yaml and no browser should call them.
 */
@ApiExcludeController()
@Controller('internal')
@UseGuards(InternalSecretGuard)
export class InternalController {
  constructor(private readonly tokens: ActivationTokenService) {}

  @Post('activation-tokens')
  @HttpCode(HttpStatus.CREATED)
  async mint(@Body() dto: MintActivationTokenDto): Promise<ActivationTokenResponseDto> {
    const { activationToken, expiresAt } = await this.tokens.mint(dto.clientId);
    return { activationToken, expiresAt: expiresAt.toISOString() };
  }
}
