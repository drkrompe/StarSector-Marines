# Direct control nouns

Status: ACTIVE — Marine, Mech, any player-owned deployed vehicle, friendly Warship, and drone Tender control are implemented; live acceptance remains open.

Written: 2026-09-23

Updated: 2026-10-23 — the right button lists throws from both special slots, so one Marine can carry smoke and a frag.

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
families are a Marine infantry member, a deployed combat Mech, and any deployed
vehicle the player owns, whatever its type. A vehicle is the player's when it is
Marine-faction: every hull the player's force puts on the field (a company bay
vehicle, a granted or fallback drop, one a works the company holds rolls out) is
spawned Marine, while scripted convoys, militia and allied armour the player
escorts or fights beside are spawned Ally, and stay out of reach even when
enlisted to take the player's move orders. A hull still being built on a works'
stocks is not yet a vehicle anybody drives. Allied or enemy bodies, incapacitated or riding
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

Selecting an exact eligible Marine infantry member, Mech, or vehicle exposes an enter/exit
control: a compact action beside that unit's details. No entry panel or disabled
selection prompt appears without an eligible unit, and hidden controls retain no
pointer or keyboard focus. Tab toggles takeover and Escape releases it. Tab is reserved before
retained UI focus traversal, and holding it never repeatedly switches modes. The action camera follows the controlled body, which
is how the player knows which body is theirs: a ground body draws no bracket.
A crosshair shows its point aim. The battle screen owns the mode indicator and
routes input before ordinary
world picking, contextual orders, and camera keys. During control, strategic
chrome is hidden: commander rails, squad and Mech panels, command
powers, retreat, communications, and developer panels. Their hidden bounds
cannot capture input or suppress movement and fire. Entry clears old UI focus,
pointer capture, and retreat confirmation. The takeover HUD follows the game's
own combat HUD and leaves the centre of the screen to the fight. A **status panel**
at bottom left names the body and its carrier and reads its durability as labelled
bars with a percentage: HULL (HEALTH for a Marine), ARMOR above FLUX, and a
Marine's STAMINA. A Mech or a vehicle shows its **damage doll** in the corner and
the status stands beside it: the machine seen from the front and from behind,
captioned `R FRONT L` and `L REAR R` as MechWarrior does, each location filled by
its state (green whole, yellow as armor wears,
orange once it is gone, red as structure goes, dark red destroyed) with two flags
beneath, CRIPPLED and SENSORS LOST for a Mech, IMMOBILE and GUN LOST for a vehicle.
A hull's side plates are the cheeks either side of each view, so a side reads in
both; a Mech's rear view drops the legs, which are thin and the same from behind.
The locations are owned by `convoy-nouns.md` and `mechs-nouns.md`; see `damage-doll.md`. A Marine
shows a **Marine doll** in the same place and size: one front figure, since a person
has one armor pool and no back plate. Its head and torso read that pool and health on
the machine doll's colours. Its arms and legs read only the limb wounds the
simulation keeps (`WoundService`): green when sound, orange at the first wound, red
once wounds stack to the floor. LEG WOUND (slowed) and ARM WOUND (aim) flags sit
beneath. A power suit draws as a broader shell. Beside the figure a kit column
lists what the Marine carries and what is left: special equipment in both slots (the
belt's throw beside the special, in a fixed order whichever slot holds it) with its rounds or
cooldown, the integral system's charges or cooldown, and the medic kit's supplies
when this Marine is the squad medic. The doll invents no hit location: a head or
torso hit only spends the shared pools. Filled lengths reflect the exact body's remaining fractions;
armor depletion empties its bar independently of health, and an armorless body
has no armor row. A **weapon-groups panel** at bottom right lists equipment under
the button that fires it (see Mouse buttons and weapon groups). At top right,
boxless keycap controls read `[TAB] COMMAND` and `[SPACE] PAUSE`, beside the HELP
tab; while the battle is paused the pause control turns amber and reads PAUSED.
Space pauses and resumes anywhere on the battle screen, command mode included, and
resumes at the speed the battle had before the pause. Both panels size
to their rows and hug the bottom edge, and like the game's own combat HUD they have no box: text and bars float over the battle, and only the selected weapon row carries a wash. Weapon rows distinguish the authored firing
pattern, remaining resources, and burst or recovery clocks. Loaded describes
resource and clock availability, not a guarantee that the barrel is aimed or
clear. A magazine-fed infantry primary draws its load as rounds rather than a
number: a strip under the weapon row (rifle cartridges, shotgun shells, or a
belt in blocks of one burst, so a long belt still reads at a glance), spent
rounds hollow and a reload pressing them back in. The cursor carries no ammo
mark. A primary without a magazine shows no counter. Machines draw their loads
the same way, by what they throw (`Ordnance`): a missile mount is a rack of
missiles in salvos of one trigger pull, a vehicle's belt is cartridges in
bursts, and an energy weapon counts nothing but shows charge cells, one per
bolt of its burst (five for a single shot), emptying through the burst,
refilling over the cycle and held violet when flux refuses the shot. A gun with nothing to count that
fires in bursts shows the burst one click sends as cartridges, spent as the burst
goes out and chambered again over the cycle. A mech's chaingun is a **rotary gun**
(`mechs-nouns.md`, Rotary guns): it fires while its button is held, its strip is
its belt (folded, with the count beside it, emptying as it fires and filling from
the hopper whether it fires or not), and its row's cycle bar is the barrels' spin,
filling as they come up to speed and reading SPIN with the percentage. A rack or
belt too long for the row folds to one icon per salvo or burst (several whole
bursts per icon for a long belt) and then keeps its exact count beside it; a
load too long even folded keeps only the number. While a rack's button is held, the missiles (or
folded salvos) that hold has fired draw lit beside the live ones, so a chain is seen building
salvo by salvo, and go back to spent when the button comes up. Finite Mech stores count
trigger packs, while a vehicle turret counts individual rounds. In the Marine doll's kit column,
a rocket or precision-rifle special with a short load draws it as missiles or
cartridges in place of its count.

