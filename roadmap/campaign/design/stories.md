# Campaign story board

Status: ACTIVE — 10 open stories

Written: 2026-08-23

Updated: 2026-10-13 — authored arcs now have dedicated boards; campaign-wide work remains here.

Read `campaign-nouns.md` and `meta-progression.md` before changing an
umbrella story. Live feature work is owned by its feature board; broad
economy, relationship, and background direction remains in design until it
can be made a bounded vertical slice.

For authored mission sequences and their dedicated arc boards, start with
`campaign-overview.md`. This board retains campaign-wide systems work.

| Story | Status | Dependencies / freshness |
| --- | --- | --- |
| `retreat-defeat-settlement.md` | In progress — implementation ready for live acceptance | Confirmed Retreat uses ordinary defeat Results and writeback; verify pre-landing exit, losses, recovery, retry, and source-specific settlement in game. |
| `stationed-ground-defence.md` | In progress — slices 1–5 shipped, live pass remains | A Garrison detachment counts toward the protected market's vanilla ground defences, a won vanilla-triggered defence sends the raid home, and an unanswered one settles from vanilla's own result. Adds the raid-intel reader beside the existing fleet-group trigger. First of the pair. |
| `polity-defence-raid-hook.md` | In progress — slices 1–6 shipped, live acceptance remains | Meet a vanilla raid on a player-owned colony on the ground, and post a detachment there for the days the player is elsewhere. Depends on the stationed-defence story for its readers, ending handle, and absent settlement. |
| `derived-polity-roster.md` | In progress — slices 1–4 shipped, live pass remains | The polity ground doctrine implemented: released kit, production quality, a derived `GroundRosterProfile` registered under the player faction, defence-strength headcount net of the company, three doctrine points and their panel. |
| `campaign-backend.md` | In progress — all but news and world basics moved | What screens ask of the running campaign goes behind one seam, family by family, so `playBattle` can host them with no game. Each family needs a pass in the real game. |
| `player-raid-fork.md` | In progress — built and unit-tested, live pass remains | Lead the player's own raid on a market as a Marine Operations battle, or launch it as usual; works with and without Nexerelin (`raid-resolution.md`). |
| `ruins-expedition-fork.md` | In progress — slices 1 and 2 (fork, commit path) built, development builds only | Send the company into a ruins world's ruins as a Marine Operations battle, or the salvage team as usual; a won expedition commits through vanilla's own salvage (`ruins-expedition.md`). |
| `ruins-expedition-battle.md` | Planned — owner direction | The Ruins Expedition, a mission family of its own: a delve to the vault, the breach hold and the haul out to a held lift point, with the delve and crew commanders, against scavengers on a stand-in map. Slice 3 of the fork. |
| `ruins-occupants.md` | Planned — owner direction | The seeded occupant per ruins site (nobody, scavengers, Rav, derelict security) and its survey readings in the briefing. Slice 5 of the fork; derelict security needs `derelict-security-network.md`. |
| `ruins-works.md` | Planned — owner direction | The ruins' works (seal doors, power hall and junctions, collapses, records terminal) and their sealed stores: an empty site's puzzle and an occupied site's detours, so every expedition gives gameplay and a reward. Slice 5 of the fork. |
