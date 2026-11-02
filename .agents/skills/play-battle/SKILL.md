---
name: play-battle
description: Question a Starsector Marines battle while it is being played in the playBattle window - what a squad is doing and why, units, mission command, objectives, story beats, who hit whom - and pause it, resume it or run it to a tick or story beat, from a shell, with no MCP server to connect and no session restart. Use whenever the owner has playBattle open with -PplayServer, or asks what is happening in a battle they are playing, for mission design or diagnosis.
---

# Asking a played battle questions

`gradlew playBattle -PplayServer` serves a read-only diagnosis server on
`127.0.0.1:47811` while its window is open (`build-and-run.md`, `PlayDiagnosis`).
These scripts call it with `curl`, so it does not matter whether the server was up
when this session started. Each call stands alone.

Run them from the repository root, or any worktree of it:

```bash
.agents/skills/play-battle/scripts/play-status.sh
```

```bash
.agents/skills/play-battle/scripts/play-query.sh squads
```

```bash
.agents/skills/play-battle/scripts/play-query.sh "why 4" "units squad=4"
```

```bash
.agents/skills/play-battle/scripts/play-hitlog.sh on
```

```bash
.agents/skills/play-battle/scripts/play-run-to.sh 900 speed=4
```

Answers are one line of JSON on stdout. Exit 1 means the server refused or the
question failed, with the reason on stderr. Exit 2 means nothing is listening: ask
the owner to open the window, then just run the script again. With no Python on the
path, the answer comes back inside its JSON-RPC envelope.

If the owner chose another port with `-PplayServer=<port>`, set `PLAY_BATTLE_PORT`.

## Stopping at a moment

Stop the battle first when several questions must describe one moment.

| Script | Does |
|--------|------|
| `play-run-to.sh 900 [speed=1\|2\|4]` | Runs to tick 900 and pauses on exactly it |
| `play-run-to.sh +300` | 300 ticks from now, for a query, wait, query loop. While paused, `+1` steps one tick |
| `play-run-to.sh beat=final_run` | Pauses when a story beat whose id contains the text fires |
| `play-pause.sh` | Pauses now and answers the tick |
| `play-resume.sh [1\|2\|4]` | Runs again, at the speed it last ran at unless told |

`play-run-to.sh` waits until the battle has stopped, then prints the status: `run.state`
is `ARRIVED`, `BEAT` or `COMPLETE`, with `stoppedAt` and `reason`. At 30 ticks a second a
far target takes a while, so give a long run `speed=4` and run the script in the background.

These press the battle's own time buttons, so the owner sees the HUD change. **They move
the owner's battle: use them when the owner asked for it or has handed over the clock.**
If the owner changes the speed during a run, the run ends with `CANCELLED` and exit 1, and
nothing fights them. `run` stays `CANCELLED` until the next run; check `time` in the status
for whether the battle is paused now.

A run only goes forward. The window's asynchronous battle does not repeat, so to see one
moment again exactly, use `battleProbe`.

## Finding out why

Start with `play-status.sh` to see which screen the player is on, then `squads` to find
a squad, then `why <squad>`. Read these fields of `why` first:

- `playerCommands`: the player owns this squad, so mission command gives it no
  objective. An idle squad here is waiting for an order. That is not a fault.
- `directive`: who pointed the squad here (`issuer`, `authority`), `reason`, `status`
  and `disposition`.
- `goal`, `plan`, `steps`: what the planner chose and which step is running.
- `route`: whether the way there is built (`READY`, `BUILDING`, `STARVED`, ...).

`command` lists every side's directives, including ones the arbiter rejected, so it
shows what mission command tried to do as well as what happened.

## The commands

| Command | Answers |
|---------|---------|
| `why SQUAD` | One squad, from who owns it down to each member |
| `squads [label=TEXT]` | Every squad alive: assignment, reason, goal, plan, route, centroid |
| `units [label=TEXT] [squad=N]` | Bodies: position, health, squad, goal, order, reflex, target. A label is `FACTION:TYPE`, such as `label=DEFENDER` |
| `entity ID` | Everything readable about one body |
| `command` | Each side's strategy, phase and directives, and every objective |
| `presence` | What keeps the company in play: bodies, boats, reserve, objectives |
| `beats [since=T] [text=TEXT]`, `places` | Story beats; compounds and capture |
| `near ID R`, `near-cell X Y R`, `sight ID R`, `find [label=TEXT]` | What is around a body or cell, and what it can see |
| `path ID [count=N]`, `members SQUAD`, `speed ID` | A unit's route, a squad's members, why a body moves at its speed |
| `cell X Y`, `grid X0 Y0 X1 Y1`, `ground ...`, `windows ...` | The map. Keep the box small: a whole-map `grid` is 164 KB |
| `route X0 Y0 X1 Y1`, `drive ID X Y` | Whether a way exists, on foot or for a hull |
| `escort`, `lanes`, `bores`, `creep`, `pads`, `cordon`, `airbase`, `corridor FROM TO` | One mission's own machinery |
| `hits [target=ID] [attacker=ID] [since=T] [limit=N]`, `hits-by attacker\|target` | Who hit whom, recorded only since `play-hitlog.sh on` |

## What to expect

- **The battle moves between calls** unless it is paused. Commands in one
  `play-query.sh` call see one tick, so ask together whatever must agree; every answer
  carries its tick.
- **A question never moves the battle.** `advance`, `until`, `watch`, `topology` and the
  other stepping commands are refused in `play-query.sh`; move it with `play-run-to.sh`.
  There is no way to give orders from here.
- **"no battle is being played"** comes from the mission board and other screens;
  `play-status.sh` still answers there.
- **"reached no frame in 5 s"** means the window is opening or building a battle. Ask
  again.
- **Never start or close the owner's window** unless asked. `gradlew playBattle` needs a
  worktree, as every build does. A window a session opens without `-PplayScript` passes
  `-PplayProfile=script`, or its board arrivals overwrite the owner's saves.
