# IM Debug Scripts

This folder contains standalone scripts to verify:
- HTTP auth + IM endpoints via Gateway (`/im/**`)
- WebSocket connect/close reasons via Gateway (`/ws/**`)

## Prerequisites

- Gateway running: `http://localhost:8080`
- IM service running behind Gateway
- A valid Sa-Token token from your login

## Node.js (recommended)

```powershell
cd im-debug
npm i
$env:TOKEN="YOUR_TOKEN_HERE"
npm run test:im
```

Optional overrides:

```powershell
$env:BASE_HTTP="http://localhost:8080"
$env:WS_URL="ws://localhost:8080/ws/chat?token=YOUR_TOKEN_HERE"
```

## Python

Install deps:

```powershell
pip install requests websocket-client
```

Run:

```powershell
cd im-debug
$env:TOKEN="YOUR_TOKEN_HERE"
python .\im_test.py
```
