# Phased assault

Status: ACTIVE — direction for how a large attacking force lands, masses and
assaults a defended position. Nothing here is built yet; slices are on the
mission-command board.

Written: 2026-10-13

Read `conquest-command.md`, `forts.md` and `ai-nouns.md` first.

## Why

On a Conquest map with forts, the attacker trickles single squads down three
lanes. A squad that reaches a fort is cut down 3–8 cells from the bunker by the
wall nests and garrison; nothing has failed in command, the force is just
arriving one squad at a time against a position built to beat exactly that
(`forts.md`, "Built: command awareness"). A fort is meant to be hard. The answer
the owner chose is a doctrine change, not a weaker fort: land, mass, then
assault, and assault a known kind of base by a plan made for it.

## The three phases

1. **Foothold.** The opening is slapdash on purpose: take and hold the landing
   zone and probe the nearest enemy positions to learn what is there. This is
   roughly what Conquest command already does — single squads down lanes — and
   it stays, now with a purpose: hold the beachhead and discover.
2. **Muster.** Squads gather at the defended drop zone and combine into an
   **assault company** until it reaches critical mass for the objective in
   front of it. A company may be infantry only or mixed (mech lances,
   later other assets). While a company musters, the foothold keeps being held
   and probes continue; a muster is a deliberate pause, not idleness.
3. **Assault.** The company commits as one body against one objective. Against
   a stamped base (a fort, and later other authored installations) it follows
   an **assault plan** made for that stamp rather than leaving the approach to
   per-squad GOAP alone. GOAP still executes each squad's part; the plan says
   which part.

Phases are per objective, not per battle: one company can be assaulting a fort
while the next musters behind it.

## Laws

- **Fair knowledge.** Readiness and plans read only what the side believes:
  disclosed facts, sightings and memory. A plan for a fort applies once the fort
  is disclosed; until then the objective is ordinary ground.
- **Mass is measured, not counted.** Critical mass is a judgement of the
  company against what is believed to be in front of it (strength, nests,
  walls), with a timeout so a muster can never wait forever. The numbers are
  untuned and come from play.
- **Delivery is transport, not ownership** (`reinforcement-nouns.md`). A
  commander-called drop — including a mech lance — lands claimed for the
  commander that asked for it, like any other reinforcement.
- **Symmetric.** Nothing here is marine-only in its rules; the defender may use
  the same muster and plan machinery for a counter-assault.
- **A plan is data per stamp.** The plan for a battery fort lives with the
  battery fort's geometry (`FortPlan`: gates, nests, doors, belt openings), so a
  new stamp brings its plan with it.

## Assault plan

A plan is an ordered set of steps over roles, read from the stamp:

- **Base of fire** — elements (mechs are good at this) placed outside the wall
  with sight of the nests covering the chosen gate, suppressing them.
- **Breach / entry** — the assault element takes the chosen gate (or breaches a
  wall section with demolition) once the nests covering it are suppressed or
  dead.
- **Objective** — the element enters the aspect building chosen by
  `FortTactics` (bunker, plant, control room) and takes or wrecks it.
- **Hold** — part of the company holds what was taken against the counterattack.

Several plans per stamp are allowed (main gate, postern, breach), chosen by what
the company has (demolition, mechs) and what is believed about the defence.

## Mech lances arrive without the player

Today a marine mech lance only arrives through the player's Mech Support power.
A muster that wants mechs needs a commander-called drop: the same delivery (a
lift carrying a lance) claimed for the attacking commander, landing at the
defended drop zone, paid from the mission's own allowance rather than the
player's command points. The player's power is unchanged.

## Slices

1. **Foothold and muster** — attacker phases, a rally point at the defended drop
   zone, assault companies formed from squads, a readiness judgement with a
   timeout, and commit; the beachhead is held.
2. **Commander-called mech drop** — the attacker can call a lance to the drop
   zone from its mission allowance; the lance joins a company.
3. **Fort assault plans** — per-stamp plan data for the battery and shield forts
   and a plan executor driving a company through base of fire, entry, objective
   and hold.

Evidence for each is a scene (a single fort and a company) plus the Conquest
fixtures that declare forts, compared before and after.
