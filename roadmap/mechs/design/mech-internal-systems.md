# Mech internal systems

Status: Heat, mechanical weapon cycles and the stock-frame baseline implemented; engine, armor and alternate subsystem fittings remain design-only. No new recorded dialogue.

Updated: 2026-10-16 — stock frame shipped; internals arrive as learned patterns with tier-scaled size (`heavy-equipment-progression.md`).

## Player promise

Wayward teaches ownership, active-lance selection and physical weapon fitting.
Its baseline chaingun and SRM-5 should remain understandable and useful. The next
slice gives a player a reason to tune that same chassis instead of replacing it
with the first larger frame. Preserve existing weapons and existing saves.

## Proposed equipment choices

- **Engine:** one core with output and mass. A cheap industrial engine supports
  the baseline fit; improved output buys mobility or equipment headroom at a
  material and cooling cost. Engine output is a fitting budget, not a second
  fuel or ammunition economy. Do not let engines erase chassis mobility identity.
- **Armor package:** trade protection capacity against mass and mobility.
  Armor rating, armor capacity and internal structure remain separate. Plates
  should not secretly multiply all three; start with capacity and mass only.
- **Heat:** a visible short-term burst budget. Firing adds heat, passive cooling
  removes it; a hot walker must ease off until it recovers. A brief combined
  chaingun/SRM burst is stronger than sustainable fire, so timing matters during
  direct control. Start with a soft fire-rate limit and clear warning rather than
  surprise shutdown or permanent damage. Exact thresholds need playtesting.
- **Loader / equipment:** retain the existing missile replenisher as the owner
  of pack restoration cadence. A faster loader competes with cooling or utility
  equipment for one subsystem slot. It cannot change missile capacity, projectile
  damage, and cooldown as an undocumented bundle. Guns do not gain a second ammo
  economy simply to make the slot relevant.

## Stock frame and the fitting budget

Every chassis now carries a stock frame — industrial engine, factory plating,
factory cooler — set below the old values on exactly the axes these internals
buy back: armor capacity (plating), move speed (engine), cooling (cooler). The
table is in `heavy-equipment-progression.md`. Each internal replaces one stock
part; none is a multiplier on the chassis.

- Engine output is a mass budget; every weapon and internal has mass, and a fit
  over budget is refused atomically. The engine also sets speed within the
  chassis's own band.
- Higher-tier weapons and internals are larger or heavier than the part they
  replace in the same role, so a light chassis cannot carry the top of every
  ladder. Footprints use the existing socket grid; the CORE socket hosts the
  engine, AMMO_RESERVE the heat sink, MINI_FAB the cooler or loader.
- `MechHeat` capacity and cooling become per-loadout values frozen from the
  installed internals; AI thresholds scale with capacity.
- Internals are learned as patterns (`mechs-nouns.md`, mech pattern) and built as
  finite assemblies like weapons.

## Authority and presentation

Campaign fittings validate mount, mass, output, finite stock and costs atomically.
Deployment freezes them. Battle owns current heat and cooling; player and AI fire
through the same heat admission rules. AI reserves burst headroom for useful
contacts and cools without abandoning mission assignments. Heat never bypasses
faction, visibility or friendly-fire checks. Direct-control handback preserves heat.

Show a small heat gauge near the controlled mech's weapon feedback, with sustained
and burst estimates in the lab's hover sheet. Engine/armor remain sealed and
read-only until their mechanics exist. Castle introduces each real system as it
ships; no narration production before the rules and tutorial text settle.

## Next fitting slice

The Hound baseline now has shared heat and independent cycler/loader clocks,
with held-fire, paced-burst, AI and handback evidence in `hound-heat`. Next add
one alternate cooling/loader choice and the fitting budget.
Only afterwards expose engine and armor swaps. Wider patterns should arrive as
explicit story/salvage/market rewards, not an automatic full-catalog unlock when
the fitting lesson ends. Keep earlier owned gear intact.

Acceptance includes save migration; identical heat costs for AI and player shots;
no ammunition or cooldown reset on takeover; useful cool-down behavior; atomic
failed fittings; and readable 1920 x 1080 / 100% lab and battle evidence.
