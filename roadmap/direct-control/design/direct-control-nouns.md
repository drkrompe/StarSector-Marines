# Direct control nouns

Status: ACTIVE — Marine, Mech, and deployed APC control are implemented; live acceptance remains open.

Written: 2026-09-23

Updated: 2026-09-26 — deployed APC control shares vehicle motion, turret fire, and suspended-order handback.

Direct control lets the player temporarily inhabit one of their ground units in
the Marine Operations battle. It changes who supplies that body's movement and
fire intent; it does not turn the player into a second mission commander.

## Vocabulary and authority

- A **control session** is the battle-owned interval in which one exact, live
  Marine-faction body accepts player movement and aim. There is at most one.
- A **control request** names that body. Selection offers a candidate, but
  selection itself grants no authority. The simulation validates entry and
  owns the active identity until release.
- A **manual intent** is a snapshot of movement axes, cursor-derived world aim,
  trigger state, and optional weapon choice for one simulation tick. Input
  events are presentation data until the battle accepts that snapshot.
- A **point shot** is direct fire along a weapon's sampled direction around a
  world aim point. It has no locked target identity. Physical contacts, not a
  cursor hit test, decide what the round strikes.
- **Handback** releases the session and returns the body to its current AI
  assignment. It is not a new squad order and does not restore a stale plan.

Only an exact player-owned, controllable ground body may enter. The implemented
families are a Marine infantry member, a deployed combat Mech, and a deployed
Marine HEAVY_APC. Allied or enemy bodies, incapacitated or riding
members, wrecks, and vehicles in a delivery leg are not eligible. Marine entry
also waits for committed special-equipment actions and is unavailable to a
mission planter or kit retriever. Those interactions need an explicit manual
action contract; primary control cannot silently continue an objective-owned
channel. A broken fire team, broken squad, forced fallback, structural squad
retreat, or hard withdrawal releases control to its existing survival authority. A deployed
Mech requires its combat loadout and locomotion components in a friendly
non-rescue lance. An unfinished frame and a rescue pickup Mech cannot enter.
Mech morale follows the existing squad survival decision; an individual
chassis contributes to that decision without adding a stricter manual-only gate.
The exact eligibility of a future hero designation belongs to company/progression; the
control mechanism should not mint a second soldier identity or change the
campaign's casualty accounting.

During a session, player intent wins only that body's ordinary locomotion,
facing/aim, and selected weapon trigger. Its squad or lance still has a mission
assignment; other members keep acting under it. Manual takeover must not leave
an essential fire-team role waiting on a member the AI no longer executes.
Entry, exit, and loss of eligibility prompt a fresh tactical allocation. Health,
morale, unavoidable hazards, withdrawal, capture presence, equipment use,
cooldowns, and campaign identity remain under their existing authorities.
There is no position teleport, invulnerability, free ammunition, or hidden
target knowledge. A controlled Mech retains its real lance membership, leader
identity, and physical strength. It leaves autonomous role candidates, doctrine
selection, formation anchors, and shared arrival work until handback. The
remaining members choose an autonomous lead. Entry cancels both queued and
active one-shot moves for the exact chassis; new one-shot moves cannot compete
with manual ownership. Mission, doctrine, and lance orders remain current and
may change while the player controls the body. Handback replans from that
current context without restoring old movement or firing work.

## Input and time

Selecting an exact eligible Marine infantry member, Mech, or APC exposes an enter/exit
control; C toggles takeover and Escape releases it. A bracket identifies the controlled body and a
crosshair shows its point aim. The battle screen owns the mode indicator and
routes input before ordinary
world picking, contextual orders, and camera keys. Retained UI chrome keeps
its own pointer and keyboard focus. In the world, WASD supplies movement and
the mouse supplies aim; a primary press starts held fire and its release ends
it, even if the pointer has since crossed UI chrome. World fire is suspended
while the pointer is over chrome or outside the battlefield. Entering and
leaving clears every held key and trigger. Escape or an explicit exit control
releases the session. Death, boarding, hard withdrawal, battle completion,
screen detach, and lost input focus release it as well.

Intent is sampled at the fixed 30 Hz simulation boundary. A paused battle
accepts no movement or shot; the aim preview may still move. Direct control
plays at 1x so a real-time hand has a stable movement and firing cadence.
Entering while paused leaves the battle paused. Entering from 2x or 4x
remembers that rate, and exiting restores it unless the player deliberately
changed time while controlling. A follow camera uses the same screen-to-world
projection as picking and shooting, with wheel zoom available during control.
Temporary camera look is deferred; the camera keeps following the
controlled body and reserves right-click without issuing a world order.

