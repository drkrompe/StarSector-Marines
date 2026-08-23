# Progression — next session

## State of play

**S11 combat reflexes shipped in `cb22c3d3`.** New-threat registration is a
universal experience passive, not a doorway rule: Green / Regular / Veteran /
Elite soldiers wait 0.50 / 0.35 / 0.20 / 0.05 seconds before primary fire at a
new target. The timer advances while closing, resets on a target switch, and is
not reapplied to follow-up cadence. Opportunity fire shares the same gate;
fauna, drones, turrets, and mechs are excluded. Full record:
[`complete/s11-combat-reflexes.md`](complete/s11-combat-reflexes.md).

**S3 is shipped — all three slices.** In-battle recording and attribution, the
crossing onto `MissionOutcome`, and the persisted `SoldierCareer` on
`MarineSoldier`. The story moved to `complete/`; full details there.

A played mission now logs a combat-telemetry table covering every entity
(defenders, employer militia, the fallen), and every deployed marine's
lifetime record accumulates missions, rounds fired and landed, damage dealt
and taken, kills, and times wounded.

Four things worth carrying:

1. **Attribution belongs at `DamageResolver.resolve`**, not at the fire site.
   It is the only point that knows what a hit cost after cover, armor and the
   hardened multiplier — a queued damage value is a request, not an outcome.
   Credited damage is clamped to the pool that was left, so overkill is not
   counted as output.
2. **Attacker-side reads in the damage path must be presence-gated.** Slice 1
   shipped a fail-loud `identity().faction(attackerId)` and an armed convoy
   APC crashed the sim on its first splash round — a vehicle carries
   `GROUND_IDENTITY`, not `IDENTITY`. Fixed in `ce031aba` by gating on
   `CombatTelemetryService.isRecorded`. Not every attacker is a unit.
3. **`roundsHit` is counted at the arriving `PendingImpact`**, not the damage
   event, so one round stays one landed round even when its detonation
   damages six units.
4. **`outcomes` is the deployment manifest; telemetry is the evidence.**
   Every marine in the manifest gets a deployment counted even with no
   telemetry row — they were there. Only the ones with a row get counters.

**S4 is now unblocked and is the obvious next story**: it converts these
totals into experience, and S1 slice 2 deliberately made equipment 2.4x the
soldier ladder on the understanding that S4 lifts the soldier side to match.
S8 and S9 are unblocked too.

**S1 is code-complete — Slices 0, 1 and 2 all shipped. Combat lethality and
the equipment ladder both changed across the board, and the story's last
acceptance item is a play pass that cannot be signed off from tests.** The
track's documents are:

- [`audit.md`](audit.md) — the source-read baseline for all four progression
  axes as of 2026-08-22. Numbers were read from source, not estimated.
- [`overview.md`](overview.md) — concept, eight locked design commitments,
  and the ten-story decomposition.
- [`stories/`](stories/) — S1 through S10, each with scope, out-of-scope,
  acceptance, and open questions. S1 has shipped; S2 through S10 are
  **contracted but unstarted**.

**S1 Slice 0 (TTK harness) shipped.** `TtkHarness` + `TtkReportTest` under
`src/test/java/.../battle/balance/` drive the real firing pipeline —
`FiringSystem`, burst continuation, the pending-impact drain — against two
spawned units until one dies, and emit
`build/reports/balance/ttk-baseline.md`. The one production change is a
`BattleSimulation.getShots()` accessor, in the same service-direct style as
`getRoster()`. Full findings are in
[`stories/s1-lethality-and-tier-spread.md`](stories/s1-lethality-and-tier-spread.md)
under "Measured baseline"; the three that change what S1 does next:

1. **The floor is worse than the audit estimated.** Pulse rifle vs an
   unarmored marine is **30 s**, not ~20. Against T4 armor, **47 s**.
2. **The Field Rifle cannot kill a marine at all** — 0 of 120 trials inside a
   two-minute timeout, 18 of 120 against militia. Recruit issue is
   non-functional, not merely weak, and a uniform global damage scale will
   not fix it. Its *ratio* to the pulse rifle needs correcting.
3. **Grade and soldier ladders are equally strong, and both are ~1.75x** in
   measured TTK — far more than the audit's damage-multiplier reading of
   8-13%. S1 Slice 2's proposed spread is therefore a much bigger change than
   it was scoped as, and it would make equipment dominate who the soldier is.
   Decide that deliberately.

Cover was measured at 3.1x from open to hard, which is already the strongest
lever in the game and needs no widening. The problem is the floor it
multiplies.

**S1 Slice 1 (lethality budget) shipped.** Anti-personnel damage scaled by 9
and hardened-class HP by the same factor, so infantry fights resolve nine
times faster while rifle-vs-mech, rocket-vs-turret and mech-vs-mech are
bit-for-bit unchanged. All three target bands are hit: 3.40 s on an unarmored
marine, 5.13 s at T3, 8.90 s at T4. The full before/after table and every
hand-correction are in the story under "What shipped".

Two things worth carrying forward:

