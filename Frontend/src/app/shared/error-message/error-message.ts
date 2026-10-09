import { Component, computed, input } from '@angular/core';
import { KnownErrorCode, describeError } from '../../core/errors/error-messages';

/**
 * Renders a failed call as one sentence, announced to screen readers. Nothing
 * at all when there is no error, so a screen never shows an empty red box.
 */
@Component({
  selector: 'app-error-message',
  template: `
    @if (shown(); as shown) {
      <p class="alert error" role="alert" data-testid="error-message">{{ shown.message }}</p>
    }
  `,
})
export class ErrorMessage {
  /** The failure, as caught. null or undefined renders nothing. */
  readonly error = input<unknown>(null);
  /** A screen's own wording for particular codes. */
  readonly overrides = input<Partial<Record<KnownErrorCode, string>>>({});

  protected readonly shown = computed(() => {
    const error = this.error();
    return error == null ? null : describeError(error, this.overrides());
  });
}
