import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { Session } from './core/session/session';

/** The shell: a header, and the routed feature below it. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
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