- **The uniform scale was wrong for aliens and swarm runners, and the
  existing contract test caught it.** Those two were already tuned to the
  target TTK, so preserving them meant scaling their *HP* and leaving their
  *damage* alone. Measured after: 1.73 s and 2.90 s, against 1.77 s and
  2.92 s before. When a later pass moves this scale again, that asymmetry has
  to be re-applied deliberately.
- **Slice 1 briefly made the grade ladder narrower, not wider** — 1.75x
  down to 1.46x — because higher damage means fewer rounds per kill, and
  fewer rounds quantizes away the accuracy and cooldown advantages that
  carried most of grade's value. Slice 2 then fixed it. The lesson carries:
  at this lethality, **damage per round is the only grade lever that moves
  TTK cleanly**; accuracy and cooldown only pay off when the round count is
  high enough to notice them.

**S1 Slice 2 (grade spread) shipped.** The story's proposed multiplier
table went in as authored. Surplus → Masterwork now spans **4.3x** in
measured TTK (from 1.46x), and every step is felt: 6.09 s / 3.42 s / 2.54 s
/ 1.41 s against an unarmored marine.

- **Defender difficulty barely moves at HIGH risk** and drops at LOW. The
  player mix and the HIGH-risk defender mix are nearly identical, so a wider
  ladder scales both equally; the player's edge over LOW-risk militia grows
  from 1.23x to 1.75x. Weighted numbers are in the story.
- **Defenders never wear armor packages.** `MarineLoadout.seedInto` applies
  one only when the loadout carries an `armorFamily`, and only
  `CampaignMarineDeployment` produces one. So Slice 1's widened armor tiers
  are a player-only buff — worth knowing before anyone tries to balance
  against "armored defenders".
- **Equipment is now ~2.4x the soldier ladder in impact** (4.3x vs 1.77x).
  Deliberate, and S4 is where the soldier side catches up. If S4 slips,
  veterans will read as a rounding error next to kit.

**Three existing tests implicitly depended on low lethality** and were
fixed as part of this work: `CommanderInfluenceServiceTest` and
`KillZoneIntegrationTest` both ran a measurement window long enough that a
combatant now dies inside it, which ends the battle and freezes the counter
under test; `SwarmRunnerContractTest` pinned deliberate hits-to-kill
breakpoints. Expect the same shape from any test that spawns two hostile
units in line of sight and then advances the sim for a while — give the
unit that must survive an explicit pool rather than relying on the
archetype's.

**What still needs a play pass** — neither is checkable from tests: the
living-world rescue scenarios (the alien and swarm exchange rates were
preserved exactly, which is the specific thing that would have broken them,
but every surrounding fight is 9x faster), and the Surplus-grade Field Rifle
against heavy armor, which is now close to hopeless by construction.

## What the audit found

Ordered by how badly it hurts. This is the 2026-08-22 record; items 1 and 2
are closed by S1.

1. ~~**Lethality is far too low.** A 25 HP marine takes ~20 s to drop under
   sustained pulse-rifle fire. Firefights are attrition, not decisions.~~
   Measured at 30 s, and now 3.4 s.
2. ~~**The upgrade ladder moves damage 8-13% end to end.** Masterwork is a 3%
   damage step over Milspec. Nothing reads as a power tier.~~ Now 4.3x in
   measured TTK, Surplus to Masterwork.
3. **Four armor patterns are authored and unreachable**, including
   `RED_ELITE`, the best armor in the game. `MarineArmory.recordVictory`
   unlocks no armor at all, ever.
4. **The unlock ladder is four rungs and ends at mission five.**
5. **Parts have one source: winning.** No market, loot, salvage, or reward
   channel.
6. **XP is flat per survivor.** Nothing a marine did feeds it.
   `CombatService.addExperience` exists and is unwired.
7. **No career history exists**, so there is no data to balance against.
8. **Six of eleven traits are inert**, and traits have no UI at all.
9. **Aptitude and experience are invisible** — one letter each in
   `SoldierProfile.shortLabel()`.

## Recommended pickup

**Play the game first.** S1 rewrote every combat number in the mod, and its
last acceptance item is a feel pass. Everything below is worth more after a
mission or two than before one.

Then **S4** (`s4-performance-derived-experience.md`). S3 shipped the input
data it needs, and S1 slice 2 made equipment 2.4x the soldier ladder on the
explicit understanding that S4 lifts the soldier side to match — if it slips,
veterans read as a rounding error next to kit. Confirm the story's open fork
first: live in-battle XP is recommended *against* for v1, with the mid-battle
promotion beat re-homed to S9 slice 4 as presentation. It is now the highest-value next story for
two reasons beyond its own scope: S1's tuning is argued entirely from a
synthetic two-unit harness, and per-soldier telemetry is what would let the
same claims be checked against real missions; and **S4 is load-bearing now**
— Slice 2 deliberately made equipment 2.4x the soldier ladder, on the
understanding that S4 lifts the soldier side to match.

When re-running the balance report: it is statistical, not seeded, so read
the standard-error column and compare bands. A 3% move between runs is
noise, not a result.

## Decisions already locked

Do not relitigate these in a story; change them in `overview.md` if needed.

- Raise the lethality floor **and** widen the tier gap — both, as separable
  work.
