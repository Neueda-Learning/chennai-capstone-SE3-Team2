import { TestBed } from '@angular/core/testing';
import { OrderStatus } from '../../../generated/trade';
import { StatusBadge } from './status-badge';

describe('StatusBadge', () => {
  it('carries the word as well as the colour, for every status', async () => {
    for (const status of Object.values(OrderStatus)) {
      const fixture = TestBed.createComponent(StatusBadge);
      fixture.componentRef.setInput('status', status);
      await fixture.whenStable();
      const badge = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="status-badge"]')!;

      expect(badge.textContent, status).toContain(status);
      expect(badge.classList.contains(status.toLowerCase()), status).toBe(true);
    }
  });
});
