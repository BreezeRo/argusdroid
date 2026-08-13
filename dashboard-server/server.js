const fs = require('node:fs');
const fsp = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const Fastify = require('fastify');
const fastifyStatic = require('@fastify/static');

const args = process.argv.slice(2);
const portArgIndex = args.findIndex((a) => a === '--port');
const port = portArgIndex >= 0 ? Number(args[portArgIndex + 1]) : Number(process.env.PORT || 8091);
const configArgIndex = args.findIndex((a) => a === '--config');
const explicitConfig = configArgIndex >= 0 ? args[configArgIndex + 1] : '';

const repoRoot = path.resolve(__dirname, '..');
const dashboardRoot = path.join(repoRoot, 'dashboard');
const meshConfigPath = explicitConfig || path.join(dashboardRoot, 'config', 'mesh-config.json');

const cacheState = {
  snapshot: {
    generatedAt: new Date().toISOString(),
    mesh: { discoveredPeers: 0, connectedPeers: 0, peers: [] },
    encounters: [],
    meta: { live: false, reason: 'Snapshot cache warming up' }
  },
  generatedAtMs: 0,
  ttlMs: 15000,
  buildPromise: null
};

function getLanIpCandidates() {
  const interfaces = os.networkInterfaces();
  const ips = [];
  for (const [name, records] of Object.entries(interfaces)) {
    if (!records || /virtual|vmware|loopback|wsl|hyper-v/i.test(name)) {
      continue;
    }
    for (const r of records) {
      if (r.family === 'IPv4' && !r.internal && !r.address.startsWith('169.254.')) {
        ips.push(r.address);
      }
    }
  }
  return [...new Set(ips)];
}

async function getMeshConfig(cfgPath) {
  const fallback = {
    enabled: false,
    requesterNodeId: `dashboard-${os.hostname()}`,
    sharedSecret: '',
    syncWindowMinutes: 120,
    peers: []
  };

  try {
    const raw = await fsp.readFile(cfgPath, 'utf8');
    const cfg = JSON.parse(raw);
    return {
      enabled: Boolean(cfg.enabled),
      requesterNodeId: String(cfg.requesterNodeId || fallback.requesterNodeId),
      sharedSecret: String(cfg.sharedSecret || ''),
      syncWindowMinutes: Number(cfg.syncWindowMinutes || 120),
      peers: Array.isArray(cfg.peers) ? cfg.peers : []
    };
  } catch {
    return fallback;
  }
}

async function getMapsApiKey(rootPath) {
  const candidates = [
    path.join(rootPath, 'local.properties'),
    path.join(rootPath, 'app', 'local.properties')
  ];

  for (const p of candidates) {
    if (!fs.existsSync(p)) {
      continue;
    }

    try {
      const lines = (await fsp.readFile(p, 'utf8')).split(/\r?\n/);
      for (const key of ['DASHBOARD_MAPS_API_KEY', 'MAPS_API_KEY']) {
        const found = lines.find((line) => new RegExp(`^\\s*${key}\\s*=`).test(line));
        if (!found) {
          continue;
        }
        const value = found.split('=').slice(1).join('=').trim();
        if (value) {
          return value;
        }
      }
    } catch {
      continue;
    }
  }

  return '';
}

function newHmacSha256Hex(secret, payload) {
  return crypto.createHmac('sha256', Buffer.from(secret, 'utf8')).update(Buffer.from(payload, 'utf8')).digest('hex');
}

function newAuthHeaders(sharedSecret, nodeId, method, routePath, body) {
  const timestampMs = Date.now();
  const canonical = `${method.toUpperCase()}\n${routePath}\n${nodeId}\n${timestampMs}\n${body}`;
  const signature = newHmacSha256Hex(sharedSecret, canonical);

  return {
    'X-Argus-Auth-Node': nodeId,
    'X-Argus-Auth-Timestamp-Ms': String(timestampMs),
    'X-Argus-Auth-Signature': signature,
    'Content-Type': 'application/json'
  };
}

async function fetchJsonWithTimeout(url, options = {}, timeoutMs = 1500) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, {
      ...options,
      signal: controller.signal
    });
    if (!res.ok) {
      throw new Error(`${res.status} ${res.statusText}`);
    }
    return await res.json();
  } finally {
    clearTimeout(timer);
  }
}

