import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { OrderResponse, OrderSide, OrderStatus } from '../../../generated/trade';
import { TradeApi } from '../../core/api/trade-api';
import { Session } from '../../core/session/session';
import { ErrorMessage } from '../../shared/error-message/error-message';
import { priceAboveZeroTwoDecimals, symbolShape, wholeNumberAboveZero } from './order-validators';

/** What each status the API can return means to the person who just placed the order. */
const OUTCOME: Readonly<Record<OrderStatus, string>> = {
  NEW: 'Accepted and still working: it has not been executed yet. Your orders on the dashboard update as it is.',
  FILLED: 'Filled.',
  REJECTED: 'Rejected when it was executed. Your orders on the dashboard show it.',
  CANCELLED: 'Cancelled.',
};

@Component({
  selector: 'app-order-ticket',
  imports: [ReactiveFormsModule, ErrorMessage, RouterLink],
  templateUrl: './order-ticket.html',
  styleUrl: './order-ticket.css',
})
export class OrderTicket {
  private readonly tradeApi = inject(TradeApi);

  /**
   * The account comes from the token and is shown read-only. An account field
   * the user could edit would be an authorisation decision moved into the
   * browser -- and the API refuses any other account anyway (ACC-403).
   */
  protected readonly accountId = inject(Session).accountId;

  protected readonly sides = [OrderSide.Buy, OrderSide.Sell];
  protected readonly outcome = OUTCOME;

  protected readonly form = new FormGroup({
    symbol: new FormControl('', { nonNullable: true, validators: [Validators.required, symbolShape] }),
    side: new FormControl<OrderSide>(OrderSide.Buy, { nonNullable: true }),
    quantity: new FormControl('', { nonNullable: true, validators: [Validators.required, wholeNumberAboveZero] }),
    price: new FormControl('', { nonNullable: true, validators: [Validators.required, priceAboveZeroTwoDecimals] }),
  });

  protected readonly submitting = signal(false);
  protected readonly result = signal<OrderResponse | null>(null);
  protected readonly error = signal<unknown>(null);

  /**
   * The idempotency key of an order whose fate is unknown: sent, but no
   * response came back. Retrying that same order reuses it, so if the first
   * attempt did land the API answers ORD-409 instead of placing it twice. Any
   * other outcome, or a different order, starts a new key.
   */
  private unconfirmed: { readonly key: string; readonly order: string } | null = null;

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
