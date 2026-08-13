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
  dashboardStatus: "loading",
  dashboardStatusMessage: "Connecting to live mesh feed...",
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
  mapTypeIndexByTab: {
    encounters: 0,
    hotspots: 0,
    "device-locations": 0
  },
  lastBoundsByTab: {
    encounters: null,
    hotspots: null,
    "device-locations": null
  },
  mapPinLimits: {
    encounters: 250,
    hotspots: 80,
    "device-locations": 250
  },
  mapLocationMode: "PRECISE",
  latestRenderedEncounters: []
};

const MAP_TYPES = ["roadmap", "hybrid", "terrain", "satellite"];
const PIN_LIMIT_OPTIONS = [100, 250, 500, 1000, 0];
const HOTSPOT_LIMIT_OPTIONS = [40, 80, 120, 200, 0];
const LOCATION_MODES = ["PRECISE", "ZONED"];
const MAP_AUTO_FOCUS_MAX_DISTANCE_METERS = 160000;

bootstrap();

async function bootstrap() {
  state.data = emptyDashboardData();
  renderDashboardStatus();

  state.config = await fetchDashboardConfig();
  try {
    state.data = await fetchDashboardData();
    state.dashboardStatus = "ready";
    state.dashboardStatusMessage = "";
  } catch (error) {
    state.dashboardStatus = "error";
    state.dashboardStatusMessage = error?.message || "Unable to load live mesh data.";
    state.data = emptyDashboardData();
  }

  renderFilters();
  renderDashboardControls();
  initDeviceDetailsInteractions();
  renderDashboardStatus();
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
    try {
      state.data = await fetchDashboardData();
      state.dashboardStatus = "ready";
      state.dashboardStatusMessage = "";
    } catch (error) {
      state.dashboardStatus = "error";
      state.dashboardStatusMessage = error?.message || "Live refresh failed.";
    }

    renderDashboardStatus();
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
  const live = await fetchWithTimeout("/api/mesh/live", { cache: "no-store" }, 7000);
  if (!live.ok) {
    throw new Error(`Live API failed with ${live.status}`);
  }

  const payload = await live.json();
  if (payload.meta?.live === true) {
    return normalizeLivePayload(payload);
  }

  if (Array.isArray(payload.encounters) && payload.encounters.length > 0) {
    return normalizeLivePayload(payload);
  }

  throw new Error(payload.meta?.reason || "Live API returned no data.");
}

function normalizeLivePayload(payload) {
  return {
    generatedAt: payload.generatedAt || new Date().toISOString(),
    mesh: payload.mesh || { discoveredPeers: 0, connectedPeers: 0, peers: [] },
    encounters: Array.isArray(payload.encounters) ? payload.encounters : [],
    meta: payload.meta || { live: false }
  };
}

function emptyDashboardData() {
  return {
    generatedAt: new Date().toISOString(),
    mesh: { discoveredPeers: 0, connectedPeers: 0, peers: [] },
    encounters: [],
    meta: { live: false }
  };
}

function renderDashboardStatus() {
  const host = document.getElementById("dashboard-status");
  if (!host) {
    return;
  }

  if (state.dashboardStatus === "ready") {
    host.hidden = true;
    host.innerHTML = "";
    return;
  }

  if (state.dashboardStatus === "loading") {
    host.hidden = false;
    host.className = "dashboard-status loading";
    host.innerHTML = `
      <span class="status-spinner" aria-hidden="true"></span>
      <span>${state.dashboardStatusMessage || "Connecting to live mesh feed..."}</span>
    `;
    return;
  }

  host.hidden = false;
  host.className = "dashboard-status error";
  host.innerHTML = `
    <strong>Live feed unavailable.</strong>
    <span>${state.dashboardStatusMessage || "Unable to retrieve mesh encounters."}</span>
  `;
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

function getFilteredEncountersUncapped() {
  const filtered = state.data.encounters
    .filter((e) => state.enabledSources.has(e.source))
    .sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp));

  return dedupeEncountersByDevice(filtered);
}

function getFilteredEncounterSightings() {
  return state.data.encounters
    .filter((e) => state.enabledSources.has(e.source))
    .sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp));
}

function dedupeEncountersByDevice(encounters) {
  const deduped = [];
  const seen = new Set();

  for (const encounter of encounters) {
    const key = encounterDedupKey(encounter);
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    deduped.push(encounter);
  }

  return deduped;
}

