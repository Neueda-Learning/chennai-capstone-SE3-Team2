import { accountProvisioned } from './envelope';

describe('ACCOUNT_PROVISIONED envelope', () => {
  const now = new Date('2026-09-30T18:22:41.123Z');
  const envelope = accountProvisioned(7, now);

  it('carries exactly the five envelope fields plus a payload', () => {
    expect(Object.keys(envelope).sort()).toEqual(
      ['eventId', 'eventTime', 'eventType', 'payload', 'schemaVersion', 'source'],
    );
  });

  it('matches the contract field for field', () => {
    expect(envelope).toEqual({
      eventId: expect.stringMatching(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/),
      eventType: 'ACCOUNT_PROVISIONED',
      eventTime: '2026-09-30T18:22:41Z',
      source: 'auth-service',
      schemaVersion: 1,
      payload: { clientId: 7 },
    });
  });

  it('puts the client id in the payload and nothing else: no name, no email, no token', () => {
    expect(Object.keys(envelope.payload)).toEqual(['clientId']);
    expect(typeof envelope.payload.clientId).toBe('number');
  });

  it('gives every event its own id, the consumer idempotency key', () => {
    expect(accountProvisioned(7, now).eventId).not.toBe(envelope.eventId);
  });
});
