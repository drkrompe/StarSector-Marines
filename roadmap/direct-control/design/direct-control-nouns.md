# Direct control nouns

Status: ACTIVE — Marine, Mech, deployed APC, friendly Warship, and drone Tender control are implemented; live acceptance remains open.

Written: 2026-09-23

Updated: 2026-10-12 — a drone Tender is flown through the warship seam with build and drone buttons; hover-only help tab explains each carrier's controls.

Direct control lets the player temporarily inhabit one of their ground units, or a
friendly capital hovering over the battle, in
the Marine Operations battle. It changes who supplies that body's movement and
fire intent; it does not turn the player into a second mission commander.

## Vocabulary and authority

- A **control session** is the battle-owned interval in which one exact, live
  Marine-faction body accepts player movement and aim. There is at most one.
- A **control request** names that body. Selection offers a candidate, but
  selection itself grants no authority. The simulation validates entry and
  owns the active identity until release.
- A **manual intent** is a snapshot of movement axes, cursor-derived world aim,
  trigger and sprint state, and optional weapon choice for one simulation tick. Input
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
control: a compact action beside that unit's details. No entry panel or disabled
selection prompt appears without an eligible unit, and hidden controls retain no
pointer or keyboard focus. Tab toggles takeover and Escape releases it. Tab is reserved before
retained UI focus traversal, and holding it never repeatedly switches modes. A bracket identifies the controlled body and a
crosshair shows its point aim. The battle screen owns the mode indicator and
routes input before ordinary
world picking, contextual orders, and camera keys. During control, strategic
chrome is hidden: commander/objective rails, squad and Mech panels, command
powers, retreat, communications, and developer panels. Their hidden bounds
cannot capture input or suppress movement and fire. Entry clears old UI focus,
pointer capture, and retreat confirmation. A thin bottom-center equipment strip
shows installed weapon cards and two color-only durability lines: armor above
health or chassis structure. Their filled lengths reflect the exact body's
remaining fractions. Armor depletion empties its line independently of health;
an armorless body has no armor fill. The strip omits identity, capacity labels,
and numeric durability values. All stays with the Mech weapon groups; separate
top-right buttons provide return-to-command and pause/resume. Weapon cards
distinguish the authored firing
pattern, remaining resources, and burst or recovery clocks. Loaded describes
resource and clock availability, not a guarantee that the barrel is aimed or
clear. Infantry primaries have no ammunition counter; finite Mech stores count
trigger packs, while the APC turret counts individual rounds. Indirect mounts
remain visible as unavailable for manual fire. The world retains health bars, the controlled bracket, and aim
crosshair. Selection and command/debug cell overlays are suppressed while
controlling. Exit restores the strategic view and current selection. In the world, WASD supplies movement and
the mouse supplies aim; a primary press starts held fire and its release ends
it, even if the pointer has since crossed UI chrome. World fire is suspended
while the pointer is over chrome or outside the battlefield. Entering and
leaving clears every held key and trigger. Escape or an explicit exit control
releases the session. Death, boarding, hard withdrawal, battle completion,
screen detach, and lost input focus release it as well.

A small `! HELP` tab sits just left of the return/pause buttons. It is hover-only: the pointer
over the tab opens a panel of this carrier's bindings and what its cards mean, and leaving
both the tab and the panel closes it. It takes no click and no focus, and nothing opens
by itself on entry. The tab is chrome like the buttons, so world fire is suspended
while the pointer is over it; the open panel is chrome only while open, and a collapsed
panel captures no pointer. The content is per carrier (Marine, Mech, APC, Warship, Tender) and
lists only that carrier's real bindings; with nothing controlled there is no tab.

A faint aim lane connects the current firing origin toward the cursor. Its
clear segment ends at the first structural projectile blocker, marked in amber;
a fainter dashed segment continues beyond that obstruction. Marine origin is
the ordinary point-shot source. Mech and APC lanes use their actual posed
muzzles, and Mech weapon selection determines which direct hardpoints appear.
A blocked body-to-barrel segment cannot masquerade as an open lane from a
muzzle beyond the wall. The guide uses current continuous terrain and shared
edge profiles, so opening or destroying structure changes the next preview.

