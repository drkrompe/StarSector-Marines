# S7 — Grade visual identity

> If a marine is carrying the best rifle in the company, the player should
> be able to tell by looking at the fight.

Status: PLANNED — depends on the shipped quality scale in `progression-nouns.md`.
Written: 2026-08-22
Updated: 2026-08-24 — separated weapon-grade effects from armor-role and faction-provenance presentation.

## Problem

`EquipmentGrade` is a pure stat modifier with zero presentation. A Surplus
rifle and a Masterwork rifle produce identical muzzle flashes, identical
tracers, identical impacts, and identical marines. The player spends the
chase currency, wins the unlock, issues the weapon — and the battlefield
looks exactly the same as before.

The armory screen shows a tier mark (`I`/`II`/`III`/`IV`). That is the
entire feedback loop for the mod's top-end reward.

## Goal

Make grade read at a glance in the field, and make Masterwork feel like an
event.

## Design principle

Key the presentation to the **grade capability**, not to a new carrier type
([[feedback_compose_effects_not_carrier]]). `EquipmentGrade` is already
carried on the combat component and already flows into
`InfantryCombatStats`; the render side should read the same value and
select effects from it. No `MasterworkMarine` unit type, no parallel
sprite family per grade.

Practically: `ShotFx` and the appearance/render collectors take grade as an
input the same way they take weapon family today. Appearance stays
authored component data written by presentation systems
([[feedback_appearance_authored_component]]) — the sim must not read any
of it.

## Slice 1 — Weapon fire chrome

Scale existing effect parameters by grade rather than authoring new assets:

- **Tracer/bolt tint and intensity** — higher grades read cleaner, hotter,
  more saturated. The existing per-weapon `tracerColor` stays the family
  identity; grade modulates it.
- **Muzzle flash scale and light contribution** — grade-scaled, feeding the
  shipped `surface-relief-nouns.md`
  dynamic light budget. Note that budget is a fixed eight lights; grade
  must modulate existing muzzle events, not add new ones.
- **Impact weight** — `ImpactProfile` selection or scaling, so a Masterwork
  round visibly hits harder.
- **Fire audio** — a subtle layer or pitch/mix shift, not a new clip set.
  The mod ships a public-release licensing constraint
  ([[project_audio_licensing_policy]]); reuse before authoring.

Reuse-first is the rule here, matching how the ballistics S3a weapon-FX
families were built.

## Slice 2 — Masterwork as an event

Masterwork should not just be "Milspec but more". Candidates, pick one or
two rather than all:

- A distinct traveling-round silhouette, the way shell-backed weapons
  already differ from bolt-family ones.
- A persistent glint or metallic treatment on the carried weapon layer.
- A distinct impact signature the player learns to recognize.

The test is whether a player watching a firefight can point at which marine
has the good gun without opening a screen.

## Slice 3 — The marine, not just the gun

Grade lives on the weapon, but the audit's broader finding is that **no
per-marine quality is visible in the field at all**. A small persistent
marker on high-tier marines closes the loop:

- Armor tier already varies the sprite family — `RED_ELITE` reads
  differently. Confirm that reads clearly at battle zoom; if it does not,
  that is a cheaper win than anything else in this story.
- Armor role, silhouette, and faction provenance belong to
  `powered-assault-armor-roles.md`. S7 may consume that visible suit, but must
  not recolor it from weapon grade or invent a competing armor presentation
  table.
- A restrained kit marker for top-grade or veteran marines. Restraint
  matters: the battlefield already carries fog-of-war state, squad
  selection, contact ghosts, and objective markers. This must not become
  another layer of noise.

Note the overlap with `s9-in-battle-quality-conveyance.md`, which owns
*experience* conveyance in battle. Coordinate so the two do not each invent
a marker. Recommended split: S7 owns anything driven by **equipment**, S9
owns anything driven by **the person**.

## Out of scope

- Armory screen presentation of grades — that surface exists and works.
- New sprite families per grade. Modulation of existing assets only, unless
  a specific Masterwork silhouette earns its keep in Slice 2.
- Armor-role and faction-provenance visuals; those are concrete armor-pattern
  identity rather than weapon-grade chrome.
- Sim behavior. This story changes nothing the simulation reads.

## Acceptance

- Grade is identifiable from the battlefield without opening a screen,
  confirmed by an in-game pass. This story is inherently sign-off-by-eye —
  tests can only guard the plumbing.
- The sim reads none of the new presentation data; determinism is
  unchanged. The battle-radio track already established this
  presentation-only discipline for audio and it applies here.
- Effects are grade-keyed, not type-keyed: adding a weapon family requires
  no new grade-specific code.
- The surface-relief light budget is respected; no new light sources.
- Performance: no additional per-frame state queries in the render path
  ([[async_renderer_bridge_glget_stall]]).
