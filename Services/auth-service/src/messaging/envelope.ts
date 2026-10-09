import { randomUUID } from 'node:crypto';
import { SOURCE } from './topics';

/** The platform envelope: identical on every topic, so one deserialiser covers them all. */
export interface Envelope<T> {
  eventId: string;
  eventType: string;
  eventTime: string;
  source: string;
  schemaVersion: number;
  payload: T;
}

export const ACCOUNT_PROVISIONED = 'ACCOUNT_PROVISIONED';

/** The client id and nothing else: the topic is retained for days and read widely. */
export interface AccountProvisionedPayload {
  clientId: number;
}

export function accountProvisioned(clientId: number, now: Date = new Date()): Envelope<AccountProvisionedPayload> {
  return {
    eventId: randomUUID(),
    eventType: ACCOUNT_PROVISIONED,
    // Second precision, 'Z' suffix: RFC 3339 in UTC, as the contract's examples show.
    eventTime: now.toISOString().replace(/\.\d{3}Z$/, 'Z'),
    source: SOURCE,
    schemaVersion: 1,
    payload: { clientId },
  };
}
