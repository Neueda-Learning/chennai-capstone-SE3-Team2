import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * The application's checks, the same rules the Trade REST API applies, so the
 * obvious mistakes are caught before sending. The server decides; KYC then
 * decides eligibility.
 */

const PAN = /^[A-Z]{5}[0-9]{4}[A-Z]$/;
const IFSC = /^[A-Z]{4}0[A-Z0-9]{6}$/;
const MOBILE = /^[6-9]\d{9}$/;
const BANK_ACCOUNT = /^[0-9]{9,18}$/;

function text(control: AbstractControl): string {
  return String(control.value ?? '').trim();
}

/** Builds a validator from a rule; an empty field is left to `required`. */
function rule(name: string, test: (value: string) => boolean): ValidatorFn {
  return (control) => {
    const value = text(control);
    return value === '' || test(value) ? null : { [name]: true };
  };
}

/** A PAN: five letters, four digits, a letter. Checked upper-cased, as it is sent. */
export const panShape = rule('pan', (v) => PAN.test(v.toUpperCase()));

/** An IFSC: four letters, a zero, six letters or digits. */
export const ifscShape = rule('ifsc', (v) => IFSC.test(v.toUpperCase()));

/** Ten digits starting 6 to 9; the form adds the +91. */
export const mobileShape = rule('mobile', (v) => MOBILE.test(v.replace(/[\s-]/g, '')));

/** 9 to 18 digits. */
export const bankAccountShape = rule('bankAccount', (v) => BANK_ACCOUNT.test(v.replace(/\s/g, '')));

/** A real date, not in the future, at least 18 years ago. */
export function adultOn(today: () => Date = () => new Date()): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = text(control);
    if (value === '') {
      return null;
    }
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
    if (!match) {
      return { dob: true };
    }
    const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
    const born = new Date(year, month - 1, day);
    if (born.getFullYear() !== year || born.getMonth() !== month - 1 || born.getDate() !== day) {
      return { dob: true };
    }
    const now = today();
    const eighteenth = new Date(year + 18, month - 1, day);
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    return eighteenth <= startOfToday ? null : { underage: true };
  };
}
