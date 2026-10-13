import { Global, Module } from '@nestjs/common';
import { Pool } from 'pg';
import { Env } from '../config/env';

/**
 * The connection pool to the auth service's own schema. Every connection
 * starts with search_path set to it, so a table name means auth's table and
 * never one of the trading tables beside it.
 */
export const AUTH_POOL = 'AUTH_POOL';

@Global()
@Module({
  providers: [
    Env,
    {
      provide: AUTH_POOL,
      inject: [Env],
      useFactory: (env: Env) =>
        new Pool({ connectionString: env.databaseUrl, max: 5, options: `-c search_path=${env.databaseSchema}` }),
    },
  ],
  exports: [AUTH_POOL, Env],
})
export class DatabaseModule {}