function estimateDistanceMeters(rssi) {
  if (rssi === null || rssi === undefined || Number.isNaN(Number(rssi))) {
    return 0;
  }
  const txPower = -59;
  const n = 2.2;
  const exp = (txPower - Number(rssi)) / (10 * n);
  const meters = Math.pow(10, exp);
  return Math.round(Math.max(0.5, Math.min(1200, meters)) * 10) / 10;
}

function getZoneLabel(lat, lng, peerLabel) {
  const latBucket = Math.floor(lat * 100) / 100;
  const lngBucket = Math.floor(lng * 100) / 100;
  return `${latBucket},${lngBucket} (${peerLabel})`;
}

function getSourceDisplayLabel(source, secondaryId) {
  switch (source) {
    case 'CELL':
      return secondaryId ? `CELL TOWER (${secondaryId})` : 'CELL TOWER';
    case 'WIFI_DIRECT':
      return 'WIFI DIRECT';
    case 'BLUETOOTH_LE':
      return 'BLUETOOTH LE';
    case 'BLUETOOTH_CLASSIC':
      return 'BLUETOOTH CLASSIC';
    case 'REMOTE_ID':
      return 'REMOTE ID';
    case 'UNKNOWN_RF':
      return 'UNKNOWN RF';
    default:
      return source;
  }
}

function toFiniteNumber(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function isValidLatLng(lat, lng) {
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
    return false;
  }
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
    return false;
  }
  // Treat null-island style values as invalid for device-detail map coordinates.
  if (Math.abs(lat) < 0.0001 && Math.abs(lng) < 0.0001) {
    return false;
  }
  return true;
}

function getPathValue(obj, pathExpr) {
  if (!obj) {
    return undefined;
  }
  const segments = pathExpr.split('.');
  let current = obj;
  for (const segment of segments) {
    if (current === null || current === undefined || typeof current !== 'object') {
      return undefined;
    }
    current = current[segment];
  }
  return current;
}

function pickCoordinatePair(objects, latPaths, lngPaths) {
  for (const obj of objects) {
    if (!obj || typeof obj !== 'object') {
      continue;
    }

    for (const latPath of latPaths) {
      for (const lngPath of lngPaths) {
        const lat = toFiniteNumber(getPathValue(obj, latPath));
        const lng = toFiniteNumber(getPathValue(obj, lngPath));
        if (lat !== null && lng !== null && isValidLatLng(lat, lng)) {
          return { lat, lng };
        }
      }
    }
  }

  return null;
}

