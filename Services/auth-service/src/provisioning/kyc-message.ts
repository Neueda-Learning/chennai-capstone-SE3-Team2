export const KYC_VERIFIED = 'KYC_VERIFIED';

export type KycDecision =
  | { kind: 'provision'; clientId: number; eventId: string }
  | { kind: 'ignore'; eventType: string }
  | { kind: 'poison'; reason: string };

/**
 * What a message on kyc-events asks auth to do. Pure, so every branch is tested
 * without a broker.
 *
 * Only KYC_VERIFIED provisions. Any other well-formed event type is ignored and
 * acknowledged, so KYC may add types (KYC_REJECTED, say) without breaking auth.
 * Anything auth cannot read is poison: no retry will make it readable.
 */
export function interpretKycMessage(value: Buffer | null): KycDecision {
  if (!value || value.length === 0) {
    return { kind: 'poison', reason: 'empty message' };
  }

  let envelope: unknown;
  try {
    envelope = JSON.parse(value.toString('utf8'));
  } catch {
    return { kind: 'poison', reason: 'not JSON' };
  }
  if (!isObject(envelope)) {
    return { kind: 'poison', reason: 'envelope is not an object' };
  }

  const { eventId, eventType, payload } = envelope;
  if (typeof eventType !== 'string' || eventType.length === 0) {
    return { kind: 'poison', reason: 'missing eventType' };
  }
  if (eventType !== KYC_VERIFIED) {
    return { kind: 'ignore', eventType };
  }
  if (typeof eventId !== 'string' || eventId.length === 0) {
    return { kind: 'poison', reason: 'missing eventId' };
  }
  if (!isObject(payload)) {
    return { kind: 'poison', reason: 'missing payload' };
  }

  const clientId = payload.clientId;
  if (typeof clientId !== 'number' || !Number.isSafeInteger(clientId) || clientId <= 0) {
    return { kind: 'poison', reason: 'payload.clientId is not a positive integer' };
  }

  return { kind: 'provision', clientId, eventId };
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
