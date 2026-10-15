Add-Type -AssemblyName System.Drawing
$bitmap = New-Object System.Drawing.Bitmap 1920,1080
$g = [System.Drawing.Graphics]::FromImage($bitmap)
$g.Clear([System.Drawing.Color]::Transparent)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
function Rect($color, $x, $y, $w, $h) {
    $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml($color))
    $g.FillRectangle($brush, [float]$x, [float]$y, [float]$w, [float]$h)
    $brush.Dispose()
}
function Ellipse($color, $x, $y, $w, $h) {
    $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml($color))
    $g.FillEllipse($brush, [float]$x, [float]$y, [float]$w, [float]$h)
    $brush.Dispose()
}
# Six 640x540 frames, canonical nose east. Both tire shoulders are visible
# equally; tire treads are rectangles, never side-view circular wheel hubs.
for ($i=0; $i -lt 6; $i++) {
    $ox=($i%3)*640; $oy=[math]::Floor($i/3)*540
    $g.ResetTransform(); $g.TranslateTransform($ox,$oy)
    $paint=@('#b59142','#a66538','#9eaaa7','#afbeb1','#b07b48','#687667')[$i]
    Rect '#282e30' 60 162 520 216
    foreach($wx in @(105,210,465)) {
        Rect '#171e22' $wx 130 70 52
        Rect '#171e22' $wx 358 70 52
        foreach($dx in @(8,20,32,44,56)) {
            Rect '#3c4245' ($wx+$dx) 134 3 44
            Rect '#3c4245' ($wx+$dx) 362 3 44
        }
    }
    Rect $paint 390 165 180 210
    Rect '#b8b9a9' 408 187 105 166
    Rect '#263e49' 528 192 28 156
    Rect '#505b5d' 427 221 52 98
    Rect '#d3b64a' 565 176 8 35
    Rect '#d3b64a' 565 329 8 35
    if ($i -eq 0) {
        Rect $paint 70 175 305 190
        Rect '#424b4e' 95 201 110 138
        Rect '#b8b9a9' 221 194 125 72
        Rect '#4c5657' 221 284 125 57
        Rect '#d3b64a' 90 180 262 12
    } elseif ($i -eq 1) {
        Rect $paint 70 175 305 190
        Rect '#4b5356' 84 190 277 160
        foreach($xx in @(97,137,177,217,257,297,337)) { Rect '#788083' $xx 195 5 150 }
        Rect '#ae9c70' 120 221 95 98
        Rect '#727b6c' 244 222 86 96
    } elseif ($i -eq 2) {
        Ellipse '#697875' 70 180 298 180
        Rect '#a9b7b3' 140 180 160 180
        foreach($xx in @(150,278)) { Rect '#4c5b58' $xx 180 12 180 }
        Ellipse '#4b5855' 203 243 54 54
        Ellipse '#afb7ac' 213 253 34 34
    } elseif ($i -eq 3) {
        Rect $paint 80 175 315 190
        Rect '#d4d2bb' 103 191 266 158
        Rect '#687c7c' 128 218 104 104
        Rect '#535e5c' 251 222 84 96
    } elseif ($i -eq 4) {
        Rect $paint 70 175 305 190
        Rect '#c1b99e' 84 187 277 166
        foreach($xx in @(104,144,184,224,264,304,344)) { Rect '#7c776b' $xx 190 7 160 }
    } else {
        Rect $paint 70 165 330 210
        Rect '#89927e' 90 185 285 170
        foreach($xx in @(116,225)) { Rect '#49574e' $xx 218 85 104 }
        Rect '#3c4944' 340 210 25 120
    }
}
$g.Dispose()
$bitmap.Save((Join-Path $PSScriptRoot 'parked-vehicles-guide.png'),[System.Drawing.Imaging.ImageFormat]::Png)
$bitmap.Dispose()
