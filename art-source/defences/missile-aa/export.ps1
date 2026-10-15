# Mechanical full-frame exports and comparisons. No painting or source-frame changes.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$registration = Get-Content -Raw (Join-Path $PSScriptRoot 'registration.json') | ConvertFrom-Json
$coordinateSize = [double]$registration.coordinateCanvasSize
if ($coordinateSize -ne 512) { throw 'Registration coordinates must use the 512-pixel export canvas' }
$definitions = @(
    @{ name='swarmer'; label='SWARMER'; directory='swarmer-aa'; native='swarmer_srm_launcher_turret_base.png' },
    @{ name='proximity'; label='PROXIMITY CHARGE'; directory='proximity-aa'; native='phase_charge_launcher_turret_base.png' }
)
function Draw-Frame($graphics, $sprite, [single]$x, [single]$y, [single]$size, [bool]$nearest=$false) {
    $graphics.InterpolationMode = if ($nearest) { [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor } else { [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic }
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $graphics.DrawImage($sprite,
        [System.Drawing.RectangleF]::new($x,$y,$size,$size),
        [System.Drawing.RectangleF]::new(0,0,$sprite.Width,$sprite.Height),
        [System.Drawing.GraphicsUnit]::Pixel)
}
function Assert-Registration($pivot, [double]$scale, [string]$name) {
    if ($pivot.Count -ne 2 -or $scale -le 0 -or [double]::IsNaN($scale) -or [double]::IsInfinity($scale)) { throw "Invalid registration for $name" }
    foreach ($value in $pivot) {
        if ([double]::IsNaN([double]$value) -or [double]::IsInfinity([double]$value) -or $value -lt 0 -or $value -gt 512) { throw "Invalid pivot for $name" }
    }
}
$loaded = [System.Collections.Generic.List[System.IDisposable]]::new()
$measurements = @()
$weapons = @()
try {
    foreach ($definition in $definitions) {
        $root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ("../"+$definition.directory)))
        $settings = $registration.mounts.($definition.name)
        if ($null -eq $settings) { throw "Missing manifest entry for $($definition.name)" }
        Assert-Registration $settings.pivot $settings.scale $definition.name
        $master = [System.Drawing.Bitmap]::FromFile((Join-Path $root 'mount.master.png'))
        $loaded.Add($master)
        $native = [System.Drawing.Bitmap]::FromFile((Join-Path $root ("sources/"+$definition.native)))
        $loaded.Add($native)
        # Reproduce the exact nearest-neighbor ImageGen reference from native art.
        $enlarged=[System.Drawing.Bitmap]::new(768,768,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $referenceGraphics=[System.Drawing.Graphics]::FromImage($enlarged)
        try {
            $referenceGraphics.CompositingMode=[System.Drawing.Drawing2D.CompositingMode]::SourceCopy
            $referenceGraphics.Clear([System.Drawing.Color]::Transparent)
            Draw-Frame $referenceGraphics $native 0 0 768 $true
            $enlarged.Save((Join-Path $root 'sources/vanilla-enlarged.png'),[System.Drawing.Imaging.ImageFormat]::Png)
        } finally { $referenceGraphics.Dispose(); $enlarged.Dispose() }
        if ($master.Width -ne $master.Height) { throw "$($definition.name) master must be square" }
        $layer = [System.Drawing.Bitmap]::new(512,512,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $loaded.Add($layer)
        $layerGraphics = [System.Drawing.Graphics]::FromImage($layer)
        try {
            $layerGraphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
            $layerGraphics.Clear([System.Drawing.Color]::Transparent)
            Draw-Frame $layerGraphics $master 0 0 512
        } finally { $layerGraphics.Dispose() }
        $minX=512; $minY=512; $maxX=-1; $maxY=-1; $transparent=0
        for ($y=0; $y -lt 512; $y++) {
            for ($x=0; $x -lt 512; $x++) {
                $alpha=$layer.GetPixel($x,$y).A
                if ($alpha -eq 0) { $transparent++ }
                if ($alpha -ge 32) {
                    $minX=[Math]::Min($minX,$x); $minY=[Math]::Min($minY,$y)
                    $maxX=[Math]::Max($maxX,$x); $maxY=[Math]::Max($maxY,$y)
                }
                # Preserve faint alpha residue, but reject visible edge content.
                if (($x -eq 0 -or $x -eq 511 -or $y -eq 0 -or $y -eq 511) -and $alpha -ge 8) { throw "$($definition.name) touches its export boundary" }
            }
        }
        if ($maxX -lt 0 -or $transparent -eq 0) { throw "$($definition.name) is empty or lacks transparent space" }
        $layerRoot = Join-Path $root 'layers'
        New-Item -ItemType Directory -Force -Path $layerRoot | Out-Null
        $layer.Save((Join-Path $layerRoot 'mount.png'),[System.Drawing.Imaging.ImageFormat]::Png)
        $measurement = [ordered]@{
            name=$definition.name; masterWidth=$master.Width; masterHeight=$master.Height
            nativeWidth=$native.Width; nativeHeight=$native.Height; layerSize=512
            pivot=@($settings.pivot); compositeScale=$settings.scale
            alphaBounds=@($minX,$minY,($maxX+1),($maxY+1)); transparentPixels=$transparent
            registrationSource='../missile-aa/registration.json'; fullFramePreserved=$true
        }
        $measurement | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 (Join-Path $root 'export-metadata.json')
        $measurements += $measurement
        $weapons += @{ name=$definition.name; label=$definition.label; root=$root; master=$master; native=$native; layer=$layer; pivot=$settings.pivot; scale=[double]$settings.scale }
    }
    $baseRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../locust-aa'))
    $tower = [System.Drawing.Bitmap]::FromFile((Join-Path $baseRoot 'ground-base.master.png'))
    $loaded.Add($tower)
    Assert-Registration $registration.groundBase.pivot 1 'ground base'
    $locust = [System.Drawing.Bitmap]::FromFile((Join-Path $baseRoot 'locust-mount.master.png'))
    $loaded.Add($locust)
    $locustMetadata = Get-Content -Raw (Join-Path $baseRoot 'export-metadata.json') | ConvertFrom-Json
    $locustRegistration = $locustMetadata | Where-Object { $_.name -eq 'locust-mount' }
    if ($null -eq $locustRegistration -or $locustRegistration.layerSize -ne 512) { throw 'Missing 512-canvas Locust registration' }
    Assert-Registration $locustRegistration.pivot 1 'Locust'
    function Draw-Assembly($graphics, $weapon, [single]$x, [single]$y, [single]$size) {
        $centerX=$x+$size/2; $centerY=$y+$size/2
        Draw-Frame $graphics $tower ($centerX-$size*$registration.groundBase.pivot[0]/512) ($centerY-$size*$registration.groundBase.pivot[1]/512) $size
        $mountSize=$size*$weapon.scale
        Draw-Frame $graphics $weapon.master ($centerX-$mountSize*$weapon.pivot[0]/512) ($centerY-$mountSize*$weapon.pivot[1]/512) $mountSize
    }
    foreach ($weapon in $weapons) {
        $composite=[System.Drawing.Bitmap]::new(512,512,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $graphics=[System.Drawing.Graphics]::FromImage($composite)
        try {
            $graphics.Clear([System.Drawing.Color]::Transparent)
            Draw-Assembly $graphics $weapon 0 0 512
            $composite.Save((Join-Path $weapon.root 'composite.png'),[System.Drawing.Imaging.ImageFormat]::Png)
        } finally { $graphics.Dispose(); $composite.Dispose() }
    }
    $title=[System.Drawing.Font]::new('Segoe UI',32); $label=[System.Drawing.Font]::new('Segoe UI',20); $small=[System.Drawing.Font]::new('Segoe UI',16)
    $white=[System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(232,237,235))
    $muted=[System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(166,181,178))
    $panel=[System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(39,48,49))
    foreach ($resource in @($title,$label,$small,$white,$muted,$panel)) { $loaded.Add($resource) }
    $family=@(@{ label='LOCUST'; master=$locust; pivot=$locustRegistration.pivot; scale=1 }) + $weapons
    $sheet=[System.Drawing.Bitmap]::new(1920,1080)
    $graphics=[System.Drawing.Graphics]::FromImage($sheet)
    try {
        $graphics.Clear([System.Drawing.Color]::FromArgb(23,30,32))
        $graphics.DrawString('GROUND MISSILE AA / FAMILY', $title,$white,60,35)
        $graphics.DrawString('Shared raised foundation  |  Full frames  |  Explicit mount registration', $small,$muted,62,95)
        for ($i=0; $i -lt 3; $i++) {
            $left=40+620*$i
            $graphics.FillRectangle($panel,$left,160,600,820)
            $graphics.DrawString($family[$i].label,$label,$white,$left+20,180)
            Draw-Assembly $graphics $family[$i] ($left+20) 225 560
            Draw-Assembly $graphics $family[$i] ($left+236) 805 128
            $graphics.DrawString('128 px canvas',$small,$muted,$left+215,943)
        }
        $graphics.DrawString('1920 x 1080 art review. Canvas sizes are readability studies; runtime footprint and firing origins remain unauthored.', $small,$muted,60,1015)
        $sheet.Save((Join-Path $PSScriptRoot 'family-review.png'),[System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $graphics.Dispose(); $sheet.Dispose() }
    $sheet=[System.Drawing.Bitmap]::new(1920,1080)
    $graphics=[System.Drawing.Graphics]::FromImage($sheet)
    try {
        $graphics.Clear([System.Drawing.Color]::FromArgb(23,30,32))
        $graphics.DrawString('MISSILE MOUNTS / SOURCE AND UPRES', $title,$white,60,35)
        $graphics.DrawString('Vanilla source enlarged with nearest neighbor  |  Generated full-frame master  |  Assembled below', $small,$muted,62,95)
        for ($i=0; $i -lt 2; $i++) {
            $weapon=$weapons[$i]; $left=40+940*$i
            $graphics.FillRectangle($panel,$left,160,900,820)
            $graphics.DrawString($weapon.label,$label,$white,$left+20,180)
            $nativeLabel="VANILLA / $($weapon.native.Width) x $($weapon.native.Height)"
            $graphics.DrawString($nativeLabel,$small,$muted,$left+20,235)
            Draw-Frame $graphics $weapon.native ($left+45) 290 180 $true
            $graphics.DrawString('GENERATED MASTER',$small,$muted,$left+390,235)
            Draw-Frame $graphics $weapon.master ($left+370) 265 400
            Draw-Assembly $graphics $weapon ($left+305) 690 280
            $graphics.DrawString('ASSEMBLED / 280 px canvas',$small,$muted,$left+20,925)
        }
        $graphics.DrawString('1920 x 1080 art review. Native references: Fractal Softworks. Generated art preserves full frames without painting or trimming.', $small,$muted,60,1015)
        $sheet.Save((Join-Path $PSScriptRoot 'upres-review.png'),[System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $graphics.Dispose(); $sheet.Dispose() }
    $measurements | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 (Join-Path $PSScriptRoot 'export-metadata.json')
    $measurements | ConvertTo-Json -Depth 5
} finally {
    foreach ($resource in $loaded) { $resource.Dispose() }
}
