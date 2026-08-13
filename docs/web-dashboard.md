# Web Dashboard (View-Only)

This repository now includes a static view-only dashboard in `dashboard/`.

## What it includes

- Google Maps-based geospatial views
- Map tabs:
	- Encounters Map
	- Regional Hotspots
	- Device Location Map
- Source filters (WIFI, WIFI DIRECT, BLE, BT CLASSIC, CELL, REMOTE ID, UWB, SDR, UNKNOWN RF)
- KPI summary cards
- Latest encounters table
- Regional hotspot ranking and hotspot circles
- Chain mesh peer status list
- Source distribution bars
- Live mesh API bridge: `GET /api/mesh/live` (served by Node host)

## Run on your local machine and Wi-Fi

From the repository root:

```powershell
./scripts/run-dashboard-node.ps1
```

First run installs dependencies in `dashboard-server/node_modules` automatically.

Optional custom port:

```powershell
./scripts/run-dashboard-node.ps1 -Port 8090
```

The script prints both URLs:

- Local: `http://localhost:<port>`
- LAN: `http://<your-local-ip>:<port>`

Open the LAN URL on any device connected to the same Wi-Fi network.

## Wire to live app / mesh data

1. Configure peers and secret in `dashboard/config/mesh-config.json`.
2. Set `enabled` to `true`.
3. Set `sharedSecret` to the exact Chain Shared Passphrase used in the app mesh settings.
4. Set `peers` to Argus device IPs on your LAN (example: `192.168.1.40`).
5. Restart `./scripts/run-dashboard-node.ps1`.

Notes:


## Notes

 API key source is local `local.properties` with fallback to `app/local.properties`.
 Key lookup order:
	1. `DASHBOARD_MAPS_API_KEY` (recommended for web dashboard)
	2. `MAPS_API_KEY` (app key fallback)
- Preferred launcher is Node/Fastify via `./scripts/run-dashboard-node.ps1` for better concurrency and reliability.
- Legacy launcher `./scripts/run-dashboard.ps1` is still available as a fallback.
- Dashboard no longer falls back to bundled sample geodata. If live feed is unavailable, it shows loading/error state explicitly.
If your Android app key is restricted to Android package/SHA-1, it can work in-app but fail in browser JavaScript maps. Use a separate browser-allowed key via `DASHBOARD_MAPS_API_KEY`.
