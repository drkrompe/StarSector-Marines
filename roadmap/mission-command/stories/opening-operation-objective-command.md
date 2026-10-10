# Opening-operation objective command

Status: IN PROGRESS — paired frozen command and authored COMMS/DEPOT places are
implemented; structural and live acceptance remain.

Written: 2026-08-25

Updated: 2026-10-10 — both chapters own mission-first maps; Counterattack adds a
joint staged advance and own-report defense. Live acceptance remains.

Read `mission-command-nouns.md`, `assault-command.md`,
`early-operation-nouns.md`, and
`autonomous-mission-command-foundation.md` before planning this story.

## Intent

Replace omniscient nearest-enemy chasing with a belief-honest paired command
whose duties express the authored opening scenario. Relief treats the local
anchor as the place to preserve against an assault; Counterattack treats the
bandit depot as the place to secure against a defense and bounded local
counteraction. Early Operations remains the authority for terminal mission
objectives and victory.

## Scope

- Consume preserve/secure context authored by Early Operations through
  perspective-appropriate disclosure. Until that feature supplies a terminal
  objective law, preserve/secure are command intent rather than a new victory
  condition.
- Migrate both factions to command frames and explicit pools. Authored local
  garrisons keep their duty; mobile finite forces receive stable scenario roles.
- Remove direct scanning of live opponent positions. Initial movement follows
  authored place/approach context; contact response follows faction knowledge.
- Publish the generic commander snapshot and operation-specific phase/reason.
- Preserve the finite militia/bandit force model and recurring-operation
  progression laws.

## Acceptance

- [ ] Relief militia preserve the anchor while mobile raiders assault it; the
  arriving player tips a battle that progresses before exact contact knowledge.
- [ ] Counterattack attackers advance on and secure the depot while bandits
  defend or locally counter from legally known context.
- [ ] Relief's disclosed anchor is an authored COMMS place, and Counterattack's
  disclosed anchor is an authored DEPOT place; each has legible structure and
  usable ingress without displacing landing pads; Relief stages its local line and assault approach from the finished place.
- [x] Reversing commander dispatch order does not change the command result.
- [x] Neither side receives a hidden hostile cell from mission command.
- [x] Command intent does not change terminal outcome authority; any new
  preserve/secure victory law is separately authored and accepted by Early
  Operations.
- [x] The selected-squad panel and dump explain scenario role, phase, objective,
  directive authority, and reason for both factions.
- [x] `opening-ladder-live-acceptance.md` remains the owner of live pacing,
  lift, force-ratio, and early-company acceptance. It does not treat company
  growth as offer ineligibility.

## Implemented architecture checkpoint

All fighting perspectives freeze before planning, and no strategy receives
`BattleView`. Relief discloses the checkpoint gate; its local and arriving allied
garrisons preserve it while the company and finite road assault have mission
ownership. Counterattack discloses gate, yard and dispatch destinations. Employer
boats convey separate allied mission ownership so their troops join the advance.
Company ownership and player intervention remain separate.

Counterattack uses attack-move duties through those public stages and bounded
defensive duties at the posts. Own contact and losses commit the reserve or
cause fallback, while a permanent warehouse guard retains the rear. Pure policy
checks cover those decisions; the production-factory matrix checks ordinary,
late, absent and paired fitted-boat arrivals with repeated command traces.

Both chapters now author their layout before staging: Relief owns a checkpoint
compound and Counterattack a larger freight depot. Finished interior anchors,
connected roads and clear berths are checked during construction. Preserve and
secure remain command intent, not fabricated interactions or victory rules.
Live operation pacing stays with `opening-ladder-live-acceptance.md`.

## Constraints

- Do not generalize opening-operation small-force rules to every Assault.
- Do not add reinforcements, late-game support, or a third opening-operation variant.
- Do not let objective state disclose a hostile identity or exact approach.
- Do not move terminal objective or victory ownership into AI command.

## Exit

Fold durable opening-scenario command laws into `early-operation-nouns.md`, add
this story to the mission-command shipped ledger, and delete it when implementation ships.