A bounded, scrollable objective list stays at the top left, showing the same
public mission steps, instructions, measured progress and blockers as command
mode. It has no squad commands or camera-jump action; the action camera continues
following the controlled body. Pointer input over the list scrolls it and suspends
world fire, while movement and ability bindings retain their existing routing.
The world retains public objective locators, capture footprints, off-screen
objective directions, health bars, and aim crosshair.
A lone controlled ground body counts through the ordinary objective's physical
presence rules. Capture and recovery do not require the rest of its squad to be
nearby; contesting enemies, required rooms, dwell times, equipment and specialist
roles still belong to each objective. The readout grants no progress itself.

Selection and command/debug cell overlays are suppressed while
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
panel captures no pointer. The content is per carrier (Marine, Mech, vehicle, Warship, Tender) and
lists only that carrier's real bindings, so a vehicle with no working gun lists
drive and steer and no aim or fire; with nothing controlled there is no tab.

A faint aim lane connects the current firing origin toward the cursor. Its
clear segment ends at the first structural projectile blocker, marked in amber;
a fainter dashed segment continues beyond that obstruction. Marine origin is
the ordinary point-shot source. Mech and vehicle lanes use their actual posed
muzzles, and the Mech's trigger picks determine which flat-fire hardpoints appear;
an arcing rack flies over walls and draws no lane.

