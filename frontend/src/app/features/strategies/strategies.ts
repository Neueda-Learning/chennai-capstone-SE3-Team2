import { CurrencyPipe, DatePipe, formatCurrency } from '@angular/common';
import { Component, DestroyRef, InjectionToken, LOCALE_ID, effect, inject, signal, untracked } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { InstrumentResponse } from '../../../generated/extensions';
import { Strategy, StrategyRequest, StrategyRun } from '../../../generated/strategy';
import { StrategiesApi } from '../../core/api/strategies-api';
import { KnownErrorCode } from '../../core/errors/error-messages';
import { Session } from '../../core/session/session';
import { ErrorMessage } from '../../shared/error-message/error-message';
import { InstrumentSearch } from '../../shared/instrument-search/instrument-search';

/** How often the open page reads the strategies again, to show one firing. */
export const STRATEGIES_POLL_MS = new InjectionToken<number>('STRATEGIES_POLL_MS', { factory: () => 15_000 });

type TriggerKind = Strategy['trigger'];

/** The two triggers that fire at a price the customer sets; the others read the averages or the band. */
const LEVEL: ReadonlySet<TriggerKind> = new Set(['FALLS_THROUGH', 'RISES_THROUGH']);

const OUTCOMES: Readonly<Record<StrategyRun['outcome'], string>> = {
  PLACED: 'Order placed',
  FILLED: 'Filled',
  REJECTED: 'Rejected',
  REFUSED_LIMIT: 'Refused: past its limit',
  FAILED: 'Not placed',
  STOPPED: 'Stopped',
};

/**
 * The customer's strategies (Sprint 10): a rule set once, traded by the
 * platform. Created off; switched on, it waits for a quote through its level
 * and places one order through the same route and checks as the order
 * window. A buy never spends past its bound, and three failures stop it
 * (decision log 0012). The browser only sets the rule: firing is the
 * platform's, so it fires with the page closed.
 */
@Component({
  selector: 'app-strategies',
  imports: [CurrencyPipe, DatePipe, ErrorMessage, InstrumentSearch, ReactiveFormsModule, RouterLink],
  templateUrl: './strategies.html',
  styleUrl: './strategies.css',
})
export class Strategies {
  private readonly api = inject(StrategiesApi);
  private readonly accountId = inject(Session).accountId;
  private readonly locale = inject(LOCALE_ID);

  protected readonly strategies = signal<Strategy[] | null>(null);
  protected readonly error = signal<unknown>(null);
  protected readonly busy = signal<number | null>(null);
  protected readonly overrides: Partial<Record<KnownErrorCode, string>> = {
    'LIM-409': 'An account can have 10 strategies. Delete one to add another.',
  };

  protected readonly form = new FormGroup({
    side: new FormControl<'BUY' | 'SELL'>('BUY', { nonNullable: true }),
    quantity: new FormControl<number | null>(1),
    trigger: new FormControl<TriggerKind>('FALLS_THROUGH', { nonNullable: true }),
    triggerPrice: new FormControl<number | null>(null),
    maxSpend: new FormControl<number | null>(null),
    maxPosition: new FormControl<number | null>(null),
  });
  protected readonly instrument = signal<InstrumentResponse | null>(null);
  protected readonly problems = signal<string | null>(null);
  protected readonly creating = signal(false);
  protected readonly created = signal(false);
  protected readonly createError = signal<unknown>(null);

  protected readonly openRuns = signal<number | null>(null);
  protected readonly runs = signal<StrategyRun[] | null>(null);

  constructor() {
    effect(() => {
      const accountId = this.accountId();
      if (accountId !== null) {
        untracked(() => void this.load(accountId));
      }
    });
    const poll = setInterval(() => {
      const accountId = this.accountId();
      if (accountId !== null && this.busy() === null) {
        void this.load(accountId);
      }
    }, inject(STRATEGIES_POLL_MS));
    inject(DestroyRef).onDestroy(() => clearInterval(poll));
  }

  private async load(accountId: number): Promise<void> {
    try {
      this.strategies.set(await this.api.list(accountId));
      const open = this.openRuns();
      if (open !== null) {
        this.runs.set(await this.api.runs(accountId, open));
      }
    } catch (failure) {
      this.error.set(failure);
    }
  }

  /** Whether the form's trigger fires at a price, and so asks for one. */
  protected levelTrigger(): boolean {
    return LEVEL.has(this.form.controls.trigger.value);
  }

  protected isLevel(strategy: Strategy): boolean {
    return LEVEL.has(strategy.trigger);
  }

  /** The rule in words. A level trigger's price follows it on screen. */
  protected rule(strategy: Strategy): string {
    const buy = strategy.side === 'BUY';
    const when = {
      FALLS_THROUGH: 'the price falls to',
      RISES_THROUGH: 'the price rises to',
      MA_CROSSOVER: `the 20-day average crosses ${buy ? 'above' : 'below'} the 50-day`,
      BOLLINGER: `the price reaches the ${buy ? 'lower' : 'upper'} Bollinger band`,
    }[strategy.trigger];
    return `${buy ? 'Buy' : 'Sell'} ${strategy.quantity} when ${when}`;
  }

