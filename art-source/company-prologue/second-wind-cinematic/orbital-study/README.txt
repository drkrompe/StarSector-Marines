SECOND WIND - REJECTED ORBITAL COMPOSITING STUDY

Status: compositing technique prototype; physical staging rejected in review.
The hangar opening, ship attitude and approach path do not form a coherent
docking maneuver. Reference layers and stills remain; the superseded movie is retired.
Historical movie measurements below describe the original export, recoverable
through ../../cleanup-record.json. The current sequence is indexed in ../README.md. Plan the
storyboard, then resolve ship/berth clearance, cameras and travel in a shared
Blender blockout before producing the next detailed scene layers.
../storyboard-v2.txt records the expanded surface-arrival proposal: a crew-window
entry/cloud reveal, landscape-to-port approach and exterior landing. The roughly
65-second timing and existing intro voice cues remain a discussion draft. Lore
sources, campaign sequence and unresolved physical details are recorded there.

A 12-second, silent, fixed-angle 2.5D compositing study. The damaged Second Wind
coasts toward an industrial berth above Jangala. This is an arrival establishing
shot, before the campaign's planetary-yard berthing conversion. The orbital
architecture is an art interpretation, not a change to campaign geography.

The ship uses the current heavy-arrival skin: nearly a decade of small asteroid
impacts, overlapping craters, crushed outer plates and emergency patchwork. Its
position, scale and lighting animate; the silhouette, pod, turrets and engine
locations stay fixed. This is a layered shot, not a freely orbiting 3D animation.

Contents
- review/arrival-cinematic.mp4: retired historical movie, H.264, 1920x1080, 24 fps,
  288 frames, 12 seconds, yuv420p, faststart. Letterboxed to 2.39:1. No audio.
- review/poster.jpg and review/contact-sheet.jpg: compact review stills.
- review/verification.json: measured export properties, sizes and source hashes.
- ship.png: transparent ship layer, derived by built-in ImageGen from
  ../../second-wind-skins/arrival-heavy.reference.png.
- background.png: industrial approach berth and Jangala plate.
- foreground.png: transparent near gantry for stronger parallax.
- *.prompt.txt: exact built-in ImageGen prompts for those layers.
- composition.json: timing, layer positions, scale, color grade and encoding.
- build_cinematic.py: editable compositor using Pillow, numpy and FFmpeg.

Source resolution and size
All three generated layers are 1672x941. The ship is reduced to 900-950 pixels
wide in the 1080p frame; the dock plate is modestly enlarged for delivery.
The source layers total about 5.0 MiB and are retained losslessly for editing.
The movie uses CRF 20 H.264; exact delivered bytes are in verification.json.
No frame sequence is checked in. These files remain under art-source and are
excluded from the runtime JPEG packer and mod deployment.

Composition
The ship drifts left with a small push-in; the dock moves slowly behind it and
the near gantry moves faster. Fractional translations use premultiplied alpha
to avoid dark fringes. The ship grade lowers the bright reference illumination,
preserves crater blacks and adds a restrained warm bounce toward the dock.
There is no flame, smoke or venting in vacuum. No crew are shown working outside.
Fine generated lettering and surface details remain conceptual reference art.

Rebuild from the repository root with Python, Pillow, numpy and FFmpeg on PATH:

python art-source/company-prologue/second-wind-cinematic/orbital-study/build_cinematic.py --preview
python art-source/company-prologue/second-wind-cinematic/orbital-study/build_cinematic.py

Use --ffmpeg C:/path/to/ffmpeg.exe if it is not on PATH. Config paths are relative
to composition.json, so the script also works from a different working directory.
Generated files go into ignored build/. Rebuilding does not rewrite source art. Retired movie paths are ignored;
promote only useful new review outputs deliberately.
For a new accepted edit, copy build/arrival-cinematic.mp4 into review/, refresh
the poster/contact sheet, and record the new output metadata and source hashes.
