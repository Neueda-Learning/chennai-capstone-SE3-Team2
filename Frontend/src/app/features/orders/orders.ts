import { Component, inject, signal } from '@angular/core';
import { CurrentAccount } from '../../core/session/current-account';
import { Session } from '../../core/session/session';
import { Blotter } from '../blotter/blotter';
import { HoldingsFilter } from '../holdings/holdings';

/**
 * Kite's Orders: what is still working, with Cancel, above what has been
 * filled, rejected or cancelled. Stocks and funds together, or one of them.
 */
@Component({
  selector: 'app-orders',
  imports: [Blotter],
  template: `
    <div class="heading">
      <h1>Orders</h1>
      <div class="filter-chips" role="group" aria-label="Show">
        @for (option of filters; track option.value) {
          <button
            type="button"
            class="filter-chip"
            [class.active]="segment() === option.value"
            [attr.aria-pressed]="segment() === option.value"
            [attr.data-testid]="'orders-filter-' + option.value"
            (click)="segment.set(option.value)"
          >
            {{ option.label }}
          </button>
        }
      </div>
    </div>
    @if (accountId(); as accountId) {
      <app-blotter [accountId]="accountId" [segment]="segment()" layout="split" (settled)="settled()" />
    }
  `,
  styles: `
    .heading {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
    }
    .heading h1 {
      margin: 0;
    }
  `,
})
export class Orders {
  protected readonly accountId = inject(Session).accountId;
  private readonly current = inject(CurrentAccount);

  protected readonly segment = signal<HoldingsFilter>('all');
  protected readonly filters: ReadonlyArray<{ readonly value: HoldingsFilter; readonly label: string }> = [
    { value: 'all', label: 'All' },
    { value: 'stocks', label: 'Stocks' },
    { value: 'mutual-funds', label: 'Mutual funds' },
  ];

  /** An order left NEW: the cash it held or spent has moved. */
  protected settled(): void {
    void this.current.refresh();
  }
}
