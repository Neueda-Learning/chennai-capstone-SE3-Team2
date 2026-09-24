export class TokenResponseDto {
  accessToken: string;
  /** Opaque, stored server-side as a hash, rotated on every use. */
  refreshToken: string;
  tokenType: string;
  /** Access token lifetime in seconds. */
  expiresIn: number;
}
