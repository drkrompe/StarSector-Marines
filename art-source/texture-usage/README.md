# Texture usage record

`usage.json` is the measured input to the release and deploy builds' texture
fit (`roadmap/release/design/texture-fit.md`). For each image it holds the
largest size the whole image is drawn at on screen, per source label, scaled
to a 1920x1080 screen, the closest battle zoom and the largest UI scale.

It is data, not art: never edit figures by hand. Grow it with
`gradlew.bat createSnapshots -PtextureUsage` (and `commanderEvidence ...
-PsnapshotEveryTicks=450 -PtextureUsage`, and a dev-mode game), then
`gradlew.bat mergeTextureUsage`. Merging only ever raises a figure. To
re-measure an image, delete its entry (or one label under it) and run again.
