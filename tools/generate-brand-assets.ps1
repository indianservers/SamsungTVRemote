# Original native artwork. No stock/Samsung assets or screenshots are used.
Add-Type -AssemblyName System.Drawing
$destination=Join-Path $PSScriptRoot '../release/assets'
[System.IO.Directory]::CreateDirectory($destination) | Out-Null
function Draw-Mark($graphics,[single]$x,[single]$y,[single]$scale) {
    $state=$graphics.Save()
    $graphics.TranslateTransform($x,$y)
    $graphics.ScaleTransform($scale,$scale)
    $pen=[System.Drawing.Pen]::new([System.Drawing.Color]::White,3)
    $blue=[System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#2563EB'))
    $graphics.DrawRectangle($pen,17,30,56,38)
    $graphics.DrawLine($pen,45,68,45,76)
    $graphics.DrawLine($pen,32,77,58,77)
    $path=[System.Drawing.Drawing2D.GraphicsPath]::new()
    $path.AddArc(60,43,10,10,180,90);$path.AddArc(74,43,10,10,270,90)
    $path.AddArc(74,79,10,10,0,90);$path.AddArc(60,79,10,10,90,90);$path.CloseFigure()
    $graphics.FillPath([System.Drawing.Brushes]::White,$path)
    $graphics.FillEllipse($blue,64,57,16,16)
    $graphics.FillRectangle($blue,69,49,6,3)
    $graphics.FillRectangle($blue,66,79,12,3)
    $graphics.Restore($state)
    $path.Dispose();$pen.Dispose();$blue.Dispose()
}
$icon=[System.Drawing.Bitmap]::new(512,512,[System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$g=[System.Drawing.Graphics]::FromImage($icon)
$g.SmoothingMode=[System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.ColorTranslator]::FromHtml('#2563EB'))
Draw-Mark $g 0 0 (512/108)
$icon.Save((Join-Path $destination 'app-icon-native-512.png'),[System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose();$icon.Dispose()
$feature=[System.Drawing.Bitmap]::new(1024,500,[System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$g=[System.Drawing.Graphics]::FromImage($feature)
$g.SmoothingMode=[System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint=[System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$g.Clear([System.Drawing.ColorTranslator]::FromHtml('#F8FBFF'))
$blue=[System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#2563EB'))
$ink=[System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#0F172A'))
$muted=[System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#475569'))
$pale=[System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#E8F1FF'))
$title=[System.Drawing.Font]::new('Segoe UI',40,[System.Drawing.FontStyle]::Bold,[System.Drawing.GraphicsUnit]::Pixel)
$subtitle=[System.Drawing.Font]::new('Segoe UI',27,[System.Drawing.FontStyle]::Regular,[System.Drawing.GraphicsUnit]::Pixel)
$body=[System.Drawing.Font]::new('Segoe UI',19,[System.Drawing.FontStyle]::Regular,[System.Drawing.GraphicsUnit]::Pixel)
$foot=[System.Drawing.Font]::new('Segoe UI',15,[System.Drawing.FontStyle]::Regular,[System.Drawing.GraphicsUnit]::Pixel)
$g.FillEllipse($pale,685,-125,490,490)
$g.DrawString('Smart TV Remote',$title,$ink,64,137)
$g.DrawString('Your remote. Your network.',$subtitle,$muted,64,213)
$g.DrawString('Navigate · Touchpad · Local control',$body,$blue,64,307)
$g.FillRectangle($blue,724,112,244,244)
Draw-Mark $g 724 112 (244/108)
$g.DrawString('Independent app. Compatibility varies by TV model and firmware.',$foot,$muted,64,424)
$feature.Save((Join-Path $destination 'feature-graphic-1024x500.png'),[System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose();$feature.Dispose()
@($blue,$ink,$muted,$pale,$title,$subtitle,$body,$foot) | ForEach-Object { $_.Dispose() }

