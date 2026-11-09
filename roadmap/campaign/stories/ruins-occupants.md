# Ruins occupants

Status: IN PROGRESS — the draw (1), the Rav (3), derelict security (4) and the
briefing (5) built; an empty site (2) fields no one, and its puzzle is
`ruins-works.md`'s. Slice 5 of `ruins-expedition-fork.md`.

Written: 2026-11-09

Read `ruins-expedition.md` (Occupants, Briefing) first, then
`colony-breach-nouns.md` (Reservoir) and `derelict-lore-guide.md`.

## Goal

Every ruins site has its seeded occupant: nobody, scavengers, the Rav or
derelict security. The briefing names it truthfully through survey readings
of the occupant's own kind.

## Built

- **The draw** is `RuinsOccupants.draw`, a pure function of the sector seed,
  market id, grade, reservoir fact and system tags, returning a
  `RuinsOccupant`: `NOBODY`, `SCAVENGERS`, `RAV`, `DOMAIN_SECURITY` or
  `CONTINGENCY_SECURITY` (the derelict lineage is part of the occupant, so the
  briefing and the network read it directly). Tested in `RuinsOccupantsTest`.
- **The live reader** is `SectorRuinsOccupants.of(market)`: the save seed
  (`SectorBloomWorld.sectorSeed`), the reservoir fact
  (`RavBloomRules.hasReservoir` from the market id, ruins and farmland) and the
  system's tags. The mission factory and the briefing both read it there.
- **Carried to the battle**: `MarineRuinsCMD` passes it to
  `RuinsExpeditionMissionFactory.create`, which puts it on
  `Mission.ruinsOccupant`; `MissionLaunch` puts it on `RuinsBattleFixture`
  (serialized as `occupant`, read as scavengers when absent) and the fixture on
  `BattleSetup.createRuins`.
- **The seam**: `RuinsDefence.of(occupant)` decides a site's force, and
  `createPlaceholder` fields only that: the scavengers' crew, derelict
  security, the Rav's sleepers, or no one for an empty site. No ruins site gets
  the generic garrison, patrols, emplacements or air. The mission names the
  occupant's faction (none for nobody or the Rav) and the briefing reads the
  occupant's own pool. Tested in `RuinsDefenceTest`.
- **The briefing pools** are in `RuinsSurvey`, one per occupant through
  `poolFor`: nobody reads quiet, with a power hall, a dead seal, a collapse and
  a records system hinting at the works; the Rav read as cold, clustered
  biological lifesigns and seismic chatter; Domain security as fixed patrols,
  power draw and sweeps answering the ping; contingency security names
  Tri-Tachyon in its assessment and adds the codes and a lockdown reading. The
  `ruins-briefing` snapshot suite renders a briefing per occupant from its own
  pool.

- **The Rav** are `RuinsSleepers`: the grade's closed bores (two to five) sit
  in the deeper layers' side rooms and middles, deepest layer first and spread
  apart, held dormant, with the director run from the bores alone
  (`configureSwarmFromBores`: no mother, harvest or cohort). Each closed bore
  stirs with time below, with company gunfire it hears (16 cells) and, fast,
  with a breach under way; past its threshold (staggered per bore) it opens and
  surges. The breach opens every bore still closed and lifts the director's
  pressure share from 0.6 to full, so the egress is the peak. A bore's tier is
  capped by grade (`SwarmBores.capTier`): runners only at scattered, brutes and
  spitters from widespread, the maw's heavy strains at extensive and vast. The
  mission names no faction for the Rav. Rule tests: `RuinsSleepersTest`.
  Agents never run these maps to judge them: the owner plays and balances
  them against a player (owner, 2026-11-09). Fixtures for the owner:
  `ruins-rav-<grade>-v1.json`, `ruins-nobody-scattered-v1.json`.

## Slices

1. **The draw.** (Built.) A pure function from the sector seed, the market id, the ruins
   grade, the reservoir fact and the system's theme tags to an occupant and a
   lineage, with the starting weights in `ruins-expedition.md`. It reads nothing
   that changes over a campaign and stores nothing. Unit-test that it is stable
   across calls, that only a scattered site draws nobody, that a reservoir
   world draws the Rav one time in two, and the theme modifiers.
2. **Nobody.** (Its side built: no one, proved in `RuinsDefenceTest`. The works
   chain is `ruins-works.md`'s.) An empty site plays the expedition with no
   opposing side, its way to the vault through the works chain.
3. **Rav.** (Built; the bore-opening rule is unit-tested where it is decided.)
   The swarm director's ruins profile: closed bores in the deeper layers that
   noise and time open, no mother bore, no harvest, no cohort, late strains
   only at the larger grades, pressure peaking on the egress. It never touches
   the campaign bloom or the reservoir's rest.
4. **Derelict security.** Built: a derelict occupant's `createRuins` calls
   `DerelictSecurity.install` in place of the generic squads and
   `ExtractionDefenderCommand`, and the mission names the occupant's faction
   (`derelict-security-network.md`, What is built).
5. **Briefing.** (Built.) The survey readings pool for nobody, the Rav and derelict
   security (lifesigns; movement and power; quiet with structure and power
   hints), seeded per site, with the contingency lineage's own reading. The
   scavengers' pool is `ruins-expedition-battle.md`'s. Each pool is a
   `RuinsSurvey.Pool` with its case in `RuinsSurvey.poolFor`.

## Constraints

- The occupant is never stored, so a retry meets it again at full strength.
- Disclosure is the assessment, a band and true readings, never the layout.
- An expedition never wakes, feeds or ends a bloom.

## Done

Each occupant is played by the owner on at least one grade. Fold the rules into
`ruins-expedition.md`, add a row to `shipped.md`, and delete this story.
