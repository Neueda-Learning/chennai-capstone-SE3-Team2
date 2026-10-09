import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideClients } from '../../core/api/provide-clients';
import { ERROR_MESSAGES } from '../../core/errors/error-messages';
import { Apply } from './apply';

const TRADE = 'http://trade.test';
const URL = `${TRADE}/onboarding/applications`;

describe('Apply', () => {
  let fixture: ComponentFixture<Apply>;
  let page: HTMLElement;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: TRADE, authApiUrl: 'http://auth.test' }),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Apply);
    page = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const VALID = {
    'apply-name': 'Priya Menon',
    'apply-dob': '1990-05-17',
    'apply-email': 'priya@example.com',
    'apply-mobile': '98123 45611',
    'apply-pan': 'abcpm1234q',
    'apply-address': '12 Anna Nagar, Chennai',
    'apply-bank': '5098 7654 3210',
    'apply-ifsc': 'demo0000001',
  };

  function fill(values: Record<string, string>): void {
    for (const [id, value] of Object.entries(values)) {
      const input = page.querySelector<HTMLInputElement>(`[data-testid="${id}"]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
  }

  async function submit(): Promise<void> {
    page.querySelector<HTMLButtonElement>('[data-testid="apply-submit"]')!.click();
    await fixture.whenStable();
  }

  async function settle(): Promise<void> {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  it('sends a valid application, normalised as the server expects, with no token, and shows it was received', async () => {
    fill(VALID);
    await submit();

    const request = http.expectOne(URL);
    expect(request.request.headers.has('Authorization')).toBe(false);
    expect(request.request.body).toEqual({
      name: 'Priya Menon',
      dob: '1990-05-17',
      email: 'priya@example.com',
      phoneNumber: '+919812345611',
      pan: 'ABCPM1234Q',
      address: '12 Anna Nagar, Chennai',
      bankAccountNumber: '509876543210',
      ifsc: 'DEMO0000001',
    });
    request.flush({ status: 'RECEIVED', message: 'Application received.' }, { status: 202, statusText: 'Accepted' });
    await settle();

    expect(page.querySelector('[data-testid="apply-received"]')?.textContent).toContain('Application received');
    expect(page.querySelector('form')).toBeNull();
  });

  it('blocks an application with a bad PAN, a minor, a short bank account or a bad IFSC, and sends nothing', async () => {
    fill({ ...VALID, 'apply-pan': 'ABCPM12', 'apply-dob': '2020-01-01', 'apply-bank': '1234', 'apply-ifsc': 'DEMO1000001' });
    await submit();

    http.expectNone(URL);
    expect(page.textContent).toContain('Enter your 10-character PAN');
    expect(page.textContent).toContain('You must be 18 or over to open an account.');
    expect(page.textContent).toContain('9 to 18 digits');
    expect(page.textContent).toContain("11-character IFSC");
  });

  it('says what to do when the address has applied too often', async () => {
    fill(VALID);
    await submit();

    http.expectOne(URL).flush({ errorCode: 'RATE-429', message: 'x' }, { status: 429, statusText: 'Too Many Requests' });
    await settle();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain(ERROR_MESSAGES['RATE-429']);
    expect(page.querySelector('form')).not.toBeNull();
  });
});
