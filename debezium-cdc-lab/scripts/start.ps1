$ErrorActionPreference = "Stop"

Write-Host "Starting Debezium CDC lab..."
docker compose up -d

Write-Host "Waiting for services to become ready..."
Start-Sleep -Seconds 30

Write-Host "Stack is up. Next: .\scripts\register-connector.ps1"
