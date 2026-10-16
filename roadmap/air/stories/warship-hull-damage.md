# Story — Warship hull damage: plating, sections and the core

Status: ACTIVE — slices 1 (plating and hit place) and 2 (visible damage) built; 3 (degraded mounts, fires) next

Written: 2026-10-16

Read `air-nouns.md` (**Overhead ships**, especially "A warship is hittable, but only by ordnance" and the
balance law) and `warship-behaviours.md` (Decided: ship against ship) first. Vocabulary (warship, mount, shield,
armour rule, wreck, fall, duel ledger, hull-fire hold) is theirs. This story adds what a hull is made of where it is
hit, and how that shows.

## Problem

Owner direction, verbatim:

> see what we can do about VFX damage across the hulls. The base game models a type of 'hull destruction' sim as
> the armor gets stripped and in sections going into the ship before the 'core' starts taking heavier damage. We
> could similarly have this more compartmentalized damage modeling and also handle cratering and 'cut away' from
> the ship using our 'destroyed ship' sprite tech.

Wider intent: atmospheric ship combat should read like ships of the line or a fleet action, lumbering giants
trading broadsides. A broadside only means something if the side that takes it is the side that suffers.

What the simulation has today (read 2026-10-16):

- **One flat armour number that never wears.** `Warship.takeDamage` puts every hit through
  `effectiveDamage(damage, penetration, armour)` against the hull table's rating. The thousandth hit on the same
  plate meets the same armour as the first. Nothing is ever stripped.
- **No hit location.** `WarshipSystem.strikeHulls` asks only whether a blast reaches the hull's footprint
  rectangle. A hull-fire round is aimed at the target's sprite centre, so where it "lands" is the middle of the
  ship whichever side it came from.
- **The shield asks the wrong point.** A front shield is asked about the bearing from its own centre to that
  landing point, not about where the round came from. On an Onslaught the sprite centre is 52 hull pixels ahead of
  the centre of gravity and the shield centre 32, so a round aimed at the hull lands *ahead* of the shield centre
  and a front shield covers it from any side, astern included. This is a defect and a candidate cause of lopsided
  capital duels (`hostile-ship-support`); slice 1 fixes it as a consequence of giving hits a location.
- **Damage shows as one tint.** `WarshipRenderSystem.drawHull` darkens the whole sprite toward charcoal with hit
  points. The wreck is the same sprite darker still, falling and then lying whole.
- **The destroyed-ship sprite tech exists, for aircraft.** `HullBreakup` tears a normalised hull lattice into
  pieces whose cells are drawn as runs of source rectangles out of the hull sprite (`HullWreckRenderer`,
  `UnitRenderService`), so a hull can be cut without editing any art the game owns. Warships do not use it yet.

How the base game models it (`ArmorGridAPI`, `settings.json`):

- An **armour grid** of square cells over the sprite's box, about 15 hull pixels each. Each cell holds
  roughly a fifteenth of the armour rating.
- A hit lands at a point on the hull's edge and reads the cells around it (a 5x5 block without its corners, the
  inner 3x3 at full weight, the ring at half), so a fresh plate reads as the full rating. Effective armour never
  falls below `minArmorFraction` (0.05) of the rating.
- The plate soaks the hit and is stripped; what the plate cannot stop goes to the hull's single hit-point pool.
  Once a patch is stripped every hit there reaches the hull nearly whole: the side that is being hammered dies.
- Weapons and engines under heavy damage are disabled; the hull's hit points stay one pool; at zero the ship
  becomes a hulk that may split into pieces (`splitShip`, `isPiece`).
- Damage is drawn with decals: holes, burns and cracks (`graphics/damage/damage_*48_*`) laid where cells are
  stripped, and whole-hull overlays (`ship_damage_over*`) masked to the sprite.

## Model

All of this extends **"A warship is hittable, but only by ordnance"** in `air-nouns.md`; it adds three nouns and
no parallel ones.

**Plating** is the hull's armour grid: the base game's own idea, read off the hull's own geometry. A warship's
sprite box is divided into cells of about 15 hull pixels (the base game's cell, clamped to between 6 and 28 cells
along the hull so a frigate still has a shape and a capital is not a mosaic). A cell belongs to the hull when its
centre is inside the hull's `bounds` polygon from its `.ship`; nothing is authored per hull. Each hull cell has
**structure**, an equal share of the hull's hit points, and the outer band of cells (the two-cell reach of the
base game's hit block) carries **plate**, a share of the armour rating. Deeper cells are the **core**: structure
with no plate of its own.

