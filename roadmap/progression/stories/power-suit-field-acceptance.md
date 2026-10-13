# Power-suit field acceptance

Status: IN PROGRESS — bounded exchange evidence exists; mission balance, live persistence, and final art remain unverified.
Written: 2026-10-13

Read `progression-nouns.md`, `personnel-nouns.md`, `combat-durability-nouns.md`,
`role-and-access.md`, and `power-suit-infantry.md` before this acceptance pass.

## Scope

Validate the explicit equipment category on real named squads, then contract
only the tuning or visual work the evidence requires. The equipment category
preserves infantry structure, systems, and formation size.
It does not establish a two-squad equivalence or a final campaign pacing target.

## Controlled exchange evidence

`sceneEvidence -Pscene=power-suit` plays four fixed open-ground exchanges; the
same cases render through `createSnapshots -Psnapshot=power-suit` at 1920x1080.
The retained observations are in `power-suit-exchange.json`. Both sides use
Service weapons and Regular/Steady profiles, with no integral systems, special
items or officers, so the first three cases isolate the armor package and force
size. All squads contain twelve marines and face two Legionary rifle squads.

| Player force | Defenders eliminated | Player survivors | End |
| --- | ---: | ---: | --- |
| One Legionary squad | 4 of 24 | 0 of 12 | Wiped out at 12.0 s |
| One XIV squad | 15 of 24 | 1 of 12 | Unresolved at 60.0 s |
| Two Legionary squads | 19 of 24 | 0 of 24 | Wiped out at 50.5 s |
| One XIV squad against marksmen | 2 of 24 | 0 of 12 | Wiped out at 8.3 s |

This supports a meaningful endurance step and preserves vulnerability to a
stronger weapon package. The marksman control changes the whole primary, not
penetration alone. One layout and seed, no cover, and no objective completion
cannot establish general two-squad equivalence; the two-squad case also uses
two starting groups. Scene verdicts verify that combat occurred, not that the
player won. Mission outcomes below remain the acceptance question.

The retained `power-suit-armor-comparison.png` and `power-suit-battle-hover.png`
show class, role, tier, and live protection at 1920x1080 and 100% UI scale.
They use existing infantry art and do not satisfy the final silhouette requirement.

The Specter layered review in `art-source/marine-modular-topdown/specter-v1/`
provides a separate body, helmet, boot, and shared weapon, with four casing
colors. Its retained 1920x1080 production-compositor preview shows weapon
clearance, independent head look, walking feet, and a 24px shoulder-unit sample.
This establishes a first art pass, not crowded-field or live campaign acceptance.

## Acceptance

- Compare one POWER_SUIT squad with two STANDARD squads under concentrated
  fire, matched weapons and visible issued quality, and identical objectives.
  Report casualties, armor breaks, completion and ammunition use. Include cover,
  open movement, and high-penetration opposition. The approximate two-squad
  capability target is a design question, not an asserted result.
- Show that advanced STANDARD recon, line and support sheets remain obtainable
  and useful after heavy templates are learned. Unavailable-class sheets must
  not advertise a refit or silently cross class during issue.
- Refit an existing named squad, deploy and settle it, save, reload, and inspect
  identities, career, kit, status, officer and squad membership. Wounded people
  retain billets; casualties never become anonymous cargo deductions.
- Confirm twelve-person formation and one named seat per wearer through ordinary
  lift. Equipment conversion must not create Mech Support or another roster.
- Author and review distinctive layered suit art against the visual specification
  in `power-suit-infantry.md`. Verify weapon clearance, poses, silhouette and
  crowded-field readability at 1920x1080 and 100% UI scale.

## Boundaries

No new cap, upkeep, repair economy, suit pilot lifecycle, mech hardpoints, thermal
system, or reduced formation size is implied. Capture necessary follow-ups as
bounded stories with observed failures and measurable questions. Unit tests and
concept art do not replace live persistence or field-balance acceptance.
