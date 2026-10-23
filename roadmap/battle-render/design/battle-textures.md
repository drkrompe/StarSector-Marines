# Battle textures: what a battle loads, who owns it, and when it goes

Status: ACTIVE — the ledger, the load queue and the loading screen are built; the per-battle
texture set and the release at battle exit land once `texture-fit.md`'s loader changes are on
`main`.

Written: 2026-10-23

The canonical model of the render pipeline is `battle-render-nouns.md`; this doc owns the
texture lifecycle under it: which textures a battle owns, which it shares, and how they are
loaded and released. `texture-fit.md` owns how big each shipped image is; this doc owns how
long it stays on the card.

## Why

The mod loads its art lazily through `SettingsAPI.loadTexture` and never unloads any of it, so
everything a battle draws stays resident until the game closes. The shipped PNGs are about
160 MB on disk and about 590 MB decoded, and the card holds more than that: the game pads every
texture up to power-of-two sides, which puts the whole set near **1.1 GB of video memory**
(`tools`-free measurement: image headers, `nextPow2(w) * nextPow2(h) * 4`). A battle loads most
of it whether it uses it or not — every vehicle line, every infantry family, every mech livery,
every structure in the turret catalog, every tileset in the registry — and the three atlases it
composites on top (`GroundAtlas`, `UnitAtlas`, `DecorationAtlas`) hold their own copies.
Starsector is a heavily modded game, and that is video memory every other mod is short of.

## Vocabulary

- A **texture ledger** (`TextureLedger`) records, for every texture the mod loaded, which
  leases hold it. It is the only thing that unloads a mod texture.
- A **texture lease** (`TextureLease`) is one owner's claim on the textures it draws. A
  battle holds one for its lifetime and releases it when it ends. The **session lease** is
  never released: it is what a screen that does not manage its textures loads under, which
  keeps those textures exactly as resident as they always were.
- A texture is **foreign** when it was already on the card the first time the ledger saw it:
  vanilla art the game preloaded, another mod's, or a load that went round the ledger. The
  ledger never unloads a foreign texture, because it cannot know who else draws it.
- The **battle texture set** is what one battle actually places and spawns: its map's
  tilesets and props, the armor families of the bodies on its roster, the two sides' mech
  liveries, its vehicle lines and airframes, its structures and their projectiles, and what
  its mission can bring in later (reinforcements, command powers). It is derived from the
  battle, never from the catalogs.
- The **shared set** is what other screens draw: the company deck (HQ command deck, barracks,
  ship view, Mech Lab scenes), the Armory's soldier previews, retained-UI images and fonts,
  planets. Those screens keep their own textures on the session lease.
- The **load queue** (`TextureLoadQueue`) is the battle texture set cut into steps and run a
  frame's budget at a time, so the loading screen keeps drawing while the GL thread uploads.
- The **loading screen** (`BattleLoadingOverlay`) is what a battle shows while its queue runs:
  the operation's name, where it is fought, a bar and a line saying what is loading.

## Ownership and flow

1. **Start.** When `BattleScreen` attaches a battle it has not loaded, it opens a new lease,
   derives the battle texture set from the simulation and builds the load queue from it. Until
   the queue is done, and until the loading screen has been up for its minimum time, the
   screen draws only the loading screen and the simulation does not advance.
2. **Play.** Every load during the battle goes through the battle's lease — including the
   lazy ones (`BattleSprites.spriteByPath` for fort buildings, hull art, drones), and
   including anything the set missed. A miss is loaded on first draw, as it always was, and in
   the dev build it is logged as an unplanned load, which is how the set's derivation is kept
   honest. A miss is a hitch, never a blank.
3. **End.** On every exit — victory and defeat through Results, retreat (a defeat), a dialog
   dismissed mid-fight — the screen detaches, disposes the atlases and other GL resources it
   already disposed, and releases the lease. The ledger unloads what no other lease holds and
   the dev build logs how many textures went and how many megabytes were freed. The screen's
   `BattleSprites` is discarded with the lease, so no sprite that refers to a deleted texture
   survives into the next battle.
4. **Again.** A second battle in the same session opens a fresh lease and loads afresh. A
   texture the first battle released is a new GL texture in the second; one a shared screen
   held throughout is the same one.

## Standing laws

1. **Only the ledger unloads a mod texture, and only when no lease holds it.** The game keeps
   one texture per path for the whole process, so two screens that load one image share one
   GL texture and `unloadTexture` takes it from both. A screen finished with a texture gives
   it back; it never unloads it.
2. **Unloading deletes the GL texture.** `SettingsAPI.unloadTexture` only drops the game's
   references to the texture and never calls `glDeleteTextures` (`TextureLoader` in the game's
   own code), so on its own it frees no video memory at all. The ledger deletes the id itself,
   then lets the game forget the path.
3. **A sprite does not outlive its lease.** A `SpriteAPI` holds the texture it was made with,
   so one kept past an unload draws whatever the driver later puts at that id — nothing, or a
   different image. That failure is a blank or wrong draw that no count and no unit test can
   see, which is why the owner that releases a lease drops every sprite it handed out, and why
   the acceptance for this lifecycle is a picture on a real driver (`Evidence`).
4. **Every mod load goes through the ledger.** A load that goes round it makes the texture
   invisible to the reference count: if a battle then acquires the same path, the ledger
   believes the battle is its only holder. `ModTextures` is the one way the mod loads its
   textures (`texture-fit.md`), and it loads through the ledger, on the session lease unless
   told otherwise.
