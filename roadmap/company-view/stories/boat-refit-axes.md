# Boat refit axes: the Hermes as the company's boat

Status: IN PROGRESS — slices 1 (campaign simplification), 2 (catalog, six sockets, frame budget) and 3 (seats, deboard, load) shipped; slices 4–6 open
Written: 2026-10-26
Updated: 2026-10-26 — owner answered every picks question: cycle, lift, shield and flux, weapons, saves, patterns and ship changes.

The standing model is in `company-view-nouns.md` (Boat Deck, Boat spare, Fitting)
and `air-nouns.md` (fitted boat, Lift, the rotation). This story replaces the
three fitting slots with six refit axes and makes the Hermes the boat they are
built around. Nothing here is shipped. The owner's answers are recorded on the
boat refit picks page (`tools/picks/pages.md`) and summarised at the end.

## Why

Today a boat is upgraded on three slots (plating, drive, lift) plus its door
guns, and the Hermes, the boat Second Wind starts with, takes none of them: its
frame "has no upgrade path", and the only way forward is Secondhand Wings, which
trades both Hermes for two Aeroshuttles. That makes the company's first boat a
dead end and its first boat upgrade a pattern swap rather than a refit.

Owner direction (2026-10-26): scrap the Aeroshuttle conversion and build the
upgrade equipment tiers around the Hermes. Upgrades are reworked around six
ideas: speed, lift, seats, weapons, armor and shield, and flux. Upgrading or
swapping boats is no longer part of the campaign.

## The company flies two Hermes

- **The campaign's boats are two Hermes, for the whole campaign.** The company
  repairs the two founding wrecks and refits them; it never buys, converts or
  trades for another pattern. Getting better boats means refitting these two.
- The Hermes takes every axis. `BoatFitting.supports` and `BoatGun.supports`
  stop refusing it; "no upgrade path" copy goes.
- **Tier 1 is the Hermes as the base game builds her**, at battle scale: her
  hull, speed and four seats as the mod flies her today, plus what the mod has
  so far left out of her hull table and spec. She has three small turret slots
  and an omni shield (120° arc, upkeep 0.5, efficiency 1.0) over a 1,000 flux
  pool dissipating 80 a second, scaled the way warships scale hull figures
  (`WarshipFlux`). Each tier above is a step from those numbers; she stays the
  same airframe, sprite and hull id.
- Tiers are parts, kept as boat spares (`company-view-nouns.md`): built from a
  bill into stores, dragged on and off sockets, and moved freely between the
  two boats.
- **Secondhand Wings is deleted outright.** Mara's offer, its notice, its modal,
  `SecondhandWings`, `SecondhandWingsCampaign`, the deck's conversion record and
  the hull's Aeroshuttle adapter all go. The second bay keeps its own
  restoration in Engineering (`CompanyBayRestoration`), already a separate
  order, so Second Wind reaches two Hermes by restoring the bay and repairing
  the second wreck.
- Other patterns (Aeroshuttle, Valkyrie, the bus-class freighters) remain
  employer and mission transports with their pattern numbers. The refit ladder
  is the company's Hermes's.
- **The two Hermes belong to the company and move with it to any ship**
  (owner). A change of ship never leaves a boat behind and never reconciles the
  deck against the new hull's pattern: `BoatDeck.reconcile`'s left-behind path
  and `LeftBehind` go, and the berths a hull offers only say where the boats
  stand aboard her. A hull with fewer berths than boats still carries both; a
  boat with no berth is stowed, flies like any other, and is not drawn in the bay.

## The six axes

Each axis is a socket kind on the boat doll, with a yard-standard tier 1 part
that is free and never stored. Every other part has a **weight** against the
Hermes frame's rating (below).

