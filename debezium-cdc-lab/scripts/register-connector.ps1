$ErrorActionPreference = "Stop"

$connectorPath = Join-Path $PSScriptRoot "..\connector\postgres-connector.json"

Write-Host "Registering Debezium PostgreSQL connector..."
Invoke-RestMethod `
    -Uri "http://localhost:8083/connectors" `
    -Method Post `
    -ContentType "application/json" `
    -InFile $connectorPath

Write-Host "Connector registered. Run .\scripts\verify.ps1 to check status."
