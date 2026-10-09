import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TransferResponse } from '../../../generated/extensions';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../../core/api/provide-clients';
import { Session } from '../../core/session/session';
import { REREAD_POLICY } from '../../shared/reread/reread-policy';
import { Cash } from './cash';

const TRADE = 'http://trade.test';
const ACCOUNT = `${TRADE}/api/v1/accounts/3`;

function transfer(id: number, status: TransferResponse['status'], over: Partial<TransferResponse> = {}): TransferResponse {
  return { transferId: id, direction: 'DEPOSIT', amount: 5000, status, reason: null, createdAt: '2026-10-03T09:00:00Z', decidedAt: null, ...over };
}

describe('Cash', () => {
  let fixture: ComponentFixture<Cash>;
  let page: HTMLElement;
  let http: HttpTestingController;
  /** Re-reads the page has asked for and not yet had run. */
  let scheduled: Array<() => void>;

  beforeEach(async () => {
    sessionStorage.clear();
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
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Cash);
    page = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => {
    http.verify();
    fixture.destroy();
  });

  async function settle(): Promise<void> {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  /** Answers one read of the transfers and the available cash. */
  async function respond(transfers: TransferResponse[], available = 10000): Promise<void> {
    http.expectOne(`${ACCOUNT}/transfers`).flush(transfers);
    http.expectOne(`${ACCOUNT}/balance`).flush({ accountId: 3, cashBalance: available, currency: 'INR', asOf: '2026-10-03T09:00:00Z' });
    await settle();
  }

  async function open(transfers: TransferResponse[] = [], available = 10000): Promise<void> {
    http.expectOne(`${ACCOUNT}/bank-account`).flush({ accountNumberLast4: '0031', ifsc: 'DEMO0000001', holderName: 'Rohan Nair' });
    await settle();
    await respond(transfers, available);
  }

  function elapse(): void {
    const task = scheduled.shift();
    expect(task, 'a re-read was scheduled').toBeDefined();
    task!();
  }

  const byTestId = (id: string) => page.querySelector<HTMLElement>(`[data-testid="${id}"]`);
  const rows = () => [...page.querySelectorAll<HTMLElement>('[data-testid="cash-transfer-row"]')];

  async function type(amount: string): Promise<void> {
    const input = byTestId('cash-amount') as HTMLInputElement;
    input.value = amount;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function press(id: 'cash-deposit' | 'cash-withdraw'): Promise<void> {
    byTestId(id)!.click();
    await settle();
  }

  it('shows the available cash and the bank account masked to its last four digits', async () => {
    await open([], 7500.5);

    expect(byTestId('cash-available')?.textContent).toContain('7,500.50');
    expect(byTestId('cash-bank')?.textContent).toContain('••••0031');
    expect(page.textContent).toContain('DEMO0000001 · Rohan Nair');
    expect(page.textContent).toContain('No transfers yet.');
  });

  it('adds cash: sends the amount with a key, shows it PROCESSING, then re-reads until the bank says yes', async () => {
    await open();
    await type('5000.50');
    await press('cash-deposit');

    const sent = http.expectOne(`${ACCOUNT}/deposits`);
    expect(sent.request.method).toBe('POST');
    expect(sent.request.body.amount).toBe(5000.5);
    expect(sent.request.body.idempotencyKey).toMatch(/^[0-9a-f-]{36}$/);
    sent.flush(transfer(41, 'PENDING', { amount: 5000.5 }), { status: 202, statusText: 'Accepted' });
    await settle();
    await respond([transfer(41, 'PENDING', { amount: 5000.5 })]);

    expect(rows()[0].dataset['status']).toBe('PENDING');
    expect(rows()[0].textContent).toContain('PROCESSING');
    expect(rows()[0].textContent).toContain('+5,000.50');
    expect(byTestId('cash-pending')?.textContent).toContain('1 transfer is processing');
    expect((byTestId('cash-amount') as HTMLInputElement).value).toBe('');

    elapse();
    await respond([transfer(41, 'SUCCESS', { amount: 5000.5 })], 15000.5);

    expect(rows()[0].dataset['status']).toBe('SUCCESS');
    expect(byTestId('cash-available')?.textContent).toContain('15,000.50');
    expect(byTestId('cash-pending')?.textContent?.trim()).toBe('');
    expect(scheduled).toHaveLength(0);
  });

  it('shows the 10 latest transfers, and the rest on request', async () => {
    await open(Array.from({ length: 12 }, (_, i) => transfer(i + 1, 'SUCCESS')));

    expect(rows().length).toBe(10);
    expect(byTestId('cash-transfers-more')?.textContent).toContain('Showing 10 of 12');
    (byTestId('cash-transfers-show-more') as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(rows().length).toBe(12);
  });

  it('withdraws to the bank account, shown as money out', async () => {
    await open();
    await type('1000');
    await press('cash-withdraw');

    const sent = http.expectOne(`${ACCOUNT}/withdrawals`);
    expect(sent.request.body.amount).toBe(1000);
    sent.flush(transfer(42, 'PENDING', { direction: 'WITHDRAWAL', amount: 1000 }), { status: 202, statusText: 'Accepted' });
    await settle();
    await respond([transfer(42, 'PENDING', { direction: 'WITHDRAWAL', amount: 1000 })], 9000);

    expect(rows()[0].textContent).toContain('Withdrawn');
    expect(rows()[0].textContent).toContain('−1,000.00');
  });

  it('does not send a withdrawal over the available cash', async () => {
    await open([], 500);
    await type('500.01');
    await press('cash-withdraw');

    expect(byTestId('cash-over-available')?.textContent).toContain('more than your available cash');
    http.expectNone(`${ACCOUNT}/withdrawals`);
  });

  for (const amount of ['', '0', '-5', '10.005', 'ten']) {
    it(`does not send an amount of "${amount}"`, async () => {
      await open();
      await type(amount);
      await press('cash-deposit');

      expect(page.querySelector('.field-error')).not.toBeNull();
      http.expectNone(`${ACCOUNT}/deposits`);
    });
  }

  it("shows why the bank declined a transfer", async () => {
    await open([transfer(43, 'FAILED', { amount: 250000, reason: 'over the ₹2,00,000 per-transfer limit' })]);

    expect(rows()[0].textContent).toContain('FAILED');
    expect(rows()[0].textContent).toContain('over the ₹2,00,000 per-transfer limit');
    expect(scheduled).toHaveLength(0);
  });

  it('sends the same key again when the first attempt never got an answer, so the bank moves the money once', async () => {
    await open();
    await type('200');
    await press('cash-deposit');
    const first = http.expectOne(`${ACCOUNT}/deposits`);
    const key = first.request.body.idempotencyKey;
    first.error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
    await settle();

    await press('cash-deposit');
    const retry = http.expectOne(`${ACCOUNT}/deposits`);
    expect(retry.request.body.idempotencyKey).toBe(key);
    retry.flush(transfer(44, 'PENDING', { amount: 200 }), { status: 202, statusText: 'Accepted' });
    await settle();
    await respond([transfer(44, 'PENDING', { amount: 200 })]);

    await type('300');
    await press('cash-deposit');
    expect(http.expectOne(`${ACCOUNT}/deposits`).request.body.idempotencyKey).not.toBe(key);
  });

  it("says when the server refuses a withdrawal for lack of cash", async () => {
    await open([], 1000);
    await type('900');
    await press('cash-withdraw');
    http.expectOne(`${ACCOUNT}/withdrawals`).flush({ errorCode: 'PAY-400', message: 'x' }, { status: 400, statusText: 'Bad Request' });
    await settle();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain("There isn't enough available cash");
  });

  it('stops re-reading after the policy limit and says so; Refresh starts again', async () => {
    await open([transfer(45, 'PENDING')]);
    for (let i = 0; i < 3; i++) {
      elapse();
      await respond([transfer(45, 'PENDING')]);
    }

    expect(scheduled).toHaveLength(0);
    expect(byTestId('cash-pending')?.textContent).toContain('Still processing');

    byTestId('cash-refresh')!.click();
    await settle();
    await respond([transfer(45, 'SUCCESS')]);
    expect(byTestId('cash-pending')?.textContent?.trim()).toBe('');
  });
});
