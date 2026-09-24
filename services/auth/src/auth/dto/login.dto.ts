import { IsString, MaxLength } from 'class-validator';

/** No accountId: after registration the account is asserted by the server, not the client. */
export class LoginDto {
  @IsString()
  @MaxLength(64)
  username: string;

  @IsString()
  @MaxLength(128)
  password: string;
}
