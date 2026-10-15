# Geometry contract: two 768x576 frames; hull and independent heavy MG face left.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$image = [System.Drawing.Bitmap]::new(1536,576)
$g = [System.Drawing.Graphics]::FromImage($image)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.Color]::Transparent)
function Rect($color,$x,$y,$w,$h) {
    $brush=[System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($color))
    $g.FillRectangle($brush,$x,$y,$w,$h); $brush.Dispose()
}
function Oval($color,$x,$y,$w,$h) {
    $brush=[System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($color))
    $g.FillEllipse($brush,$x,$y,$w,$h); $brush.Dispose()
}
function Poly($color,$points) {
    $brush=[System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($color))
    $vertices=[System.Drawing.PointF[]]@($points | ForEach-Object { [System.Drawing.PointF]::new($_[0],$_[1]) })
    $g.FillPolygon($brush,$vertices); $brush.Dispose()
}
foreach($x in @(175,365,555)) {
    Rect '#262b29' $x 112 100 62
    Rect '#262b29' $x 402 100 62
}
# Narrow wedge nose at left; wide, squared troop body and rear ramp at right.
Poly '#55614d' @(@(49,247),@(100,191),@(222,149),@(666,149),@(712,186),@(712,390),@(666,427),@(222,427),@(100,385),@(49,329))
# Sloped glacis and shoulder planes imply volume without exposing vertical sides.
Poly '#9caa88' @(@(49,247),@(100,191),@(222,149),@(254,201),@(131,238),@(99,268),@(99,308),@(131,338),@(254,375),@(222,427),@(100,385),@(49,329))
Poly '#718064' @(@(99,268),@(131,238),@(254,201),@(254,375),@(131,338),@(99,308))
Poly '#a4ad90' @(@(222,149),@(666,149),@(677,184),@(254,201))
Poly '#3f4c3d' @(@(222,427),@(666,427),@(677,392),@(254,375))
Rect '#303d3d' 199 217 43 38
Rect '#303d3d' 199 321 43 38
Rect '#75846a' 445 190 215 196
Rect '#56644f' 462 209 181 158
Rect '#859378' 484 231 137 114
Rect '#2d3933' 681 205 22 166
Rect '#a0513f' 684 189 15 12
Rect '#a0513f' 684 375 15 12
# The ring is an opaque bearing plate, not a hole through the vehicle.
Oval '#343e35' 274 228 120 120
Oval '#87927b' 283 237 102 102
Oval '#4d5848' 294 248 80 80
Rect '#3e4941' 276 206 116 24
Rect '#3e4941' 276 346 116 24
# Turret pivot is (1152,288), with paired muzzles (834,217) and (834,359).
# At roughly 1.4 cells/frame these match the MG's forward .58, lateral +/- .13.
Oval '#30382f' 1036 172 232 232
Poly '#64745b' @(@(1044,208),@(1082,172),@(1222,172),@(1260,208),@(1260,368),@(1222,404),@(1082,404),@(1044,368))
Rect '#3b4640' 1010 196 190 42
Rect '#252d2b' 834 207 184 20
Rect '#657267' 834 203 30 28
Rect '#3b4640' 1010 338 190 42
Rect '#252d2b' 834 349 184 20
Rect '#657267' 834 345 30 28
Rect '#8a927c' 1125 264 88 38
Rect '#3d5558' 1215 205 24 24
$g.Dispose()
$image.Save((Join-Path $PSScriptRoot 'guide.png'),[System.Drawing.Imaging.ImageFormat]::Png)
$image.Dispose()
