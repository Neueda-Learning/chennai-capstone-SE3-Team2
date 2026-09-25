import { Injectable } from '@nestjs/common';
import { Credential, CredentialRepository } from '../credentials/credential.repository';
import { PasswordHasher } from '../credentials/password-hasher';
import { AccessTokenService } from '../tokens/access-token.service';
import { RefreshTokenService } from '../tokens/refresh-token.service';
import { LoginFailure } from './login-failure';
import { ACCESS_TOKEN_SECONDS } from '../tokens/claims';
import { PlatformError } from '../common/platform-error';
import { LoginDto } from './dto/login.dto';
import { RefreshDto } from './dto/refresh.dto';
import { RegisterDto } from './dto/register.dto';
import { TokenResponseDto } from './dto/token-response.dto';
import { UserResponseDto } from './dto/user-response.dto';

@Injectable()
export class AuthService {
  constructor(
    private readonly credentials: CredentialRepository,
    private readonly hasher: PasswordHasher,
    private readonly accessTokens: AccessTokenService,
    private readonly refreshTokens: RefreshTokenService,
    private readonly loginFailure: LoginFailure,
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
      // Verifies against a dummy hash before failing, so this path costs what
      // the wrong-password path costs.
      return this.loginFailure.unknownUser(dto.password);
    }
    if (!(await this.hasher.verify(credential.passwordHash, dto.password))) {
      return this.loginFailure.wrongPassword();
    }
    return this.issue(credential);
  }

  async refresh(dto: RefreshDto): Promise<TokenResponseDto> {
    const credentialId = await this.refreshTokens.rotate(dto.refreshToken);
    const credential = await this.credentials.findById(credentialId);
    if (!credential) {
      throw PlatformError.unauthorised();
    }
    return this.issue(credential);
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
    const accessToken = await this.accessTokens.issue(credential);

    return {
      accessToken,
      refreshToken: await this.refreshTokens.issue(credential.id),
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
