import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { OnboardingApi } from '../../core/api/onboarding-api';
import { ErrorMessage } from '../../shared/error-message/error-message';
import { adultOn, bankAccountShape, ifscShape, mobileShape, panShape } from './application-validators';

type Field = 'name' | 'dob' | 'email' | 'mobile' | 'pan' | 'address' | 'bankAccountNumber' | 'ifsc';

/**
 * Opening an account. Public, like sign-in: the applicant has no login yet.
 *
 * The confirmation is the same whatever happened to the application -- the
 * server answers a PAN or email already registered exactly as it answers a new
 * one, so this page cannot be used to find out who is a customer either.
 */
@Component({
  selector: 'app-apply',
  imports: [ReactiveFormsModule, RouterLink, ErrorMessage],
  templateUrl: './apply.html',
  styleUrl: './apply.css',
})
export class Apply {
  private readonly onboarding = inject(OnboardingApi);

  protected readonly today = new Date().toISOString().slice(0, 10);

  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2), Validators.maxLength(120)] }),
    dob: new FormControl('', { nonNullable: true, validators: [Validators.required, adultOn()] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email, Validators.maxLength(200)] }),
    mobile: new FormControl('', { nonNullable: true, validators: [Validators.required, mobileShape] }),
    pan: new FormControl('', { nonNullable: true, validators: [Validators.required, panShape] }),
    address: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(500)] }),
    bankAccountNumber: new FormControl('', { nonNullable: true, validators: [Validators.required, bankAccountShape] }),
    ifsc: new FormControl('', { nonNullable: true, validators: [Validators.required, ifscShape] }),
  });

  protected readonly submitting = signal(false);
  protected readonly received = signal(false);
  protected readonly error = signal<unknown>(null);

  async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.submitting.set(true);
    this.error.set(null);
    try {
      await this.onboarding.apply({
        name: v.name.trim(),
        dob: v.dob,
        email: v.email.trim(),
        phoneNumber: '+91' + v.mobile.replace(/[\s-]/g, ''),
        pan: v.pan.trim().toUpperCase(),
        address: v.address.trim() || undefined,
        bankAccountNumber: v.bankAccountNumber.replace(/\s/g, ''),
        ifsc: v.ifsc.trim().toUpperCase(),
      });
      this.received.set(true);
    } catch (failure) {
      this.error.set(failure);
    } finally {
      this.submitting.set(false);
    }
  }

  protected invalid(field: Field): boolean {
    const control = this.form.controls[field];
    return control.touched && control.invalid;
  }

  protected has(field: Field, error: string): boolean {
    return this.form.controls[field].hasError(error);
  }
}
