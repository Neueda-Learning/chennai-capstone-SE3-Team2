import { IsArray, IsInt, IsOptional, IsPositive, IsString, Matches, MaxLength, MinLength } from 'class-validator';

export class RegisterDto {
  @IsString()
  @MinLength(3)
  @MaxLength(64)
  @Matches(/^[a-zA-Z0-9._-]+$/, { message: 'username may contain letters, digits, dot, underscore and hyphen' })
  username: string;

  @IsString()
  @MinLength(12)
  @MaxLength(128)
  password: string;

  /** The account this user will trade. It must already be provisioned. */
  @IsInt()
  @IsPositive()
  accountId: number;

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  roles?: string[];
}
