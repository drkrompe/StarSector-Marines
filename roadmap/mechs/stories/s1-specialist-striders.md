# S1 — Specialist strider acceptance

Status: READY — implementation is shipped; manual battlefield tuning remains.

Written: 2026-08-19

Updated: 2026-08-23 — narrowed to the live comparison and tuning pass.

Read `mechs-nouns.md` before completing this story.

## Goal

Accept the Bulwark, Hound, and Sirocco as distinct, readable battlefield
choices. Tune only from representative play: the durable family boundaries,
hardware/doctrine split, absent weapon bands, and encounter budget are already
part of the noun model.

## Manual acceptance

- At ordinary gameplay zoom, distinguish all three variants by silhouette,
  movement, mounted hardware, and firing character before reading a label.
- Confirm the Hound reaches and pressures close objectives faster than the
  Bulwark, but cannot answer disciplined standoff fire and dies meaningfully
  sooner. Under Brawler doctrine it may keep a legal attack moving after losing
  nearby support, but it must remain inside its assignment leash.
- Confirm the Sirocco under Long-Range Support seeks a non-Sirocco friendly
  screen, uses long-range missiles as its primary pressure, and keeps its heavy
  cannon readable as a ballistic anti-armor fallback rather than a second
  close-range primary.
- Confirm a moving mixed lance adopts role-aware spacing in open terrain,
  compresses through real constraints, and expands afterward without moving
  planted firing posts or merging separate squads into one formation.
- Play representative MEDIUM and HIGH production encounters. Mixed defenders
  should change target priority without feeling strictly stronger than the
  former all-heavy allocation; small attacker forces must still shed mech and
  static-defense candidates through the shared force budget.
- In a DEBUG Conquest briefing, verify the configured family roster remains
  stable while editing other choices, rerolls explicitly, fits the briefing,
  and arrives in the displayed order through physical drops. Exercise a full
  four-chassis group, a partial final group, zero support, and a stress roster.

## Tuning constraints

- Preserve Bulwark as the durable all-band control case and the ordinary
  production Mech Support payload.
- Preserve Hound's missing long-range band and Sirocco's missing close-missile
  band. Do not tune either into a cheaper generalist.
- Keep visible scale, selection, collision, separation, ballistic contact,
  blast contact, and morale footprint consistent for every chassis.
- Preserve Brawler's assignment leash, Long-Range Support's screened posture,
  planted hip traverse, and role-aware formation ordering while tuning
  thresholds.
- Lighter variants replace encounter allocation; they do not add threat above
  the admitted defender budget.
- DEBUG delivery remains iteration scaffolding. Do not infer ownership,
  salvage, refit, lift, recovery, or campaign lance rules from it.

## Out of scope

- Needle or a recon/target-painting doctrine.
- Player variant ownership, acquisition, salvage, recovery, or refit.
- A campaign definition of lance organization or lift requirements.
- Procedural/custom hardpoint construction, multi-cell bodies, or final
  balance outside representative encounters.

## Completion

Record any accepted tuning in code and focused Javadoc, update the standing
noun model only if a family law changes, then fold and delete this story.
