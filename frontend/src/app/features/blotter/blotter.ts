import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { OrderHistoryEntry, OrderStatus } from '../../../generated/trade';
import { TradeApi } from '../../core/api/trade-api';
import { ErrorMessage } from '../../shared/error-message/error-message';
import { REREAD_POLICY } from '../../shared/reread/reread-policy';
import { StatusBadge } from '../../shared/status-badge/status-badge';

/**
 * An order at NEW is normal: the Trade REST API answered before the executor
 * resolved it, and neither contract pushes to the browser. So while anything
 * is at NEW the blotter re-reads order history, on the shared REREAD_POLICY --
 * never re-posts the order, because the same idempotency key answers ORD-409
 * and a new key places a second order.
 */
@Component({
  selector: 'app-blotter',
  imports: [DatePipe, DecimalPipe, RouterLink, StatusBadge, ErrorMessage],
  templateUrl: './blotter.html',
  styleUrl: './blotter.css',
})
export class Blotter {
  private readonly tradeApi = inject(TradeApi);
  private readonly policy = inject(REREAD_POLICY);

  /** The account whose orders to show: the session's own. */
  readonly accountId = input.required<number>();

  protected readonly orders = signal<readonly OrderHistoryEntry[] | null>(null);
  protected readonly error = signal<unknown>(null);
  protected readonly loading = signal(false);
  protected readonly rereads = signal(0);
  protected readonly gaveUp = signal(false);

  /** Newest first, rejections included: the rejection is the record that the desk tried. */
  protected readonly rows = computed(() =>
    [...(this.orders() ?? [])].sort((a, b) => Date.parse(b.createdOn) - Date.parse(a.createdOn)),
  );

  protected readonly working = computed(() => this.rows().filter((order) => order.status === OrderStatus.New).length);

  protected readonly intervalSeconds = this.policy.intervalMs / 1000;
  protected readonly limitSeconds = (this.policy.intervalMs * this.policy.maxRereads) / 1000;

  private cancelReread: (() => void) | null = null;

  constructor() {
    effect(() => {
      const accountId = this.accountId();
      untracked(() => void this.refresh(accountId));
    });
    inject(DestroyRef).onDestroy(() => this.stopRereading());
  }

  /** Re-reads now and starts a fresh burst. The button, and the first load. */
  async refresh(accountId: number = this.accountId()): Promise<void> {
    this.stopRereading();
    this.rereads.set(0);
    this.gaveUp.set(false);
    await this.read(accountId);
  }

  private async read(accountId: number): Promise<void> {
    this.loading.set(true);
    try {
      this.orders.set(await this.tradeApi.orderHistory(accountId));
      this.error.set(null);
    } catch (failure) {
      // A failed re-read leaves the last good table up, with the error above it.
      this.error.set(failure);
      return;
    } finally {
      this.loading.set(false);
    }
    this.scheduleReread(accountId);
  }

  private scheduleReread(accountId: number): void {
    if (this.working() === 0) {
      return; // Nothing working: stop.
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

  private stopRereading(): void {
    this.cancelReread?.();
    this.cancelReread = null;
  }
}
