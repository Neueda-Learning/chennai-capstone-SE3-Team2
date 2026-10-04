import { DecimalPipe } from '@angular/common';
import { Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PositionResponse } from '../../../generated/trade';
import { InstrumentCatalog } from '../../core/api/instrument-catalog';

/**
 * What the account holds, at what it cost. No market value: the platform
 * prices an order when it executes and keeps no live prices to show here.
 * Each holding still tradable links to the ticket, filled in to sell it; a
 * delisted one has nowhere to be sold, so it has no link.
 */
@Component({
  selector: 'app-holdings',
  imports: [DecimalPipe, RouterLink],
  templateUrl: './holdings.html',
  styleUrl: './holdings.css',
})
export class Holdings {
  /** null while the dashboard is still reading them. */
  readonly positions = input.required<readonly PositionResponse[] | null>();
  /** A stock by its ticker, a fund by its name. */
  protected readonly catalog = inject(InstrumentCatalog);
}
