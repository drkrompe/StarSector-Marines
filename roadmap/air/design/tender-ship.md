# Tender ship — a mobile drone and construction hull

Status: ACTIVE

Written: 2026-10-12

Updated: 2026-10-14 — builder cues and interceptor pod retained; Working Passage low hover, physical departure and owned campaign support boundaries documented.

Read `air-nouns.md` (**Overhead ships**, **Tenders**) first. Vocabulary for the hull, the hover model and point defence is that doc's; `warship-behaviours.md` owns what a posture is. This doc says what a tender is, why it is built the way it is, and what is left.

## What the owner asked for

The vanilla Shepherd ("Drone Tender"): a hull with a built-in wing of automated drones that defends itself with them, and that also does construction on the battlefield, setting up turrets and other deployables. It is already a `ShuttleType` (`SHEPHERD`), a medium utility lift boat in the campaign roster. Standing direction applies unchanged: ships are overpowered by design and gated by context, not balance, and a posture or role changes what a ship chooses to do and never how hard anything hits.

## Decision 1: a tender is a separate unit kind, not a role on the boat

The boat Shepherd and the tender Shepherd share a sprite and a hull id and nothing else.

- The **boat** is an `AirBody` craft that lifts squads between a landing zone and the map edge, exists for one sortie, and lives in the air layer's shuttle machinery.
- The **tender** is an overhead hull. It hovers, it stays, it has a complement and a job, and nobody boards it. That is the `Warship` body's whole shape, so the tender is **composed over a `Warship`** rather than extending it or re-implementing flight: the body is fitted from the same `.ship`, hull table and sprite, registered with the same warship system, and flown by the same helm.

A tender is therefore one more thing that can be put over a battle by the same hands that put a capital there (a mission, a scene, the debug picker, a campaign fleet member). It never competes with the boat for a lift slot.

**A campaign Shepherd is usable as a tender.** `Tender.fit` takes a `FleetMemberAPI` the way `Warship.fit` does and reads the hull from the member's own variant. Stock fitting remains available for explicit debug and mission-supplied hosts. Ordinary campaign support validates the committed source against current fleet ownership before construction; stock fitting cannot replace a member sold or lost after commitment. `shepherd-recovery.md` owns Working Passage's implemented award of the actual named Morrow and willing minimum crew after physical departure, including its permanent once-only receipt. Live campaign handover and subsequent support acceptance remain open. Hull condition and the member's built-in mods are not read yet.

Rejected: a role flag on the shuttle mission. The shuttle machinery is built around pads, a mission state machine and a payload, none of which a tender has, and the tender would have inherited the shuttle's `AirBody` flight (no shadow, no hull table, no hover station) to lose the warship body's.

## Vocabulary

- **Tender** — the hull plus its complement, stock and line of orders. The only new noun that owns state.
- **Complement** — the built-in wing: the Shepherd's `borer_wing` names the number (six). Half are **builder drones**, half **defence drones**.
- **Defence drone** — an ordinary armed drone unit on the tender's side, anchored to the tender instead of to a hub.
- **Builder drone** — the same unit, unarmed. It exists to work a site and has no use for a target search.
- **Build option** — a deployable that already exists, its cost in charges (spent only when the tender is metered) and the time a full crew needs. Not a new kind of thing.
- **Charge** — an optional cap on builds. It defaults to unlimited (building is free), so nothing is drawn; on a metered tender a laid order draws its option's charge and a cancelled or failed order returns it.
- **Construction order / build site** — one option at one cell. Phases: queued, transit, working, then complete, cancelled or failed.
- **Standoff** — the point a few cells short of the site where the tender hovers while its builders fly out, so the work is visible.
- **Known hostile** — one the tender's side knows of: a contact its squads remember, at the cell it was last seen, or one inside the overhead sight of the hull or a drone it has out (20 cells).
- **Stood down** — a site with a known hostile inside the threat radius, or one that is under fire: no work is done and the builders go home.
- **Under fire** — a site one of whose builders was hit or shot down in the last eight seconds. It is blocked until that long passes without another hit.

