import { Global, Logger, Module } from '@nestjs/common';
import { Kafka, logLevel, LogEntry } from 'kafkajs';
import { Env } from '../config/env';
import { KAFKA, KafkaPublisher } from './kafka-publisher';

/** kafkajs logs through Nest, so its lines pass the RedactingLogger like everything else. */
function nestLogCreator() {
  const log = new Logger('Kafka');
  return ({ level, log: entry }: LogEntry) => {
    const line = `${entry.message}${entry.broker ? ` broker=${entry.broker}` : ''}`;
    if (level === logLevel.ERROR) log.error(line);
    else if (level === logLevel.WARN) log.warn(line);
    else log.debug(line);
  };
}

@Global()
@Module({
  providers: [
    {
      provide: KAFKA,
      inject: [Env],
      useFactory: (env: Env) =>
        new Kafka({
          clientId: 'auth-service',
          brokers: env.kafkaBrokers,
          logLevel: logLevel.WARN,
          logCreator: nestLogCreator,
          // Bounded: a relay tick or a DLT write gives up and tries again later
          // rather than holding a consumer or a timer for minutes.
          retry: { retries: 3, initialRetryTime: 300 },
        }),
    },
    KafkaPublisher,
  ],
  exports: [KAFKA, KafkaPublisher],
})
export class MessagingModule {}