The mode is a freely entered one-unit intervention. It consumes
the player's attention and excludes simultaneous control of another body.
If play evidence calls for a duration, charge, or cooldown, that pacing must
wrap the same control session. A command-power card would additionally need
the fleet-source, pre-battle deck, and activation-payment laws of
`command-powers-nouns.md`; the control session itself must not bypass them.

## Movement by carrier

One manual intent has carrier-specific execution. Infantry translates on the
ground plane at its current movement speed, with normalized diagonal input and
the same walkability, body interaction, velocity, and pose rules as ordinary
movement. A Mech takes a desired travel direction but retains its chassis
pivot, gait, and facing constraints; its weapon aim may turn independently of
its legs where the equipped hardpoint allows. A vehicle uses its own ground
controller: W/S request forward/reverse motion and A/D steer, while the mouse
aims its independent turret. A vehicle must never acquire grid-infantry
components merely to share the input mode.

The session stores intent, not a second movement physics model. Existing
collision and reachability remain authoritative. Losing focus or releasing
movement yields zero drive input on the next tick, rather than replaying the
last key state. A manual detour does not rewrite the squad's mission directive
or the vehicle's delivery errand. On handback, the current AI decides a new
route from the body's actual position.

Manual movement is checked along the **whole proposed step**, not only at its
destination. Each tick turns input into a speed-limited displacement, then
tests every crossed cell boundary against the battle's current walkability,
reciprocal edge passability, and diagonal corner rules. A closed door or thin
barrier still blocks when the cells on both sides are walkable. The moving
body's collision envelope must also clear solid terrain and closed edge
segments, including at sub-cell positions; map edges are solid for deployed
ground bodies. A feature authored as walkable remains traversable even when
it catches shots or provides cover. At first contact, motion stops at the
last legal position and may use the remaining
displacement to slide along the obstacle if that slide is legal. Velocity,
and gait reflect the displacement actually applied, while a Mech or vehicle
may still pivot in place under its own facing rules. Pressing into a wall
never animates forward progress or accumulates motion to be released later.
A changing wall or door is read from current topology on the next tick.
Physical body separation remains downstream and cannot push the unit across
an impassable edge. Entry requires a legal body envelope at the current
position. A body already outside that envelope is refused for takeover rather than
teleported or allowed to begin a motionless session. Mech AI routes, placement,
translation, and separation use the same physical chassis clearance. This
shared terrain authority applies equally during direct control and AI handback.

Infantry and Mechs share the terrain/topology law but keep their own movement
speed, body clearance, and Mech pivot behavior. The existing path follower
assumes a planned route and cannot serve as the manual collision check by
itself. Mech routes begin at the actual continuous position and retain a legal endpoint
for their requested destination. On handback, an asynchronous proof may remain
pending; that wait preserves the intended destination and grants no old
point-route fallback. AI and manual translation must clear the same chassis
radius, including centerlines between ordinary cell centers. An existing route
never permits either owner to cross a newly closed wall or edge. Vehicles test their complete
oriented footprint throughout translation and rotation, including intervening
poses and crossed closed edges. A legal end pose alone does not prove that a
chassis did not pass through an obstacle on the way there. A blocked drive
step retains the last legal pose and uses the vehicle's ordinary stopped or
recovery behavior; it never teleports or snaps through the obstruction.
Other live bodies keep their shared separation rules. In particular, moving
vehicle versus infantry occupancy still needs the cross-domain policy in
`truck-infantry-interaction.md`; direct control must not invent a player-only
collision outcome for it.

A controlled Mech interprets WASD as desired travel direction. Its hips accelerate
and brake toward that direction and may translate only within the existing
eight-degree movement alignment gate. Neutral input brakes hip turning; mouse
aim does not rotate the legs. The torso turns toward the aim point within the
existing 145-degree limit to either side of the hips. A mount requires the same
four-degree firing alignment used by autonomous fire. A blocked drive step may
still pivot, but its gait follows only the motion actually applied.

A controlled APC must be a live Marine chassis in DEPLOYED state, with its
complete oriented footprint legal at the current pose. Incoming, landed,
departing, gone, and wrecked delivery bodies cannot enter. W/S requests forward
or reverse throttle and A/D steers the wheels through the existing bicycle
kinematics. Braking and reversal remain physical; steering does not pivot the
hull in place. The live full-footprint sweep checks intermediate translation
and rotation against solid cells, reciprocal closed edges, and map limits.
Blocked motion retains the last legal pose. The manual owner supplies recovery
input without a retained AI maneuver moving the hull independently.

Entry suspends an APC's one-shot move order and discards its route and recovery
state while retaining the requested destination. An order received during
control replaces that retained destination without driving or proving a route.
It cannot unload passengers merely by pointing at the controlled hull. Exit
stops manual drive and requests a fresh route from the actual pose to the latest
destination, including requests still queued when control ends. No destination,
or one already reached, leaves the deployed vehicle holding. Loss of live
deployed eligibility drops retained and queued intent. Passengers, delivery
payload, and mission state are unchanged by takeover and handback.

