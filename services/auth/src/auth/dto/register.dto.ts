import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsArray, IsInt, IsOptional, IsPositive, IsString, Matches, MaxLength, MinLength } from 'class-validator';

export class RegisterDto {
  @ApiProperty({ example: 'priya.menon', minLength: 3, maxLength: 64 })
  @IsString()
  @MinLength(3)
  @MaxLength(64)
  @Matches(/^[a-zA-Z0-9._-]+$/, { message: 'username may contain letters, digits, dot, underscore and hyphen' })
  username: string;

  @ApiProperty({ example: 'correct horse battery staple', minLength: 12, writeOnly: true })
  @IsString()
  @MinLength(12)
  @MaxLength(128)
  password: string;

  /** The account this user will trade. It must already be provisioned. */
  @ApiProperty({ example: 1, description: 'The provisioned trading account this user will trade.' })
  @IsInt()
  @IsPositive()
  accountId: number;

  @ApiPropertyOptional({ example: ['CUSTOMER'], isArray: true })
  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  roles?: string[];
}
