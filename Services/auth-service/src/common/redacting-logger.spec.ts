import { redact, REDACTED } from './redacting-logger';

describe('redact', () => {
  it('removes a password at the top level', () => {
    expect(redact({ username: 'priya.menon', password: 'hunter2' }))
      .toEqual({ username: 'priya.menon', password: REDACTED });
  });

  it('removes one nested several levels deep', () => {
    const logged = redact({ request: { body: { credentials: { password: 'hunter2' } } } });
    expect(JSON.stringify(logged)).not.toContain('hunter2');
  });

  it('removes one inside an array, which is how a batch gets logged', () => {
    const logged = redact([{ password: 'hunter2' }, { password: 'hunter3' }]);
    expect(JSON.stringify(logged)).not.toContain('hunter');
  });

  it('removes tokens as well as passwords', () => {
    const logged = redact({ accessToken: 'eyJhbG', refreshToken: '9c1f7a', authorization: 'Bearer x' });
    expect(logged).toEqual({ accessToken: REDACTED, refreshToken: REDACTED, authorization: REDACTED });
  });

  it('keeps an error readable without serialising whatever is attached to it', () => {
    const error: any = new Error('login failed');
    error.dto = { password: 'hunter2' };

    const logged = redact(error) as Record<string, unknown>;

    expect(logged.message).toBe('login failed');
    expect(JSON.stringify(logged)).not.toContain('hunter2');
  });

  it('survives a circular reference rather than throwing inside a log call', () => {
    const circular: any = { username: 'priya.menon' };
    circular.self = circular;
    expect(() => redact(circular)).not.toThrow();
  });
});
