import { Module } from '@nestjs/common';
import { KycEventsConsumer } from './kyc-events.consumer';
import { OutboxRelay } from './outbox-relay';
import { OutboxRepository } from './outbox.repository';
import { ProvisioningService } from './provisioning.service';

@Module({
  providers: [ProvisioningService, OutboxRepository, OutboxRelay, KycEventsConsumer],
})
export class ProvisioningModule {}
