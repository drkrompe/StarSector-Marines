# Perception integral systems

> The Janus suit is described as carrying sensor and electronic-warfare
> integration. In the simulation it is a fast suit that is hard to hit.

Status: PLANNED — depends on an explicit shared perception contract.
Written: 2026-08-28

Read `progression-nouns.md`, `integral-system-slate.md`,
`integral-armor-systems.md`, `powered-assault-armor-roles.md`, and the fog-of-war
model before implementing. Depends on `integral-system-use-policy.md`.

## Problem

Two Tri-Tachyon patterns are authored around perception and express none of it.
The Janus scout suit's identity is sensor and EW integration; the Specter
battlesuit's is continuous threat prediction. Both currently differ from their
peers only in the same four scalars everything else uses, which means the
corporate tradition's distinguishing idea is invisible in play.

Perception is also the one capability family the concept has repeatedly circled
and never landed. `powered-assault-armor-roles.md` flags concealment as needing
an explicit shared perception contract before anything touches it.
`integral-armor-systems.md` rejected a concealment system for the breacher and
explicitly relocated the idea to the light/recon role rather than deleting it.
This story is where that lands — from the *seeing* side rather than the *hiding*
side, which is the half the fog-of-war model can already almost express.

## Goal

A suit whose provenance is sensors spends that provenance: a bounded, expiring
capability that reveals more of the room than the wearer's ordinary vision does.

## Why seeing, not hiding

The shipped fog of war is per-cell shadowcast vision with ref-counted visibility
and a unit visibility gate. Extending *what one actor can see, briefly* works
along that grain: a wider or wall-tolerant read for a few seconds, ref-counted
like everything else, expiring cleanly.

Hiding works against it. Concealment means a unit is not visible to observers
who would otherwise see it, which touches the gate every other system trusts,
and it inherits the objection the breacher story already made at length: a room
that fails to react is indistinguishable from a room whose AI is broken. Seeing
has no equivalent failure mode — the player either gets information or does not,
and either way the world behaves normally.

## Standing rules

- **Bounded and expiring**, like every integral system. A permanent sensor
  advantage is a stat, and a stat belongs on the pattern.
- **Information, not authority.** A sweep reveals; it does not target, mark for
  extra damage, or bypass cover. The moment revealing is worth damage it has
  stopped being perception.
- **No hidden knowledge.** What the sweep reveals is revealed to the player too.
  A capability the player cannot observe is the hidden modifier the visible-issue
  law forbids, and a sensor system is unusually easy to build that way by
  accident — feeding the AI better information and showing the player nothing.
- **It does not conceal.** Concealment stays with the recon role and its own
  contract. This story must not become a back door to it.

## Scope

- A perception effect on `IntegralSystemDef`, parse-validated like the others.
- A bounded temporary vision contribution that composes with the existing
  ref-counted visibility rather than bypassing the gate, and releases exactly on
  expiry with no leaked references.
- The Janus sweep as the first carrier, cooldown-gated.
- Whether the Specter's prediction is the same capability at a different scale
  or a genuinely different effect — resolved in this story rather than assumed.
  It may well be that prediction wants a bounded reaction window instead, in
  which case it belongs with the firing-platform work and not here.
- Presentation, following `integral-system-battle-presentation.md`: a sweep the
  player cannot see happen is information arriving from nowhere.

## Out of scope

- Concealment, stealth, and anything that makes a unit unseen.
- Campaign-side sensor or detection behavior.
- A shared squad or company perception picture beyond what the sweep's own
  reveal contributes.
- Marking, tagging, or any damage or accuracy consequence of being revealed.

## Acceptance

- Activating the sweep reveals cells the wearer could not otherwise see, for its
  authored duration, and the reveal is gone on expiry.
- Visibility ref counts return to exactly their pre-activation values, including
  when the wearer dies mid-sweep.
- The player sees what the sweep revealed; no AI-only knowledge is created.
- A sweep confers no damage, accuracy, or targeting advantage that the same
  information obtained by walking around the corner would not.

## Open questions

- Whether a sweep should see through walls at all, or only further and wider.
  Wall-tolerant reads are the more interesting capability and the larger
  departure from how the model currently works.
- Whether two marines sweeping the same room should compose or overlap
  wastefully. Overlapping waste is arguably correct and is certainly simpler.
