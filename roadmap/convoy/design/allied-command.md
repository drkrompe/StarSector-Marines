# Direction: the allies command their own machines

Status: ACTIVE — enlistment, the allied command's two tasks and the screened
advance have landed; the Cordon's logistics base and The Burn's advancing armour
build on them.
Read `convoy-nouns.md` first; this extends `vehicle-as-commandable-unit.md` to
the allied side.

Written: 2026-10-22
Updated: 2026-10-24 — a straggler (stuck, or a late joiner still walking up) is out of the formation's body

## Why

An allied force that only stands in its posts is scenery the swarm wears away.
The owner wants the allies to fight their own war, the way a lane fights in an
RTS or MOBA: allied squads delivered by APC, light armour and cars that hold
ground, with the player's company as the backstop when a lane goes bad and the
push that carries one through. That needs allied machines that move and fight
without the player, under a mission's direction.

## The model

**Enlisted hull.** An allied (`ALLY`) hull is either its script's or enlisted.
A scripted hull (a story column on an authored route, a convoy) moves only when
its script says, and the vehicle combat AI leaves it alone. An enlisted hull
belongs to an allied command: it takes orders through the same order mailbox
the player's hulls do (`VehicleMoveOrderService.enlist`), so it plans a
drivable route the same way, from the same queue, behind the player's own
orders (`convoy-nouns.md`, orders are planned from a queue), and between orders it fights as an autonomous
hull does (`VehicleEngagement`). The player never orders one: selection picks
only the player's own hulls. Hostility in the combat AI is relative to the
hull's own side, so an enlisted hull needs nothing else to fight the right
enemy.

**Allied command** (`AlliedCommand`). Mission-neutral, and owned by the
mission that uses it: it is advanced from the mission's own serial tick.
The mission decides where the allies want their machines (which redoubt is
calling, where the barrage line has got to). The command carries that out.
It gives two tasks:

- **Station:** drive to a place and fight from it. A stationed hull that strays
  far from its post while fighting is driven back when it next stops. An
  *advance* is a station that the mission keeps moving forward.
- **Carry:** a squad walks to the hull and boards it (all of the squad or
  none). The hull drives it to a place and sets it down there, and the squad
  is handed back to the mission, which gives it its next order. The hull then
  stations where it set the squad down, as fire support. If the squad never
  arrives, the hull gives up waiting and stations. The riders travel as a
  count, off the field, and the mission seats them again at the drop.

