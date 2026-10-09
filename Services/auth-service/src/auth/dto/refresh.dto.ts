import { ApiProperty } from '@nestjs/swagger';
import { IsString, MaxLength } from 'class-validator';

export class RefreshDto {
  @ApiProperty({ example: '9c1f7a2e4b6d8e0a2c4e6a8c0e2a4c6e8a0c2e4a6c8e0a2c' })
  @IsString()
  @MaxLength(512)
  refreshToken: string;
}
