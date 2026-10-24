# Mechs

Status: ACTIVE — distinct chassis, persistent support loadouts, and production composition share one mech authority; battlefield tuning continues.

Written: 2026-08-23

Updated: 2026-10-23 — a mech's flux shows as a haze on its vents; held racks chain their salvos; earlier: torso rear plate, torso arcs, missile warheads, duel range against armor.

## Purpose

Mechs are a readable family of armored battlefield actors, not one universal
heavy unit scaled by encounter difficulty. A chassis gives up or gains a
specific combination of mobility, durability, weapon bands, silhouette, and
physical presence. Doctrine decides how that chassis uses its equipment. The
family creates target-priority and counterplay decisions without quietly
increasing an encounter's total armored threat.

## Vocabulary and ownership

- A **variant** is persistent hardware identity: chassis body, physical
  dimensions, default loadout, silhouette, and default doctrine. It owns what
  a live mech is.
- A **livery** is appearance-only faction or company surface treatment for a
  known chassis and the authored paintable casing of its installed equipment.
  It may change paint, plate-face material, wear, and non-readable markings,
  but it is not a hardware variant and owns no geometry, mount, loadout,
  durability, movement, doctrine, or faction-wide bonus.
- A **role** is tactical doctrine. It owns where a mech attempts to operate,
  what it supports, and when it advances, holds, or withdraws. A recommended
  role is not a chassis lock; the same hardware may receive another doctrine.
- A **deployed role** is the campaign mech's authored role frozen into one
  battle payload. A live mech may carry a nullable **battle role override**;
  its **effective role** is that override when present and otherwise its
  deployed role. Clearing the override returns to the deployed role, not the
  variant's recommendation. The override ends with the battle.
- A **loadout** is the live mount configuration. It owns installed hardpoint
  components and their independent ammunition, mechanical cycle, salvo, lock,
  and resupply state, plus one shared chassis flux reservoir.
- **Relative handling mass** is the chassis's authored inertia for manual
  acceleration and braking. It is a dimensionless response parameter, not a
  tonnage estimate or remaining durability. The Bulwark has the heaviest
  response, the Hound the lightest, and the Warden an intermediate response.
- A **mount** is physical hardware at an arm or shoulder position. An absent
  mount is genuinely absent, never a dummy weapon or a hidden firing band.
  The arm positions come in two shapes, and a chassis has one or the other:
  a **centreline arm mount** (`ARMS`, the gun under the Hound's nose or the
  Warden's chin) or a **left and a right arm mount** (`LEFT_ARM`,
  `RIGHT_ARM`, the Bulwark's two arms, each with its own gun). Every arm gun is
  one gun: there is no paired or "dual" component, because a pair is two arm
  mounts, each fitted, damaged, lost and fired on its own. A gun whose art only
  reads on the centreline (the nose chaingun and nose autogun, the heavy,
  demolition and quarry-breaker cannons) fits only a centreline mount; the
  other arm guns fit any arm mount. Direct-control number keys follow mount
  order: 1 restores the default fire groups, then 2 centreline arm, 3 left arm,
  4 right arm, 5 and 6 the shoulders.
- A **missile replenisher** is an installed onboard subsystem. It owns how
  quickly finite SRM and LRM mounts regain trigger packs, independently of
  their mechanical trigger cycle and ready-ammunition capacity. Replenishment repeats
  for the life of the mech; the installed component is the future item-upgrade
  seam for changing that cadence.
- A **rotary gun** (the chainguns) has no trigger pull. It fires while its
  trigger is held: its **spin** ramps from a first-turn rate to its full rate over
  the spin-up time, the barrels **coast** at speed for a moment after release, so a
  quick re-press fires at full rate, then spin down. Its rounds come off a **belt**
  of a few hundred, which a **hopper** refills at a fixed rate every tick for the
  life of the mech, firing or not: the chaingun's counterpart of the replenisher's
  cadence, owned by the gun. Ammunition is unlimited over a fight; a long hold
  empties the belt, and then the gun fires only as fast as the hopper. Spin-up,
  refill and the round's damage are what make a better chaingun, so the feed is
  authored on the weapon (`sim.rotary`), not on the chassis, and the better gun
  is a better component (the Advanced high-feed chaingun and its nose fit).
- A **campaign mech** is one persistent player-owned chassis plus its doctrine
  and installed components. It is the campaign authority that produces a
  frozen battle loadout; the live battle mech never reaches back into it.
- A **callsign** is the player-authored display label for one campaign mech,
  edited on its Mech Lab surface. The persistent mech id remains its identity;
  changing the callsign changes neither hardware, doctrine, squad membership nor
  inventory. Callsigns need not be unique, since ids, never display text,
  distinguish records and battle entities. Input is trimmed and holds at most 48
  characters; blank input and a refused rename leave the current name. Support
  freezes the callsign with the other deployment values at commitment, so a
  later rename cannot reach a mech already in flight, and the battle mech never
  reads it back from the campaign record. The landed unit's name, shown on its
  selection panel and in direct control, is that frozen callsign; a deployment
  without one (debug, legacy, story) keeps its deterministic `support-` name.
- A **mech squad** is the player-facing group of up to four campaign mechs
  configured together in the Mech Lab. One selected active squad becomes
  the payload when a sourced Mech Support power is committed. It is distinct
  from the battle-lifetime lance that realizes that payload after landing.
- A **commander-called mech drop** delivers a lance by the same air delivery a Mech Support power uses, but is called by
  a side's commander and paid from a **mech-drop allowance** the mission declared (a count of lances and the lance
  composition, like `GroundDefences`). It spends no command points or charges, is claimed for the calling commander
  rather than the player, and lands as an ordinary claimed machine squad that commander plans. The marines' lance
  descends from orbit onto the drop zone as the marine lift does; another side's crosses the map edge. It is symmetric
  (the ledger is keyed by faction) and only a Conquest declares one today, for its marines (untuned). The player's
  power and a commander's call share one payload and unloading path and never draw on each other.
- A **field works** is a vehicle bay on a battle map that is building a chassis
  rather than only sheltering one. It is the only structure on a map that makes
  a unit. It owns what is on its stocks; it does not own the berths it stands
  them in, the technicians who work it, or anything about a machine once it has
  driven out. What it can lay down is bounded by what a field shed can carry,
  stated as structure rather than as a list of chassis names, so a chassis added
  later is admitted or refused by what it weighs. Within that bound it is
  **tooled for one faction**: the map's own, which is the same doctrine its
  garrison's lance is drawn from.
- A **machine on the stocks** is the part-built chassis itself, standing in a
  gantry as an ordinary battle body: seen, targeted and damaged exactly where it
  stands. It is the **real variant** — an unfinished Hound is composed from a
  Hound's own modular art and is a Hound's size, structure and silhouette — with
  its plate fitted and empty. What it has not got is a **loadout**, and that
  alone is what makes it inert: every firing path is keyed off the installed
  mounts, so a chassis with none is skipped by all of them without anything
  having been told to skip it. Being built on a chassis and carrying a loadout
  are therefore separate questions; folding them together forces a choice between
  an unfinished machine drawn as a generic mech and one that arrives with a
  working gun. It is not a mech and never becomes one — a finished machine comes
  off the stocks and an ordinary chassis stands where it stood.
