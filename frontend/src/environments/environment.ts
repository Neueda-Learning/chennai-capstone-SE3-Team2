import { ApiConfig } from '../app/core/config/api-config';

function runtimeHost(): string {
  if (typeof window === 'undefined') {
    return 'localhost';
  }
  return window.location.hostname;
}

/**
 * Where the two platform APIs are. Origins only -- no keys, no secrets: this
 * file ends up in the bundle every browser downloads.
 */
export const environment: { readonly api: ApiConfig } = {
  api: {
    tradeApiUrl: `http://${runtimeHost()}:8085`,
    authApiUrl: `http://${runtimeHost()}:3000`,
  },
};