## What a tender can build

Only deployables that already exist, through the paths they already have. It builds four **catalog turrets** (vulcan, arbalest, dual flak, heavy mortar): each a `TurretCatalogRegistry` structure placed through `MapTurret.create`, exactly the entity a defence post stamps, so it fires, takes fire, shows a durability bar and dies through the ordinary pipelines. A fifth option, the **interceptor pod**, runs the same construction order and differs only at completion (`BuildOption.Kind`).

Candidates, in the order they cost least to add (each is a `BuildOption` plus, where noted, a placement path):

| Candidate | Path it would use | Note |
| --- | --- | --- |
| Other catalog turrets (arbalest, flak, mortar) | the same structure path | built: number keys 1-4, vulcan first. Time and cost scale from the vulcan (12 s, 1 charge) by the structure's weight: arbalest 15 s / 2, dual flak 17 s / 2, heavy mortar 18 s / 3. Grenade launcher, locust, heavy MG and hephaestus are further data-only candidates once a sixth slot or a selector exists |
| Point-defence emplacement | `PointDefenseService.queuePlacement` | built: key 5, 8 s / 1 charge. The structure and spec are the carried Palisade pod's (45 s life, 8-round magazine, 44 structure plus armour), so the item stays the one authority; it is lighter than any turret and so quicker. Completion queues the placement with the tender's faction and no carrier. Number keys were extended to 1-5 rather than replacing a turret. The stance AI considers turret kinds only: the pod is quicker than every turret and would win ATTACK every time, and an unpiloted tender has no read on incoming ordnance |
| Revetment / cover screen | `DeployedCoverService.queuePlacement` | a boundary feature, not an entity, and needs a facing |
| Sensor, repair, supply | none yet | wait on the systems that would use them |

An option never restates what the structure is. Durability, weapon, magazine and art come from the catalog.

## How it builds

1. A site is laid on open, walkable ground that no other site is within two cells of. A metered tender draws the charge; a refused site draws nothing.
2. The tender sets out for a standoff point five cells short of the site, on the side it is already on, and holds station there.
3. Within a couple of cells of the standoff and slow, it is on station. The builders launch from its bay and fly out to a ring around the site.
4. The builders leave the bay one at a time, 0.55 s apart, fly out through the bay's mouth, and each takes its own **station** on a ring round the site, starting with the side the tender is on. A station is kept for the whole job; the drone hovers on it with a small jitter of its own, turns to face the site, and strikes a welding arc in short bursts of its own (drawn from the drone's position and the tick, with sparks at the landing point, and a quiet crackle, see **Cues** below). Progress accrues with the builders at the site: linear in how many, capped at the complement. The option's time is the time a **full crew** needs; one builder takes three times as long and none take forever.
5. When the work is done the structure is spawned on the cell and the builders fly home in the order they left, released 0.7 s apart, along a short approach line to the bay, docking on arrival. The tender moves to the next order meanwhile.

**It is interruptible, by what the tender knows and by what happens to it.** Two things stand the work down: no progress, and the builders fly home into the bay (a flight, in the same order) where nothing can shoot them.