On a ground body the crosshair is an **aim reticle** that answers, per mouse
button, what a click would do now: one **mark** per button, read from that
button's current pick, as a short tick either side of a neutral centre dot on
the aim point (owner, 2026-10-23; side ticks chosen over split brackets and
concentric rings). The ticks lie across the **line of fire**, from the body (a
vehicle's turret ring) to the cursor, so they never run along the aim lane: the
left button's tick is on the body's own left of that line and the right
button's on its right, as a gunsight turns with its gun. Aiming down the screen
therefore puts the left button's tick on the screen's right; the player reads
left and right relative to the body (owner, 2026-10-23). A button with nothing on it draws no mark. **White** fires well,
inside optimal range; **yellow** fires with accuracy falling off, between
optimal and maximum range; **red** fires but is wasted, past maximum range;
**grey** will not fire: too close (inside a manual minimum), out of the mount's
alignment, barrel blocked, or out of ammunition. The reticle stays quiet,
because the player looks straight through it: thin ticks with no backing, no
shadow and no end caps (owner, 2026-10-23). Pattern carries what hue does, so
the marks read without colour: white and yellow are solid, red is dotted, and
grey is two wide dashes. The ticks are also partly transparent (owner,
2026-10-23): white at 80% opacity, yellow and red at 85% (a dotted or
falling-off tick carries less ink), and grey lowest at 60%. LINKED, or any
pick of several mounts, shows the best mark among them and is grey only when
none would fire. Cooldown, reload, burst and flux never change a mark: those
are clocks, and the panel's ammunition strips own them, so a Marine's empty
magazine is a reload rather than grey. A Marine's left mark is its pick: the
primary, or the special it is on, read at that special's range and accuracy and
grey once the special is empty; the right mark is the right-button pick: a throw,
smoke or a hand grenade (red past its throw reach, where it lands short; grey with
none left), or the shield. A vehicle's turret is its left mark.
**Optimal range** is derived, not authored: the distance at which a cursor round
keeps 80% of its point-blank chance of landing on one body (half a cell,
widened by the round's blast radius). The manual error disk is sized at weapon
range from the weapon's accuracy and spread there and scales with distance, so
the chance is the share of the disk the body covers; a weapon that stays on
target to maximum range has no yellow band. Warship and tender reticles are out
of this model.

There is no scatter ring (owner, 2026-10-23): a button's **landing spread** is
carried in its own tick. When the mounts its pick fires burst where they are
thrown (rockets and LRM racks), the tick **blooms**: it keeps its length and
thickness and opens away from the dot so that its inner end sits on the widest
landing disk among them, the way a shooter's crosshair opens, and it never
opens further than 40 pixels. A vehicle turret's launcher blooms the same way,
measured from its turret ring, and so does a Marine's left tick while it is on a
rocket launcher. A Marine's picked hand grenade blooms its right tick by the
grenade's burst radius, so the tick shows what the blast will cover (owner,
2026-10-23). Guns, which have no ground burst, keep the
resting tick, and so does a grey tick, since a refused trigger lands nothing.
The spread is the clear-air figure; smoke on a rocket's lane widens it. Bloom
was chosen over a tick that grows longer instead (owner, 2026-10-23).
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

Equipped Marine abilities have one-shot input: C raises an integral directional
shield, G throws carried smoke toward the cursor, and R reloads a magazine. Smoke,
a hand grenade and the shield are also the right button's items, so their rows sit
under the RMB group below the primary weapon (see Mouse buttons), smoke marked G and
the shield C; a grenade has no key of its own. Only present
equipment appears. Shield state reads its real soak pool, expiry, break flash,
and cooldown; smoke and grenades read what is left of those issued and the
committed throw clock (there is no grenade icon, so the count is a number).
Smoke has no additional cooldown. Held keys never repeat activation or queue a
later use when equipment is unavailable. Pause, chrome, lost focus, or handback
clear uncommitted requests. The equipment owners validate requests and spend
resources at their existing activation or release boundaries.

During manual control the suit's supported shield waits for C rather than its
autonomous activation policy. Its normal clocks and other suit systems keep
running. The shield faces authoritative mouse aim, including while strafing;
zero-length aim retains the screen's last simulation bearing. Its finite directional soak and
authored movement effect remain unchanged. Releasing control restores the
normal activation policy without resetting an active effect or cooldown.

A smoke press, or a right-button press with a hand grenade picked, freezes a
legal, range-clamped world point and begins the equipment's ordinary committed
throw. It blocks movement and primary fire for the whole channel, releases once
at its midpoint, and spends one canister or grenade only at that release. This
recognized manual channel retains control through its follow-through; an already
committed autonomous equipment action still blocks entry. A controlled carrier
is excluded from squad smoke reservations. Accepted throws retain their point
and finish under ordinary prep after handback. Pause freezes their progress;
death before release prevents release, while a canister already launched follows
the shared smoke-field lifetime. Smoke opacity affects both factions and does
not make a walk obstacle. A thrown grenade resolves exactly as an autonomous
throw does: the same handling scatter, flight, point-defence exposure, burst,
friendly fire and incendiary fire. Its point is booked as the squad's grenade
reservations book a throw, so autonomous throwers keep clear of it, but the
player's own throw is never refused for landing near a squadmate's. Other
secondary equipment needs its own manual contract before it gains an activation key.

Holding either Shift key requests sprint for every controllable Marine,
independently of armor or carried equipment. Sprint is an individual movement
choice; it does not add a squad order or a Mech or vehicle speed mode. A small Shift
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
world-edge clamping remains authoritative. The panels hold the bottom corners, so
framing reserves no clearance for them. If map clamping puts the body under a
panel, that panel fades and lets the pointer through rather than moving: chrome
that jumps is harder to read than chrome that steps back. Moving onto UI chrome or outside
the battlefield eases the lead back toward the body. Entry resets old lead.
Wheel zoom remains available, reaching twice the former close-up limit in
strategic and action views. Action zoom preserves the body’s screen framing;
strategic zoom retains its cursor anchor. Camera projection remains shared
with point aiming and the crosshair. Right-click issues no world order while
controlling. It is a second trigger for the carriers that have a second battery: a
Warship's missiles (see Warship carrier), a Tender's drone recall (see Tender), a Mech's secondary weapon group, and a
Marine's secondary equipment (see Mouse buttons). A carrier with nothing to put on
it, such as a vehicle turret, lets the press fall on the floor; the press never
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
Warden lies between them. It does not change top speed or depend on damage.

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

A controlled vehicle must be a live Marine chassis in DEPLOYED state, with its
complete oriented footprint legal at the current pose. Incoming, landed,
departing, gone, and wrecked delivery bodies cannot enter. W/S requests forward
or reverse throttle and A/D steers the wheels through the existing bicycle
kinematics. Braking and reversal remain physical; steering does not pivot the
hull in place. The live full-footprint sweep checks intermediate translation
and rotation against solid cells, reciprocal closed edges, and map limits.
Blocked motion retains the last legal pose. The manual owner supplies recovery
input without a retained AI maneuver moving the hull independently.

Entry suspends a vehicle's one-shot move order and discards its route and recovery
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
resetting cooldown. A magazine-fed primary spends one round per release from
the same load its AI fire uses, and an empty load reloads on its own; manual
fire grants no separate supply. R asks for a reload of a part-spent load (an
autonomous marine tops up after a short lull instead). A semi-automatic
magazine fires one round per press of the trigger rather than on a hold, and
under control there is no cycle between rounds: every press with a round loaded
fires at once, so the player's finger sets the rate of fire (owner,
2026-10-23). It reloads on its own only when the load runs dry.

