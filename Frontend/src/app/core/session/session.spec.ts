import { TestBed } from '@angular/core/testing';
import { testToken } from '../../../testing/tokens';
import { Session, decodeClaims } from './session';

describe('Session', () => {
  beforeEach(() => sessionStorage.clear());

  it('starts from a token, reads the account from it, and keeps it for a reload', () => {
    const session = TestBed.inject(Session);

    session.start(testToken({ accountId: 7 }));

    expect(session.isSignedIn()).toBe(true);
    expect(session.accountId()).toBe(7);
    expect(sessionStorage.getItem('trading-ui.accessToken')).toBe(session.accessToken());
  });

  it('keeps the refresh token beside it, for a reload', () => {
    const session = TestBed.inject(Session);

    session.start(testToken(), 'refresh-1');

    expect(session.refreshToken()).toBe('refresh-1');
    expect(sessionStorage.getItem('trading-ui.refreshToken')).toBe('refresh-1');
  });

  it('clears the session on sign-out, both tokens, from memory and from storage', () => {
    const session = TestBed.inject(Session);
    session.start(testToken(), 'refresh-1');

    session.end();

    expect(session.isSignedIn()).toBe(false);
    expect(session.accessToken()).toBeNull();
    expect(session.accountId()).toBeNull();
    expect(session.refreshToken()).toBeNull();
    expect(sessionStorage.getItem('trading-ui.accessToken')).toBeNull();
    expect(sessionStorage.getItem('trading-ui.refreshToken')).toBeNull();
  });

  it('treats an expired token as signed out, and clears it -- but keeps the refresh token that can renew it', () => {
    const session = TestBed.inject(Session);
    session.start(testToken({ exp: Math.floor(Date.now() / 1000) - 1 }), 'refresh-1');

    expect(session.isSignedIn()).toBe(false);
    expect(session.accessToken()).toBeNull();
    expect(sessionStorage.getItem('trading-ui.accessToken')).toBeNull();
    expect(session.refreshToken()).toBe('refresh-1');
  });

  it('refuses a token it cannot read', () => {
    const session = TestBed.inject(Session);

    expect(() => session.start('not-a-jwt')).toThrow();
    expect(session.isSignedIn()).toBe(false);
  });

  it('decodes only a payload carrying the claims a session needs', () => {
    expect(decodeClaims(testToken({ accountId: 3 }))?.accountId).toBe(3);
    expect(decodeClaims('a.b.c')).toBeNull();
    expect(decodeClaims(testToken({ accountId: '3' as unknown as number }))).toBeNull();
  });
});
