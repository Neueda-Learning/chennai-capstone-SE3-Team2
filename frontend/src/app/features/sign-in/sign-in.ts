import { Component, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthApi } from '../../core/api/auth-api';
import { PreferencesApi } from '../../core/api/preferences-api';
import { KnownErrorCode } from '../../core/errors/error-messages';
import { DEFAULT_RETURN_URL, safeReturnUrl } from '../../core/guards/return-url';
import { Session } from '../../core/session/session';
import { ErrorMessage } from '../../shared/error-message/error-message';

/** On this screen AUTH-401 means the credentials were refused, not that a session ran out. */
const SIGN_IN_WORDING: Partial<Record<KnownErrorCode, string>> = {
  'AUTH-401': "That username and password don't match. Check them and try again.",
  'VAL-422': 'Enter your username and password.',
};

@Component({
  selector: 'app-sign-in',
  imports: [ReactiveFormsModule, RouterLink, ErrorMessage],
  templateUrl: './sign-in.html',
  styleUrl: './sign-in.css',
})
export class SignIn {
  private readonly authApi = inject(AuthApi);
  private readonly session = inject(Session);
  private readonly router = inject(Router);
  private readonly preferences = inject(PreferencesApi);

  /**
   * Where the guard was taking the user, from the `returnUrl` query parameter
   * (bound by the router's withComponentInputBinding). Checked before it is
   * followed.
   */
  readonly returnUrl = input<string | undefined>(undefined);

  protected readonly wording = SIGN_IN_WORDING;

  protected readonly form = new FormGroup({
    username: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected readonly submitting = signal(false);
  protected readonly error = signal<unknown>(null);

  async submit(): Promise<void> {
    if (this.form.invalid) {
      // The obvious mistake never reaches the wire.
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    try {
      const { username, password } = this.form.getRawValue();
      const tokens = await this.authApi.signIn(username, password);
      this.session.start(tokens.accessToken, tokens.refreshToken);
      // Where the guard was taking them, or else the landing screen they chose
      // in Settings: the stored preference applied at this sign-in.
      const returnTo = safeReturnUrl(this.returnUrl());
      const accountId = this.session.accountId();
      const target =
        returnTo === DEFAULT_RETURN_URL && accountId !== null ? await this.preferences.landingUrl(accountId) : returnTo;
      await this.router.navigateByUrl(target);
    } catch (failure) {
      this.form.controls.password.reset();
      this.error.set(failure);
    } finally {
      this.submitting.set(false);
    }
  }
}
