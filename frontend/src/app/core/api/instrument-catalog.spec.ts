import { provideHttpClient } from '@angular/common/http';
import { HttpRequest } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { InstrumentResponse } from '../../../generated/extensions';
import { provideClients } from './provide-clients';
import { InstrumentCatalog } from './instrument-catalog';

const TRADE = 'http://trade.test';
const LIST: InstrumentResponse[] = [
  { symbol: 'MRF.NS', name: 'MRF Limited', type: 'STOCK', exchange: 'NSE', tradable: true },
  { symbol: 'GOLDBEES', name: 'Gold ETF', type: 'ETF', exchange: 'NSE', tradable: true },
  { symbol: '120716', name: 'UTI Nifty 50 Index Fund - Direct Plan - Growth', type: 'MF', exchange: null, tradable: true },
  { symbol: 'SCH100001', name: 'Bluechip Growth Fund', type: 'MF', exchange: null, tradable: true },
  { symbol: 'MERSTL', name: 'Meridian Steel Ltd', type: 'STOCK', exchange: 'NSE', tradable: false },
];

/** A lookup of these symbols: GET /api/v1/instruments?symbols=A,B. */
const lookup = (symbols: string) => (request: HttpRequest<unknown>) =>
  request.url === `${TRADE}/api/v1/instruments` && request.params.get('symbols') === symbols;

describe('InstrumentCatalog', () => {
  let catalog: InstrumentCatalog;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideClients({ tradeApiUrl: TRADE, authApiUrl: 'http://auth.test' })],
    });
    catalog = TestBed.inject(InstrumentCatalog);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function resolved(): Promise<void> {
    const done = catalog.resolve(LIST.map((i) => i.symbol));
    http.expectOne(lookup('MRF.NS,GOLDBEES,120716,SCH100001,MERSTL')).flush(LIST);
    await done;
  }

  it('names a stock by its ticker and a fund by its name, nothing else', async () => {
    await resolved();

    expect(catalog.label('MRF.NS')).toBe('MRF.NS');
    expect(catalog.label('GOLDBEES')).toBe('GOLDBEES');
    expect(catalog.label('120716')).toBe('UTI Nifty 50 Index Fund - Direct Plan - Growth');
  });

  it('puts funds on the mutual funds dashboard by their type, not by what the symbol looks like', async () => {
    await resolved();

    expect(catalog.segmentOf('SCH100001')).toBe('mutual-funds');
    expect(catalog.segmentOf('120716')).toBe('mutual-funds');
    expect(catalog.segmentOf('MRF.NS')).toBe('stocks');
    expect(catalog.segmentOf('GOLDBEES')).toBe('stocks');
  });

  it('knows a delisted instrument is no longer tradable, and still names and files it', async () => {
    await resolved();

    expect(catalog.isTradable('MERSTL')).toBe(false);
    expect(catalog.isTradable('MRF.NS')).toBe(true);
    expect(catalog.label('MERSTL')).toBe('MERSTL');
    expect(catalog.segmentOf('MERSTL')).toBe('stocks');
  });

  it('before an answer, shows the symbol, guesses the dashboard from it, and offers no sale', () => {
    expect(catalog.label('122639')).toBe('122639');
    expect(catalog.segmentOf('122639')).toBe('mutual-funds');
    expect(catalog.isTradable('MRF.NS')).toBe(false);
  });

  it('asks for each symbol once, however many screens ask, a symbol nobody lists included', async () => {
    await resolved();
    const again = catalog.resolve(['MRF.NS', 'NOPE.NS']);
    http.expectOne(lookup('NOPE.NS')).flush([]);
    await again;

    await catalog.resolve(['MRF.NS', 'NOPE.NS', '120716']);
    http.expectNone((request) => request.url === `${TRADE}/api/v1/instruments`);
  });

  it('knows a symbol nobody lists, once the lookup has answered', async () => {
    expect(catalog.isMissing('NOPE.NS')).toBe(false);
    const done = catalog.resolve(['NOPE.NS', 'MRF.NS']);
    http.expectOne(lookup('NOPE.NS,MRF.NS')).flush([LIST[0]]);
    await done;

    expect(catalog.isMissing('NOPE.NS')).toBe(true);
    expect(catalog.isMissing('MRF.NS')).toBe(false);
  });

  it('asks 50 at a time, the most one lookup takes', async () => {
    const symbols = Array.from({ length: 60 }, (_, i) => `S${i}.NS`);
    const done = catalog.resolve(symbols);

    http.expectOne(lookup(symbols.slice(0, 50).join(','))).flush([]);
    await Promise.resolve();
    http.expectOne(lookup(symbols.slice(50).join(','))).flush([]);
    await done;
  });

  it('remembers what a search found, so a picked instrument is named at once', () => {
    catalog.remember([LIST[2]]);

    expect(catalog.label('120716')).toBe('UTI Nifty 50 Index Fund - Direct Plan - Growth');
    expect(catalog.get('120716')?.type).toBe('MF');
  });

  it('keeps the failure for the screen to show, and asks again next time', async () => {
    const failed = catalog.resolve(['MRF.NS']);
    http.expectOne(lookup('MRF.NS')).flush({ errorCode: 'AUTH-401', message: 'x' }, { status: 401, statusText: 'Unauthorized' });
    await failed;
    expect(catalog.error()).not.toBeNull();

    const retried = catalog.resolve(['MRF.NS']);
    http.expectOne(lookup('MRF.NS')).flush([LIST[0]]);
    await retried;
    expect(catalog.error()).toBeNull();
    expect(catalog.isTradable('MRF.NS')).toBe(true);
  });
});