1. **Speed** (drive socket). How fast she gets in, out and round again. Drive
   tiers scale forward speed and both accelerations, as the drive does today,
   and also **shorten the deboard cadence**: a better drive comes with a quick
   ramp, so she sits on a hot landing zone for less time. The deck crew's
   turnaround stays paid in hand-seconds (`air-nouns.md`, "A turnaround is a
   window"). Drive tiers are also what carries a heavy load at speed (Lift).
2. **Lift** (hold socket, its own part). The heaviest machine class she
   carries: none at tier 1, then Light (Hound, heavy APC), Medium (Warden,
   escort APC, militia technical) and Heavy (Bulwark, breakthrough tank).
   **Lift never costs seats** (owner: the company has only two boats). A
   sortie is still one kind: a squad load or exactly one machine
   (`air-nouns.md`, Lift). Every consumer still asks `LiftAdmission`.
3. **Seats** (cabin socket, new). How many marines a sortie lands: 4, then 6
   (a half-squad), then 8 (two fire teams). Today seats are the pattern's
   `capacity` and `FittedBoat` delegates them untouched; the cabin makes them a
   fitted number. More seats means fewer trips to land a squad.
4. **Weapons** (one socket per turret slot). The Hermes hull already has three
   small turret slots: two forward quarters (45° either side, 150° arcs) and one
   astern (210° arc). The mod flies her with one gun today; all three become
   sockets, and the player chooses what goes on each. Three families:
   - **Ballistic**: the door guns (heavy MG, Arbalest, grenade launcher,
     Hephaestus).
   - **Energy**: new boat guns that fire on flux and need no ammunition.
   - **Missile**: new pods with finite rounds and no flux, a few volleys a
     sortie for a hard opening strike on the run in.
   **Mount rules bend for boats** (owner): the hull's slots are small ballistic
   turrets, and a boat socket takes any family regardless. What limits a socket
   is the frame's weight, not the slot's type or size. A slot's position and
   arc stay the hull's, so the stern gun still only covers the stern.
5. **Armor and shield** (plating socket, emitter socket). Plating tiers scale
   the hull and its armour skin, as today. The **shield** tries to block
   incoming fire: raised, it turns a hit from inside its arc into hard flux
   instead of hull damage. Emitter tiers widen the arc and improve efficiency
   (less flux per point blocked). It raises itself when fire is near, as a
   warship's does in automatic mode (`WarshipShield`).
6. **Flux** (power socket). Capacitors and vents: flux capacity and
   dissipation. **Flux is one shared pool for the guns and the shield**
   (owner): every gun but a missile pod builds flux as it fires, the raised
   shield draws upkeep, and blocked shots add hard flux. A full pool
   overloads: the shield drops and the guns stop until it vents, as a
   warship's does. So a boat that fights its way in has less shield left for
   the landing.

**Weight, loads and the cycle.** A boat flies slower with weight aboard: a
machine slows her by its lift class, and a full cabin slows her a little. Once
she has dropped her load she is lighter, so **the run back is faster than the
run in** and she cycles round sooner. Drive tiers win the loaded speed back.

**The frame budget.** Every part above tier 1 has a weight, and the Hermes
frame has a rating, as a mech's parts are weighed against its engine
(`MechFrameBudget`). A refit over the rating is refused. The trade is the
point: lift, and the drive that keeps a loaded boat fast, take weight that
armour, shield, flux and guns cannot use. The Boat Deck shows the frame as a
meter, the way the Mech Lab shows the engine budget.

## What the battle reads

`FittedBoat` is still the only battle noun, frozen at launch. It gains seats,
lift, a deboard cadence, a weight-to-speed rule, a gun per turret slot, and a
shield and flux pool. The air systems read them from the frame; nothing in the
battle learns about the campaign.

- Seats move from `type.capacity` to the frame in `AirSystem` and
  `LiftQueueReadout`, and the deboard cadence from `type.deboardInterval`.
- Speed is recomputed from what is aboard when the load changes: on boarding
  and after each deboard, so the run in is slower than the run out.
- A boat's guns mount on all three of the hull's turret slots
  (`TurretSlotResolver`), each with the family the player chose, whatever type
  the slot names.
- Flux and shield reuse the warship's rules (`WarshipFlux`, `WarshipShield`)
  at boat scale rather than a second model: one pool fed by gunfire, shield
  upkeep and blocked hits, draining at dissipation, overloading at capacity.
  An anti-air hit inside a raised shield's arc goes to flux, not the hull.
- **A boat's shield is drawn with the shield dome's shader** (owner;
  `ShieldDomeShader`, `ShieldDomeRenderSystem`), not the warship's sprite
  shield: the bright rim and hex field, cells lit as the pool fills, a ripple
  where each blocked round lands, the collapse on overload and the rim
  re-forming. A boat's shield is an arc, so the shader gains an arc mask (its
  facing and width as uniforms, the dome being the full-circle case) rather
  than a second shield effect. It keeps the dome's fail-soft flat drawing and
  its side colours, and needs `shaderEvidence` and a `SnapshotSuite` look shot.

