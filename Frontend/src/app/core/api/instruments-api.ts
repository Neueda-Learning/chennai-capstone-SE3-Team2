import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { InstrumentResponse, InstrumentsService } from '../../../generated/extensions';

export type InstrumentType = InstrumentResponse['type'];

/** Finding instruments, through the generated extensions client. */
@Injectable({ providedIn: 'root' })
export class InstrumentsApi {
  private readonly instruments = inject(InstrumentsService);

  /** GET /api/v1/instruments?q=: tradable instruments matching the text, best match first. */
  search(text: string, type?: InstrumentType, limit = 8): Promise<InstrumentResponse[]> {
    return firstValueFrom(this.instruments.getTradableInstruments(text, type, limit));
  }

  /** GET /api/v1/instruments?symbols=: these instruments, delisted ones included. At most 50. */
  lookup(symbols: readonly string[]): Promise<InstrumentResponse[]> {
    return firstValueFrom(this.instruments.getTradableInstruments(undefined, undefined, undefined, [...symbols]));
  }
}
