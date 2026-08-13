const SOURCE_DEFS = [
  { key: "WIFI", label: "WIFI", color: "#27c29a" },
  { key: "WIFI_DIRECT", label: "WIFI DIRECT", color: "#1bbd72" },
  { key: "BLUETOOTH_LE", label: "BLUETOOTH LE", color: "#ff8f57" },
  { key: "BLUETOOTH_CLASSIC", label: "BLUETOOTH CLASSIC", color: "#ff6f43" },
  { key: "REMOTE_ID", label: "REMOTE ID", color: "#ffd85e" },
  { key: "CELL", label: "CELL TOWER", color: "#6dc3ff" },
  { key: "UWB", label: "UWB", color: "#ff5f88" },
  { key: "SDR", label: "SDR", color: "#9f87ff" },
  { key: "UNKNOWN_RF", label: "UNKNOWN RF", color: "#a6aeb0" }
];

const SOURCE_BY_KEY = Object.fromEntries(SOURCE_DEFS.map((item) => [item.key, item]));

const state = {
  data: null,
  config: null,
  enabledSources: new Set(SOURCE_DEFS.map((item) => item.key)),
  activeMapTab: "encounters",
  map: null,
  hotspotMap: null,
  deviceMap: null,
  markers: [],
  hotspotCircles: [],
  deviceMarkers: [],
  deviceIconCache: new Map(),
  hotspotCircleInfoWindow: null,
  infoWindow: null,
  deviceInfoWindow: null,
  autoRefreshTimer: null,
  trafficLayer: null,
  mapTypeIndex: 0,
  lastBounds: null,
  mapPinLimit: 250,
  mapLocationMode: "PRECISE"
};

const MAP_TYPES = ["roadmap", "hybrid", "terrain", "satellite"];
const PIN_LIMIT_OPTIONS = [100, 250, 500, 1000, 0];
const LOCATION_MODES = ["PRECISE", "ZONED"];

bootstrap();

async function bootstrap() {
  state.config = await fetchDashboardConfig();
  state.data = await fetchDashboardData();

  renderFilters();
  renderAll();

  try {
    await loadGoogleMaps(state.config?.googleMapsApiKey || "");
    initMaps();
    initMapTabs();
    renderMapControls();
    drawMarkers();
    drawHotspotRegions();
    drawDeviceLocations();
  } catch (error) {
    console.error("Google Maps failed to initialize", error);
  }

  state.autoRefreshTimer = setInterval(async () => {
    state.data = await fetchDashboardData();
    renderAll();
    if (state.map) {
      drawMarkers();
      drawHotspotRegions();
      drawDeviceLocations();
    }
  }, 15000);
}

async function fetchDashboardConfig() {
  try {
    const response = await fetch("/api/dashboard-config", { cache: "no-store" });
    if (!response.ok) {
      return { googleMapsApiKey: "" };
    }
    return await response.json();
  } catch (_error) {
    return { googleMapsApiKey: "" };
  }
}

