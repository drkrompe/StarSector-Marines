# Allied faction: a friendly side that is not the player's

Status: PLANNED

Written: 2026-09-02

Read `polity-ground-doctrine.md` (the first consumer), `fog-of-war-nouns.md`
(law 2 already anticipates allied contributors), `battle-render-nouns.md`
(allegiance is already a four-bucket reading with an unused ally bucket), and
`ai-nouns.md` before implementing this story.

## Goal

The simulation has two sides that fight, `MARINE` and `DEFENDER`, plus
civilians. Every friendly force that is not the player's company has been
faked as the player's: employer lift seats are player-rolled marines under the
player's faction, the opening ladder's local militia is the same, and the
civilian-rescue shelter guard is a `MARINE` militia squad with a flag that keeps
it out of the command pool. Those troops share the player's victory
conditions, the player's sight, the player's target acquisition, and the
player's casualty accounting, and differ only in whether a click can move
them.

An allied faction makes friends a real side: hostile to the player's enemies,
friendly to the player, commanded by its own commander or garrison authority,
counted in its own ledger, and read as `ALLY` on screen. The first producer is
the allied garrison of `polity-ground-doctrine.md`; the three fakes migrate
onto it.

## Shape

- **One new simulation faction**, friendly to `MARINE`, hostile to `DEFENDER`.
  Hostility is a relation read in one place, not a chain of `!= MARINE` checks.
- **Its own sight.** Allied units contribute to the player's picture under
  fog-of-war law 2, which was written for this; they do not contribute to the
  defender's.
- **Its own command.** Allied squads are never in the player's command pool.
  They plan under their own commander where one exists, and under the garrison
  posture where one does not, using the existing defender command
  architecture pointed the other way.
- **Its own ledger.** Allied casualties are recorded and reported but never
  reach the company's personnel outcome, and allied survival never decides the
  player's defeat. A mission's terminal check names which sides it counts.
- **Its own reading.** `Allegiance.of` maps the new faction to `ALLY`, and the
  four-bucket render contract does the rest.

## Slices

1. **The faction and its relations.** The enum value, the hostility relation,
   and a sweep of every `Faction.MARINE` and `!= MARINE` test in targeting,
   line-of-fire friendly checks, and the spatial index to read the relation
   instead. Unit tests pin that an ally is not a target and not friendly fire
   for either side.
2. **Sight and reading.** Allied fog contribution and the allegiance mapping.
   The perception-sweep and durability-bars evidence gain an allied unit.
3. **Command and terminal accounting.** Allied squads under garrison posture,
   excluded from the pool by faction; terminal checks take a side list.
4. **Migration.** Employer lift seats, the opening ladder's local militia, and
   the rescue shelter guard become allied. The shelter guard loses its flag.
   Opening-ladder and rescue evidence must read the same as before the
   migration, which is the acceptance for not having changed the battles.
5. **The allied garrison.** The producer: a protected market's own troops from
   its faction roster, sized and kitted as `polity-ground-doctrine.md` says,
   placed by the existing defender allocation under the allied faction. First
   used by the polity defence.

## Acceptance

- An allied unit is never acquired as a target by the player's squads and
  never blocks their fire as a friendly-fire concern; a defender acquires it.
- An allied unit reveals fog to the player and not to the defender.
- No allied squad appears in the command pool, and an allied wipe does not end
  the battle in defeat.
- The three migrated forces play their existing scenes and opening fixtures
  with unchanged verdicts.
- A Garrison defence at a patron's market fields that faction's militia beside
  the company, and a polity defence fields the derived roster's.

## Out of scope

- A third hostile side, or allies hostile to each other. One friendly
  non-player side is the whole story.
- Player orders to allies. An ally that takes orders is a player unit.
- Any change to what the fakes carry today beyond the faction they carry it
  under.
