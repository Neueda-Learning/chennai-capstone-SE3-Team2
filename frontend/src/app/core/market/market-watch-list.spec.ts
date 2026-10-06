import { TestBed } from '@angular/core/testing';
import { testToken } from '../../../testing/tokens';
import { Session } from '../session/session';
import { DEFAULT_MARKET_WATCH, MARKET_WATCH_LIMIT, MARKET_WATCH_STORAGE, MarketWatchList } from './market-watch-list';

/** A browser's localStorage, kept in memory. */
class MemoryStorage implements Storage {
  private readonly items = new Map<string, string>();
  get length(): number {
    return this.items.size;
  }
  clear(): void {
    this.items.clear();
  }
  getItem(key: string): string | null {
    return this.items.get(key) ?? null;
  }
  key(index: number): string | null {
    return [...this.items.keys()][index] ?? null;
  }
  removeItem(key: string): void {
    this.items.delete(key);
  }
  setItem(key: string, value: string): void {
    this.items.set(key, value);
  }
}

describe('MarketWatchList', () => {
  let localStorage: MemoryStorage;

  beforeEach(() => {
    sessionStorage.clear();
    localStorage = new MemoryStorage();
    TestBed.configureTestingModule({ providers: [{ provide: MARKET_WATCH_STORAGE, useValue: localStorage }] });
  });

  function signedInAs(accountId: number): MarketWatchList {
    TestBed.inject(Session).start(testToken({ accountId }));
    const list = TestBed.inject(MarketWatchList);
    TestBed.tick();
    return list;
  }

  it('starts from the default list', () => {
    expect(signedInAs(3).symbols()).toEqual(DEFAULT_MARKET_WATCH);
  });

  it('adds to the end once, removes, and keeps the list for the next visit', () => {
    const list = signedInAs(3);
    expect(list.add('TATASTEEL.NS')).toBe(true);
    expect(list.add('TATASTEEL.NS')).toBe(false);
    list.remove('MRF.NS');

    expect(list.symbols().at(-1)).toBe('TATASTEEL.NS');
    expect(list.symbols()).not.toContain('MRF.NS');
    expect(JSON.parse(localStorage.getItem('yellow.market-watch.3')!)).toEqual(list.symbols());
  });

  it('keeps one list per account, so a shared browser does not mix them', () => {
    signedInAs(3).add('TATASTEEL.NS');

    localStorage.setItem('yellow.market-watch.4', JSON.stringify(['ITC.NS']));
    TestBed.inject(Session).start(testToken({ accountId: 4 }));
    TestBed.tick();

    expect(TestBed.inject(MarketWatchList).symbols()).toEqual(['ITC.NS']);
  });

  it(`holds at most ${MARKET_WATCH_LIMIT}, the most one price request takes`, () => {
    const list = signedInAs(3);
    for (let i = 0; list.symbols().length < MARKET_WATCH_LIMIT; i++) {
      list.add(`S${i}.NS`);
    }

    expect(list.full).toBe(true);
    expect(list.add('ONEMORE.NS')).toBe(false);
  });

  it('starts from the default when what was stored is not a list', () => {
    localStorage.setItem('yellow.market-watch.3', '{"not":"a list"}');

    expect(signedInAs(3).symbols()).toEqual(DEFAULT_MARKET_WATCH);
  });
});
