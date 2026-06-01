# Deliberate, capped, uncontested-aware compound capture

> **Shipped** (commit `PENDING`). `ConquestCommand`'s strip-local "ripe"
> compound pass was replaced with a map-global `assignCompoundCaptures`
> pass + 6 new `ConquestCommandTest` cases. What landed matches the design
> below.

> The attacker commander treats compound capture as the win objective it
> actually is — peeling a *measured* detachment off the hunt to grab a
> compound the moment it's safe, rather than capturing only as an accident
> of the front line sweeping over it.

## The bug

Conquest is won when **every** compound (COMMAND_POST / BARRACKS / ARMORY)
reaches `MARINE_HELD` (`ConquestObjective`). But in playtest the marines
clear the map of enemies and then *swarm* in search-and-destroy — chasing
the squads that defender convoys periodically drop — without ever
consistently capturing the objectives needed to actually win.

Root cause, in `ConquestCommand` (pre-fix):

- The **only** path that produced a `SECURE_COMPOUND` order was Pass 1,
  `nearestRipeCompound()`, gated on **"the compound is at or behind the
  squad's forward line"** (`compoundForward <= squadForward + LOOKAHEAD`).
- Compounds sit *deep* in defender territory (ahead of the landing force),
  so a squad only earned a capture order once it had *already advanced* to
  within a few cells of the compound.
- What advances the squad? Pass 2, `nearestDefenderZoneInStrip()` — chase
  the nearest defender. With convoy drops constantly relocating "nearest
  defender" all over the map (and `EliminateEnemiesGoal` engaging anything
  in LoS), the front sloshes around mopping up drops and never closes the
  last cells to a compound. Compounds never go "ripe" → never assigned →
  never captured → unwinnable → permanent swarm.
- Secondary: capture orders were **strip-local** — only squads sticky-
  anchored to a compound's strip could ever be assigned it. With 3 strips
  and 3–5 squads, a compound in a strip holding no live squad was orphaned
  (zero capture orders, forever).
- Tertiary: when Pass 1 *did* fire, every squad in the strip independently
  targeted the same nearest compound — no per-compound cap. So it was
  either nobody or everybody.

## The fix — a map-global, capped capture pass

Replace the strip-local "ripe" heuristic with a deliberate compound pass
that runs ahead of the clear-zone push:

1. **At init**, cache per non-`MARINE_HELD` compound:
   - its **garrison zones** via `GarrisonArea.garrisonZones(node, margin, sim)`
     — the AABB size+containment gate (story 17). This is the *logical zone
     filter*: the unbounded outdoor flood fails the size gate on a single
     cell-count read, so a defender merely standing outdoors near a compound
     never counts as "in" the compound.
   - `desiredSquads` = **2** if the compound is multi-room (a keep), else
     **1** (scale by size).
2. **Per slow tick**, for each non-`MARINE_HELD` compound, classify
   **contested** = any live defender in a garrison zone.
   - **Uncontested** → assign the *nearest available* squads up to
     `desiredSquads` (greedy nearest-pair, so several compounds spread the
     squads instead of piling them on one).
   - **Contested** → assign only squads **already inside/adjacent** to a
     garrison zone (convert incidental presence into a committed
     capture+hold); never pull fresh squads into a defended compound.
   - A squad's existing valid `SECURE_COMPOUND` is preserved (stability /
     idempotency) and counts against the compound's quota.
3. Squads not pulled for capture fall through to the **existing strip
   clear-zone push** (search-and-destroy), unchanged. `HOLD_NODE` garrison
   squads are still skipped.

The cap is what answers the design intent: *some* squads capture, the rest
keep hunting; the whole force is never stripped off the enemy.

## Why it composes with what's already shipped

Downstream is already wired (tug-of-war v2 slice 1 + story 17 0b):
capture → state flips `MARINE_HELD` → `CompoundGarrisonSystem` drops a
born-`HOLD_NODE` garrison squad to hold it → the capturing assault squad's
target compound leaves the candidate set → it releases back to the strip
push / hunt. So peeling one squad to capture and releasing it is exactly
the intended flow; the commander simply now *initiates* it deliberately
instead of waiting for the front to wash over the compound.

## Design knobs (tunable constants)

- `desiredSquads` size threshold — room count at which a compound rates 2
  squads. Default: multi-room.
- "contested" radius — garrison zones only (the compound's own rooms), via
  the AABB gate. A defender in the adjacent open street does *not* block a
  capture order; one in a garrison room does.

## Cross-refs

- [`central-keep.md`](../central-keep.md) — V1 compound-as-supply + win
  condition (`ConquestObjective`).
- [`tug-of-war-v2.md`](../tug-of-war-v2.md) — garrison drop that holds the
  captured compound (the release valve this pass relies on).
- `roadmap/ai/stories/12-squad-of-squads.md` § Improvement path — the
  commander-tier improvement bank this slots into.
- [[battle_services_systems]], [[tier_override_design]].
