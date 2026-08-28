# Integral system use policy

> The suit knows what it does. It does not yet know when to do it, and right now
> the answer is twelve cells, hard-coded, for every system that will ever exist.

Status: PLANNED — the shipped trigger is a deliberate placeholder.
Written: 2026-08-28

Read `progression-nouns.md` and `integral-armor-systems.md` before implementing.

## Problem

`IntegralSystemSystem` decides when to spend a breacher assist with a rule
written into the sweep: the marine is moving and something hostile is inside
twelve cells. That is real behavior and it fires at roughly the right moment,
but it is one rule for one effect, and the second authored system will have no
place to put its own.

Special equipment already solved this. `SpecialAiPolicy` is a closed enum
selected per definition and validated at parse time against the effect it
accompanies, so a definition cannot declare a payload with a policy that makes
no sense for it. The standing rule in `integral-armor-systems.md` says an
integral system is a second carrier for that vocabulary and not a second
vocabulary — and it currently carries none of it.

## Goal

An integral system authors its use policy the way special equipment does, and
the sweep dispatches on the authored policy instead of on the effect.

## The naming wrinkle worth deciding deliberately

The four shipped policies — hardened direct fire, soft cluster indirect, squad
smoke screen, contact demolition — read like descriptions of the *item*. They
function as descriptions of the *moment*: what has to be true for spending this
to be a good idea. That reading is the one to keep, because every shipped policy
is about a target and an integral system may have no target at all. A self-
directed capability has nothing to aim at, only a situation it is right for.

So self-directed policies must be named for the moment, not the effect:
something like crossing open ground under fire, holding a position to shoot from,
or reacting to first contact. A policy named after its effect would be a second
vocabulary wearing the first one's clothes, and the next system would need
another one.

Whether these join the existing enum or sit beside it is the real decision here.
Joining it keeps one vocabulary and one parse-time validation path, at the cost
of an enum whose members no longer share a shape. A sibling keeps each family
coherent and risks the two drifting. The standing rule leans toward joining;
this story should confirm that against the second and third policy rather than
against the first.

## Scope

- A use policy authored on `IntegralSystemDef`, required rather than optional,
  and validated against the declared effect at parse time in the same style the
  armour catalog already refuses durability keys.
- At least two policies, so the dispatch is exercised by something other than a
  single case. One is the breacher's crossing; the second should come from
  whichever faction system lands next.
- The sweep dispatching on policy, with the twelve-cell rule becoming that first
  policy's authored parameters rather than a constant in a system class.
- Parameters authored per system where they are genuinely per system — a
  threat radius that suits an industrial rig charging a doorway is not obviously
  the radius that suits a recon suit reacting to contact.

## Out of scope

- Player manual activation. Squad and individual override authority is its own
  question and `progression-nouns.md` already places tier override elsewhere.
- Reworking the four shipped special-equipment policies or their behavior.
- Defender adoption, which `defender-integral-systems.md` owns and which will
  exercise these policies harder than the player side does.

## Acceptance

- No integral system's trigger lives in a system class; every one of them is
  readable from the catalog entry.
- A definition declaring a policy that cannot apply to its effect is refused at
  parse time, with a message naming the policies that do apply.
- The breacher assist fires on the same occasions it does today, measured
  through the mission harness rather than asserted.
- A second system with a different policy fires on different occasions, and
  neither one's parameters affect the other.

## Open questions

- Whether an integral system and a carried special item should ever coordinate —
  a marine who has just spent a breacher assist is in a very specific situation,
  and the grenade in their hand does not know it. This is probably a later
  squad-level question rather than an equipment one.
