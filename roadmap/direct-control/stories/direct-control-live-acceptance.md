# Direct control live acceptance

Status: LIVE ACCEPTANCE

Written: 2026-09-23

Updated: 2026-09-29 — verify stamina sprint, weapon lowering, and compact equipment readouts.

Read `direct-control-nouns.md` first. Begin after the Marine slice; repeat
carrier-specific checks when Mech and vehicle adapters ship.

## Goal

Decide whether direct control is readable, fair, and useful in a real battle,
including its transition back to autonomous play.

## Acceptance pass

- Select infantry, a Mech lance, and an APC at supported UI scales. The control
  button must remain visible and clickable outside the selected-unit panels.
- Enter control with strategic panels or retreat confirmation open. Only the
  compact action HUD and world status/crosshair remain; firing across
  the old panel bounds must work. Pause, resume, and exit through the separate
  top-right buttons or exit key; All remains beside the Mech weapon groups.
  The strategic view must return with current information and the same selection.
- Sweep the cursor near and far from the controlled body, across the dead zone,
  and onto UI chrome. Look-ahead should ease without drift or losing the body.
  Check both camera modes at the closest wheel zoom, including map edges and
  transitions into and out of control; aim and the crosshair must agree. The
  thin HUD must leave the body visible, docking at the top when map clamping
  places it near the bottom edge.
- Sweep the cursor during a Bulwark burst, including behind the hips and while
  a barrel is blocked. The torso must follow current input within its physical
  limits, without an old burst pulling aim back or crossing the rear blind wedge.

- Compare the strip's color-only armor and health or structure lines with each
  controlled body before and after damage. Armor sits above health and empties
  independently even with high health; armorless bodies have no armor fill.
  Identity, durability labels, and numeric capacity readouts must be absent.
- Check rifle, Mech, and APC weapon cards against real firing: authored bursts
  versus simultaneous volleys, finite Mech trigger packs versus APC rounds,
  and current cooldown/burst state. Loaded must not imply that an unaimed or
  structurally blocked barrel can fire. Indirect mounts remain AI-only.
- Select each direct Mech hardpoint by card and number key, including an empty
  rack, then return to all direct mounts. A switch during a burst must stop the
  old queued fire and require a new press without refunding ammunition or
  clearing cooldowns. Pause retains the choice; exit and re-entry reset it.
- Control a shield-and-smoke Marine. Only equipped abilities appear beside the
  primary, with no extra HUD height. Press E once to raise the shield, strafe
  while sweeping aim, and verify the world arc matches protected bearings.
  Break the soak pool and check the card's break state and real cooldown.
- Press G over the battlefield. The throw commits the cursor point and stops
  movement and primary fire until the channel ends. Move the cursor, pause,
  and hand back before release: the point must stay fixed, pause must freeze
  progress, and handback must finish exactly one throw at one canister cost.
  Hold E/G through pause or an unavailable state: no use may start later without
  another press. Check depleted smoke and the absence of automatic squad smoke
  throws by the controlled carrier.
- Hold left and right Shift separately and together while walking a Marine.
  Sprint should provide a readable speed increase and lower the primary;
  releasing one held Shift must leave the other active. Check diagonal speed,
  walls and sliding, shield facing, and a smoke throw while Shift remains held.
  Fully blocked movement must not drain stamina, and released primary bursts
  must not resume from a backlog. Exhaust the bar and verify the recovery
  threshold and release requirement. Pause, exit, switch Marines, and re-enter:
  resource state must persist and pause must not refill it. Compare the Shift
  card with actual movement at supported scales, including a Marine with both
  shield and smoke; Mechs and APCs must have no sprint card or speed increase.

- Use each eligible body under contact, behind cover, and near a friendly
  firing lane. Verify cursor direction, shot path and impact, moving fire,
  burst rhythm, sound, and zoom/follow behavior by eye.
- Walk a Marine and a Mech into a wall, along it, through a doorway, and
  toward a blocked diagonal corner. Drive and turn the APC beside the same
  obstacles. No sprite or collision body may pass through them, and opened
  doors must be usable immediately.
- Enter and exit repeatedly while the squad/lance or vehicle has a live
  mission. Observe continuing allied work, mission progress, and an immediate
  handback without a stuck role or stale path.
- Pause, change focus, lose the body, open UI chrome, and leave the battle
  while keys or trigger are held. No stale input may survive.
- Compare zero-input outcome and resource use against the same battle without
  takeover. Tune spread, speed, camera, and any future pacing from the
  observed play rather than granting invisible damage or target knowledge.

Record the resulting control feel and any balance decision in the canonical
noun doc when this story ships; keep raw run notes and traces outside it.
