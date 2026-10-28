# Story — Vehicles fit the shared engines and weapons

Status: IN PROGRESS — slices 1 and 2 (fixture seam, engines) implemented; weapons, stance and turret art remain

Written: 2026-10-28

Read `mechs-nouns.md`, `convoy-nouns.md` and `company-view-nouns.md` (the Store) before changing this story.
It reverses part of an owner ruling in `heavy-equipment-progression.md` (Vehicles: variants and kits).

## Problem

A mech and a vehicle share one stores inventory, one set of learned patterns and, since the Store's three-section
reshuffle, one vocabulary of parts: Chassis, Engines, Weapons, Armor, Equipment. Only half of that is real. Armor and
kits already cross over: a vehicle's armor point takes the mech plating parts (`VehiclePlating`). Its engine and its
gun are still **sealed** (owner, 2026-10-24: "a vehicle's gun, plate, drive and bay are sealed"), so an engine or
weapon licence bought in the Store is a mech's alone, and the Store's own detail copy has to say so.

Owner direction, 2026-10-28: "Generally Mechs/Vehicle should share the same 'parts' and the player buys the license
to buy/build the part", and "plan what it takes to let vehicles fit the shared weapon and engine parts." This story
is that plan; it is played later, not now.

## Goal

A vehicle's **engine card** (`SocketId.CORE`) and **gun card** (`SocketId.ARMS`) become live: the company can fit a
learned, built engine or weapon from stores to a hull and strip it again, exactly as it fits plating today. An empty
card is the hull's own part, as an empty armor point is the hull's own plate. The Store, the pattern ledger, the
fabrication catalog and stores inventory need no new concept; the vehicle fitting path starts accepting what they
already hold.

Out of scope: shuttle weapon mounts (their own item, below), secondary mounts (`VehicleType.secondaryMounts`), the bay, the sight mast, a blade and active
protection stay sealed and stay hull variants (`heavy-equipment-progression.md`). New engines or weapons. Any change
to what a mech does with the same parts.

## What exists to build on

- **The plating precedent is the template.** Plating's whole path is: `CampaignVehicle.platingId` (null is the hull's
  own part, a plain serialised field, null-safe `readResolve`), the ownership arithmetic in `MechBay`
  (`installedInternal`, `loseVehicles`, `stowVehicle`, `berthVehicle`), the commands in `MechWorkshop`, the Mech Lab
  view model (`fitVehiclePlating`, the catalog, drop and strip rules), the doll (`VehicleDoll.sockets`,
  `MechLabDollCanvas.drawVehiclePlating`), the frozen `VehicleDeploymentSpec`, and `ConvoyService.spawn` turning it into
  `VehicleKit.Effect`. Engines and weapons take the same seams.
- **Learning and licences need nothing.** `MechPattern` learning is a flat set (`MechBay.knownPatternIds`) and
  `canFabricate` is per pattern, never per chassis. Once a vehicle accepts an engine or weapon, a bought licence
  already makes it buildable for vehicles. The Mech Lab catalog (`MechLabViewModel.buildCatalogRows`) is where a
  vehicle selection currently hides every engine and weapon, so a branch is added there, not a rule removed.
- **A mech engine's effect** is `outputFactor` (1.00 / 1.25 / 1.50), `speedFraction` (0 / 0.5 / 1.0), mass and, for
  the Military engine, -0.5 flux/s dissipation (`MechInternal`). A vehicle has no mass budget, output budget or
  flux reservoir, so only the speed term maps.
- **A mech weapon** is a `MechWeaponComponent` (hardpoint type, footprint, `weaponId` into the shared
  `WeaponRegistry`, pulls per magazine, `projectilesPerTrigger`, flux). A vehicle gun is a turret structure
  (`TurretCatalogRegistry`, a `TurretMountDef` with its own magazine, loader seconds, flux block, turn rate, muzzles
  and `visualCells`). Both resolve to the same `WeaponDef`; no adapter between the two mount shapes exists yet.

## Design

### Engines (slice 1)

- An empty engine card is the hull's own drive. As with plating, the stock part is no inventory item: the mech's
  **Industrial** engine is the factory part, so it is what an empty vehicle card already is, and only **Uprated**
  and **Military** are fittable.
- The effect is a **speed factor** on the hull's top speed and acceleration, banded as the mech's refit speed
  bands are (`speedFraction` 0.5 and 1.0). Proposed: Uprated 1.10, Military 1.20 of the hull's `maxSpeed` and
  `accel`. A vehicle has no output budget, so `outputFactor` and mass do not apply; the Military engine's flux
  penalty is meaningless without flux and is dropped. Numbers are the owner's to tune (see Decisions).
