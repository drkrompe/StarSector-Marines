# Mech internal systems

Status: Proposed — design only; no new live stats or recorded dialogue.

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

## Next implementation slice

Prototype heat on one Hound and its baseline weapons first. Compare sustained AI
fire, held player fire, timed bursts and handback using deterministic firing
fixtures. Then add one alternate cooling/loader choice and the fitting budget.
Only afterwards expose engine and armor swaps. Wider patterns should arrive as
explicit story/salvage/market rewards, not an automatic full-catalog unlock when
the fitting lesson ends. Keep earlier owned gear intact.

Acceptance includes save migration; identical heat costs for AI and player shots;
no ammunition or cooldown reset on takeover; useful cool-down behavior; atomic
failed fittings; and readable 1920 x 1080 / 100% lab and battle evidence.
