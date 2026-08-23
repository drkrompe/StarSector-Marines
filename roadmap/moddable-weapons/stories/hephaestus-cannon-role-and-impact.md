# Hephaestus cannon role and impact

> Make the loud shot behave and read like the anti-armor shell it promises.

Status: IN PROGRESS

Written: 2026-08-23

Read `moddable-weapons-nouns.md` and `combat-durability-nouns.md` before
implementing this story.

## Finding

The Hephaestus emplacement currently fires quickly, carries no area payload,
uses a kinetic impact profile, and has only moderate penetration. Its strong
fire sound therefore promises a heavy cannon while the resolved hit behaves
like another autocannon.

The existing Heavy Mortar is not the missing weapon: it already owns the
long-range area-and-wall role and already maps to the smoke/fire-bearing
`CANNON_HE` presentation recipe. The Hephaestus should instead become the
medium-range, slow-cycle anti-armor cannon.

The live static-turret behavior also routes single-shot kinds through generic
infantry fire. That compatibility path discards turret-owned area payloads and
penetration, so the cannon must use the turret fire procedure even though its
burst count remains one.

## Goal

Give the Hephaestus one physically resolved shell with two mutually exclusive
damage results: a direct contact is a high-penetration anti-armor hit, while a
nearby actor receives lower-penetration blast damage. The direct victim is not
damaged a second time by the area payload.

## Scope

- Slow the Hephaestus firing cadence and tune its contact damage and
  penetration for armored actors.
- Add a modest lower-penetration area payload and structural blast damage.
- Preserve the physical resolver: only the actor actually contacted by the
  shell receives the contact payload; wall stops and overshoots do not create a
  fictional direct hit.
- Route live single-shot area turrets through `TurretFireSystem`.
- Map the Hephaestus to `CANNON_HE` and give its detonation a short fire phase
  alongside the existing lingering smoke plume.
- Let the existing transitional target-affinity seam see turret penetration so
  the anti-armor cannon does not actively avoid hardened targets.

## Constraints

- Contact privilege comes from `BallisticResolver`, never a mech/turret type
  check.
- Direct and area payloads do not stack on the contacted actor.
- Splash retains current friendly-fire, cover, roof, and line-of-sight rules.
- Heavy Mortar keeps its distinct long-range area-and-wall identity.
- Do not data-drive or split `TurretKind`; W6 still owns the broader
  platform/mount/weapon migration.
- Do not invent a parallel particle renderer; reuse the existing effect-event
  and `CANNON_HE` presentation paths.

## Acceptance

- Live Hephaestus fire queues an arrival-timed detonation rather than generic
  infantry damage.
- A direct armored victim receives contact damage at contact penetration once;
  an adjacent armored victim receives area damage at area penetration once.
- A wall stop has no direct victim, and an overshoot has neither direct damage
  nor a detonation.
- The Hephaestus remains single-shot, has a deliberately slower cooldown, and
  prefers an armored target over materially closer infantry when both are
  otherwise valid.
- Its impact profile produces the heavy cannon explosion/light/audio recipe,
  an immediate fire burst, and a smoke plume with a short-lived fire phase.
- Focused turret, durability, and presentation tests pass, followed by the full
  suite.
