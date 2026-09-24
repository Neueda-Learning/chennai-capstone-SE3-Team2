import { IsString, MaxLength } from 'class-validator';

export class RefreshDto {
  @IsString()
  @MaxLength(512)
  refreshToken: string;
}