**A hit has a place.** A round fired at a hull enters it from the side it came from: the first live cell along its
line from the muzzle (or, for a blast with no source such as a bomb or a battery round, from the side nearest
where it landed). That cell is where the shield is asked (a front shield now covers only rounds from inside its
arc, which is what the arc meant), where the plate is read and stripped, and where the damage is attributed.

**The armour rule reads the local plate, and the plate soaks first.** A hit's effective armour is the hull
rating times the weighted plate left around the cell it entered (the base game's 21-cell block, read only over
cells that carry plate, so a fresh edge reads exactly the rating), never below the base game's 5% floor. What the
armour rule lets through is the base game's armour damage: it is dealt over the block by weight, each plated cell
soaks its share out of the plate it has left, and only what the plate cannot soak reaches the hull. So a fresh
side shrugs off the first rounds, and once it is stripped nearly every round reaches the hull whole: the side
under fire dies and the far side does not (see Decided, 1). The glance threshold stays against the hull's rating,
so ground fire that could not touch a hull still cannot once its plate is gone.

**A breach is a cell holed through.** A cell whose plate is gone (or which never had any) and whose structure has
been spent is **breached**: a round's line passes through it to the next live cell. So a battered side craters
inward, the next rounds reach the core, and the core, having no plate, takes them nearly whole. **An outer section
gives way as one**: none of its cells is holed until the hull has lost a quarter of its hit points through that
section, so a stripped side first takes its punishment on the frames behind the plate and then opens. A core cell
is holed only from an open neighbour, so the core is opened from the breach inward and never under standing plate.
(Without that gate a concentrated broadside drilled a tunnel to the core within seconds and on through the far side.) Damage the hull
takes is spread over the struck cell's block in proportion to the same weights; the hull's hit points stay the one
authority for when the ship dies, so a ship can be lost with whole sections still standing.

**A section** is a named region of cells, derived and never authored: **bow** (the forward quarter of the hull
length), **stern** (the aft fifth), **port** and **starboard** (the remaining plate band either side of the
centreline) and the **core** (everything deeper than the plate). Sections are how the battle and a person read the
grid ("the port side is stripped, the core is open"); the cells are how it is computed. A section is **stripped**
when most of its plate is gone and **breached** once a share of its cells are breached.

**Consequences, in order of build.**

1. *Readings and visuals* (slices 1 and 2): plate, structure and breaches are readable per section and per cell;
   the duel ledger and scene readings can say which side took it.
2. *Degraded mounts* (slice 3): a mount whose slot cell is breached is knocked out for the rest of the battle; its
   turret is drawn torn off. This is the base game's weapon disabling made permanent, because there is no repair
   in a ground battle. Point defence is included. A ship that has lost its port battery has a reason to turn the
   other side, which a broadside helm can read.
3. *Fires* (slice 3, presentation first): a breached section burns, a flicker of fire and a column of smoke from
   its breached cells, visible from the ground. No damage over time unless the owner asks; the base game has none.
