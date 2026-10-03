import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { BankAccountResponse, TransferResponse } from '../../../generated/extensions';
import { PaymentsApi } from '../../core/api/payments-api';
import { TradeApi } from '../../core/api/trade-api';
import { Session } from '../../core/session/session';
import { REREAD_POLICY } from '../../shared/reread/reread-policy';
import { ErrorMessage } from '../../shared/error-message/error-message';
import { priceAboveZeroTwoDecimals } from '../order-ticket/order-validators';

type Direction = 'DEPOSIT' | 'WITHDRAWAL';

/**
 * Cash in and out, only to and from the registered bank account. A request
 * is PENDING until the payment gateway decides it, a few seconds later; while
 * anything is PENDING the page re-reads, on the shared bounded policy, and
 * never re-sends.
 */
@Component({
  selector: 'app-cash',
  imports: [ReactiveFormsModule, CurrencyPipe, DatePipe, DecimalPipe, ErrorMessage],
  templateUrl: './cash.html',
  styleUrl: './cash.css',
})
export class Cash {
  private readonly payments = inject(PaymentsApi);
  private readonly tradeApi = inject(TradeApi);
  private readonly policy = inject(REREAD_POLICY);
  protected readonly accountId = inject(Session).accountId;

  protected readonly amount = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, priceAboveZeroTwoDecimals],
  });

  protected readonly available = signal<number | null>(null);
  protected readonly bank = signal<BankAccountResponse | null>(null);
  protected readonly transfers = signal<readonly TransferResponse[] | null>(null);
  protected readonly error = signal<unknown>(null);
  protected readonly submitting = signal<Direction | null>(null);
  protected readonly overAvailable = signal(false);
  protected readonly rereads = signal(0);
  protected readonly gaveUp = signal(false);

  protected readonly pending = computed(() => (this.transfers() ?? []).filter((t) => t.status === 'PENDING').length);

  /** The key of a transfer whose fate is unknown; a retry of the same one reuses it. */
  private unconfirmed: { readonly key: string; readonly request: string } | null = null;
  private cancelReread: (() => void) | null = null;

  constructor() {
    effect(() => {
      const accountId = this.accountId();
      if (accountId !== null) {
        untracked(() => void this.load(accountId));
      }
    });
    inject(DestroyRef).onDestroy(() => this.cancelReread?.());
  }

  async submit(direction: Direction): Promise<void> {
    const accountId = this.accountId();
    this.overAvailable.set(false);
    if (this.amount.invalid || accountId === null) {
      this.amount.markAsTouched();
      return;
    }
    const amount = Number(this.amount.value);
    const available = this.available();
    if (direction === 'WITHDRAWAL' && available !== null && amount > available) {
      // The server refuses it too (PAY-400); this only saves the round trip.
      this.overAvailable.set(true);
      return;
    }

    const request = `${direction}:${amount}`;
    const key = this.unconfirmed?.request === request ? this.unconfirmed.key : crypto.randomUUID();
    this.unconfirmed = { key, request };

    this.submitting.set(direction);
    this.error.set(null);
    try {
      await (direction === 'DEPOSIT'
        ? this.payments.deposit(accountId, amount, key)
        : this.payments.withdraw(accountId, amount, key));
      this.unconfirmed = null;
      this.amount.reset('');
      await this.refresh();
    } catch (failure) {
      if (!(failure instanceof HttpErrorResponse && failure.status === 0)) {
        this.unconfirmed = null;
      }
      this.error.set(failure);
    } finally {
      this.submitting.set(null);
    }
  }

  /** Re-reads now and starts a fresh burst: the Refresh button, and after each request. */
  async refresh(): Promise<void> {
    const accountId = this.accountId();
    if (accountId === null) {
      return;
    }
    this.cancelReread?.();
    this.rereads.set(0);
    this.gaveUp.set(false);
    await this.read(accountId);
  }

  private async load(accountId: number): Promise<void> {
    try {
      this.bank.set(await this.payments.bankAccount(accountId));
    } catch (failure) {
      this.error.set(failure);
    }
    await this.read(accountId);
  }

  private async read(accountId: number): Promise<void> {
    try {
      const [transfers, available] = await Promise.all([
        this.payments.transfers(accountId),
        this.tradeApi.availableCash(accountId),
      ]);
      this.transfers.set(transfers);
      this.available.set(available);
    } catch (failure) {
      this.error.set(failure);
      return;
    }
    if (this.pending() === 0) {
      return;
    }
    if (this.rereads() >= this.policy.maxRereads) {
      this.gaveUp.set(true);
      return;
    }
    this.cancelReread = this.policy.schedule(() => {
      this.cancelReread = null;
      this.rereads.update((n) => n + 1);
      void this.read(accountId);
    }, this.policy.intervalMs);
  }
}
