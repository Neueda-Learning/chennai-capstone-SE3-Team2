import { Logger } from '@nestjs/common';
import { EachMessagePayload, Kafka } from 'kafkajs';
import { KafkaPublisher } from '../messaging/kafka-publisher';
import { KycEventsConsumer } from './kyc-events.consumer';
import { ProvisioningService } from './provisioning.service';

function payload(value: Buffer | null, key = '11'): EachMessagePayload {
  return {
    topic: 'kyc-events',
    partition: 2,
    message: { key: Buffer.from(key), value, offset: '41', timestamp: '0', attributes: 0, headers: {} },
    heartbeat: async () => undefined,
    pause: () => () => undefined,
  } as unknown as EachMessagePayload;
}

const verified = (clientId: unknown) =>
  Buffer.from(JSON.stringify({
    eventId: '3f0c9a52-1d7e-4b8a-9c2e-5a6b7c8d9e0f',
    eventType: 'KYC_VERIFIED',
    eventTime: '2026-10-02T09:14:22Z',
    source: 'kyc-service',
    schemaVersion: 1,
    payload: { clientId },
  }));

describe('KycEventsConsumer', () => {
  let provision: jest.Mock;
  let sendRaw: jest.Mock;
  let consumer: KycEventsConsumer;

  beforeEach(() => {
    jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => undefined);
    provision = jest.fn().mockResolvedValue(true);
    sendRaw = jest.fn().mockResolvedValue(undefined);
    consumer = new KycEventsConsumer(
      {} as Kafka,
      { provision } as unknown as ProvisioningService,
      { sendRaw } as unknown as KafkaPublisher,
    );
  });

  afterEach(() => jest.restoreAllMocks());

  it('provisions the verified client', async () => {
    await consumer.handle(payload(verified(11)));

    expect(provision).toHaveBeenCalledWith(11);
    expect(sendRaw).not.toHaveBeenCalled();
  });

  it('dead-letters a malformed message on the first attempt, with its original bytes and the reason', async () => {
    const bad = verified('eleven');
    await consumer.handle(payload(bad));

    expect(provision).not.toHaveBeenCalled();
    expect(sendRaw).toHaveBeenCalledWith(
      'kyc-events.DLT',
      Buffer.from('11'),
      bad,
      expect.objectContaining({
        'x-failure-class': 'POISON',
        'x-failure-reason': 'payload.clientId is not a positive integer',
        'x-original-topic': 'kyc-events',
        'x-original-partition': '2',
        'x-original-offset': '41',
      }),
    );
  });

  it('acknowledges event types it does not act on without dead-lettering them', async () => {
    const rejected = Buffer.from(JSON.stringify({ eventId: 'e', eventType: 'KYC_REJECTED', payload: { clientId: 11 } }));

    await consumer.handle(payload(rejected));

    expect(provision).not.toHaveBeenCalled();
    expect(sendRaw).not.toHaveBeenCalled();
  });

  it('lets a database failure escape, so the message is retried rather than lost', async () => {
    provision.mockRejectedValue(new Error('connection terminated'));

    await expect(consumer.handle(payload(verified(11)))).rejects.toThrow('connection terminated');
    expect(sendRaw).not.toHaveBeenCalled();
  });
});
