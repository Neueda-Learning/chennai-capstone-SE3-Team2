import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { InstrumentResponse } from '../../../generated/extensions';
import { OrderResponse, OrderSide, OrderStatus } from '../../../generated/trade';
import { InstrumentCatalog } from '../../core/api/instrument-catalog';
import { TradeApi } from '../../core/api/trade-api';
import { CurrentAccount } from '../../core/session/current-account';
import { Session } from '../../core/session/session';
import { ErrorMessage } from '../../shared/error-message/error-message';
import { InstrumentSearch } from '../../shared/instrument-search/instrument-search';
import { priceAboveZeroTwoDecimals, wholeNumberAboveZero } from './order-validators';

/** What each status the API can return means to the person who just placed the order. */
const OUTCOME: Readonly<Record<OrderStatus, string>> = {
  NEW: 'Accepted and still working: it has not been executed yet. Your orders on the dashboard update as it is.',
  FILLED: 'Filled.',
  REJECTED: 'Rejected when it was executed. Your orders on the dashboard show it.',
  CANCELLED: 'Cancelled.',
};

@Component({
  selector: 'app-order-ticket',
  imports: [ReactiveFormsModule, ErrorMessage, InstrumentSearch, RouterLink, CurrencyPipe, DecimalPipe],
  templateUrl: './order-ticket.html',
  styleUrl: './order-ticket.css',
})
export class OrderTicket {
  private readonly tradeApi = inject(TradeApi);
  /** Names the instrument the result is about; looks up a linked symbol. */
  protected readonly catalog = inject(InstrumentCatalog);

  /** ?symbol= and ?side=, so a holding's Sell link opens the ticket filled in. */
  readonly symbol = input<string>();
  readonly side = input<string>();

  /**
   * The account comes from the token and is shown read-only. An account field
   * the user could edit would be an authorisation decision moved into the
   * browser -- and the API refuses any other account anyway (ACC-403).
   *
   * Sent as the token's numeric key; shown as the reference the customer
   * knows (ACC-000003), since the key is the database's.
   */
  protected readonly accountId = inject(Session).accountId;
  protected readonly account = inject(CurrentAccount).account;

  protected readonly sides = [OrderSide.Buy, OrderSide.Sell];
  protected readonly outcome = OUTCOME;

  protected readonly form = new FormGroup({
    // Picked from a search of the tradable instruments, so only a symbol the API knows can be sent.
    symbol: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    side: new FormControl<OrderSide>(OrderSide.Buy, { nonNullable: true }),
    quantity: new FormControl('', { nonNullable: true, validators: [Validators.required, wholeNumberAboveZero] }),
    price: new FormControl('', { nonNullable: true, validators: [Validators.required, priceAboveZeroTwoDecimals] }),
  });

  protected readonly submitting = signal(false);
  protected readonly result = signal<OrderResponse | null>(null);
  protected readonly error = signal<unknown>(null);

  /** The instrument picked, shown in place of the search until changed. */
  protected readonly chosen = signal<InstrumentResponse | null>(null);
  /** Why a linked symbol could not be looked up. */
  protected readonly lookupError = this.catalog.error;

  /**
   * The idempotency key of an order whose fate is unknown: sent, but no
   * response came back. Retrying that same order reuses it, so if the first
   * attempt did land the API answers ORD-409 instead of placing it twice. Any
   * other outcome, or a different order, starts a new key.
   */
  private unconfirmed: { readonly key: string; readonly order: string } | null = null;

  constructor() {
    // A symbol from the link is taken only once a lookup shows it is tradable.
    effect(() => {
      const wanted = this.symbol()?.trim().toUpperCase();
      if (wanted) {
        untracked(() => void this.prefill(wanted));
      }
    });
    effect(() => {
      const side = this.side()?.trim().toUpperCase();
      if (side === OrderSide.Buy || side === OrderSide.Sell) {
        untracked(() => this.form.controls.side.setValue(side));
      }
    });
  }

  private async prefill(symbol: string): Promise<void> {
    await this.catalog.resolve([symbol]);
    const instrument = this.catalog.get(symbol);
    if (instrument?.tradable) {
      this.choose(instrument);
    }
  }

  protected choose(instrument: InstrumentResponse): void {
    this.chosen.set(instrument);
    this.form.controls.symbol.setValue(instrument.symbol);
  }

  /** Back to the search, to pick another. */
  protected change(): void {
    this.chosen.set(null);
    this.form.controls.symbol.setValue('');
  }

  async submit(): Promise<void> {
    const accountId = this.accountId();
    if (this.form.invalid || accountId === null) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const order = {
      accountId,
      symbol: value.symbol.trim().toUpperCase(),
      side: value.side,
      quantity: Number(value.quantity),
      price: Number(value.price),
    };
    const fingerprint = JSON.stringify(order);
    const idempotencyKey = this.unconfirmed?.order === fingerprint ? this.unconfirmed.key : crypto.randomUUID();
    this.unconfirmed = { key: idempotencyKey, order: fingerprint };

    this.submitting.set(true);
    this.error.set(null);
    this.result.set(null);
    try {
      this.result.set(await this.tradeApi.placeOrder({ ...order, idempotencyKey }));
      this.unconfirmed = null;
      this.form.reset({ symbol: order.symbol, side: order.side, quantity: '', price: '' });
    } catch (failure) {
      if (!(failure instanceof HttpErrorResponse && failure.status === 0)) {
        this.unconfirmed = null;
      }
      this.error.set(failure);
    } finally {
      this.submitting.set(false);
    }
  }

  protected invalid(name: 'symbol' | 'quantity' | 'price'): boolean {
    const control = this.form.controls[name];
    return control.touched && control.invalid;
  }
}