The lane is a geometric aid. Spread, probabilistic cover, smoke, ammunition,
cooldown, and firing alignment retain their ordinary authorities. A movement
barrier that passes direct rounds does not become an amber firing stop. The
guide neither samples a shot nor identifies bodies along the lane. It stops at
the edge of current observation so hidden terrain cannot leak through an
obstruction marker or line-style transition. Pointer movement and camera motion
continue to update the guide while paused; pointing at chrome or outside the
world viewport hides it.

Equipped Marine abilities have one-shot input: E raises an integral directional
shield and G throws carried smoke toward the cursor. Their small, read-only
cards stay beside the primary weapon in the same equipment strip. Only present
equipment appears. Shield state reads its real soak pool, expiry, break flash,
and cooldown; smoke reads carried canisters and the committed throw clock.
Smoke has no additional cooldown. Held keys never repeat activation or queue a
later use when equipment is unavailable. Pause, chrome, lost focus, or handback
clear uncommitted requests. The equipment owners validate requests and spend
resources at their existing activation or release boundaries.

During manual control the suit's supported shield waits for E rather than its
autonomous activation policy. Its normal clocks and other suit systems keep
running. The shield faces authoritative mouse aim, including while strafing;
zero-length aim retains the screen's last simulation bearing. Its finite directional soak and
authored movement effect remain unchanged. Releasing control restores the
normal activation policy without resetting an active effect or cooldown.

A smoke press freezes a legal, range-clamped world point and begins the
equipment's ordinary committed throw. It blocks movement and primary fire for
the whole channel, releases once at its midpoint, and spends one canister only
at that release. This recognized manual channel retains control; an already
committed autonomous equipment action still blocks entry. A controlled carrier
is excluded from squad smoke reservations. Accepted throws retain their point
and finish under ordinary prep after handback. Pause freezes their progress;
death before release prevents release, while a grenade already launched follows
the shared smoke-field lifetime. Smoke opacity affects both factions and does
not make a walk obstacle. Other secondary equipment needs its own manual
contract before it gains an activation key.

Holding either Shift key requests sprint for every controllable Marine,
independently of armor or carried equipment. Sprint is an individual movement
choice; it does not add a squad order or a Mech/APC speed mode. A small Shift
card in the existing equipment row shows the exact Marine's stamina and running
or exhausted state. Its bar is distinct from armor and health.

Intent is sampled at the fixed 30 Hz simulation boundary. A paused battle
accepts no movement or shot; the aim preview may still move. Direct control
plays at 1x so a real-time hand has a stable movement and firing cadence.
Entering while paused leaves the battle paused. Entering from 2x or 4x
remembers that rate, and exiting restores it unless the player deliberately
changed time while controlling. An action camera follows body movement immediately and eases a bounded lead
toward the cursor beyond a central dead zone. Its framing sits between the body
and cursor without letting camera motion feed back into further camera drift.
The lead scales with the viewport and leaves the controlled body on screen;
world-edge clamping remains authoritative. Framing reserves clearance above the
equipment strip; if map clamping places the body beneath that strip, the strip docks
at the top below the separate session controls. Moving onto UI chrome or outside
the battlefield eases the lead back toward the body. Entry resets old lead.
Wheel zoom remains available, reaching twice the former close-up limit in
strategic and action views. Action zoom preserves the body’s screen framing;
strategic zoom retains its cursor anchor. Camera projection remains shared
with point aiming and the crosshair. Right-click issues no world order while
controlling. It is a second trigger for the carriers that have a second battery: a
Warship's missiles (see Warship carrier), a Tender's drone recall (see Tender), a Mech's secondary weapon group, and a
Marine's secondary equipment (see Mouse buttons). A carrier with nothing to put on
it, such as the APC turret, lets the press fall on the floor; the press never
reaches the world, the camera, or the command layer, and releasing input clears it
exactly as it clears the left button.

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
movement. A Mech eases its velocity toward a desired travel direction using
its chassis handling mass, damped hips, and actual-travel gait; its weapon aim may turn independently of
its legs where the equipped hardpoint allows. A vehicle uses its own ground
controller: W/S request forward/reverse motion and A/D steer, while the mouse
aims its independent turret. A vehicle must never acquire grid-infantry
components merely to share the input mode.

