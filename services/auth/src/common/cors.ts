import { CorsOptions } from '@nestjs/common/interfaces/external/cors-options.interface';

/**
 * Cross-origin rules for the trading UI. The origin is an exact allow list:
 * a browser on any other origin gets no Access-Control-Allow-Origin and
 * cannot read a response. No credentials -- the UI sends a bearer token in a
 * header, never a cookie.
 */
export function corsOptions(allowedOrigins: string[]): CorsOptions {
  return {
    origin: allowedOrigins,
    methods: ['GET', 'POST'],
    allowedHeaders: ['Authorization', 'Content-Type'],
    credentials: false,
    maxAge: 600,
  };
}