**A refused place is reached a leg at a time.** A hull that steers wide (an
APC turns on about twice a car's circle) is refused a long route with one
corner it cannot make in a single drive, though every leg of it is drivable on
its own. A refused goal is therefore pulled back to the corner halfway along the
way there and asked again, halving at each refusal until a leg is drivable;
standing at that corner the hull turns on its own and is sent on to its post.
Where no way is known at all the goal is pulled back a step along the straight
line instead. Both are bounded, so a hull with nowhere to go stops rather than
asking every tick; a stationed hull that stopped short of its post is sent to it
afresh after a pause (`AlliedCommand.STALL_RETRY_TICKS`), because the ground
changes under it (a machine that stood in its way moves off, a wall comes down).

**What a hull was refused it is not asked again from the same place.** A
stalled hull that has not moved walks the same pull-backs to the same
refusals, and on The Burn a handful of hulls stuck where they stood made most
of the battle's vehicle refusals that way. So the commander keeps each hull's
refused goals (`RefusedGoals`) and pulls a goal it already knows is refused
straight past, without an order; out of pull-backs, the hull is not sent at all
and waits for its retry. The memory is a statement about one place: the hull
moving a cell and a half forgets it, and a minute forgets the goals (fire
burns the undergrowth off, a wreck is towed away) so they are asked once more.
Each retry that finds the hull where it stalled doubles the pause, up to eight
times, and a move resets it.

A mission sets its hulls down where they can drive out: facing the way they
will go (the chassis heading, 0 at +Y), on ground whose footprint clears
whichever way the first leg leaves (`TurnAwareCorridor.canSetOutFrom`). A hull
set down side-on in a narrow road, or with its tail against trees, has every
route refused at its first pose.

An allied **mech** is stationed the same way: its squad attack-moves to ground
the mech can stand on at the post, the post pulled back toward the mech a step
at a time where it cannot (woods, a wall). A mech is a squad member, so this is
a squad order, not a hull's; its doctrine fights it once there.

Squads the allied command orders are under `GARRISON` authority, as every
allied garrison's are.

## Screened advance

Owner, 2026-10-23: "consider how to make armor screened advances or marine
screened advances toward a rally point make sense. We can somewhat have this be
something we can script per mission for authored ones like these to make the
commanders seem smarter than they are." An advance that is only a station the
mission keeps moving sends every machine and squad at its own pace, so tanks
leave their infantry behind and an advance looks like a crowd rather than a
plan.

A **screened advance** (`ScreenedAdvance`) is two elements moving to a **rally
point** as **one formation**:

- The **screen** goes a few cells in front, or abreast on the main's two
  flanks. Infantry screen armour through woods or streets, where a tank alone is
  blind and the swarm closes unseen. Armour screens infantry across open ground,
  where the guns take the fire.
- The **main** keeps its slot right behind the screen, close enough to give it
  fire at any moment.
- Either element may be squads, hulls, mechs or a mix. Squads take an
  attack-move under `GARRISON` authority, and hulls and mechs take this
  command's station orders. So each element fights on its own doctrine wherever
  it stands, and the advance only moves it.

**One formation, not bounds** (owner, 2026-10-23): "I don't necessarily like
'bounded' advance. I'd rather they moved in a formation instead. Otherwise the
marine screen results in the marines being out in the open while the vehicles
are far behind." An earlier form bounded from phase line to phase line, each
element waiting for the other at every line. That left the screen standing in
the open with the main a bound behind. It is gone, and nothing replaces phase
lines by default.

**The anchor and the slots.** The formation has one **anchor**, a distance along
the route (`AdvanceRoute`, a line of cells from the start line to the rally
point). Every squad and machine has a **slot** fixed relative to the anchor:

- With the screen ahead, the main's line abreast is on the anchor and the
  screen's is six cells in front of it.
- With the screen on the flanks, there is one line, with the screen's members on
  its ends.
- Neighbours stand six cells apart. A large formation closes up to fit a
  **frontage**, such as a lane's width.

**The slowest sets the pace.** The anchor moves on only as far as the slowest
element's **body** keeps up with its slot, at a walking marine's pace at most.
An element's body is the leading two thirds of its squads and machines along the
route. So the slowest element walks on without stopping, and the fastest waits
in its slot for it. A tank never leaves its infantry in the open, and a screen
never stands out in front of armour that has fallen behind. Only members that
**keep up** are the body. A member that has stood ten seconds off its slot
without moving a cell is a **straggler**: a hull that cannot find its way, a squad
pinned down. So is a member that joined after step-off and has not yet walked up
to its slot. A straggler is left to catch up and rejoins the body when it moves
again (or, for a late joiner, once it has stood on its slot). Leaving it out matters
because the anchor never runs more than a few cells ahead of the body, and a
member already on its slot cannot go further forward. One member that could not
reach its own slot would otherwise hold the whole formation where it stood: on
Broad Front (2026-10-24) a lane stood still for six thousand ticks behind one
stuck tank and two walkers still coming up. With nothing keeping up, the
formation holds. At the end, the screen's line is on
the rally point and the main's just behind it, and both arrive together.

**A fighting advance.** Owner, 2026-10-23: "is there a 'fighting advance' where
they pause to fight when engaged, then resume the advance?", and "'stopping'
might not be right as it may prevent re-orientation to handle the attack". When
the formation is **engaged**, only its progress toward the rally point pauses.
It does not freeze. It turns to face the threat:

- The main holds where it stands on the route.
- The slots rotate about it.
- The screen swings round to stand between the main and the threat. From the
  front it is there already; from a flank it walks round.
- Every element fights from its slot. A stationed hull may still close on the
  threat within its leash, so armour can swing onto a flank contact before the
  infantry gets there.

As the threat moves, the formation keeps facing it. "Engaged" means a hostile
within contact range (sixteen cells) of any member, screen or main, or one that
a squad or walker of the formation has seen within the last second, however far
off: a picket its guns trade fire with at range. That is the commander's report
of what its units see, and it is how the advance looks deliberate.

The advance picks up again once the contact has **cleared**: nothing hostile in
range and nothing seen, for five seconds. It is not a fixed timeout. A contact it
cannot clear is **capped** at forty-five seconds by default. At the cap the
mission's choice applies: the formation either **pushes through**, facing the
route again and fighting as it moves, or **hands back**, suspending itself so
the mission decides. A battle where contact never lets up (a swarm) can shorten
both times.

The fighting advance sits above a squad's own attack-move, and the two differ:

- **Squad attack-move.** An attack-move is already a per-squad fighting advance
  (`ai-nouns.md`, The attack move and its cooperating group). A squad that meets
  a threat on its route commits to it, fights from firing positions held close
  to the route, and walks on when the threat lets go. It shoots on the move at
  contacts too slight or too far off its axis to earn that.
- **What the formation adds.** Every squad decides that alone. One squad halts
  to fight while its neighbours and the armour walk on, and nothing turns the
  group to a flank.
- **Together.** The formation pauses everyone's progress together and faces them
  all toward the threat. Each squad still fights its own engagement inside its
  slot.

**Losses.** A screen worn below a third of its strength at step-off is
**broken**. By the mission's choice, the advance then either halts where it
stands, or the main takes the lead with the screen's remnants and goes on as one
element. Squads, hulls and mechs that die drop out of their element. A late
wave may join either element.

**Side tasks.** A battle whose advance meets something to deal with on the way
(a satellite the barrage has already passed, a gap that needs holding)
**suspends** the advance: it gives no orders, and the mission's own orders
stand. When it **resumes**, the formation re-forms where its slowest element now
stands.

**Scripted per mission.** An authored battle declares the advance:

- its elements;
- its route;
- where the screen goes (ahead, and how far, or on the flanks);
- a trigger for the step-off (a tick, a beat, a lifted barrage);
- where something else sets how far it may go, a **limit** (a walking barrage's
  hold line, an objective not yet taken).

The defaults are what a generic commander would use. The advance raises beats
(`<name>.step_off`, `.contact_N`, `.cleared_N`, `.pushed_N`, `.stalled`,
`.rallied`, `.screen_broken`), and the mission
is free to turn them into its own. It is advanced from the mission's serial
tick, before the mission's `AlliedCommand`, which it drives. It never ends a
battle.

The behaviour scene `screened-advance` has four loops: infantry screening
armour, armour screening infantry, a flank contact (a picket rising abreast of
the formation, which it must wheel to meet), and a control that sends everything
straight to the rally point, which is what station orders alone do. In the
control the armour runs some fifty cells ahead of the militia.

## Boundaries

- Choosing where the allies go is the mission's job; the command only carries
  out those choices. A screened advance is a way of carrying them out, not a
  choice of where to go: the mission still names the route and the rally
  point. A general allied commander that weighs the whole field
  (`marine-side-reinforcement.md` applied to `ALLY`) is still later work.
- A hull's turret picks its own targets; the command only moves the hull.
- Making machines (a vehicle shop building hulls, `FabricationService`) and
  bringing them on (walk-in squads, convoys) belong to the mission's setup.
  The command takes a hull once it stands on the field.