A handheld point request is consumed once whether it fires or is held by a gate.
It requires a live equipped direct primary, a finite noncoincident world bearing,
positive weapon reach, a ready cooldown, a loaded magazine where it has one, and no active burst. The cursor is
not a range limit: an unobstructed round travels to the weapon’s full modeled
reach beyond it. Accuracy, falloff, spread, and smoke depth are calibrated at
weapon range so placing the cursor nearer cannot improve distant accuracy.

### Mouse buttons and weapon groups

Two held buttons, one model across carriers: the left button is the primary
trigger and the right button the secondary, and both may be held together.

A machine's weapon mounts (a Mech's point-fire hardpoints, a vehicle's turrets)
split into two **groups by weapon role**, one rule for every carrier
(`FireGroups`). Direct-fire guns, cannons and lasers are the primary group;
missile, rocket and indirect-fire mounts are the secondary group, arcing LRM racks
included, and so is **any secondary turret**, whatever it throws, so a hull's second
gun is never merged into its main gun's group. The Hound therefore fires its nose
chaingun on the left button and its SRM pod on the right; a Bulwark fires its
chainguns left and both SRM racks right; a tank with a coax MG fires its cannon left
and the MG right. A carrier whose primary mounts are all ordnance keeps them on the
left button (an all-rack chassis, or a launcher hull, is not stranded without a
primary) and leaves the right one to its secondary turrets, inert where it has none;
a hull whose main gun is shot away fires its second gun from the left.

