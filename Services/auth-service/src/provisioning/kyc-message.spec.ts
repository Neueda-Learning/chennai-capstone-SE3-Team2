import { interpretKycMessage } from './kyc-message';

const message = (value: unknown) => Buffer.from(typeof value === 'string' ? value : JSON.stringify(value));

const verified = (payload: unknown) => ({
  eventId: '3f0c9a52-1d7e-4b8a-9c2e-5a6b7c8d9e0f',
  eventType: 'KYC_VERIFIED',
  eventTime: '2026-10-02T09:14:22Z',
  source: 'kyc-service',
  schemaVersion: 1,
  payload,
});

describe('interpretKycMessage', () => {
  it('provisions the client a KYC_VERIFIED names', () => {
    expect(interpretKycMessage(message(verified({ clientId: 11 })))).toEqual({
      kind: 'provision',
      clientId: 11,
      eventId: '3f0c9a52-1d7e-4b8a-9c2e-5a6b7c8d9e0f',
    });
  });

  it('tolerates fields it does not know, as the contract requires', () => {
    const decision = interpretKycMessage(message({ ...verified({ clientId: 11, extra: true }), traceId: 'x' }));
    expect(decision.kind).toBe('provision');
  });

  it('ignores other event types rather than dead-lettering them', () => {
    expect(interpretKycMessage(message({ ...verified({ clientId: 11 }), eventType: 'KYC_REJECTED' })))
      .toEqual({ kind: 'ignore', eventType: 'KYC_REJECTED' });
  });

  it.each([
    ['an empty message', null],
    ['non-JSON bytes', Buffer.from('not json')],
    ['a JSON array', message([1, 2])],
    ['no eventType', message({ payload: { clientId: 1 } })],
    ['no eventId', message({ ...verified({ clientId: 1 }), eventId: undefined })],
    ['no payload', message({ ...verified(undefined) })],
    ['a string clientId', message(verified({ clientId: '11' }))],
    ['a zero clientId', message(verified({ clientId: 0 }))],
    ['a negative clientId', message(verified({ clientId: -4 }))],
    ['a fractional clientId', message(verified({ clientId: 1.5 }))],
  ])('treats %s as poison', (_label, value) => {
    expect(interpretKycMessage(value as Buffer | null).kind).toBe('poison');
  });
});
