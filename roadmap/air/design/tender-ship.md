# Tender ship — a mobile drone and construction hull

Status: ACTIVE

Written: 2026-10-12

Updated: 2026-10-12 — slice 1 built (hull, drone complement, one build option, debug path, scene); control, AI stance, more options and resupply are open.

Read `air-nouns.md` (**Overhead ships**, **Tenders**) first. Vocabulary for the hull, the hover model and point defence is that doc's; `warship-behaviours.md` owns what a posture is. This doc says what a tender is, why it is built the way it is, and what is left.

## What the owner asked for

The vanilla Shepherd ("Drone Tender"): a hull with a built-in wing of automated drones that defends itself with them, and that also does construction on the battlefield, setting up turrets and other deployables. It is already a `ShuttleType` (`SHEPHERD`), a medium utility lift boat in the campaign roster. Standing direction applies unchanged: ships are overpowered by design and gated by context, not balance, and a posture or role changes what a ship chooses to do and never how hard anything hits.

## Decision 1: a tender is a separate unit kind, not a role on the boat

The boat Shepherd and the tender Shepherd share a sprite and a hull id and nothing else.

- The **boat** is an `AirBody` craft that lifts squads between a landing zone and the map edge, exists for one sortie, and lives in the air layer's shuttle machinery.
- The **tender** is an overhead hull. It hovers, it stays, it has a complement and a job, and nobody boards it. That is the `Warship` body's whole shape, so the tender is **composed over a `Warship`** rather than extending it or re-implementing flight: the body is fitted from the same `.ship`, hull table and sprite, registered with the same warship system, and flown by the same helm.

A tender is therefore one more thing that can be put over a battle by the same hands that put a capital there (a mission, a scene, the debug picker, later a campaign fleet member). It is not a campaign boat that happens to be asked to build, and it never competes with the boat for a lift slot. Whether a campaign Shepherd can be *brought as* a tender is a fleet-composition question that waits on general ship support (open question 1).

Rejected: a role flag on the shuttle mission. The shuttle machinery is built around pads, a mission state machine and a payload, none of which a tender has, and the tender would have inherited the shuttle's `AirBody` flight (no shadow, no hull table, no hover station) to lose the warship body's.

## Vocabulary

- **Tender** — the hull plus its complement, stock and line of orders. The only new noun that owns state.
- **Complement** — the built-in wing: the Shepherd's `borer_wing` names the number (six). Half are **builder drones**, half **defence drones**.
- **Defence drone** — an ordinary armed drone unit on the tender's side, anchored to the tender instead of to a hub.
- **Builder drone** — the same unit, unarmed. It exists to work a site and has no use for a target search.
- **Build option** — a deployable that already exists, its cost in charges and the time a full crew needs. Not a new kind of thing.
- **Charge** — the tender's finite stock. A laid order has drawn its charge; a cancelled or failed order returns it.
- **Construction order / build site** — one option at one cell. Phases: queued, transit, working, then complete, cancelled or failed.
- **Standoff** — the point a few cells short of the site where the tender hovers while its builders fly out, so the work is visible.
- **Stood down** — a site with hostile units inside the threat radius: no work is done and the builders go home.

## What a tender can build

Only deployables that already exist, through the paths they already have. Slice 1 builds a **vulcan turret**: a `TurretCatalogRegistry` structure placed through `MapTurret.create`, exactly the entity a defence post stamps, so it fires, takes fire, shows a durability bar and dies through the ordinary pipelines.

Candidates, in the order they cost least to add (each is a `BuildOption` plus, where noted, a placement path):

| Candidate | Path it would use | Note |
| --- | --- | --- |
| Other catalog turrets (arbalest, flak, mortar) | the same structure path | data only |
| Point-defence emplacement | `PointDefenseService.queuePlacement` | already bounded by lifetime and magazine |
| Revetment / cover screen | `DeployedCoverService.queuePlacement` | a boundary feature, not an entity, and needs a facing |
| Sensor, repair, supply | none yet | wait on the systems that would use them |

An option never restates what the structure is. Durability, weapon, magazine and art come from the catalog.

## How it builds

1. A site is laid on open, walkable ground that no other site is within two cells of. The charge is drawn; a refused site draws nothing.
2. The tender sets out for a standoff point five cells short of the site, on the side it is already on, and holds station there.
3. Within a couple of cells of the standoff and slow, it is on station. The builders launch from its bay and fly out to a ring around the site.
4. Progress accrues with the builders at the site: linear in how many, capped at the complement. The option's time is the time a **full crew** needs; one builder takes three times as long and none take forever.
5. When the work is done the structure is spawned on the cell, the builders fly home and dock, and the tender moves to the next order.