Marine sprint temporarily scales the current authored movement speed, including
an active suit's ordinary movement effect. It uses the same full terrain sweep,
normalized diagonal input, applied velocity, separation, and gait as walking.
Only extra movement actually gained through sprint spends stamina. Standing
still, fully blocked motion, input suspension, and a committed smoke throw
spend none. A partly funded final tick receives only that share of the boost.
While boosted movement is applied, the primary is lowered and pending primary
fire and burst work are cancelled without changing its cooldown or already
launched shots. Mouse facing and shield protection remain under their ordinary
manual authorities. Walking or stopping returns normal primary control.

Stamina belongs to the exact Marine within the battle, rather than to an entry
into control. It survives handback, switching bodies, and re-entry; those bodies
recover in simulation time while outside control. Pausing advances neither
spending nor recovery, and death retires the body's resource. Exhaustion
requires releasing Shift and recovering a minimum reserve before
sprinting again, avoiding a repeated speed pulse while Shift stays held. Input
cancellation clears sprint and a stale key repeat cannot restore it. Temporarily
blocking input over chrome does not count as releasing a held Shift key.
Initial tuning is a 1.6 speed multiplier, six seconds of full sprint, a one-second
recovery delay, four seconds for a full refill, and a quarter-bar reserve after
exhaustion. Those values are balance choices; stamina ownership and terrain
authority are standing laws. This resource is specific to manual sprint and
does not change autonomous movement or campaign condition.

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

A controlled Mech interprets WASD as desired travel velocity, bounded by its
current movement speed with normalized diagonal input. Acceleration, direction
changes, and braking ease the retained velocity in simulation time. Releasing
movement therefore decelerates to rest rather than stopping in one tick; a
reversal first arrests the old momentum. Relative handling mass belongs to the
chassis: the Bulwark responds more slowly, the Hound is more agile, and the
Sirocco lies between them. It does not change top speed or depend on damage.

Manual translation continues while the damped hips turn toward cursor aim,
without waiting for the autonomous eight-degree path-alignment gate. WASD stays
world-relative: forward travel gets full speed, lateral travel 72%, reverse 50%,
with continuous interpolation between them. Off-axis motion has a heavier
acceleration/braking response. Turning and speed depend on the actual hip
bearing; a mouse reversal cannot instantly rotate the body. With no valid aim,
travel supplies the fallback heading. Every momentum step still sweeps the actual chassis
envelope; terrain contact removes blocked velocity and retains only legal
sliding, so a wall cannot bank a later speed surge. Applied velocity and gait
describe achieved travel, separately from retained manual drive. Entry,
handback, loss of eligibility, pause, and input suspension discard stale manual
momentum. Ordinary key release uses natural braking. Autonomous paths retain
their alignment gate and movement tuning.

The torso turns toward the aim point within the
existing 145-degree limit to either side of the hips. Traversing between the
two rear limits goes through the forward arc, never across the blind wedge.
A mount requires the same four-degree firing alignment used by autonomous fire. A blocked drive step may
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
shot pipeline. A handheld primary burst retains the world point accepted at
trigger pull for all its follow-up rounds; moving the cursor affects the next trigger, while moving the
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

### Mouse buttons and weapon groups

Two held buttons, one model across carriers: the left button is the primary
trigger and the right button the secondary, and both may be held together.

A Mech's point-fire hardpoints split into two **groups by weapon role**. Guns,
cannons and lasers are the primary group; missile and rocket racks are the
secondary group. Indirect launchers (LRM) are in neither because the cursor cannot
aim them. The Hound therefore fires its nose chaingun on the left button and its
SRM pod on the right; a Bulwark fires its chainguns left and both SRM racks right.
A chassis whose point-fire mounts are all one role keeps them all on the left
button and leaves the right one inert (an all-rack chassis is not stranded without
a primary). Each button's trigger still requests only its own group's mounts,
subject to each mount's arc, alignment, cooldown, ammunition, heat and burst gates.

Keys 1-4 keep their place but act on the left button only. Key 1 is the default
(left = primary group); keys 2-4 re-point the left button at a single hardpoint
(arms, left shoulder, right shoulder), including a rack. The right button always
fires the secondary group. The strip marks every card with the button(s) that
fire it: `L`, `R`, `LR`, or nothing (an indirect rack or an unfired hardpoint).
Empty or indirect hardpoints cannot be selected; an installed direct mount remains
selectable when its ammunition is exhausted. Selection belongs to the control
session, survives pause, and resets on takeover or handback. Changing it cancels
queued manual rounds and releases held fire while preserving movement, aim,
ammunition, and clocks; a new press must trigger the new choice. A queued burst
keeps firing after its button is released and is dropped only when its mount leaves
both buttons' groups. Repeated input cannot replay a cancelled burst. Neither
selection nor groups change the installed equipment, authored packet size, or AI
firing policy. The authored single shot,
timed burst, or simultaneous volley remains distinct from this player choice.

