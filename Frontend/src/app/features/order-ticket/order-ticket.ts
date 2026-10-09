import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { InstrumentResponse } from '../../../generated/extensions';
import { OrderResponse, OrderSide, OrderStatus, PositionResponse } from '../../../generated/trade';
import { InstrumentCatalog } from '../../core/api/instrument-catalog';
import { TradeApi } from '../../core/api/trade-api';
import { watchPrices } from '../../core/market/live-prices';
import { CurrentAccount } from '../../core/session/current-account';
import { Session } from '../../core/session/session';
import { ErrorMessage } from '../../shared/error-message/error-message';
import { InstrumentSearch } from '../../shared/instrument-search/instrument-search';
import { expectedFillPrice, marketLimit, unitsForAmount } from './order-pricing';
import { priceAboveZeroTwoDecimals, wholeNumberAboveZero } from './order-validators';

/** What each status the API can return means to the person who just placed the order. */
const OUTCOME: Readonly<Record<OrderStatus, string>> = {
  NEW: 'Accepted and still working: it has not been executed yet. Your orders update as it is.',
  FILLED: 'Filled.',
  REJECTED: 'Rejected when it was executed. Your orders show why.',
  CANCELLED: 'Cancelled.',
};

export type OrderType = 'MARKET' | 'LIMIT';

/** An amount in rupees, above zero, at most two decimal places. */
const amountPattern = /^\d+(?:\.\d{1,2})?$/;

