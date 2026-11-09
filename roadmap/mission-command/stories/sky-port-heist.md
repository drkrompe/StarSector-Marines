# Story — Sky port heist: a raid on a freighter at its berth

Status: IN PROGRESS — the timed raid, the called freighter and the debug mission are built; the owner's playtest and
the next layer of the job remain.

Written: 2026-11-09

Read `raid-command.md` (the raid's seize-and-egress law and its commanders) and `voids.md` (the sky port station:
landing deck, viaducts, raised concourse, platforms, track canyons, slips) first.

## What the owner asked for

The sky port station needed a reason to be there (owner, 2026-11-09: "are we here to raid or steal something?").
Of a freighter heist, a snatch and grounding the port, the owner chose **the freighter heist**: a freighter carrying
cargo a patron wants is due at one of the station's platforms; the company lands on the west deck, fights up a
viaduct and across the concourse, is at the platform when the freighter berths, seizes the cargo from its hold, and
brings it back to the landing deck. The freighter stands at its berth only a few minutes. If the company is late it
leaves; if early, the company holds the platform under fire until it comes.

## Model

- **A raid with a service window.** The heist is a Raid (`RaidObjective`) whose target, the freighter's hold, may
  be served only within a `RaidObjective.ServiceWindow`: from the tick the freighter berths until the tick it leaves.
  Before the window the hold cannot be worked and raises no alarm; once the window closes with the cargo not seized,
  the raid has failed, so the defender wins. After seizure the window no longer matters: the package is abstract, as
  every raid's is, and any surviving Marine combatant reaching the landing deck's egress pocket completes the job.
- **One schedule, two readers.** `SkyPortHeistSetup` states the freighter's time at its berth once. The battle reads
  it as the raid's window; the backdrop reads it as a `VoidBackdrop.Call` at the slip, so the void renderer flies the
  freighter in down the canyon, slowing, stands it at the berth exactly while the window is open, and flies it out,
  turning for the way out. A slip with a call carries no ambient traffic. The renderer keeps the battle's own clock
  for a call, never the presentation's, which is the one thing it reads from the battle.
- **The checklist says the clock.** Before the window the raid row reads how long until the freighter berths, as its
  instruction and its blocker; while berthed, how long until it leaves.
- **Port security** holds the two viaduct heads on the concourse and the berth as garrisons, outside mission
  command; a mobile reserve of two squads on the concourse answers the raid's alarm under `RaidDefenderCommand`.
  The station is an independent port and its security is **independent customs** (owner, 2026-11-09), drawn from
  the independent ground roster.

## Built

- `RaidObjective.ServiceWindow` and `withServiceWindow`; the raid row's clock lines (`objectiveRaidBerthsIn`,
  `objectiveRaidLeavesIn`). Tests: `RaidServiceWindowTest`.
- `VoidBackdrop.Call` and the renderer's called craft (`VoidBackdropRenderSystem.callCraft`). Tests: `VoidCallTest`.
- `SkyPortHeistSetup`: the freighter (a Tarsus) due at the slip between the third and fourth platforms, its hold on
  the third platform's edge, egress at the landing deck, both landing pads as the withdrawal pads, garrisons and
  reserve, the paired Raid commanders. **Sky Port Heist** on the debug missions' Diagnostics tab;
  `playBattle -Pbattle=sky-port-heist`.
- The look suite's `9-freighter-berthed` shot.

Every time, place and strength in the setup is a first placement (UNTUNED): when the freighter berths (4:00) and
leaves (7:00), where port security stands and how many. Balance is the owner's playtest.

## Left

- The owner's playtest of the clock, the defence and the route.
- The freighter's own crew and guns, and port security reinforcing by air down the canyons once the alarm is up.
- Briefing art and a campaign offer: the heist is a debug mission only.
