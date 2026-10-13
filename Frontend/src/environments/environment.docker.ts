import { ApiConfig } from '../app/core/config/api-config';

/**
 * The build the frontend container serves (Frontend/Dockerfile,
 * `ng build --configuration docker`). nginx serves this page AND forwards
 * /api/... to the order-service and /auth/..., /activate to the auth-service,
 * so both APIs are at the page's own origin: no address is baked into the
 * bundle, the same image works on any host, and the browser makes no
 * cross-origin request at all.
 */
const sameOrigin = globalThis.location.origin;

export const environment: { readonly api: ApiConfig } = {
  api: {
    tradeApiUrl: sameOrigin,
    authApiUrl: sameOrigin,
  },
};
