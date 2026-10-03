import { InjectionToken } from '@angular/core';

/** The two platform APIs this application calls, as origins without a trailing slash. */
export interface ApiConfig {
  readonly tradeApiUrl: string;
  readonly authApiUrl: string;
}

export const API_CONFIG = new InjectionToken<ApiConfig>('API_CONFIG');