function encounterDedupKey(encounter) {
  const source = String(encounter?.source || "UNKNOWN_RF");
  const label = String(encounter?.label || "unknown");
  const secondary = String(encounter?.secondaryId || "");
  return `${source}|${label}|${secondary}`;
}

function getFilteredEncounters() {
  return getFilteredEncountersUncapped();
}

function renderDashboardControls() {
  const host = document.getElementById("dashboard-controls");
  if (!host) {
    return;
  }
  host.innerHTML = "";
  host.hidden = true;
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
  const latest = [...encounters]
    .sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp))
    .slice(0, 16);
  state.latestRenderedEncounters = latest;

  const rows = latest
    .map(
      (e, index) => `
      <tr class="encounter-row" data-encounter-index="${index}" tabindex="0" role="button" aria-label="Open device details for ${escapeHtml(buildEncounterMapLabel(e))}">
        <td>${formatTime(e.timestamp)}</td>
        <td>${displaySourceForEncounter(e)}</td>
        <td>${buildEncounterMapLabel(e)}</td>
        <td>${e.signalDbm} dBm</td>
        <td>${e.distanceMeters.toFixed(1)} m</td>
      </tr>
    `
    )
    .join("");

  body.innerHTML = rows || "<tr><td colspan=\"5\">No encounters for the selected sources.</td></tr>";
}

function initDeviceDetailsInteractions() {
  const encounterRows = document.getElementById("encounter-rows");
  if (encounterRows) {
    encounterRows.addEventListener("click", (event) => {
      const row = event.target?.closest?.("tr[data-encounter-index]");
      if (!row) {
        return;
      }
      const index = Number(row.dataset.encounterIndex);
      const encounter = Number.isInteger(index) ? state.latestRenderedEncounters[index] : null;
      if (encounter) {
        showDeviceDetailsPage(encounter);
      }
    });

    encounterRows.addEventListener("keydown", (event) => {
      if (event.key !== "Enter" && event.key !== " ") {
        return;
      }
      const row = event.target?.closest?.("tr[data-encounter-index]");
      if (!row) {
        return;
      }
      event.preventDefault();
      const index = Number(row.dataset.encounterIndex);
      const encounter = Number.isInteger(index) ? state.latestRenderedEncounters[index] : null;
      if (encounter) {
        showDeviceDetailsPage(encounter);
      }
    });
  }

  const closeButton = document.getElementById("device-detail-close");
  if (closeButton) {
    closeButton.addEventListener("click", () => {
      const panel = document.getElementById("device-detail-page");
      if (panel) {
        panel.hidden = true;
      }
    });
  }
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
  const center = getDashboardMapAnchorPosition(state.data.encounters) || { lat: 37.7749, lng: -122.4194 };

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

  renderMapControls();
}

function renderMapControls() {
  renderMapControlsForTab("encounters", state.map, "map-controls");
  renderMapControlsForTab("hotspots", state.hotspotMap, "map-controls-hotspots");
  renderMapControlsForTab("device-locations", state.deviceMap, "map-controls-device-locations");
}

