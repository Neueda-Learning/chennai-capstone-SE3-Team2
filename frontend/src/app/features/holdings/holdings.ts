import { DecimalPipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PositionResponse } from '../../../generated/trade';

/**
 * What the account holds, at what it cost. No market value: the platform
 * prices an order when it executes and keeps no live prices to show here.
 * Each holding links to the ticket, filled in to sell it.
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
}
