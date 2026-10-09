import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApplicationRequest, OnboardingService } from '../../../generated/extensions';

/**
 * Opening an account, through the generated extensions client. A Promise, not
 * an Observable: the observables stop at HttpClient.
 */
@Injectable({ providedIn: 'root' })
export class OnboardingApi {
  private readonly onboarding = inject(OnboardingService);

  /** POST /onboarding/applications. Public: no token. Resolves when received. */
  async apply(application: ApplicationRequest): Promise<void> {
    await firstValueFrom(this.onboarding.applyForAccount(application));
  }
}
