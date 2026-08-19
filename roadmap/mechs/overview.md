# Mech roster

**Status:** Active. S1's modular hardpoint substrate and three-chassis debug
comparison shipped in `2d3f044b`; battlefield tuning and production-roster
integration remain.

## Concept

Mechs should form a readable battlefield family rather than a single rare unit
that answers every problem. The existing heavy remains the apex generalist. A
small set of lighter striders trades its health pool and complete weapon suite
for speed, a sharper purpose, and a weakness the player can exploit.

The important split is:

- A **variant** is hardware: body profile, weapons, ammunition, silhouette, and
  physical/stat limits.
- A **role** is doctrine: where the GOAP planner tries to stand, what it
  protects, and when it commits or withdraws.

Those dimensions stay independent. A missile strider can use the existing
long-range-support doctrine without making `LR_SUPPORT` mean a particular
chassis, while a heavy can still be assigned either of today's doctrines.

## Current baseline

The only live mech is `UnitType.HEAVY_MECH`: 540 health, 1.15 movement speed,
and a chaingun, SRM pod, and LRM artillery pod. Its weapon tracks operate
independently, so it threatens every range band. `LR_SUPPORT` and
`ARMORED_SUPPORT` change how it uses that equipment, not what it carries.

The renderer composes distinct Bulwark, Hound, and Sirocco chassis, chaingun or
twin-linear-cannon arms, and empty, SRM, LRM, or heavy-SRM shoulder slots. The
first shared-hull comparison proved too samey, so Hound and Sirocco now have
dedicated raster silhouettes. External racks render beneath the body layer and
extend its flanks rather than covering the torso.

## Proposed family

Names and numbers are provisional. The battlefield identities are the part to
approve first.

| Variant | Intended job | Initial equipment | Character and weakness |
| --- | --- | --- | --- |
| **Bulwark** (current heavy) | Apex generalist and anchor | Chaingun + SRM + LRM | 540 HP and all three range bands, but slow, conspicuous, and expensive |
| **Hound** (breach strider) | Fast close assault | Chaingun + SRM; no LRM | Reaches streets and compounds quickly, but has no indirect or long-range answer |
| **Sirocco** (missile strider) | Mobile fire support | Linear cannon + LRM; no SRM | Projects indirect pressure, but is fragile and folds when isolated at close range |
| **Needle** (scout strider) | Recon, target finding, flanking | Linear cannon; no pods | Fast and observant, but lacks area damage and needs a new recon doctrine to matter |

S1 implements the substrate plus Hound and Sirocco. Its first slice now ships
paired small racks on the light specialists: Hound carries two SRM-5 components
and Sirocco two LRM-5 components, while Bulwark retains its SRM-15/LRM-15 pair.
Needle deliberately waits
for a later story: merely making a fast weak shooter would not fulfill the
recon fantasy. It needs information play such as spotting or target painting
and a corresponding GOAP role.

## Design rules

- Every light variant must give up at least one weapon band and much of the
  heavy's durability. It is not a discounted heavy.
- Silhouette, movement, and firing behavior should reveal a variant before the
  player learns its name.
- Smaller artwork must have matching selection, collision, separation, hit,
  and blast geometry. A cosmetic scale change over the heavy's body is not
  acceptable.
- Mixed lances should become more interesting, not simply add more total mech
  threat. Defender composition therefore moves toward a threat budget rather
  than treating every mech body as equal.
- The current heavy remains the control case and the default player Mech
  Support drop until a separate player-choice story exists.

## Story sequence

1. [S1 — Specialist striders](stories/s1-specialist-striders.md): add the
   variant model, Hound and Sirocco, a deterministic comparison fixture, then
   integrate them into budgeted defender lances after tuning.
2. **S2 — Recon strider** *(unwritten)*: give Needle a real information role,
   including sensor/target-painting interactions and a `RECON` doctrine.
3. **S3 — Player access and progression** *(unwritten)*: decide how variants
   enter command powers, ownership, salvage, and refit rather than exposing a
   debug-style loadout picker as progression.

## Boundaries

This track does not yet include procedural hardpoint construction, player mech
ownership, salvage/refit UI, or multi-cell occupancy. It also does not replace
the AI role work documented in
[`../ai/overview.md`](../ai/overview.md); it supplies distinct hardware for
that planner to command.
