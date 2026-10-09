/** Names fixed by Contracts/API Schemas/kafka-topics.md. */
export const Topics = {
  /** KYC publishes KYC_VERIFIED here; auth consumes it to provision an account. */
  KYC_EVENTS: 'kyc-events',
  KYC_EVENTS_DLT: 'kyc-events.DLT',
  /** Auth publishes ACCOUNT_PROVISIONED here once the provisioned_account row is committed. */
  ACCOUNT_PROVISIONING: 'account-provisioning',
} as const;

/** Names this logical consumer; shared with nothing else. */
export const KYC_CONSUMER_GROUP = 'auth-provisioning';

/** The `source` this service stamps on every envelope it produces. */
export const SOURCE = 'auth-service';
