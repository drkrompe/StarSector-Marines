# S2A — Anti-materiel rifle

> Pick a hardened target apart without throwing an explosion into the marines
> fighting beside it.

Status: PLANNED — follows the marine-secondary weapon migration in
`w3-remaining-catalogs.md` and establishes the shared special-equipment
identity used by S2B–S2D.

Written: 2026-08-23

Read `progression-nouns.md` and `moddable-weapons-nouns.md` before
implementing this story.

## Tactical identity

The **anti-materiel rifle** is a limited-ammunition, long-aim direct-fire
special that deals precise damage to turrets, drone hubs, light vehicles, and
heavy mechs without splash. It occupies the same single special-equipment slot
as a rocket launcher while the marine retains their ordinary primary.

Its contrast with adjacent kit is the feature:

| Kit | Advantage | Cost |
| --- | --- | --- |
| Rocket launcher | Immediate splash, strong burst damage, remote wall damage | Friendly-fire danger, visible projectile, scarce tubes |
| Anti-materiel rifle | Precise hard-target damage near friendlies, more deliberate shots | Requires clear direct line, long stationary aim, no breach or area damage |
| DMR primary | Persistent anti-personnel precision with ordinary ammunition cadence | Does not receive the special's hard-target multiplier |

Initial balance should start around four heavy rounds. Exact damage, aim time,
range, and hardened-target multiplier are playtest values, not design laws.
The shot may damage ordinary infantry if it physically intercepts them, but AI
does not spend scarce ammunition on soft personnel as its intended target.

## Shared special-equipment identity

This is the cheapest additional special and therefore owns the minimum shared
identity seam. A billet and marine carry an optional stable special-equipment
id. A special-equipment definition declares a typed activation behavior and,
when it fires a conventional weapon, references the weapon id that owns the
round. The rocket and anti-materiel rifle are direct-fire activations; smoke
and satchels later add utility activations without pretending their clouds or
placement channels are `WeaponDef` shots, while fragmentation grenades add an
arcing weapon-like activation.

The current `MarineSecondary` name may remain as a compatibility handle until
`w4-retire-enums.md`; new player-facing copy says **special equipment**. This
story must not create a second stat table beside the weapon registry.

## Battle execution

- The carrier braces and becomes stationary through a readable aim window.
  The target is locked at commitment; death, invalid identity, or lost legal
  firing solution cancels the shot rather than redirecting it invisibly.
- The physical round uses the shared ballistic resolver, including walls,
  doodads, and intervening units. It has no AoE and negligible structural bite;
  it does not make doors.
- Ammunition is consumed when the round is fired. Mission resupply remains out
  of scope unless a separate supply feature grants it.
- Telemetry records the special use, the heavy round, resolved damage, and kill
  through the existing shared seams.

## Gameplay-AI integration

### Use policy

- Replace the rocket-only branch with item-specific special activation policy.
  An anti-materiel carrier considers only an identified, visible hardened
  target inside range with a clear direct-fire solution.
- A normal engagement may start the brace from a safe firing position. An
  opportunity use while moving is allowed only when stopping does not violate
  a mission-priority channel, active retreat, or the moving half of a bound.
- Target suitability prefers a target the primary cannot efficiently answer,
  then favors high threat and low remaining heavy-shot count. Ordinary infantry
  never wins this selection merely because it is close.
- Projected-damage reservation counts squadmates already bracing and committed
  heavy rounds, so a fire team does not spend four shots on one nearly-dead
  turret. This is the direct-fire analogue of `shouldCommitRocket`, not a
  second unrelated reservation system.
- The same policy is faction-neutral. Enemy availability is decided by
  defender loadout content, not by a separate behavior implementation.

### Counterplay and belief

- The muzzle report is a localized direct-fire audio event. A hostile squad
  that hears or sees it updates ordinary contact belief and may break line of
  sight, suppress the firing position, or flank; it does not gain omniscient
  knowledge of the carrier.
- The long brace is interruptible by the same death and legality changes that
  cancel other special aims. Incoming pressure does not invent a special panic
  rule; existing survival goals decide whether the squad remains committed.

## Campaign and presentation integration

- The armory owns recipe, stock, fabrication cost, assignment, and recovery.
  One rifle supplies one billet; cards remain reusable only as far as physical
  stock permits.
- Add a Fire Support or Anti-Materiel starter/library card with one special
  carrier, and make its unlock explicit in `s6-unlock-ladder-expansion.md`.
- The battle HUD shows a distinct AMR abbreviation, remaining rounds, and the
  brace pose. It must not reuse the rocket's projectile or explosion language.

## Acceptance

- A marine retains their primary while an AMR occupies the one special slot.
- A fired heavy round resolves through shared direct-fire collision, deals no
  splash, and receives its authored hardened-target behavior from one source.
- AI does not target ordinary infantry with the special, does not stop an
  active retreat/bound/plant to use it, and does not overcommit lethal damage
  already reserved by squadmates.
- A hostile can react from honest sight or sound without learning an unseen
  carrier's exact identity.
- Player and defender carriers pass the same focused AI scenarios.
- Armory stock, cards, deployment freeze, HUD ammunition, telemetry, save
  compatibility, and unlock reachability all recognize the new special id.
