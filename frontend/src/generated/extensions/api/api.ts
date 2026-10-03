export * from './instruments.service';
import { InstrumentsService } from './instruments.service';
export * from './onboarding.service';
import { OnboardingService } from './onboarding.service';
export * from './payments.service';
import { PaymentsService } from './payments.service';
export const APIS = [InstrumentsService, OnboardingService, PaymentsService];
