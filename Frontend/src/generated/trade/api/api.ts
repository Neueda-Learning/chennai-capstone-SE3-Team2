export * from './accounts.service';
import { AccountsService } from './accounts.service';
export * from './orders.service';
import { OrdersService } from './orders.service';
export const APIS = [AccountsService, OrdersService];
