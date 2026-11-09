# Campaign story board

Status: ACTIVE — 11 open stories

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
| `ruins-expedition-fork.md` | In progress — slices 1 to 5 (fork, commit path, battle, ground, occupants) built, development builds only until the live pass | Send the company into a ruins world's ruins as a Marine Operations battle, or the salvage team as usual; a won expedition commits through vanilla's own salvage (`ruins-expedition.md`). |
| `ruins-guardians.md` | In progress — owner picks in; derelict guardians' rules built | Ruins mini bosses: a vault guardian at every site and a layer guardian from extensive up; derelict security's hive cradle in a light well and its bracing vault frame, each with a readable fight, a signpost and a store. |
| `ruins-pressure.md` | In progress — the stir and its pushes fielded per occupant, with their comms line; wake points and the Delve row remain | Constant pressure in the delve: an occupied site's stir rises with time inside and loud acts, never falls, and each step sends a push out of a finite reserve, hunting the company or, when it sits still, onto the way back. |
| `ruins-vault-prize.md` | In progress — picks decided; draw, tell, batteries and stakes built; prize settlement and themed ground left | Each vault holds one seeded prize beside vanilla's haul (goods, tech, an AI core or a colony item) and the site is built round it, so its survey tell, approach, art and works hint what is inside. |
