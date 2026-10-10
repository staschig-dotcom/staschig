<#
  Помощник агента mp-finreport-downloader.
  -Mark                      печатает текущую отметку времени (ISO), её агент передаёт потом в -Since.
  -Collect -Since -Dest -Prefix [-TimeoutSec] [-Downloads]
                             ждёт файлы, появившиеся в папке загрузок после -Since, дожидается
                             окончания загрузки (.crdownload/.tmp исчезли, размер стабилен),
                             перемещает их в -Dest с префиксом. Печатает JSON {ok, files, error}.
  Ничего не удаляет. Существующие файлы в -Dest не перезаписывает (добавляет суффикс).
#>
param(
  [switch]$Mark,
  [switch]$Collect,
  [string]$Since,
  [string]$Dest,
  [string]$Prefix,
  [int]$TimeoutSec = 180,
  [string]$Downloads
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Out-Json($obj) { $obj | ConvertTo-Json -Compress -Depth 4 | Write-Output }

if ($Mark) {
  Write-Output ((Get-Date).ToString('o'))
  exit 0
}

if (-not $Collect) { Out-Json @{ ok = $false; error = 'use -Mark or -Collect' }; exit 1 }
if (-not $Since -or -not $Dest -or -not $Prefix) { Out-Json @{ ok = $false; error = '-Since, -Dest, -Prefix required' }; exit 1 }

if (-not $Downloads) {
  # Папка «Загрузки» из реестра пользователя (учитывает перенос на другой диск).
  $key = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\User Shell Folders'
  $raw = (Get-ItemProperty -Path $key -Name '{374DE290-123F-4565-9164-39C4925E467B}' -ErrorAction SilentlyContinue).'{374DE290-123F-4565-9164-39C4925E467B}'
  $Downloads = if ($raw) { [Environment]::ExpandEnvironmentVariables($raw) } else { Join-Path $env:USERPROFILE 'Downloads' }
}

$sinceTime = [datetime]::Parse($Since, $null, [System.Globalization.DateTimeStyles]::RoundtripKind)
$deadline = (Get-Date).AddSeconds($TimeoutSec)
$partial = @('.crdownload', '.tmp', '.part')

function Get-NewFiles {
  Get-ChildItem -LiteralPath $Downloads -File | Where-Object { $_.CreationTime -gt $sinceTime -or $_.LastWriteTime -gt $sinceTime }
}

$ready = @()
while ((Get-Date) -lt $deadline) {
  $new = @(Get-NewFiles)
  $inProgress = @($new | Where-Object { $partial -contains $_.Extension.ToLower() })
  $done = @($new | Where-Object { $partial -notcontains $_.Extension.ToLower() })
  if ($done.Count -gt 0 -and $inProgress.Count -eq 0) {
    $sizes1 = $done | ForEach-Object { $_.Length }
    Start-Sleep -Seconds 2
    $sizes2 = $done | ForEach-Object { (Get-Item -LiteralPath $_.FullName).Length }
    if (-not (Compare-Object $sizes1 $sizes2)) { $ready = $done; break }
  }
  Start-Sleep -Seconds 3
}

if ($ready.Count -eq 0) {
  Out-Json @{ ok = $false; error = "no completed download in $Downloads after $TimeoutSec s"; files = @() }
  exit 2
}

New-Item -ItemType Directory -Force -Path $Dest | Out-Null
$moved = @()
foreach ($f in $ready) {
  $base = "$Prefix`_$($f.BaseName)"
  $target = Join-Path $Dest ($base + $f.Extension)
  $i = 2
  while (Test-Path -LiteralPath $target) { $target = Join-Path $Dest ("$base ($i)" + $f.Extension); $i++ }
  Move-Item -LiteralPath $f.FullName -Destination $target
  $moved += $target
}

Out-Json @{ ok = $true; files = $moved }
exit 0
