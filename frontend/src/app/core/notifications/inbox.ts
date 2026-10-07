import { Injectable, inject, signal } from '@angular/core';
import { NotificationsApi } from '../api/notifications-api';
import { Session } from '../session/session';

/**
 * How many notifications are unread, for the bell in the header. The bell
 * asks it to refresh; the inbox page asks again once it has marked one read.
 */
@Injectable({ providedIn: 'root' })
export class Inbox {
  private readonly api = inject(NotificationsApi);
  private readonly accountId = inject(Session).accountId;
  private readonly count = signal(0);

  readonly unread = this.count.asReadonly();

  async refresh(): Promise<void> {
    const accountId = this.accountId();
    if (accountId === null) {
      this.count.set(0);
      return;
    }
    try {
      this.count.set(await this.api.unread(accountId));
    } catch {
      // The bell never shows an error: it keeps the last count it had, and
      // the inbox page says what went wrong if the customer opens it.
    }
  }
}
