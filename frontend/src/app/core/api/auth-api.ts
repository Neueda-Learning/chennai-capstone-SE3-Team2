import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../generated/auth';

/**
 * The Auth service, as this application uses it. Wraps the generated client
 * so components get a Promise rather than an Observable: the observables stop
 * at HttpClient.
 */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly auth = inject(AuthService);

  /** POST /auth/login. Resolves with the access token; rejects with the HttpErrorResponse. */
  async signIn(username: string, password: string): Promise<string> {
    const tokens = await firstValueFrom(this.auth.login({ username, password }));
    return tokens.accessToken;
  }
}