- The factor needs a seam that does not exist: speed and turn rate are baked in `VehicleType.createBody()`, which
  `ConvoyService.spawn` calls. Add a factor to body construction; `Crippled.SPEED_SHARE` (a lost drive) stays a share of
  whatever the body's speed is, so a fitted engine and a shot-out drive compose.
- A heavier hull is not a faster one by fitting alone: the factor is on the hull's own band, so a Heavy tank stays
  slower than a Light technical. The doll's engine card keeps its factory art for the stock part and draws the
  fitted engine's own (`engine-uprated`, `engine-military`).

### Weapons (slice 2)

- **A vehicle is a mech for parts** (owner, 2026-10-28). Its gun card is a typed weapon socket, and a weapon fits it by
  the mech's own rules: the socket's hardpoint type (ballistic, energy or missile) against the weapon's, and the
  weapon's footprint against the card (`MechLabDollCanvas.isSocketCompatible`, `MechWeaponComponent.hardpointType`).
  Not any weapon fits any card. A card is **omni** only where a hull is truly authored with an omni mount; no vehicle
  hull is today.
- **The card takes the type and size of the stock gun.** `VehicleDoll.Gun` already gives each hull's turret a
  hardpoint family and one to three rows; the card becomes that type (the ARMS card today draws an energy gun as
  ballistic, so it gains the true energy type) and that size. A hull still carries what its turret was built for: a
  cannon hull takes ballistic weapons up to its card's height, a laser hull energy ones, a missile hull missile ones.
- **The AI follows the gun.** A missile card takes both direct (SRM) and indirect (LRM) weapons, so engagement stance
  (`VehicleType.engagementStance()`: duel, standoff, ambush) and the spotter requirement for indirect fire
  (`VehicleEngagement`) are read from the weapon actually on the hull, not fixed per hull type. The stock hull's stance
  is the default for its stock gun. Slice 4.
