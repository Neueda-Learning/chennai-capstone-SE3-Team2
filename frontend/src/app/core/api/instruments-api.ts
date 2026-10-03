import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { InstrumentResponse, InstrumentsService } from '../../../generated/extensions';

/** What can be ordered, through the generated extensions client. */
@Injectable({ providedIn: 'root' })
export class InstrumentsApi {
  private readonly instruments = inject(InstrumentsService);

  /** GET /api/v1/instruments: every tradable instrument, by symbol; delisted ones left out. */
  tradable(): Promise<InstrumentResponse[]> {
    return firstValueFrom(this.instruments.getTradableInstruments());
  }
}
