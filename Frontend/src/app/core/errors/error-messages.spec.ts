import { HttpErrorResponse } from '@angular/common/http';
import { ErrorResponse as AuthErrorResponse } from '../../../generated/auth';
import { ErrorResponse as TradeErrorResponse } from '../../../generated/trade';
import {
  ERROR_MESSAGES,
  OFF_CATALOGUE_CODES,
  UNKNOWN_ERROR_MESSAGE,
  UNREACHABLE_MESSAGE,
  describeError,
} from './error-messages';

function platformError(errorCode: string, message = 'developer-facing text', status = 400): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: { errorCode, message } });
}

/** Every code both contracts declare, as the generator read them out of Contracts/API Schemas/. */
const CONTRACT_CODES = [
  ...new Set<string>([
    ...Object.values(TradeErrorResponse.ErrorCodeEnum),
    ...Object.values(AuthErrorResponse.ErrorCodeEnum),
  ]),
];

describe('describeError', () => {
  it('maps every code in both catalogues to a message', () => {
    for (const code of CONTRACT_CODES) {
      const shown = describeError(platformError(code));

      expect(shown.code, code).toBe(code);
      expect(shown.message, code).toBe(ERROR_MESSAGES[code as keyof typeof ERROR_MESSAGES]);
      expect(shown.message, code).not.toBe(UNKNOWN_ERROR_MESSAGE);
      expect(shown.message.length, code).toBeGreaterThan(20);
    }
  });

  it('covers exactly the eight codes the two catalogues declare', () => {
    expect(CONTRACT_CODES.sort()).toEqual(
      ['ACC-403', 'ACC-404', 'AUTH-401', 'AUTH-409', 'INS-404', 'ORD-400', 'ORD-409', 'VAL-422'],
    );
  });

  it('also renders the codes our services send outside the catalogues', () => {
    for (const code of OFF_CATALOGUE_CODES) {
      expect(describeError(platformError(code)).message).toBe(ERROR_MESSAGES[code]);
    }
  });

  it('falls back to a readable sentence for an unrecognised code', () => {
    const shown = describeError(platformError('XYZ-999'));

    expect(shown).toEqual({ code: 'XYZ-999', message: UNKNOWN_ERROR_MESSAGE });
  });

  it('renders a readable sentence for a request that never reached a service', () => {
    const shown = describeError(new HttpErrorResponse({ status: 0, statusText: 'Unknown Error' }));

    expect(shown).toEqual({ code: null, message: UNREACHABLE_MESSAGE });
  });

  it('branches on the code, never on the message, and never shows the message', () => {
    const a = describeError(platformError('ORD-400', 'Insufficient funds'));
    const b = describeError(platformError('ORD-400', 'Not enough money, reworded by a developer'));

    expect(a.message).toBe(b.message);
    expect(a.message).not.toContain('Insufficient funds');
  });

  it('falls back when the body is not the platform envelope', () => {
    expect(describeError(new HttpErrorResponse({ status: 502, error: '<html>Bad Gateway</html>' })).message).toBe(
      UNKNOWN_ERROR_MESSAGE,
    );
    expect(describeError(new Error('not an HTTP failure')).message).toBe(UNKNOWN_ERROR_MESSAGE);
  });

  it("uses a screen's own wording for a code when it gives one", () => {
    const shown = describeError(platformError('AUTH-401', 'Unauthorised', 401), {
      'AUTH-401': 'That username and password do not match.',
    });

    expect(shown.message).toBe('That username and password do not match.');
  });
});

describe('describeError, for our extension routes', () => {
  it('maps every code the extension contract declares to a message', async () => {
    const { ErrorResponse } = await import('../../../generated/extensions');
    for (const code of Object.values(ErrorResponse.ErrorCodeEnum)) {
      const shown = describeError(platformError(code));

      expect(shown.message, code).toBe(ERROR_MESSAGES[code]);
      expect(shown.message, code).not.toBe(UNKNOWN_ERROR_MESSAGE);
    }
  });
});
