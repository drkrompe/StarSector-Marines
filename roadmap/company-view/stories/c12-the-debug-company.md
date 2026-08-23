# C12 — The debug company

> A debug mission deploys a bag of seats. Twelve identical loadouts arrive
> with no squad, no leader, no name on the manifest — so the one surface we
> use to test deployment is the one surface that cannot test what a
> deployment now is.

**Status:** contracted 2026-08-23, from a playtest question — "do these
changes impact the DEBUG missions as well?" The answer was *half*, and the
half that was missing is the behavioural half.
**Slices 1-2 shipped 2026-08-23; slice 3 (combined arms) is deferred by
design, waiting on player-side vehicle deployment to exist.**

Depends on C1 (`c1-fireteam-identity-through-the-drop.md`) and C7
(`c7-organization-and-ranks.md`), both complete: this story reuses their
seam rather than adding one.

## Problem

`CampaignMarineDeployment` has two paths into a battle, and they do not
carry the same thing:

| | campaign | debug |
| --- | --- | --- |
| built by | `freeze` / `freezeSelection` from `MarineRoster` | `debugFixture` from `DebugPersonnelPreset` |
| squad identity | `CampaignSquadTag` per seat | **none** |
| squad leader | the campaign NCO takes the billet | first marine to land |
| multi-lift join | one squad across every lift | a fresh squad per wave |
| form-up gate | holds at the LZ until assembled | **never fires** |

`SquadFormUpSystem.formingUp` requires `campaignSquadId != null`, so the
gate C8 shipped is unreachable from a debug mission. So is C1's
`(campaign squad, LZ)` join. Every behaviour those two stories added is
invisible on the surface built for testing behaviour.

The capacity half *does* apply — `ShuttleType` is global, so a debug
Valkyrie already lands twelve — which is what makes the split confusing:
the fixture looks updated and behaves like the old one.

### Why a preset cannot fix it

`DebugPersonnelPreset` is a **per-seat texture**: `loadout(seat)` returns
weapon, grade, aptitude, experience, armor for one seat, keyed on
`seat % 3`. It has no concept of a squad, so there is nowhere to hang a
tag. Adding tags to it would mean re-deriving squad structure from seat
arithmetic — the exact thing C1 removed.

## Decision

**A debug mission deploys a real `MarineRoster`.**

Not a parallel fixture type — the same class the campaign persists, built
in memory, never registered with `MarineRosterScript` and never written
back. The existing `freezeSelection` path then runs unchanged, and every
downstream behaviour follows for free: tags, frozen labels, the NCO in the
leader billet, `EnlistedRank`, `CampaignSquadIndex` joins across lifts, the
form-up gate.

The point is not that this is less code (it is, but only just). The point
is that **the two paths stop being able to drift**. A debug company is
built by the same `createSquad` / `recruitToSquad` calls a campaign uses,
outfitted through the same `allocatePrimary` / `allocateArmor` rules
against a real `MarineArmory`, and led by the same
`MarineRoster.refreshLeadership`. A loadout the armory would refuse is a
loadout the fixture cannot produce.

### Campaign points, not personnel flavors

`RECRUITS` / `MIXED` / `VETERANS` described the *texture of the marines*.
That is one axis of a company and not the interesting one. What a playtest
actually wants to choose is **where on the campaign arc the company sits**,
because that moves several things at once — count, experience, kit,
supporting arms — and moves them together in the combinations that can
really occur.

So `DebugCompanyStage` replaces the preset, and each stage is a snapshot:

| Stage | Squads | Marines | Plan | Officer | Mechs |
| --- | ---: | ---: | --- | --- | ---: |
| First Contract | 1 | 12 | starter issue, all Green | Lieutenant | 0 |
| Established | 3 | 36 | seasoned — Veteran NCOs over Regulars | Captain | 3 |
| Veteran Company | 6 | 72 | hardened — Elite NCOs over Veterans | Major | 6 |
| Reinforced | 17 | 204 | seasoned | Lt. Colonel | 9 |
| Full Strength | 34 | 408 | hardened | Colonel | 18 |

