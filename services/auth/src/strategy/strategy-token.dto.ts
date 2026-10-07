import { IsInt, IsPositive } from 'class-validator';

export class MintStrategyTokenDto {
  @IsInt()
  @IsPositive()
  accountId: number;
}

export class StrategyTokenResponseDto {
  accessToken: string;
  tokenType: 'Bearer';
  /** Seconds. */
  expiresIn: number;
}
