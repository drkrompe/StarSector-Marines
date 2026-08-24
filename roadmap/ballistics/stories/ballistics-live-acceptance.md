# Ballistics live acceptance

Status: PARKED — shipped behavior needs an in-game feel pass.

Written: 2026-08-23

Read `ballistics-nouns.md` before running this acceptance.

## Acceptance

- [ ] Fire from immediately behind low cover, in midfield, and at target-side
  cover. Confirm close firing positions are usable while downrange cover still
  provides a legible tactical benefit.
- [ ] Exercise dense friendly firing lanes across experience levels. Confirm
  that unsafe primary rounds may be withheld without a visible/noise/telemetry
  side effect, while the firing cadence remains honest and friendly catches do
  not dominate ordinary formation fire.
- [ ] Exercise hostile through-fire in dense enemies and wide bursts. Confirm
  a missed path can visibly transfer into a physical hostile interposer without
  making friendly or civilian behavior misleading.
- [ ] Observe slow and fast weapons against moving targets. Confirm lead,
  high/low/wide misses, and each projectile family remain readable at ordinary
  and accelerated battle speeds.

## Out of scope

- Rebalancing weapon damage, armor, penetration, or weapon catalog values.
- Adding cover destruction, lead error, point defense, or aerial collision.
- Migrating fighter fire before its Air-owned policy is defined.

## Exit

Fold any durable tuning decision into `ballistics-nouns.md`, add this story to
`shipped.md`, and delete it when the live pass is complete.
