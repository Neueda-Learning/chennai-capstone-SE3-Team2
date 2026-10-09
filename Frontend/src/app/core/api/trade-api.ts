import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  AccountResponse,
  AccountsService,
  OrderHistoryEntry,
  OrderResponse,
  OrdersService,
  PlaceOrderRequest,
  PositionResponse,
} from '../../../generated/trade';

/**
 * The Trade REST API, as this application uses it. Wraps the generated
 * clients so components get Promises: the observables stop at HttpClient.
 */
@Injectable({ providedIn: 'root' })
export class TradeApi {
  private readonly orders = inject(OrdersService);
  private readonly accounts = inject(AccountsService);

  /** POST /api/v1/orders. Resolves with what the API returned -- usually NEW. */
  placeOrder(order: PlaceOrderRequest): Promise<OrderResponse> {
    return firstValueFrom(this.orders.placeOrder(order));
  }

  /**
   * DELETE /api/v1/orders/{id}: only an order still NEW; any other is ORD-409.
   * The route takes the bare UUID, without the `ORD-` prefix the order is
   * displayed with; sent with it, the API answers VAL-422.
   */
  cancelOrder(orderId: string): Promise<OrderResponse> {
    return firstValueFrom(this.orders.cancelOrder(orderId.replace(/^ORD-/, '')));
  }

  /** GET /api/v1/accounts/{id}/positions: what the account holds, by symbol. A position sold to zero is gone. */
  positions(accountId: number): Promise<PositionResponse[]> {
    return firstValueFrom(this.accounts.getPositions(accountId));
  }

  /** GET /api/v1/accounts/{id}/orders: every order, rejections included. */
  orderHistory(accountId: number): Promise<OrderHistoryEntry[]> {
    return firstValueFrom(this.accounts.getOrders(accountId));
  }

  /** GET /api/v1/accounts/{id}/balance: available cash -- the balance less what open orders and withdrawals hold. */
  async availableCash(accountId: number): Promise<number> {
    return (await firstValueFrom(this.accounts.getBalance(accountId))).cashBalance;
  }

  /** GET /api/v1/accounts/{id}. */
  account(accountId: number): Promise<AccountResponse> {
    return firstValueFrom(this.accounts.getAccount(accountId));
  }
}
