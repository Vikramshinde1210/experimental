$ErrorActionPreference = "Stop"

Write-Host "Connector status:"
Invoke-RestMethod -Uri "http://localhost:8083/connectors/postgres-connector/status" | ConvertTo-Json -Depth 5

Write-Host ""
Write-Host "Registered connectors:"
Invoke-RestMethod -Uri "http://localhost:8083/connectors" | ConvertTo-Json
