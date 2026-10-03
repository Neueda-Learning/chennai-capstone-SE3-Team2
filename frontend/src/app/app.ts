import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Session } from './core/session/session';

/** The shell: a header, and the routed feature below it. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly session = inject(Session);
  private readonly router = inject(Router);

  async signOut(): Promise<void> {
    this.session.end();
    await this.router.navigateByUrl('/sign-in');
  }
}
