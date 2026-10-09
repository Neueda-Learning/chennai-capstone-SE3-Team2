import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Notification, NotificationsService } from '../../../generated/notifications';

/** The customer's notification history (Sprint 10), through the generated client. */
@Injectable({ providedIn: 'root' })
export class NotificationsApi {
  private readonly notifications = inject(NotificationsService);

  /** GET /api/v1/accounts/{id}/notifications: newest first. */
  history(accountId: number, limit = 50): Promise<Notification[]> {
    return firstValueFrom(this.notifications.getNotifications(accountId, limit));
  }

  /** GET /api/v1/accounts/{id}/notifications/unread, for the bell. */
  async unread(accountId: number): Promise<number> {
    return (await firstValueFrom(this.notifications.getUnreadCount(accountId))).unread;
  }

  /** POST /api/v1/accounts/{id}/notifications/{notificationId}/read. Reading one twice changes nothing. */
  async markRead(accountId: number, notificationId: string): Promise<void> {
    await firstValueFrom(this.notifications.markRead(accountId, notificationId));
  }
}
