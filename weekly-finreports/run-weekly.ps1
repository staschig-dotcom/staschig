<#
  Запуск агента mp-finreport-downloader из Планировщика заданий Windows.
  Без аргументов — полный прогон по всем активным клиентам.
  -Retry          — повторный прогон только по тому, что не скачалось (статус не OK).
  Вывод Claude дописывается в D:\Еженедельные оперативки\_агент\scheduler.log
#>
param([switch]$Retry)

$root = 'D:\Еженедельные оперативки'
$log  = Join-Path $root '_агент\scheduler.log'
Set-Location -LiteralPath $root

$prompt = if ($Retry) {
  'Используй агента mp-finreport-downloader: повторный прогон по всем активным клиентам.'
} else {
  'Используй агента mp-finreport-downloader: полный прогон по всем активным клиентам.'
}

Add-Content -LiteralPath $log -Value "`n===== $((Get-Date).ToString('yyyy-MM-dd HH:mm')) $(if ($Retry) {'RETRY'} else {'FULL'}) ====="
& claude --chrome -p $prompt *>&1 | Out-File -LiteralPath $log -Append -Encoding utf8
Add-Content -LiteralPath $log -Value "===== exit code $LASTEXITCODE ====="
