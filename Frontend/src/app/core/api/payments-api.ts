import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { BankAccountResponse, PaymentsService, TransferResponse } from '../../../generated/extensions';

/**
 * Cash in and out, through the generated extensions client. Promises, not
 * Observables: the observables stop at HttpClient.
 */
@Injectable({ providedIn: 'root' })
export class PaymentsApi {
  private readonly payments = inject(PaymentsService);

  /** The registered bank account, masked to its last four digits. */
  bankAccount(accountId: number): Promise<BankAccountResponse> {
    return firstValueFrom(this.payments.getBankAccount(accountId));
  }

  /** Every deposit and withdrawal, newest first. */
  transfers(accountId: number): Promise<TransferResponse[]> {
    return firstValueFrom(this.payments.getTransfers(accountId));
  }

  /** Recorded PENDING; the gateway decides a few seconds later. */
  deposit(accountId: number, amount: number, idempotencyKey: string): Promise<TransferResponse> {
    return firstValueFrom(this.payments.deposit(accountId, { amount, idempotencyKey }));
  }

  /** Held at once, recorded PENDING; the gateway decides a few seconds later. */
  withdraw(accountId: number, amount: number, idempotencyKey: string): Promise<TransferResponse> {
    return firstValueFrom(this.payments.withdraw(accountId, { amount, idempotencyKey }));
  }
}
