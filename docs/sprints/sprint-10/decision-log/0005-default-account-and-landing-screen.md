# 0005 The default account is stored with a default landing screen

| Field | Value |
|---|---|
| Status | proposed |
| Date | 2026-10-06 |
| Decided by | Shiva Sai Adithiyan, drafting the day-one design for the team; for the team and the instructor to confirm on day one |

## Context

The criterion: preferences "persists a default account and an alert channel per customer, and
applies them at the customer's next login". On this platform a login has exactly one account,
fixed in its token's `accountId` claim, so a default account has nothing to choose between.

## Options considered

| Option | For | Against |
|---|---|---|
| Store the default account (only the caller's own accepted) and a landing screen sign-in opens on | The criterion is met as written, and the stored preference visibly changes the next sign-in | The landing screen is our addition, to be confirmed with the instructor |
| Store the default account only | Exactly the criterion | Applying it changes nothing a customer or an assessor can see |
| Let one login own several accounts | A default account that means something | Changes auth, onboarding and the token claim, mid-platform; out of proportion to a preference |

## Decision

The default account and a landing screen. It meets the criterion and gives "applied at next
sign-in" an effect the demonstration can show.

## Consequences

Saving a default account other than the token's is `ACC-403`. The Angular sign-in reads the
preference after the token arrives and navigates to the landing screen unless the customer was
going somewhere already. Raised with the instructor on day one; proposed until they confirm.
