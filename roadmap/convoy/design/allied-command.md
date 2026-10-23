# Direction: the allies command their own machines

Status: ACTIVE — enlistment and the allied command's two tasks have landed; the
Cordon's logistics base and The Burn's advancing armour build on them.
Read `convoy-nouns.md` first; this extends `vehicle-as-commandable-unit.md` to
the allied side.

Written: 2026-10-22

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
  arrives, the hull gives up waiting and stations.

An allied **mech** is stationed the same way: its squad attack-moves to ground
the mech can stand on at the post, the post pulled back toward the mech a step
at a time where it cannot (woods, a wall). A mech is a squad member, so this is
a squad order, not a hull's; its doctrine fights it once there.

Squads the allied command orders are under `GARRISON` authority, as every
allied garrison's are.

## Boundaries

- Choosing where the allies go is the mission's job; the command only carries
  out those choices. A general allied commander that weighs the whole field
  (`marine-side-reinforcement.md` applied to `ALLY`) is still later work.
- A hull's turret picks its own targets; the command only moves the hull.
- Making machines (a vehicle shop building hulls, `FabricationService`) and
  bringing them on (walk-in squads, convoys) belong to the mission's setup.
  The command takes a hull once it stands on the field.
