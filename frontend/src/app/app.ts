import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { CurrentAccount } from './core/session/current-account';
import { Session } from './core/session/session';
import { MarketWatch } from './features/market-watch/market-watch';
import { NotificationBell } from './shared/notification-bell/notification-bell';

/** The shell: a header; beside a signed-in screen, the market watch; and the routed feature. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MarketWatch, NotificationBell],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly session = inject(Session);
  /** Who is signed in, as they know themselves: name and account reference. */
  protected readonly current = inject(CurrentAccount);
  private readonly router = inject(Router);

  async signOut(): Promise<void> {
    this.session.end();
    await this.router.navigateByUrl('/sign-in');
  }
}
