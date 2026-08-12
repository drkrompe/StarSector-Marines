# Layered infantry runtime

Converted infantry render as a runtime composition instead of a directional
sprite sheet. Legacy live sheets remain available when any modular texture
fails to load, and remain the corpse representation after death.

## Anatomical unit

`1 sw` is the rendered shoulder width. At the reference scale it is 150 source
pixels; on screen it is the normal infantry render size. Every source sprite,
pivot, attachment, locomotion offset, and recoil distance resolves from `sw`, so
zoom and future body-width changes preserve the same anatomy.

## Authored ECS state

`LAYERED_ANIMATION` is presentation-only and contains:

- continuous body facing;
- repeating locomotion phase;
- weapon-pose phase;
- head look relative to the torso;
- weapon pose (`idle`, `aimed`, `firing`, `rocket aim`, `rocket fire`);
- flags for movement, muzzle flash, and over-shoulder weapon occlusion.
- independent body-family and head-family selectors.

`FacingSystem` authors these values at the tail of each simulation tick. The
renderer never derives them from targets, paths, or cooldowns.

## Composition and paint order

The ordinary order is feet, weapon, body, head, muzzle flash. Feet remain fully
occluded at rest and alternate a short tip reveal from the locomotion phase.
The weapon translates and rotates between a 45-degree carry and the aimed
right-shoulder registration; firing adds transform recoil and a transient flash.

Rocket fire uses feet, body, launcher, head, firing FX. This is a pose-specific
occlusion change: the launcher crosses above the right shoulder while the helmet
still paints above the launcher and retains independent look rotation.

## Armor families and spawn defaults

The cache loads armorless fatigues/bare head, charcoal, field-blue scout, red elite,
outlaw, army green, and militia families. Body and head selectors are separate, so a head can be mixed
with any body at runtime. Unit type supplies only a default:

- `MARINE`: charcoal armor.
- `MARINE_BLUE`: dark field-blue scout armor.
- `MARINE_RED`: rugged outlaw armor.
- `MILITIA`: improvised militia carrier and helmet.
- Pulse rifle loadouts use the laser-gun layer; SMG and DMR loadouts use their
  own compact and precision layers; generic combatants use the rifle layer; an
  active secondary aim uses the rocket-launcher layer.

Weapon equipment grades fall back to their family's registered sprite and animation.
Authored exact-grade overrides currently demonstrate the ends of the scale: a battered
Surplus rifle and precision Masterwork DMR. They keep the same pivots, so grade changes
combat statistics and surface read without changing facing or firing behavior.

## Campaign armory bridge

`MarineRosterScript` persists an armory and rank-and-file roster in the same xstream
graph as captains. Soldiers have stable ids, aptitude, earned XP, status, primary/grade,
secondary and armor allocations. Mission launch freezes those allocations into shuttle
seats after the scenario authors roles/objectives. The campaign soldier id survives on
the battle entity and corpse, allowing results to award survivor XP and mark real losses KIA.

Victories award one shared `masterwork parts & materials` fabrication resource and advance
recipe milestones. Recipes unlock permanently; printing creates finite inventory; allocation
cannot exceed inventory. The Masterwork DMR recipe requires five victories including at least
one high-risk operation.

Adding an armor family is an asset-cache mapping. Adding a weapon animation is a
new authored pose/transform profile; it should not introduce directional sheets
or combat-state reads in the renderer.