A Marine's primary weapon stays on the left button. The right button is the
Marine's secondary equipment, through the same one-shot request as the G and E
keys: on the press it throws the carried smoke at the cursor and, for a Marine with
no smoke, raises the integral shield toward it. It acts on the press and does not
repeat while held, so a held button cannot spend a magazine of grenades; ammunition,
cooldown and the committed throw are the equipment's own. No other secondary item
has a manual contract (grenade launchers and satchels are autonomous), so a Marine
carrying only those leaves the right button inert rather than inventing a new free
action. E and G keep working.

Indirect mounts stay untriggered. Every mount keeps one clock and resource stream under the ordinary
heavy-weapon pass; manual control neither advances it twice nor applies an
infantry training multiplier to Mech accuracy. The live cursor owns torso aim
and each scheduled round of a manual Mech burst. A round still requires the
actual torso and muzzle to align legally; a refused release consumes its
scheduled slot without emitting a shot or refunding the accepted trigger pack.
A blocked or unaimed mount cannot retain a backlog or steer the torso. Entry,
exit, and input suspension cancel queued rounds and aim while preserving
ammunition, mechanical cycle clocks, shared chassis heat, and replenishment progress.

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

## Warship carrier

A friendly capital over the battle (see `air-nouns.md` and `warship-behaviours.md`) is a
fourth carrier of the same session, not a second input mode. It is not a roster body and
has no entity identity; the session holds the hull itself, and selection offers it the
same compact enter/exit action. A click on the hull selects it only where no unit was
under the cursor, since it hovers over the fight. Only a live hull on the player's side
that is not departing may be entered. Entry is refused while any other carrier is held,
and a hull destroyed, departing, or removed from the battle releases control at the next
validation. A mission-bounded ship that runs out of station time departs and releases.

Vocabulary. A **laid area** is a ground circle the pilot has put on one battery of the
ship (`FireOnArea`, ten cells, bring guns to bear). The **gun battery** is every
area-fire mount that is not a missile, lances included; the **missile battery** is the
missile mounts. Point defence belongs to neither: it keeps shooting at the air under
the ship's own rules. A laid area stands in for the ship's own held areas for its
battery only; a battery with nothing laid keeps whatever the ship holds. A laid area
saturates: with no hostile standing in it the battery works over open ground inside the
circle, so laying fire on empty ground is never silent. A pilot's
area is a hand order for its battery, so a posture's range and battery limits do not
refuse it, but it is never exempt from the physical pipeline: flux, ordnance,
traverse, arc, reach, shields, armour, point defence, spread, and friendly fire all
stay the ship's. The telegraph ring turns red when a round aimed at hostiles inside it
would reach a friendly.

