import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideClients } from './provide-clients';
import { InstrumentCatalog } from './instrument-catalog';

const TRADE = 'http://trade.test';
const LIST = [
  { symbol: 'MRF.NS', name: 'MRF Limited', type: 'STOCK', exchange: 'NSE' },
  { symbol: 'GOLDBEES', name: 'Gold ETF', type: 'ETF', exchange: 'NSE' },
  { symbol: '120716', name: 'UTI Nifty 50 Index Fund - Direct Plan - Growth', type: 'MF', exchange: null },
  { symbol: 'SCH100001', name: 'Bluechip Growth Fund', type: 'MF', exchange: null },
];

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

  async function loaded(): Promise<void> {
    const done = catalog.load();
    http.expectOne(`${TRADE}/api/v1/instruments`).flush(LIST);
    await done;
  }

  it('names a stock by its ticker and a fund by its name, nothing else', async () => {
    await loaded();

    expect(catalog.label('MRF.NS')).toBe('MRF.NS');
    expect(catalog.label('GOLDBEES')).toBe('GOLDBEES');
    expect(catalog.label('120716')).toBe('UTI Nifty 50 Index Fund - Direct Plan - Growth');
  });

  it('puts funds on the mutual funds dashboard by their type, not by what the symbol looks like', async () => {
    await loaded();

    expect(catalog.segmentOf('SCH100001')).toBe('mutual-funds');
    expect(catalog.segmentOf('120716')).toBe('mutual-funds');
    expect(catalog.segmentOf('MRF.NS')).toBe('stocks');
    expect(catalog.segmentOf('GOLDBEES')).toBe('stocks');
  });

  it('knows a delisted instrument is no longer tradable, and still files it sensibly', async () => {
    await loaded();

    expect(catalog.isTradable('MERSTL')).toBe(false);
    expect(catalog.isTradable('MRF.NS')).toBe(true);
    expect(catalog.label('MERSTL')).toBe('MERSTL');
    expect(catalog.segmentOf('MERSTL')).toBe('stocks');
  });

  it('reads the list once, however many screens ask', async () => {
    const first = catalog.load();
    const second = catalog.load();
    http.expectOne(`${TRADE}/api/v1/instruments`).flush(LIST);
    await Promise.all([first, second]);
    await catalog.load();
  });

  it('keeps the failure for the screen to show, and tries again on the next load', async () => {
    const failed = catalog.load();
    http.expectOne(`${TRADE}/api/v1/instruments`).flush({ errorCode: 'AUTH-401', message: 'x' }, { status: 401, statusText: 'Unauthorized' });
    await failed;
    expect(catalog.error()).not.toBeNull();

    await loaded();
    expect(catalog.error()).toBeNull();
    expect(catalog.instruments()).toHaveLength(4);
  });
});
