import { Component, DestroyRef, InjectionToken, computed, effect, inject, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';
import { Inbox } from '../../core/notifications/inbox';
import { Session } from '../../core/session/session';

/** How often the bell asks for the unread count while a customer is signed in. */
export const INBOX_POLL_MS = new InjectionToken<number>('INBOX_POLL_MS', { factory: () => 30_000 });

/**
 * The bell in the header: how many notifications are unread, a link to the
 * inbox. Asks on sign-in, on every navigation and every half minute; a fill
 * a few seconds after an order shows up without a reload.
 */
@Component({
  selector: 'app-notification-bell',
  imports: [RouterLink, RouterLinkActive],
  template: `
    <a class="bell" routerLink="/notifications" routerLinkActive="active" data-testid="nav-notifications" [attr.aria-label]="label()">
      <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" focusable="false">
        <path
          d="M12 22a2.5 2.5 0 0 0 2.45-2h-4.9A2.5 2.5 0 0 0 12 22Zm7-6V11a7 7 0 0 0-5.5-6.84V3.5a1.5 1.5 0 0 0-3 0v.66A7 7 0 0 0 5 11v5l-2 2v1h18v-1l-2-2Z"
          fill="currentColor"
        />
      </svg>
      @if (inbox.unread() > 0) {
        <span class="count" data-testid="bell-count">{{ shown() }}</span>
      }
    </a>
  `,
  styleUrl: './notification-bell.css',
})
export class NotificationBell {
  protected readonly inbox = inject(Inbox);
  private readonly accountId = inject(Session).accountId;

  protected readonly shown = computed(() => (this.inbox.unread() > 99 ? '99+' : String(this.inbox.unread())));
  protected readonly label = computed(() =>
    this.inbox.unread() === 0 ? 'Notifications, none unread' : `Notifications, ${this.inbox.unread()} unread`,
  );

  constructor() {
    effect(() => {
      this.accountId();
      untracked(() => void this.inbox.refresh());
    });

    inject(Router)
      .events.pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => void this.inbox.refresh());

    const poll = setInterval(() => void this.inbox.refresh(), inject(INBOX_POLL_MS));
    inject(DestroyRef).onDestroy(() => clearInterval(poll));
  }
}