function renderMapControlsForTab(tabName, mapRef, hostId) {
  const host = document.getElementById(hostId);
  if (!host || !mapRef) {
    return;
  }
  host.innerHTML = "";

  const controls = [
    {
      label: "Zoom +",
      onClick: () => {
        const current = mapRef.getZoom() || 12;
        mapRef.setZoom(current + 1);
      }
    },
    {
      label: "Zoom -",
      onClick: () => {
        const current = mapRef.getZoom() || 12;
        mapRef.setZoom(current - 1);
      }
    },
    {
      label: tabName === "hotspots" ? "Fit Regions" : "Fit Markers",
      onClick: () => {
        const bounds = state.lastBoundsByTab[tabName];
        if (bounds) {
          mapRef.fitBounds(bounds);
        }
      }
    },
    {
      label: "Center Latest",
      onClick: () => {
        const point = getLatestCenterPointForTab(tabName);
        if (point && isValidLatLng(Number(point.lat), Number(point.lng))) {
          mapRef.setCenter({ lat: Number(point.lat), lng: Number(point.lng) });
          mapRef.setZoom(16);
        }
      }
    },
    {
      label: `Map: ${MAP_TYPES[state.mapTypeIndexByTab[tabName]]}`,
      onClick: () => {
        state.mapTypeIndexByTab[tabName] = (state.mapTypeIndexByTab[tabName] + 1) % MAP_TYPES.length;
        const type = MAP_TYPES[state.mapTypeIndexByTab[tabName]];
        mapRef.setMapTypeId(type);
        renderMapControlsForTab(tabName, mapRef, hostId);
      }
    },
    {
      label: `Pins: ${state.mapPinLimits[tabName] === 0 ? "All" : state.mapPinLimits[tabName]}`,
      onClick: () => {
        const options = tabName === "hotspots" ? HOTSPOT_LIMIT_OPTIONS : PIN_LIMIT_OPTIONS;
        const currentIndex = options.indexOf(state.mapPinLimits[tabName]);
        const nextIndex = currentIndex >= 0 ? (currentIndex + 1) % options.length : 0;
        state.mapPinLimits[tabName] = options[nextIndex];
        renderMapControlsForTab(tabName, mapRef, hostId);
        if (tabName === "hotspots") {
          drawHotspotRegions();
        } else if (tabName === "device-locations") {
          drawDeviceLocations();
        } else {
          drawMarkers();
        }
      }
    }
  ];

  if (tabName === "encounters") {
    controls.push(
      {
        label: "Traffic",
        active: !!state.trafficLayer?.getMap(),
        onClick: () => {
          const showing = !!state.trafficLayer.getMap();
          state.trafficLayer.setMap(showing ? null : state.map);
          renderMapControlsForTab(tabName, mapRef, hostId);
        }
      },
      {
        label: `Loc: ${state.mapLocationMode === "ZONED" ? "Zoned" : "Estimated Precise"}`,
        onClick: () => {
          const currentIndex = LOCATION_MODES.indexOf(state.mapLocationMode);
          const nextIndex = currentIndex >= 0 ? (currentIndex + 1) % LOCATION_MODES.length : 0;
          state.mapLocationMode = LOCATION_MODES[nextIndex];
          renderMapControlsForTab(tabName, mapRef, hostId);
          drawMarkers();
        }
      }
    );
  }

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
  const anchor = getMapAutoFocusAnchor(state.map, encounters);
  const focusPoints = [];
  const bounds = new google.maps.LatLngBounds();

  encounters.forEach((e) => {
    const point = getMarkerPosition(e);
    if (!point) {
      return;
    }
    const displayLabel = buildEncounterMapLabel(e);

    const marker = new google.maps.Marker({
      position: point,
      map: state.map,
      title: displayLabel,
      icon: getDeviceTypeIcon(e)
    });

    marker.addListener("click", () => {
      state.infoWindow.setContent(buildInfoWindowHtml(e, displayLabel));
      state.infoWindow.open({ map: state.map, anchor: marker });
      showDeviceDetailsPage(e);
    });

    state.markers.push(marker);
    const markerPos = marker.getPosition();
    if (markerPos && shouldAutoFocusPoint(point, anchor, MAP_AUTO_FOCUS_MAX_DISTANCE_METERS)) {
      bounds.extend(markerPos);
      focusPoints.push(point);
    }
  });

  if (focusPoints.length > 1) {
    state.lastBoundsByTab.encounters = bounds;
    state.map.fitBounds(bounds);
  } else if (focusPoints.length === 1) {
    state.lastBoundsByTab.encounters = bounds;
    state.map.setCenter(bounds.getCenter());
    state.map.setZoom(15);
  } else {
    state.lastBoundsByTab.encounters = null;
  }
}

function getMapRenderEncounters() {
  const encounters = [...getFilteredEncounters()].sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp));
  const pinLimit = state.mapPinLimits.encounters;
  if (pinLimit === 0) {
    return encounters;
  }
  return encounters.slice(0, pinLimit);
}