- **A known hostile inside the threat radius of the site** (30 cells, wider than any rifle's reach). The radius is wide on purpose: a builder that waits to be shot at from outside a narrow radius is a loss for nothing, and the first version of this lost every builder to a patrol that never came inside it.
- **A hit.** A builder hit or shot down puts the site under fire, and it stays blocked until eight seconds pass without another hit. The work then resumes if no known hostile is near.

The cost is real and is the context gate the owner wants: a tender builds on ground its side holds or its screen can clear, and cannot throw a turret up in the middle of an enemy compound.

**Cues** (owner decision: the earlier "no new audio" is reversed). Builders are heard in the game's own sounds, positionally, and shown with presentation-only effects, all driven by events the serial tender tick records (`TenderCues`) and never read back, so nothing presented can reach the simulation. A *weld* crackle (`mote_attractor_impact_emp_arc`, quiet) sounds at the site while any builder's deterministic torch burst is lit, at most once per nine ticks per site so a crew of three is one crackle. A *hit* on a builder, the moment the hp drop is seen, throws a short burst of jagged blue-white arcs from it (hashed from the builder id and tick) with a zap (`shock_repeater_emp_impact`). A builder killed outright by one hit is simply lost (its wreck is its own effect); the stun shows on hits it survives. The first hit that stands a site down also gives one soft ping (`mine_ping`) at the site, then the red pulse. A finished turret gives a chime (`ui_build_industry`) and a ring of green with sparks. A hostile tender's cues are heard and drawn only where the map has been revealed, as its torch arcs are. Which sounds read well is a live-listen judgement; the ids are one-line swaps in `TenderAudio`.

**Knowledge, not omniscience** (owner decision). The first version stood a site down for any hostile inside the radius, seen or not, so builders fled from enemies nobody had spotted and the AI never laid a site an unseen picket would contest. That read as cheating. A tender knows what its side knows: the contacts its squads remember, at the cell each was last seen, and whatever its own eyes see from the air (the hull and every drone it has out, 20 cells round each). What it does not know can surprise it, and being surprised is a hit on a builder: stunned and blocked, rather than warned in advance. An unseen marksman therefore keeps a site blocked for as long as it keeps shooting, and costs a builder each time the work resumes into its sights; clearing it is the screen's job, or the squads'. The site marker pulses red and the pilot's site card reads UNDER FIRE while a site is blocked. `-Dbattle.tender.fairThreat=false` is the control run with the old rule.

**Building is free for now** (owner decision): builds are unlimited and the readouts say "builds free" rather than show a count that means nothing. The charge cap is kept so a cost can be added by changing `Tender.DEFAULT_STOCK` (or a mission's `Tender.stock(int)`) with each option's `cost` already authored; a metered tender then refuses a site it cannot pay for. What does bound a tender is its complement: a drone lost stays lost for the battle, because a wing that refills itself makes the complement a decoration.

## How its drones defend

Defence drones are the shipped drone: the same unit, weapon, hit points and swarm action a hub launches. The one change is where home is. A drone squad now has an optional **anchor** (`DroneAnchor`, implemented by the tender) beside its hub; the action's three reads of the hub position go through one helper that returns the hub's or the anchor's. Patrol sectors, the engage orbit, the pursuit latch and the leash all work around a moving ship unchanged, and the squad's goal is relevant only while the anchor lives.

**Their look is the Shepherd's own.** Builders and defenders are drawn as the Borer, the drone the hull's `borer_wing` launches in fleet combat, read from the drone hull's spec and drawn at hull scale (under a cell long beside a hull of about four), so they read as the player knows them. The look rides the drone's body as a presentation-only variant, so the falling wreck wears it too, and a hub's drones keep the shipped sprite. Stats, weapon and behaviour do not depend on the look.

They are not a garrison. A drone has eighteen hit points and a few rifle bursts end it. The recorded finding is that a screen of three stops a lone raider and loses to four; that is the shipped drone's strength and it is left alone, so a tender is no tougher for being a ship. If a tender is meant to hold ground against a fire team, the lever is more or better drones (a wing refit, a story), not a thicker drone.

**The wing is a swarm tied to a moving ship.** The same anchor seam that lets a hub squad screen a hub lets a carrier say its squad is an *escort*, which changes four things and nothing else (a hub squad takes none of them):

- **Sorties are flights.** A drone leaves through the bay's mouth, staggered 0.8 s apart, on its own fan bearing, and flies to an exit point five cells out before it goes about its business. A recall, a stood-down build, or a retreat sends it to an approach point just outside the mouth and then in; it docks only on arrival, never by teleport. The bay moves with the hull, so the goal is led by the bay's velocity for a few seconds (capped) and a drone catches a moving tender. A recalled wing is released 0.4 s apart. Each leg is computed once per tick in the serial part of the tick and published as an immutable goal; the swarm action only reads it.
- **A loose ring at ease.** In place of random patrol waypoints the escort loiters on its own share of a ring round the hull, drifting slowly round it, breathing in and out on its own phase, turning with the hull's heading and riding a little ahead of it. A threat inside the hull's reach draws the ring in tight. Slots are the drone's place in the carrier's roster, stable across squad replans.
- **Engage what comes into reach, then break off.** The tender scans (five times a second) for the hostile nearest the hull inside 30 cells; escorts that can shoot it prefer it to what is nearest themselves, and a threat further out than the leash (24 cells) plus half a weapon range is not chased. The engage orbit is clamped to the leash, so a raider that flees past it is let go; with no target the drone falls back to the ring.
- **No stacking.** Neighbours inside 2.2 cells lean each other's goals apart (applied after the leash clamp, or goals pulled onto the boundary would collapse onto one point). Neighbour positions are a snapshot published in the serial part of the tick, because a read of a position another worker is moving made the first version nondeterministic.

**A badly hurt drone goes home rather than die pointlessly.** Below 40% hit points a defence drone breaks off and flies home like a recall; a recall withdrawn does not turn a hurt drone round. It spends ten seconds in the bay being repaired (only that drone's slot is held; the rest launch as usual) and flies out at full strength. A drone shot down on the way is lost for the battle, as before. All variation between drones is hash-seeded from the drone's id, never drawn from the simulation's random stream, so a replay flies the same wing.

On loss of the hull its drones go with it, as a hub's do. On a clean departure they are recovered.

## Point defence: the drones' mining lasers

As in the game, the Shepherd's drones are its point defence, and the tender fits no flak of its own. Each defence drone carries the Borer's mining laser (`drone_borer`'s only mount) and burns hostile ordnance with it, through the machinery warship flak and carried pods already use: the `Interceptable` bodies warships have in flight (`landingX/Y` added so a defender can tell what is bound for it) plus opted-in ground projectiles, with the tracer posted as a warship point-defence mark so the existing renderer draws the weapon's own beam. Its lasers' sounds are the game's own; the builders' cues are under **Cues**.

- **Everything is the laser's row in the weapon table.** Reach is its range at ground scale (about 27 cells), strength its damage per second, tracking its mount turn rate. Nothing is authored per drone.
- **A beam is a dwell, not a die roll.** It deals its damage per second while on the bearing and the body is destroyed when the damage taken reaches its hit points. That is what makes it weak and gives saturation honestly: at 30 per second a 60-hit-point Locust costs one drone two seconds, a three-drone wing on one body burns it in under a second but crosses a window of only a second or two, so it stops missiles that arrive singly and about one of two that arrive together, a swarm of forty lands almost untouched, and a torpedo is beyond it. The mount slews at its turn rate, so a body that crosses faster than the mount follows is not burnt.
- **Whom it protects.** Ordnance bound for the hull (it lands within the hull's half length plus a few cells) outranks everything; then whatever else crosses the drone's own reach, which is the ground it overflies, not an umbrella around the hull. Within a group the body that lands soonest goes first and the wing does not spread: a slow beam wastes the window if it is split.
- **Who fires.** Only a defence drone that is out of the bay, alive and not pursuing a raider. A drone with a pursuit latch is away fighting and intercepts nothing; a docked or recalled drone is not on the field; a builder never fires.
- **It does not make the tender a combat ship.** No guns, no flux, no extra hit points. The rejected alternative was reusing the warship per-engagement hit roll for the drones: it ignores damage per second and would have made a wing of three stop a Locust swarm, which the laser cannot.

The mining laser's mount arc in its `.ship` slot (30 degrees) is not applied: the drone body turns with its motion and the mount is treated as a turret at its table turn rate. Mount arc and the drone's own heading are the open refinement.

## Vulnerability

**A recovering tender can use a full-size low hover.** Working Passage supplies
Morrow at the same 24 by 20-cell scale as her grounded body. Her fixed attitude
and swept footprint must clear terrain through mission travel, remote movement
and direct control. Construction stays within the moving hull's working reach,
outside its footprint, and ordinary builders, threat stand-down and turret damage
still own the job. Completed or cancelled remote build work releases mission
travel; explicit Hold and Move continue to own the helm. Climb-out uses ordinary
propulsion and cloud-deck presentation, but the mission accepts only the whole
hull physically leaving the map. Timeout removal is a failed departure, never
proof of transfer. The story owns critical abort, recovery and fleet ownership.

A tender is a warship body, so it is exactly as hittable as one: **only by ordnance**, through the armour rule, and never by infantry fire. It is a weak ship, not a combatant, pressed into the role haphazardly like its vanilla counterpart: the hull table's 1200 hit points and 200 armour, and the Shepherd's omni shield (arc, upkeep and efficiency from the hull table) on the hull's own flux through the existing shield model. The shield raises itself under the ordinary automatic policy when ordnance threatens the hull and nothing else is added: no buff, no regeneration, no extra flux. With no guns, the only flux on the pool is what the shield absorbs, so a determined raid collapses it and then the hull. Its drones are the targetable thing. Marines shoot the drones, and a builder shot at a site is a builder gone, which is why they stand down rather than work under fire. A tender takes no weapons of its own: the vanilla variant's missile rack and machine gun are deliberately left off, because a gun it could fire would make the drones decoration. Its point defence is its drones' (next section).

## Who steers

- **Slice 1:** the debug path. The briefing's WARSHIP DEBUG picker offers "Shepherd (tender)"; the debug panel's keys order a build at the cursor, cancel builds, recall or deploy the defence drones, and cycle the hull's stance (HOLD, ATTACK, DEFEND), and its status line reports stance, hull, shield, builds ("builds free", or the charges left on a metered tender), drones and the current order.
- **Player (built):** the existing direct-control carrier seam (`DirectControlSession`, with `TenderPilot` for the hands; `direct-control-nouns.md` Tender owns the bindings and handback). Click the hull, Tab to pilot, WASD flies it, the left button lays a site at the cursor for the selected option, the right button recalls or deploys the defence drones on the press, 1-4 select the option (all four slots are filled), X cancels every unfinished site, E is the shield. Fog is the same `revealed` predicate a pilot's laid fire uses. Nothing in the tender needs the pilot; it is the same orders by another hand.
- **AI by stance (built):** a tender with a mission stance and no pilot is steered by `TenderDirector`, a director over `Tender.lay` and never a second order model. The stance is the hull's own `MissionStance` and the objective ground is the warship's (`ObjectiveBias`): what its side's squads were sent to take or hold, plus ground a mission declares. HOLD, the default, is never directed, so a scripted or debug tender waits to be told.

### How the AI chooses

Every two seconds, with nothing on its line, the director looks at the battle and lays at most one site.

- **ATTACK builds forward positions on cleared ground.** It takes the objective its side has pushed closest to and the friendly leading on it, and walks the line from a few cells ahead of that lead back towards the rear, taking the first acceptable cell: the most forward ground no known hostile stands within the build's threat radius of. Idle, it hovers ten cells behind the lead, so it keeps up with the advance. It builds the **quickest** option, because the advance moves on and a position is worth having soon.
- **DEFEND builds on the approaches.** From the held point the nearest known hostile is closest to, it builds about eight cells out along the bearing to that hostile; with no hostile known there is no approach to read, and it builds on the held point. Once the enemy is seen coming, its next site goes out on their approach. Idle, it hovers on the far side of that point. It builds the **heaviest** option it can pay for, because held ground gives the builders time.
- **What bounds a free tender is the ground, not a count.** A cell is refused when a known hostile stands within the threat radius (the site would stand down at once), within six cells of another of its side's turrets or of a live site, or where two of its side's turrets already stand within fourteen cells. So a defended point gains a couple of turrets on its threatened side and then the tender idles, and an attacking tender builds again only as the advance moves past what it already put up.
- **It reads the same known hostiles as the stand-down,** so it never lays a site its builders would at once abandon, and never avoids an enemy nobody has seen. A site it lays into an unseen enemy's reach is found out the hard way, by a hit.

### Decisions made for the pilot

- **Right button is a press, not a hold-to-send.** The drone swarm has no order to go to a point, and a pilot-directed drone would be a second drone behaviour; the button recalls or deploys, nothing more.
- **A piloted tender does not station itself.** The pilot's hands are what bring the hull near a site: builders launch once it is within 16 cells, and are called home past 26. An AI tender still holds station five cells short of its site.
- **Handback withdraws the pilot's sites.** A half-built site is not left for an AI that never chose it; leaving costs the work in hand, which is the honest price of taking the hull away. The defence drones return to what they were told before entry.
- **Four sites at a time.** A held button paints sites; a short cap keeps the line something a person can read and cancel.

## Friendly fire and fog

No tender weapon exists, so there is no friendly fire to design. A friendly turret it builds is an ordinary friendly turret. A hostile tender's sites and drones are drawn only where the player has seen, as a hostile emplacement is. A player cannot lay a site on ground it has not revealed.

## Failure cases

- **A metered stock cannot pay, ground is shut, site crowded:** the order is refused and nothing is spent.
- **Site no longer open ground while queued or working:** the order fails (and returns its charge on a metered tender).
- **Every builder shot down:** the order fails ("no builders left").
- **Unseen shooter:** the first hit blocks the site and sends the builders home; each resumption into its sights costs another builder until it is found or the builders run out.
- **Overrun screen:** the defence drones die, the site stays contested, the builders stay safely in the bay and the job stalls. Cancelling withdraws the order.
- **Hull lost:** every order fails, the drones that were out are destroyed with it.

## Open questions for the owner

1. **Resupply and cost.** Building is free and drones are finite for the battle. If a cost is added later, should a build charge be a fabricated item the company carries, a number from the mission, or both?
2. **Point defence strength.** The wing is faithful to the laser and therefore weak against fast, fire-and-forget missiles. If a tender should reliably answer a light Locust volley, the lever is more drones or a stronger wing refit, not a boosted laser.
3. **Campaign hull condition.** A campaign Shepherd's damage and built-in mods are not yet read into the tender; their adaptation remains a separate decision from the story's fleet handover.

## What is built, and what is not

Built: point defence by the defence drones' mining lasers (the `tender-point-defence` scene: a light volley mostly shot down, a heavy swarm saturating it, a docked wing and a builder-only crew shooting nothing), and the hull as a composed warship body (fitted from a campaign member or the stock hull, with the vanilla shield, hit points and armour) with the vanilla drone complement and launch bay, defence drones anchored to it, builder drones, both drawn as the Borer, one free build option with a visible work phase and a build-site marker, stand-down under threat, cancel and failure, the debug picker and panel path, unit tests, and the `tender-build` scene (a plain build, a patrol the screen stops, the same patrol with the screen recalled, and an assault that overruns it).

Since added: launch and recovery as real flight (staggered, through the bay's mouth, docking on arrival, leading a moving bay), the escort ring, the leash and break-off, retreat and repair of hurt defenders, builder stations with a welding arc, and separation, with unit tests and the `tender-sortie` scene beside `tender-build`.

Player control through direct control is built too (the pilot's hands, strip, help panel and the `tender-pilot` scene). AI by stance is built (`TenderDirector`, unit tests and the `tender-stance` scene). Working Passage implements the story handover and full-size recovery hover; its live campaign/save and subsequent support acceptance remain in `shepherd-recovery.md`. Open tender work includes the remaining non-turret build option (revetment) and live choreography acceptance, on the board in `stories.md`.