5. **The set is derived from the battle.** What a battle loads is what its map places, its
   roster wears and its mission can bring, never every family the catalogs ship. A family
   that only some battles use costs only those battles.
6. **Loading never blocks a frame for long.** A battle's uploads run as queue steps under a
   per-frame budget behind the loading screen; a single step larger than the budget runs alone
   in its frame, so the queue always finishes.

## Inventory

Measured 2026-10-23 from the shipped image headers. *Card* is what the GPU holds after the
game's power-of-two padding; the dev build's lease log reports what a given battle actually
holds, which is the number to quote for a battle.

| Art | Loaded by | When | Shared with | Card |
|---|---|---|---|--:|
| Convoy and armor vehicle sheets, 14 lines (`graphics/battle/vehicles`) | `BattleSprites.ensureConvoySprites` | every battle attach, all lines | combat bridge backdrop | 212 MB |
| Structures and turrets (`graphics/structures`) | `ensureTurretSprites` (whole `TurretCatalogRegistry`), `spriteByPath` | every attach; lazy on draw | combat bridge | 202 MB |
| Tilesets (`graphics/tilesets`) | `SheetTexture` via `ensureTileSheet` and the other sheet `ensure*`, every sheet in `TileRegistry` | every attach | company deck (tile, road, doodad sheets), combat bridge | 190 MB |
| Marine infantry, every armor family (`marine-modular-topdown`) | `ensureLayeredUnitSprites` | every attach, all families | company deck, Armory previews | 161 MB |
| Doodad and parked-vehicle sheets (`graphics/doodads`) | `ensureDoodadSheet` | every attach | company deck | 56 MB |
| Ship-crew and colonist families | `ensureLayeredUnitSprites` | every attach | company deck | 61 MB |
| Fort buildings (`graphics/fort`) | `spriteByPath` from `FortBuildingRenderSystem` | lazy, on draw | — | 30 MB |
| Grounded warship hulls (`graphics/battle/ships`) | `spriteByPath` from `GroundHullArt` | lazy | — | 24 MB |
| Battle FX (`graphics/battle/fx`), smoke, fire, satchel, decals | `ensureEngineFxSprites`, `ensureSmokeSprites`, `ensureSatchelSprite`, `ensureDecalSheet`, `ImpactFx.ensureSprites` | every attach | combat bridge | 21 MB |
| Mech parts and every livery (`mech-modular-topdown`) | `ensureLayeredMechSprites` | every attach, all liveries | company deck | 17 MB |
| Rav Bloom rigs, xeno families, blood pools | `ensureLayeredUnitSprites` | every attach | — | 15 MB |
| Aircraft, drones, objective icons | `ensureAirframeSprites`, `ensureDrone*`, `ensureObjectiveIcons` | every attach, all airframes | company deck (airframes) | 6 MB |
| Mech Lab welding art (`graphics/battle/mech-lab`) | company deck scenes | deck scenes | — | 16 MB |
| Armory icons (`graphics/ui/armory`), HQ deck UI, portraits | `UiSpriteCache`, `ArmoryPreviewAssets` | the screens that show them | all retained-UI screens | 61 MB |
| Fonts | `BitmapFont` | first use | every screen | small |
| Planets | `PlanetSphereDrawable`, `PlanetBillboardDrawable` | planet intel panel | — | vanilla, foreign |

On top of those, per battle: the **ground atlas**, **unit atlas** and **decoration atlas**
(34–55 MB together on the measured maps), the ground relief targets, the decal accumulator,
the fog field, creep, scorch, cliff art and night-lighting targets. Those are already GL
resources the battle generates and deletes itself at detach; they are not on the ledger and
need not be, since nothing else can hold them.

Owners of `BattleSprites` instances, for reference when deciding which lease a load is under:
the battle screen (also used for the Armory range trial), the company deck (one per dialog,
alive while the dialog is open), the Armory previews, and the combat bridge backdrop. Only the
battle screen's instance loads under a battle lease; the others stay on the session lease.

## Loading screen

The battle shows a card in the middle of a dark screen with the operation's name, where it is
fought, a bar, and a line naming what is loading (the recommended options on the picks page;
the owner's choice of look, progress line and minimum display time replaces them). The screen
is a retained-UI component (`battle-loading-overlay.mlx`) so the `battle-loading` snapshot
suite renders the same markup the game draws.

## Evidence

`shaderEvidence` carries the lifecycle check, because the failure it guards is a picture: it
loads a battle's texture set onto a real driver through a stand-in for the game's texture
store that behaves as the game's does (it pads to power-of-two and its unload deletes
nothing), draws, releases, asserts with `glIsTexture` that every battle-only texture is gone
and every shared one is not, then loads the battle again and asserts the second frame is the
first frame's pixels. The dev build logs megabytes held by the ledger before a battle, after
its queue finishes, and after its release.

## Boundaries

- The company deck lives for the whole ops dialog and holds infantry, mechs and the common
  tile sheets on the session lease, so while the dialog is open those stay resident whether a
  battle runs or not. Giving the deck a lease released when the dialog closes is the next
  step for that share of the card, and a separate change.
- Shrinking each image is `texture-fit.md`'s; this doc only decides how long it is resident.
