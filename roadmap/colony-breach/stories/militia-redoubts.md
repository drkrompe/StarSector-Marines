# Militia redoubts

Status: IN PROGRESS

Written: 2026-10-21

Model: `colony-breach-nouns.md`. Marine side: `extraction-command.md`.

Owner, 2026-10-21: "Now that we have larger maps we might consider creating
militia redoubts where our colonists might be holed up vs just having a
building."

## Scope

The colonists to be rescued are holed up in **2-4 redoubts** spread across the
colony instead of one shelter building.

1. **Redoubts.** One **main redoubt** — today's shelter, with the largest cohort
   and the sealed shelter guard, unchanged in every law — and one to three
   **holdouts**, seeded. A holdout is a colony building away from the main
   redoubt, the lift and the other holdouts, with a small cohort of its own
   inside and a militia fireteam holding it through ordinary held-place
   garrison manning. Its approaches get sandbag aprons laid by the compound
   prepared-ground stamper (`CompoundPreparedGround`), so it reads as a
   desperate holdout and offers the garrison real fighting positions. One
   holdout may keep a working colony emplacement. Nothing new is invented:
   building, apron, emplacement and garrison all exist.
2. **Cohorts and release.** All redoubts' colonists are one evacuation cohort
   for the objective, the outcome and the campaign. Each colonist belongs to
   one redoubt and is released when a Marine reaches *that* redoubt's door,
   then walks to the lift under the existing per-colonist escort law.
3. **Falling.** A holdout's militia are ordinary prey: the swarm hunts them as
   it hunts anything it is hostile to. A holdout **falls** when its garrison is
   dead and a swarm body stands inside its perimeter, or when every colonist
   still sealed in it is dead. Its sealed colonists are then unprotected. The
   main redoubt is sealed and hidden as the shelter is today, so it does not
   fall; that is the triage: the main cohort is safe, the holdouts are not.
4. **Mission command.** The rescue commander handles several cohorts with the
   simplest honest model (recorded in `extraction-command.md`): every released
   redoubt with colonists still walking gets its own close escort to the lift;
   the rest of the mobile force relieves one pending redoubt at a time, chosen
   by route length from where that force stands, and keeps its choice until
   that redoubt is reached or falls. Escorts come before relief: colonists in
   the open are exposed, colonists behind a door are not. With one redoubt the
   commander plays exactly as it does today.
5. **Beats.** `redoubt_reached_N` and `redoubt_fell_N` (PLAYER), redoubts
   numbered from 1 with the main redoubt as 1; `shelter_reached` stays the
   main redoubt's release.
6. **Map requirements** for redoubt sites; **annotations**: `REDOUBT N` on
   review frames, with the redoubt's state.
7. **Evidence**: the colony-breach summary records each redoubt (cohort, guard,
   reached and fell ticks). A preview run shows the redoubts on the map and one
   falling.

## Interplay noted, not built

- **Harvest** (`swarm-harvest.md`): a fallen holdout's colonists are the
  harvest's easiest biomass; harvest decides what a runner does with them.
- **Creep** (hive creep): a redoubt under creep is a holdout whose ground is
  turning; whether creep weakens aprons or garrisons is creep's to say.
- **Hive posture**: a purge posture competes with relief for the same squads;
  the readiness gate should count pending holdouts as unmet need.

## Out of scope

Bores, creep, harvest, strains, swarm movement. Lower-risk rescues keep one
shelter.

## Acceptance

- The colony generates 2-4 redoubts, seeded and stable across builds, each
  holdout outside the shelter and lift zones with a garrison and its own
  colonists; maps that cannot seat them re-roll.
- Each holdout's colonists stay put until a Marine reaches it, then walk out.
- A holdout whose garrison dies to the swarm emits `redoubt_fell_N`.
- `commanderEvidence -Pmission=colony-breach` completes and reports redoubts;
  `-Pmission=extraction`'s Rescue half is unchanged; new unit tests under a
  second each.
- Noun doc, beat table and `extraction-command.md` updated.
