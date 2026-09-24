import { Injectable } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { randomBytes } from 'node:crypto';
import { Credential, CredentialRepository } from '../credentials/credential.repository';
import { PasswordHasher } from '../credentials/password-hasher';
import { PlatformError } from '../common/platform-error';
import { Env } from '../config/env';
import { LoginDto } from './dto/login.dto';
import { RefreshDto } from './dto/refresh.dto';
import { RegisterDto } from './dto/register.dto';
import { TokenResponseDto } from './dto/token-response.dto';
import { UserResponseDto } from './dto/user-response.dto';

export const ACCESS_TOKEN_SECONDS = 900;

@Injectable()
export class AuthService {
  constructor(
    private readonly credentials: CredentialRepository,
    private readonly hasher: PasswordHasher,
    private readonly jwt: JwtService,
    private readonly env: Env,
  ) {}

  /**
   * Binds a login to an account onboarding already opened. It creates no
   * trading account, and issues no tokens: an unauthenticated route that
   * mints a session is an authentication bypass once it has its first defect.
   */
  async register(dto: RegisterDto): Promise<UserResponseDto> {
    if (await this.credentials.findByUsername(dto.username)) {
      throw PlatformError.usernameTaken();
    }

    const passwordHash = await this.hasher.hash(dto.password);
    const roles = dto.roles?.length ? dto.roles : ['CUSTOMER'];

    // Null when the account was never provisioned, or somebody claimed it first.
    const credential = await this.credentials.claim(dto.username, passwordHash, dto.accountId, roles);
    if (!credential) {
      throw PlatformError.unauthorised();
    }

    return this.toUser(credential);
  }

  async login(dto: LoginDto): Promise<TokenResponseDto> {
    const credential = await this.credentials.findByUsername(dto.username);
    if (!credential) {
      throw PlatformError.unauthorised();
    }
    if (!(await this.hasher.verify(credential.passwordHash, dto.password))) {
      throw PlatformError.unauthorised();
    }
    return this.issue(credential);
  }

  async refresh(dto: RefreshDto): Promise<TokenResponseDto> {
    throw PlatformError.unauthorised();
  }

  async me(userId: string): Promise<UserResponseDto> {
    const credential = await this.credentials.findById(userId);
    if (!credential) {
      throw PlatformError.unauthorised();
    }
    return this.toUser(credential);
  }

  /** The account comes from the stored credential, never from the request. */
  private async issue(credential: Credential): Promise<TokenResponseDto> {
    const accessToken = await this.jwt.signAsync(
      {
        sub: credential.id,
        accountId: credential.accountId,
        roles: credential.roles,
      },
      {
        secret: this.env.jwtSecret,
        issuer: this.env.jwtIssuer,
        expiresIn: ACCESS_TOKEN_SECONDS,
      },
    );

    return {
      accessToken,
      refreshToken: randomBytes(32).toString('hex'),
      tokenType: 'Bearer',
      expiresIn: ACCESS_TOKEN_SECONDS,
    };
  }

  private toUser(credential: Credential): UserResponseDto {
    return {
      id: credential.id,
      username: credential.username,
      accountId: credential.accountId,
      roles: credential.roles,
    };
  }
}
