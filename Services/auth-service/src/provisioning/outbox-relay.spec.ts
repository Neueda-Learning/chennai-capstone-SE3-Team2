import { Logger } from '@nestjs/common';
import { Env } from '../config/env';
import { accountProvisioned } from '../messaging/envelope';
import { KafkaPublisher } from '../messaging/kafka-publisher';
import { OUTBOX_PUBLISH_FAILED, OutboxRelay } from './outbox-relay';
import { OutboxRepository, OutboxRow } from './outbox.repository';

function row(clientId: number): OutboxRow {
  const envelope = accountProvisioned(clientId);
  return { eventId: envelope.eventId, topic: 'account-provisioning', messageKey: String(clientId), envelope, attempts: 0 };
}

function setup(batch: OutboxRow[], send: jest.Mock) {
  const client = { query: jest.fn().mockResolvedValue({}), release: jest.fn() };
  const outbox = {
    connect: jest.fn().mockResolvedValue(client),
    lockBatch: jest.fn().mockResolvedValue(batch),
    markPublished: jest.fn().mockResolvedValue(undefined),
    recordFailure: jest.fn().mockResolvedValue(undefined),
  };
  const relay = new OutboxRelay(
    outbox as unknown as OutboxRepository,
    { send } as unknown as KafkaPublisher,
    { outboxPollMs: 1000 } as Env,
  );
  return { relay, outbox, client };
}

describe('OutboxRelay', () => {
  it('publishes each committed row keyed by client id, then marks it published', async () => {
    const send = jest.fn().mockResolvedValue(undefined);
    const rows = [row(7), row(8)];
    const { relay, outbox, client } = setup(rows, send);

    await expect(relay.relayOnce()).resolves.toBe(2);

    expect(send).toHaveBeenCalledWith('account-provisioning', '7', rows[0].envelope);
    expect(send).toHaveBeenCalledWith('account-provisioning', '8', rows[1].envelope);
    expect(outbox.markPublished).toHaveBeenCalledWith(client, rows[0].eventId);
    expect(outbox.markPublished).toHaveBeenCalledWith(client, rows[1].eventId);
    expect(client.query).toHaveBeenLastCalledWith('COMMIT');
  });

  it('with the broker down, keeps the row unpublished, records why, and logs a tagged warning', async () => {
    const warn = jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => undefined);
    const send = jest.fn().mockRejectedValue(new Error('Connection error: ECONNREFUSED'));
    const rows = [row(7), row(8)];
    const { relay, outbox, client } = setup(rows, send);

    await expect(relay.relayOnce()).resolves.toBe(0);

    expect(outbox.markPublished).not.toHaveBeenCalled();
    expect(outbox.recordFailure).toHaveBeenCalledWith(client, rows[0].eventId, 'Connection error: ECONNREFUSED');
    // The second row is not attempted: the broker would refuse it the same way.
    expect(send).toHaveBeenCalledTimes(1);
    expect(warn.mock.calls[0][0]).toContain(`${OUTBOX_PUBLISH_FAILED} event=${rows[0].eventId}`);
    expect(client.query).toHaveBeenLastCalledWith('COMMIT');
    warn.mockRestore();
  });

  it('does not start a second tick while one is still sending', async () => {
    let release!: () => void;
    const send = jest.fn(() => new Promise<void>((resolve) => (release = resolve)));
    const { relay } = setup([row(7)], send);

    const first = relay.relayOnce();
    await new Promise((r) => setImmediate(r));
    await expect(relay.relayOnce()).resolves.toBe(0);

    release();
    await expect(first).resolves.toBe(1);
  });
});
