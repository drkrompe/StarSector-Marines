# Progression — next session

## State of play

**Design stage. Nothing implemented.** This session established the track:

- [`audit.md`](audit.md) — the measured baseline for all four progression
  axes as of 2026-08-22. Numbers were read from source, not estimated.
- [`overview.md`](overview.md) — concept, six locked design commitments,
  and the ten-story decomposition.
- [`stories/`](stories/) — S1 through S10, each with scope, out-of-scope,
  acceptance, and open questions. All ten are **contracted but unstarted**.

No code has changed.

## What the audit found

Ordered by how badly it hurts:

1. **Lethality is far too low.** A 25 HP marine takes ~20 s to drop under
   sustained pulse-rifle fire. Firefights are attrition, not decisions.
2. **The upgrade ladder moves damage 8-13% end to end.** Masterwork is a 3%
   damage step over Milspec. Nothing reads as a power tier.
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

**Start with S1** ([`stories/s1-lethality-and-tier-spread.md`](stories/s1-lethality-and-tier-spread.md)).
It has no dependencies, needs no new systems, and is the single change that
most alters how the game feels. Every other story in this track is a reward
layered on combat — that combat should be worth rewarding first.

Two things to know before opening S1:

- It is a **cross-cutting numeric change**. Alien/swarm HP (currently
  1.875, runners 2.5) was tuned across several living-world passes against
  *today's* damage values and will invert if damage moves. Mech, turret,
  wall, and rocket numbers share the same scale. S1 lists the full
  re-derivation set.
- The **TTK harness is a deliverable**, not a scratch script. It is what
  makes this tuning pass and every later one reviewable.

**Then S3** ([`stories/s3-per-soldier-telemetry.md`](stories/s3-per-soldier-telemetry.md)),
which unblocks S4, S8, and S9 and starts accumulating the data S1's tuning
will want anyway.

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
- *(this session)* — resolve the font floor into a type scale plus S8
  Slice 0 (UI scale mult), and add factional equipment identity as S6
  Slice 3.