**Size and quality are separate axes.** `DebugBilletPlan` carries quality
(three profiles); the stage names a plan and a default size; the briefing's
squad dial overrides size up to `DebugCompany.MAX_SQUADS`. A fixed ladder
cannot answer "how many marines does this mission need" — that is a dial
question, and it is the question play actually asked.

**First Contract is literally the campaign opening** — one squad of twelve
through `recruitToSquad` with the auto-issue pattern untouched, which is
what `bootstrapInitialComplement(MarineSquad.CAPACITY)` produces on a new
game. It is a fixture only in that nothing persists it.

A stage is one enum constant plus a billet plan; adding a fourth point on
the arc is a small edit, deliberately.

### What the stage does *not* decide

- **Lift.** The debug transport picker stays authoritative — pick the
  hulls, and `freeze` truncates to the seats that exist, exactly as a short
  campaign manifest does. A six-squad company in one Valkyrie deploys
  twelve marines and says so. The dial's ceiling of 40 squads is the lift
  ceiling: a CONQUEST at HIGH risk authorises 40 drops, and 40 Valkyrie
  drops is 480 seats.
- **Command.** `captainCommandReady` already returns `true` for debug
  missions, so the officer cap does not gate the fixture. The stage names
  an officer rank for the readout only; wiring a synthetic captain is
  deferred until something reads it.

## Slices

1. ~~**The company.**~~ **Shipped.** `DebugCompany.roster(stage)` builds the detached
   roster; `DebugCompanyStage` carries the three points; `MissionLaunch`'s
   debug branch calls `freezeSelection` like the campaign branch does.
   `DebugPersonnelPreset` and `CampaignMarineDeployment.debugFixture` go
   away. The briefing's personnel line and fixture button read the company.
2. ~~**The lance.**~~ **Shipped.** A stage declares its mech support, and switching stage
   sets the mech picker's default count. The picker still overrides — it is
   the mech-family testing tool and this story does not take it away.
3. **Combined arms.** *Deferred, not built.* When player-side vehicles
   deploy, the stage is where a convoy joins the company. Recorded here so
   the extension point is not re-derived: `DebugCompanyStage` already
   declares supporting arms, and `MechSupport` shows the shape a
   `CommandPower`-delivered arm takes.

## Acceptance

- A debug deployment produces squads with real names, one NCO leader each,
  and `EnlistedRank` on the roster rows.
- A debug squad crossing in three lifts lands as **one** battle squad.
- `SquadFormUpSystem` holds an assembling debug squad at its LZ — the gate
  is reachable from a debug mission for the first time.
- Cycling the stage changes squad count, experience, kit and mech default
  together.
- Nothing in the campaign roster is read, mutated, or persisted by a debug
  mission. `MissionResolver.apply` already short-circuits on
  `isDebug()`; that stays true and stays tested.
- The briefing states what the company is before deploy, and the marines it
  names are the marines that land.

## Files touched

- `ops/detachment/DebugCompany.java`, `DebugCompanyStage.java` — new.
- `ops/detachment/DebugPersonnelPreset.java` — deleted.
- `ops/detachment/CampaignMarineDeployment.java` — `debugFixture` deleted.
- `ops/MarineOpsContext.java` — holds the stage and caches its roster.
- `ops/MissionLaunch.java` — one deployment shape for both sources.
- `ops/BriefingScreen.java` — personnel line, stage button, mech default.

## Open questions

- **Determinism.** `createRecruit` rolls names and aptitudes, so two builds
  of the same stage are not identical. The roster is cached per stage so a
  session is self-consistent (the briefing names the marines that land),
  but a seeded build would let a balance run be repeated exactly. Worth
  doing when something needs it, not before.