function drawHotspotRegions() {
  if (!state.hotspotMap || !state.data || !window.google?.maps) {
    return;
  }

  state.hotspotCircles.forEach((circle) => circle.setMap(null));
  state.hotspotCircles = [];

  const hotspotLimit = state.mapPinLimits.hotspots;
  const regions = buildHotspotRegions(getFilteredEncounters(), hotspotLimit);
  const anchor = getMapAutoFocusAnchor(state.hotspotMap);
  const focusRegions = [];
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
    if (shouldAutoFocusPoint(region.center, anchor, MAP_AUTO_FOCUS_MAX_DISTANCE_METERS)) {
      bounds.extend(region.center);
      focusRegions.push(region);
    }
  });

  if (focusRegions.length > 1) {
    state.lastBoundsByTab.hotspots = bounds;
    state.hotspotMap.fitBounds(bounds);
  } else if (focusRegions.length === 1) {
    state.lastBoundsByTab.hotspots = bounds;
    state.hotspotMap.setCenter(focusRegions[0].center);
    state.hotspotMap.setZoom(12);
  } else {
    state.lastBoundsByTab.hotspots = null;
  }
}

function buildHotspotRegions(encounters, hotspotLimit = 80) {
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
    .slice(0, hotspotLimit === 0 ? Number.MAX_SAFE_INTEGER : hotspotLimit);
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
  const anchor = getMapAutoFocusAnchor(state.deviceMap);
  const focusRows = [];
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
      title: row.displayLabel,
      icon: markerIcon
    });

    marker.addListener("click", () => {
      const content = buildInfoWindowHtml(row.encounter, row.displayLabel, row.locationBasis);
      state.deviceInfoWindow.setContent(content);
      state.deviceInfoWindow.open({ map: state.deviceMap, anchor: marker });
      showDeviceDetailsPage(row.encounter, row.locationBasis);
    });

    state.deviceMarkers.push(marker);
    if (shouldAutoFocusPoint(row.position, anchor, MAP_AUTO_FOCUS_MAX_DISTANCE_METERS)) {
      bounds.extend(row.position);
      focusRows.push(row);
    }
  });

  if (focusRows.length > 1) {
    state.lastBoundsByTab["device-locations"] = bounds;
    state.deviceMap.fitBounds(bounds);
  } else if (focusRows.length === 1) {
    state.lastBoundsByTab["device-locations"] = bounds;
    state.deviceMap.setCenter(focusRows[0].position);
    state.deviceMap.setZoom(15);
  } else {
    state.lastBoundsByTab["device-locations"] = null;
  }
}

function getDashboardMapAnchorPosition(encounters = null) {
  const pool = Array.isArray(encounters) ? encounters : getFilteredEncounters();
  const latestObserver = pool.find((encounter) =>
    isValidLatLng(Number(encounter?.observerLat), Number(encounter?.observerLng))
  );
  if (!latestObserver) {
    return null;
  }
  return {
    lat: Number(latestObserver.observerLat),
    lng: Number(latestObserver.observerLng)
  };
}

function getMapAutoFocusAnchor(mapRef, encounters = null) {
  const center = mapRef?.getCenter?.();
  const centerLat = typeof center?.lat === "function" ? Number(center.lat()) : Number(center?.lat);
  const centerLng = typeof center?.lng === "function" ? Number(center.lng()) : Number(center?.lng);
  if (isValidLatLng(centerLat, centerLng)) {
    return { lat: centerLat, lng: centerLng };
  }
  return getDashboardMapAnchorPosition(encounters);
}

function getLatestAutoFocusEncounter(mapRef = null) {
  const encounters = getFilteredEncounters();
  const anchor = getMapAutoFocusAnchor(mapRef, encounters);
  if (!anchor) {
    return null;
  }
  const nearby = encounters.find((encounter) => {
    const point = getPrecisePosition(encounter);
    return shouldAutoFocusPoint(point, anchor, MAP_AUTO_FOCUS_MAX_DISTANCE_METERS);
  });
  return nearby || null;
}

function getLatestCenterPointForTab(tabName) {
  if (tabName === "hotspots") {
    const regions = buildHotspotRegions(getFilteredEncounters(), state.mapPinLimits.hotspots);
    const latestRegion = regions[0];
    if (latestRegion?.center && isValidLatLng(Number(latestRegion.center.lat), Number(latestRegion.center.lng))) {
      return latestRegion.center;
    }
    return null;
  }

  if (tabName === "device-locations") {
    const rows = buildLatestDeviceRows();
    const latestRow = rows[0];
    if (latestRow?.position && isValidLatLng(Number(latestRow.position.lat), Number(latestRow.position.lng))) {
      return latestRow.position;
    }
    return null;
  }

  const encounters = getMapRenderEncounters();
  for (const encounter of encounters) {
    const point = getMarkerPosition(encounter);
    if (point && isValidLatLng(Number(point.lat), Number(point.lng))) {
      return point;
    }
  }
  return null;
}

