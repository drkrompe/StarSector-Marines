Add-Type -AssemblyName System.Drawing
$bitmap = New-Object System.Drawing.Bitmap 1536,1536
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
# Columns: 3x3 fabrication enclosure, 3x2 assembly bed, 2x2 coolant skid.
# Rows: intact, damaged standing, destroyed low separate fragments.
# Each slot is 512 square; the full placement frame is centered at 128 px/cell.
# Canonical operator face is image-bottom; no floor, apron or shadow beyond frame.
for ($row=0; $row -lt 3; $row++) {
    for ($col=0; $col -lt 3; $col++) {
        $g.ResetTransform(); $g.TranslateTransform($col*512,$row*512)
        $w=@(384,384,256)[$col]; $h=@(384,256,256)[$col]
        $x=(512-$w)/2; $y=(512-$h)/2
        if ($row -eq 2) {
            # Debris keeps the original frame with broad transparent cross-gaps.
            Rect '#555e5a' ($x+12) ($y+14) ($w*0.32) 35
            Rect '#777e6e' ($x+16) ($y+55) 40 ($h*0.27)
            Rect '#474f50' ($x+$w*0.63) ($y+20) ($w*0.29) ($h*0.21)
            Rect '#96977f' ($x+$w*0.53) ($y+$h*0.55) ($w*0.24) 40
            Rect '#414b4b' ($x+19) ($y+$h*0.71) ($w*0.32) ($h*0.21)
            Rect '#6b776d' ($x+$w*0.84) ($y+$h*0.69) 23 ($h*0.24)
            Rect '#a09261' ($x+$w*0.46) ($y+$h*0.38) 28 19
            continue
        }
        Rect '#343f40' ($x+8) ($y+8) ($w-16) ($h-16)
        Rect '#899486' ($x+16) ($y+16) ($w-32) ($h-32)
        if ($col -eq 0) {
            Rect '#3a4d50' ($x+70) ($y+50) 244 235
            Rect '#29464b' ($x+90) ($y+70) 204 195
            Rect '#647b79' ($x+106) ($y+88) 172 155
            Rect '#adb3a2' ($x+80) ($y+144) 224 32
            Rect '#4a5a58' ($x+175) ($y+82) 40 143
            Rect '#c0ad60' ($x+20) ($y+305) 344 14
            Rect '#334e50' ($x+218) ($y+327) 100 31
            Rect '#79c9bd' ($x+229) ($y+334) 40 14
            foreach ($dy in @(46,66,86,106,126,146,166,186,206,226,246)) { Rect '#4e5f5a' ($x+25) ($y+$dy) 25 7 }
        } elseif ($col -eq 1) {
            Rect '#3c4949' ($x+75) ($y+35) 270 150
            foreach ($dx in @(86,111,136,161,186,211,236,261,286,311)) { Rect '#a0aba2' ($x+$dx) ($y+45) 12 130 }
            Rect '#b1b3a0' ($x+166) ($y+25) 30 173
            Rect '#465d5b' ($x+142) ($y+92) 78 48
            Rect '#50635d' ($x+23) ($y+28) 37 154
            Rect '#c2ab5f' ($x+20) ($y+198) 344 11
            Rect '#263e42' ($x+236) ($y+218) 99 21
            Rect '#69c7ba' ($x+245) ($y+222) 38 11
        } else {
            Ellipse '#374c4c' ($x+27) ($y+24) 124 124
            Ellipse '#a9b6a6' ($x+38) ($y+35) 102 102
            Ellipse '#5c7872' ($x+68) ($y+65) 42 42
            Rect '#455851' ($x+175) ($y+30) 52 140
            foreach ($dy in @(40,60,80,100,120,140)) { Rect '#94a292' ($x+180) ($y+$dy) 40 8 }
            Rect '#6b8177' ($x+34) ($y+163) 117 39
            Rect '#bba35b' ($x+22) ($y+211) 210 10
            Rect '#63c5b8' ($x+177) ($y+183) 42 15
        }
        if ($row -eq 1) {
            Rect '#292b2b' ($x+$w*0.18) ($y+$h*0.31) ($w*0.44) 13
            Rect '#373633' ($x+$w*0.49) ($y+$h*0.31) 18 ($h*0.37)
            Rect '#5f5440' ($x+$w*0.50) ($y+$h*0.72) ($w*0.36) 20
        }
    }
}
$g.Dispose()
$bitmap.Save((Join-Path $PSScriptRoot 'factory-machines-guide.png'),[System.Drawing.Imaging.ImageFormat]::Png)
$bitmap.Dispose()
