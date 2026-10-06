import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { OrderHistoryEntry } from '../../../generated/trade';
import { provideClients } from '../../core/api/provide-clients';
import { REREAD_POLICY } from '../../shared/reread/reread-policy';
import { Blotter } from './blotter';

const TRADE = 'http://trade.test';
const HISTORY = `${TRADE}/api/v1/accounts/3/orders`;
/** What the lookup of the orders' instruments answers. */
const KNOWN = [
  { symbol: 'INFY.NS', name: 'Infosys Ltd', type: 'STOCK', exchange: 'NSE', tradable: true },
  { symbol: '120716', name: 'UTI Nifty 50 Index Fund', type: 'MF', exchange: null, tradable: true },
  { symbol: 'SCH100001', name: 'Bluechip Growth Fund', type: 'MF', exchange: null, tradable: true },
];

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
    // The orders' instruments, looked up the first time they are seen.
    for (const lookup of http.match((request) => request.url === `${TRADE}/api/v1/instruments`)) {
      const symbols = lookup.request.params.get('symbols')!.split(',');
      lookup.flush(KNOWN.filter((instrument) => symbols.includes(instrument.symbol)));
    }
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
    expect(rows()[0].textContent).toContain('INFY.NS');
    expect(rows()[0].querySelector('[data-testid="status-badge"]')?.textContent).toContain('REJECTED');
  });

  it('never shows the order id: it is the platform\'s, not the customer\'s', async () => {
    await respond([order('a', 'FILLED', '2026-10-02T09:00:00Z')]);

    expect(page.querySelector('[data-testid="blotter-table"]')?.textContent).not.toContain('ORD-');
    const headers = [...page.querySelectorAll('[data-testid="blotter-table"] th')].map((th) => th.textContent?.trim());
    expect(headers).not.toContain('Order');
    expect(headers).toContain('Fill price');
  });

  describe('on the mutual funds dashboard', () => {
    const fund = (id: string, symbol: string): OrderHistoryEntry => ({ ...order(id, 'FILLED', '2026-10-02T09:00:00Z'), symbol });

    it('lists funds by their type -- a scheme code that is not digits included -- and names each one', async () => {
      fixture.componentRef.setInput('segment', 'mutual-funds');

      await respond([order('a', 'FILLED', '2026-10-02T09:00:00Z'), fund('b', '120716'), fund('c', 'SCH100001')]);

      const names = rows().map((row) => row.querySelector('td:nth-child(2)')?.textContent?.trim());
      expect(names).toEqual(['UTI Nifty 50 Index Fund', 'Bluechip Growth Fund']);
    });
  });

  it('shows an order at NEW as still working, and schedules a re-read', async () => {
    await respond([order('a', 'NEW', '2026-10-02T09:00:00Z')]);

    expect(rows()[0].querySelector('[data-testid="status-badge"]')?.textContent).toContain('NEW');
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

  describe('cancelling', () => {
    const cancelButtons = () => [...page.querySelectorAll<HTMLButtonElement>('[data-testid="blotter-cancel"]')];
    let settled: number;

    /** Lets the cancel's answer reach the blotter, which then re-reads. */
    async function answered(): Promise<void> {
      for (let i = 0; i < 5; i++) {
        await Promise.resolve();
      }
    }

    beforeEach(() => {
      settled = 0;
      fixture.componentInstance.settled.subscribe(() => settled++);
    });

    it('offers Cancel only on an order still NEW', async () => {
      await respond([order('a', 'NEW', '2026-10-02T09:00:00Z'), order('b', 'FILLED', '2026-10-02T08:00:00Z')]);

      expect(cancelButtons()).toHaveLength(1);
      expect(rows()[0].contains(cancelButtons()[0])).toBe(true);
      // Says which order without the order id.
      expect(cancelButtons()[0].getAttribute('aria-label')).toBe('Cancel the buy of 10 INFY.NS');
    });

    it('cancels the order, re-reads, and says the order settled', async () => {
      await respond([order('a', 'NEW', '2026-10-02T09:00:00Z')]);

      cancelButtons()[0].click();
      await fixture.whenStable();
      // The bare UUID: the route refuses the ORD- display prefix.
      const cancel = http.expectOne(`${TRADE}/api/v1/orders/a`);
      expect(cancel.request.method).toBe('DELETE');
      cancel.flush({ orderId: 'ORD-a', status: 'CANCELLED', message: 'Order cancelled' });
      await answered();
      await respond([order('a', 'CANCELLED', '2026-10-02T09:00:00Z')]);

      expect(rows()[0].getAttribute('data-status')).toBe('CANCELLED');
      expect(cancelButtons()).toHaveLength(0);
      expect(settled).toBe(1);
      expect(scheduled.length).toBe(0);
    });

    it('explains an order that finished before the cancel arrived, and shows what happened to it', async () => {
      await respond([order('a', 'NEW', '2026-10-02T09:00:00Z')]);

      cancelButtons()[0].click();
      await fixture.whenStable();
      http.expectOne(`${TRADE}/api/v1/orders/a`).flush({ errorCode: 'ORD-409', message: 'Order is not cancellable' }, { status: 409, statusText: 'Conflict' });
      await answered();
      await respond([order('a', 'FILLED', '2026-10-02T09:00:00Z')]);

      expect(page.querySelector('[role="alert"]')?.textContent).toContain('That order had already finished');
      expect(rows()[0].getAttribute('data-status')).toBe('FILLED');
      expect(settled).toBe(1);
    });

    it('does not report a settle on the first read, or when nothing left NEW', async () => {
      await respond([order('a', 'FILLED', '2026-10-02T09:00:00Z'), order('b', 'NEW', '2026-10-02T09:01:00Z')]);
      elapse();
      await respond([order('a', 'FILLED', '2026-10-02T09:00:00Z'), order('b', 'NEW', '2026-10-02T09:01:00Z')]);

      expect(settled).toBe(0);

      elapse();
      await respond([order('a', 'FILLED', '2026-10-02T09:00:00Z'), order('b', 'REJECTED', '2026-10-02T09:01:00Z')]);
      expect(settled).toBe(1);
    });
  });
});
