# Integral system slate

Status: ACTIVE — direction for which patterns build a suit capability and what
it should be. The breach family, the shoulder micro-missile pod, and the Janus
sensor sweep are authored; the firing-platform brace is not.

Written: 2026-08-28
Updated: 2026-08-28 — the Janus sensor sweep shipped, leaving the line-suit brace as the only remaining candidate.

Read `progression-nouns.md` for the standing rules an integral system must obey,
`equipment-lore-catalog.md` for each pattern's provenance and deliberate limits,
and `integral-armor-systems.md` for the concept and its remaining scope. The
individual faction guides under `roadmap/factions/design/` carry the
institutional context this doc compresses.

## Purpose

An integral system is provenance made mechanical. It is the sharpest tool the
equipment model has for keeping faction suits from becoming palette swaps —
and the easiest one to ruin, because the failure mode is not a bad system but
too many adequate ones.

This doc exists so that question is answered once, across the whole catalog,
rather than one story at a time. It says which traditions should build a suit
capability, what it should be, and what it must not become. It does not say how
any of them is implemented.

## The claim is the role, not the tier

Breaching is what an assault suit is *for*. The XIV's own catalog copy calls it
a breach and shock pattern; the Bulwark is built for League breach formations;
the foundry-breaker is named after the act. A capability for getting through the
door is not a bonus bolted onto those patterns, it is the thing that makes them
that role rather than "heavy armour" — so **all six ASSAULT patterns carry a
breaching assist, and no other pattern does.**

That is deliberately not a tier rule. Every assault pattern happens to be
tier IV today, but a future tier-IV scout or line suit would still carry
nothing, and a cheaper assault pattern would carry one. What a player learns is
"assault suits breach", not "expensive suits get a trick".

What decides whether a pattern carries one is what that suit is *for*, and
nothing else:

- **One family, one role.** The breach family exists because breaching is a role
  definition — an assault suit that could not get through a door would be a
  strange assault suit. Every other capability below is an *individual* system on
  one named pattern, because holding ground and reading a room are things
  particular suits are built for, not things a whole role is defined by.
- **Do not give a faction a system so it has one.** `equipment-lore-catalog.md`
  establishes that absence is meaningful and that a faction-color clone is not a
  reason to add an item. A pattern with nothing distinctive to say should say
  nothing; the Church's Palatine and the League's Bastion are complete as
  tradeoffs and would be worse with a trick bolted on.
- **Suits sharing an effect must be different suits.** Identical numbers under
  different names is the palette swap this catalog exists to prevent. A test
  refuses a renamed copy.

## The breach family

Authored and running. Every one of these is the same effect and none of them
plays the same, because the axes a tradition is good at are the axes it spends
on. Both halves are live: the wearer speeds up, and the authored screen refuses
its fraction across its arc for the window's duration
(`combat-durability-nouns.md` owns what a screen is).

| Pattern | Tradition | System | The character it buys |
| --- | --- | --- | --- |
| `armor.heavy` | Hegemony (XIV) | **Assault bracing** | The perfected version, and the most *available* one — the longest window against the shortest recovery. Doctrine, not a gamble. |
| `armor.bulwark-heavy` | Persean League | **Interlock advance** | The widest screen in the family, covering more front than one wearer needs, because the marine beside you may not be from your navy. No worst axis. |
| `armor.specter-heavy` | Tri-Tachyon | **Predictive breach** | The briefest window, the quickest recycle, and the fastest crossing on the field. The screen is a courtesy. Prediction, spent rather than worn. |
| `armor.reliquary-heavy` | Knights of Ludd | **Consecrated advance** | Barely faster than standing still, for a very long time, behind the best screen anyone has. A Knight crosses the room in front of somebody else. |
| `armor.lions-mantle` | Sindrian Lion's Guard | **Blazon advance** | One grand gesture per engagement: long, bright, well covered, and then thirty seconds of nothing. Spectacle priced as spectacle. |
| `armor.foundry-breaker` | Pirate / Pather | **Breaching assist** | The crude copy, and the only one without a proper designation. The biggest raw shove in the family, over almost immediately, behind a sheet of scrap, followed by the longest sulk. |

Two of these are honest about being weak in the current build. The Consecrated
and Blazon advances spend most of their design on the screen, so until the
mitigation concept lands they under-express — the Armory correctly advertises the
Reliquary as a twelve-percent movement boost, which is true and is not what the
suit is for.

## Individual systems

Not a family. Each is one capability on one named pattern, because that pattern
is the one built for it.

Two are shipped:

