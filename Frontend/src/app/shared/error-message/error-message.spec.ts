import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ERROR_MESSAGES } from '../../core/errors/error-messages';
import { ErrorMessage } from './error-message';

describe('ErrorMessage', () => {
  async function render(error: unknown) {
    const fixture = TestBed.createComponent(ErrorMessage);
    fixture.componentRef.setInput('error', error);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('renders the sentence for the code, as an alert', async () => {
    const page = await render(new HttpErrorResponse({ status: 400, error: { errorCode: 'ORD-400', message: 'x' } }));
    const alert = page.querySelector('[role="alert"]');

    expect(alert?.textContent).toContain(ERROR_MESSAGES['ORD-400']);
    // The code is the platform's, not the customer's: never on screen.
    expect(alert?.textContent).not.toContain('ORD-400');
  });

  it('renders nothing when there is no error', async () => {
    const page = await render(null);

    expect(page.querySelector('[role="alert"]')).toBeNull();
  });
});
