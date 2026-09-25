import { ApiProperty } from '@nestjs/swagger';
import { IsString, MaxLength } from 'class-validator';

/** No accountId: after registration the account is asserted by the server, not the client. */
export class LoginDto {
  @ApiProperty({ example: 'priya.menon' })
  @IsString()
  @MaxLength(64)
  username: string;

  @ApiProperty({ example: 'correct horse battery staple', writeOnly: true })
  @IsString()
  @MaxLength(128)
  password: string;
}
