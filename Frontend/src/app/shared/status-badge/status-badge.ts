import { Component, computed, input } from '@angular/core';
import { OrderStatus } from '../../../generated/trade';

const BADGES: Readonly<Record<OrderStatus, { readonly label: string; readonly icon: string; readonly hint: string }>> = {
  NEW: { label: 'NEW', icon: '◷', hint: 'Still working: accepted, not yet executed' },
  FILLED: { label: 'FILLED', icon: '✓', hint: 'Executed' },
  REJECTED: { label: 'REJECTED', icon: '✕', hint: 'Refused when it was executed' },
  CANCELLED: { label: 'CANCELLED', icon: '–', hint: 'Cancelled before it was executed' },
};

/**
 * One badge per order status. Each carries the WORD and a shape as well as a
 * colour: roughly one man in twelve cannot tell red from green.
 */
@Component({
  selector: 'app-status-badge',
  template: `
    <span [class]="'badge ' + status().toLowerCase()" [title]="badge().hint" data-testid="status-badge">
      <span aria-hidden="true">{{ badge().icon }}</span>
      {{ badge().label }}
    </span>
  `,
  styles: `
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      padding: 0.1rem 0.55rem;
      border-radius: 999px;
      border: 1px solid currentColor;
      font-size: 0.8rem;
      font-weight: 700;
      letter-spacing: 0.02em;
      white-space: nowrap;
    }
    .new {
      color: var(--warning);
      background: var(--warning-bg);
    }
    .filled {
      color: var(--success);
      background: var(--success-bg);
    }
    .rejected {
      color: var(--danger);
      background: var(--danger-bg);
    }
    .cancelled {
      color: var(--muted);
      background: var(--bg);
    }
  `,
})
export class StatusBadge {
  readonly status = input.required<OrderStatus>();
  protected readonly badge = computed(() => BADGES[this.status()]);
}