Each button is a **trigger**, a delegation with its own **sub-cycle** (owner,
2026-10-23): an ordered list of **members** and a **pick** into it, and the press
fires the pick. A mech trigger's list is **LINKED** (the whole group at once, each
mount under its own arc, alignment, cooldown, ammunition, flux and burst gates),
then each mount of its group in hardpoint order. A Bulwark therefore fires both
arm chainguns on LINKED, or one of them alone. A group of one mount has only
LINKED, which is that mount, and an empty group has no list. LINKED is the
default pick. **Q** moves the left trigger to its next member and **E** the right,
wrapping back to LINKED, on every carrier (owner, 2026-10-23); the mouse wheel stays on
zoom. Abilities sit off the cycle keys: C is the shield, G smoke, R reload, F a
warship's low pass and X a tender's cancel. Number keys are direct
jumps on the left trigger only: key 1 is LINKED and keys 2 onward are its guns in
list order. **A member its trigger's pick leaves out holds fire**: no manual round
and no autonomous one, since the controlled body takes no AI trigger. A **crewed
defence** (a mount with its own gunner, such as a tank's active protection) never
joins a list and keeps firing autonomously under control; today's tank APS is a
point-defence structure already, and a defensive turret on a hull declares itself
crewed in data (`VehicleMount`) rather than being inferred.

A destroyed or empty hardpoint is never a member, so cycling skips it; a mount
whose ammunition is exhausted stays in its list and reads EMPTY. A pick whose
mount is lost returns its trigger to LINKED at the next validation. The picks
belong to the control session, survive pause, and reset to LINKED on takeover or
handback. Changing a pick cancels queued manual rounds and releases held fire
while preserving movement, aim, ammunition, and clocks; a new press must trigger
the new choice. A rotary gun fires only while its button is held and its pick names
it; let go, its barrels coast and spin down. A queued burst keeps firing after its button is released and is
dropped when its mount is no longer what either trigger's pick fires. Repeated
input cannot replay a cancelled burst. Neither picks nor groups change the
installed equipment, authored packet size, or AI firing policy. The authored
single shot, timed burst, or simultaneous volley remains distinct from this
player choice. A vehicle's lists follow the same rule over its mounts: the main
turret, then each secondary mount (`VehicleType.secondaryMounts`), so a one-gun hull
has a single-member left list and nothing on the right. A warship has no cycle, so
Q and E do nothing there. A Marine's left and right lists are described below.

The weapon-groups panel lists each mount under the button whose list it is in (a
hull with more than one gun is panelled as a Mech is; a one-gun hull keeps its
single LMB row):
an LMB group (guns), an RMB group (racks), and OTHER MOUNTS for a hardpoint
neither lists. Each trigger header names its pick and the key that cycles it,
for example `LMB  LINKED  [Q]` or `LMB  R arm Chaingun  [Q]`, and `[E]` on the RMB header (a picked mount is
named with its hardpoint when another member shares its name). The picked rows
carry the wash; the others, which hold fire, dim. Gun rows carry their number
key. Clicking a trigger header cycles it, clicking a gun row is its number key,
and clicking a rack row picks that rack. A Marine with a direct-fire special
shows an LMB group of the primary and each special, under the same header; a
special's row draws its rockets as missiles and its rifle rounds as cartridges,
as the doll's kit column does, and its clock is the readying and then the
cooldown.

A Marine's left button **toggles into a secondary weapon** (owner, 2026-10-23).
Its list is the primary and then each direct-fire special the Marine carries that
spends ammunition, rockets before anti-materiel rifles before the flamer, with no LINKED: one pair
of hands fires one weapon. Q steps through it and wraps to the primary; key 1 is
the primary and key 2 the special. A picked special is under a **manual
contract**. Picking it readies it over its authored aim time, and a press then
fires one round at the cursor through the same point-fire path a company uses to
open a wall with rockets: the launcher's round bursts at the point or on the
first thing in its lane, and the rifle's strikes the first body on it. The round
spends the special's own ammunition and starts its own cooldown. A press made
while the special is still readying or cooling stays armed for as long as the
button is held, and a held button fires once. The primary holds while a special
is picked, and sprinting lowers the special, which is readied again afterwards.
An empty special keeps the pick and reads EMPTY; it never falls back to the
primary on its own. The pick resets to the primary on takeover and handback, and
a special no longer carried returns it there. The controlled Marine **holds the
picked special** in place of the primary, the launcher on the shoulder or the
rifle in the hands: raised over the wind-up, through its firing clip for the
follow-through after a round, and in its aim pose on walking feet while moving.
Autonomous Marines use their specials through their AI policy as before.

The **flamer is a held stream** rather than a single round. Picked, it readies over
its aim time like the others; then for as long as the button is held it sends one
charge of its own fuel each time its cooldown allows, and letting go stops it. Each
charge flies toward the cursor, pulled in to the flamer's range and kept on the map,
and stops short of the first wall on its bearing by the structural rule direct fire
follows (smoke does not stop it); it lands and burns through the same release an
autonomous flamer uses, with its accuracy scatter. A jet that could land only in the
Marine's own cell, because the cursor is on the Marine or a wall is at the nozzle, is
refused without spending fuel. **The player's flamer can burn its own Marine**
(owner, 2026-10-23): fire it starts spreads through grass like any other, and the
manual path deliberately has none of the autonomous flamer's own-side reach guard,
so a Marine who keeps standing in a burning meadow pays for it. Its row counts fuel as a tank of cells, one a charge,
and its reticle tick is grey on an empty tank or a refused jet and red past its
range, where the jet lands short. It has no held art of its own: the Marine stands
in the primary's aim pose while it is picked and in the primary's firing clip while
a charge is leaving.

The right button holds the
Marine's **thrown and utility items** (owner, 2026-10-23): grenades are a throw, not
a gun, so they sit beside smoke and the shield. Its list is, in this order, carried
smoke, a carried hand grenade (frag or incendiary), then the integral shield; only
what the Marine has appears, there is no LINKED (one pair of hands), and the first
item is the default pick. E moves the pick and wraps; a row click picks that item.
An item with nothing left stays in the list and reads empty, as a dry rack does, and
an item the Marine no longer carries returns the pick to the first. The press uses
the pick at the cursor through the same one-shot request as the G and C keys, and a
pick that cannot be used now does nothing rather than falling to another item. It
acts on the press and does not repeat while held, so a held button cannot spend a
pouch of grenades; ammunition, cooldown and the committed throw are the equipment's
own. The pick belongs to the control session, survives pause, and resets on takeover
and handback. C and G keep working whatever the pick is, which is why the panel washes
the picked row without dimming the others. The list reads both special slots: a Marine
with smoke and a frag on the belt (`progression-nouns.md`) lists smoke, the grenade and
the shield. Using a belt throw swaps it into the hand for its channel, and a direct-fire
special it displaced returns once the hand is idle, so neither list moves while it runs.
**The flamer is not a throw**:
it shares the grenades' arcing activation in data, but it sprays a short flat jet
(its weapon is not indirect fire) and is aimed and held like a gun, so its manual
contract belongs to the left button with the direct-fire specials. Items with no
manual contract (grenade launchers, satchels) stay autonomous
and never join the list.

A held rack chains its salvos (`mechs-nouns.md`, Flux): a click is one salvo, and a
hold that has fired one keeps firing the next as soon as the last has gone, without the cycle, until
release or an empty rack. A vehicle turret's launcher does the same under manual fire.

An arcing LRM rack drops its salvo on the thrown point: the aim point, pulled in
to twice its range if further, plus the same growing scatter disk a rocket's ground
burst uses (`ballistics-nouns.md`), with no hit roll. It flies over walls as its
autonomous fire does. A point nearer the chassis centre than eight cells (or the
weapon's authored minimum, where larger) is **too close**: the rack's trigger is
refused without spending ammunition, flux or its cycle, while the other mounts
on the same button still fire. Its HUD row reads TOO CLOSE, the hint on the
header of the button that fires it reads TOO CLOSE in red, and the button's
tick turns grey. Every mount keeps one clock and resource stream under the ordinary
heavy-weapon pass; manual control neither advances it twice nor applies an
infantry training multiplier to Mech accuracy. The live cursor owns torso aim
and each scheduled round of a manual Mech burst. A round still requires the
torso to align legally with the point, judged from the chassis centre; each
barrel then fires from its own muzzle at the point, so paired and shoulder
barrels converge rather than each needing the point inside its own alignment
cone, which an offset muzzle loses at close range. A point nearer the centre
than one and a half hull widths is pushed out along its bearing to that
distance, so no barrel fires beside or behind itself. A refused release consumes its
scheduled slot without emitting a shot or refunding the accepted trigger pack.
A blocked or unaimed mount cannot retain a backlog or steer the torso. Entry,
exit, and input suspension cancel queued rounds and aim while preserving
ammunition, mechanical cycle clocks, shared chassis flux, and replenishment progress.

AI and manual Mech direct rounds originate at the installed barrel's actual
posed muzzle. The body-to-barrel structural segment must also be clear, so a
barrel protruding beyond a wall cannot launch a round through it. A manually
thrown rocket ground-bursts at the scattered aim point, which may lie past
nominal range at a growing scatter cost; gun area payloads detonate only at their
committed physical terminal contact (`ballistics-nouns.md` owns both rules).
Direct SRMs retain their ordinary interception opportunity and delayed impact.

A vehicle's independent turret slews toward the mouse bearing at its authored
traverse rate, whatever the gun, and a secondary mount is a turret of its own (its
own structure, ring, aim, magazine and cadence) that slews with it and fires only
while its button's pick names it: a flat-fire gun or beam, a rocket pod, or an
arcing rack (the LRM carrier's launcher, the fire-support APC's mortar) refused
inside its manual minimum. A hull with no turret weapon (the supply truck, the
scout car, the drone skimmer, whose drones keep their own tether) drives only,
and its buttons do nothing. The held primary trigger uses the existing magazine, cooldown,
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
rules allow it. Arcing mounts share one ground-point contract on every hull
(`ManualPointFire`): a pilot lobs a salvo at the cursor, scattered about the
thrown point and refused inside a minimum range. A vehicle turret obeys it as a
mech rack does, measured from its turret ring: the LRM carrier's launcher drops
each salvo on the thrown point, and inside eight cells its trigger is refused
and its row reads TOO CLOSE. A turret rocket that does not arc (the SRM pod) is
thrown at ground as a mech's rockets are, bursting at the point or on the first
thing in its lane. Any ranged turret can therefore be fired by hand, and none
reads AI ONLY. Mech mounts also retain their
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
(all, heavy only, none). C raises or drops the shield, leaving its automatic mode, on a
hull that has one. F sends the ship on a **low pass** (`ship-support.md`, slice 5): a fast, low run
from where the hull is to the cursor, on ground the player has observed, its guns working a corridor
of the line and nothing else, charged twice its flying time in station time. The pilot keeps the helm
throughout (the hull's travel is the pass's until it ends; laying ground is suspended and the previous
areas are given back after), key 1 silences the pass's guns, and a hull with too little station time
left, or already passing or leaving, refuses it. The tender does not have the key. The policy was chosen over the flux toggle for key 4 because turning
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

The status panel shows hull above armour (armour as its rating, since it is never
spent down) and the flux gauge with the ship's flux state beneath it; the
weapon-groups panel, titled SHIP SYSTEMS, holds read-only rows for the gun battery, lances, missile battery, point defence, shield, the low pass
(`[F] LOW PASS`: ready, running in, passing, or no time, with the station seconds left) and
the automation (director, missile preset, ground-fire policy), each with its laid area
or a no-area/auto note and a readiness fill. Armour is a rating in this model and is
never spent down, so its line is shown whole. The camera frames the hull by its beam so
the cursor can lead beyond the bow. The two laid rings and a crosshair are drawn
in the world; there is no aim lane, since a ship fires on ground and not along a
line, and no hull outline, since the camera is leashed to the piloted ship. Its
selection circle belongs to the strategic view and stays there. The F6-F11 debug keys of the debug mission still work outside
control and are hidden during it.

### Tender

A friendly drone tender (`tender-ship.md`) is the Warship carrier with different hands, not a
fifth input mode. It is selected by clicking its hull, entered with Tab through the same
`enterWarship` seam, and flown with WASD on the hull's own hover handling; the session
recognises the hull as a tender and routes the buttons to the tender instead of to batteries
it does not have. The shield (C) is the warship's.

Strategic ship selection can also issue remote movement and construction commands
without entering this session (`ui-nouns.md`, Remote ship commands). Takeover
suspends a previously accepted remote task, and handback resumes it with its
existing resources and sites. Uncommitted remote input from before or during a
pilot session cannot take effect afterward. Sites laid before takeover survive
handback; only new sites laid by the pilot are withdrawn under the manual contract.

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
  five: [1] vulcan, [2] arbalest, [3] dual flak, [4] heavy mortar, [5] interceptor pod); a slot with no option behind it is refused and shown as
  "soon". Choosing is not a handback and changes no running build.
- **X cancels every unfinished site**; each returns its charge on a metered tender.
- **A piloted tender does not station itself at a site**, because the hands are what move it.
  A laid site waits until the pilot has flown within 16 cells of it ("fly closer"), then the
  builders launch. If the pilot flies farther than 26 cells from a working site the builders
  go home and the work stops ("out of range"), as a threat stands it down. Everything else
  about the work is the tender's: the clock, the crew, the threat stand-down (a known hostile
  near reads STOOD DOWN; a builder hit reads UNDER FIRE until the site has been quiet for a few seconds).
