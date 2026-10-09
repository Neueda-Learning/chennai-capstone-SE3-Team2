import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthService, ProfileService, TokenResponse, UserResponse } from '../../../generated/auth';

/**
 * The Auth service, as this application uses it. Wraps the generated client
 * so components get a Promise rather than an Observable: the observables stop
 * at HttpClient.
 */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly auth = inject(AuthService);
  private readonly profile = inject(ProfileService);

  /** POST /auth/login. Resolves with the token pair; rejects with the HttpErrorResponse. */
  signIn(username: string, password: string): Promise<TokenResponse> {
    return firstValueFrom(this.auth.login({ username, password }));
  }

  /**
   * POST /auth/refresh: a new pair for the refresh token, which stops working
   * at once. Presenting it a second time revokes every one the user holds.
   */
  refresh(refreshToken: string): Promise<TokenResponse> {
    return firstValueFrom(this.auth.refresh({ refreshToken }));
  }

  /** GET /auth/me, the protected Auth route: who this token belongs to. */
  currentUser(): Promise<UserResponse> {
    return firstValueFrom(this.profile.getCurrentUser());
  }
}
