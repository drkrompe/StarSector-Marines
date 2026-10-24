# Weapon cycling

> Two buttons, two groups; each button cycles what it fires.

Status: IN PROGRESS — steps 1-5 shipped (keycap header; Marine left-button specials and the flamer; Marine right button with grenades; vehicles' right button through the shared sorter); held-weapon art question open
Written: 2026-10-23
Updated: 2026-10-24 — the launcher and the anti-materiel rifle walk in carry clips of their own; which carry each uses is with the owner

Read `direct-control-nouns.md` first, in particular "Mouse buttons and weapon
groups". This story extends that model, so the groups it describes stay true.

## Goal

A controlled body often carries more than two things that shoot. The left and
right buttons are the two **delegations** (owner, 2026-10-23): each holds a
group, and each gets its own **sub-cycle** through that group, so the player
chooses what the button fires without a key per weapon.

## Model

- A **trigger** (left or right button) has an ordered list of **members** and a
  **pick** into it. Pressing the trigger fires the pick.
- A mech trigger's list starts with **LINKED**: every member of the group at
  once, each under its own arc, cooldown, ammunition, flux and burst gates. A
  Bulwark's two arm chainguns fire together on LINKED, or one at a time
  (owner, 2026-10-23). After LINKED come the members one by one. LINKED is the
  default pick.
- **Q** cycles the left trigger and **E** the right, on every carrier (owner,
  2026-10-23); the shield moved from E to C, and F stays the warship's low
  pass. Number keys stay as direct jumps on the left trigger. The
  mouse wheel stays on zoom.
- **A member that isn't picked holds fire** (owner, 2026-10-23). The exception
  is a **crewed defence**, a mount with its own gunner: a tank's active
  protection, point-defence interceptors and the like. These never join a
  cycle and keep firing autonomously while the body is under control. Today's
  tank APS is a point-defence structure already, so it needs nothing new; a
  future defensive mount declares itself crewed in data rather than being
  inferred.
- Cycling skips a destroyed or empty hardpoint. A member whose ammunition is
  exhausted stays in the cycle. Changing the pick cancels the trigger's
  queued manual rounds and releases held fire, as selection does now. The pick
  belongs to the control session, survives pause and resets on takeover or
  handback.

### Per carrier

| Carrier | Left trigger | Right trigger |
| --- | --- | --- |
| Mech | Guns, cannons, lasers: LINKED, then each | Missile and rocket racks: LINKED, then each |
| Vehicle | The main turret | A second weapon where the hull has one (a coax gun, smoke dischargers); inert otherwise |
| Marine | Weapons: the primary, then each **direct-fire special** (rocket launcher, anti-materiel rifle) | Thrown and utility items: smoke, grenades, the integral shield |

- **The Marine's left trigger toggles into a secondary weapon** (owner,
  2026-10-23). The player switches the left button from the rifle to the rocket
  launcher, aims, and fires it like a gun. No LINKED: one pair of hands.
- A direct-fire special joins the cycle only with a **manual contract**: it is
  readied when picked (its aim time is a wind-up), fires on the press from the
  cursor point, and spends its own ammunition. When it runs out, the pick stays
  on it, shown empty; it does not fall back to the rifle on its own.
- **Grenades are special** (owner, 2026-10-23). They are a throw, not a gun:
  they belong on the right trigger beside smoke, with an arcing throw to a
  range-clamped point under the smoke throw's committed-channel rules.
- C and G stay as direct hotkeys for the shield and smoke.

## HUD

- The weapons panel already lists rows under an LMB and an RMB header. The
  header shows the pick and its key, for example `LMB · LINKED  [Q]`. The
  picked rows carry the wash and the rest dim.
- A Marine's weapons panel gains a row per direct-fire special under LMB, and
  the thrown items under RMB. The kit column on the doll keeps its icons.
- The header and row look goes to the owner on the picks page as rendered
  options before it ships.

## Plan

1. ~~Trigger model~~ (shipped): `MechFireGroups` lists and `MechTriggerPicks`,
   Q/E in `BattleDirectControlInput`, number keys as left-trigger jumps; Marines
   and vehicles have single-member lists.