## Fire and knowledge

Ground direct fire already resolves a physical path. `BallisticResolver`
commits a shot's trajectory and ordered wall, cover, and body contacts at fire
time; `ShotService` delivers its payload at the committed arrival time, and
`ShotEvent` presents that same flight. Manual fire extends this contract with
a world-point aim, not with a parallel damage or hit-point rule.

The weapon's authored reach, accuracy, falloff, spread, cadence, burst,
ammunition, and muzzle origin still apply. A point shot samples direction
from aim error before the physical contact walk. It cannot borrow an entity
target's perfect lead or
silhouette-based hit roll, and treating every body as an incidental contact
must not erase the weapon's accuracy. A moving shooter retains the existing
moving-fire penalty. Structural stops, smoke, cover catches, friendly-fire
contacts, damage delay, telemetry, sound, and effects follow the ordinary
shot pipeline. A primary burst retains the world point accepted at trigger pull for all its
follow-up rounds; moving the cursor affects the next trigger, while moving the
shooter changes its bearing to that frozen point. Follow-up spread uses the
shooter’s actual applied movement. A held trigger cannot restart an unfinished
burst. Releasing control cancels pending intent and burst work without
resetting cooldown. The current handheld primary family has no ammunition
counter; manual fire shares that resource contract rather than granting a
separate ammunition supply.

A handheld point request is consumed once whether it fires or is held by a gate.
It requires a live equipped direct primary, a finite noncoincident world bearing,
positive weapon reach, a ready cooldown, and no active burst. The cursor is
not a range limit: an unobstructed round travels to the weapon’s full modeled
reach beyond it. Accuracy, falloff, spread, and smoke depth are calibrated at
weapon range so placing the cursor nearer cannot improve distant accuracy.

For a Mech, the held primary trigger requests every installed ground-direct
mount whose own arc, alignment, cooldown, ammunition, and burst gates permit
it. There is no separate weapon selector in this adapter. Indirect mounts stay
untriggered. Every mount keeps one clock and resource stream under the ordinary
heavy-weapon pass; manual control neither advances it twice nor applies an
infantry training multiplier to Mech accuracy. Each accepted burst freezes its
own world point. A committed burst has priority over the current cursor for
torso aim, with installed-mount order breaking ties. Later triggers use the
current cursor after that commitment ends. Entry, exit, and input suspension
cancel queued rounds and aim while preserving ammunition, cooldowns, and
replenishment progress.

AI and manual Mech direct rounds originate at the installed barrel's actual
posed muzzle. The body-to-barrel structural segment must also be clear, so a
barrel protruding beyond a wall cannot launch a round through it. Direct area
payloads detonate only at their committed physical terminal contact; the cursor
and an unobstructed overshoot produce no blast. Direct SRMs retain their ordinary
interception opportunity and delayed impact.

The APC's independent turret slews toward the mouse bearing at its authored
traverse rate. The held primary trigger uses the existing magazine, cooldown,
burst spacing, minimum range, and firing alignment. Each burst retains its accepted world
point until completion. Manual and autonomous fire share one turret tick; AI
acquisition and triggers are suppressed only for the controlled hull. Entry,
exit, and input suspension clear target and queued burst work while preserving
ammunition and cooldown. Point rounds use the actual hardpoint and barrel pose,
with a body-to-barrel structural guard, and resolve through ordinary ground
ballistics without turning the cursor into a target identity.

The player may point at ground they can see without creating a squad belief
or revealing an unseen enemy. A physically struck unknown body is resolved
normally, but its identity is presented only when the player's observation
rules allow it. Weapon families outside modeled ground direct fire, including
indirect missiles, need their own ground-point targeting contract before they can be
triggered manually. Mech mounts also retain their
hardpoint arcs and per-mount clocks; a vehicle turret retains its independent
aim and fire authority.

## Boundaries

`ai-nouns.md` owns squad roles, mission assignment, morale, and AI handback.
`ecs-nouns.md` owns the distinct roster and vehicle carrier shapes.
`ballistics-nouns.md` owns direct-round contact and arrival, while
`moddable-weapons-nouns.md` owns authored weapon characteristics.
`ui-nouns.md` owns battle input routing and camera projection.
`convoy-nouns.md` owns vehicle motion and delivery states; `mechs-nouns.md`
owns Mech gait, facing, and mount constraints. This design targets the
standalone Marine Operations battle first. The vanilla-combat bridge has a
separate ground inspection/order mode in `ground-control-mode.md`; supporting
direct control there requires its own host input adapter and cannot leak WASD
or fire into vanilla ship controls.

Open implementation slices and acceptance are indexed in `stories.md`.
