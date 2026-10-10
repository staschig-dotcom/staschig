<#
  Помощник агента mp-finreport-downloader.
  -Mark                      печатает текущую отметку времени (ISO), её агент передаёт потом в -Since.
  -Collect -Since -Dest [-Prefix] [-TimeoutSec] [-Downloads]
                             ждёт файлы, появившиеся в папке загрузок после -Since, дожидается
                             окончания загрузки (.crdownload/.tmp исчезли, размер стабилен),
                             перемещает их в -Dest С ИСХОДНЫМ ИМЕНЕМ (-Prefix необязателен). Если файл с таким именем уже есть, старый уходит в _агент\_replaced\<время>\. Печатает JSON {ok, files, error}.
  Ничего не удаляет. Существующие файлы в -Dest не удаляет и не перезаписывает: переносит в архив.
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
if (-not $Since -or -not $Dest) { Out-Json @{ ok = $false; error = '-Since, -Dest required' }; exit 1 }

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
$moved = @(); $replaced = @()
$archiveRoot = Join-Path $PSScriptRoot ('_replaced\' + (Get-Date).ToString('yyyyMMdd-HHmmss'))
foreach ($f in $ready) {
  # Default: keep the marketplace's original file name (downstream skills glob by it).
  # -Prefix is optional and only prepended when given explicitly.
  $name = if ($Prefix) { "$Prefix`_$($f.Name)" } else { $f.Name }
  $target = Join-Path $Dest $name
  if (Test-Path -LiteralPath $target) {
    # Same name already in Dest (re-run): move the OLD one to archive, never delete, never create "(2)" duplicates.
    New-Item -ItemType Directory -Force -Path $archiveRoot | Out-Null
    $arch = Join-Path $archiveRoot $name
    Move-Item -LiteralPath $target -Destination $arch
    $replaced += $arch
  }
  Move-Item -LiteralPath $f.FullName -Destination $target
  $moved += $target
}

Out-Json @{ ok = $true; files = $moved; replaced = $replaced }
exit 0
