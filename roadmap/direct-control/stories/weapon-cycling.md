# Weapon cycling

> Two buttons, two groups; each button cycles what it fires.

Status: PLANNED — model agreed with the owner; HUD look to go on the picks page with the first build
Written: 2026-10-23

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
- **Q** cycles the left trigger and **C** the right (both free in direct
  control today). Number keys stay as direct jumps on the left trigger. The
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
- E and G stay as direct hotkeys for the shield and smoke.

## HUD

- The weapons panel already lists rows under an LMB and an RMB header. The
  header shows the pick and its key, for example `LMB · LINKED  [Q]`. The
  picked rows carry the wash and the rest dim.
- A Marine's weapons panel gains a row per direct-fire special under LMB, and
  the thrown items under RMB. The kit column on the doll keeps its icons.
- The header and row look goes to the owner on the picks page as rendered
  options before it ships.

## Plan

1. Trigger model: member lists and picks per carrier; Q/C input; the mech
   path, replacing keys 2-4's one-mount pointing with the cycle. Unit tests for
   cycle order, skipping, reset and LINKED.
2. HUD: headers and row states from the pick; picks-page question with
   rendered options; snapshot frames in `direct-control-ui`.
3. Marine direct-fire specials: a manual contract for the rocket launcher and
   the anti-materiel rifle on the left trigger.
4. Marine grenades: the manual throw on the right trigger.
5. Vehicles: wire the right trigger to a second weapon when a hull gains one;
   add data for crewed defensive mounts if one appears outside the
   point-defence service.

## Acceptance

- A Bulwark fires both arm chainguns on LINKED and one alone after a Q press.
  Its racks cycle on C.
- A Marine with a rocket launcher presses Q, fires one rocket at the cursor,
  presses Q again and is back on the rifle.
- A Marine with grenades and smoke cycles the right trigger between them.
- Nothing unpicked fires, except crewed defences, which keep intercepting.
- The noun doc's "Mouse buttons and weapon groups" describes the cycle, and
  this story is folded and deleted.
