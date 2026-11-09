# Ruins occupants

Status: PLANNED — owner direction; nothing built. Slice 5 of
`ruins-expedition-fork.md`.

Written: 2026-11-09

Read `ruins-expedition.md` (Occupants, Briefing) first, then
`colony-breach-nouns.md` (Reservoir) and `derelict-lore-guide.md`.

## Goal

Every ruins site has its seeded occupant: nobody, scavengers, the Rav or
derelict security. The briefing names it truthfully through survey readings
of the occupant's own kind.

## Slices

1. **The draw.** A pure function from the sector seed, the market id, the ruins
   grade, the reservoir fact and the system's theme tags to an occupant and a
   lineage, with the starting weights in `ruins-expedition.md`. It reads nothing
   that changes over a campaign and stores nothing. Unit-test that it is stable
   across calls, that only a scattered site draws nobody, that a reservoir
   world draws the Rav one time in two, and the theme modifiers.
2. **Nobody.** An empty site plays the expedition with no opposing side, its
   way to the vault through the works chain (`ruins-works.md`).
3. **Rav.** The swarm director's ruins profile: closed bores in the deeper
   layers that noise and time open, no mother bore, no harvest, no cohort, late
   strains only at the larger grades, pressure peaking on the egress. It never
   touches the campaign bloom or the reservoir's rest. `sceneEvidence` for the
   bore-opening rule; a `commanderEvidence` fixture on the vast grade.
4. **Derelict security.** Wire the occupant to `derelict-security-network.md`
   once that story's director and units exist.
5. **Briefing.** The survey readings pool for nobody, the Rav and derelict
   security (lifesigns; movement and power; quiet with structure and power
   hints), seeded per site, with the contingency lineage's own reading. The
   scavengers' pool is `ruins-expedition-battle.md`'s.

## Constraints

- The occupant is never stored, so a retry meets it again at full strength.
- Disclosure is the assessment, a band and true readings, never the layout.
- An expedition never wakes, feeds or ends a bloom.

## Done

Each occupant is played by the owner on at least one grade. Fold the rules into
`ruins-expedition.md`, add a row to `shipped.md`, and delete this story.