/**
 * The order window, as on Kite: pick an instrument and see its live price;
 * Buy or Sell; at market or at a limit; what it should cost and the cash it
 * leaves, or what you hold when selling. A fund is bought by amount, in whole
 * units at its NAV.
 *
 * The platform takes limit orders only: a market order is sent with a limit
 * just past the live price (see order-pricing.ts) and fills at the price the
 * executor finds. The checks here are courtesy; the Trade REST API's business
 * rules decide.
 */
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
  private readonly current = inject(CurrentAccount);

  /** ?symbol= and ?side=, so B and S links open the window filled in. */
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
  protected readonly account = this.current.account;

  protected readonly sides = [OrderSide.Buy, OrderSide.Sell];
  protected readonly outcome = OUTCOME;

  protected readonly form = new FormGroup({
    // Picked from a search of the tradable instruments, so only a symbol the API knows can be sent.
    symbol: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    side: new FormControl<OrderSide>(OrderSide.Buy, { nonNullable: true }),
    type: new FormControl<OrderType>('LIMIT', { nonNullable: true }),
    quantity: new FormControl('', { nonNullable: true, validators: [wholeNumberAboveZero] }),
    price: new FormControl('', { nonNullable: true, validators: [priceAboveZeroTwoDecimals] }),
    amount: new FormControl('', { nonNullable: true }),
  });
  private readonly value = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  protected readonly submitting = signal(false);
  protected readonly result = signal<OrderResponse | null>(null);
  protected readonly error = signal<unknown>(null);

  /** The instrument picked, shown in place of the search until changed. */
  protected readonly chosen = signal<InstrumentResponse | null>(null);
  /** Why a linked symbol could not be looked up. */
  protected readonly lookupError = this.catalog.error;
  /** What the account holds, for the sell side; null until read, or if the read failed. */
  private readonly positions = signal<readonly PositionResponse[] | null>(null);

  protected readonly live = watchPrices(() => {
    const chosen = this.chosen();
    return chosen ? [chosen.symbol] : [];
  });
  protected readonly quote = computed(() => {
    const chosen = this.chosen();
    return chosen ? this.live.quote(chosen.symbol) : undefined;
  });
  protected readonly fund = computed(() => this.chosen()?.type === 'MF');
  protected readonly selling = computed(() => this.value().side === OrderSide.Sell);
  /** A fund is bought by amount; sold, like a stock, in units. */
  protected readonly byAmount = computed(() => this.fund() && !this.selling());
  /** Market needs a live price to set its limit from. */
  protected readonly marketAvailable = computed(() => {
    const quote = this.quote();
    return quote !== undefined && marketLimit(this.value().side ?? OrderSide.Buy, quote, this.fund()) !== null;
  });
  protected readonly atMarket = computed(() => this.value().type === 'MARKET' && this.marketAvailable());

  /** The limit this order goes out with: the market limit, or the one typed. null while unknown. */
  protected readonly sentPrice = computed(() => {
    const quote = this.quote();
    if (this.atMarket() && quote) {
      return marketLimit(this.value().side ?? OrderSide.Buy, quote, this.fund());
    }
    const typed = (this.value().price ?? '').trim();
    return typed !== '' && this.form.controls.price.valid ? Number(typed) : null;
  });

  /** The price it should fill at: the touch at market, the limit otherwise. */
  protected readonly fillPrice = computed(() => {
    const quote = this.quote();
    if (this.atMarket() && quote) {
      return expectedFillPrice(this.value().side ?? OrderSide.Buy, quote, this.fund());
    }
    return this.sentPrice();
  });

  /** Units: typed, or what the amount buys at the NAV. */
  protected readonly units = computed(() => {
    if (this.byAmount()) {
      const amount = (this.value().amount ?? '').trim();
      const nav = this.quote()?.price;
      return amountPattern.test(amount) && nav ? unitsForAmount(Number(amount), nav) : null;
    }
    const quantity = (this.value().quantity ?? '').trim();
    return quantity !== '' && this.form.controls.quantity.valid ? Number(quantity) : null;
  });

  /** What it should cost, or bring in. */
  protected readonly estimate = computed(() => {
    const units = this.units();
    const price = this.fillPrice();
    return units !== null && price !== null ? units * price : null;
  });

  /** What the API holds against the cash while it works: units at the limit sent. */
  protected readonly held = computed(() => {
    const units = this.units();
    const price = this.sentPrice();
    return units !== null && price !== null ? units * price : null;
  });

  protected readonly cash = computed(() => this.account()?.cashBalance ?? null);
  protected readonly cashAfter = computed(() => {
    const cash = this.cash();
    const held = this.held();
    return cash !== null && held !== null ? cash - held : null;
  });

  /** How many of the chosen instrument the account holds; null when not known. */
  protected readonly holding = computed(() => {
    const chosen = this.chosen();
    const positions = this.positions();
    if (!chosen || positions === null) {
      return null;
    }
    return positions.find((position) => position.symbol === chosen.symbol)?.quantity ?? 0;
  });

  /** A buy the available cash cannot cover, or a sale of more than is held. */
  protected readonly shortfall = computed<'cash' | 'holding' | null>(() => {
    if (this.selling()) {
      const holding = this.holding();
      const units = this.units();
      return holding !== null && units !== null && units > holding ? 'holding' : null;
    }
    const after = this.cashAfter();
    return after !== null && after < 0 ? 'cash' : null;
  });

  /**
   * The idempotency key of an order whose fate is unknown: sent, but no
   * response came back. Retrying that same order reuses it, so if the first
   * attempt did land the API answers ORD-409 instead of placing it twice. Any
   * other outcome, or a different order, starts a new key.
   */
  private unconfirmed: { readonly key: string; readonly order: string } | null = null;

  constructor() {
    // The cash available now, not as it was at sign-in: a deposit may have landed since.
    void this.current.refresh();
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
    effect(() => {
      const accountId = this.accountId();
      if (accountId !== null) {
        untracked(() => void this.readPositions(accountId));
      }
    });
    // The first live price of a newly picked instrument: at market by default,
    // and the limit box filled in with it, as Kite does.
    effect(() => {
      const quote = this.quote();
      if (quote?.price === null || quote?.price === undefined) {
        return;
      }
      untracked(() => {
        if (this.pristinePrice) {
          this.form.controls.type.setValue('MARKET');
          this.form.controls.price.setValue(quote.price!.toFixed(2));
          this.pristinePrice = false;
        }
      });
    });
  }

  /** Until the picked instrument's first price arrives. */
  private pristinePrice = false;

  private async prefill(symbol: string): Promise<void> {
    await this.catalog.resolve([symbol]);
    const instrument = this.catalog.get(symbol);
    if (instrument?.tradable) {
      this.choose(instrument);
    }
  }

  private async readPositions(accountId: number): Promise<void> {
    try {
      this.positions.set(await this.tradeApi.positions(accountId));
    } catch {
      // Without them a sale is not checked here; the API still checks it.
      this.positions.set(null);
    }
  }

  protected choose(instrument: InstrumentResponse): void {
    this.chosen.set(instrument);
    this.form.patchValue({ symbol: instrument.symbol, type: 'LIMIT', price: '', quantity: '', amount: '' });
    this.pristinePrice = true;
    const quote = this.live.quote(instrument.symbol);
    if (quote?.price !== null && quote?.price !== undefined) {
      // Already priced, by the market watch: no new answer will come to fill it in.
      this.form.patchValue({ type: 'MARKET', price: quote.price.toFixed(2) });
      this.pristinePrice = false;
    }
  }

  /** Back to the search, to pick another. */
  protected change(): void {
    this.chosen.set(null);
    this.form.controls.symbol.setValue('');
  }

  async submit(): Promise<void> {
    const accountId = this.accountId();
    this.form.markAllAsTouched();
    const units = this.units();
    const price = this.sentPrice();
    // What this mode needs, not the whole form: a market order ignores the limit box.
    const ready = this.form.controls.symbol.valid && units !== null && units >= 1 && price !== null;
    if (accountId === null || !ready || this.shortfall()) {
      return;
    }
    const order = {
      accountId,
      symbol: this.form.controls.symbol.value,
      side: this.form.controls.side.value,
      quantity: units,
      price,
    };
    // A market order's limit follows the live price, so a retry may carry a
    // newer one: the same order all the same, under the same key.
    const fingerprint = JSON.stringify(this.atMarket() ? { ...order, price: 'MARKET' } : order);
    const idempotencyKey = this.unconfirmed?.order === fingerprint ? this.unconfirmed.key : crypto.randomUUID();
    this.unconfirmed = { key: idempotencyKey, order: fingerprint };

    this.submitting.set(true);
    this.error.set(null);
    this.result.set(null);
    try {
      this.result.set(await this.tradeApi.placeOrder({ ...order, idempotencyKey }));
      this.unconfirmed = null;
      this.form.patchValue({ quantity: '', amount: '' });
      this.form.markAsUntouched();
      void this.current.refresh();
      void this.readPositions(accountId);
    } catch (failure) {
      if (!(failure instanceof HttpErrorResponse && failure.status === 0)) {
        this.unconfirmed = null;
      }
      this.error.set(failure);
    } finally {
      this.submitting.set(false);
    }
  }

  protected invalid(name: 'symbol' | 'quantity' | 'price' | 'amount'): boolean {
    const control = this.form.controls[name];
    if (!control.touched) {
      return false;
    }
    switch (name) {
      case 'quantity':
        return !this.byAmount() && (control.invalid || control.value.trim() === '');
      case 'price':
        return !this.atMarket() && (control.invalid || control.value.trim() === '');
      case 'amount':
        return this.byAmount() && (this.units() === null || this.units()! < 1);
      default:
        return control.invalid;
    }
  }
}
