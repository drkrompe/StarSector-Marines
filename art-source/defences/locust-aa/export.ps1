# Mechanical full-frame export and art review only; does not register a weapon.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$layers = Join-Path $PSScriptRoot 'layers'
New-Item -ItemType Directory -Force -Path $layers | Out-Null
$measurements = @()
foreach ($name in @('locust-mount', 'ground-base')) {
    $source = [System.Drawing.Bitmap]::FromFile((Join-Path $PSScriptRoot "$name.master.png"))
    try {
        if ($source.Width -ne $source.Height) { throw "$name must have a square canvas" }
        if ($source.GetPixel(0, 0).A -ne 0) { throw "$name must have genuine transparency" }
        $target = [System.Drawing.Bitmap]::new(512, 512, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g = [System.Drawing.Graphics]::FromImage($target)
        try {
            $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
            $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $g.DrawImage($source, 0, 0, 512, 512)
            $minX = 512; $minY = 512; $maxX = -1; $maxY = -1
            $transparent = 0
            for ($y = 0; $y -lt 512; $y++) {
                for ($x = 0; $x -lt 512; $x++) {
                    $alpha = $target.GetPixel($x, $y).A
                    if ($alpha -eq 0) { $transparent++ }
                    if ($alpha -ge 32) {
                        $minX = [Math]::Min($minX, $x); $minY = [Math]::Min($minY, $y)
                        $maxX = [Math]::Max($maxX, $x); $maxY = [Math]::Max($maxY, $y)
                    }
                    # ImageGen may leave alpha=1 residue in otherwise empty space.
                    # Preserve its alpha; reject visible content at the frame edge.
                    if (($x -eq 0 -or $y -eq 0 -or $x -eq 511 -or $y -eq 511) -and $alpha -ge 8) {
                        throw "$name touches the canvas boundary"
                    }
                }
            }
            if ($maxX -lt 0 -or $transparent -eq 0) { throw "$name is empty or lacks transparent space" }
            $target.Save((Join-Path $layers "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
            $measurements += [ordered]@{name=$name; masterSize=$source.Width; layerSize=512; pivot=@(256,256); alphaBounds=@($minX,$minY,($maxX+1),($maxY+1)); transparentPixels=$transparent}
        } finally { $g.Dispose(); $target.Dispose() }
    } finally { $source.Dispose() }
}
$measurements | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 (Join-Path $PSScriptRoot 'export-metadata.json')

# Fixed 1920x1080 art contact sheet, authored at 100% (no UI scale transform).
$sheet = [System.Drawing.Bitmap]::new(1920, 1080)
$g = [System.Drawing.Graphics]::FromImage($sheet)
$titleFont = [System.Drawing.Font]::new('Segoe UI', 32)
$labelFont = [System.Drawing.Font]::new('Segoe UI', 20)
$smallFont = [System.Drawing.Font]::new('Segoe UI', 16)
$white = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(232,237,235))
$muted = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(166,181,178))
$panel = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(39,48,49))
$original = [System.Drawing.Bitmap]::FromFile((Join-Path $PSScriptRoot 'sources/locust_turret.png'))
$mount = [System.Drawing.Bitmap]::FromFile((Join-Path $layers 'locust-mount.png'))
$base = [System.Drawing.Bitmap]::FromFile((Join-Path $layers 'ground-base.png'))
function Draw-Sprite($sprite, [int]$x, [int]$y, [int]$size, [bool]$nearest = $false) {
    $g.InterpolationMode = if ($nearest) { [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor } else { [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic }
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $g.DrawImage($sprite, [System.Drawing.Rectangle]::new($x,$y,$size,$size), 0,0,$sprite.Width,$sprite.Height,[System.Drawing.GraphicsUnit]::Pixel)
}
try {
    $g.Clear([System.Drawing.Color]::FromArgb(23,30,32))
    $g.DrawString('LOCUST / GROUND AA', $titleFont, $white, 60, 35)
    $g.DrawString('Art pass 01  |  Separate stationary base and rotating mount  |  Upward-facing orthographic sprites', $smallFont, $muted, 62, 95)
    $labels = @('VANILLA SOURCE', 'UP-RES MOUNT', 'GROUND FOUNDATION', 'ASSEMBLED')
    for ($i = 0; $i -lt 4; $i++) {
        $x = 40 + 470*$i
        $g.FillRectangle($panel, $x, 170, 430, 490)
        $g.DrawString($labels[$i], $labelFont, $white, $x+15, 185)
    }
    Draw-Sprite $original 45 225 420 $true
    Draw-Sprite $mount 515 225 420
    Draw-Sprite $base 985 225 420
    Draw-Sprite $base 1455 225 420
    Draw-Sprite $mount 1455 225 420
    $g.DrawString('64 x 64 source, nearest-neighbor enlargement', $smallFont, $muted, 55, 680)
    $g.DrawString('512 x 512 transparent layers; full master frames retained', $smallFont, $muted, 530, 680)
    $g.DrawString('SIZE STUDY / ASSEMBLED', $labelFont, $white, 60, 755)
    Draw-Sprite $base 60 810 64; Draw-Sprite $mount 60 810 64
    Draw-Sprite $base 200 795 96; Draw-Sprite $mount 200 795 96
    Draw-Sprite $base 370 780 128; Draw-Sprite $mount 370 780 128
    $g.DrawString('64 px', $smallFont, $muted, 60, 920)
    $g.DrawString('96 px', $smallFont, $muted, 215, 920)
    $g.DrawString('128 px', $smallFont, $muted, 390, 920)
    $g.DrawString('NEXT IN THE FAMILY', $labelFont, $white, 670, 770)
    $g.DrawString('Swarmer SRM Launcher  /  Proximity Charge Launcher', $smallFont, $muted, 670, 820)
    $g.DrawString('Art review only. World footprint, muzzle placement and gameplay are not yet authored.', $smallFont, $muted, 670, 870)
    $g.DrawString('Vanilla Locust art: Fractal Softworks. Up-res interpretation: built-in ImageGen. Ground base derived from project Ridgewatch art.', $smallFont, $muted, 60, 1000)
    $sheet.Save((Join-Path $PSScriptRoot 'review.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $g.Dispose(); $sheet.Dispose(); $original.Dispose(); $mount.Dispose(); $base.Dispose()
    $titleFont.Dispose(); $labelFont.Dispose(); $smallFont.Dispose(); $white.Dispose(); $muted.Dispose(); $panel.Dispose()
}
$measurements | ConvertTo-Json -Depth 5
