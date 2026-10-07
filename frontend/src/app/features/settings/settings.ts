import { Component, effect, inject, signal, untracked } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { AlertChannel, LandingScreen, Preferences } from '../../../generated/preferences';
import { PreferencesApi } from '../../core/api/preferences-api';
import { CurrentAccount } from '../../core/session/current-account';
import { Session } from '../../core/session/session';
import { ErrorMessage } from '../../shared/error-message/error-message';

const SCREENS: ReadonlyArray<{ readonly value: LandingScreen; readonly label: string }> = [
  { value: 'dashboard', label: 'Dashboard' },
  { value: 'orders', label: 'Orders' },
  { value: 'holdings', label: 'Holdings' },
  { value: 'market-watch', label: 'Market watch' },
];

/**
 * The customer's preferences (Sprint 10): where sign-in opens, and how alerts
 * and order news reach them. The address is the profile's, shown masked, never
 * typed here. The default account is the one this sign-in trades.
 */
@Component({
  selector: 'app-settings',
  imports: [ReactiveFormsModule, ErrorMessage],
  templateUrl: './settings.html',
  styleUrl: './settings.css',
})
export class Settings {
  private readonly api = inject(PreferencesApi);
  protected readonly accountId = inject(Session).accountId;
  protected readonly account = inject(CurrentAccount).account;
  protected readonly screens = SCREENS;

  protected readonly form = new FormGroup({
    landingScreen: new FormControl<LandingScreen>('dashboard', { nonNullable: true }),
    alertChannel: new FormControl<AlertChannel>('EMAIL', { nonNullable: true }),
  });

  protected readonly preferences = signal<Preferences | null>(null);
  protected readonly saving = signal(false);
  protected readonly saved = signal<string | null>(null);
  protected readonly error = signal<unknown>(null);

  constructor() {
    effect(() => {
      const accountId = this.accountId();
      if (accountId !== null) {
        untracked(() => void this.load(accountId));
      }
    });
  }

  private async load(accountId: number): Promise<void> {
    try {
      this.show(await this.api.get(accountId));
    } catch (failure) {
      this.error.set(failure);
    }
  }

  private show(preferences: Preferences): void {
    this.preferences.set(preferences);
    this.form.setValue({ landingScreen: preferences.landingScreen, alertChannel: preferences.alertChannel });
  }

  protected labelOf(screen: LandingScreen): string {
    return SCREENS.find((s) => s.value === screen)?.label ?? screen;
  }

  async save(): Promise<void> {
    const accountId = this.accountId();
    if (accountId === null) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.saved.set(null);
    try {
      const stored = await this.api.save(accountId, { defaultAccountId: accountId, ...this.form.getRawValue() });
      this.show(stored);
      this.saved.set(`Saved. Sign-in now opens on ${this.labelOf(stored.landingScreen)}.`);
    } catch (failure) {
      this.error.set(failure);
    } finally {
      this.saving.set(false);
    }
  }
}
