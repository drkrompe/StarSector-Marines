# Noto Sans SC

Source fonts for the zh_CN translation mod's CJK bitmap faces.

- From `notofonts/noto-cjk` release `Sans2.004`, asset `18_NotoSansSC.zip`
  (sha256 `4d107c09ada479d3e48b6e78c83835773cbd9214bf6e12cdb7b60f8e068292ec`);
  only the Regular and Bold weights are kept.
- Licence: SIL Open Font License 1.1, no Reserved Font Name (`OFL.txt`). The
  same text ships with the translation mod beside its fonts
  (`translations/zh_CN/graphics/fonts/NotoSansSC-OFL.txt`).
- `gradlew :asset-pipeline:generateCjkFonts` bakes the faces listed in
  `asset-pipeline/src/tool/resources/fonts/zh_CN.json` into
  `translations/zh_CN/graphics/fonts/` (ignored output; see
  `GenerateCjkFontsTask`).