- A fitted weapon becomes a **derived mount**: the stock structure's physical fields stay (turn rate, muzzle geometry,
  `visualCells`, hit shares, raise and fold), and the fitted weapon supplies the `WeaponDef`, the magazine (the
  component's pulls times `projectilesPerTrigger`) and its flux numbers, where the loader seconds follow the stock
  mount. `antiArmorOnly` maps to `hardenedTargetsOnly`. The mapping is data, not a per-weapon branch, so it respects
  `moddable-weapons-nouns.md`: the mechanical authority is the weapon and mount catalogs.
- **Kits read the fitted gun.** `VehicleKit.suits` and `VehicleKit.Effect` (ready rack, accelerated loader,
  capacitor pack) read the gun actually on the hull, not `type.turretStructure()`. A kit that stops suiting a newly
  fitted gun (a capacitor pack on a gun that builds no flux) is refused at fitting and returned to stores when the
  gun changes, as plating is.
- **The turret sprite follows the gun** (owner, 2026-10-28). A hull's turret is a frame of its sprite sheet
  (`turretFrame`, `ConvoyRenderSystem`), which cannot draw a mech gun. Slice 5 gives the fitted weapon its own turret
  art on the hull: either a turret frame per weapon over the hull's turret mount, or the layered mech gun composited
  there. The choice is an art decision and comes back to the owner as picture cards.

### Doll and Mech Lab

- `VehicleDoll.sockets` marks CORE and ARMS live (no longer factory-locked); `caption` and `art` read the fitted part
  and fall back to the hull's own. The lab draws and answers drops on them as it does on the armor point, with the
  same fit, refuse and strip behaviour and the same spare arithmetic.
- The Mech Lab catalog for a selected vehicle gains an engine and a weapon list, each filtered to what fits, with the
  reason shown for a refusal (hardpoint type, height, a kit that would no longer suit).
- Store copy loses its caveat that a licence is a mech's alone.

### Data and saves

- `CampaignVehicle` gains an engine id and a weapon id, each null for the hull's own part, handled in `readResolve`
  like `platingId`. Old saves load unchanged.
- `VehicleDeploymentSpec` carries both through the frozen launch seam. Its record is constructed in several places;
  every one is updated.
- **A gap to close first:** `BattleFixtureJson` writes a vehicle deployment as only type, campaign id and callsign, so
  a fixture replay already loses fitted kits and plating. Extend it to carry kits, plating, engine and weapon in one
  change, so evidence runs play what the company built.
- `MechBay.installedWeapon` and `installedInternal` count vehicle engines and weapons as they count plating, so a
  fitted part is never also a spare; `loseVehicles` strikes them, `stowVehicle` and `berthVehicle` return them.

## Slices

Each slice is green and integrable on its own.

1. **Fixture seam:** `BattleFixtureJson` carries kits and plating (and is ready for the two new fields). No
   behaviour change; fixtures from before read as they do now.
2. **Engines:** `CampaignVehicle` field, `MechBay` and `MechWorkshop` commands, speed factor in body construction,
   `VehicleDeploymentSpec` and `ConvoyService.spawn`, the doll's engine card, Mech Lab engine catalog, fit and
   strip. Ships alone.
3. **Weapons:** the typed gun card and mech fit rules, the derived mount, `GroundTurret.fitted`, kits reading the
   fitted gun, the doll's gun card, Mech Lab weapon catalog, fit and strip.
4. **Stance follows the gun:** engagement stance and the spotter requirement read from the fitted weapon, with each
   stock hull's behaviour unchanged for its stock gun.
5. **Turret art:** the fitted weapon's sprite on the hull.
6. **Documents:** amend the sealed-hull statements (below), the Store caveat and the Mech Lab copy, and fold.

## Decisions (owner, 2026-10-28)

- Engines: the factors stand (Uprated 1.10, Military 1.20) and a Light hull may take a Military engine.
- Weapons: think of vehicles as just being mechs. A vehicle's gun card is typed like a mech's socket and takes only
  weapons of its type, unless truly omni; the AI follows the fitted gun.
- Turret art: a fitted weapon changes the turret sprite.
- Shuttle weapon mounts are a separate item: each is a truly **omni** slot that takes any shared weapon (the Store
  already sells shuttle Weapons in the shared vocabulary). Not part of this story.

## Constraints

- **Balance against a player, not the autopilot.** Fitted engines and guns change how hard a convoy or breakthrough
  mission is. Tune with the company's vehicles built the way a player would build them, not with the Marine
  commander playing the fixture.
- No new `battle.*` or `marines.*` switch unless an A/B needs one; if it does, it is listed in `switches.md` with
  its removal condition.
- A mech's use of the same parts is unchanged: its tests and `MechFrameBudget` fits must read the same.

## Documents to amend when it ships

- `heavy-equipment-progression.md`: the sealed gun, plate, drive and bay ruling (Vehicles: variants and kits) and
  the line that vehicle weapon swaps stay out.
- `mechs-nouns.md`: a vehicle's gun and drive being its type's, the sealed-doll bullet, the armor point bullet and
  the vehicle engine mention.
- `company-view-nouns.md` (the Store): the sentence that a mech weapon or engine licence is a mech's until a hull can
  take one.
- `convoy-nouns.md`: the loader sealed in its doll like its gun; the kit effects on a fitted gun.
- `moddable-weapons-nouns.md`: the derived vehicle mount.
- Code Javadoc and copy that say sealed: `CampaignVehicle`, `VehicleKit`, `VehicleDoll`, `MechLabViewModel` and the
  strings for the sealed hull.

## Tests

- `CampaignVehicle`: null engine and weapon ids read as the hull's own; a save from before them loads; fit and strip
  round trip, as `VehiclePlatingTest`.
- `MechBay`: spare arithmetic with a fitted engine and weapon, loss, stow and berth, as `MechBayVehicleTest`.
- The speed factor on body construction, and its composition with a crippled drive.
- The fit rule: hardpoint type, height, and a kit refused after a gun swap; a stance read from the fitted gun.
- The derived mount from a `MechWeaponComponent` and a stock structure, on the smallest input.
- Existing tests that encode the sealed hull (`MechLabViewModelTest`, `VehicleKitsTest`, `VehicleVariantLadderTest`)
  are updated where they assert it, and deleted where they only restated the content.
- Evidence, not tests: one `battleProbe` on a convoy fixture with a stock hull and the same hull fitted with an
  Uprated engine and a swapped weapon, comparing speed and first-shot timing; `createSnapshots` for the Mech Lab
  doll with both cards live.

## Risks

- **Hull-keyed AI.** Engagement stance and the indirect-fire spotter are set per hull today; slice 4 moves them to the
  fitted gun. Every convoy and breakthrough hull's stock behaviour must read the same, which a control run against
  stock guns shows.
- **Magazine and flux mismatch.** Mech weapons count pulls and draw on a chassis reservoir; vehicle guns count rounds
  with a loader and a per-gun flux block. The derived mount is the one place the two are reconciled, and the place to
  look first if a fitted gun fires wrongly.
- **Save ripple.** `VehicleDeploymentSpec`'s record change touches every constructor call; the fixture format gains
  fields.
- **Art.** A turret sprite per fitted weapon is real art work, and the most open-ended slice.