- A **mech pattern** is the company's permanent right to fabricate one chassis,
  weapon component, internal or vehicle (`MechPattern`). Patterns gate fabrication; owned assemblies
  are separate, finite and losable, so losing every assembly keeps the pattern and
  a pattern never puts a free assembly in stores. A new company knows the Hound,
  nose chaingun, SRM-5 and the three stock internals; a pre-pattern save that had
  an unrestricted workshop knows every chassis and weapon pattern but only the
  stock internals, which did not exist when it was written. Weight class is the
  access ladder: Hound (light) Common, Warden (medium) Advanced, Bulwark (heavy)
  Prestige (the three are first stand-ins for a grid of mech lines, tech family by
  weight class: `mech-lines.md`); weapons and internals carry their own authored tier. Vehicle patterns carry an authored tier too —
  heavy APC Common, escort APC and militia technical Advanced, breakthrough tank
  Prestige — and no vehicle is a starter: a company earns its first hull. A vehicle
  card's weight class is the lift its hull asks for, not its tier, so the card says
  what the drop will need; the two usually agree, and where they do not (the
  spotter pickup is Advanced and rides a Light cradle) the lift is what is printed.
  **Anything built on a truck is a field rig, not military issue**: the militia
  technical, the **supply pickup** (`SUPPLY_TRUCK`, Common) and the **spotter
  pickup** (`SCOUT_CAR`, Advanced) are jerry-rigged civilian pickups. Their cards
  carry a field-rig provenance and are weighted up by the outfits that weld them
  (pirates, independents), never by a navy; a military faction is known for
  purpose-built hulls. The two light roles are lines of their own, one hull each,
  Light lift, and each pays for its one job with the gun, the seats and the plate:
  the supply pickup hands out a finite stock through the battle's resupply
  authority while halted, and the spotter reports what its mast sees to the fog
  and the commander (`convoy-nouns.md`, the light roles). Tri-Tachyon's vehicles are
  hover and drone coded: the **hover scout** (`HOVER_SCOUT`, Advanced, Light lift) and
  the **drone skimmer** (`DRONE_SKIMMER`, Prestige, Medium lift), each with a drone bay, carry a Tri-Tachyon
  pattern provenance and are weighted up by Tri-Tachyon alone (`convoy-nouns.md`, the
  hover line). The **SRM technical** (`SRM_TECHNICAL`, Common, Light lift) is a field
  rig too: the pickup carrying a mech SRM-5 anti-armor pod.
  A vehicle line climbs by **variants**, each its own pattern card and bill, never
  by fitting the hull it has: the heavy APC's up-armored APC (twice the plate, slower,
  Medium lift) and fire-support APC (a heavy mortar where the troop bay was, no seats,
  Medium lift) are Advanced, as is the LRM carrier (two LRM-15 anti-armor racks
  where the troop bay was, no seats, Medium lift, spotted indirect fire;
  `convoy-nouns.md`, the missile carriers); the breakthrough tank's variants (screened tank, laser tank,
  dozer breacher) are Prestige and Heavy lift. A variant earns its place by a trade —
  among fabricable hulls on the same lift with the same gun, none is at least as good
  at everything — and costs more than the hull it improves on. A faction weights up
  the variant it is known for (Hegemony the up-armored APC and the LRM carrier, the Lion's Guard the
  fire-support APC, the Luddic Path the dozer). Tri-Tachyon weights its own hover line,
  not anybody's tank.
  Every variant now has its own hull and turret art and is offered. The dozer
  trades the cannon and speed for the line's heaviest plate and a blade that
  pushes through barricades and ordinary walls, opening a route other vehicles
  can follow (`convoy-nouns.md`, the dozer's blade).
  **A variant whose hull or turret art is missing is not offerable**, exactly as a
  loadout card whose presentation is pending is not: it keeps its stats, pattern and
  bill, but no channel draws it and no card for it can become cargo
  (`VehicleType.presentationDeclared`). A **mech pattern card** is the cargo item that teaches one
  pattern; learning writes only to the mech bay and is consumed only when it
  teaches something new. Cards arrive through the same market, licence, patron and
  recovery channels and access gates as loadout cards (`progression-nouns.md`),
  as their own scarce stack: one market slot on large or military markets, an
  occasional patron award in place of a loadout card, at most one per high-risk
  salvage; faction issue weights the draw. Known or carried patterns are never
  offered, and unlearned items with no stock are absent from the Mech Lab catalog.
- A **gantry** is one of the support squad's four bay spaces, and it holds a mech
  or a **company vehicle** (`CampaignVehicle`): an id, a callsign and a
  `VehicleType`. The gantry is the only thing the two share. A vehicle has no
  chassis, sockets, flux, replenisher or weapon components; its hull, turret and
  kinematics are its type's, so there is nothing to fit until a vehicle doll
  exists (`mech-lab-data-authored-dolls.md`). Mechs and vehicles count against
  the same four gantries and the same restored berths, fabricate from the same
  material currency against a learned pattern, and a save from before vehicles
  keeps every gantry a mech.
- The **bay manifest** is the active squad's bay spaces frozen at launch: its
  mechs in gantry order, then its vehicles, each with chassis or hull, weight
  class and callsign. Vehicles carry their campaign id across the launch-fixture
  seam as mechs carry theirs. Armour Support is sourced as before (a committed
  Valkyrie, or a story battle whose employer arranges the heavy lift while the
  bay holds a vehicle). Where Mech Support and Armour Support would both be in
  the roster and the bay was read, Mech Support carries the whole manifest and
  Armour Support leaves the roster: one bay, one picker. Armour Support alone (a
  vehicle source with no mech source) still brings vehicles one per charge; only
  a debug grant or a launch with no bay sets down its unowned fallback APC.
- An **armor drop** is one call of Mech Support. The player arms the card, picks
  which manifest entries come down this time (the picker starts on every machine
  aboard that lift allows, up to a lance), then clicks the landing ground. Only
  the picked machines fly; the rest stay aboard for a later call. A cooldown
  (30 s) spaces the calls in place of one charge per lance, and the card counts the
  machines still aboard, reading spent when none are.
- The **lift budget** is the mission's armor lift, carrier by carrier: a cradled
  company boat flies two machine lifts at its rating on a light cradle and three
  on a heavy one, and the synthetic heavy
  carrier a hull, ship, employer or story arranges lifts the whole bay. Each
  picked machine spends one lift from the lowest-rated carrier that admits it
  (`air-nouns.md`, Lift), so a Heavy machine spends Heavy lift. Spent lift stays
  spent for the mission whether or not the carrier came home: better boats mean
  more armor on the ground, not faster calls. An entry the budget cannot lift is
  shown disabled with its reason (no lift of its class, lift spent, or its only
  carrier away), and a call the budget refuses stays armed and spends nothing.
  A call split between the company boat and the synthetic carrier flies both.
- **A vehicle wrecked on the field is lost to the company**: settlement takes it
  off its gantry, which stands empty for a new one. Only a vehicle that landed
  and was wrecked writes back — one still aboard a carrier is not tracked as
  landed. Mechs write back by section instead (see Section loss).
- **Mech component inventory** is finite fleet stock. Installed weapon assemblies
  and subsystems count
  against owned quantity; a refit transaction returns the target mech's
  current component before evaluating the replacement.
- An **internal** is a non-weapon fitting that changes the frame (`MechInternal`):
  an **engine** in the CORE socket, a **capacitor bank** in CAPACITORS, an optional
  **vent array** in MINI_FAB, and **plating**, which covers the whole frame and has no
  socket. The **stock frame** is the industrial engine, factory plating and factory
  capacitor bank, with the utility socket empty; every fabricated, recovered or migrated
  chassis carries it, and an existing mech with no recorded internals resolves to
  it. Each internal replaces one stock part; none multiplies the chassis.
- The **loader** is the missile replenisher. The standard one is integral; a
  faster one takes the MINI_FAB socket, so a loader and a vent array never share a
  frame, and a chassis without that socket (the Hound) takes neither.
- The **engine budget** is the engine's output against the fitted mass of every
  weapon and internal (`MechFrameBudget`). Output is the chassis's rated output
  times the engine's factor; a weapon's mass is its footprint plus the weight of
  its access tier, plus a build weight for a weapon whose carriage is the cost
  (the heavy cannon's breech and recoil mounting and the bastion autocannons'
  feed, so a Bulwark carrying either gives up a frame part;
  `heavy-equipment-progression.md`), plating's scales with the frame it covers, and an upgraded
  engine carries its own, since every CORE socket already takes the largest
  engine and its footprint costs nothing. A refit is refused
  when the result is over budget, unless it is no further over than the current
  fit, so a fit from before the budget existed can be stripped down but never
  loaded further.
- **Frame values** are what a fit resolves to and the deployment freezes
  (`MechFrame`): armor capacity, move speed, flux capacity and dissipation. Engine and
  plating move speed and armor capacity along the chassis's own band, from its
  stock value to its refit ceiling (roughly the pre-cut value), so no fit makes
  one chassis another. The capacitor bank sets the reservoir and base dissipation, a vent array
  adds to it, and an uprated engine runs hotter. Battle reads only these values;
  internals, mass and budget stay campaign authority.
- A **ship refit** is what the company ship's Mech Lab is worth to an owned
  mech (`MechRefit`; `second-wind-refit-tree.md`): an armor and a dissipation
  factor over the frame values, a fire-control factor on every mount's
  accuracy, and a torso traverse factor. The launch freezes it into each owned
  mech's deployment beside its frame, never into the frame, so the fit's own
  figures stay what the Mech Lab budget reads. Unowned mechs, and any mech off
  the founding hull, deploy neutral; battle never reads the ship.
- A **section** is where on a mech a round lands (`MechSection`): legs, torso,
  the centreline arm mount or the left and right arm mounts, the left and right
  shoulder mounts, and the head. A chassis's
  **mech plan** (`MechBodyPlan`, authored per chassis) stacks four height bands
  from the feet up (legs, torso, shoulders, head) so the ballistic height picks
  the band exactly as it picks a marine's part; the torso band gives the arm
  mounts a lateral share of its hits (a centreline mount takes them all, a
  two-armed chassis gives each to the arm on the side the round came from), and the shoulder band gives the hit to the
  shoulder on the side the round came from, read against the upper torso's
  facing. A lone pod a chassis draws on its centreline (the Hound's) takes the
  whole shoulder band. A payload with no ballistic height (a blast, a strafe) is
  struck at a uniform height. The roll is never aimed; aiming at a section in
  direct control would be a separate, later feature.
- **Cover by section.** Each band of a mech plan carries cover rows, and they
  read from the ground up: cover hides a mech's legs (low cover about half,
  medium most, high all of them), at its tallest the bottom of its torso, and
  never its shoulders or head. A mech behind a berm is **hull-down** — its legs
  hidden, its weapons still in the fight — the counterpart of a marine in a
  trench. The cover's target-plane catch band, sized for a man, does not gate a
  mech: centred on a body twice his height it would hide the torso and bare the
  legs. Cover counted by section is counted once, so a round that reaches an
  exposed section takes no further cover cut (`body-part-cover.md`).
- **Section pools** divide the frame's armor capacity and the chassis structure
  across the sections by authored shares, so the totals are what frame and
  plating set; the armor rating stays per chassis. A mount the mech deployed
  without has no section and its share is the torso's. Each section is resolved
  by the shared durability calculation; what a section cannot hold carries on
  into the torso, which meets it with its own armor before its structure, and
  a hit on a section already at zero goes through the hole to the torso's plate. The chassis health and armor columns carry the
  sections' totals, so whole-chassis readers see one body. Plating may later
  shift capacity between sections; that is the fitting decision sections make
  possible.
  **The torso's plate depends on its arc** (`ArmorArcs`, `MechSections.TORSO_ARCS`).
  The arc is read from the hit's shooter against the upper torso's facing, the
  frame the shoulder side is read in: front 45 degrees either side of the torso's
  nose, rear 45 degrees either side of its back, and the sides between. The front
  is the chassis rating as it stands. The flanks are 0.95 of the rating with each
  point of torso armor counting 0.85. The back, where the reactor and its flux vents
  sit, is 0.75 of the rating at 0.6 plate, so a round from behind strips torso
  armor two thirds faster. A round from behind spends the torso's own **rear plate**
  (`TORSO_REAR`, `MechSections.REAR_SHARE` of the torso armor, held on top of it so a
  mech shot head-on lasts as long as it did) before the torso structure behind both;
  `-Dbattle.mech.torsoRear=false` is the control. The arc governs the torso's own plate whether a hit
  lands on the torso or spills into it. Legs, arms and head meet the chassis rating
  from every side: a limb is hit on whatever face it shows, and a second set of
  arcs per section would buy little. Every chassis shares the one profile. A hit
  with no shooter to read meets the front. `-Dbattle.mech.armorArcs=false` is the
  control. **Facing is mostly kept.** A standing mech pivots its hips onto its
  target and its torso twists 145 degrees, so the mech it is shooting nearly
  always meets the front. The back is for the second machine, the one its torso
  is not following (`heavy-equipment-progression.md`, facing).
  **Shares are tuned against the whole-body control.** Sections are allowed to
  move a mech's time to kill, not reinvent it. `sceneEvidence -Pscene=mech-time-to-kill`
  stands each stock chassis under one fire team (field rifles, pulse, DMR, two
  AMRs, two rocket tubes, one heavy cannon) in the open and hull-down. Run it
  again with `-Dbattle.mech.locationalDamage=false` for the control. Kill time
  runs 0.70-1.15 of the control, mean 0.88; every death is a torso kill and
  the durability order holds in every row. The ratio sits under 1 by structure:
  fire from one side never reaches the far shoulder, and the head and arms are
  often still standing when the torso dies. Raising the torso share costs mount
  life almost one for one, and a Bulwark at 0.95 would lose its main gun at 0.61
  of its life instead of 0.77. So the head is kept small (0.06 on Bulwark and
  Warden, its hit rate rather than its silhouette) and the rest stays as the
  capability a mech gives up before it dies: the first mount goes at 0.14-0.70
  of the time to kill, the main gun at 0.63-0.97, and a two-mount Hound is
  disarmed only in the last 15-20% of its life. A Warden under rockets in the
  open dies at 0.70 of the control because a 0.48 torso is two rockets, not
  because damage spills: spill meets the torso's armor before its structure, so
  heavy, slow rounds spread about 1.5-2 times the control's spread rather than
  the tenfold swing they had before.
- **Section loss** is a capability before it is the machine. Legs at zero
  **cripple** the mech: no walking speed, autonomous or manual, while it still
  pivots its hips onto its target, turns its torso and fires. A mount at zero
  **destroys its weapon**: the mount becomes absent for the rest of the battle.
  The head at zero **loses the sensors**: vision halves and weapon accuracy
  drops. The torso at zero is the only death; legs are never a kill. Damage above
  zero costs only that section's protection, with no hidden stat penalty.
  Doctrine follows the loss. A crippled mech is a **turret** under any order:
  it holds, turns and fires at what it can engage, asks for no movement, and is
  never an anchor its lance or a screened infantry advance keys movement to, so
  nobody is held waiting on it; a player's move order releases itself. A mech
  that has lost every mount is **disarmed**: it declines its doctrine's attack
  and breaks contact while it can walk, and a lance with no armed standing mech
  declines its attack goals as broken morale does, without touching the
  command's assignment; crippled and disarmed, it simply holds. One lost mount
  needs no posture: doctrine and engagement range read only the surviving
  mounts. `-Dbattle.mech.locationalDamage=false` is the whole-body
  control. The battle keeps each owned mech's section record (pools, what was
  destroyed, which weapon each lost mount carried); the frozen deployment
  carries the mech's campaign identity so the record finds its way home.
  Section loss shows on the chassis itself: a lost mount's weapon layer drops
  off the layered appearance, a section bursts into flame where it sat the
  moment it goes and its stump smoulders after, and a crippled mech smokes at
  its legs for as long as it stands. These are presentation events read off the
  sections (`MechSectionFxSystem`); they draw nothing from the battle's random
  stream, so they change no outcome and a review frame stays a function of
  simulation state. A damage doll in the direct-control HUD and above the
  selected-mech panel draws the chassis facing the viewer and from behind (the
  rear view shows the torso's back plate and shoulders, no legs), each section
  coloured by its armor and structure, and marks what is gone.
- **After the battle** settlement writes that record back. A destroyed torso
  (or a chassis that died as one body under the control) **loses the chassis**:
  it leaves its gantry and every part fitted to it leaves stores with it. A
  destroyed mount **loses its weapon assembly** from stores; the mount comes
  back empty and the pattern is kept, so the loss feeds fabrication. Every
  other damaged section, a destroyed mount's own section included, comes back
  as **mech damage**: the fraction of each section's structure and armor gone.
  Settlement is idempotent: a replayed result strikes nothing twice.
- A **repair** is ordered in the Mech Lab and is atomic: the whole bill or
  nothing. Its size is the chassis's **wear**, each section's loss (structure
  weighted over armor) weighted by its pool share. At full wear the bill is half
  the chassis's own fabrication bill, the boat precedent, and the clock is a
  few campaign days by weight class; both scale down with wear. A paid order
  clears the damage and starts the clock. The ship's field-repair refit shortens the clock when
  the order is quoted, and its spares rack cheapens a replacement weapon
  assembly, which is what refitting a destroyed mount costs.
- A mech's **readiness** is ready, damaged (no order yet) or under repair
  (clock running). Only a ready mech enters the armor-drop manifest; the Mech
  Lab shows a held-back mech's reason and days left. A damaged mech never fights
  on worn pools.
- The **Mech Lab** is the shipboard room that selects the active mech squad,
  parks its assets across four fabrication gantries, frames one selected asset as
  a top-down hardpoint doll, and performs inventory-checked refits. Asset/lance browsing is
  a separate internal bay screen so the fitting gantry can spend its width on
  equipment, the physical workspace, and sockets. Selecting a location changes
  catalog context; it does not itself change hardware. It is an authoring surface
  over campaign authorities, not a second inventory or a battle debug picker.
- A **socket** is one spatial equipment location on a heavy asset. Refittable
  sockets declare a compatibility type—ballistic, energy, missile, or
  omni—and an active rectangular region bounded by three columns and three rows.
  A component declares a compatible type and its own
  column-by-row footprint. It fits only when that complete rectangle lies inside
  the socket's active region, allowing a quarter-turn; equal scalar area alone is
  not interchangeable geometry. SRM-5 and LRM-5 racks both occupy two columns by
  two rows; SRM-15 and LRM-15 occupy two columns by three rows. Large shoulder
  sockets present that same upright grid. Range and delivery distinguish SRM
  direct fire from LRM artillery, rather than different footprints within a size class.
  Nothing is smaller than one row by two columns: every weapon, internal and
  socket is at least 2x1, so the smallest piece still reads as a piece.
  Above a single row, a piece is never wider than it is tall: multi-row
  footprints are 2x2 or 2x3 (columns x rows), and long guns stand upright in
  them, muzzle forward.
  Each asset-class layout owns the socket's compatibility and capacity. The lab
  draws the sockets as a **schematic**, not as docks around the machine: the
  engine core in the middle of a drafting panel on the left, the arm mount
  ahead of it (on a two-armed chassis, an arm card ahead-left and one
  ahead-right), a shoulder either side, the capacitors and mini-fab behind, each
  wired back to the core in its hardpoint colour. The fitting camera stands the
  machine itself in the right of the view, beside the schematic.
  Presentation may enforce a minimum pointer hit area for usability without
  changing that grid or making the asset physically larger. Inactive cells remain
  visible and blocked so a smaller mount reads against the same scale as a larger
  one. An empty translucent
  footprint means an authored socket is unoccupied; an omitted socket is still
  genuinely absent. The drag gesture is presentation; the validated resulting
  placement is domain intent.
- **Hardpoint language** (after MechWarrior 4's lab). Colour names the
  compatibility type everywhere hardware or a socket is drawn: energy red-orange,
  missile green, ballistic yellow, omni grey. The frame's own mounts have their
  own colours: the engine socket blue, the capacitor (flux) socket violet and the
  mini-fab utility socket teal, and an engine, capacitor bank, vent array or
  missile loader is a brick piece in its socket's colour like any weapon. Every
  chassis has an engine and a capacitor bank: those sockets only take a
  replacement and are never left empty. Size reads from shape, not text: every
  grid cell is a chamfered brick narrower than it is tall, and a footprint is its
  bricks stacked, so a 2x3 rack is visibly a taller stack than a 2x2. On the doll
  every brick is the same size, so a socket's card grows with its capacity and a
  2x3 piece is physically larger than a 2x2. An empty doll socket wears its type's colour on its rim
  over dim bricks; fitted hardware fills its bricks in its own colour and fuses
  them into one solid piece, notched only along its outside edge. The piece's
  face is a painted icon (`HardwareArt`): a flat pictogram in a dark shade and a
  light highlight over that colour, never the battle sprite, with a silver
  stencil designation (SRM, LRM, CG, ENG, CAP...) tagged in the piece's bottom-right
  brick. Designations are markings, not prose: they do not localize. Drag
  compatibility therefore uses a colour outside that palette. The doll is the
  location picker: clicking a socket selects it, the right column describes only
  that socket, and a fitted weapon is stripped from the X on its doll card.
- **Stores and fabrication are separate gestures.** The left column lists
  stores: spares the bay holds, and patterns the company can build. Opening a
  mech lists **all parts**, every kind whichever mount it would go on, and
  dragging one lights the mounts that take it; selecting a mount narrows the
  list to what fits there, and ALL PARTS returns to everything. Stores are
  sorted by **category toggles** drawn as part symbols on their brick colours
  (ballistic, missile, energy, engine, capacitor, utility): none chosen lists
  everything, and a mount whose socket already decides the kind offers none. A fitted part is on the mech, not in that list. Fitting is a drag from a
  spare onto a mount, and taking a part off is a drag back to stores (or the X);
  there is no install button. A known pattern with no spare in stores carries a
  build strip under its piece: its material cost and a wide BUY button;
  it builds one into stores and fits nothing, and is dimmed while the materials are
  short. A row with a spare offers no build at all: the spare is what to drag, and
  stripping a part back to stores takes the offer away again. Plating is the one exception: it covers the whole frame and
  has no mount to drop on, so a spare plate is fitted from its row. Internals
  drag off like weapons (or come off by the X) and leave their socket empty. An
  engine and a capacitor bank are **required**: a mech missing either is
  `INCOMPLETE`, a readiness beside damaged and under repair, and stays off the
  drop manifest until both are fitted; its empty sockets warn on the doll and in
  the slot list. Plating covers the frame and is only ever replaced. Until a
  refit, an empty socket reads as the stock part for the engine budget, the
  lightest real fit, so nothing fitted meanwhile can exceed a budget a refit
  would honour. The stores filters follow the
  socket: a weapon mount sorts by hardpoint type, and every other socket offers
  All and In stock. The whole stores row is the drag handle and the hover target
  for its spec sheet. The fitting
  view has no call-sign or status bars: a BACK button returns to the lance
  overview, as the Mech Lab tab itself does.
- A **fitting doll** is the asset-authored maintenance presentation of one mech
  or vehicle class. It references the same ordered appearance and physical scale
  used in battle, while owning the maintenance facing and every socket's physical
  mount anchor, remote equipment dock, interaction footprint, compatibility, and
  rectangular grid. The renderer consumes that definition; it does not guess mount
  locations from a generic chassis shape. Future external authoring may move the
  definition out of code without changing that ownership boundary.
- A **workshop task route** is a battle-owned ambient assignment for one Mech Lab
  worker. Fabrication, parts-running, inspection, and coordination routes own the
  actor's stations, walking, dwell activity, facing, and any task-local tool or
  effect. The worker remains a real room entity rendered by the battle unit
  pipeline. A workshop route communicates a busy facility but owns no refit
  duration, inventory movement, or campaign outcome.
- A **weapon family** owns projectile behavior and presentation; a component
  turns it into a mountable rack/arm with capacity and appearance. The gun,
  mount, and chassis remain distinct authorities.
- A **capability tag** is the compatibility classification that routes a
  strider through shared mech construction. It is not the source of a
  variant's live stats, geometry, or loadout.
- A **battle lance** is the current combat grouping for one coherent mech
  squad. Formation and separation apply within that moving squad; unrelated
  squads do not become one formation. This does not define campaign ownership
  or lift organization.
- A **lance order** is the battle-local coordination rule shared by every mech
  in one battle lance. **Form on Lead** retains the lance's cohesive formation
  and lets the live lance lead shape how far a Brawler may press or recalls one
  that separated under Free Reign. **Free Reign** releases generic formation
  and support tethering so every member may execute its own doctrine
  independently. Both remain subordinate to the lance's mission assignment
  and legal contact picture. A lance begins on Form on Lead.
- A **tactical move order** is a one-shot, battle-local destination for one
  exact player Mech. It temporarily owns that chassis's locomotion without
  replacing the lance's mission directive, shared plan, doctrine, or contact
  picture. The clicked ground selects a provisional walkable cell in coarse
  connectivity; the actual chassis must still prove a route to a legal point
  within that cell. Arrival or proof refusal releases the order back to ordinary
  behavior. A right-drag instead of a click gives the order a **held heading**:
  the mech goes to where the drag began and, on arrival, stands with its hips
  the way the drag went. The heading outranks the combat target for the hips
  only — the torso still twists onto the fight — so the legs and their armour
  point where the player put them while the guns keep working. It lasts while
  the mech stands: the next route it walks, a new order, or a cancel ends it.
  A click on a garrisonable is not a move: one its side believes is enemy-held
  is an assault, one its side mans a **guard**, which holds a cell beside the
  place with the hips on the believed threat while nothing is in its sights
  (`ai-nouns.md`, machine assault and machine guard). A guard is the one order a
  commander may also give a lance of its own side; a player's order on the
  chassis outranks it.
- A **Defend Area order** is a persistent, battle-local tactical assignment for
  one whole battle lance. Its clicked center resolves to reachable ground and
  owns a circular twenty-cell-radius zone. Every live lance member positions
  and reacts inside that shared boundary while retaining its own combat target,
  installed weapons, and ordinary damage, ammunition, and cooldown state. It is
  distinct from both exact-Mech movement and Form on Lead / Free Reign
  coordination, and remains until superseded or withdrawal cancels it.
- A **force budget** is the ordinary-encounter authority that admits defender
  mechs and static defenses only when the attacker-side force can support them.
  Authored set pieces may explicitly decline that protection; Conquest does.

## Family and doctrine

The Bulwark is the durable all-band anchor and compatibility control case. Its
neutral production fit carries a chaingun on each arm and twin anti-armor
SRM shoulders (Missile warheads). Tri-Tachyon issue replaces them with a rapid pulse laser on
each arm and the right omni shoulder with a long-cooldown direct laser cannon whose
lance can pass through one body. Those are explicit faction hardware swaps;
livery changes only the accepted casing paint of whichever components are
installed. Hegemony Bulwarks put a Bastion autocannon on each
arm: slower six-round service bursts trade saturation for range and
armor penetration. Luddic Path Hounds replace the nose gun with a five-shell
Foundry Breaker, an inaccurate demolition cannon whose contact payload, broad
blast, and structural damage make every shot consequential. Lion's Guard
Wardens replace the right LRM rack with a short-ranged thermal lance whose
directional contact burn and one-cell impact bloom reward aggressive support.
These signature fits belong to the exact campaign faction ids `hegemony`,
`luddic_path`, and `lions_guard`; the Diktat does not inherit Guard prototypes.
The commercial floor is separate from those signatures. A Muster autogun is a
cheap direct-fire arm available as paired or nose packaging; a Quarry breaker is
a finite, inaccurate centerline industrial cannon whose main virtue is structural
damage; a Pioneer cradle fires four loose, straight-flight utility rockets from
a shoulder mount. These components are weaker because their original users
optimized for cost, security, mining, or public works—not because Independent,
pirate, or salvaged equipment is inherently low quality. They enter the weapon
catalog and livery family without becoming faction defaults. The Mech Lab exposes
them as common-market fabrication patterns whose lower material bills preserve
that commercial floor. No faction doctrine receives one implicitly.

The Warden's heavy cannon also carries real structural damage (140 per shell over
a wide blast, untuned), so it opens a wall section from range over a number of
shells: an assault plan treats it as a reliable distance breacher beside its
anti-structure fire, and a squad ordered at a wall turns the torso onto the cell
and fires at it (`phased-assault.md`, "Capabilities and roles"). Breaching is a
use of the same weapon, not a separate mode.

The Hound is a quick close-assault strider that lacks long-range pressure. The
Warden is a fragile long-range specialist whose cannon is an anti-hardened
fallback rather than a replacement close-range saturation weapon. Those are
hardware identities; any deployed chassis may receive any of these doctrines:

- **Brawler** seeks the close or direct-fire band and may pursue a local threat
  inside its assignment leash. Form on Lead shapes that advance around the
  lance; Free Reign lets it prosecute the perceived enemy position without a
  generic ally-distance clamp. Allied support is never permission to act.
- **Frontline Support** attaches to a nearby allied infantry squad or mech
  element and takes the threat-facing side of that group. It absorbs pressure
  without inventing an independent attack; without a legal anchor it holds and
  defends itself. The battle UI may call this doctrine **Tank**.
- **Long-Range Support** prefers the outer useful weapon band, retained line of
  sight, and a friendly screen between itself and perceived danger. When a
  legal screened firing position exists it wins over an unscreened one; cover
  and travel cost rank positions only within the screened or unscreened class.
  A screen is a broad threat-facing front, not an ally placed in the direct
  projectile ray, so the selected perch keeps every friendly body clear of its
  rear-oblique firing lane. If a threat closes, the mech fights while opening
  distance rather than treating the old perch as sacred. It never opens that
  distance with its back turned. While a threat is in reach with a clear line
  of fire, an unscreened perch whose walk puts the threat more than 120° off
  the walk ranks behind every perch that keeps it in arc. If no unscreened
  perch keeps it in arc, the mech takes a fighting-withdrawal step instead,
  farther from the threat but within the band and keeping it in arc, and a
  rushed escape tries both obliques before going straight back. Otherwise the
  torso's 145° twist loses the target while the hips follow the route, and a
  mech that cannot reach its perch never fires at all: a planted Warden under
  rifle fire fired 2.4 rounds before it died. Keeping the arc, it fires 10.6,
  deals twice the damage and lasts 29 s instead of 18
  (`-Dbattle.mech.overwatchKeepsArc=false` is the control). A screened perch,
  and a stand-off with the threat out of reach, are unchanged.
- **Balanced** serves its assignment from the middle or outer portion of its
  installed direct-fire band. It accepts a close threat already upon it, then
  resumes the assignment, and has no special ally-geometry dependency.

**Duel range.** Against armor — another mech or a ground vehicle — every
doctrine fights from a **duel band** chosen from the mech's own installed,
loaded weapons read against the target's (`MechDuelBand`): expected damage per
second at a distance, given against received. A tank whose gun has been shot away
(`convoy-nouns.md`, the turret section) has no weapons to read, so a mech
treats it as unarmed armor. A mech that out-reaches its
target by four cells or more **stands off**, holding just outside the target's
reach and inside its own, whatever its role. Otherwise it **closes** to a
shorter band of its own only when that band trades clearly better than its
longest one — a Brawler on a small edge, Balanced and Frontline Support on a
clear one, Long-Range Support never — and nobody closes on a tie, with one
exception: a band that brings a loaded **anti-armor rack** to bear closes when
it trades merely as well as the longest band. Between equal machines that rack
would otherwise never fire, and its warhead was fitted for exactly this target
(`-Dbattle.mech.closeOnAntiArmorTie=false` is the control). A rack that has run
dry leaves the reading, so a mech with empty magazines walks back out. Failing
both, it **duels** from the outer part of its longest band (about 72–92% of
reach) rather than walking inside it for no reason: two equal machines meet
there, not at contact. In band with a clear shot it holds and fires, stepping
once into cover facing the target when a covered in-band cell is a few cells
off (hull-down, by the section cover rows). Out of band it looks a short way
for an in-band firing cell or a cell nearer the band; while the target can
reach it, it never takes a walk that turns its back on the target, and too
close with nowhere legal to go it holds and fights. An armored contact
shelling it from beyond its own reach is a duel too: the band decides whether
it closes on it. While its rounds are landing the mech takes the contact to
stand where the fire comes from, not where it was last seen: a long-reach
shooter is usually beyond sight, and its last-seen cell is wherever it stood
before it stepped back. The rule sits below the
reflexes — a player's move order still owns the chassis, a crippled one is
still a turret — and keeps to command ground: every cell must be legal
under the active assignment, and a mech outside its command (an exact cell not
yet reached) is left to its doctrine. The one stretch is the **sortie**: a mech
settled on a post (defend area, hold node) that an armored contact out-reaches
from ground that can shell the leash, and that cannot reach the shooter from
where it stands, may go out to the ground the shooter can shell the leash from
(leash plus the shooter's reach). It stays out while the shooter can still
shell the leash, and doctrine walks it home after. Asking instead whether some
cell of the leash could answer strands the mech at the leash's edge, since a
shooter that out-reaches it steps back as it arrives; a defender that only
stands in the open and takes fire is not defending its post. Exact-cell moves,
rushes, attack-moves and posts not yet reached stay strict. A Frontline Support mech with a legal
anchor keeps pacing it; withdrawal, rescue and survival goals do not run
doctrine and so never meet the rule. Infantry targets are unchanged. A ground
vehicle is both a duel target, read by its turret, and a duelist: a deployed
armed hull between orders takes the same band against armor
(`convoy-nouns.md`, deployed vehicles). A player's move order still wins over
any band, for a hull as for a mech. Pressure
leaves out penetration, flux and ammunition depth on purpose: they scale both
sides of a trade alike far more often than they reverse it. A missile rack is the
exception and is read by its warhead (Missile warheads).
`-Dbattle.mech.duelRange=false` is the control; `MechDuelRangeScene` is the
acceptance.

The role changes how a mech serves an assignment; it never supplies an
assignment, hidden contact, or permission to leave mission-command bounds.
Different effective roles in one battle lance execute per member rather than
competing to turn the lance's shared plan into one role.

The lance order answers a different question from doctrine. Form on Lead keeps
the moving lance coherent while still arranging Brawler and Frontline Support
forward, Balanced through the middle, and Long-Range Support rearward. Free
Reign removes that generic formation authority; it does not remove
doctrine-specific relationships. A free-reign Frontline Support mech still
needs something legal to support, and a free-reign Long-Range Support mech
still values a credible screen.

Long-range doctrine still evaluates whether a prospective screen is physically
credible. A fragile Warden is not made into armor merely because it receives
another doctrine. A `CLEAR_ZONE` or `SECURE_COMPOUND` contact inside the named
zone lets Long-Range Support use a bounded perimeter around that zone for its
firing posture; losing that in-zone contact removes the perimeter, and exact
cell, withdrawal, survival, and rescue authority remain unchanged. Once
long-range racks can no longer apply pressure, their carrier may close to the
outer edge of its installed arms range while the onboard subsystem replenishes
them, returning to long-range posture only when every rack is full; individual
restored triggers and role-toggle round trips cannot make it oscillate between
bands. A chassis entering Long-Range Support before it has exhausted its racks
immediately uses any partly spent rack that can still apply pressure. Exhausted
ammunition may change doctrine positioning, but never grants access to an
absent mount or another role's withheld weapon.

All variants share movement-aware targeting and a planted-hip torso envelope:
near visible danger can interrupt a distant engagement, but the rear blind
wedge still makes body facing matter. When a lance moves in open terrain,
roles arrange its members forward, shoulder, and rearward; terrain may relax
that arrangement while retaining a minimum allied-mech separation. Idle posts
are not re-formed merely for visual tidiness.

Facing communicates intent without granting knowledge or changing locomotion.
An active target owns upper-chassis aim. Without one, the mech looks toward its
squad's primary remembered contact at the last-known cell; without contact, it
looks a short horizon along its queued route so an approaching turn reads before
the hips reach it. Remembered facing never reads the hidden unit's live position
and never becomes permission to fire. Moving hips remain aligned to the path;
stationary hips may settle toward the same remembered contact.

Hip and upper-chassis turns accelerate and brake within separate angular
budgets. A changed target or route therefore arrests the old swing before
reversing it instead of snapping to a new turn direction. Each foot retains a
world-space plant until reach or yaw requires a step; the lifted foot advances
toward a stance-constrained landing predicted around the body's touchdown
position and adopts the hip bearing only when it plants. A completed moving
stride leaves one pad behind the body and the new pad ahead, rather than letting
the chassis overrun both supports. Pivoting uses the same rule, so one pad
supports the body while the other walks around the turn. The rendered waist and
complete upper assembly respond through a damped, two-dimensional weight
transfer toward the single support pad or the segment between two planted pads.
Composition preserves the physical depth stack: feet first, thigh linkages over
the feet, then the ordinary upper assembly and its equipment layers.
This gait is fixed-tick presentation state only: it does not move collision,
pathing, aim, or targeting authority.

Each foot's lift and landing own separate positional sounds. Lift starts a short
pneumatic stroke; touchdown starts a heavy muffled ground impact. These remain
independent one-shots with separate gain, pitch, variation, and admission budgets,
so machinery never spends the impact budget. Their overlap comes from actual
gait transitions, never a baked walking recording or a second walking timer.
Heavier chassis lower impact pitch and raise its weight; variation is independent
of the battle's random source. Spawn, teleport, and a stationary planted stance
are silent. Audio consumes transitions even when hidden or distant, so revealing
a unit or returning the camera cannot replay old steps. A frame spanning several
ticks retains the newest landing and any lift still in progress, discarding
finished pneumatic strokes. Only nearby, visible events reach the mix, with the
closest events taking a bounded share of each phase's presentation budget.
Both battlefield hosts consume the same per-foot gait.

## Missile warheads

Every SRM and LRM rack size comes with two warheads. A rack's size, footprint,
cycle, flux, ammunition and replenisher are the same for both; only the warhead
differs. A mech's arm guns are its weapons against men, and its racks are its
weapons against armor (owner, 2026-10-18), so the plain SRM-5, SRM-15, LRM-5 and
LRM-15 are the anti-armor racks.

- An **anti-armor (AT) rack** fires a shaped charge. Most of its damage is a
  contact payload at high penetration, landing only on the body the rocket
  strikes, with a small, weak splash for a near miss. The AI fires it only at a
  hardened target (`TacticalScoring.isHardened`: mechs, ground vehicles,
  turrets, drone hubs), the same gate infantry rockets use. Against men,
  power-suited men included, it holds its pack and leaves the reservoir to the
  arms. Manual fire is not gated: a player who aims a rack at a man spends it.
- A **fragmentation (Frag) rack** has no contact payload. All of its damage is
  area damage at low penetration over a wider blast. It fires at anything and
  strips little plate.
- A rocket's contact payload travels with its detonation, so point defence that
  stops the rocket stops the charge too. A guided LRM that makes its hit roll
  lands its contact payload on the target it was fired at when it detonates.
- **Target choice.** Beyond the ten-cell close-threat radius, which still wins as
  self-defence, a mech aims its torso at the believed, engageable target its
  loaded mounts can hurt most per second (`MechTargetPressure`). Each mount counts
  its warhead at its armor efficiency, and an AT rack counts nothing against a
  soft target. A held target is kept unless another would take a quarter more.
  So a chaingun Bulwark with loaded AT racks turns to a tank behind an infantry
  screen, and turns back to the men while the racks replenish.
  `-Dbattle.mech.damageAwareTargeting=false` restores nearest-target choice.
- **Duel band.** The band reads a rack by its warhead against the target's
  plate: an AT rack at its contact payload, a Frag rack at its armor efficiency.
  This is the one place penetration enters the band (Family and doctrine, duel
  range), because penetration is what a warhead is chosen for.
- **Patterns.** A Frag rack is a sidegrade of its AT rack: the same tier, the
  same footprint and so the same mass, and so the same bill.
  The navies (Hegemony, Tri-Tachyon) weight up the Advanced AT racks. Pirates,
  independents and the Luddic Path weight up the Frag racks. A new company knows
  both SRM-5s, and Castle's recovered spare for Wayward is the Frag one, since
  the first operations are fought against men.
- **Factory fits.** The Bulwark keeps twin AT SRM-15s behind its chainguns. The
  Hound keeps an AT SRM-5. The Warden ships Frag LRM-5s: its heavy cannon is
  its answer to armor, and its racks are artillery against men.

`-Dbattle.mech.missileVariants=false` is the control: the AT ids fire the
single-warhead rack every SRM and LRM was before the split (area damage at
penetration 14 and 16), at any target. Readings are in
`heavy-equipment-progression.md` (missile warheads); `MechVersusInfantryScene`
is the infantry acceptance and `armorMatchup` the armor one.

## Ground clearance and arrival

A Mech moves as its actual circular chassis envelope through the map's solid
cells, closed shared edges, and limits. The Bulwark cannot use a one-cell
passage, but can use the centerline of a two-cell passage even where ordinary
cell centers are too close to its walls. Walkable cover remains traversable.
Doctrine, tactical orders, survival movement, and AI handback share that
clearance law; manual control inherits it without gaining a smaller body.

A physical route begins at the chassis's exact position. Its requested cell and
resolved legal endpoint are distinct. Resolution stays within the requested
cell's closed area, so a shared-boundary centerline may be valid without changing
the requested goal. Reaching that endpoint completes the route and leaves an
arrival witness while the chassis remains there on legal terrain. The coarse
cell projection remains useful for display and occupancy but cannot authorize
movement or claim arrival. Hip direction and route lookahead follow the physical
waypoints; chassis pivot limits still govern whether it may translate.

A proof may remain pending while bounded workers search frozen terrain. The
mech keeps that exact candidate rather than repeatedly canceling it for another
scored point or treating the wait as a failed route. Topology changes retire
stale proofs, and any drift before installation needs a legal connection from
the current body. Actual motion and downstream separation both sweep current
terrain, so an old route or crowd pressure cannot carry the chassis through a
newly closed obstacle. A blocked route stops and is reconsidered; it is not a
completed order.

A refused proof is cheap to repeat and expensive to compute: an unreachable
goal makes the search cost the whole component the chassis can reach. One
search therefore shares that single flood across its endpoint candidates, and a
refused intent stands for a backoff window rather than reopening each time the
body drifts half a cell. Both are controls (`battle.pathfinding.shareExhaustedFlood`,
`battle.pathfinding.clearanceFailureBackoff`); the serial scheduler runs proofs
inside the tick, so an unbounded repeat there stalls the whole simulation.

Tactical selection ranks candidates using the existing perceived contact and
mission boundary, checks local room for the chassis, then requests a route for
the chosen candidate. Refused candidates are remembered within bounded tactical
state; fallback selection follows refusal, never mere waiting. Search limits
remain limits on what was proved, not evidence that a hostile disappeared. A
firing perch scored at a cell center must also work from its resolved endpoint:
if arrival loses the promised firing lane or weapon band, the candidate is
refused and positioning continues. Actual firing legality remains authoritative
throughout. Optional firing improvements retain their detour limit measured in
physical travel; an exact mission destination may justify a longer route.

Deployment chooses a legal continuous point inside the deployment's allowed
area before constructing the body. It does not repair an existing chassis by
teleporting it. A delivery or finished frame without room for its actual body
must wait or decline placement under its owning system's rules. Spawn position,
foot plants, spatial lookup, and visible chassis begin at the same point.

## Direct control

The one-body session in `direct-control-nouns.md` may take over an exact live
Marine combat Mech in a friendly non-rescue lance. A chassis still being built,
a delivery payload, and a rescue pickup Mech are outside this contract. Entry
requires its actual body envelope to fit current terrain. Existing broken-squad
morale, forced fallback, structural squad retreat, and hard withdrawal retain
authority to refuse or release control. Personal chassis morale continues to
feed the shared survival rule without a separate manual-only threshold.

WASD supplies a speed-bounded desired velocity. Manual drive eases acceleration,
direction changes, and key-release braking according to relative handling mass.
The damped hips follow cursor aim without a hard translation alignment gate;
autonomous path following retains its eight-degree gate. WASD remains world-relative:
full forward speed, 72% lateral and 50% reverse, interpolated by actual hip bearing.
Reverse and side steps respond more heavily. The upper chassis tracks the cursor
independently within 145 degrees of either side of the hips.
Fire requires four-degree alignment and the installed mount's own legal arc.
Terrain sweep, speed, separation, and gait remain the shared chassis rules.
Only swept, achieved movement may survive as manual momentum after obstacle
contact, preserving legal sliding without stored wall pressure. Applied travel
remains the authority for gait and moving-fire effects. Entry, handback, pause,
and input suspension clear retained manual drive; natural key release brakes it.

The primary trigger requests all installed ground-direct mounts, each spending
its own ready ammunition and observing its mechanical cycle, flux admission, and burst state. Indirect
mounts are withheld. The live cursor owns torso aim and every scheduled
manual burst round. A refused release consumes its scheduled slot without
emitting a shot or refunding the trigger pack, so blocked or unaimed mounts
cannot hold a firing backlog or override current aim. Torso traversal between
rear limits stays inside the forward arc rather than crossing the blind wedge.
Entry, exit, and input suspension cancel queued rounds and aim without resetting mount clocks,
ammunition, flux, or replenishment progress. Mech accuracy uses its shared weapon
calculation without infantry training multipliers.

The actual posed barrel is the source for both autonomous and manual direct
fire. A structural obstruction between body and barrel blocks launch, including
when the rendered muzzle protrudes beyond the obstacle. Manual alignment is the
torso's, judged from the chassis centre, and the barrels converge on the point.
A pilot's rockets ground-burst where they are thrown, past nominal range at a
scatter cost; gun area payloads keep physical terminal-contact detonation, and
every rocket keeps missile interception. `ballistics-nouns.md` owns their shared
contact and arrival law.

Manual ownership cancels queued and active one-shot moves for this exact body.
It excludes the body from autonomous role allocation, doctrine relevance,
formation anchors, and shared arrival work; the remaining lance chooses an
autonomous lead. Real leader identity, membership, physical strength, screening,
and morale contributions remain intact. Mission, doctrine, and lance orders
remain current during control. Release discards stale route and firing work and
forces fresh allocation from the actual position and current command context.

## Authority flow

A spawn or delivery names a variant. The chassis profile supplies the body
across movement, targeting geometry, collision/separation, blast contact,
morale footprint, picking, appearance, and wreck continuity. It creates a
loadout, whose installed mounts drive firing, ammunition, resupply, and the
visible hardpoint layers. The role attached to that loadout feeds the mech
planner, independently of the hardware profile. Campaign support first freezes
callsign, variant, role, and installed subsystem from the active mech squad into
plain deployment values. The delivery power transports those values; landing
then constructs the live loadout and installs the frozen subsystem. The
callsign supplies the live unit's display name while the battle's own entity id
remains its identity.

Campaign faction identity crosses into presentation separately from that
hardware flow. At battle-screen attachment, the selected client's exact
campaign faction id selects the marine livery and the target faction id (or an
explicit defender comparison override) selects the defender livery. Tactical
`MARINE`/`DEFENDER` identity remains only the lookup side; it does not become a
campaign faction. The renderer resolves one complete livery family for that
side—three chassis plus thirteen weapon casings—with unknown human factions using
the practical Independent/mercenary treatment and automated or absent identity
retaining the base art. If any painted layer is unavailable, the entire mech
uses the complete base family rather than mixing liveries. This bridge is
presentation-only and never enters battle determinism, save data, spawning, or
mechanical authority.

Selecting an exact friendly mech during battle may request another effective
role, another lance order, a lance-wide Defend Area order, or a tactical move.
The role request affects only that mech; the lance and Defend Area requests
affect its whole battle lance; the tactical move temporarily affects only the
selected chassis's locomotion. The battle
applies each request at its serialized command boundary. Doctrine and lance
changes invalidate the movement and planning state owned by the changed layer
and replan immediately. Defend Area installs one player tactical assignment on
the battle lance and gives its specialized bounded-defense plan precedence over
the underlying mission until superseded. A move selects a provisional cell from
coarse connectivity, then waits for the selected chassis's physical route proof.
It clears only that member's old path and leaves the shared plan in place. It
keeps acquiring legal contacts and firing installed weapons while walking or
waiting. Arrival or proof refusal removes the override before ordinary doctrine
executes, so there is no planless handback tick. Broken-morale
survival suspends rather than erases the move; a hard withdrawal cancels it.
These interrupts preserve the mission assignment, legal contact picture,
morale, damage, ammunition, cooldowns, and deployed role. They cannot target an
enemy, non-mech, stale entity, or rescue payload, and never write back to
campaign state. Persistent doctrine authoring remains a Mech Lab responsibility.

Defender setup produces a deterministic sequence of variants, not an
interchangeable mech count. Risk, target conditions, and attacking force
decide which candidates are affordable; the selected sequence then reaches
battle setup unchanged. A DEBUG briefing holds a stable, explicitly rerollable
family roster; support preserves that order while partitioning it into physical
drops and coherent squads of up to four chassis. This deliberately exercises
the real air-delivery seam but does not confer ownership, inventory, salvage,
refit, lift, or campaign entitlement. Ordinary campaign state separately owns
an empty support-lance gantry group. It receives no chassis or spare subsystem
stock until the player fabricates or recovers them; legacy saves retain the
starter Bulwark they already owned. Fleet or employer sourcing
still determines whether Mech Support is available in an operation; ownership
determines the payload, not the entitlement to call it.
The Wayline depot chapter introduces a recovered Hound named Wayward, fitted
with a nose chaingun and empty missile mounts. It is a normal owned chassis
after victory, including when Wayline must recover and repair its wreck.
On Second Wind, this recovery also makes the reserved Mech Lab accessible and
restores its first berth. The other three gantries require separate paid repairs
owned by company campaign restoration; `company-view-nouns.md` owns those orders.
Mech fabrication consumes only working physical capacity, rechecked before a
material debit. Restoring a berth grants no chassis or component, and a smaller
working bay never deletes an already-owned mech.
The transfer is once-only and adds its installed parts to owned inventory.
The award also supplies one loose SRM-5 and Castle's optional, text-only gantry
induction. Return from debrief (including salvage settlement) leads to the lab.
Castle uses the shared portrait modal and isolated input scope above an unchanged
lab layout. Escape dismisses the current prompt; Close / Try It reveals the
controls, and a completed fitting action opens the next prompt. Later pauses the
guide until reopened from the room bar. The lesson selects Wayward's owning lance,
explains four gantries and active-lance
support, waits for a real SRM installation, then observes that rack returned to
stores and refitted into the Hound's single authored shoulder mount. Progress
and dismissal survive saves; presentation cannot grant another rack. An older
save that already owns Wayward receives the lesson package once on load.

New campaigns fabricate only the basic nose chaingun, SRM-5 and Hound pattern.
These are the existing baseline weapons, without a new quality modifier. Other
owned weapons remain refittable, but ownership does not confer their fabrication
pattern. The workshop checks permission before spending cargo, including drag
commits; older saves retain their prior unrestricted workshop. Wider pattern
acquisition is reserved for subsequent progression content.

A full lance receives it in a recovery group; neither existing mechs nor the
active-lance selection is overwritten. Later Broken Supply Route contracts
provide employer heavy lift for the active owned lance. Physical arrival claims
the chapter's mission authority so its AI can continue after direct-control
handback. Story recovery does not create a general repair or casualty economy.

The debug roster count has no scenario-authored maximum; every requested chassis
becomes part of the deterministic roster and therefore another four-chassis-or-less
support sortie, subject only to practical runtime resources.

## Laws

- **The picker chooses; it never edits.** An armor drop selects from the frozen
  manifest. It cannot change a fit, add a machine, or bring a machine twice: one
  already called (landed, lost, or still in the air) is not aboard again this
  mission. The AI commander's mech-drop allowance is a separate ledger and is
  untouched by the player's picks.

- **A field works produces from work done, never from elapsed time.** Its
  progress is the work of technicians actually at a servicing point in that bay —
  not standing in it, not walking across it, and not at its stores or its
  terminals, which are jobs the same person does on the same rotation. A shed on
  a clock would be a timer wearing a building's clothes: an attacker could stand
  in the doorway killing its crew and the line would run at exactly the same
  rate. Counting hands is what makes every consequence fall out on its own, and
  the difference is invisible from outside because both versions produce
  machines.
- **How built a machine is, is its structure.** Not a number beside a body but
  the body's own: welding raises it, a marine lowers it, and the machine is
  finished when it is whole. Nothing states what damage does to progress, because
  damage *is* what it does to progress — and the fragility comes out right for
  free, a keel being trivial to destroy and a nearly-finished machine being
  nearly a mech. A production line kept as a private counter can only be stopped
  by killing everybody adding to it; one standing in a gantry can be shot.
- **Work destroyed is not refunded.** A machine shot on the stocks is wreckage
  and the next one starts from a keel. That is what makes an attacker's rounds
  worth spending on the building rather than only on the people in it.
- **A captured works builds for whoever captured it.** A field works has no side
  of its own: it produces for whichever side its technicians belong to, and who
  those are is settled by who holds the ground. `ai-nouns.md` owns the crew and
  the replacement rule; what follows here is that taking a motor pool is taking
  its output rather than only denying it.
- **A shed builds the faction's own machines, not the catalog's.** Both halves:
  which chassis it lays down are the map faction's authored heavy-support cycle,
  in that cycle's order, less anything too heavy for a field shed; and what it
  hangs on the finished one is that faction's issue rather than the chassis's
  neutral production fit. So what drives out of a garrison's motor pool is
  indistinguishable from what was already patrolling past it — a Path shed turns
  out Hounds because a Path lance is Hounds. A shed with a second, quietly
  different opinion about what its faction fields would make every faction's
  motor pool the same building with a different roof.
- **The stock is the shed's, not the crew's.** A motor pool is tooling, jigs and
  racks of one faction's parts, and taking the ground it stands on changes none
  of that. A machine finished by marines in a captured Tri-Tachyon bay flies a
  marine flag, because they built it, and carries Tri-Tachyon guns, because that
  is what was in the racks. This is also the only answer that needs no invented
  doctrine for a side that has none, and it is what makes capturing a shed worth
  something beyond denial. Where the map has no faction behind it at all, the
  shed falls back to the whole light catalogue on a neutral fit — never to
  building nothing, which is indistinguishable from a shed whose crew have all
  been killed.
- **A shed may be tooled for vehicles instead** (`FabricationService.toolForVehicles`):
  the allies' base behind a cordon builds carriers and cars. The hull on the
  stocks is then the real vehicle, laid at a keel's share of its structure with
  its gun empty, so it is quiet while it is built and shootable where it stands;
  finishing it is welding it whole and loading its gun, with nothing swapped out.
  A new hull is laid only once the last has left the berth. A mission is told of
  every machine that rolls out (`onRollOut`), and may run its shed at a
  production line's pace rather than a garrison's sideline (`setPace`), still
  counted in hands.
- **The crew lays the keel.** A bay with nothing on its stocks starts the next
  machine on the first tick somebody is at the gantry to start it, so a crew that
  has stood down under fire lays nothing and a crew that is dead lays nothing
  ever again. No rule says so; it falls out of who does the work.
- **One machine on the stocks per bay, not one per berth.** A shed's crew is a
  handful of people and a rank of berths is six, so work spread across all of
  them finishes nothing — six machines at a sixth each for the whole battle. The
  bay publishes servicing at the berth its machine is standing in and at no
  other, so its crew is never sent to weld on empty air.
- **A built machine is an ordinary unit and is left unclaimed.** Nothing about
  having been made here rather than delivered here changes how it fights, who may
  command it, or how it dies. It is minted into an ordinary squad with no
  delivery-arm claim, because a claim of that kind outranks mission command and
  would leave the machine visible to its own side's commander and permanently
  immovable by it.
- Variant, physical geometry, and visible silhouette must agree. A lighter
  render scale may not retain heavy-sized picking, collision, hit, blast, or
  morale behavior.
- A livery consumes the accepted chassis silhouette and shared equipment
  geometry. Equipment color may change only through its authored paintable
  casing; exposed mechanisms, ordnance, and functional indicators remain the
  shared hardware. No livery may alter alpha footprint, hardpoint openings,
  layer order, runtime scale, or mechanical authority merely to strengthen
  faction flavor.
- Hardware and doctrine are independent. Do not encode a planner behavior in
  a variant name or infer a chassis solely from its assigned role.
- A battle role override changes only local tactical manner. It does not author
  mission destination, reveal a contact, waive survival law, or mutate the
  deployed or campaign role.
- A lance order changes only battle-local coordination. Form on Lead is the
  default. Free Reign removes generic lance formation and the Brawler's generic
  support tether, but it does not erase the shared assignment, grant hostile
  knowledge, or suppress ally geometry required by a selected doctrine.
- A tactical move order changes one Mech's locomotion only. It does not become
  a squad assignment, acquire directive authority, change doctrine or lance
  order, disclose a hostile, or silence installed weapons.
- A Defend Area order changes the whole battle lance's temporary tactical
  assignment, never one chassis's doctrine or the Form on Lead / Free Reign
  coordination rule. Its center and radius constrain positioning without
  disclosing contacts or suspending ordinary weapons.
- Tactical move arrival is a completion boundary, not a new hold posture. A
  refused physical route likewise releases the override instead of leaving an
  impossible order stalled. The order returns to the still-current squad plan
  and effective doctrine. Broken-morale survival may suspend it, and withdrawal may cancel it.
- Mixed-role lances apply doctrine per live mech. One member's role may not
  starve another member's doctrine through a shared squad plan.
- Every specialist loses a meaningful capability as well as durability; a
  lighter chassis cannot be a discounted all-range Bulwark.
- Chassis structure, armor capacity, and armor rating are separate values. Bulwark
  remains the high-capacity, high-rating anchor; Hound trades armor endurance for
  mobility; Warden has the least protection and depends on range and a screen.
- Mount absence is a tactical weakness. Firing, continuation, AI utility,
  resupply, and rendering must operate only on installed components. A mount
  destroyed in battle is an absent mount, not a disabled one.
- A mech dies only through its torso. Every other section at zero costs a
  capability and passes further damage to the torso; section pools always sum to
  the frame's armor capacity and the chassis structure.
- Missile weapon hardware owns capacity and projectile behavior; the installed
  replenisher owns refill cadence. Upgrade content changes the subsystem rather
  than encoding reload speed in a chassis, role, or weapon definition.
- A rotary gun's belt and hopper belong to the gun: a better feed is a better gun,
  never a chassis or role value. One rule advances spin and belt for the pilot and
  the AI. An autonomous gunner leases the trigger while a target is exposed, keeps
  the barrels turning while engaged without a shot, and keeps a reserve: below a
  fifth of the belt it stops asking for rounds until the hopper has put a third
  back, so it eases off rather than running dry. A pilot may empty the belt. A
  round a gate refuses uses its slot, so a blocked gun never dumps a backlog.
- Owned subsystem quantity includes installed copies. A failed refit changes
  neither inventory accounting nor the target mech's installed loadout.
- A custom refit validates socket type, complete rectangular footprint, component inventory,
  and the fabrication bill in one atomic command. An omni socket
  accepts several equipment types; it does not waive footprint or budgets.
- Spatial selection and drag previews have no mutation authority. Dropping a
  component may propose a placement, but only a successful campaign command
  changes the installed loadout.
- The fitting doll consumes the same ordered layer composition and hull-relative
  transforms as battle rendering. A preview may choose a static maintenance pose,
  but it may not approximate mount scale, pivots, absence, or above/below-chassis
  order independently. The gantry rotates that complete composition south toward
  the player; it does not rotate individual equipment layers independently.
- The fitting room is one lance-scale facility battle scene rendered through the
  shared battle camera and ordered render systems. Its four striped-and-grated
  gantry pads, service gallery, storage machinery, and connected ship corridor are
  real grid-aligned room cells and registered fixtures; its south edge is the vehicle
  entrance. Responsive layout may change the camera's fitted cell size or choose
  a closer framing, but it may not canvas-fit the
  selected asset or distort its physical size relative to technicians, tiles,
  props, or another chassis.
- Socket overlays consume the selected asset's authored fitting layout. Recessed
  metal equipment wells frame the actual weapon art around the physical doll;
  quiet internal grids retain the socket's authored dimensions. Empty mounts show
  a hardware silhouette and compatibility label. The CORE, CAPACITORS and
  MINI_FAB wells host internals rather than weapons, and the workspace shows the
  engine budget beside them.
  Amber corners identify selection; green alignment scans and a release prompt
  identify an accepted proposed drop, and red corners explicitly reject one.
  Motion follows the presentation clock without mutating campaign hardware.
  The complete well is the pointer target. Catalog art can be dragged onto a
  compatible mount, installed weapons can move to a compatible vacant mount, and
  dropping them over stores returns them to finite inventory. The carried weapon
  stays visible above the document; Escape or an outside drop cancels the gesture.
  All commits use the atomic campaign refit commands. An explicit empty weapon
  mount remains empty in the frozen deployment and fixture serialization. An
  authored empty socket stays visible while an absent socket produces no well.
- The catalog beside the doll filters by hardpoint category or by cargo stock
  without losing the selected socket's context, and clicking a dock on the doll
  selects that socket. A card in free cargo stock offers a spare install with no
  bill; one that must be fabricated shows its material bill and affordability.
  Weapon cards and the equipped rack read damage, range, trigger capacity and
  ammunition, and a candidate shows its difference from what the selected socket
  holds. An occupied, unlocked mount can be stripped back to cargo in one atomic
  command. Factory-locked internals read as recessed rather than as swappable
  mounts. Hover ghosting on docks and on the top performance meters is
  presentation only: it projects a candidate and never changes hardware.
- Each performance meter wears the colour of the part that moves it (plate for
  armor, engine blue for mobility, flux violet for capacitors and dissipation,
  weapon amber for reach, missile green for racks) and reads against the
  **stock frame**: the chassis's roll-out weapons on its stock internals. The
  stock share is a dim shade with a tick where stock stands, what the fit adds
  past it is the full colour, and a fit that falls short of stock shows the
  shortfall in red.
- The garage is a flat top-down, non-advancing room simulation assembled from the
  battle renderer's indoor tileset cells and props. Its mech and workers are real
  battle entities consumed by the ordinary unit render system. The retained host
  supplies only a bounded viewport and camera: battle HUD, input, selection,
  audio, fog, combat decorators, and surface-relief targets remain detached.
  It does not advance combat AI, but deterministic workshop jobs may author real
  technician positions, walk poses, facing, tools, and effects as presentation time
  advances. Welding sparks exist only while a technician is dwelling at a welding
  job; they are not a fixed canvas ornament. Job motion continues while the player
  inspects equipment, but it never decides refit duration, stock, or command success.
- Live rendering and headless evidence consume one room layout: ground kind,
  perimeter walls, gantry overlays, props, workers, and every lance asset occupy authored
  battle-grid cells. Headless evidence collects the ordinary battle renderer's command
  list and substitutes only a Java2D drain; it may not invent percentage-positioned
  scenery, off-grid props, actor approximations, or a second gantry illustration.
- The room opens on a facility-wide camera frame that always includes all four
  physical pads—including vacant ones—plus the adjacent service and circulation
  space. The camera does not crop to the number of assigned assets. This is a
  non-selected state: it gives the room its full width, labels all four gantries,
  and exposes no equipment catalog, performance strip, or socket rack. **Pads are
  lettered A to D** in gantry order: the letter is stencilled on an amber plate in each
  pad's corner, and the cards, headings and messages name a pad by the same letter,
  never by a number. A vacant pad is marked as a build site in the BUY amber (a faint
  wash, corner brackets, the fabrication cog and BUILD), never a bare plus. The status
  line on the right of the heading says what the current view asks for: select a pad,
  choose a chassis or vehicle, or select a mount. Selecting
  an occupied gantry establishes the fitting asset and eases
  the same battle camera into it; only then does the fitting workspace reveal those
  chassis-scoped controls. Selecting a vacant gantry instead opens the chassis- and
  vehicle-pattern catalog, laid out like the parts list: each pattern is a row in the
  dark well with its bill and a BUY in the build strip. **Clicking the card is not
  buying:** it stands the pattern on the pad as a translucent preview at the scene's own
  scale (a mech in its standard fit, a vehicle nose-up) and fills the PATTERN card in
  the right-hand column, where an occupied pad shows its specifications, with the
  maker's crest, name, procurement credit and kind, the standard fit's doll (the fitting
  schematic, read-only; a vehicle shows its hull), its figures as the specifications' own
  bars, its lore and its bill. Only a BUY,
  on the row or the card's BUILD, spends anything. Every chassis and vehicle pattern is
  credited to a faction in the loadout cards' voice (`MechPatternCredits`: a square
  faction crest and a procurement line such as the Ordnance Board heavy schedule), and
  the credit must agree with which faction's markets weight the pattern up
  (`MechPatternPicker`): a navy's issue is that navy's, and a pickup-based field rig is a
  frontier or pirate workshop's. With nothing chosen the first pattern
  the filter shows is previewed. A successful cargo-backed BUILD puts one persistent
  standard-fit chassis, or one vehicle, onto that exact pad and immediately makes
  it part of the active lance. A gantry holding a vehicle is selectable and shows its
  callsign, hull, weight class and read-only hull stats (structure, armour, mobility,
  passengers, turret); it exposes no sockets, catalog or doll, and its pad is never
  drawn as vacant. The build catalog filters on two axes at once, as symbols in
  the strip the parts catalog uses: kind (walkers or vehicles) and weight class (light,
  medium, heavy: rank chevrons on a danger ramp, since weight is how the player reads a
  machine's threat). Each axis toggles on its own; none chosen lists everything. Gantries order mechs first, then vehicles.
- **Scrapping frees a pad.** An occupied pad's heading carries SCRAP, which takes two
  presses: the first arms it and says what comes back, the second breaks the machine
  up. Nothing fitted is lost (weapons, internals and the replenisher stay owned and are
  spares in stores again) and half the chassis or vehicle bill, rounded down, returns
  to the ship's hold, never to a depot it may leave behind. This is the opposite of a
  battle loss, which takes the fitted parts with the frame (`MechWorkshop.scrapMech`).
  The lance returns to its overview with that pad vacant. Returning
  through the current Mech Lab room route clears
  that selection and restores the overview. No camera transition moves, respawns,
  pauses, or rebuilds garage contents to fake motion.
- The fitting header names the pad by its letter. Navigation wraps across the four physical pads; arriving
  at a vacant pad clears the chassis selection and exposes fabrication patterns rather
  than rendering chassis controls against nothing. Lance browsing remains a
  separate direct-jump surface, and it is pictures first: each lance shows one
  lettered tile per pad (the machine standing there with its fitted guns, or an empty pad), and
  the selected lance lists every pad as a card with its thumbnail, vacant pads included
  as BUILD cards, so what a lance lacks reads as plainly as what it holds.
- Fabrication spends three ordinary player-fleet materials: metals, heavy machinery
  and transplutonics (the base game's `rare_metals`). The lab spends no supplies.
  **Hardware is priced, not authored.** A weapon is worth its mass (footprint, tier
  weight and heavy build) at a common rate, times its access tier; an internal is
  worth its role's stock part, times its tier. What the part is made of decides
  which materials pay that worth: plate is mostly metals, mechanisms and engines
  mostly machinery, energy weapons, guidance and capacitance mostly
  transplutonics. A bigger or rarer part therefore costs more without a second
  table to keep in step with the first (`MechFabricationCatalog.weaponValue`).
  Chassis and vehicle bills stay authored, because they carry the armor-per-credit
  parity between hulls and walkers. Workshop tooling saves a fifth of the heavy
  machinery (and of a boat job's supplies). A shared icon-and-count strip shows fleet material stock; each
  fabrication pattern shows only its required quantities beside commodity icons.
  Hover details supply material names, exact cargo counts and shortages. Icons and
  names resolve from the base game's live commodity specifications, so the Mech Lab
  does not copy or re-author cargo art.
- A weapon commit uses a free owned assembly when available. Otherwise it rechecks the
  complete material bill, consumes it atomically, creates one finite owned assembly,
  and installs it; the outgoing assembly becomes free stock. A chassis commit performs
  the same recheck before creating identity, default doctrine, standard replenisher,
  and the variant's standard roll-out weapons. Failed compatibility, capacity, cargo,
  or gantry checks change neither cargo nor campaign hardware.
- The active home-deck gantries reproject the campaign loadouts after every successful
  commit. Weapon layers and newly fabricated chassis therefore change in the physical
  room immediately, while mission commitment separately freezes the same components
  for battle.
- Campaign-to-battle deployment freezes values. Live battle code does not read
  or mutate the campaign mech, squad, or fleet inventory.
- Gun-launched HE is a ballistic shot whose timed detonation owns splash and
  structural damage. It must not be represented as a boost-ramping missile
  merely to obtain spectacle.
- Defender variants replace equivalent encounter allocation; they do not add
  bodies. Ordinary encounters apply the defender-vs-attacker force budget and
  let static turrets spend from the remainder after mobile defenders. Conquest
  deliberately bypasses that budget and retains its authored mechs and static
  weapons as part of the fixed siege.
- Production behavior is deterministic from its setup inputs. Debug variation
  is explicitly scoped iteration scaffolding and must not leak into campaign
  progression.

## Extension boundaries

A power suit is wearable infantry equipment, not a mech chassis. Its named
marine remains in the personnel roster, infantry squad, ordinary lift and
casualty authority; the armor pattern owns its class and protection. Suit
shoulder hardware does not imply mech sockets, shared flux, component inventory,
lances, the Mech Lab, or Mech Support. `power-suit-infantry.md` owns that category.

A new chassis requires a distinct information or combat doctrine and a real
capability it gives up; hardware variety alone does not earn another variant.

General salvage acquisition, ammunition modules, wider recipe unlock progression,
and chassis recovery still belong to future progression and economy authority;
`heavy-equipment-progression.md` sets their direction (learned patterns, built
assemblies, lift classes).
The current Mech Lab owns a bounded workshop catalog: registered weapon, internal and chassis
patterns, typed capacity checks, finite component stores, and atomic player-cargo
fabrication. It does not imply that future salvage or markets should bypass those
authorities or that every later pattern begins discovered.

The doll presentation may later host tanks and other scarce heavy armor, but it must
consume an asset-class-specific socket layout. Sharing selection, catalog, drag, and
validation presentation does not make a vehicle use a mech chassis or mech mount
schema.

The fitting gantry's reference composition is wide-screen: equipment catalog,
physical bay, and socket rack remain simultaneously visible. Narrower viewports and
larger user UI scales preserve the same commands through bounded scrolling and
shorter labels; they are not required to preserve the wide view's information density.

A future shared weapon catalog may data-drive projectile, weapon, and mount
definitions, but it must preserve the chassis/mount/weapon authority split.

## Boundaries

The AI feature owns general planner machinery; mechs provide role-specific
hardware and constraints for it to command. Air delivery owns when a payload
lands, while this feature owns the landed variant/loadout identity.
`moddable-weapons-nouns.md` owns the future shared weapon-catalog direction;
mechs owns the mechanical and tactical meaning of a chassis and hardpoint.
`combat-durability-nouns.md` owns how the chassis's authored structure and
armor profile receives damage and exposes an armor-break transition.
Rendering consumes the variant and installed mounts to show the same body that
simulation uses; it does not choose stats or doctrine.
Infantry equipment recovery owns which ordinary marine fetches or picks up a
dropped demolition kit; mechs own the classification that keeps a machine out of
it. Live `MECH_LOADOUT` membership is that classification, never faction, unit
type or rendering, so a future chassis is excluded without the recovery sweep
knowing it. A drop with only mechs nearby stays unassigned.


## Flux reservoir and weapon mechanisms

The battle loadout owns one flux reservoir whose capacity and dissipation are the
deployment's frozen frame values. The stock frame gives 100 units of flux capacity
dissipating at 6 units/second, and every enemy, employer and debug mech fights on it; a
fitted capacitor bank, vent array and engine change it for one player mech. Current flux
is battle state, not a campaign repair cost.

The words are Starsector's own, because the game already has this concept: weapons
build **flux**, the frame's **capacity** bounds it, **dissipation** sheds it every second,
and a frame forced to stop firing until it drops is **venting**. The internals follow the
same vocabulary: a **capacitor bank** sets capacity and base dissipation, a **vent array**
adds dissipation. A mech's flux and a warship's flux share one gauge on the direct-control
HUD. Never call it heat, which reads as a separate mod mechanic.

A weapon component owns its mechanical trigger-to-trigger period and its flux
reservation. A rotary gun commits its flux round by round instead (1.25 a round
for the stock chaingun). That is enough that even a single-gun frame cannot empty
its belt in one hold: a stock Hound, venting 6 a second against 15 at full rate,
reaches its 100-flux cap about 140 rounds into a 200-round belt (owner,
2026-10-23, after 0.75 let it dump the whole belt with almost no flux). A Bulwark
holding both arm guns runs hot within seconds.
SRM-5 loads every 1.8 seconds for a two-rocket packet
costing 28 flux. Burst continuation must also finish before another trigger.
Projectile damage and spacing are unchanged. Larger and specialist components
have explicit profiles; ordinary infantry/vehicle weapon cadence is unchanged.

AI and manual triggers share the 100-unit admission ceiling. The first emitted
round commits the whole packet's flux and ammunition, allowing scheduled rounds
to complete even if the chassis becomes hot. Blocked/unaimed first rounds spend
nothing; cancelled packets retain spent flux and ammo. Dissipation runs once in the
serial heavy-weapon pass, including on unselected mounts and autonomous chassis.
Direct-control entry, exit, pause and input suspension never reset these resources.

**A held rack chains its salvos** (`SalvoChain`). A rack's trigger is one
salvo; once a hold has fired one, the next follows as soon as the last has gone, without the
rack's cycle, until the button is released or the rack is empty, and the cycle then runs from the
last salvo. So an LRM-15 is 5 on a click, 10 on a short hold and 15 on a long one, and a deeper rack
is a bigger spike. A fresh press after release waits out the cycle like any trigger. A chained
salvo reserves half its trigger's flux (owner, 2026-10-23): at the full share an LRM-15's fifteen
would cost 120 and could never leave inside the stock 100, while at half they cost 80, and flux still
bounds the deepest racks (an SRM-15's six salvos would cost 147). The replenisher restores the
spent salvos one pack at a time, as ever. An autonomous mech chooses its count on a fresh trigger:
one salvo at a soft target, the whole load at a mech or at a target its own side's point defence
covers, and half the load (rounded up) at other armor; a chained salvo goes only to the body its
chain began on. A vehicle turret's missile launcher obeys the same hold and choice, except that its
crew chains at most three salvos (owner, 2026-10-23): a vehicle magazine is its whole stock and is
never replenished in battle, so it is not one rack's buffer to empty at the first machine it sees.

**Against a machine, the mount that best defeats its armor fires first.** The
reservoir admits one packet at a time, so whichever mount an autonomous mech tries
first after a release takes the flux. Against a mech or a ground vehicle, mounts
are tried in order of armor efficiency against that target's rating
(`clamp(penetration / rating, 0, 1)`, the better of a weapon's payloads), with
slot order kept on ties (`MechFireOrder`). Against anything else the slot order
stands: arm mounts, left shoulder, right shoulder. So a Bulwark's SRM shoulders, its
anti-armor weapons, are no longer starved by the chaingun against a machine,
while its fire at infantry is exactly what it was. An anti-armor rack (penetration 30) defeats any mech's plate outright, so a
heavy cannon, which also does, keeps its slot ahead of it, a bastion yields to
it, and a fragmentation rack (penetration 4) goes after every arm gun. Manual fire follows the player's groups, not this order.
`-Dbattle.mech.armorFireOrder=false` is the control.

Thresholds are fractions of capacity, quoted here at the stock 100. AI refuses a
trigger that would exceed 90 flux. At 75 it holds further triggers until 40,
continuing its standing movement and assignment. Manual fire may spend the extra
headroom; at 95 it vents until 55, with no damage or movement shutdown. A trigger that would exceed capacity
waits for enough headroom even below that interlock. The HUD shows shared flux
and per-mount flux holds separately from cycler/loader progress and pack
supply. Mech Lab hover sheets expose both costs and dissipation time per burst.

Flux shows on the body as a ground hull's gun shows it, in the same look
(`FluxHaze`, `convoy-nouns.md`): a purple haze over the vents across the torso's
back, turning with the torso, that thickens with the square of the reservoir's
share, wisps drifting aft off it, and a bright lavender plume for as long as the
mech vents. It is drawn from current flux and simulation time alone.

Evidence: `hound-flux` compares held fire, paced bursts, autonomous dissipation and
reverse/strafe motion; its snapshot suite overlays the live production HUD on
1920x1080 battle frames at 100% scale. Focused tests cover admission, single-charge
salvos, independent mechanical clocks, resource-preserving handback and terrain-safe
manual movement.
