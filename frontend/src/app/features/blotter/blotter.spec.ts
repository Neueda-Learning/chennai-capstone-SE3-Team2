import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { OrderHistoryEntry } from '../../../generated/trade';
import { provideClients } from '../../core/api/provide-clients';
import { Blotter, REREAD_POLICY } from './blotter';

const TRADE = 'http://trade.test';
const HISTORY = `${TRADE}/api/v1/accounts/3/orders`;

function order(id: string, status: OrderHistoryEntry['status'], createdOn: string): OrderHistoryEntry {
  return { orderId: `ORD-${id}`, accountId: 3, symbol: 'INFY.NS', side: 'BUY', quantity: 10, price: 1450.5, executedPrice: status === 'FILLED' ? 1450.25 : null, status, createdOn };
}

describe('Blotter', () => {
  let fixture: ComponentFixture<Blotter>;
  let page: HTMLElement;
  let http: HttpTestingController;
  /** Re-reads the blotter has asked for and not yet had run. */
  let scheduled: Array<() => void>;

  beforeEach(async () => {
    scheduled = [];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: TRADE, authApiUrl: 'http://auth.test' }),
        {
          provide: REREAD_POLICY,
          useValue: {
            intervalMs: 3000,
            maxRereads: 3,
            schedule: (task: () => void) => {
              scheduled.push(task);
              return () => (scheduled = scheduled.filter((t) => t !== task));
            },
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Blotter);
    fixture.componentRef.setInput('accountId', 3);
    page = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => {
    http.verify();
    fixture.destroy();
  });

  async function respond(orders: OrderHistoryEntry[]): Promise<void> {
    const read = http.expectOne(HISTORY);
    expect(read.request.method).toBe('GET');
    read.flush(orders);
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  /** Lets the scheduled re-read fire, as the interval elapsing would. */
  function elapse(): void {
    const task = scheduled.shift();
    expect(task, 'a re-read was scheduled').toBeDefined();
    task!();
  }

  const rows = () => [...page.querySelectorAll('[data-testid="blotter-row"]')];

  it('renders order history newest first, rejections included', async () => {
    await respond([
      order('a', 'FILLED', '2026-10-02T09:00:00Z'),
      order('c', 'REJECTED', '2026-10-02T11:00:00Z'),
      order('b', 'CANCELLED', '2026-10-02T10:00:00Z'),
    ]);

    expect(rows().map((row) => row.getAttribute('data-status'))).toEqual(['REJECTED', 'CANCELLED', 'FILLED']);
    expect(rows()[0].textContent).toContain('ORD-c');
    expect(rows()[0].querySelector('[data-testid="status-badge"]')?.textContent).toContain('REJECTED');
  });

  it('shows an order at NEW as still working, and schedules a re-read', async () => {
    await respond([order('a', 'NEW', '2026-10-02T09:00:00Z')]);

    expect(rows()[0].textContent).toContain('still working');
    expect(page.querySelector('[data-testid="blotter-working"]')?.textContent).toContain('1 order is still working');
    expect(scheduled.length).toBe(1);
  });

  it('re-reads order history while anything is at NEW, and stops when nothing is', async () => {
    await respond([order('a', 'NEW', '2026-10-02T09:00:00Z')]);

    elapse();
    await respond([order('a', 'FILLED', '2026-10-02T09:00:00Z')]);

    expect(rows()[0].getAttribute('data-status')).toBe('FILLED');
    expect(scheduled.length).toBe(0);
    expect(page.querySelector('[data-testid="blotter-working"]')?.textContent?.trim()).toBe('');
  });

  it('stops after its bounded number of re-reads and says the order is still working', async () => {
    await respond([order('a', 'NEW', '2026-10-02T09:00:00Z')]);
    for (let i = 0; i < 3; i++) {
      elapse();
      await respond([order('a', 'NEW', '2026-10-02T09:00:00Z')]);
    }

    expect(scheduled.length).toBe(0);
    expect(page.querySelector('[data-testid="blotter-working"]')?.textContent).toContain('Still working after 9 seconds');
  });

  it('handles an empty history gracefully', async () => {
    await respond([]);

    expect(page.querySelector('[data-testid="blotter-empty"]')?.textContent).toContain('No orders yet.');
    expect(page.querySelector('[data-testid="blotter-table"]')).toBeNull();
    expect(scheduled.length).toBe(0);
  });

  it('re-reads when Refresh is pressed, and only ever reads -- never re-posts an order', async () => {
    await respond([order('a', 'FILLED', '2026-10-02T09:00:00Z')]);

    page.querySelector<HTMLButtonElement>('[data-testid="blotter-refresh"]')!.click();
    await fixture.whenStable();

    await respond([order('a', 'FILLED', '2026-10-02T09:00:00Z'), order('b', 'NEW', '2026-10-02T09:05:00Z')]);
    expect(rows().length).toBe(2);
    http.expectNone({ method: 'POST' });
  });

  it('keeps the last good table and shows the error when a re-read fails', async () => {
    await respond([order('a', 'NEW', '2026-10-02T09:00:00Z')]);
    elapse();
    http.expectOne(HISTORY).flush({ errorCode: 'SRV-500', message: 'boom' }, { status: 500, statusText: 'Server Error' });
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();

    expect(rows().length).toBe(1);
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('Something went wrong on our side');
  });
});
