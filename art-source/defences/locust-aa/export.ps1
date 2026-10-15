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
        if ($source.GetPixel(0, 0).A -ge 8) { throw "$name must have a transparent corner" }
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
            $pivot = @(256,256)
            if ($name -eq 'locust-mount') {
                # The round floor contains the whole mount silhouette. Its bounds
                # locate the painted yaw center despite ImageGen's framing drift.
                # Record registration; never shift pixels in the retained frames.
                $pivot = @((($minX+$maxX+1)/2.0), (($minY+$maxY+1)/2.0))
            }
            $measurements += [ordered]@{name=$name; masterSize=$source.Width; layerSize=512; pivot=$pivot; alphaBounds=@($minX,$minY,($maxX+1),($maxY+1)); transparentPixels=$transparent}
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
$guide = [System.Drawing.Bitmap]::FromFile((Join-Path $PSScriptRoot 'locust-reskin-guide.png'))
$master = [System.Drawing.Bitmap]::FromFile((Join-Path $PSScriptRoot 'locust-mount.master.png'))
$mount = [System.Drawing.Bitmap]::FromFile((Join-Path $layers 'locust-mount.png'))
function Draw-Sprite($sprite, [int]$x, [int]$y, [int]$size, [bool]$nearest = $false) {
    $g.InterpolationMode = if ($nearest) { [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor } else { [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic }
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $g.DrawImage($sprite, [System.Drawing.Rectangle]::new($x,$y,$size,$size), 0,0,$sprite.Width,$sprite.Height,[System.Drawing.GraphicsUnit]::Pixel)
}
try {
    $g.Clear([System.Drawing.Color]::FromArgb(23,30,32))
    $g.DrawString('LOCUST / GROUND AA', $titleFont, $white, 60, 35)
    $g.DrawString('Approved elongated head  |  Parallel forward tubes  |  Side trunnions over the gimbal floor', $smallFont, $muted, 62, 95)
    $labels = @('BLENDER GEOMETRY GUIDE', 'IMAGEGEN RESKIN')
    for ($i = 0; $i -lt 2; $i++) {
        $x = 40 + 780*$i
        $g.FillRectangle($panel, $x, 160, 760, 800)
        $g.DrawString($labels[$i], $labelFont, $white, $x+20, 180)
    }
    Draw-Sprite $guide 50 210 740
    Draw-Sprite $master 830 210 740
    $g.FillRectangle($panel, 1600, 160, 280, 800)
    $g.DrawString('VANILLA PALETTE', $smallFont, $white, 1620, 185)
    Draw-Sprite $original 1660 220 160 $true
    $g.DrawString('SIZE STUDIES', $smallFont, $white, 1620, 410)
    Draw-Sprite $mount 1644 445 192
    $g.DrawString('192 px canvas', $smallFont, $muted, 1660, 632)
    Draw-Sprite $mount 1676 675 128
    $g.DrawString('128 px canvas', $smallFont, $muted, 1660, 802)
    Draw-Sprite $mount 1708 850 64
    $g.DrawString('64 px canvas', $smallFont, $muted, 1665, 922)
    $g.DrawString('Transparent 512 x 512 export. Full frames retained. Forward is up; head elevation is 20 degrees.', $smallFont, $muted, 60, 980)
    $g.DrawString('Art review only; world scale and firing origins remain to be authored. Vanilla palette reference: Fractal Softworks.', $smallFont, $muted, 60, 1017)
    $sheet.Save((Join-Path $PSScriptRoot 'review.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $g.Dispose(); $sheet.Dispose(); $original.Dispose(); $guide.Dispose(); $master.Dispose(); $mount.Dispose()
    $titleFont.Dispose(); $labelFont.Dispose(); $smallFont.Dispose(); $white.Dispose(); $muted.Dispose(); $panel.Dispose()
}
$measurements | ConvertTo-Json -Depth 5
