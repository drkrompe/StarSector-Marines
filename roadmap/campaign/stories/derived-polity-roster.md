# Derived polity roster: the colony's own troops, made from its own economy

Status: IN PROGRESS

Written: 2026-09-02

Updated: 2026-09-02 — slices 1 (Derivation), 2 (Numbers), 3 (Persistence,
system, registration) and 4 (The panel) shipped; only the live pass remains.

Read `polity-ground-doctrine.md` (the model and its laws — this story is its
implementation), `meta-progression.md` (the company-and-polity boundary),
`campaign-battle-bridge-nouns.md` (the one resolve path), `progression-nouns.md`
(cards, access tiers, the Common floor), and the Sides section of
`ai-nouns.md` (the allied garrison this feeds) before implementing.

## Goal

The allied garrison ships today with a placeholder: its headcount is a crude
reading of market size and its kit is whatever roster the registry falls back
to, because nothing is registered under the player faction. After this story
the polity has a **ground doctrine of its own** in the sense
`polity-ground-doctrine.md` gives it: a `GroundRosterProfile` derived from the
colonies' live economy and the kit the company has released to them, rebuilt
as the economy moves, registered under the player faction id, and reached by
the battle through the registry's one resolve path. Its headcount is the
market's own ground-defence strength, less the company's stationed
contribution so a detachment never counts twice. Three zero-sum doctrine
points shape it, edited from a small panel in the colony's Marine Ops dialog.

## Shape (decisions made; the design doc owns the reasons)

- **Released kit** is a set of equipment template card ids persisted on
  `CampaignState` (a card registry and an id table, in the stationed-strength
  style), never on the Armory. A release is permanent and never revoked.
  Every Common-tier card is released by construction; Advanced and Prestige
  cards are released one at a time by an explicit act. A release grants the
  definition only; the Armory is untouched.
- **Ground production quality** is a four-step ladder read off the polity's
  best producing market — none, basic (Heavy Industry), advanced (Orbital
  Works), advanced with no deficits — pulled down one step by a deficit in
  supplies or heavy armaments. The rule is pure; the read is one adapter.
- **The derivation is pure.** Inputs: the released cards, the quality step,
  the doctrine points. Output: one profile whose bulk tier is militia in the
  released primaries, whose grade tables come from the quality step (and only
  from it — doctrine may tighten, never admit), whose armour is the released
  patterns capped at the tier the quality step can make, whose specials are
  the released specials beside "none", and whose heavy support is one mech
  variant when the heavy-support point is spent and the quality step can
  fabricate one. The elite tier is the bulk tier one grade band up.
- **Doctrine** is three ints on `CampaignState` — quality, numbers, heavy
  support — each 0 to 2, summing to at most 3. Quality tightens the grade
  table toward its top and raises the elite share; numbers multiplies the
  garrison headcount; heavy support admits the mech lance. No fourth axis.
- **Registration** is a campaign system that rebuilds the profile every day
  from the live economy and installs it under the player faction id,
  replacing the previous derived profile; a rebuild can also be asked for
  immediately by the panel. The registry gains a replace-derived operation
  so a daily rebuild never trips the duplicate-faction check.
- **Numbers.** `TargetProfile` gains the market's ground-defence strength
  and the company's stationed contribution there, both read at resolve time,
  so `AlliedGarrisonSize` becomes a reading of defence strength net of the
  company, with vanilla's own stability scaling already inside the number.
  The numbers doctrine reaches the battle as a multiplier on the mission,
  set by the polity defence factory; a patron's garrison defence uses one.
- **The panel** is a second polity row in the colony's Marine Ops dialog:
  the three steppers with points remaining, what the derivation currently
  yields, this market's headcount, and the release list.

## Slices

1. **Derivation.** A public builder on `GroundRosterProfile`; the quality
   ladder and its pure rule; the doctrine value type; the derivation itself.
   Unit tests pin law 3 (no grade the step cannot make, whatever doctrine
   says), the Common floor, the armour cap, the heavy-support gate, and that
   the same inputs derive the same profile.
2. ~~**Numbers.**~~ SHIPPED. `TargetProfile` carries `groundDefence` and
   `stationedStrength`; `StationedStrength.totalAt` reads the company's own
   contribution back off the market's modifiers; `AlliedGarrisonSize` is
   defence-strength arithmetic at 45 strength per fireteam, floor 1, cap 8; the
   mission carries `alliedGarrisonStrengthMult`, which
   `PolityDefenceMissionFactory` sets and `BattleSetup` threads. The multiplier
   argument at `MarineOpsContext.polityDefenceMissions` is still the literal
   `1f` slice 3 replaces.
3. ~~**Persistence, system, registration.**~~ SHIPPED. `CampaignState` carries
   the released-kit table (`equipmentTemplateRegistry`,
   `releasedKitTemplateId`, `CampaignTable.RELEASED_KIT`) and the three doctrine
   ints, both with legacy-load guards; `ReleasedKit` and `PolityDoctrineLedger`
   are their only accessors; `ProductionSignals` / `MarketProductionSignals` /
   `VanillaProductionSignals` split the pure ladder from the one live market
   read; `GroundRosterRegistry.replaceDerived` makes a rebuild a replacement;
   `PolityRosterSystem` rebuilds daily and on game load; and
   `MarineOpsContext.polityDefenceMissions` now passes
   `PolityDoctrineLedger.read(state).numbersMultiplier()`.
4. ~~**The panel.**~~ SHIPPED. `PolityDoctrineScreen` and
   `polity-doctrine-screen.mlx` are a second polity row beside the posting one,
   reached at `ScreenId.POLITY_DOCTRINE`. Three steppers refuse a fourth point
   and refuse a third on one axis; every press writes through
   `PolityDoctrine.of` and rebuilds the roster at once. The middle column says
   what the derivation currently yields — production step, issued grades,
   armour patterns, the lance, and this market's own headcount net of the
   company's posted detachment — and the right column releases one owned card
   at a time, irreversibly, with the Common floor named rather than listed. The
   whole panel is built from a static props seam taking stated inputs, so it
   projects with no sector; `GroundRosterProfile.Issue` gained read-only
   `grades`/`armorPatterns` so the summary reads the derived tables rather than
   re-deriving them. `polity-ground-doctrine-wide.png` is its `ui` snapshot.
5. **Live pass.** Found a colony with and without Heavy Industry, release one
   Advanced card, spend the points three ways, and read the garrison that
   stands at a spawned raid.

## Acceptance

- A polity with no industry fields Surplus-heavy Common militia; one with
  Orbital Works and no deficits fields a Masterwork tail; a supplies deficit
  pulls it down a step.
- A released Advanced primary appears in the derived roster and an
  unreleased one never does; the Armory is unchanged by a release.
- The derived profile resolves under the player faction id and the fallback
  profile no longer stands in for it.
- A market's allied headcount falls by exactly the company's stationed
  contribution there.
- Doctrine points cannot exceed three, and a point spent on heavy support
  puts a mech variant in the profile only where the quality step allows.

## Out of scope

- The absent-case multiplier on the ground-defence stat
  (`polity-ground-doctrine.md`, optional).
- MRB scrutiny of an armed polity (`meta-progression.md`).
- Any cost to a release beyond the industry gate.
