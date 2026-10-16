# Hegemony auxiliary warrant

> The first military warrant a company can earn by story rather than by
> standing: the Hegemony takes on a mercenary company it has watched fight.

Status: PLANNED — builds on the shipped Restricted tier (`progression-nouns.md`,
Kit and armory). The second stage depends on `warrant-obligations.md`.
Written: 2026-10-15

Read `campaign-kit-arc.md`, `progression-nouns.md` (Kit and armory),
`a-small-war.md` (its ending and its military-source gate) and
`early-operation-nouns.md` (Cold Account and Kord) before implementing.

## Problem

The standing-warrant rule (`progression-nouns.md`) makes every
approved suit reachable. Its bar is deliberately high: Prestige band plus
close faction standing. That is right for the Knights or the Lion's Guard,
but it leaves the authored campaign without its own beat. `a-small-war.md`
ends with "an introduction to a more demanding procurement contact" and asks
for "a later authored arc, not an unimplemented shop or a tier-IV unlock
button". Its plan step 7 calls for that route before strict story-first
progression can be claimed.

The campaign has already earned this beat. At Garnir the company fought
beside Captain Irena Kord's Hegemony forces. On Agreus it learned to field
recovered Hegemony and Tri-Tachyon patterns. The Hegemony is the tradition
that has seen the company, so the Hegemony is the one to ask.

## Scope

**The offer.** After *A Small War* settles, the procurement introduction Rusk
passes on reaches a Hegemony procurement office. It arrives as readable
correspondence, in the same shape as Rusk's civil-security terms. It is not a
shop or a button. Its terms cite the record that justifies it:

- the Garnir after-action report from HSS Resolute;
- the company's Agreus service, which it carried out as a recovered-suit
  section under civil terms.

The office is a new authored contact. Its name, voice and staging belong to
the arc author. Kord does not deliver it; she was "the intervention's one-off
Hegemony contact". Her report is the evidence, not the messenger.

Preconditions:

- *A Small War* completed.
- Hegemony relationship at least Favorable (the licence threshold).
- No standing hostility toward the Hegemony.

The standing rule's higher bar does not apply. That is the point of an
authored route.

**The terms**, readable before acceptance:

- The company is enrolled as a Hegemony auxiliary. It gains the military
  warrant's access and accepts the obligations of
  `warrant-obligations.md`, all stated in the offer.
- **Stage one, at acceptance:** the warrant opens `armor.arbalest`, the
  military support suit. The Hegemony licence channel may then offer its card.
  The Arbalest comes first because it is the pattern the company already
  fields as a recovered frame. The first approved suit it holds is the
  upgrade of one it knows.
- **Stage two, after the first obligation period is honoured:** the warrant
  adds `armor.heavy`, the XIV shock pattern. The Hegemony trusts the company
  with assault armour only after it has kept its terms once.
- What it does not grant, stated as Rusk's terms state their limits:
  - no other tradition's Restricted kit;
  - no release of military suits to the company's polity;
  - no Hegemony command authority;
  - no immunity from the Hegemony's own law.

**Declining is a real answer.** A company that declines keeps every other
route, including the standing rule later, and is not offered the same terms
again. Mercenary independence has a price, and so does enrolment.

**Settlement.** The grant goes through the existing once-only reward
settlement. A reloaded or restarted debrief can never grant it twice, or
grant stage two early.

## Out of scope

- The Counter-Battery Issue card and any other new loadout card
  (`campaign-kit-arc.md`).
- Military warrants from other traditions; they use the standing rule until
  their own arcs are authored.
- Showing approved Hegemony kit at Garnir; that is Corvus roster work, tracked
  in `campaign-kit-arc.md`.

## Acceptance

- A unit test on the offer's eligibility: offered only after *A Small War* and
  at Favorable or better, never twice, and not to a company hostile to the
  Hegemony.
- A unit test on settlement: acceptance records a Hegemony military warrant
  naming only `armor.arbalest`. Stage two adds `armor.heavy` only after an
  honoured obligation period. Replayed settlement grants nothing further.
- With the warrant held, the Hegemony licence channel offers the military
  Arbalest card, and no other channel or faction offers it.
- Offer and debrief correspondence snapshots at 1920x1080, 100% UI scale,
  showing the terms, the limits and the stage-two condition.

## Plan

1. The offer as a dossier entry, gated on *A Small War*'s settled state and
   Hegemony standing.
2. Terms copy, written as readable correspondence alongside Rusk's.
3. Acceptance records the military warrant (stage one); `warrant-obligations.md`
   reports the honoured period that triggers stage two.
4. Snapshots, then fold: the authored route enters `campaign-overview.md`'s
   arc table. `a-small-war.md` step 7 cites the shipped gate.
