import { Pool } from 'pg';
import { OutboxRepository } from './outbox.repository';
import { ProvisioningService } from './provisioning.service';

/** A pg client that records statements and answers INSERTs as scripted. */
function fakeClient(opts: { provisionedRows: number; outboxFails?: boolean }) {
  const statements: string[] = [];
  const params: unknown[][] = [];
  const client = {
    query: jest.fn(async (sql: string, values?: unknown[]) => {
      statements.push(sql.trim().split(/\s+/).slice(0, 3).join(' '));
      params.push(values ?? []);
      if (sql.includes('INSERT INTO provisioned_account')) return { rowCount: opts.provisionedRows };
      if (sql.includes('INSERT INTO outbox_event') && opts.outboxFails) throw new Error('disk full');
      return { rowCount: 1, rows: [] };
    }),
    release: jest.fn(),
  };
  return { client, statements, params };
}

function serviceWith(client: unknown) {
  const pool = { connect: jest.fn().mockResolvedValue(client) } as unknown as Pool;
  return new ProvisioningService(pool, new OutboxRepository(pool));
}

describe('ProvisioningService', () => {
  it('inserts the account and its outbox row in one committed transaction', async () => {
    const { client, statements, params } = fakeClient({ provisionedRows: 1 });

    await expect(serviceWith(client).provision(7)).resolves.toBe(true);

    expect(statements).toEqual([
      'BEGIN',
      'INSERT INTO provisioned_account',
      'INSERT INTO outbox_event',
      'COMMIT',
    ]);

    const [, topic, key, envelopeJson] = params[2] as [string, string, string, string];
    const envelope = JSON.parse(envelopeJson);
    expect(topic).toBe('account-provisioning');
    expect(key).toBe('7');
    expect(envelope.eventType).toBe('ACCOUNT_PROVISIONED');
    expect(envelope.payload).toEqual({ clientId: 7 });
    expect(client.release).toHaveBeenCalled();
  });

  it('publishes nothing for an account that was already provisioned', async () => {
    const { client, statements } = fakeClient({ provisionedRows: 0 });

    await expect(serviceWith(client).provision(7)).resolves.toBe(false);

    expect(statements).toEqual(['BEGIN', 'INSERT INTO provisioned_account', 'ROLLBACK']);
  });

  it('rolls back the account when the outbox row fails, so no message can ever be sent', async () => {
    const { client, statements } = fakeClient({ provisionedRows: 1, outboxFails: true });

    await expect(serviceWith(client).provision(7)).rejects.toThrow('disk full');

    expect(statements).toContain('ROLLBACK');
    expect(statements).not.toContain('COMMIT');
    expect(client.release).toHaveBeenCalled();
  });
});