function loadGoogleMaps(apiKey) {
  return new Promise((resolve, reject) => {
    if (!apiKey) {
      reject(new Error("Missing Google Maps API key in local.properties"));
      return;
    }

    if (window.google?.maps) {
      resolve();
      return;
    }

    const existing = document.querySelector("script[data-google-maps='1']");
    if (existing) {
      existing.addEventListener("load", () => resolve());
      existing.addEventListener("error", () => reject(new Error("Google Maps failed to load")));
      return;
    }

    const script = document.createElement("script");
    const timeoutId = setTimeout(() => {
      reject(new Error("Google Maps load timed out"));
    }, 8000);

    script.setAttribute("data-google-maps", "1");
    script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(apiKey)}`;
    script.async = true;
    script.defer = true;
    script.onload = () => {
      clearTimeout(timeoutId);
      resolve();
    };
    script.onerror = () => {
      clearTimeout(timeoutId);
      reject(new Error("Google Maps failed to load"));
    };
    document.head.appendChild(script);
  });
}

async function fetchDashboardData() {
  try {
    const live = await fetchWithTimeout("/api/mesh/live", { cache: "no-store" }, 7000);
    if (!live.ok) {
      throw new Error(`Live API failed with ${live.status}`);
    }
    const payload = await live.json();
    if (payload.meta?.live === true) {
      return payload;
    }
    if (Array.isArray(payload.encounters) && payload.encounters.length > 0) {
      return payload;
    }
  } catch (_error) {
    // Fall through to sample data.
  }

  const sample = await fetchWithTimeout("data/sample-encounters.json", { cache: "no-store" }, 5000);
  return sample.json();
}

async function fetchWithTimeout(url, options, timeoutMs) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, {
      ...options,
      signal: controller.signal
    });
  } finally {
    clearTimeout(timeoutId);
  }
}

function renderAll() {
  const encounters = getFilteredEncounters();
  renderKpis(encounters, state.data.mesh);
  renderLatestTable(encounters);
  renderHotspots(encounters);
  renderSourceBars(encounters);
  renderPeers(state.data.mesh);
}

function getFilteredEncounters() {
  return state.data.encounters.filter((e) => state.enabledSources.has(e.source));
}

function renderFilters() {
  const host = document.getElementById("source-filters");
  host.innerHTML = "";

  SOURCE_DEFS.forEach((sourceDef) => {
    const chip = document.createElement("label");
    chip.className = "filter-chip";
    chip.innerHTML = `
      <input type="checkbox" checked data-source="${sourceDef.key}" />
      <span>${sourceDef.label}</span>
    `;
    chip.querySelector("input").addEventListener("change", (event) => {
      if (event.target.checked) {
        state.enabledSources.add(sourceDef.key);
      } else {
        state.enabledSources.delete(sourceDef.key);
      }
      renderAll();
      drawMarkers();
      drawHotspotRegions();
      drawDeviceLocations();
    });
    host.appendChild(chip);
  });
}

function renderKpis(encounters, mesh) {
  const now = Date.now();
  const oneDayMs = 24 * 60 * 60 * 1000;
  const recentCount = encounters.filter((e) => now - Date.parse(e.timestamp) < oneDayMs).length;
  const strongSignal = encounters.filter((e) => e.signalDbm >= -65).length;
  const nearest = encounters.length
    ? Math.min(...encounters.map((e) => e.distanceMeters))
    : 0;

  const cards = [
    { label: "Visible Encounters", value: encounters.length },
    { label: "Last 24 Hours", value: recentCount },
    { label: "Strong Signals", value: strongSignal },
    { label: "Nearest Range", value: `${nearest.toFixed(1)} m` },
    { label: "Peers Connected", value: mesh.connectedPeers },
    { label: "Peers Discovered", value: mesh.discoveredPeers }
  ];

  document.getElementById("kpi-cards").innerHTML = cards
    .map(
      (card) => `
      <div class="kpi">
        <div class="label">${card.label}</div>
        <div class="value">${card.value}</div>
      </div>
    `
    )
    .join("");
}

function renderLatestTable(encounters) {
  const body = document.getElementById("encounter-rows");
  const rows = [...encounters]
    .sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp))
    .slice(0, 16)
    .map(
      (e) => `
      <tr>
        <td>${formatTime(e.timestamp)}</td>
        <td>${displaySourceForEncounter(e)}</td>
        <td>${e.label}</td>
        <td>${e.signalDbm} dBm</td>
        <td>${e.distanceMeters.toFixed(1)} m</td>
      </tr>
    `
    )
    .join("");

  body.innerHTML = rows || "<tr><td colspan=\"5\">No encounters for the selected sources.</td></tr>";
}

function renderHotspots(encounters) {
  const grouped = new Map();

  encounters.forEach((e) => {
    const key = e.zone;
    const current = grouped.get(key) || { count: 0, strongest: -999 };
    current.count += 1;
    current.strongest = Math.max(current.strongest, e.signalDbm);
    grouped.set(key, current);
  });

  const items = [...grouped.entries()]
    .map(([zone, stats]) => ({ zone, ...stats }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 8)
    .map(
      (item) => `
      <li class="stack-item">
        <div>
          <strong>${item.zone}</strong>
          <div class="small">Strongest signal: ${item.strongest} dBm</div>
        </div>
        <div>${item.count}</div>
      </li>
    `
    )
    .join("");

  document.getElementById("hotspot-list").innerHTML = items || "<li class=\"small\">No hotspot data.</li>";
}

function renderSourceBars(encounters) {
  const totals = {};
  SOURCE_DEFS.forEach((sourceDef) => {
    totals[sourceDef.key] = 0;
  });

  encounters.forEach((e) => {
    const key = e.source || "UNKNOWN_RF";
    totals[key] = (totals[key] || 0) + 1;
  });

  if (Object.keys(totals).length === 0) {
    totals.UNKNOWN_RF = 0;
  }

  const max = Math.max(1, ...Object.values(totals));
  const markup = Object.entries(totals)
    .map(
      ([name, value]) => `
      <div class="bar-row">
        <div>${labelForSource(name)}</div>
        <div class="track">
          <div class="fill" style="width:${(value / max) * 100}%; background:${colorForSource(name)}"></div>
        </div>
        <div>${value}</div>
      </div>
    `
    )
    .join("");

  document.getElementById("source-bars").innerHTML = markup;
}

function renderPeers(mesh) {
  const items = mesh.peers
    .map(
      (peer) => `
      <li class="stack-item">
        <div>
          <strong>${peer.name}</strong>
          <div class="small">${peer.lastSeen}</div>
        </div>
        <div>${peer.state}</div>
      </li>
    `
    )
    .join("");

  document.getElementById("peer-list").innerHTML = items;
}

function initMaps() {
  const center = state.data.encounters?.[0]
    ? { lat: state.data.encounters[0].lat, lng: state.data.encounters[0].lng }
    : { lat: 37.7749, lng: -122.4194 };

  state.map = new google.maps.Map(document.getElementById("map"), {
    center,
    zoom: 12,
    zoomControl: true,
    zoomControlOptions: {
      position: google.maps.ControlPosition.RIGHT_BOTTOM
    },
    mapTypeControl: true,
    streetViewControl: false,
    fullscreenControl: true
  });

  state.infoWindow = new google.maps.InfoWindow();
  state.hotspotMap = new google.maps.Map(document.getElementById("hotspot-map"), {
    center,
    zoom: 11,
    zoomControl: true,
    mapTypeControl: true,
    streetViewControl: false,
    fullscreenControl: true
  });

  state.deviceMap = new google.maps.Map(document.getElementById("device-map"), {
    center,
    zoom: 12,
    zoomControl: true,
    mapTypeControl: true,
    streetViewControl: false,
    fullscreenControl: true
  });

  state.hotspotCircleInfoWindow = new google.maps.InfoWindow();
  state.deviceInfoWindow = new google.maps.InfoWindow();
  state.trafficLayer = new google.maps.TrafficLayer();
}

function initMapTabs() {
  const buttons = document.querySelectorAll("[data-map-tab]");
  buttons.forEach((button) => {
    button.addEventListener("click", () => {
      activateMapTab(button.dataset.mapTab);
    });
  });
}

function activateMapTab(tabName) {
  if (tabName === "hotspots") {
    state.activeMapTab = "hotspots";
  } else if (tabName === "device-locations") {
    state.activeMapTab = "device-locations";
  } else {
    state.activeMapTab = "encounters";
  }

  document.querySelectorAll("[data-map-tab]").forEach((button) => {
    const isActive = button.dataset.mapTab === state.activeMapTab;
    button.classList.toggle("active", isActive);
    button.setAttribute("aria-selected", isActive ? "true" : "false");
  });

  const encounterPane = document.getElementById("map-pane-encounters");
  const hotspotPane = document.getElementById("map-pane-hotspots");
  const devicePane = document.getElementById("map-pane-device-locations");
  const showingHotspots = state.activeMapTab === "hotspots";
  const showingDeviceMap = state.activeMapTab === "device-locations";

  encounterPane.classList.toggle("active", !showingHotspots && !showingDeviceMap);
  encounterPane.hidden = showingHotspots || showingDeviceMap;
  hotspotPane.classList.toggle("active", showingHotspots);
  hotspotPane.hidden = !showingHotspots;
  devicePane.classList.toggle("active", showingDeviceMap);
  devicePane.hidden = !showingDeviceMap;

  setTimeout(() => {
    if (!window.google?.maps) {
      return;
    }
    if (showingHotspots) {
      google.maps.event.trigger(state.hotspotMap, "resize");
      drawHotspotRegions();
    } else if (showingDeviceMap) {
      google.maps.event.trigger(state.deviceMap, "resize");
      drawDeviceLocations();
    } else {
      google.maps.event.trigger(state.map, "resize");
      drawMarkers();
    }
  }, 40);
}

function renderMapControls() {
  const host = document.getElementById("map-controls");
  host.innerHTML = "";

  const controls = [
    {
      id: "zoom-in",
      label: "Zoom +",
      onClick: () => {
        const current = state.map.getZoom() || 12;
        state.map.setZoom(current + 1);
      }
    },
    {
      id: "zoom-out",
      label: "Zoom -",
      onClick: () => {
        const current = state.map.getZoom() || 12;
        state.map.setZoom(current - 1);
      }
    },
    {
      id: "fit",
      label: "Fit Markers",
      onClick: () => {
        if (state.lastBounds) {
          state.map.fitBounds(state.lastBounds);
        }
      }
    },
    {
      id: "latest",
      label: "Center Latest",
      onClick: () => {
        const latest = [...getFilteredEncounters()]
          .sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp))[0];
        if (latest) {
          state.map.setCenter({ lat: latest.lat, lng: latest.lng });
          state.map.setZoom(16);
        }
      }
    },
    {
      id: "type",
      label: `Map: ${MAP_TYPES[state.mapTypeIndex]}`,
      onClick: () => {
        state.mapTypeIndex = (state.mapTypeIndex + 1) % MAP_TYPES.length;
        const type = MAP_TYPES[state.mapTypeIndex];
        state.map.setMapTypeId(type);
        renderMapControls();
      }
    },
    {
      id: "traffic",
      label: "Traffic",
      isToggle: true,
      active: !!state.trafficLayer?.getMap(),
      onClick: () => {
        const showing = !!state.trafficLayer.getMap();
        state.trafficLayer.setMap(showing ? null : state.map);
        renderMapControls();
      }
    },
    {
      id: "pin-limit",
      label: `Pins: ${state.mapPinLimit === 0 ? "All" : state.mapPinLimit}`,
      onClick: () => {
        const currentIndex = PIN_LIMIT_OPTIONS.indexOf(state.mapPinLimit);
        const nextIndex = currentIndex >= 0 ? (currentIndex + 1) % PIN_LIMIT_OPTIONS.length : 0;
        state.mapPinLimit = PIN_LIMIT_OPTIONS[nextIndex];
        renderMapControls();
        drawMarkers();
      }
    },
    {
      id: "location-mode",
      label: `Loc: ${state.mapLocationMode === "ZONED" ? "Zoned" : "Estimated Precise"}`,
      onClick: () => {
        const currentIndex = LOCATION_MODES.indexOf(state.mapLocationMode);
        const nextIndex = currentIndex >= 0 ? (currentIndex + 1) % LOCATION_MODES.length : 0;
        state.mapLocationMode = LOCATION_MODES[nextIndex];
        renderMapControls();
        drawMarkers();
      }
    }
  ];

  controls.forEach((control) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `map-control-btn${control.active ? " active" : ""}`;
    button.textContent = control.label;
    button.addEventListener("click", control.onClick);
    host.appendChild(button);
  });
}

function drawMarkers() {
  if (!state.map || !state.data || !window.google?.maps) {
    return;
  }

  state.markers.forEach((marker) => marker.setMap(null));
  state.markers = [];

  const encounters = getMapRenderEncounters();
  const bounds = new google.maps.LatLngBounds();

  encounters.forEach((e) => {
    const point = getMarkerPosition(e);
    if (!point) {
      return;
    }

    const marker = new google.maps.Marker({
      position: point,
      map: state.map,
      title: e.label,
      icon: {
        path: google.maps.SymbolPath.CIRCLE,
        scale: 7,
        fillColor: colorForSource(e.source),
        fillOpacity: 0.9,
        strokeColor: "#102122",
        strokeWeight: 1
      }
    });

    marker.addListener("click", () => {
      state.infoWindow.setContent(buildInfoWindowHtml(e));
      state.infoWindow.open({ map: state.map, anchor: marker });
    });

    state.markers.push(marker);
    bounds.extend(marker.getPosition());
  });

  if (encounters.length > 1) {
    state.lastBounds = bounds;
    state.map.fitBounds(bounds);
  } else if (encounters.length === 1) {
    state.lastBounds = bounds;
    state.map.setCenter(bounds.getCenter());
    state.map.setZoom(15);
  } else {
    state.lastBounds = null;
  }
}

function getMapRenderEncounters() {
  const encounters = [...getFilteredEncounters()].sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp));
  if (state.mapPinLimit === 0) {
    return encounters;
  }
  return encounters.slice(0, state.mapPinLimit);
}

function drawHotspotRegions() {
  if (!state.hotspotMap || !state.data || !window.google?.maps) {
    return;
  }

  state.hotspotCircles.forEach((circle) => circle.setMap(null));
  state.hotspotCircles = [];

  const regions = buildHotspotRegions(getFilteredEncounters());
  const bounds = new google.maps.LatLngBounds();

  regions.forEach((region) => {
    const strokeColor = colorForSource(region.primarySource);
    const radiusMeters = Math.min(1600, Math.max(70, (region.maxDistanceMeters || 0) + 30));

    const circle = new google.maps.Circle({
      map: state.hotspotMap,
      center: region.center,
      radius: radiusMeters,
      strokeColor,
      strokeOpacity: 0.95,
      strokeWeight: 2,
      fillColor: strokeColor,
      fillOpacity: 0.22
    });

    circle.addListener("click", () => {
      const content = `
        <div style="color:#111; font-family:Arial,sans-serif; line-height:1.35; min-width:220px;">
          <strong>Regional Hotspot</strong><br/>
          <span><strong>Zone:</strong> ${region.zoneLabel}</span><br/>
          <span><strong>Encounters:</strong> ${region.count}</span><br/>
          <span><strong>Strongest:</strong> ${region.strongestSignal} dBm</span><br/>
          <span><strong>Primary Source:</strong> ${labelForSource(region.primarySource)}</span>
        </div>
      `;
      state.hotspotCircleInfoWindow.setContent(content);
      state.hotspotCircleInfoWindow.setPosition(region.center);
      state.hotspotCircleInfoWindow.open({ map: state.hotspotMap });
    });

    state.hotspotCircles.push(circle);
    bounds.extend(region.center);
  });

  if (regions.length > 1) {
    state.hotspotMap.fitBounds(bounds);
  } else if (regions.length === 1) {
    state.hotspotMap.setCenter(regions[0].center);
    state.hotspotMap.setZoom(12);
  }
}

function buildHotspotRegions(encounters) {
  const grouped = new Map();

  encounters.forEach((encounter) => {
    const center = getZonedPosition(encounter);
    if (!center) {
      return;
    }

    const key = `${center.lat.toFixed(4)},${center.lng.toFixed(4)}`;
    const current = grouped.get(key) || {
      count: 0,
      strongestSignal: -999,
      sourceCounts: {},
      center,
      maxDistanceMeters: 0,
      zoneLabel: encounter.zone || `${center.lat.toFixed(4)},${center.lng.toFixed(4)}`
    };

    current.count += 1;
    current.strongestSignal = Math.max(current.strongestSignal, Number(encounter.signalDbm) || -999);
    current.sourceCounts[encounter.source] = (current.sourceCounts[encounter.source] || 0) + 1;

    const precise = getPrecisePosition(encounter);
    if (precise) {
      const dist = haversineMeters(center.lat, center.lng, precise.lat, precise.lng);
      if (dist > current.maxDistanceMeters) {
        current.maxDistanceMeters = dist;
      }
    }

    grouped.set(key, current);
  });

  return [...grouped.values()]
    .map((region) => {
      const primarySource = Object.entries(region.sourceCounts)
        .sort((a, b) => b[1] - a[1])[0]?.[0] || "UNKNOWN_RF";
      return { ...region, primarySource };
    })
    .sort((a, b) => b.count - a.count)
    .slice(0, 80);
}

function haversineMeters(lat1, lng1, lat2, lng2) {
  const toRad = Math.PI / 180;
  const dLat = (lat2 - lat1) * toRad;
  const dLng = (lng2 - lng1) * toRad;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * toRad) * Math.cos(lat2 * toRad) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return 6371000 * c;
}

function drawDeviceLocations() {
  if (!state.deviceMap || !state.data || !window.google?.maps) {
    return;
  }

  state.deviceMarkers.forEach((marker) => marker.setMap(null));
  state.deviceMarkers = [];

  const deviceRows = buildLatestDeviceRows();
  const noteEl = document.querySelector("#map-pane-device-locations .map-pane-note");
  if (noteEl) {
    if (deviceRows.length === 0) {
      noteEl.textContent = "No usable device locations are currently available from mesh detail fields or inferred history.";
    } else {
      noteEl.textContent = "Latest known location per unique device identifier using mesh detail coordinates first, then inferred multi-sample history.";
    }
  }
  const bounds = new google.maps.LatLngBounds();

  deviceRows.forEach((row) => {
    const markerIcon = getDeviceTypeIcon(row.encounter);
    const marker = new google.maps.Marker({
      position: row.position,
      map: state.deviceMap,
      title: row.encounter.label,
      icon: markerIcon
    });

    marker.addListener("click", () => {
      const content = `
        <div style="color:#111; font-family:Arial,sans-serif; line-height:1.35; min-width:220px;">
          <strong>${row.encounter.label}</strong><br/>
          <span><strong>Source:</strong> ${displaySourceForEncounter(row.encounter)}</span><br/>
          <span><strong>Location Basis:</strong> ${row.locationBasis}</span><br/>
          <span><strong>Signal:</strong> ${row.encounter.signalDbm} dBm</span><br/>
          <span><strong>Time:</strong> ${new Date(row.encounter.timestamp).toLocaleString()}</span>
        </div>
      `;
      state.deviceInfoWindow.setContent(content);
      state.deviceInfoWindow.open({ map: state.deviceMap, anchor: marker });
    });

    state.deviceMarkers.push(marker);
    bounds.extend(row.position);
  });

  if (deviceRows.length > 1) {
    state.deviceMap.fitBounds(bounds);
  } else if (deviceRows.length === 1) {
    state.deviceMap.setCenter(deviceRows[0].position);
    state.deviceMap.setZoom(15);
  }
}

function buildLatestDeviceRows() {
  const encounters = [...getFilteredEncounters()].sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp));
  const grouped = new Map();

  for (const encounter of encounters) {
    const key = `${encounter.source}|${encounter.label || "unknown"}`;
    if (!grouped.has(key)) {
      grouped.set(key, []);
    }
    grouped.get(key).push(encounter);
  }

  const rows = [];
  for (const [deviceKey, deviceEncounters] of grouped.entries()) {
    const latest = deviceEncounters[0];
    const source = String(latest.source || "UNKNOWN_RF");
    const resolved = resolveDeviceLocationLikeAndroid(source, deviceEncounters);
    if (!resolved) {
      continue;
    }

    rows.push({
      deviceKey,
      encounter: latest,
      position: resolved.position,
      locationBasis: resolved.basis
    });
  }

  rows.sort((a, b) => Date.parse(b.encounter.timestamp) - Date.parse(a.encounter.timestamp));
  const spreadRows = spreadOverlappingDeviceRows(rows);

  if (state.mapPinLimit === 0) {
    return spreadRows;
  }
  return spreadRows.slice(0, state.mapPinLimit);
}

function resolveDeviceLocationLikeAndroid(source, encounters) {
  const latest = encounters[0];

  if (source === "WIFI" || source === "BLUETOOTH_LE") {
    const inferred = inferLikelyDeviceLocation(encounters, source);
    if (inferred) {
      return { position: inferred, basis: "inferred" };
    }
    const observed = getObservedEncounterLocation(latest);
    if (observed) {
      return { position: observed, basis: "observed" };
    }
    return null;
  }

  if (source === "CELL") {
    const detailPosition = getDeviceMapPosition(latest);
    if (detailPosition) {
      return { position: detailPosition.position, basis: detailPosition.basis };
    }
    const observed = getObservedEncounterLocation(latest);
    if (observed) {
      return { position: observed, basis: "observed" };
    }
    return null;
  }

  const detailPosition = getDeviceMapPosition(latest);
  if (detailPosition) {
    return { position: detailPosition.position, basis: detailPosition.basis };
  }
  const observed = getObservedEncounterLocation(latest);
  if (observed) {
    return { position: observed, basis: "observed" };
  }
  return null;
}

function getDeviceMapPosition(encounter) {
  const candidates = [
    { lat: encounter.estimatedLat, lng: encounter.estimatedLng, basis: "estimated" },
    { lat: encounter.estimatedLat, lng: encounter.estimatedLon, basis: "estimated" },
    { lat: encounter.deviceLat, lng: encounter.deviceLng, basis: "device" },
    { lat: encounter.deviceLat, lng: encounter.deviceLon, basis: "device" },
    { lat: encounter.detailLat, lng: encounter.detailLng, basis: "details" },
    { lat: encounter.detailLat, lng: encounter.detailLon, basis: "details" }
  ];

  for (const item of candidates) {
    const lat = Number(item.lat);
    const lng = Number(item.lng);
    if (isValidLatLng(lat, lng)) {
      return { position: { lat, lng }, basis: item.basis };
    }
  }

  return null;
}

function getObservedEncounterLocation(encounter) {
  const lat = Number(encounter?.lat);
  const lng = Number(encounter?.lng);
  if (!isValidLatLng(lat, lng)) {
    return null;
  }
  return { lat, lng };
}

function isValidLatLng(lat, lng) {
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
    return false;
  }
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
    return false;
  }
  if (Math.abs(lat) < 0.0001 && Math.abs(lng) < 0.0001) {
    return false;
  }
  return true;
}

function inferDeviceLocationFromEncounters(encounters) {
  const samples = encounters
    .slice(0, 16)
    .map((encounter) => {
      const lat = Number(encounter.lat);
      const lng = Number(encounter.lng);
      if (!isValidLatLng(lat, lng)) {
        return null;
      }

      const signal = Number(encounter.signalDbm);
      const weight = Number.isFinite(signal) ? Math.max(1, 140 + signal) : 4;
      return { lat, lng, weight };
    })
    .filter(Boolean);

  if (samples.length === 0) {
    return null;
  }

  let latSum = 0;
  let lngSum = 0;
  let weightSum = 0;
  for (const sample of samples) {
    latSum += sample.lat * sample.weight;
    lngSum += sample.lng * sample.weight;
    weightSum += sample.weight;
  }

  if (weightSum <= 0) {
    return null;
  }

  const lat = latSum / weightSum;
  const lng = lngSum / weightSum;
  if (!isValidLatLng(lat, lng)) {
    return null;
  }

  return { lat, lng };
}

function inferLikelyDeviceLocation(encounters, source) {
  const observations = encounters
    .map((encounter) => {
      const lat = Number(encounter.lat);
      const lng = Number(encounter.lng);
      if (!isValidLatLng(lat, lng)) {
        return null;
      }

      const rangeMeters = estimateRangeMeters(encounter, source);
      if (!Number.isFinite(rangeMeters) || rangeMeters <= 0) {
        return null;
      }

      return { lat, lng, rangeMeters };
    })
    .filter(Boolean);

  if (observations.length === 0) {
    return null;
  }

  if (observations.length === 1) {
    return { lat: observations[0].lat, lng: observations[0].lng };
  }

  const refLat = observations.reduce((sum, item) => sum + item.lat, 0) / observations.length;
  const refLng = observations.reduce((sum, item) => sum + item.lng, 0) / observations.length;
  const refLatRad = (refLat * Math.PI) / 180;
  const metersPerDegLat = 111132;
  const metersPerDegLng = 111320 * Math.cos(refLatRad);
  if (!Number.isFinite(metersPerDegLng) || Math.abs(metersPerDegLng) < 1e-6) {
    return null;
  }

  const localObs = observations.map((obs) => ({
    x: (obs.lng - refLng) * metersPerDegLng,
    y: (obs.lat - refLat) * metersPerDegLat,
    range: obs.rangeMeters,
    weight: 1 / Math.max(1, obs.rangeMeters)
  }));

  const objective = (x, y) => {
    let total = 0;
    for (const obs of localObs) {
      const dx = x - obs.x;
      const dy = y - obs.y;
      const dist = Math.sqrt(dx * dx + dy * dy);
      const err = dist - obs.range;
      total += obs.weight * err * err;
    }
    return total;
  };

  const weightTotal = localObs.reduce((sum, obs) => sum + obs.weight, 0);
  let x = weightTotal > 0 ? localObs.reduce((sum, obs) => sum + obs.x * obs.weight, 0) / weightTotal : 0;
  let y = weightTotal > 0 ? localObs.reduce((sum, obs) => sum + obs.y * obs.weight, 0) / weightTotal : 0;
  let step = Math.max(
    20,
    localObs.reduce((sum, obs) => sum + obs.range, 0) / Math.max(1, localObs.length)
  );
  let bestScore = objective(x, y);

  for (let i = 0; i < 80; i += 1) {
    const candidates = [
      [x, y],
      [x + step, y],
      [x - step, y],
      [x, y + step],
      [x, y - step],
      [x + step, y + step],
      [x + step, y - step],
      [x - step, y + step],
      [x - step, y - step]
    ];

    let moved = false;
    for (const [cx, cy] of candidates) {
      const score = objective(cx, cy);
      if (score < bestScore) {
        bestScore = score;
        x = cx;
        y = cy;
        moved = true;
      }
    }

    step = moved ? step * 0.92 : step * 0.7;
    if (step < 1) {
      break;
    }
  }

  const inferredLat = refLat + y / metersPerDegLat;
  const inferredLng = refLng + x / metersPerDegLng;
  if (!isValidLatLng(inferredLat, inferredLng)) {
    return null;
  }

  return { lat: inferredLat, lng: inferredLng };
}

function estimateRangeMeters(encounter, source) {
  if (source === "WIFI") {
    return estimateWifiRangeMeters(encounter);
  }
  if (source === "BLUETOOTH_LE") {
    return estimateBleRangeMeters(encounter);
  }
  return null;
}

function estimateWifiRangeMeters(encounter) {
  const rssi = Number(encounter?.signalDbm);
  if (!Number.isFinite(rssi) || rssi >= 0) {
    return null;
  }

  const frequencyMhz = Number(encounter?.frequencyMhz || 2412);
  if (!Number.isFinite(frequencyMhz) || frequencyMhz <= 0) {
    return null;
  }

  const estimate = Math.pow(10, (27.55 - 20 * Math.log10(frequencyMhz) + Math.abs(rssi)) / 20);
  if (!Number.isFinite(estimate)) {
    return null;
  }
  return Math.min(3000, Math.max(1, estimate));
}

function estimateBleRangeMeters(encounter) {
  const rssi = Number(encounter?.signalDbm);
  if (!Number.isFinite(rssi) || rssi >= 0) {
    return null;
  }

  let txPower = -59;
  if (typeof encounter?.rawPayloadJson === "string" && encounter.rawPayloadJson.length > 1) {
    try {
      const payload = JSON.parse(encounter.rawPayloadJson);
      const candidate = Number(payload?.txPower);
      if (Number.isFinite(candidate) && candidate >= -127 && candidate <= 20) {
        txPower = candidate;
      }
    } catch (_error) {
      // Ignore malformed payload.
    }
  }

  const pathLossExponent = 2.2;
  const estimate = Math.pow(10, (txPower - rssi) / (10 * pathLossExponent));
  if (!Number.isFinite(estimate)) {
    return null;
  }
  return Math.min(3000, Math.max(1, estimate));
}

function spreadOverlappingDeviceRows(rows) {
  if (rows.length < 2) {
    return rows;
  }

  const grouped = new Map();
  rows.forEach((row) => {
    const latKey = Math.trunc(row.position.lat * 100000);
    const lngKey = Math.trunc(row.position.lng * 100000);
    const key = `${latKey}:${lngKey}`;
    if (!grouped.has(key)) {
      grouped.set(key, []);
    }
    grouped.get(key).push(row);
  });

  const adjusted = [];
  grouped.forEach((group) => {
    if (group.length <= 1) {
      adjusted.push(...group);
      return;
    }

    group.forEach((row, index) => {
      const jitterMeters = Math.min(1.8, 0.6 + Math.floor(index / 10) * 0.25);
      const angle = (2 * Math.PI * index) / group.length;
      const baseLat = row.position.lat;
      const baseLng = row.position.lng;
      const latRad = (baseLat * Math.PI) / 180;
      const metersPerDegLat = 111132;
      const metersPerDegLng = Math.max(1, 111320 * Math.cos(latRad));
      const dLat = (jitterMeters * Math.cos(angle)) / metersPerDegLat;
      const dLng = (jitterMeters * Math.sin(angle)) / metersPerDegLng;

      adjusted.push({
        ...row,
        position: {
          lat: baseLat + dLat,
          lng: baseLng + dLng
        }
      });
    });
  });

  adjusted.sort((a, b) => Date.parse(b.encounter.timestamp) - Date.parse(a.encounter.timestamp));
  return adjusted;
}

function getDeviceTypeIcon(encounter) {
  const source = String(encounter?.source || "UNKNOWN_RF");
  const subtype = getDeviceSubtypeHint(encounter);
  const glyph = subtype || shortGlyphForSource(source);
  const cacheKey = `${source}|${glyph}`;
  const cached = state.deviceIconCache.get(cacheKey);
  if (cached) {
    return cached;
  }

  const fill = colorForSource(source);
  const svg = `
<svg xmlns="http://www.w3.org/2000/svg" width="54" height="28" viewBox="0 0 54 28">
  <rect x="1" y="1" width="52" height="26" rx="13" ry="13" fill="${fill}" stroke="#102122" stroke-width="2"/>
  <text x="27" y="18" text-anchor="middle" font-family="Arial,sans-serif" font-size="11" font-weight="700" fill="#0f1516">${glyph}</text>
</svg>`;

  const icon = {
    url: `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`,
    scaledSize: new google.maps.Size(54, 28),
    anchor: new google.maps.Point(27, 14)
  };
  state.deviceIconCache.set(cacheKey, icon);
  return icon;
}

function shortGlyphForSource(source) {
  switch (source) {
    case "CELL":
      return "CELL";
    case "WIFI":
      return "WIFI";
    case "WIFI_DIRECT":
      return "WFD";
    case "BLUETOOTH_LE":
      return "BLE";
    case "BLUETOOTH_CLASSIC":
      return "BT";
    case "REMOTE_ID":
      return "RID";
    case "UWB":
      return "UWB";
    case "SDR":
      return "SDR";
    default:
      return "RF";
  }
}

function getDeviceSubtypeHint(encounter) {
  if (String(encounter?.source || "") !== "BLUETOOTH_LE") {
    return "";
  }

  if (typeof encounter?.rawPayloadJson !== "string" || encounter.rawPayloadJson.length < 2) {
    return "";
  }

  try {
    const payload = JSON.parse(encounter.rawPayloadJson);
    const classLabel = String(payload?.classLabel || "").toLowerCase();
    if (classLabel.includes("tracker") || classLabel.includes("tag")) {
      return "TAG";
    }
    if (classLabel.includes("wear") || classLabel.includes("watch")) {
      return "WR";
    }
    if (classLabel.includes("audio") || classLabel.includes("ear")) {
      return "AUD";
    }
    if (classLabel.includes("sensor")) {
      return "SNS";
    }
  } catch (_error) {
    // Ignore malformed payload.
  }

  return "";
}

function getMarkerPosition(encounter) {
  if (state.mapLocationMode === "ZONED") {
    const zoned = getZonedPosition(encounter);
    if (zoned) {
      return zoned;
    }
  }

  return getPrecisePosition(encounter);
}

function getPrecisePosition(encounter) {
  const lat = Number(encounter.lat);
  const lng = Number(encounter.lng);

  if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
    return null;
  }

  return { lat, lng };
}

function getZonedPosition(encounter) {
  if (typeof encounter.zone === "string") {
    const parsed = parseZoneCoordinates(encounter.zone);
    if (parsed) {
      return parsed;
    }
  }

  const precise = getPrecisePosition(encounter);
  if (!precise) {
    return null;
  }

  return {
    lat: Math.floor(precise.lat * 100) / 100,
    lng: Math.floor(precise.lng * 100) / 100
  };
}

function parseZoneCoordinates(zone) {
  const match = zone.match(/^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)/);
  if (!match) {
    return null;
  }

  const lat = Number(match[1]);
  const lng = Number(match[2]);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
    return null;
  }

  return { lat, lng };
}

function formatTime(timestamp) {
  return new Date(timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function colorForSource(source) {
  return SOURCE_BY_KEY[source]?.color || "#a6aeb0";
}

function labelForSource(source) {
  return SOURCE_BY_KEY[source]?.label || source || "UNKNOWN_RF";
}

function buildInfoWindowHtml(encounter) {
  return `
    <div style="color:#111; font-family:Arial,sans-serif; line-height:1.35; min-width:220px;">
      <strong>${encounter.label}</strong><br/>
      <span><strong>Source:</strong> ${displaySourceForEncounter(encounter)}</span><br/>
      <span><strong>Signal:</strong> ${encounter.signalDbm} dBm</span><br/>
      <span><strong>Distance:</strong> ${encounter.distanceMeters.toFixed(1)} m</span><br/>
      <span><strong>Zone:</strong> ${encounter.zone}</span><br/>
      <span><strong>Time:</strong> ${new Date(encounter.timestamp).toLocaleString()}</span>
    </div>
  `;
}

function displaySourceForEncounter(encounter) {
  if (encounter?.sourceLabel) {
    return encounter.sourceLabel;
  }
  if (encounter?.source === "CELL") {
    if (encounter.secondaryId) {
      return `CELL TOWER (${encounter.secondaryId})`;
    }
    return "CELL TOWER";
  }
  return labelForSource(encounter?.source);
}