Fixtures keep writing part ids, never the frame.

## Saves

No old saves need to load (owner). The work deletes compatibility code rather
than adding any: Secondhand Wings' purchase record and adapter, the deck's
left-behind record, and the fitting slots' legacy readings, go without a
migration.

## Still to author

- Tuning the part list (`BoatFitting`, `BoatGun` weights, `BoatFrame`): first
  numbers are in the catalog; they want a matchup evidence run once slices 3
  and 5 make them bite in battle.
- The energy guns and missile pods themselves: names, turret structures and
  art, against the turret catalog.

## Slices

Each slice is a green, integrable commit chain.

1. **Campaign simplification.** Delete Secondhand Wings (offer, notice,
   modal, model, campaign hook, the deck's conversion record, the hull's
   Aeroshuttle adapter, strings, tests, snapshot suite). The two Hermes move
   with the company: `BoatDeck.reconcile` stops leaving boats behind or
   changing pattern, `LeftBehind` goes, and a boat with no berth is stowed.
   Independent of the rest.
2. **Catalog, sockets and frame budget.** The Hermes takes every part. Sockets
   for drive, hold (lift Light to Heavy), cabin (seats), plating, emitter and
   power, each part with tiers, a weight and a bill; the frame rating; the
   workshop refusing an over-budget fit; the Boat Deck doll, stores and a
   frame meter. `FittedBoat` carries the new parts but the battle reads only
   what it reads today.
3. **Seats, deboard and load in battle.** `FittedBoat` gives seats, deboard
   cadence and speed for what is aboard; `AirSystem` and `LiftQueueReadout`
   read them. Needs slice 2.
4. **Three turret slots and weapon families.** All of the hull's turret slots
   become gun sockets; energy guns and missile pods join the door guns; mount
   type is bent for boats. Needs slice 2.
5. **Boat flux and shield in battle.** One pool fed by gunfire, shield upkeep
   and blocked hits, on the warship's rules; overload drops the shield and
   silences the guns. Needs slices 2 and 4.
6. **Shield arc in the dome shader.** An arc mask (facing, width) on
   `ShieldDomeShader`, the dome being the full circle, proven by
   `shaderEvidence` and a look shot; then boats draw their shield with it.
   The mask is in (`ShieldDomeDraw`'s arc, clean bright ends, the flat
   fallback cut to it); wiring boats to it needs slice 5.

## Owner answers (2026-10-26)

- **Budget:** a frame weight budget; follows from the lift answer.
- **Cycle:** drive tiers also speed deboarding; a boat is lighter once it has
  dropped its load, so it cycles back faster. Turnaround stays hand-seconds.
- **Lift:** a separate lift part up to Heavy, costing no seats; a load slows
  the boat, and drive tiers that cost frame weight fix it.
- **Shield and flux:** the shield tries to block incoming fire; blocked shots
  build flux; flux is one pool shared by firing weapons and the shield.
  Draw it with the shield dome's shader.
- **Weapons:** ballistic, energy and missile; the hull's existing turret slots
  are the sockets, the player chooses what goes on each, and mount-type rules
  bend to allow it.
- **Old saves:** none to support.
- **Other patterns:** parts transfer between the company's boats, but
  upgrading or swapping boats leaves the campaign; the company flies two Hermes.
- **Ship changes:** the two Hermes move with the company to any ship; none is
  ever left behind.

## Acceptance

- A new Second Wind company restores its second bay, repairs both Hermes and
  refits them on all six axes from spares; no screen offers another pattern.
- A refit over the frame rating is refused, and the frame meter shows why.
- Each axis changes the number the battle reads, shown by a unit test on
  `FittedBoat` per axis.
- In unit tests: a loaded boat flies slower than an empty one and a drive tier
  recovers it; a shield turns a hit into flux; gunfire and shield draw on one
  pool, and a full pool drops the shield and silences the guns.
- A Hermes mounts a different family on each of her three turret slots.
- Boat Deck previews at 1920x1080 show a Hermes at tier 1, one fitted to lift,
  and one fitted to fight.
