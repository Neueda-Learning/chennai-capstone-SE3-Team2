import { ApiConfig } from '../app/core/config/api-config';

/**
 * Where the two platform APIs are. Origins only -- no keys, no secrets: this
 * file ends up in the bundle every browser downloads.
 */
export const environment: { readonly api: ApiConfig } = {
  api: {
    tradeApiUrl: 'http://localhost:8080',
    authApiUrl: 'http://localhost:3000',
  },
};
