# Second Wind refit tree

Status: DRAFT — direction for owner review; nothing here is implemented.
Written: 2026-10-20

The Engineering tab today sells a short list of one-off yard orders: the
workshop chain (750 / 3,000 / 4,500), the second bay (8,000), three Mech Lab
berths (5,000 each), the bunkrooms (4,000), the distribution circuits (3,000)
and the mess (2,500) — about 46,000 credits in all, nearly all of it spent
before the mid-campaign. Only the mess and the workshop tooling change anything
past presentation and capacity. This doc proposes a long upgrade path for the
founding hull that soaks up credits for the whole campaign. Each purchase is a
**passive**, in the campaign layer or in battle. The ship starts below par,
repairs bring her to neutral, and upgrades take her past it.

It extends `company-view-nouns.md` (Engineering, the yard-order authority) and
`ship-interiors-nouns.md` (room program, form vs. state). It does not replace
them.

## Three bands per track

Every track reads on one scale the player learns once:

| Band | Meaning | Reads in Engineering as |
|---|---|---|
| **Deficit** | the battered start; a real, small penalty | red glyph, the penalty named |
| **Restored** | neutral: the numbers every other company and every evidence harness uses | no glyph (healthy rooms stay quiet) |
| **Improved I / II** | past neutral; a named bonus | upgrade pip(s) on the room |

Neutral is the ship's calibration point. Mission balance, `commanderEvidence`,
scenes and matchups all run at neutral, so a Deficit or an upgrade is a known
offset from tuned ground, not a new tuning surface. Only the campaign company
aboard the active converted Second Wind ever leaves neutral. Allies, enemies,
debug companies and fixtures do not, which is the rule the mess already follows.

**Deficits must be modest, and the opening must not pay for them.** Jangala and
Relief were tuned at neutral. Either the first deficits are campaign-layer only
(slower healing, costlier upkeep), or the battle-side deficits clear with the
cheap post-Relief orders that already exist. The second option is the
recommendation: the existing mess, bunkroom and circuits orders become the
Deficit → Restored step of their tracks, so their prices and gates stand.

## Core systems gate facilities

Today every order is gated only by story progress and a docked yard. The
proposal adds four **core systems**, the ship's own plant. Each has tiers, and
facilities require core tiers. The cores are the spine of the tree, and the
facilities are its branches.

| Core | Tiers (Deficit → Restored → I → II) | Gates | Its own passive |
|---|---|---|---|
| **Power** | Emergency → Distribution restored (the existing 3,000 circuits order) → Reactor overhaul → Auxiliary plant | anything with machinery or screens: range, simulator, briefing, sick bay II, workshop tooling | none of its own; power is the gate (keeps the circuits order honest: still no hidden combat buff) |
| **Life support** | Degraded scrubbers → Restored → Expanded → Closed-loop | anything people live in: mess, bunkrooms, gym, track, pool, rec | Deficit: WIA recovery +1 day. Upper tiers: supplies upkeep (see Fleet stats) |
| **Structure** | Sealed voids → Pressure-tested → Reinforced frames | reopens the sealed compartments the new facilities live in; reinforced frames gate the large ones (range, pool, track) | Reinforced: hull/armor via the conversion hull mod (vanilla-visible) |
| **Fabrication** | the existing workshop chain: Buried → Cleared → Repaired → Equipped, plus a new Precision tier | boat and mech production rooms | existing reclamation and the 20% supplies discount; Precision adds a part-quality or time benefit |

Gating rule: a facility tier names the core tiers it needs. Engineering shows a
locked facility with its missing core named and a link to that order, so the
tree reads as "what do I buy next to reach the range". The alternative was a
power *budget*, where facilities draw load and the player picks which to run.
That makes a paid room sit dark. It is listed as an open question, not
recommended.

## Facilities and their passives

Effects are grouped by where they land. The figures are starting proposals
for a balance pass, not tuned values.

### Battle layer (frozen at launch, shown on briefing and squad card)