**It is interruptible.** Hostile units inside a threat radius of the site (30 cells, wider than any rifle's reach) stand the work down: no progress, and the builders go home into the bay where nothing can shoot them. The work resumes when the ground is clear. The radius is wide on purpose: a builder that waits to be shot at from outside a narrow radius is a loss for nothing, and the first version of this lost every builder to a patrol that never came inside it. The cost of that is real and is the context gate the owner wants: a tender builds on ground its side holds or its screen can clear, and cannot throw a turret up in the middle of an enemy compound.

There is no free build. A tender has three charges and no resupply in slice 1. A drone lost stays lost for the battle, because a wing that refills itself makes the complement a decoration.

## How its drones defend

Defence drones are the shipped drone: the same unit, weapon, hit points and swarm action a hub launches. The one change is where home is. A drone squad now has an optional **anchor** (`DroneAnchor`, implemented by the tender) beside its hub; the action's three reads of the hub position go through one helper that returns the hub's or the anchor's. Patrol sectors, the engage orbit, the pursuit latch and the leash all work around a moving ship unchanged, and the squad's goal is relevant only while the anchor lives.

They are not a garrison. A drone has eighteen hit points and a few rifle bursts end it. The recorded finding is that a screen of three stops a lone raider and loses to four; that is the shipped drone's strength and it is left alone, so a tender is no tougher for being a ship. If a tender is meant to hold ground against a fire team, the lever is more or better drones (a wing refit, a story), not a thicker drone.

On loss of the hull its drones go with it, as a hub's do. On a clean departure they are recovered.

## Vulnerability

A tender is a warship body, so it is exactly as hittable as one: **only by ordnance**, through the armour rule, and never by infantry fire. Its drones are the targetable thing. Marines shoot the drones, and a builder shot at a site is a builder gone, which is why they stand down rather than work under fire. A tender takes no weapons of its own: the vanilla variant's missile rack and machine gun are deliberately left off, because a gun it could fire would make the drones decoration. Point defence for a tender is a later, separate decision (open question 3).

## Who steers

- **Slice 1:** the debug path. The briefing's WARSHIP DEBUG picker offers "Shepherd (tender)"; the debug panel's keys order a build at the cursor, cancel builds, and recall or deploy the defence drones, and its status line reports hull, charges, drones and the current order.
- **Next:** the player through the existing direct-control carrier seam (`WarshipPilot`): Tab to pilot, left button lays a site, right button recalls or deploys the defence drones, 1-4 choose what to build, fog respected by the same `revealed` predicate a pilot's laid fire uses. Nothing in the tender needs the pilot; it is the same orders by another hand.
- **AI by stance:** a tender with a mission stance and no pilot: ATTACK builds forward positions on cleared ground and keeps close to the advance; DEFEND builds on the held ground's approaches. Both are a director over `Tender.lay`, never a second order model.

## Friendly fire and fog

No tender weapon exists, so there is no friendly fire to design. A friendly turret it builds is an ordinary friendly turret. A hostile tender's sites and drones are drawn only where the player has seen, as a hostile emplacement is. A player cannot lay a site on ground it has not revealed.

## Failure cases

- **Charge cannot pay, ground is shut, site crowded:** the order is refused and nothing is spent.
- **Site no longer open ground while queued or working:** the order fails and returns its charge.
- **Every builder shot down:** the order fails ("no builders left") and returns its charge.
- **Overrun screen:** the defence drones die, the site stays contested, the builders stay safely in the bay and the job stalls. Cancelling returns the charge.
- **Hull lost:** every order fails, the drones that were out are destroyed with it.

## Open questions for the owner

1. **Can a campaign Shepherd be brought as a tender,** or is the tender a mission-supplied ship only until general ship support exists? Today it is the second.
2. **Resupply.** Charges and drones are finite for the battle. Should a build charge be a fabricated item the company carries, a number from the mission, or both?
3. **A tender's own point defence and shield.** The hull has a shield; slice 1 leaves the shield to the warship defaults and fits no point defence. Whether a tender should defend its drones' rearming with flak is unanswered.
4. **Sprite.** Builders and defenders both draw as the shipped drone; the vanilla borer sprite would read better and needs a per-drone sprite path in the drone render pass.

## What is built, and what is not

Built: the hull as a composed warship body with the vanilla drone complement and launch bay, defence drones anchored to it, builder drones, one build option with a visible work phase and a build-site marker, stand-down under threat, cancel and failure with refunds, the debug picker and panel path, unit tests, and the `tender-build` scene (a plain build, a patrol the screen stops, the same patrol with the screen recalled, and an assault that overruns it).

Open: recall as a flight home (slice 1 docks defenders instantly), player control through direct control, AI by stance, more build options, resupply, drone sprite, campaign fleet-member fitting, and live acceptance. They are on the board in `stories.md`.