| Pattern | Tradition | System | The character it buys |
| --- | --- | --- | --- |
| `armor.aegis-composite` | Tri-Tachyon | **Predictive volley** | The Specter's threat display is spent on evasion because breaching is what that suit is *for*; the Aegis is a line suit built to present a difficult firing solution rather than to cross a room, so its own display is spent the other direction — locking a shot instead of dodging one. A small salvo of `weapon.micro-missile` rounds, self-targeted, from a rack of two that does not refill. |
| `armor.scout` | Tri-Tachyon (Janus) | **Aperture read** | The only system that changes nothing about a fight except what the player can see of it. One wide active return, spent when the scout is walking onto ground they cannot see into: it opens the room they are standing against and a short way past its nearer walls, and then the walls start working again. It costs a long recovery and buys no advantage that walking round the corner would not have bought. |

That is a deliberate choice among three plausible homes. The faction lore
guides put micro-missile support in Hegemony, League, and Tri-Tachyon
traditions alike, which is three candidates and no obvious single one; the
pattern that carries the first pod was picked for what its tradition says
about *how* it fires rather than to fill a faction-grid slot. Hegemony's
assault heavies and League's are already spoken for by the breach family, and
a second system on an already-decorated ASSAULT pattern would blur the "one
family, one role" rule above. The Aegis is Tri-Tachyon's tier-III line
pattern — not a breacher, not yet spoken for, and its authored copy already
says "predictive threat displays" and "a difficult firing solution," which a
self-selecting missile lock is a truer reading of than a dodge would be — the
Specter already owns that half of "predictive."

One more is a candidate, gated on simulation work this doc doesn't own:

| Pattern | Tradition | Candidate capability | Needs from the simulation |
| --- | --- | --- | --- |
| `armor.line` or `armor.furnace-line` | Hegemony or Sindrian Diktat | **Brace** — a line suit planting as a firing platform: markedly steadier, and committed to standing there. | A stance with a real movement cost; accuracy as a timed effect. `firing-platform-integral-systems.md` picks whichever of the two the stance actually suits. |

Holding ground moved to the line role once the assault heavies took breaching,
and the split is better for it: **assault crosses, line holds.** That reads from
the role name alone, which the earlier allocation did not.

Everything else in the catalog carries nothing, on purpose. The Church's
Palatine, the League's Bastion, the pirate Reaver, and every tier-I and
tier-II pattern are complete as tradeoffs and gain nothing from a trick.

## What each of these must not become

The lore catalog's deliberate limits carry straight through, and the ones most
likely to be violated by a *capability* rather than by a stat are:

- **Concealment is not a systems answer.** `powered-assault-armor-roles.md`
  places concealment with the light/recon role behind an explicit shared
  perception contract. The Janus sweep reads the room; it does not hide the
  wearer, and shipping the seeing half deliberately did not open a door to the
  hiding half. `integral-armor-systems.md` already argued the breacher case at
  length — a room that fails to react is indistinguishable from a room whose AI
  broke — and the same reasoning applies to any suit that would go quiet.
- **A Tri-Tachyon prediction system is not a hidden neural bonus.** The lore
  catalog says outright that no neural-interface faction bonus exists outside the
  suit. The Specter spends its prediction as a short, visible, expiring window;
  it does not quietly improve the wearer's odds between activations.
- **Consecration is not damage reduction.** The Reliquary's rating is already
  paid for with lower capacity and the heaviest movement, and its system adds no capacity
  and no rating. What it buys is a timed, arc-limited screen someone else can
  walk behind — a squad capability with a cost, not a second helping of armour.
- **League modularity is not self-repair.** Repairability is the Bulwark's
  identity, and the obvious mechanical reading — restore something mid-battle -
  is durability, which is forbidden. The interesting half of "shared control
  standards" is the *shared* part, and the Interlock advance spends it on arc.
- **Sindrian spectacle is a liability too.** The Blazon advance is the longest,
  brightest window in the family behind the second-best screen, and it pays for
  that with the worst recovery. If it ever gains a visual treatment, the suit
  should be more conspicuous while it runs, not less.

## Where this leaves the board

`integral-armor-systems.md` owns the shipped concept and its remaining scope.
Directional mitigation and the shoulder pod have both landed, so every authored
system now expresses the whole of what it was written to be; every system
authors the moment it is spent at, and defenders wear the ones their patterns
declare (`progression-nouns.md`), and a running system is legible on the field:
its treatment is drawn from authored appearance data and, when it raises a
screen, that screen spans the arc it actually protects (`progression-nouns.md`,
presentation law). A new effect on this slate inherits that treatment by
describing itself rather than by being added to a list.

The one remaining individual system has a story of its own:
`firing-platform-integral-systems.md`. A pattern not listed here is not waiting
in a queue — it simply has nothing a capability would add.
