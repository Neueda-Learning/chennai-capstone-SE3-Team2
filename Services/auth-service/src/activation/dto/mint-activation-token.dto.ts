import { IsInt, IsPositive } from 'class-validator';

export class MintActivationTokenDto {
  @IsInt()
  @IsPositive()
  clientId: number;
}

export class ActivationTokenResponseDto {
  /** Returned once. The service keeps only its hash. */
  activationToken: string;
  expiresAt: string;
}
