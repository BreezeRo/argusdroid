# Capabilities and Limits on Android Phones

## Works on stock Android (with permissions)

- Wi-Fi network observation (BSSID, SSID, channel/frequency, RSSI)
- Bluetooth LE environment discovery
- Basic telemetry aggregation over time

## Partially available / device dependent

- Cell metadata richness and refresh rates vary by Android version/OEM.
- Background scan behavior is power-policy constrained.
- Fine-grained location and scan cadence depend on user grants and policy.

## Not universally available on stock Android

- Arbitrary RF spectrum scanning outside exposed radio stacks.
- Universal drone Remote ID decoder API across all devices.

## Remote ID status in this app

- BLE-based Remote ID decoding is implemented as best-effort parsing from service/manufacturer payloads.
- External decoder ingest is supported through a companion broadcast path into app-local JSONL feed storage.
- Decoder coverage is partial and depends on payload visibility, format, and OEM BLE behavior.

## Path to advanced detection

- Integrate external SDR hardware over USB OTG for broad RF detection.
- Add parsers for Remote ID standards (e.g., ASTM F3411) from captured payloads.
- Implement adaptive scanning policies by battery, charging state, and threat profile.
