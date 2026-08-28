# Integral system slate

Status: DRAFT — direction for which traditions build a suit capability and what
it should be. One system is shipped; the rest are unimplemented.

Written: 2026-08-28

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

## The scarcity rule comes first

Most patterns declare nothing, and that is the point. A system is a reason to
want one particular suit; if most suits have one, the reason is gone and a
system becomes a tax on the tier — the exact flattening the concept was written
to avoid. The shipped catalog keeps carriers to a small minority deliberately,
and a test pins it.

So this slate is a **budget, not a checklist**. Nineteen patterns should not
converge on nineteen systems. A tradition appearing below is a candidate, not an
entitlement, and several rows should stay unbuilt permanently.

Two consequences worth stating plainly:

- **Do not give a faction a system so it has one.** `equipment-lore-catalog.md`
  already establishes that absence is meaningful and that a faction-color clone
  is not a reason to add an item. The same discipline applies here, harder.
- **A tier is not a claim.** A tier-IV battlesuit with nothing but excellent
  scalars is a perfectly good suit. Heavy is a role, not a promise of a trick.

## What the lore supports

Each row names the sim capability it would need, because that — not the flavour
— is what decides whether it is one story or five. Rows sharing a capability
should ship together or not at all.

| Pattern | Tradition | Candidate capability | Needs from the simulation |
| --- | --- | --- | --- |
| `armor.foundry-breaker` | Pirate / Pather foundry cells | **Breaching assist** — shoulder rams and a salvaged screen on one trigger. *Shipped, movement half only.* | Timed directional mitigation (`d5-timed-directional-mitigation.md`) for the screen. |
| `armor.scout` | Tri-Tachyon (Janus) | **Sensor sweep** — the suit's integrated sensor and EW package spending itself on a brief, wide read of what is actually in the room. | A shared perception contract; bounded temporary vision that is not permanent sight. |
| `armor.specter-heavy` | Tri-Tachyon blacksite (Specter) | **Threat prediction** — continuous prediction resolved into a short window of much better reaction. | Same perception contract, or a bounded evasion window; must not become incoming-accuracy in disguise. |
| `armor.heavy` | Hegemony (XIV) | **Brace** — powered bracing planted as a firing platform: markedly steadier, and committed to standing there. | A stance with a real movement cost; accuracy as a timed effect. |
| `armor.lions-mantle` | Sindrian Lion's Guard | **Sustained fire** — oversized cooling spent on a burst the suit could not otherwise support. | The same stance/timing support; a weapon-side sustain the shipped model does not have. |
| `armor.reliquary-heavy` | Knights of Ludd | **Covering the line** — a consecrated shell extending its screen over someone beside it. | Timed directional mitigation, extended to a second actor. |
| `armor.bulwark-heavy` | Persean League | **Shared control** — the coalition standard's actual identity: designating for the fire team rather than doing something alone. | Squad-level target sharing; command authority boundaries. |

Three of the shipped tiers appear nowhere above, on purpose: the Church's
Palatine, the Diktat's Furnace line, and the pirate Reaver are complete as
tradeoffs and gain nothing from a trick.

## What each of these must not become

The lore catalog's deliberate limits carry straight through, and the ones most
likely to be violated by a *capability* rather than by a stat are:

- **Concealment is not a systems answer.** `powered-assault-armor-roles.md`
  places concealment with the light/recon role behind an explicit shared
  perception contract. A Janus sweep reads the room; it does not hide the wearer.
  `integral-armor-systems.md` already argued the breacher case at length — a room
  that fails to react is indistinguishable from a room whose AI broke — and the
  same reasoning applies to any suit that would go quiet.
- **A Tri-Tachyon prediction system is not a hidden neural bonus.** The lore
  catalog says outright that no neural-interface faction bonus exists outside the
  suit. A prediction window has to be a visible, expiring, readable effect or it
  is exactly the hidden modifier the visible-issue law forbids.
- **Consecration is not damage reduction.** The Reliquary's rating is already
  paid for with pool and the heaviest movement. A Knight covering someone else is
  a squad capability with a cost; it is not a second helping of armour.
- **League modularity is not self-repair.** Repairability is the Bulwark's
  identity, and the obvious mechanical reading — restore something mid-battle —
  is durability, which is forbidden. The interesting half of "shared control
  standards" is the *shared* part.
- **Sindrian spectacle is a liability too.** If the Lion's Mantle gets a sustain
  system, the lore's own framing says the suit should be more conspicuous while
  it runs, not less.

## Where this leaves the board

`integral-armor-systems.md` owns the shipped concept and its remaining scope.
The mechanics this slate needs are storied separately, because each one is
gated on different simulation work rather than on authoring:
`d5-timed-directional-mitigation.md`, `integral-system-use-policy.md`,
`integral-system-battle-presentation.md`, `shoulder-micro-missile-pod.md`, and
`defender-integral-systems.md`. Two concrete faction groups have stories of
their own where the capability is well enough understood to plan:
`perception-integral-systems.md` and `firing-platform-integral-systems.md`.

The remaining rows stay direction until something makes them next.