function shouldAutoFocusPoint(point, anchor, maxDistanceMeters) {
  if (!point || !isValidLatLng(Number(point.lat), Number(point.lng))) {
    return false;
  }
  if (!anchor || !isValidLatLng(Number(anchor.lat), Number(anchor.lng))) {
    return false;
  }

  const distance = haversineMeters(Number(anchor.lat), Number(anchor.lng), Number(point.lat), Number(point.lng));
  return Number.isFinite(distance) && distance <= maxDistanceMeters;
}

function buildLatestDeviceRows() {
  const encounters = [...getFilteredEncounterSightings()].sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp));
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
      locationBasis: resolved.basis,
      displayLabel: buildEncounterMapLabel(latest, deviceEncounters)
    });
  }

  rows.sort((a, b) => Date.parse(b.encounter.timestamp) - Date.parse(a.encounter.timestamp));
  const spreadRows = spreadOverlappingDeviceRows(rows);

  const pinLimit = state.mapPinLimits["device-locations"];
  if (pinLimit === 0) {
    return spreadRows;
  }
  return spreadRows.slice(0, pinLimit);
}

function resolveDeviceLocationLikeAndroid(source, encounters) {
  const latest = encounters[0];

  if (source === "REMOTE_ID") {
    const broadcast = getRemoteIdBroadcastPosition(latest);
    if (broadcast) {
      return { position: broadcast, basis: "remote-id-broadcast" };
    }
    return null;
  }

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

function getRemoteIdBroadcastPosition(encounter) {
  const source = String(encounter?.source || "");
  if (source !== "REMOTE_ID") {
    return null;
  }

  const candidates = [
    { lat: encounter?.remoteIdLat, lng: encounter?.remoteIdLng },
    { lat: encounter?.remoteIdLat, lng: encounter?.remoteIdLon }
  ];

  for (const item of candidates) {
    const lat = Number(item.lat);
    const lng = Number(item.lng);
    if (isValidLatLng(lat, lng)) {
      return { lat, lng };
    }
  }

  if (typeof encounter?.rawPayloadJson === "string" && encounter.rawPayloadJson.length > 1) {
    try {
      const payload = JSON.parse(encounter.rawPayloadJson);
      const decoded = payload?.remoteIdDecoded && typeof payload.remoteIdDecoded === "object"
        ? payload.remoteIdDecoded
        : null;
      const fallbackCandidates = [
        { lat: decoded?.droneLat, lng: decoded?.droneLon },
        { lat: payload?.droneLat, lng: payload?.droneLon },
        { lat: payload?.lat, lng: payload?.lon }
      ];

      for (const item of fallbackCandidates) {
        const lat = Number(item.lat);
        const lng = Number(item.lng);
        if (isValidLatLng(lat, lng)) {
          return { lat, lng };
        }
      }
    } catch (_error) {
      // Ignore malformed payload.
    }
  }

  return null;
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
  const iconWidth = glyph.length > 6 ? 110 : 54;
  const iconHeight = 28;
  const rectWidth = iconWidth - 2;
  const textY = iconHeight / 2 + 4;
  const radius = Math.floor(iconHeight / 2) - 1;
  const svg = `
<svg xmlns="http://www.w3.org/2000/svg" width="${iconWidth}" height="${iconHeight}" viewBox="0 0 ${iconWidth} ${iconHeight}">
  <rect x="1" y="1" width="${rectWidth}" height="${iconHeight - 2}" rx="${radius}" ry="${radius}" fill="${fill}" stroke="#102122" stroke-width="2"/>
  <text x="${iconWidth / 2}" y="${textY}" text-anchor="middle" font-family="Arial,sans-serif" font-size="11" font-weight="700" fill="#0f1516">${glyph}</text>
</svg>`;

  const icon = {
    url: `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`,
    scaledSize: new google.maps.Size(iconWidth, iconHeight),
    anchor: new google.maps.Point(iconWidth / 2, iconHeight / 2)
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
      return "RID / Drone";
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
  const source = String(encounter?.source || "");
  if (source === "REMOTE_ID") {
    return getRemoteIdBroadcastPosition(encounter);
  }

  const lat = Number(encounter.lat);
  const lng = Number(encounter.lng);

  if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
    return null;
  }

  return { lat, lng };
}

function getZonedPosition(encounter) {
  const source = String(encounter?.source || "");
  if (source === "REMOTE_ID") {
    const precise = getRemoteIdBroadcastPosition(encounter);
    if (!precise) {
      return null;
    }
    return {
      lat: Math.floor(precise.lat * 100) / 100,
      lng: Math.floor(precise.lng * 100) / 100
    };
  }

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

function buildEncounterMapLabel(encounter, history = null) {
  const baseLabel = String(encounter?.label || "unknown");
  const source = String(encounter?.source || "");
  if (source !== "REMOTE_ID") {
    return baseLabel;
  }

  const direction = getRemoteIdDirectionHint(encounter, history);
  if (!direction) {
    return baseLabel;
  }
  return `${baseLabel} [${direction}]`;
}

function getRemoteIdDirectionHint(encounter, history = null) {
  let heading = extractRemoteIdHeadingDegrees(encounter);
  if (!Number.isFinite(heading)) {
    heading = deriveHeadingFromHistory(history);
  }
  if (!Number.isFinite(heading)) {
    return "";
  }

  const cardinal = cardinalFromHeading(heading);
  if (!cardinal) {
    return "";
  }
  return `${cardinal}->`;
}

function extractRemoteIdHeadingDegrees(encounter) {
  if (typeof encounter?.rawPayloadJson !== "string" || encounter.rawPayloadJson.length < 2) {
    return Number.NaN;
  }

  try {
    const payload = JSON.parse(encounter.rawPayloadJson);
    const decoded = payload?.remoteIdDecoded && typeof payload.remoteIdDecoded === "object"
      ? payload.remoteIdDecoded
      : null;
    const candidates = [
      decoded?.headingDegrees,
      decoded?.heading_deg,
      decoded?.heading,
      payload?.headingDegrees,
      payload?.heading_deg,
      payload?.heading
    ];

    for (const value of candidates) {
      const numeric = Number(value);
      if (Number.isFinite(numeric)) {
        return normalizeHeadingDegrees(numeric);
      }
    }
  } catch (_error) {
    // Ignore malformed payload.
  }

  return Number.NaN;
}

function extractRemoteIdAircraftAltitudeMeters(encounter) {
  if (typeof encounter?.rawPayloadJson !== "string" || encounter.rawPayloadJson.length < 2) {
    return Number.NaN;
  }

  try {
    const payload = JSON.parse(encounter.rawPayloadJson);
    const decoded = payload?.remoteIdDecoded && typeof payload.remoteIdDecoded === "object"
      ? payload.remoteIdDecoded
      : null;
    const candidates = [
      decoded?.altitudeMeters,
      decoded?.aircraftAltitudeMeters,
      decoded?.aircraft_altitude_m,
      payload?.altitudeMeters,
      payload?.aircraftAltitudeMeters,
      payload?.aircraft_altitude_m,
      payload?.heightMeters,
      payload?.height_m,
      payload?.altitude_m,
      payload?.alt
    ];

    for (const value of candidates) {
      const numeric = Number(value);
      if (Number.isFinite(numeric)) {
        return numeric;
      }
    }
  } catch (_error) {
    // Ignore malformed payload.
  }

  return Number.NaN;
}

function deriveHeadingFromHistory(history) {
  if (!Array.isArray(history) || history.length < 2) {
    return Number.NaN;
  }

  const latest = history[0];
  const previous = history.slice(1).find((enc) => getRemoteIdBroadcastPosition(enc) !== null);
  if (!previous) {
    return Number.NaN;
  }

  const latestPoint = getRemoteIdBroadcastPosition(latest);
  const previousPoint = getRemoteIdBroadcastPosition(previous);
  if (!latestPoint || !previousPoint) {
    return Number.NaN;
  }

  const deltaTs = Date.parse(latest.timestamp) - Date.parse(previous.timestamp);
  if (!Number.isFinite(deltaTs) || deltaTs <= 0) {
    return Number.NaN;
  }

  return bearingDegrees(previousPoint.lat, previousPoint.lng, latestPoint.lat, latestPoint.lng);
}

function bearingDegrees(fromLat, fromLng, toLat, toLng) {
  const fromLatRad = (fromLat * Math.PI) / 180;
  const toLatRad = (toLat * Math.PI) / 180;
  const deltaLngRad = ((toLng - fromLng) * Math.PI) / 180;

  const y = Math.sin(deltaLngRad) * Math.cos(toLatRad);
  const x =
    Math.cos(fromLatRad) * Math.sin(toLatRad) -
    Math.sin(fromLatRad) * Math.cos(toLatRad) * Math.cos(deltaLngRad);
  const heading = (Math.atan2(y, x) * 180) / Math.PI;
  return normalizeHeadingDegrees(heading);
}

function normalizeHeadingDegrees(value) {
  const normalized = ((value % 360) + 360) % 360;
  return Number.isFinite(normalized) ? normalized : Number.NaN;
}

function cardinalFromHeading(headingDegrees) {
  if (!Number.isFinite(headingDegrees)) {
    return "";
  }

  const directions = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"];
  const index = Math.floor(((headingDegrees + 22.5) % 360) / 45);
  return directions[index] || "";
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

function buildInfoWindowHtml(encounter, displayLabel = buildEncounterMapLabel(encounter), locationBasis = null) {
  const detailsMarkup = buildEncounterDetailsMarkup(encounter, displayLabel, locationBasis, 48, "info-window");
  return `
    <div style="color:#111; font-family:Arial,sans-serif; line-height:1.35; min-width:260px; max-width:360px; max-height:300px; overflow:auto;">
      ${detailsMarkup}
    </div>
  `;
}

function showDeviceDetailsPage(encounter, locationBasis = null) {
  const panel = document.getElementById("device-detail-page");
  const subtitle = document.getElementById("device-detail-subtitle");
  const content = document.getElementById("device-detail-content");
  if (!panel || !subtitle || !content) {
    return;
  }

  const label = buildEncounterMapLabel(encounter);
  subtitle.textContent = `Viewing all currently available fields for ${label}.`;
  content.innerHTML = buildEncounterDetailsMarkup(encounter, label, locationBasis, 999, "panel");
  panel.hidden = false;
  panel.scrollIntoView({ behavior: "smooth", block: "start" });
}

function buildEncounterDetailsMarkup(encounter, displayLabel, locationBasis = null, maxRows = 999, tone = "panel") {
  const fields = collectEncounterDetails(encounter, displayLabel, locationBasis);
  const keyClass = tone === "info-window" ? "info-detail-key" : "device-detail-key";
  const valueClass = tone === "info-window" ? "info-detail-value" : "device-detail-value";
  const gridClass = tone === "info-window" ? "info-detail-grid" : "device-detail-grid";
  const emptyClass = tone === "info-window" ? "info-detail-empty" : "device-detail-empty";
  const rows = fields
    .slice(0, maxRows)
    .map(([key, value]) => `
      <div class="${keyClass}">${escapeHtml(key)}</div>
      <div class="${valueClass}">${escapeHtml(value)}</div>
    `)
    .join("");

  if (!rows) {
    return `<div class="${emptyClass}">No details available for this encounter.</div>`;
  }

  return `<div class="${gridClass}">${rows}</div>`;
}

function collectEncounterDetails(encounter, displayLabel, locationBasis = null) {
  const details = [];
  const push = (name, value) => {
    if (value === null || value === undefined) {
      return;
    }
    const text = String(value).trim();
    if (!text) {
      return;
    }
    details.push([name, text]);
  };

  push("Label", displayLabel || encounter?.label || "unknown");
  push("Source", displaySourceForEncounter(encounter));
  push("Timestamp", new Date(encounter.timestamp).toLocaleString());
  if (Number.isFinite(Number(encounter.signalDbm))) {
    push("Signal", `${Number(encounter.signalDbm)} dBm`);
  }
  if (Number.isFinite(Number(encounter.distanceMeters))) {
    push("Distance", `${Number(encounter.distanceMeters).toFixed(1)} m`);
  }
  push("Zone", encounter.zone);
  if (locationBasis) {
    push("Location Basis", locationBasis);
  }

  const precise = getPrecisePosition(encounter);
  if (precise) {
    push("Display Lat", precise.lat.toFixed(6));
    push("Display Lng", precise.lng.toFixed(6));
  }

  const observerToDeviceMeters = getObserverToDeviceDistanceMeters(encounter, precise);
  if (Number.isFinite(observerToDeviceMeters)) {
    push("Observer to Device Range", formatDistanceFeetMiles(observerToDeviceMeters));
  }

  if (encounter?.source === "REMOTE_ID") {
    const ridPoint = getRemoteIdBroadcastPosition(encounter);
    if (ridPoint) {
      push("Remote ID Broadcast Lat", ridPoint.lat.toFixed(6));
      push("Remote ID Broadcast Lng", ridPoint.lng.toFixed(6));
    }
    const altitude = extractRemoteIdAircraftAltitudeMeters(encounter);
    if (Number.isFinite(altitude)) {
      push("Aircraft Altitude", `${altitude.toFixed(1)} m`);
    }
  }

  const orderedTopLevelKeys = [
    "source",
    "sourceLabel",
    "secondaryId",
    "lat",
    "lng",
    "lon",
    "remoteIdLat",
    "remoteIdLng",
    "remoteIdLon",
    "deviceLat",
    "deviceLng",
    "deviceLon",
    "detailLat",
    "detailLng",
    "detailLon",
    "estimatedLat",
    "estimatedLng",
    "estimatedLon",
    "frequencyMhz",
    "hopCount",
    "peerName",
    "provenance",
    "provenanceNodeId",
    "provenanceOriginNodeId",
    "provenancePathNodeIds",
    "provenanceReceivedAtEpochMs",
    "timestamp"
  ];

  for (const key of orderedTopLevelKeys) {
    if (key in (encounter || {})) {
      const value = encounter[key];
      if (value !== null && value !== undefined && key !== "timestamp") {
        push(`Encounter ${key}`, value);
      }
    }
  }

  appendPayloadDetails(details, encounter?.rawPayloadJson);
  return details;
}

function getObserverToDeviceDistanceMeters(encounter, displayPoint = null) {
  const observerLat = Number(encounter?.observerLat);
  const observerLng = Number(encounter?.observerLng);
  if (!isValidLatLng(observerLat, observerLng)) {
    return Number.NaN;
  }

  const point = displayPoint || getPrecisePosition(encounter);
  if (!point || !isValidLatLng(Number(point.lat), Number(point.lng))) {
    return Number.NaN;
  }

  return haversineMeters(observerLat, observerLng, Number(point.lat), Number(point.lng));
}

function formatDistanceFeetMiles(meters) {
  const safeMeters = Number(meters);
  if (!Number.isFinite(safeMeters) || safeMeters < 0) {
    return "unknown";
  }

  const miles = safeMeters / 1609.344;
  const feet = safeMeters / 0.3048;
  return `${miles.toFixed(2)} mi (${feet.toFixed(0)} ft)`;
}

function appendPayloadDetails(details, rawPayloadJson) {
  if (typeof rawPayloadJson !== "string" || rawPayloadJson.length < 2) {
    return;
  }

  const push = (name, value) => {
    if (value === null || value === undefined) {
      return;
    }
    const text = String(value).trim();
    if (!text) {
      return;
    }
    details.push([name, text]);
  };

  try {
    const payload = JSON.parse(rawPayloadJson);
    const flattened = flattenPayloadObject(payload);
    Object.entries(flattened)
      .sort(([a], [b]) => a.localeCompare(b))
      .forEach(([key, value]) => {
        push(`Payload ${key}`, value);
      });
  } catch (_error) {
    push("Payload Raw", rawPayloadJson);
  }
}

function flattenPayloadObject(input, prefix = "", result = {}) {
  if (!input || typeof input !== "object") {
    return result;
  }

  if (Array.isArray(input)) {
    result[prefix || "array"] = JSON.stringify(input);
    return result;
  }

  Object.entries(input).forEach(([key, value]) => {
    const fullKey = prefix ? `${prefix}.${key}` : key;
    if (value === null || value === undefined) {
      return;
    }
    if (Array.isArray(value)) {
      result[fullKey] = JSON.stringify(value);
      return;
    }
    if (typeof value === "object") {
      flattenPayloadObject(value, fullKey, result);
      return;
    }
    result[fullKey] = value;
  });

  return result;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
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