- Parts come from **five channels**: market conversion, battlefield loot,
  breakdown of recovered gear, special-mission rewards, and victories.
- **Advanced/masterwork feedstock is loot-gated only.** Never purchasable.
- High-grade gear is **visibly** high-grade in the field.
- Telemetry is a balance artifact, not only a reward input.
- Aptitude stays **innate**; the fix is conveyance, not mutability.

## Open forks worth resolving early

Each is recorded in its story, gathered here because they affect more than
one:

- **Blueprint reframe (S6 Slice 2)** — recipes as recoverable schematics
  rather than victory counters. Recommended, and it changes what S5's
  reward channels carry. Worth deciding before S5 implementation, not
  after.
- **Live in-battle XP (S4)** — recommended *against* for v1, with the
  mid-battle promotion beat re-homed to S9 Slice 4 as presentation. Confirm
  before S4 starts.
- **Traits for rank-and-file marines (S10)** — changes how much room S8's
  roster row must reserve. Worth an answer before S8 layout work.
- **Advanced components as physical cargo (S5)** — leaning yes; makes the
  chase legible and losable, but it is a bigger integration than a counter.
- **Coherent vs mongrel factional kit (S6 Slice 3)** — decides whether set
  bonuses exist, which shapes the whole armory UI. Leaning mongrel by
  default with coherence as a late aspiration.
- **Faction attaches to grade or to family (S6 Slice 3)** — grade-side
  composes with the existing resolver almost for free; family-side is more
  expressive and more work. Leaning grade-side plus a few faction-exclusive
  families.

## Resolved this session

**The font floor.** It looked like a hard blocker on S8's dense roster
view; it is not. Recorded in full in
[`stories/s8-roster-legibility.md`](stories/s8-roster-legibility.md) under
"The font floor — resolved". Three findings:

1. It is a **typeface** problem. Orbitron is a display face used at all 224
   text call sites. Vanilla ships text faces that are smaller *and* more
   legible — `insignia17LTaa` at 0.67x area per character, `insignia15LTaa`
   at 0.50x, `arial14` at 0.56x with the only tabular digits in the set.
   `Fonts.INSIGNIA_15_AA` already ships and is proven in the GOAP overlay.
2. The original playtest rejected **Victor 10** — a 9px pixel font with AA
   off, the lowest-fidelity option available. That verdict does not
   generalize to Insignia 17 or Arial 14, which were never tried.
3. `getScreenScaleMult()` is **never called** in the mod. The UI ortho is
   in virtual pixels, so Orbitron 20 renders at 30 physical px on a 1.5x
   scale and 20 on 1.0x — the floor was set in the wrong unit at one
   unstated setting. **S8 Slice 0** fixes this and benefits every screen in
   the mod, not just the roster view; it is small and worth landing early
   regardless of when the rest of S8 runs.

**Factional equipment** is now S6 Slice 3 — the change that makes the
unlock ladder lateral rather than merely longer, and the thing that gives
the blueprint reframe and patron rewards real flavor.

## Cross-track coordination

- [`../ballistics/`](../ballistics/overview.md) owns round resolution. S1
  and S2 sit on top of S1-S4a semantics and must not reopen them.
- [`../campaign/personnel/`](../campaign/personnel/overview.md) owns the
  personnel lifecycle and says "contract a new story before extending".
  This track is that contract for the progression side. `MarineRoster`
  stays the single roster authority — do not fork it.
- [`../campaign/loot/`](../campaign/loot/overview.md) owns the salvage
  manifest. S5 extends it rather than building a parallel drop system.
  Note its own manual verification queue is still pending.
- [`../command-powers/`](../command-powers/overview.md) has a separate
  meta-progression spine (command-point budget). That is player agency;
  this is troop quality. Keep them distinct.

## Commit chain

- `55e6c32f` — establish the progression track: audit, overview, ten story
  docs, handoff.
- `e338ac31` — resolve the font floor into a type scale plus S8 Slice 0 (UI
  scale mult), and add factional equipment identity as S6 Slice 3.
- `3a307354` — S1 Slice 0: TTK harness, measured baseline, and the
  three findings that re-scope Slices 1 and 2.
- `fdc49c36` — S1 Slice 1: the lethality budget. Anti-personnel damage
  x9, hardened HP x9, alien/swarm re-derived HP-side, DMR and Field Rifle
  ratio corrections, widened armor tiers.
- `775eb26a` — S1 Slice 2: widen the equipment grade ladder to 4.3x
  measured TTK, and record the defender-difficulty effect.
- `325b89ed` — S3 Slice 1: `TELEMETRY` component, attacker threaded
  through the damage pipeline, `CombatTelemetryService` /
  `CombatTelemetryReport`, per-mission log dump.
- `ce031aba` — fix a crash that slice shipped: an armed convoy vehicle is an
  attacker with no `IDENTITY`, so the fail-loud faction read took the sim down
  on the first splash round.
- *(this session)* — S3 Slices 2 and 3: telemetry frozen onto
  `MissionOutcome`, `SoldierCareer` persisted on `MarineSoldier` and
  accumulated by `MarineRoster`.
