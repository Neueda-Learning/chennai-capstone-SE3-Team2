import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { OrderHistoryEntry, OrderStatus } from '../../../generated/trade';
import { InstrumentCatalog } from '../../core/api/instrument-catalog';
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
 *
 * An order still NEW can be cancelled from its row. Whether it was is the
 * server's call -- the executor may fill it first -- so the blotter re-reads
 * afterwards either way and shows what actually happened.
 */
@Component({
  selector: 'app-blotter',
  imports: [DatePipe, DecimalPipe, NgTemplateOutlet, RouterLink, StatusBadge, ErrorMessage],
  templateUrl: './blotter.html',
  styleUrl: './blotter.css',
})
export class Blotter {
  private readonly tradeApi = inject(TradeApi);
  private readonly policy = inject(REREAD_POLICY);
  /** Names each instrument as the customer knows it, and says which dashboard it belongs on. */
  protected readonly catalog = inject(InstrumentCatalog);

  /** The account whose orders to show: the session's own. */
  readonly accountId = input.required<number>();
  /** Stocks and ETFs, mutual funds, or both. */
  readonly segment = input<'stocks' | 'mutual-funds' | 'all'>('all');
  /**
   * list: one table with status filters, as on the dashboard. split: Kite's
   * Orders page, open orders above executed ones.
   */
  readonly layout = input<'list' | 'split'>('list');

  /**
   * An order left NEW -- filled, rejected or cancelled -- so the cash and the
   * holdings it touches have changed. The dashboard re-reads them.
   */
  readonly settled = output<void>();

  protected readonly orders = signal<readonly OrderHistoryEntry[] | null>(null);
  protected readonly error = signal<unknown>(null);
  protected readonly loading = signal(false);
  protected readonly rereads = signal(0);
  protected readonly gaveUp = signal(false);
  protected readonly statusFilter = signal<'ALL' | OrderStatus>('ALL');
  /** The order a cancel is in flight for. */
  protected readonly cancelling = signal<string | null>(null);
  protected readonly cancelError = signal<unknown>(null);
  /** On this screen ORD-409 can only mean the order had already finished. */
  protected readonly cancelMessages = {
    'ORD-409': 'That order had already finished, so there was nothing to cancel. The list below shows what happened to it.',
  };

  /** Newest first, rejections included: the rejection is the record that the desk tried. */
  protected readonly filterOptions = ['ALL', OrderStatus.New, OrderStatus.Filled, OrderStatus.Rejected, OrderStatus.Cancelled] as const;

  /** Newest first, in the segment shown. */
  private readonly inSegment = computed(() =>
    [...(this.orders() ?? [])]
      .filter((order) => {
        const segment = this.segment();
        return segment === 'all' || this.catalog.segmentOf(order.symbol) === segment;
      })
      .sort((a, b) => Date.parse(b.createdOn) - Date.parse(a.createdOn)),
  );

  protected readonly rows = computed(() => {
    const status = this.statusFilter();
    return this.inSegment().filter((order) => status === 'ALL' || order.status === status);
  });
  /** Still working: what Cancel applies to. */
  protected readonly openRows = computed(() => this.inSegment().filter((order) => order.status === OrderStatus.New));
  /** Filled, rejected or cancelled: done with. */
  protected readonly executedRows = computed(() => this.inSegment().filter((order) => order.status !== OrderStatus.New));

  protected readonly working = computed(() => this.inSegment().filter((order) => order.status === OrderStatus.New).length);

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

  /** Asks the server to cancel an order still NEW, then re-reads whatever the answer. */
  async cancel(orderId: string): Promise<void> {
    this.cancelling.set(orderId);
    this.cancelError.set(null);
    try {
      await this.tradeApi.cancelOrder(orderId);
    } catch (failure) {
      this.cancelError.set(failure);
    } finally {
      this.cancelling.set(null);
    }
    await this.refresh();
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
    const wasWorking = new Set((this.orders() ?? []).filter((o) => o.status === OrderStatus.New).map((o) => o.orderId));
    try {
      const orders = await this.tradeApi.orderHistory(accountId);
      this.orders.set(orders);
      this.error.set(null);
      // Each order's instrument name, and which dashboard it belongs on.
      void this.catalog.resolve(orders.map((order) => order.symbol));
      if (orders.some((o) => wasWorking.has(o.orderId) && o.status !== OrderStatus.New)) {
        this.settled.emit();
      }
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

  /** Which order a Cancel button is for, said without the order id. */
  protected cancelLabel(order: OrderHistoryEntry): string {
    return `Cancel the ${order.side === 'BUY' ? 'buy' : 'sell'} of ${order.quantity} ${this.catalog.label(order.symbol)}`;
  }

  /** The table template's rows, typed: a template context is not. */
  protected asOrders(rows: unknown): readonly OrderHistoryEntry[] {
    return rows as readonly OrderHistoryEntry[];
  }

  protected setStatusFilter(filter: 'ALL' | OrderStatus): void {
    this.statusFilter.set(filter);
  }
}
