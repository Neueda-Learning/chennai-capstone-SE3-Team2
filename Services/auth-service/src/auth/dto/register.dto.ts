import { ApiProperty } from '@nestjs/swagger';
import { IsString, Matches, MaxLength, MinLength } from 'class-validator';

/**
 * No account number and no roles. The activation token resolves the account on
 * the server, so a caller can no longer name an account they were not sent a
 * link for, and everyone who registers is a CUSTOMER.
 */
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

  @ApiProperty({
    description: 'The one-time token from the activation email. Single use; expires 24 hours after it was sent.',
    example: '9c1f7a2e4b6d8e0a2c4e6a8c0e2a4c6e8a0c2e4a6c8e0a2c4e6a8c0e2a4c6e8a',
    writeOnly: true,
  })
  @IsString()
  @Matches(/^[0-9a-f]{64}$/, { message: 'activationToken must be 64 lowercase hex characters' })
  activationToken: string;
}
