# Field-presence policy

Status: IN PROGRESS — mission data and battle arrival must agree on the same
whole-squad concurrency limit.

Written: 2026-09-01

Read `mission-tier-nouns.md` before accepting this story.

## Goal

Allow infiltration and target-capture operations to commit a reserve force
without putting every committed squad on the battlefield simultaneously.

## Acceptance

- A mission owns an explicit field-presence policy independently of tier, risk,
  recommendation, and total committed force.
- Unrestricted missions preserve today's arrival behavior.
- The first covert defaults are one active squad for Sabotage and two active
  squads for Raid's target-seizure operation; an authored mission can override
  those defaults.
- The cap counts persistent campaign squads. Several transports carrying one
  squad consume one slot, while a transport carrying a new squad waits off-map.
- Inbound squads reserve their slots before a shuttle starts its approach.
- When an admitted squad has neither a living member nor an inbound member, the
  next committed reserve squad may enter.
- The briefing distinguishes total recommended commitment from the concurrent
  field limit.
- Frozen launch fixtures persist the policy so headless replay exercises the
  same arrival law as campaign launch.
- The legacy synthetic DEBUG mission board is disabled while its mission ideas
  are reconsidered individually. Debug factories and canonical fixtures remain
  callable by tests and evidence tools.

## Out of scope

- Detection, alert, payout, or reputation consequences for calling reserves.
- A player-directed extraction action that deliberately frees a slot.
- Redesigning the objectives, maps, or commanders of the disabled debug entries.