Input. WASD supplies travel demand in world axes; the mouse is the aim point. The left
button, held, lays the gun area at the cursor and drags it with the cursor; the right
button does the same for the missile area. Letting go leaves the area where it was
laid, so the ship keeps firing until the pilot takes it back. Ground the player has not
observed, or off the map, is not laid: the fog rule is the aim lane's, so shelling cannot
scout. Number keys are commands rather than weapon choices: 1 holds fire (lays nothing,
drops the director's ground, stops the director), 2 hands targeting to the ship's own
director or takes it back, 3 steps the missile preset, 4 steps the ground-fire policy
(all, heavy only, none). E raises or drops the shield, leaving its automatic mode, on a
hull that has one. The policy was chosen over the flux toggle for key 4 because turning
flux off would be free ammunition; the policy only withholds fire. Tab and Escape are
unchanged, strategic chrome hides as for the other carriers, and pause, chrome and
lost focus zero the drive demand without dropping ground already laid.

Flight. The hull accelerates toward the demanded velocity at its own acceleration,
brakes at its own deceleration when nothing is demanded, never exceeds its table
speed, and is stopped by the map edge; it does not teleport. It points along its travel
while the pilot asks for travel. Stopped with ground laid, it swings at its own turn
rate to the heading that brings the most firepower onto that ground and holds it.

Authority and handback. Entry stands down the ship's own targeting and station-keeping:
its director, held ground, route or station, ground-fire policy and shield mode are
remembered, the director is off, and its doctrine stops assessing until release. Handback
restores exactly those, discards every laid area and any order the pilot gave, zeroes
the drive demand, and flies the hull back to its station if it had one. A hull that was
manually ordered keeps that order. The missile preset is a setting of the ship rather
than an order and stays as the pilot left it. Switching the director on for a ship that
was never on flux puts it on its hull's flux, as it would any directed ship.

The equipment strip shows hull above armour, the flux gauge in the heat row, and
read-only cards for the gun battery, lances, missile battery, point defence, shield and
the automation (director, missile preset, ground-fire policy), each with its laid area
or a no-area/auto note and a readiness fill. Armour is a rating in this model and is
never spent down, so its line is shown whole. The camera frames the hull by its beam so
the cursor can lead beyond the bow. The hull outline, the two laid rings and a
crosshair are drawn in the world; there is no aim lane, since a ship fires on ground
and not along a line. The F6-F11 debug keys of the debug mission still work outside
control and are hidden during it.

### Tender

A friendly drone tender (`tender-ship.md`) is the Warship carrier with different hands, not a
fifth input mode. It is selected by clicking its hull, entered with Tab through the same
`enterWarship` seam, and flown with WASD on the hull's own hover handling; the session
recognises the hull as a tender and routes the buttons to the tender instead of to batteries
it does not have. The shield (E) is the warship's.

- **Left button, held: lay a build site** at the cursor cell for the selected option. It is the
  same `Tender.lay` the debug panel calls: the cell must be ground the player has observed
  (the fog rule a warship's laid area uses), open and walkable, and clear of other sites. One
  site per cell per press (holding on a cell does not re-lay), at most four live sites, so a
  held button can paint a short line but not flood it. A metered tender that cannot pay refuses.
  A reticle at the cursor cell is blue where a site can go and red where it cannot, and the
  site card says why when a click is refused.
- **Right button, press: recall or deploy the defence drones.** The press toggles; holding does
  not repeat. The drone swarm has no order to "go to the cursor", and one is not invented here:
  the defence drones keep the screening behaviour they already have.
- **Number keys select what to build.** They are the tender's real option list in order (today
  one: [1] the vulcan turret); a slot with no option behind it is refused and shown as
  "soon". Choosing is not a handback and changes no running build.
- **X cancels every unfinished site**; each returns its charge on a metered tender.
- **A piloted tender does not station itself at a site**, because the hands are what move it.
  A laid site waits until the pilot has flown within 16 cells of it ("fly closer"), then the
  builders launch. If the pilot flies farther than 26 cells from a working site the builders
  go home and the work stops ("out of range"), as a threat stands it down. Everything else
  about the work is the tender's: the clock, the crew, the threat stand-down.
- **Handback** is the warship's, plus the tender's own: the hull returns to its prior director,
  station or route; every site the pilot laid is withdrawn (a hand order does not outlive the
  hand; builders already out fly home); and the defence drones return to whatever they were
  told before entry. Death of the hull, departure, battle end, Tab and Escape release it.

The equipment strip shows hull above armour, the hull's flux in the heat row, and read-only
cards: helm; the selected option with the other slots marked "soon"; the site card (the
current site, its phase and progress, a refusal notice, the live-site count, and the cancel
key); builders (ready, out or working, lost); defence drones (ready, out, lost, deployed or
recalled); and the shield. Its help panel lists exactly these bindings.

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


### Mech heat and mechanical cycling

Mech mounts use component-authored trigger cycles, separate from projectile
catalog cadence used by other carriers. Ballistics have cyclers, missiles have
loaders, and energy weapons recharge capacitors. Missile mini-fabs separately
restore ready packs. The shared battle-owned heat reservoir admits both AI and
player triggers; an accepted first round reserves heat for the entire packet.
Continuation and cancellation never charge again or refund that reservation.
All mounts cool through one reservoir once per simulation tick. Pause freezes
it and takeover/handback preserves it. Hot mounts remain fitted and supplied:
heat does not cause an ammunition fallback or change the mission assignment.

The action strip shows a 0-100 heat bar, amber warning at 75, red venting at 95
until 55, and a HEAT hold on each thermally blocked weapon. Mechanical progress
and finite ammunition remain separate. See `mechs-nouns` for baseline tuning.
