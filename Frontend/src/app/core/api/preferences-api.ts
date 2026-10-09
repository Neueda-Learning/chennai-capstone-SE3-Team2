import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Preferences, PreferencesService, PreferencesUpdate } from '../../../generated/preferences';
import { DEFAULT_RETURN_URL } from '../guards/return-url';

/** The customer's preferences (Sprint 10), through the generated client. */
@Injectable({ providedIn: 'root' })
export class PreferencesApi {
  private readonly preferences = inject(PreferencesService);

  /** GET /api/v1/accounts/{id}/preferences: stored, or the defaults with stored false. */
  get(accountId: number): Promise<Preferences> {
    return firstValueFrom(this.preferences.getPreferences(accountId));
  }

  /** PUT /api/v1/accounts/{id}/preferences. */
  save(accountId: number, update: PreferencesUpdate): Promise<Preferences> {
    return firstValueFrom(this.preferences.savePreferences(accountId, update));
  }

  /**
   * Where sign-in opens: the customer's stored landing screen. If the
   * preferences cannot be read, home, as before: a preference must never stop
   * a customer signing in.
   */
  async landingUrl(accountId: number): Promise<string> {
    try {
      return `/${(await this.get(accountId)).landingScreen}`;
    } catch {
      return DEFAULT_RETURN_URL;
    }
  }
}
