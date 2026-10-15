# Ground missile AA art family

First art passes for Locust, Swarmer and Proximity Charge ground turrets on the
shared raised tower. These are art-review assets; runtime weapon definitions,
world footprint, firing origins and destruction states remain unauthored.

| Head | Art approach | Master | Exact built-in ImageGen prompt |
| --- | --- | --- | --- |
| [Locust](../locust-aa/README.md) | Approved elongated microphone head, clipped side gimbals and circular floor, reskinned from a Blender guide | [Master](../locust-aa/locust-mount.master.png) | [Prompt](../locust-aa/locust-mount.prompt.txt) |
| [Swarmer](../swarmer-aa/README.md) | First direct up-res of the four-tube red vanilla launcher | [Master](../swarmer-aa/mount.master.png) | [Prompt](../swarmer-aa/mount.prompt.txt) |
| [Proximity Charge](../proximity-aa/README.md) | First direct up-res of the split olive housing and purple center | [Master](../proximity-aa/mount.master.png) | [Prompt](../proximity-aa/mount.prompt.txt) |

## Review and export

- [Family comparison](family-review.png): all three heads on the unchanged
  Locust tower, with large and 128-pixel canvas studies.
- [Source comparison](upres-review.png): each new head beside its native sprite,
  with a tower assembly below. Both review sheets are 1920 x 1080.
- Each new head directory retains an unmodified 1254 x 1254 transparent
  `mount.master.png`, a 512 x 512 RGBA `layers/mount.png`, a transparent
  512-square `composite.png`, and `export-metadata.json`.
- The shared stationary tower remains
  [`../locust-aa/ground-base.master.png`](../locust-aa/ground-base.master.png).
  The family exporter reads the approved Locust files without modifying them.

From the repository root in PowerShell:

```powershell
./art-source/defences/missile-aa/export.ps1
```

The exporter preserves complete generated frames and alpha. It downsamples,
assembles and labels previews; it does not trim, recenter, repaint or recolor
the masters. It checks square masters, nonempty visible bounds, real transparent
space and clear export borders. Visible bounds use alpha >= 32; borders reject
alpha >= 8 while retaining faint generated alpha residue. It also reproduces
the 768-square nearest-neighbor source enlargements used for ImageGen.

`registration.json` owns preview placement in 512-canvas coordinates. The
tower uses its established bearing at (256, 242). Both new heads currently use
the provisional full-frame center (256, 256), with scale 1. These placements
give a useful family comparison, but are not authored mechanical yaw axes or
runtime firing origins. Review registration and traverse clearance when the
weapons are integrated; do not infer a pivot from arbitrary alpha bounds.
Locust keeps the circular-floor pivot from its existing export metadata.

## Generation and provenance

The Swarmer and Proximity passes used the built-in ImageGen tool, one call per
head, with three references in this order:

1. The unmodified native turret sprite retained in that head's `sources/`.
2. `sources/vanilla-enlarged.png`, the same full frame enlarged for legibility.
3. The approved Locust master, used only for surface rendering and detail quality.

The broad shapes, component arrangement and colors come from vanilla. Generated
seams, fasteners, wear and internal channel details are interpretations, not
recovered high-resolution artwork. The new heads did not use Blender guides;
their masters may differ from the native sprites in exact proportions and
canvas occupancy. Forward remains image up.

References are from the read-only Starsector 0.98a-RC8 install at
`C:/Program Files (x86)/Fractal Softworks/Starsector/starsector-core`.
Vanilla art belongs to Fractal Softworks. Native PNG copies are unmodified;
retained `.wpn` specifications only normalize trailing whitespace and line
endings. Each vanilla weapon declares one turret image and an alternative
hardpoint image, not two composited layers. The tower is separately authored
project art.