- Should `MissionResolver` compute a *throwaway* outcome for a debug
  mission so after-action UI can be tested? Today it short-circuits before
  the writeback, which is correct, but it also means C6's after-action view
  has no debug surface.

## What shipped

`DebugCompany.roster(stage)` builds a detached `MarineRoster` with
`createSquad` / `recruitToSquad`, stocks a real `MarineArmory` against the
stage's billet plan, and issues kit through the inventory-checked
`allocatePrimary` / `allocateSecondary` / `allocateArmor`. `MissionLaunch`'s
two branches are now the same call with a different roster and selection.

Three things came out differently than the story assumed:

- **`MarineRoster.refreshLeadership` had to become public.** Leadership is
  re-derived by enlistment, transfer and post-mission outcome, but *not* by
  `MarineSoldier.addExperience` — and experience is what decides who leads.
  The last recruit's XP therefore lands after that squad's final enlistment
  refresh. A caller that awards XP outside `applySoldierOutcome` has to say
  so; the method is idempotent, so making it public costs nothing.
- **The armory is stocked before anyone is recruited**, not alongside.
  Allocation is inventory-checked and *silent* on refusal — an under-stocked
  armory reads as marines holding the starter rifle, not as an error. The
  test asserts the scarce item (one masterwork DMR per squad) actually
  landed, because that is the failure this design can hide.
- **`generatedPersonnelCarryNoSquadIdentity` had to be retargeted.** It
  asserted the *debug fixture* carried no tag, which is now precisely wrong.
  The claim it was really protecting — scenario-authored spawns keep the
  per-shuttle minting — is now made against `MarineLoadout.COMBATANT`.

**Not verified end to end.** The fixture is tested to produce tags, and the
tag → `CampaignSquadIndex` → `SquadFormUpSystem` chain has its own tests,
but no test drives a debug multi-lift drop through a live sim. The air
fixtures build one shuttle at a time, so that needs a harness this story did
not build. First real proof will be a play pass.

## The scaling finding (2026-08-23)

Play reports a **CONQUEST at HIGH risk wanting 200-400 marines**. Checking
the numbers against what exists:

| | value | note |
| --- | ---: | --- |
| CONQUEST/HIGH required drops | 40 | `MissionGenerator.requiredDropsFor` |
| Seats at a Valkyrie's capacity | **480** | lift was never the constraint |
| `Rank.COLONEL.squadCommandCap` | 24 squads = **288 marines** | the ladder's top rung |
| 400 marines | 34 squads | **past every officer rank** |

Two conclusions, and they point in different directions.

**The lift is fine.** Nothing needed to change for a 400-marine drop to be
physically deliverable — 40 drops of a twelve-seat hull already covers it.
The debug company was the only thing that could not field them, and now it
can.

**The mission ladder has outgrown the command ladder.** This contradicts an
assumption recorded in `next-session.md`: *"the officer rank cap and the
lift capacity together bound what reaches one battle — a dozen squads,
realistically."* A HIGH conquest wants two to three times that. So one of
these has to give:

- **More officers per deployment.** A 400-marine drop is several officers'
  commands — a task force, not a company. This matches the fiction (the
  player *is* the company; officers command sub-units), matches C3's
  planned "officer grouping" pagination, and keeps `Rank`'s numbers
  meaningful. It needs real work: `MarineOpsContext.selectedCaptainId` is
  singular and `CaptainDeploymentPolicy` validates one officer's squads.
- **Raise the rank caps.** One enum edit, but it makes a Colonel's command a
  regiment and quietly redefines what a card in C3 shows.
- **Or CONQUEST/HIGH is mistuned** and its 40 drops are the outlier.

Not decided here. The fixture deliberately does *not* pick a side: the top
two stages exceed their officer's cap and `exceedsCommandCap()` reports it,
so the briefing shows the overrun in the blocked colour instead of hiding
it. Debug missions bypass `captainCommandReady`, so the fixture still
deploys — the point is to make the gap visible while play establishes what
the real number is.
