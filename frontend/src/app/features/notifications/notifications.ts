import { DatePipe } from '@angular/common';
import { Component, DestroyRef, InjectionToken, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Notification } from '../../../generated/notifications';
import { NotificationsApi } from '../../core/api/notifications-api';
import { Inbox } from '../../core/notifications/inbox';
import { Session } from '../../core/session/session';
import { ErrorMessage } from '../../shared/error-message/error-message';

/** How often the open inbox reads its history again. */
export const NOTIFICATIONS_POLL_MS = new InjectionToken<number>('NOTIFICATIONS_POLL_MS', { factory: () => 10_000 });

const KINDS: Readonly<Record<Notification.KindEnum, string>> = {
  ORDER_FILLED: 'Executed',
  ORDER_REJECTED: 'Rejected',
  ORDER_CANCELLED: 'Cancelled',
  PRICE_ALERT: 'Price alert',
};

/**
 * The inbox (Sprint 10): every notification the account has been sent,
 * newest first, whichever channel it went on, with where it went and whether
 * it has gone yet.
 */
@Component({
  selector: 'app-notifications',
  imports: [DatePipe, RouterLink, ErrorMessage],
  templateUrl: './notifications.html',
  styleUrl: './notifications.css',
})
export class Notifications {
  private readonly api = inject(NotificationsApi);
  private readonly inbox = inject(Inbox);
  private readonly accountId = inject(Session).accountId;

  protected readonly items = signal<Notification[] | null>(null);
  protected readonly error = signal<unknown>(null);

  constructor() {
    effect(() => {
      const accountId = this.accountId();
      if (accountId !== null) {
        untracked(() => void this.load(accountId));
      }
    });
    const poll = setInterval(() => {
      const accountId = this.accountId();
      if (accountId !== null) {
        void this.load(accountId);
      }
    }, inject(NOTIFICATIONS_POLL_MS));
    inject(DestroyRef).onDestroy(() => clearInterval(poll));
  }

  private async load(accountId: number): Promise<void> {
    try {
      this.items.set(await this.api.history(accountId));
      this.error.set(null);
    } catch (failure) {
      this.error.set(failure);
    }
  }

  protected kind(notification: Notification): string {
    return KINDS[notification.kind];
  }

  /** Where it went, in the words the customer chose it in Settings. */
  protected delivery(notification: Notification): string {
    switch (notification.status) {
      case 'QUEUED':
        return 'Waiting to be sent';
      case 'SENT':
        return notification.channel === 'EMAIL' ? `Emailed to ${notification.destination}` : 'In the app';
      case 'FAILED':
        return notification.channel === 'EMAIL'
          ? `Could not be emailed to ${notification.destination}; kept here`
          : 'Could not be delivered; kept here';
    }
  }

  async markRead(notification: Notification): Promise<void> {
    const accountId = this.accountId();
    if (accountId === null || notification.readAt) {
      return;
    }
    try {
      await this.api.markRead(accountId, notification.id);
      const readAt = new Date().toISOString();
      this.items.update((items) => items?.map((item) => (item.id === notification.id ? { ...item, readAt } : item)) ?? null);
      void this.inbox.refresh();
    } catch (failure) {
      this.error.set(failure);
    }
  }
}
