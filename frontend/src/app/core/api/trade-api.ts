import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { OrderResponse, OrdersService, PlaceOrderRequest } from '../../../generated/trade';

/**
 * The Trade REST API, as this application uses it. Wraps the generated
 * clients so components get Promises: the observables stop at HttpClient.
 */
@Injectable({ providedIn: 'root' })
export class TradeApi {
  private readonly orders = inject(OrdersService);

  /** POST /api/v1/orders. Resolves with what the API returned -- usually NEW. */
  placeOrder(order: PlaceOrderRequest): Promise<OrderResponse> {
    return firstValueFrom(this.orders.placeOrder(order));
  }
}
