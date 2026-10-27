Starsector Marines 0.1.0-alpha
==============================

Run a mercenary marine company inside Starsector: named officers and squads,
mechs and vehicles, contracts from local brokers and patrons, and real-time
ground operations fought on generated colony maps from the planet dialog.

This is an ALPHA. Expect rough edges, balance swings and missing polish.


INSTALL
-------

Requirements
  - Starsector 0.98a-RC8.
  - No other mods are required. LazyLib, MagicLib and GraphicsLib are not
    needed.
  - Optional: Nexerelin. With it enabled, the mod integrates with Nexerelin's
    ground invasions and adds a custom start. Without it, everything else works.

Steps
  1. Close Starsector.
  2. Unzip the download. It contains one folder, "StarsectorMarines".
  3. Move that folder into <Starsector install>/mods/ so that the path
     <Starsector install>/mods/StarsectorMarines/mod_info.json exists.
     Do not nest it (mods/StarsectorMarines/StarsectorMarines/ will not load).
  4. Start Starsector, open Mods in the launcher, enable "Starsector Marines".
  5. Start a NEW campaign. Adding the mod to an existing save is not
     supported in this alpha.

Updating
  Delete the old mods/StarsectorMarines folder first, then install the new
  one. Saves from one alpha version may not load in the next.

Memory
  The mod ships large battle art. If the game runs out of memory, raise the
  heap in starsector-core/vmparams (for example -Xms4g -Xmx4g) as for any
  large mod pack.

Removing
  Delete mods/StarsectorMarines. Saves that used the mod will not load
  without it.


Getting started
---------------
Your company starts with a captain and an empty reserve. Visit Zoey Venn on
Jangala for your first work; the Company HQ screen (from the Company ability
on your ability bar) plots a course to her office.

Report bugs on the forum thread with starsector-core/starsector.log attached.

See CHANGELOG.txt for what is in this version and known issues, and CREDITS
for third-party assets.
