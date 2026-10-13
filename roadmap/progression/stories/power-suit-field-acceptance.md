# Power-suit field acceptance

Status: IN PROGRESS — bounded exchange evidence exists; mission balance, live persistence, and final art remain unverified.
Written: 2026-10-13
Updated: 2026-10-13 — playable pairs support campaign testing; Vesper Reach introduction authored, runtime arc and later military-source gate remain open.

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

The Specter layered review in `art-source/marine-modular-topdown/specter-v2/`
provides a torso with joint backing, independent left/right shoulder shells,
helmet, boot, and shared weapon, with four casing colors. Its retained 1920x1080
production-compositor preview shows alternating shoulder stride, recoil,
anti-materiel bracing, launcher overlap, independent head look, walking feet,
and a 24px shoulder-unit sample.
This establishes a first art pass, not crowded-field or live campaign acceptance.

The retained `specter-battle-normal.mp4` and `specter-battle-closer.mp4` show
twelve Specters, twelve Legionaries, and one Hound walking into a finite armed
picket through production simulation. The fifteen-second controlled replay uses
ordinary catalog equipment, including the Specter integral system. All 25
friendly actors move before the first shot at 5.57 seconds, and all three
groups fire. `specter-battle.json` retains the observations.

Both views are rendered at 1920x1080 and 100% scale from the same replay.
The normal view uses the live camera's cover fit at zoom 1 on a 112x64 map,
the Relief map size: 17.14 pixels per cell. The closer view adds four wheel
notches (2.0736x); it does not enlarge individual actors. The map is a controlled
lane, not the Relief mission. Java2D drains production world commands with
body shadows and shots, without battle HUD, selection markers, fog, or GL
terrain relief. This makes silhouette and gait review reproducible, while
crowded mission readability and live-host acceptance remain open.

The retained `pod-battle-normal.mp4`, `pod-battle-closer.mp4` and
`pod-battle-detail.mp4` use the same native-resolution contract for Arbalest's
olive layered shell beside regular marines and a Hound. Its shoulder-mounted
missiles originate at the authored pod muzzle. The detail clip repeats one
second of a real launch five times; the wider clips show the twelve-second
exchange and the squad's staggered salvos.

## Acceptance

For the current development build, Intel > Marines Debug > Power-suit test
technology explicitly learns either the two recovered templates or the two
military templates. This is permanent test acquisition, not a campaign reward;
it does not refit named soldiers. Fit through the ordinary Armory and use a real
campaign operation when checking settlement and live save/reload. The
Salvage-assisted Section issues eight regular billets, two assault suits and
two support suits. Full suit sheets remain available when their class is owned.

`commanderEvidence -Pmission=power-suit-campaign` checks a detached named mixed
section through ordinary equipment issue, ferry lift, combat, personnel
writeback and Java roster serialization. Its report separately names the live
Starsector save and host settlement receipt that it cannot verify. It is not
permission to call the full live loop accepted.

The narrative unlock remains open: finish Shepherd departure/fleet handover,
then implement the five-chapter Vesper Reach expedition in `a-small-war.md`.
Its recovered frames and pirate sidegrade precede a later military procurement
arc in the intended progression. `storySource: post-shepherd-power-suits` reserves
ownership of the recovered rewards and keeps them out of random acquisition;
it does not create a mission or prove that its reward has shipped. Existing
military acquisitions are retained for compatibility. Their generic sources
currently permit earlier access; a strict introduction order needs the later
procurement route and shared acquisition gate as well as this recovered-suit arc.

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