2. ~~HUD~~ (shipped): trigger headers and row wash/dim from the picks; frames
   `pick-linked` and `pick-one-mount` in `direct-control-ui`. The owner chose
   the keycap header, `LMB  LINKED  [Q]` (picks page, 2026-10-23).
3. ~~Marine direct-fire specials~~ (shipped): `MarineLeftTrigger` in the
   session, the special's own point-fire path, the reticle and HUD rows, and
   the held pose from the existing aim and firing clips, and a walking carry
   clip per special (see the art note below); frames in `marine-left-special`.
   The flamer joined as a held stream (`FlameJet`, a fuel tank of cells on its
   row); frames `flamer-walking`, `flamer-streaming` and the `flamer-sweep` GIF in `marine-left-special`
   and `active-marine-flamer` in `direct-control-ui`.
4. ~~Marine grenades~~ (shipped): the right button's list (smoke, a hand grenade,
   the shield) on E, and the grenade's committed manual throw; the flamer is a
   left-button weapon, not a throw. Frames in `marine-right-button`.
5. ~~Vehicles~~ (shipped): one carrier-neutral sorter (`FireGroups`) for mechs and
   hulls; a hull's secondary mounts (`VehicleMount`, crewed in data) sit on the right
   button and cycle on E. No shipped hull carries one yet; frame
   `active-vehicle-two-guns` in `direct-control-ui`.

## Art note (step 3)

The flamer's charge flies as a flame jet (a puff cropped from the fire sheet by
`art-source/fx/flamer-jet/export.py`, a fire-and-glow trail, and a nozzle
emitter spraying fire forward for the length of each charge). Its carried art is
its own (`art-source/flamer/`): a fuel pack of twin tanks and a pressure bottle
worn on the back as the carrier layer, always visible, and a wand drawn as the
item's handset layer, corded to the pack by a stretched hose the way the comms
radio's handset is corded to its pack. Picked, the Marine holds the wand at the
hip in place of the primary through the `flamer` variant's `aiming` and
`firing` clips (`usePose` `braced-rifle`, so the poses are the held-special
ones); the wand is drawn under the helmet (`underHead`). The armoury icon is the
kit in profile. Frames `flamer-*-close` in `marine-left-special` show it at a
close framing.

The launcher and the anti-materiel rifle use their existing sprites and the
`aiming` and `firing` clips of the `rocket` and `anti-materiel` variants of
`unit-layer-layouts`. Each also has a **walking carry**, named by its item's
`layerClips.walking`: a looping clip on the stride whose `special` layer places
the item in the hands (whatever the item is attached to) while the arms and
shoulders move with the step. Readied and walking, the Marine carries it; while
readying or standing it is in the aim clip, and after a round in the firing clip.
Going between the carry and the aim takes 0.2 s each way (`FLAG_SPECIAL_CARRIED`,
whose phase is how far it is carried), and a round snaps it to the aim.

Open, with the owner on the picks page (2026-10-24): the launcher on the shoulder
(shipped, recommended) or at port, and the rifle at low ready (shipped,
recommended) or slung across the back. All four clips are in the variants; the
`carry-option-*` frames in `marine-left-special` draw each, and the losing clips
and their frames go once the owner picks.

## Acceptance

- A Bulwark fires both arm chainguns on LINKED and one alone after a Q press.
  Its racks cycle on E.
- A Marine with a rocket launcher presses Q, fires one rocket at the cursor,
  presses Q again and is back on the rifle.
- A Marine with a throw (smoke or a grenade) and a shield cycles the right trigger
  between them (shipped). A Marine with smoke and a frag on the belt, the second special
  slot, lists both throws and the shield, and either throw is used from the slot it rides
  in (shipped; frame `active-marine-smoke-and-grenade` in `direct-control-ui`).
- Nothing unpicked fires, except crewed defences, which keep intercepting.
- The noun doc's "Mouse buttons and weapon groups" describes the cycle, and
  this story is folded and deleted.