4. *Crippling* (later, coordinated with the handling owner): breached engine cells (a nozzle's slot cell) put that
   nozzle out, its flame stops and its share of thrust goes. Kept out of the first slices because handling belongs
   to the ships-of-the-line feel work.
5. *Breaking up* (slice 4): at zero hit points the falling wreck tears along its breaches rather than along a
   generic V. The `HullBreakup` lattice is seeded from the plating's breached cells, so a hull holed amidships
   falls in two at the hole. The fall, strike and skid stay `WarshipWreck`'s and the crash's.

## Visuals: cratering and cut-away with the destroyed-ship sprite tech

The hull is drawn the way `HullWreckRenderer` draws a broken aircraft: as rows of source rectangles cut out of
the game's own hull sprite on the plating lattice, never by editing art.

- **Intact cells** draw normally, as long runs (an undamaged hull is still one quad).
- **Stripped plate** darkens and takes a scorch: the base game's own burn and crack decals, laid deterministically
  by cell, multiplied over the plating, more of them the more plate is gone.
- **Breached plate band cells** are cut away: the plate run is not drawn there, and the same source rectangle is
  drawn beneath at a charred, structural tint, inset a little, so the eye reads torn plating over exposed frames.
- **Breached core cells** are cut through: nothing is drawn, the ground shows through a crater in the ship, edged by
  the dark frames of the cells around it.
- **A fresh breach glows**: the cells around a new breach glow molten orange and fade over a few seconds, and the
  breach throws a short burst of debris and sparks. Breached sections burn (slice 3).
- The whole-hull charcoal tint is retired in favour of the cells; the shadow is the hull's own sprite as before
  (a holed shadow is a candidate later).
- A round's blast, its impact sound and a beam's end are drawn where it met the hull (or its shield bubble, on the
  side it came from), not at the hull's middle it was aimed at. Before this every hit on a hull burst over its
  centre, which hid whatever the plating did.
- A falling and fallen wreck keeps its cut, burnt darker.

Built (slice 2), read in `hull-broadside`'s close GIF: the struck side darkens and scorches over the first twenty
seconds, gives way as a dark gash of exposed frames, and the waist is cut through to the ground by the end, with
breaches glowing as they open and burning after. Not yet: turrets over a breach are still drawn (slice 3 knocks
them out), and the base game's decals are faint once the GIF palette has had them.

Every frame is a pure function of the ship's plating state and the animation clock, so a review frame and the game
draw the same thing.

## What stays deliberately simpler than the base game

- **One plate thickness.** Every plated cell carries the same share; there is no authored armour map.
- **Penetration stands in for damage type**, as it already does (`warship-behaviours.md`, ship against ship, open
  question 2); the base game's armour-damage multipliers by type (high explosive strips plate twice as fast,
  fragmentation a quarter) are not modelled. A candidate later, together with that question.
- **No hard flux, no EMP, no repair**, no weapon or engine malfunction that recovers.
- **Hull hit points stay one pool and stay authoritative.** Structure per cell is attribution for breaching and
  for the picture, not a second health model.
- **No modules** (the base game's station and Onslaught Mk.I sections are separate ships; not here).
- **Pieces are presentation.** A broken hull does not become several bodies that fight on.

## Decided (in this story, with the reason)

1. **The plate soaks first, as the base game's does.** Two rules were measured on `hull-broadside` (two
   Onslaughts, the same fight each time). With the plate merely wearing (the hull taking what the flat rule let
   through from the first round, the plate losing the same), the first hull fell at tick 985; with the base game's
   soak at 1295; with the flat armour control at 1685. The soak is both the owner's description and the closer to
   today's pace, and every shipped warship scene keeps its verdicts under it. Kept as a control:
   `-Dbattle.warship.plateSoak=false`. That a stripped capital still dies about a quarter sooner than one whose
   armour never wears is the intended effect, and a tuning question for the duel-feel work.
2. **The shield is asked where the round meets the hull.** Not a balance change: it is what the arc always
   meant.
3. **Control switches**: `-Dbattle.warship.platingGrid=false` restores the flat armour, centre landing, the old
   shield question and the whole-hull tint; `-Dbattle.warship.plateSoak=false` keeps the grid with the wearing rule.

## Slices

1. **Plating and hit location (simulation).** `WarshipPlating` built from the hull spec at fit; hits located by
   entry ray; local armour rule; plate stripping; structure attribution; breaches; sections with stripped and
   breached readings; shield asked at the entry point. Unit tests on the grid (fresh edge reads the rating, a
   repeated hit strips and then lets more through, a breach lets the next round through to the core, the
   shield's side). Acceptance: the existing warship scenes keep their verdicts.
2. **Visible damage.** Cut-away drawing on the plating lattice, scorch decals, fresh-breach glow. Scene
   `hull-broadside` (sceneEvidence and createSnapshots): one Onslaught holding station under a hostile
   capital's broadsides from one side, against a control with the grid switched off. Verdicts are mechanics:
   the struck side's plate goes and the far side's stays, damage reaches the core only after a breach opens,
   breached cells are drawn cut away, and the control's armour never changes. Hull lost and time to first breach
   are readings.
3. **Degraded mounts and fires.**
4. **Breaking up along the breaches.**
5. **Crippling**, with the handling owner.

## Acceptance

- `sceneEvidence -Pscene=hull-broadside` passes; `createSnapshots -Psnapshot=hull-broadside` shows a hull's
  struck side cratering inward over a broadside exchange at 1920x1080 / 100%.
- Every shipped warship scene keeps its verdicts.
- `air-nouns.md` (Overhead ships) absorbs plating, hit place, breach and section when the story ships.
