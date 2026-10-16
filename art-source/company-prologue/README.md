# Company prologue art

Start here for the campaign opening stills, Second Wind references and Jangala
arrival cinematic. These are authoring sources; only the packed JPEGs under
`mod/graphics/illustrations/company/` ship with the mod.

| Location | Purpose |
|---|---|
| [story-stills/](story-stills/) | Ten original story-frame masters with their exact generation prompts. Used by the campaign intro and yard UI. |
| [pack.ps1](pack.ps1) | Packs only `story-stills/*.master.png`: complete 3:2 frames, 1200x800 JPEG, quality 85, maximum 400 KiB each. |
| `second-wind-hull.reference.png`, `second-wind-design-v1/v2/v3.reference.png` and adjacent prompts | Original hull and design evolution. V3 is the accepted clean/medium geometry reference; earlier sheets remain useful reference art. |
| [second-wind-geometry/](second-wind-geometry/) | The single accepted clean-hull geometry source (V3 revision), renders and coordinate contract. Older V2 reference images are under `reference-history/v2/`; its obsolete model and builders were removed. |
| [second-wind-skins/](second-wind-skins/README.txt) | Weathering states, including the heavily damaged arrival reference. |
| [second-wind-cinematic/](second-wind-cinematic/README.md) | Current shot order, reusable ship master, sources and earlier technique studies. |

## Packing stills

Run `./art-source/company-prologue/pack.ps1` from the repository root to update
the runtime JPEGs. For a review without changing `mod/`, pass
`-OutputDirectory ./build/prologue-stills-review`. It uses the same encoder.
Do not include ship reference sheets or cinematic assets in this pack.

## What is retained

Keep native generated images, exact prompts, style/design references, editable
Blender sources, geometry/registration data, scene scripts and current review
outputs. Earlier scenes often supply geometry or materials to later ones; age
alone does not make a source disposable. The four neutral ship material tiles
are intentionally also packed/copied into the reusable full-surface package.

Raw frame sequences, caches and temporary previews belong in ignored `build/`
folders. Superseded encoded movies can be retired while keeping source art and
compact reference stills. Historical validation receipts describe their original
renders; they are not assertions that a retired movie remains in this checkout.
Rebuild or recover those movies before running an older media validator.

The 2026-10-16 cleanup moved story frames here from the folder root and isolated
the rejected orbital prototype. No image pixels or exact prompt text changed.
Historical prompts retain their original input names; resolve relocated inputs
through [cleanup-record.json](cleanup-record.json). That record also gives paths,
sizes, hashes and the pre-cleanup commit for 13 retired movies and an obsolete
staging list. Git history retains them; no repository history was rewritten.

The accepted geometry now has one stable directory: `second-wind-geometry/`.
V3 is its design revision, not a second competing model folder. The obsolete V2
model and build files were removed; only its reference images remain under
`reference-history/v2/`. The cleanup record maps historical prompt paths to the
current locations. The separately surfaced damaged cinematic ship remains in
`second-wind-cinematic/second-wind-full-surface/`.
