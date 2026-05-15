# Starsector Marines — Roadmap

> If you only read one file, read this one.

## What this is

A Starsector mod (game version 0.98a-RC8) that adds a Marine Operations
sub-game on top of vanilla. Setup and build details live in
[`CLAUDE.md`](../CLAUDE.md) at the repo root.

## Vision

The long-term north star is **a MechWarrior 3 / MechCommander Mercenaries-style
sub-game** inside Starsector. Instead of marines being anonymous cargo, the
player runs a merc company: named captains lead the troops, ships ferry the
team between planets, and contracts come from the planet's main faction, an
independent broker, pirates, and (at high enough rep with their enemies) a
deniable covert-ops track.

Each marine ops session is a full-canvas takeover of the planet interaction
dialog — own UI pipeline, own input routing, own rendering, no vanilla chrome
in the play area. This is intentional: the screens grow into their own
universe over time, not retrofitted into intel slots.

## Current focus

The **Marine Ops mission-select screen** is the active surface. Three-column
layout, planet rendering, client list with reputation gating, mission nodes
on the tactical map with hover popups. See [`backlog.md`](backlog.md) for
what's still unbuilt within this screen.

## Immediate next-up

1. **Mission click → briefing screen** — pick a mission, see full briefing,
   accept/decline. First "second screen" in the system; will inform whether
   we need a real Screen abstraction or if delegate-per-screen is enough.
2. **Text wrapping in `BitmapFont`** — deferred from this session. The
   briefing screen will force the issue (long flavor paragraphs).
3. **Faction-enemy covert ops as a mission category** — high-rep with
   Hegemony unlocks "deniable Hegemony contracts" in the mission list
   without adding another client row.

## How to use this directory

- **README.md** (this file) — vision, current focus, immediate next-up. Edit
  rarely; this is the stable view.
- **`backlog.md`** — known future work, grouped by area. Edit additively as
  ideas land.
- **`sessions/YYYY-MM-DD.md`** — per-session handoff. Write one at the end
  of every working session: what shipped, what's open, what to do next time.
  These are the load-bearing artifact for picking up cold.

## Related project context

- [`CLAUDE.md`](../CLAUDE.md) — build toolchain, Starsector API conventions,
  repo conventions. Read at session start.
- `~/.claude/projects/.../memory/` — Claude's project memory. Holds *patterns
  and gotchas* (UI font minimum, Starsector rulecmd package gotcha, GL state
  pollution, persistence pattern). Different purpose than this roadmap —
  patterns/preferences vs. features/decisions.
