# Presentation only: full source frames, aspect preserved, no source pixel/alpha edits.
param([string]$StarsectorCore='C:/Program Files (x86)/Fractal Softworks/Starsector/starsector-core')
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$items=@(
 @{id='talon';label='Talon';source='graphics/ships/talon/talon.png'},
 @{id='wasp';label='Wasp';source='graphics/ships/wasp_ftr.png'},
 @{id='thunder';label='Thunder';source='graphics/ships/thunder.png'},
 @{id='broadsword';label='Broadsword';source='graphics/ships/broadsword.png'},
 @{id='longbow';label='Longbow';source='graphics/ships/longbow_intg.png'},
 @{id='dagger';label='Dagger';source='graphics/ships/dagger_trp.png'}
)
$manifestPath=Join-Path $PSScriptRoot 'manifest.json'
$manifest=if(Test-Path $manifestPath){Get-Content -Raw $manifestPath | ConvertFrom-Json}else{$null}
function Draw-FullFrame($g,$image,[single]$x,[single]$y,[single]$w,[single]$h,[bool]$nearest){
 $scale=[Math]::Min($w/$image.Width,$h/$image.Height)
 $dw=[single]($image.Width*$scale);$dh=[single]($image.Height*$scale)
 $g.InterpolationMode=if($nearest){[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor}else{[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic}
 $g.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::Half
 $g.DrawImage($image,[System.Drawing.RectangleF]::new($x+($w-$dw)/2,$y+($h-$dh)/2,$dw,$dh),[System.Drawing.RectangleF]::new(0,0,$image.Width,$image.Height),[System.Drawing.GraphicsUnit]::Pixel)
}
$sheet=[System.Drawing.Bitmap]::new(1920,1080,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g=[System.Drawing.Graphics]::FromImage($sheet)
$resources=[System.Collections.Generic.List[System.IDisposable]]::new()
try {
 $g.Clear([System.Drawing.Color]::FromArgb(22,29,35))
 $g.TextRenderingHint=[System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
 $title=[System.Drawing.Font]::new('Segoe UI',28)
 $name=[System.Drawing.Font]::new('Segoe UI',21)
 $small=[System.Drawing.Font]::new('Segoe UI',13)
 $bright=[System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(234,239,241))
 $muted=[System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(172,188,199))
 $panel=[System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(39,49,59))
 foreach($resource in @($title,$name,$small,$bright,$muted,$panel)){$resources.Add($resource)}
 $g.DrawString('Fighter aircraft / enlarged art review',$title,$bright,38,21)
 $g.DrawString('Original and ImageGen draft at matching display sizes  /  art review, not relative gameplay scale',$small,$muted,40,72)
 for($i=0;$i -lt $items.Count;$i++){
  $item=$items[$i];$dir=Join-Path $PSScriptRoot $item.id
  $master=Join-Path $dir 'master-v1.png'
  if(Test-Path (Join-Path $dir 'master-v2.png')){$master=Join-Path $dir 'master-v2.png'}
  $entry=$null
  if($manifest){
   if($manifest.fighters){$entry=$manifest.fighters | Where-Object {$_.id -eq $item.id} | Select-Object -First 1}
   if(-not $entry){$entry=$manifest.($item.id)}
  }
  if($entry -and $entry.master){
   $master=Join-Path $PSScriptRoot $entry.master
   if(-not (Test-Path $master)){$master=Join-Path $dir $entry.master}
  }
  $source=Join-Path $StarsectorCore $item.source
  if(-not (Test-Path $master)){throw "Missing draft: $master"}
  $original=[System.Drawing.Image]::FromFile($source);$draft=[System.Drawing.Image]::FromFile($master)
  $resources.Add($original);$resources.Add($draft)
  $x=30+630*($i%3);$y=118+466*[Math]::Floor($i/3)
  $g.FillRectangle($panel,$x,$y,600,444)
  $g.DrawString($item.label,$name,$bright,$x+20,$y+14)
  $g.DrawString('ORIGINAL / nearest',$small,$muted,$x+20,$y+62)
  $g.DrawString('IMAGEGEN DRAFT',$small,$muted,$x+320,$y+62)
  Draw-FullFrame $g $original ($x+20) ($y+90) 260 316 $true
  Draw-FullFrame $g $draft ($x+320) ($y+90) 260 316 $false
  $g.DrawString("$($original.Width) x $($original.Height) px / full frame",$small,$muted,$x+20,$y+410)
  $g.DrawString("$($draft.Width) x $($draft.Height) px / full frame",$small,$muted,$x+320,$y+410)
 }
 $output=Join-Path $PSScriptRoot 'review.png'
 $sheet.Save($output,[System.Drawing.Imaging.ImageFormat]::Png)
 Write-Output $output
} finally {
 foreach($resource in $resources){$resource.Dispose()}
 $g.Dispose();$sheet.Dispose()
}