  /** What an indicator strategy waits on, at the last price the platform saw. */
  protected watching(strategy: Strategy): string {
    const figures = strategy.indicator;
    if (!figures) {
      return 'Waiting for the next price';
    }
    const price = `price ${this.money(figures.price)}`;
    if (strategy.trigger === 'MA_CROSSOVER') {
      return figures.shortAverage == null || figures.longAverage == null
        ? `Only ${figures.days} days of prices: the 50-day average needs 50; ${price}`
        : `20-day ${this.money(figures.shortAverage)}, 50-day ${this.money(figures.longAverage)}; ${price}`;
    }
    return figures.lowerBand == null || figures.upperBand == null
      ? `Only ${figures.days} days of prices: the band needs 20; ${price}`
      : `Band ${this.money(figures.lowerBand)} to ${this.money(figures.upperBand)}; ${price}`;
  }

  private money(value: number): string {
    return formatCurrency(value, this.locale, '₹', 'INR', '1.2-2');
  }

  /** What the toggle does: off goes on; armed goes off; fired or stopped is armed again. */
  protected action(strategy: Strategy): { label: string; enabled: boolean } {
    if (!strategy.enabled) {
      return { label: 'Switch on', enabled: true };
    }
    return strategy.status === 'ARMED' ? { label: 'Switch off', enabled: false } : { label: 'Arm again', enabled: true };
  }

  protected outcome(run: StrategyRun): string {
    return OUTCOMES[run.outcome];
  }

  protected pick(instrument: InstrumentResponse): void {
    this.instrument.set(instrument);
    this.created.set(false);
  }

  async create(): Promise<void> {
    const accountId = this.accountId();
    const request = this.request();
    if (accountId === null || request === null) {
      return;
    }
    this.creating.set(true);
    this.createError.set(null);
    try {
      await this.api.create(accountId, request);
      this.created.set(true);
      this.form.controls.triggerPrice.reset(null);
      await this.load(accountId);
    } catch (failure) {
      this.createError.set(failure);
    } finally {
      this.creating.set(false);
    }
  }

  /** The request the form describes, or null with what to fix said. */
  private request(): StrategyRequest | null {
    const { side, quantity, trigger, triggerPrice, maxSpend, maxPosition } = this.form.getRawValue();
    const instrument = this.instrument();
    const problems: string[] = [];
    if (instrument === null || instrument.type === 'MF') {
      problems.push('Pick a stock.');
    }
    if (quantity === null || !Number.isInteger(quantity) || quantity < 1) {
      problems.push('Enter a whole quantity of 1 or more.');
    }
    const level = LEVEL.has(trigger);
    if (level && (triggerPrice === null || !(triggerPrice > 0) || Math.round(triggerPrice * 100) !== triggerPrice * 100)) {
      problems.push('Enter a price above zero, to the paisa.');
    }
    if (maxSpend === null || !(maxSpend > 0)) {
      problems.push('Enter the most a firing may spend.');
    }
    if (maxPosition === null || !Number.isInteger(maxPosition) || maxPosition < 1) {
      problems.push('Enter the most you would hold, 1 or more.');
    }
    this.problems.set(problems.length === 0 ? null : problems.join(' '));
    this.created.set(false);
    if (problems.length > 0) {
      return null;
    }
    return {
      symbol: instrument!.symbol, side, quantity: quantity!, trigger,
      // An indicator trigger takes no price: it fires on the averages or the band.
      ...(level ? { triggerPrice: triggerPrice! } : {}),
      maxSpend: maxSpend!, maxPosition: maxPosition!,
    };
  }

  async toggle(strategy: Strategy): Promise<void> {
    const enabled = this.action(strategy).enabled;
    await this.act(strategy, (accountId) => this.api.setEnabled(accountId, strategy.id, enabled));
  }

  async remove(strategy: Strategy): Promise<void> {
    if (this.openRuns() === strategy.id) {
      this.openRuns.set(null);
      this.runs.set(null);
    }
    await this.act(strategy, (accountId) => this.api.remove(accountId, strategy.id));
  }

  async showRuns(strategy: Strategy): Promise<void> {
    if (this.openRuns() === strategy.id) {
      this.openRuns.set(null);
      this.runs.set(null);
      return;
    }
    const accountId = this.accountId();
    if (accountId === null) {
      return;
    }
    this.openRuns.set(strategy.id);
    this.runs.set(null);
    try {
      this.runs.set(await this.api.runs(accountId, strategy.id));
    } catch (failure) {
      this.error.set(failure);
    }
  }

  private async act(strategy: Strategy, action: (accountId: number) => Promise<unknown>): Promise<void> {
    const accountId = this.accountId();
    if (accountId === null) {
      return;
    }
    this.busy.set(strategy.id);
    this.error.set(null);
    try {
      await action(accountId);
      await this.load(accountId);
    } catch (failure) {
      this.error.set(failure);
    } finally {
      this.busy.set(null);
    }
  }
}
