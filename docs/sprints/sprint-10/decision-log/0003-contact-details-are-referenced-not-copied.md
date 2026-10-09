# 0003 The customer's contact details are referenced, not copied into preferences

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Shiva Sai Adithiyan, drafting the day-one design for the team; for the team and the instructor to confirm on day one |

## Context

An alert channel needs a destination: an email address. The address already exists, on
`client_profile`, captured at onboarding and used by the activation mailer. The brief asks which
module owns the customer's contact details, since a second copy doubles the places a leak can
happen and creates a reconciliation problem the day one of them changes.

## Options considered

| Option | For | Against |
|---|---|---|
| Reference the profile's address, read at the moment of resolving | One copy of the personal data; a change of address on the profile is used at once; nothing new to encrypt | Preferences depends on the profile's data through the layer that owns it; a customer cannot send alerts to an address other than their registered one |
| Store a contact address per channel in preferences | Customers choose where alerts go; preferences is self-contained | A second copy of personal data; and a customer-supplied destination the platform then sends to, the SSRF-shaped risk the brief names |

## Decision

Reference. The deciding point is that a customer-supplied destination is a risk we would then have
to defend, while the registered address is one the customer already proved they control at
activation.

## Consequences

`ResolvedChannel.destination` is read from the profile on every resolve and never stored by
preferences; notifications stores it only masked on the notification row. A customer who wants
alerts elsewhere changes their profile address, which is outside this sprint.