| Facility | Deficit | Restored | Improved I | Improved II |
|---|---|---|---|---|
| **Mess / galley** (exists) | field rations: morale recovery −15% | sealed mess restored: neutral | proper galley: +15% | galley + cold store: +25% (today's shipped value) |
| **Firing range** (new compartment) | — (absent is neutral) | lanes open: enables range trials aboard (today refused: "a ship without a range") | target machinery: infantry primary accuracy +3% | simulator lanes: +6% |
| **Gymnasium / indoor track** | — | open: neutral | conditioning: infantry move speed +3% | track loop: +5%, and recover from suppression/stagger faster |
| **Briefing room** | — | open | plot table: first action against a new threat faster (the experience-tier axis, as a flat company step) | holo-tank: mission intel shown in the briefing (enemy composition / garrison counts) |
| **Sick bay** (new) | no sick bay: none | open: neutral | surgery: a share of would-be KIA come back WIA | trauma ward: larger share; stacks with Field Medicine, not with itself |

### Campaign layer (outside battle)

| Facility | Restored | Improved I | Improved II |
|---|---|---|---|
| **Sick bay / rehab pool** | neutral heal time | WIA heal −25% (multiplies with Recovery Planning, floored) | hydrotherapy pool: −40%, and a crippled-wound class heals instead of discharging |
| **Rec center / lounge** | neutral | replacements arrive at the top half of their aptitude band | shore-leave deck: the replenishment fee drops |
| **Wardroom / staff college** | — | officer XP +15% | +30% |
| **Boat bay services** | — | grounded-boat recovery one day faster | deck crane: boat and Hermes material bills −15% |
| **Holds / bonded stores** | — | +cargo via the conversion hull mod | shielded stores |

### Fleet stats (vanilla, through the conversion hull mod)

`MercenaryCompanyConversion` is already a hull mod on the variant. Its
`applyEffectsBeforeShipCreation` can carry the tree's fleet-stat effects. The
base game then shows them on the refit screen and applies them everywhere
without our code. Candidates: supplies per month (Deficit: +25% for a battered
hull; Closed-loop life support: −15%), CR recovery, burn level (drive refit),
sensor strength, cargo. Each is one `stats.*Mod().modify*` keyed by a saved
receipt.

## What this must keep true

- **Legibility law** (`progression-nouns.md`). A squad's fighting quality is
  fully determined by what the player can see. A ship passive is a
  company-wide, named modifier like Tactical Cover. It is listed in Engineering
  and on the briefing, and frozen into `CampaignMarineDeployment` at launch, the
  way `CompanyShipRestoration.moraleRecoveryAboard` already is. It never varies
  per squad. The frozen value should become one *ship-condition* record, not a
  growing argument list.
- **An upgrade changes the room or it is not an upgrade** (law 5). Every tier
  has visible deck change: lanes and traps, a pool, machinery, lights.
  Upgrades that only change a number go in the hull mod's vanilla stats, where
  the refit screen shows them.
- **The plan is fixed through restoration.** New compartments are reserved at
  conversion as sealed voids in `DeckArrangement.SECOND_WIND`, the way the lance
  bay was. Paying for a repair never moves a room. The Second Wind is a frigate,
  and `programFor` grants a range, sick bay, lounge and gym only from destroyer
  size. These rooms are the founding hull's exception, like her lance bay and
  54 billets, not a change to the general program.
- **Receipts live on the hull.** Like the existing orders, a receipt is saved
  under the hull's id. A company that moves to another hull gets that hull's
  ordinary facilities and loses these passives for future launches, and keeps
  the receipts for when it comes back.
- **One order, one review.** Each tier is its own reviewed yard order:
  inspection spends nothing, payment rechecks gate, funds, home and dock.

## Credit curve

The current total is ~46k. A full tree of 4 cores × 3 tiers and ~9 facilities
× 2–3 tiers is about 35 orders. As a shape: Restored-tier orders at
2.5–8k, Improved I at 10–20k, Improved II at 25–50k, for roughly 350–450k
in all. The price ladder must be anchored to measured campaign income
(credits per contract after upkeep), so that the Improved II tier of any one
track takes several missions of savings and the whole tree is a late-game goal,
not a checklist. That measurement comes first in the balance story.

## First slices

1. **Deck capacity survey.** Measure how much sealed floor the 106×79 Second
   Wind envelope can actually give up for range, gym/track, sick bay, briefing
   and pool, and reserve them in the arrangement. This decides how many
   facilities exist at all.
2. **Ship-condition record and the band model.** Replace the single morale
   float with one frozen record. Re-base the mess onto the four bands (owner
   decision below). Add the Engineering card's band display and the briefing
   line.
3. **Core gating.** Power and Life support tiers above the existing orders,
   with locked-facility explanation in Engineering.
4. **Firing range**, the first new facility: compartment, range trials aboard,
   accuracy tiers.
5. **Hull-mod fleet stats** (supplies, CR) for the campaign-layer deficit and
   its recovery.
6. Remaining facilities, one story each.

## Open questions for the owner

1. **Re-base the mess?** Today's shipped curve is sealed = neutral, restored =
   +25%. The band model makes sealed −15% and moves +25% to Improved II. That
   is harder early and the same ceiling late.
2. **Battle deficits at all, or campaign-only?** The safest form keeps every
   battle deficit cleared by the cheap post-Relief orders.
3. **Gate or budget?** Tier gating (recommended) vs. a power budget the player
   allocates.
4. **Other hulls.** Second Wind only (recommended for now), or a general
   facility-fit model any company hull can buy into later
   (`company-ship.md`'s Fit axis)?
5. **Which crazy rooms are in?** Pool (as rehab), indoor track (with the gym),
   rec center: all are argued above as healing, conditioning and recruitment.
   Any to cut or add (chapel, brig/interrogation for intel, hydroponics for
   upkeep)?
