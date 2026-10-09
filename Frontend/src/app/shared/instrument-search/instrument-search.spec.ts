import { provideHttpClient } from '@angular/common/http';
import { HttpRequest } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InstrumentResponse } from '../../../generated/extensions';
import { InstrumentCatalog } from '../../core/api/instrument-catalog';
import { provideClients } from '../../core/api/provide-clients';
import { ERROR_MESSAGES } from '../../core/errors/error-messages';
import { InstrumentSearch, SEARCH_DELAY_MS } from './instrument-search';

const TRADE = 'http://trade.test';
const TATA: InstrumentResponse[] = [
  { symbol: 'TATASTEEL.NS', name: 'Tata Steel Limited', type: 'STOCK', exchange: 'NSE', tradable: true },
  { symbol: 'TCS.NS', name: 'Tata Consultancy Services Limited', type: 'STOCK', exchange: 'NSE', tradable: true },
  { symbol: '147794', name: 'Tata Small Cap Fund - Direct Plan - Growth', type: 'MF', exchange: null, tradable: true },
];

const search = (q: string) => (request: HttpRequest<unknown>) =>
  request.url === `${TRADE}/api/v1/instruments` && request.params.get('q') === q;

describe('InstrumentSearch', () => {
  let fixture: ComponentFixture<InstrumentSearch>;
  let page: HTMLElement;
  let http: HttpTestingController;
  let picked: InstrumentResponse[];

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: TRADE, authApiUrl: 'http://auth.test' }),
        { provide: SEARCH_DELAY_MS, useValue: 0 },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InstrumentSearch);
    page = fixture.nativeElement as HTMLElement;
    picked = [];
    fixture.componentInstance.picked.subscribe((instrument) => picked.push(instrument));
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const box = () => page.querySelector<HTMLInputElement>('[data-testid="instrument-search"]')!;
  const options = () => [...page.querySelectorAll<HTMLElement>('[data-testid="instrument-search-option"]')];

  async function settle(): Promise<void> {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  async function type(text: string): Promise<void> {
    box().value = text;
    box().dispatchEvent(new Event('input'));
    await settle();
  }

  function key(name: string): void {
    box().dispatchEvent(new KeyboardEvent('keydown', { key: name, bubbles: true }));
  }

  it('searches as the customer types, and lists a stock by ticker and a fund by name', async () => {
    await type('tata');
    const request = http.expectOne(search('tata'));
    expect(request.request.params.get('limit')).toBe('8');
    request.flush(TATA);
    await settle();

    expect(options().map((o) => o.querySelector('.primary')?.textContent?.trim())).toEqual([
      'TATASTEEL.NS',
      'TCS.NS',
      'Tata Small Cap Fund - Direct Plan - Growth',
    ]);
    expect(box().getAttribute('aria-expanded')).toBe('true');
  });

  it('does not search blank text', async () => {
    await type('   ');

    http.expectNone((request) => request.url === `${TRADE}/api/v1/instruments`);
    expect(options()).toHaveLength(0);
  });

  it('narrows the search to one type when asked', async () => {
    fixture.componentRef.setInput('type', 'MF');
    await type('tata');

    expect(http.expectOne(search('tata')).request.params.get('type')).toBe('MF');
  });

  it('picks with a click, says what was picked, and remembers it for naming', async () => {
    await type('tata');
    http.expectOne(search('tata')).flush(TATA);
    await settle();

    options()[2].dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    await settle();

    expect(picked.map((i) => i.symbol)).toEqual(['147794']);
    expect(options()).toHaveLength(0);
    expect(TestBed.inject(InstrumentCatalog).label('147794')).toBe('Tata Small Cap Fund - Direct Plan - Growth');
  });

  it('picks with the keyboard: arrows move, Enter picks, Escape closes', async () => {
    await type('tata');
    http.expectOne(search('tata')).flush(TATA);
    await settle();

    key('ArrowDown');
    await settle();
    expect(box().getAttribute('aria-activedescendant')).toBe(options()[1].id);
    key('Enter');
    await settle();
    expect(picked.map((i) => i.symbol)).toEqual(['TCS.NS']);

    await type('tat');
    http.expectOne(search('tat')).flush(TATA);
    await settle();
    key('Escape');
    await settle();
    expect(options()).toHaveLength(0);
  });

  it('shows only the answer to the latest text, however the answers arrive', async () => {
    await type('ta');
    const slow = http.expectOne(search('ta'));
    await type('tata');
    http.expectOne(search('tata')).flush(TATA.slice(0, 1));
    slow.flush(TATA);
    await settle();

    expect(options()).toHaveLength(1);
  });

  it('says when nothing matches, and when the search failed', async () => {
    await type('zzz');
    http.expectOne(search('zzz')).flush([]);
    await settle();
    expect(page.textContent).toContain('No instrument matches');

    await type('zzzz');
    http.expectOne(search('zzzz')).flush({ errorCode: 'AUTH-401', message: 'x' }, { status: 401, statusText: 'Unauthorized' });
    await settle();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain(ERROR_MESSAGES['AUTH-401']);
  });
});