- **Handback** is the warship's, plus the tender's own: the hull returns to its prior director,
  station or route; every site the pilot laid is withdrawn (a hand order does not outlive the
  hand; builders already out fly home); and the defence drones return to whatever they were
  told before entry. A tender whose hull carries an ATTACK or DEFEND stance builds for itself
  while unpiloted; the pilot suspends that choosing, and handback resumes it on its next pass.
  Death of the hull, departure, battle end, Tab and Escape release it.

The status panel shows hull above armour and the hull's flux; the SHIP SYSTEMS panel holds
read-only rows: helm; the selected option with the other slots marked "soon"; the site card (the
current site, its phase and progress, a refusal notice, the live-site count, and the cancel
key); builders (ready, out or working, lost); defence drones (ready, out, lost, deployed or
recalled); and the shield. Its help panel lists exactly these bindings.

## Armory range host

**Test Loadout** borrows the infantry control contract for a disposable practice
marine carrying the selected billet's proposed equipment, the person's aptitude,
and the experience band issued by the proposed armor.
The range opens already under manual control. Escape, Tab, or loss of the body
returns to the retained armory proposal and discards the practice simulation;
there is no strategic handback or mission settlement in this host. Movement,
aim, weapon cycles, special equipment, armor systems, and pause retain their
ordinary battle rules. The trial frames both ends of the range on entry, with
ordinary wheel zoom and cursor lead available afterward.

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


### Mech flux and mechanical cycling

Mech mounts use component-authored trigger cycles, separate from projectile
catalog cadence used by other carriers. Ballistics have cyclers, missiles have
loaders, and energy weapons recharge capacitors. Missile mini-fabs separately
restore ready packs. The shared battle-owned flux reservoir admits both AI and
player triggers; an accepted first round reserves flux for the entire packet.
Continuation and cancellation never charge again or refund that reservation.
All mounts cool through one reservoir once per simulation tick. Pause freezes
it and takeover/handback preserves it. Hot mounts remain fitted and supplied:
flux does not cause an ammunition fallback or change the mission assignment.

The status panel shows a 0-100% flux bar with its state beneath it: BURST
RESERVE, then HIGH FLUX - EASE FIRE in amber from 75, then VENTING - GUNS
LOCKED in red from 95 until 55. Each flux-blocked weapon row reads FLUX. Mechanical progress
and finite ammunition remain separate. See `mechs-nouns` for baseline tuning.
