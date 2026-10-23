# Direction: the allies command their own machines

Status: ACTIVE — enlistment, the allied command's two tasks and the screened
advance have landed; the Cordon's logistics base and The Burn's advancing armour
build on them.
Read `convoy-nouns.md` first; this extends `vehicle-as-commandable-unit.md` to
the allied side.

Written: 2026-10-22
Updated: 2026-10-23 — the screened advance: a screen and a main element bounding to a rally point

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
drivable route the same way, and between orders it fights as an autonomous
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
its own. A refused goal is therefore pulled back to the last corner of the way
there and asked again; standing at that corner the hull turns on its own and is
sent on to its post. Where no way is known at all the goal is pulled back a step
along the straight line instead. Both are bounded, so a hull with nowhere to go
stays where it is rather than asking for ever.

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
leave their infantry behind (or, held behind an aim, wait for nothing), and an
advance looks like a crowd rather than a plan.

A **screened advance** (`ScreenedAdvance`) is two elements going to a **rally
point** together, one covering the other:

- The **screen** goes in front: infantry screening armour through woods or
  streets, where a tank alone is blind and the swarm closes unseen, or armour
  screening infantry across open ground, where the guns take the fire.
- The **main** comes behind it, never nearer the screen's centre than the
  **standoff** (ten cells by default) and never posted forward of it, however
  much faster it is.
- Either element may be squads, hulls, mechs or a mix. Squads take an
  attack-move under `GARRISON` authority; hulls and mechs take this command's
  station orders. So each element fights on its own doctrine wherever it is put,
  and the advance only moves it.

**The route and its bounds.** The route (`AdvanceRoute`) is a line of cells from
the start line to the rally point. A **bound** is a phase line across it, given
as a distance along it. By default there is a bound every 24 cells, and the last
bound is the rally point itself. Each element stands in a line abreast across
the route, six cells between neighbours.

**A bound.** On each bound:

1. Once the bound's gate is open, the screen steps off to the bound's line. The
   main holds where the last bound left it.
2. The screen arrives: two thirds of its squads and machines are at their
   places.
3. The main closes up to the standoff behind the line.
4. Both halt for a few seconds to regroup.
5. The bound is cleared, and the screen steps off for the next one.

This is bounding overwatch. It turns speed into nothing: the faster element
waits for the slower one at every line, so the two arrive together however
mismatched they are. An element is given ninety seconds to reach a bound before
it is taken to have reached it, so one machine stuck on a fence does not hold
everyone.

**Contact.** Contact is a hostile within contact range (sixteen cells) of a
screen member. It is the commander's report, not a squad's belief, and that is
the point: it is how the advance looks deliberate. When contact comes:

- The screen halts where it stands, short of its bound.
- The main closes up behind it to give it fire.
- The screen goes on to the same bound once the contact has been quiet for six
  seconds, or after twenty seconds in contact, the main still behind it. A swarm
  that never lets up cannot stop the advance for good.

**Losses.** A screen worn below a third of its strength at step-off is
**broken**. By the mission's choice, the advance then either halts where it
stands, or the main takes the lead with the screen's remnants and goes on as one
element. Squads, hulls and mechs that die drop out of their element. A wave
arriving late may join the screen, and machines may join the main.

**Scripted per mission.** An authored battle declares the advance: its elements,
its route, its bounds (a spacing, or explicit distances), a gate on any bound
(for example, "the barrage has lifted off this line") and a trigger for the
step-off (a tick, a beat, a lifted barrage). The defaults are what a generic
commander would use. The advance raises beats (`<name>.step_off`, `.bound_N`,
`.contact_N`, `.rallied`, `.screen_broken`), and the mission is free to turn
them into its own. It is advanced from the mission's serial tick, before the
mission's `AlliedCommand`, which it drives. It never ends a battle.

The behaviour scene `screened-advance` has three loops: infantry screening
armour, armour screening infantry, and a control that sends everything straight
to the rally point, which is what station orders alone do. In the control the
armour runs some forty-seven cells ahead of the militia.

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
