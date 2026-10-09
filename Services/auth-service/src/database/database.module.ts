import { Global, Module } from '@nestjs/common';
import { Pool } from 'pg';
import { Env } from '../config/env';

/** The connection pool to the auth service's OWN database. */
export const AUTH_POOL = 'AUTH_POOL';

@Global()
@Module({
  providers: [
    Env,
    {
      provide: AUTH_POOL,
      inject: [Env],
      useFactory: (env: Env) => new Pool({ connectionString: env.databaseUrl, max: 5 }),
    },
  ],
  exports: [AUTH_POOL, Env],
})
export class DatabaseModule {}