function safeParseJson(raw) {
  if (!raw || typeof raw !== 'string') {
    return null;
  }
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

function extractMeshLocationDetails(enc) {
  const rawPayload = safeParseJson(enc?.rawPayloadJson);
  const objects = [enc, enc?.deviceDetails, enc?.details, rawPayload];

  const estimated = pickCoordinatePair(
    objects,
    ['estimatedLat', 'estimatedLocation.lat', 'estimated.location.lat', 'deviceLocationEstimate.lat'],
    ['estimatedLng', 'estimatedLon', 'estimatedLocation.lng', 'estimatedLocation.lon', 'estimated.location.lng', 'estimated.location.lon', 'deviceLocationEstimate.lng', 'deviceLocationEstimate.lon']
  );

  const device = pickCoordinatePair(
    objects,
    ['deviceLat', 'deviceLocation.lat', 'device.location.lat', 'bestLocation.lat'],
    ['deviceLng', 'deviceLon', 'deviceLocation.lng', 'deviceLocation.lon', 'device.location.lng', 'device.location.lon', 'bestLocation.lng', 'bestLocation.lon']
  );

  const details = pickCoordinatePair(
    objects,
    ['location.lat', 'coords.lat', 'latitude'],
    ['location.lng', 'location.lon', 'coords.lng', 'coords.lon', 'longitude']
  );

  const remoteId = pickCoordinatePair(
    objects,
    ['remoteIdLat', 'remoteIdDecoded.droneLat', 'droneLat'],
    ['remoteIdLng', 'remoteIdLon', 'remoteIdDecoded.droneLon', 'droneLon']
  );

  return {
    estimatedLat: estimated?.lat ?? null,
    estimatedLng: estimated?.lng ?? null,
    deviceLat: device?.lat ?? null,
    deviceLng: device?.lng ?? null,
    detailLat: details?.lat ?? null,
    detailLng: details?.lng ?? null,
    remoteIdLat: remoteId?.lat ?? null,
    remoteIdLng: remoteId?.lng ?? null
  };
}

async function buildLiveSnapshot() {
  const config = await getMeshConfig(meshConfigPath);
  if (!config.enabled) {
    return {
      generatedAt: new Date().toISOString(),
      mesh: { discoveredPeers: 0, connectedPeers: 0, peers: [] },
      encounters: [],
      meta: { live: false, reason: 'Live mesh mode disabled in config' }
    };
  }

  const sharedSecret = String(config.sharedSecret || '');
  const requesterNodeId = String(config.requesterNodeId || '').trim() || `dashboard-${os.hostname()}`;
  const hosts = [...new Set((config.peers || []).map((p) => String(p).trim()).filter(Boolean))];

  if (hosts.length === 0) {
    return {
      generatedAt: new Date().toISOString(),
      mesh: { discoveredPeers: 0, connectedPeers: 0, peers: [] },
      encounters: [],
      meta: { live: false, reason: 'No peers configured. Add peer IPs in dashboard/config/mesh-config.json' }
    };
  }

  const sinceMs = Date.now() - Math.max(1, Number(config.syncWindowMinutes || 120)) * 60 * 1000;
  const allEncounters = [];
  const peerRows = [];

  await Promise.all(hosts.map(async (peerHost) => {
    let hello;
    try {
      hello = await fetchJsonWithTimeout(`http://${peerHost}:18777/argus/v1/hello`, { method: 'GET' }, 900);
    } catch (err) {
      peerRows.push({
        name: peerHost,
        state: 'FAILED',
        lastSeen: `Unavailable: ${err.message}`
      });
      return;
    }

    const peerName = hello?.deviceName || hello?.nodeId || peerHost;

    if (!sharedSecret) {
      peerRows.push({
        name: peerName,
        state: 'DISCOVERED',
        lastSeen: 'Hello ok, waiting for shared secret'
      });
      return;
    }

    const reqObject = {
      requesterNodeId,
      sinceEpochMs: sinceMs,
      encounters: []
    };
    const reqBody = JSON.stringify(reqObject);
    const headers = newAuthHeaders(sharedSecret, requesterNodeId, 'POST', '/argus/v1/sync', reqBody);

    let sync;
    try {
      sync = await fetchJsonWithTimeout(
        `http://${peerHost}:18777/argus/v1/sync`,
        { method: 'POST', headers, body: reqBody },
        1400
      );
    } catch (err) {
      peerRows.push({
        name: peerName,
        state: 'FAILED',
        lastSeen: `Sync failed: ${err.message}`
      });
      return;
    }

    peerRows.push({
      name: peerName,
      state: 'CONNECTED',
      lastSeen: 'Synced just now'
    });

    for (const enc of sync?.encounters || []) {
      try {
        if (enc?.timestampEpochMs === null || enc?.timestampEpochMs === undefined) {
          continue;
        }

        const ts = Number(enc.timestampEpochMs);
        const iso = new Date(ts).toISOString();
        const label = enc.primaryId ? String(enc.primaryId) : 'unknown';
        const secondary = enc.secondaryId ? String(enc.secondaryId) : '';
        const source = enc.source ? String(enc.source) : 'UNKNOWN_RF';
        const signal = enc.rssiDbm !== null && enc.rssiDbm !== undefined ? Number(enc.rssiDbm) : -120;
        const detailLoc = extractMeshLocationDetails(enc);
        const observerLat = toFiniteNumber(enc.lat);
        const observerLng = toFiniteNumber(enc.lon);
        const remoteIdLat = detailLoc.remoteIdLat;
        const remoteIdLng = detailLoc.remoteIdLng;

        let effectiveLat = observerLat;
        let effectiveLng = observerLng;

        if (source === 'REMOTE_ID') {
          if (isValidLatLng(remoteIdLat, remoteIdLng)) {
            effectiveLat = remoteIdLat;
            effectiveLng = remoteIdLng;
          } else {
            effectiveLat = null;
            effectiveLng = null;
          }
        }

        const zoneLabel = isValidLatLng(effectiveLat, effectiveLng)
          ? getZoneLabel(effectiveLat, effectiveLng, peerHost)
          : `unknown (${peerHost})`;

        allEncounters.push({
          id: `${peerHost}:${source}:${enc.timestampEpochMs}`,
          timestamp: iso,
          source,
          sourceLabel: getSourceDisplayLabel(source, secondary),
          label,
          secondaryId: secondary,
          signalDbm: signal,
          frequencyMhz: enc.frequencyMhz !== null && enc.frequencyMhz !== undefined ? Number(enc.frequencyMhz) : null,
          rawPayloadJson: typeof enc.rawPayloadJson === 'string' ? enc.rawPayloadJson : '',
          distanceMeters: estimateDistanceMeters(enc.rssiDbm),
          lat: effectiveLat,
          lng: effectiveLng,
          observerLat,
          observerLng,
          estimatedLat: detailLoc.estimatedLat,
          estimatedLng: detailLoc.estimatedLng,
          deviceLat: detailLoc.deviceLat,
          deviceLng: detailLoc.deviceLng,
          detailLat: detailLoc.detailLat,
          detailLng: detailLoc.detailLng,
          remoteIdLat,
          remoteIdLng,
          zone: zoneLabel
        });
      } catch {
        continue;
      }
    }
  }));

  const connectedPeers = peerRows.filter((p) => p.state === 'CONNECTED').length;
  return {
    generatedAt: new Date().toISOString(),
    mesh: {
      discoveredPeers: peerRows.length,
      connectedPeers,
      peers: peerRows
    },
    encounters: allEncounters,
    meta: {
      live: true,
      requesterNodeId,
      peerCount: peerRows.length
    }
  };
}

async function getLiveSnapshotCached() {
  const now = Date.now();
  const ageMs = now - cacheState.generatedAtMs;
  if (cacheState.snapshot && ageMs < cacheState.ttlMs) {
    return cacheState.snapshot;
  }

  if (cacheState.buildPromise) {
    return cacheState.snapshot;
  }

  cacheState.buildPromise = buildLiveSnapshot()
    .then((fresh) => {
      cacheState.snapshot = fresh;
      cacheState.generatedAtMs = Date.now();
      return fresh;
    })
    .catch(() => cacheState.snapshot)
    .finally(() => {
      cacheState.buildPromise = null;
    });

  return cacheState.buildPromise;
}

async function start() {
  const app = Fastify({
    logger: false,
    bodyLimit: 1024 * 1024
  });

  await app.register(fastifyStatic, {
    root: dashboardRoot,
    prefix: '/',
    index: ['index.html']
  });

  app.get('/api/dashboard-config', async () => {
    const apiKey = await getMapsApiKey(repoRoot);
    return { googleMapsApiKey: apiKey };
  });

  app.get('/api/mesh/live', async () => {
    try {
      return await getLiveSnapshotCached();
    } catch (err) {
      return {
        generatedAt: new Date().toISOString(),
        mesh: { discoveredPeers: 0, connectedPeers: 0, peers: [] },
        encounters: [],
        meta: {
          live: false,
          reason: `Live API error: ${err.message}`
        }
      };
    }
  });

  app.setNotFoundHandler(async (_req, reply) => {
    reply.code(404).type('text/plain').send('404 Not Found');
  });

  await app.listen({ host: '0.0.0.0', port });

  const lanIps = getLanIpCandidates();
  console.log('Serving dashboard in view-only mode (Node/Fastify)');
  console.log(`Local URL:   http://localhost:${port}`);
  if (lanIps.length > 0) {
    console.log('LAN URL(s):');
    lanIps.forEach((ip) => console.log(`  http://${ip}:${port}`));
  } else {
    console.log('LAN URL:     Unable to detect a LAN IP automatically');
  }
  console.log('Live API:    GET /api/mesh/live');
  console.log(`Config file: ${meshConfigPath}`);
  console.log('Press Ctrl+C to stop');

  const shutdown = async () => {
    await app.close();
    process.exit(0);
  };

  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

start().catch((err) => {
  console.error(err);
  process.exit(1);
});
