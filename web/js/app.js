/**
 * Emergency Dispatch System - Front-end Application Engine
 * Project 29: Intelligent Emergency Resource Allocation & Ambulance Dispatch System
 */

(function () {
  'use strict';

  // --- Audio Feedback (Web Audio API Synthesizer) ---
  const audioCtx = (typeof AudioContext !== 'undefined' || typeof webkitAudioContext !== 'undefined')
    ? new (window.AudioContext || window.webkitAudioContext)() : null;

  function playTone(freq, duration, type = 'sine') {
    if (!audioCtx) return;
    try {
      if (audioCtx.state === 'suspended') audioCtx.resume();
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();
      osc.type = type;
      osc.frequency.setValueAtTime(freq, audioCtx.currentTime);
      gain.gain.setValueAtTime(0.08, audioCtx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + duration);
      osc.connect(gain);
      gain.connect(audioCtx.destination);
      osc.start();
      osc.stop(audioCtx.currentTime + duration);
    } catch (e) {
      // Ignore audio failure
    }
  }

  function playDispatchChime() {
    playTone(587.33, 0.12, 'triangle'); // D5
    setTimeout(() => playTone(880.00, 0.25, 'triangle'), 120); // A5
  }

  function playAlertChime() {
    playTone(440, 0.1, 'sawtooth');
    setTimeout(() => playTone(330, 0.2, 'sawtooth'), 100);
  }

  // --- State ---
  const state = {
    zones: [],
    resources: [],
    coverage: [],
    activeIncident: null,     // { x, y, type, severity, id }
    activePlan: null,         // DispatchPlan returned from server
    activeRangeBox: null,     // { minX, minY, maxX, maxY }
    activeRadiusCircle: null, // { x, y, radiusKm }
    nearestQuery: null,       // { x, y, targetRes, distance }
    relocationVectors: [],    // [{ from: {x,y}, to: {x,y}, id }]
    highlightedIds: new Set(),
    clickMode: 'dispatch',    // 'dispatch', 'nearest', 'radius', 'range'
    dashOffset: 0,
    animationFrameId: null,
    dragStart: null,
    dragEnd: null
  };

  // --- DOM Elements ---
  const canvas = document.getElementById('cityCanvas');
  const ctx = canvas.getContext('2d');
  const canvasWrapper = document.getElementById('canvasWrapper');

  const cursorCoords = document.getElementById('cursorCoords');
  const cursorSector = document.getElementById('cursorSector');
  const systemStatusText = document.getElementById('systemStatusText');
  const logContainer = document.getElementById('logContainer');

  // Forms and Result elements
  const formDispatch = document.getElementById('formDispatch');
  const dispX = document.getElementById('dispX');
  const dispY = document.getElementById('dispY');
  const dispType = document.getElementById('dispType');
  const dispSeverity = document.getElementById('dispSeverity');
  const dispatchResult = document.getElementById('dispatchResult');
  const allocatedUnitsList = document.getElementById('allocatedUnitsList');
  const unmetAlerts = document.getElementById('unmetAlerts');
  const resZoneVal = document.getElementById('resZoneVal');
  const resZone = document.getElementById('resZone');

  const formNearest = document.getElementById('formNearest');
  const nearX = document.getElementById('nearX');
  const nearY = document.getElementById('nearY');
  const nearestResult = document.getElementById('nearestResult');
  const nearResName = document.getElementById('nearResName');
  const nearResPos = document.getElementById('nearResPos');
  const nearResDist = document.getElementById('nearResDist');

  const formRange = document.getElementById('formRange');
  const rangeMinX = document.getElementById('rangeMinX');
  const rangeMinY = document.getElementById('rangeMinY');
  const rangeMaxX = document.getElementById('rangeMaxX');
  const rangeMaxY = document.getElementById('rangeMaxY');
  const rangeResult = document.getElementById('rangeResult');
  const rangeCount = document.getElementById('rangeCount');
  const rangeList = document.getElementById('rangeList');

  const formRadius = document.getElementById('formRadius');
  const radX = document.getElementById('radX');
  const radY = document.getElementById('radY');
  const radiusKm = document.getElementById('radiusKm');
  const radiusKmVal = document.getElementById('radiusKmVal');
  const radiusResult = document.getElementById('radiusResult');
  const radiusCount = document.getElementById('radiusCount');
  const radiusList = document.getElementById('radiusList');

  const coverageTableBody = document.getElementById('coverageTableBody');
  const redistributeResult = document.getElementById('redistributeResult');
  const redistributeMoves = document.getElementById('redistributeMoves');
  const rosterList = document.getElementById('rosterList');

  // Modals
  const theoryModal = document.getElementById('theoryModal');
  const scenariosModal = document.getElementById('scenariosModal');

  // --- Canvas Sizing & Coordinate Transform ---
  let mapPadding = 36; // px padding around 30x30 km grid

  function resizeCanvas() {
    const rect = canvasWrapper.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    ctx.scale(dpr, dpr);
    renderMap();
  }

  window.addEventListener('resize', resizeCanvas);

  function kmToPx(xKm, yKm) {
    const rect = canvasWrapper.getBoundingClientRect();
    const w = rect.width - 2 * mapPadding;
    const h = rect.height - 2 * mapPadding;
    const px = mapPadding + (xKm / 30.0) * w;
    const py = (rect.height - mapPadding) - (yKm / 30.0) * h;
    return { x: px, y: py };
  }

  function pxToKm(px, py) {
    const rect = canvasWrapper.getBoundingClientRect();
    const w = rect.width - 2 * mapPadding;
    const h = rect.height - 2 * mapPadding;
    const xKm = ((px - mapPadding) / w) * 30.0;
    const yKm = (((rect.height - mapPadding) - py) / h) * 30.0;
    return {
      x: Math.max(0, Math.min(30, Math.round(xKm * 10) / 10)),
      y: Math.max(0, Math.min(30, Math.round(yKm * 10) / 10))
    };
  }

  function getZoneAt(x, y) {
    if (x < 0 || x > 30 || y < 0 || y > 30) return 'Outside Map';
    if (x <= 15 && y >= 15) return 'North-West';
    if (x > 15 && y >= 15) return 'North-East';
    if (x <= 15 && y < 15) return 'South-West';
    return 'South-East';
  }

  // --- API Client ---
  async function apiCall(endpoint, method = 'GET', body = null) {
    try {
      const options = { method, headers: {} };
      if (body) {
        options.headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(body);
      }
      const res = await fetch(endpoint, options);
      return await res.json();
    } catch (err) {
      log('API Error (' + endpoint + '): ' + err.message, 'danger');
      console.error('API call failed:', err);
      return null;
    }
  }

  async function fetchState() {
    const data = await apiCall('/api/state');
    if (!data) return;
    state.zones = data.zones || [];
    state.resources = data.resources || [];
    state.coverage = data.coverage || [];
    updateRosterView();
    updateCoverageView();
    renderMap();
  }

  // --- Audit Log ---
  function log(message, type = 'normal') {
    const now = new Date();
    const timeStr = now.toTimeString().split(' ')[0];
    const entry = document.createElement('div');
    entry.className = 'log-entry';
    entry.innerHTML = `<span class="log-time">[${timeStr}]</span> <span class="log-msg ${type}">${message}</span>`;
    logContainer.prepend(entry);
  }

  document.getElementById('btnClearLog').addEventListener('click', () => {
    logContainer.innerHTML = '';
  });

  // --- Render Map ---
  function renderMap() {
    const rect = canvasWrapper.getBoundingClientRect();
    const width = rect.width;
    const height = rect.height;
    if (width === 0 || height === 0) return;

    ctx.clearRect(0, 0, width, height);

    // 1. Draw Grid Background
    ctx.fillStyle = '#060a13';
    ctx.fillRect(0, 0, width, height);

    // Check under-covered zones
    const underCoveredZones = new Set();
    state.coverage.forEach(c => {
      if (c.underCovered) underCoveredZones.add(c.zone);
    });

    // 2. Draw 4 Sectors (Background Tints & Labels)
    const sectors = [
      { name: 'North-West', minX: 0, minY: 15, maxX: 15, maxY: 30 },
      { name: 'North-East', minX: 15, minY: 15, maxX: 30, maxY: 30 },
      { name: 'South-West', minX: 0, minY: 0, maxX: 15, maxY: 15 },
      { name: 'South-East', minX: 15, minY: 0, maxX: 30, maxY: 15 }
    ];

    sectors.forEach(s => {
      const pTopLeft = kmToPx(s.minX, s.maxY);
      const pBottomRight = kmToPx(s.maxX, s.minY);
      const w = pBottomRight.x - pTopLeft.x;
      const h = pBottomRight.y - pTopLeft.y;

      const isUnder = underCoveredZones.has(s.name);
      if (isUnder) {
        // Red pulse tint for under-covered sector
        const alpha = 0.08 + 0.04 * Math.sin(state.dashOffset * 0.1);
        ctx.fillStyle = `rgba(244, 63, 94, ${alpha})`;
        ctx.fillRect(pTopLeft.x, pTopLeft.y, w, h);

        ctx.strokeStyle = 'rgba(244, 63, 94, 0.4)';
        ctx.lineWidth = 2;
        ctx.strokeRect(pTopLeft.x + 2, pTopLeft.y + 2, w - 4, h - 4);
      } else {
        ctx.fillStyle = 'rgba(15, 23, 42, 0.3)';
        ctx.fillRect(pTopLeft.x, pTopLeft.y, w, h);
      }

      // Sector watermark text
      ctx.save();
      ctx.font = 'bold 15px JetBrains Mono, monospace';
      ctx.fillStyle = isUnder ? 'rgba(244, 63, 94, 0.4)' : 'rgba(148, 163, 184, 0.15)';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(s.name.toUpperCase() + (isUnder ? ' (UNDER-COVERED)' : ''), pTopLeft.x + w / 2, pTopLeft.y + h / 2);
      ctx.restore();
    });

    // 3. Grid Lines
    ctx.lineWidth = 1;
    for (let km = 0; km <= 30; km += 1) {
      const p1 = kmToPx(km, 0);
      const p2 = kmToPx(km, 30);
      const is5 = km % 5 === 0;
      const is15 = km === 15;

      ctx.strokeStyle = is15 ? 'rgba(6, 182, 212, 0.6)' : is5 ? 'rgba(51, 65, 85, 0.4)' : 'rgba(30, 41, 59, 0.2)';
      ctx.beginPath();
      ctx.moveTo(p1.x, p1.y);
      ctx.lineTo(p2.x, p2.y);
      ctx.stroke();

      const q1 = kmToPx(0, km);
      const q2 = kmToPx(30, km);
      ctx.strokeStyle = is15 ? 'rgba(6, 182, 212, 0.6)' : is5 ? 'rgba(51, 65, 85, 0.4)' : 'rgba(30, 41, 59, 0.2)';
      ctx.beginPath();
      ctx.moveTo(q1.x, q1.y);
      ctx.lineTo(q2.x, q2.y);
      ctx.stroke();

      // Axis labels (every 5 km)
      if (is5) {
        ctx.font = '10px JetBrains Mono, monospace';
        ctx.fillStyle = 'rgba(148, 163, 184, 0.7)';
        ctx.textAlign = 'center';
        ctx.fillText(km + 'k', p1.x, p1.y + 14);
        ctx.textAlign = 'right';
        ctx.fillText(km + 'k', q1.x - 6, q1.y + 3);
      }
    }

    // Outer grid boundary
    const bTopLeft = kmToPx(0, 30);
    const bBottomRight = kmToPx(30, 0);
    ctx.strokeStyle = 'rgba(6, 182, 212, 0.8)';
    ctx.lineWidth = 1.5;
    ctx.strokeRect(bTopLeft.x, bTopLeft.y, bBottomRight.x - bTopLeft.x, bBottomRight.y - bTopLeft.y);

    // 4. Draw Active Overlays

    // A. Range Search Box (Query 1)
    if (state.activeRangeBox) {
      const rb = state.activeRangeBox;
      const p1 = kmToPx(rb.minX, rb.maxY);
      const p2 = kmToPx(rb.maxX, rb.minY);
      const rw = p2.x - p1.x;
      const rh = p2.y - p1.y;

      ctx.save();
      ctx.fillStyle = 'rgba(6, 182, 212, 0.15)';
      ctx.fillRect(p1.x, p1.y, rw, rh);
      ctx.setLineDash([6, 4]);
      ctx.lineDashOffset = -state.dashOffset;
      ctx.strokeStyle = '#06b6d4';
      ctx.lineWidth = 2;
      ctx.strokeRect(p1.x, p1.y, rw, rh);
      ctx.restore();

      ctx.fillStyle = '#06b6d4';
      ctx.font = '11px JetBrains Mono, monospace';
      ctx.fillText(`Range: [${rb.minX}..${rb.maxX}] × [${rb.minY}..${rb.maxY}]`, p1.x + 6, p1.y - 6);
    }

    // B. Interactive Drag Selection Box
    if (state.dragStart && state.dragEnd) {
      const minX = Math.min(state.dragStart.x, state.dragEnd.x);
      const maxX = Math.max(state.dragStart.x, state.dragEnd.x);
      const minY = Math.min(state.dragStart.y, state.dragEnd.y);
      const maxY = Math.max(state.dragStart.y, state.dragEnd.y);
      const p1 = kmToPx(minX, maxY);
      const p2 = kmToPx(maxX, minY);

      ctx.save();
      ctx.fillStyle = 'rgba(245, 158, 11, 0.15)';
      ctx.fillRect(p1.x, p1.y, p2.x - p1.x, p2.y - p1.y);
      ctx.strokeStyle = '#f59e0b';
      ctx.setLineDash([4, 4]);
      ctx.lineWidth = 1.5;
      ctx.strokeRect(p1.x, p1.y, p2.x - p1.x, p2.y - p1.y);
      ctx.restore();
    }

    // C. Radius Search Circle (Query 3)
    if (state.activeRadiusCircle) {
      const rc = state.activeRadiusCircle;
      const cp = kmToPx(rc.x, rc.y);
      const rp = kmToPx(rc.x + rc.radiusKm, rc.y);
      const rPx = Math.abs(rp.x - cp.x);

      ctx.save();
      ctx.fillStyle = 'rgba(59, 130, 246, 0.12)';
      ctx.beginPath();
      ctx.arc(cp.x, cp.y, rPx, 0, Math.PI * 2);
      ctx.fill();

      ctx.setLineDash([6, 4]);
      ctx.lineDashOffset = state.dashOffset;
      ctx.strokeStyle = '#3b82f6';
      ctx.lineWidth = 2;
      ctx.stroke();
      ctx.restore();

      // Radius label
      ctx.fillStyle = '#60a5fa';
      ctx.font = '11px JetBrains Mono, monospace';
      ctx.fillText(`r = ${rc.radiusKm} km`, cp.x + rPx + 4, cp.y - 4);
    }

    // D. Relocation Vectors (Query 5)
    state.relocationVectors.forEach(rv => {
      const pFrom = kmToPx(rv.from.x, rv.from.y);
      const pTo = kmToPx(rv.to.x, rv.to.y);

      ctx.save();
      ctx.strokeStyle = '#f59e0b';
      ctx.lineWidth = 2.5;
      ctx.setLineDash([6, 3]);
      ctx.lineDashOffset = -state.dashOffset;
      ctx.beginPath();
      ctx.moveTo(pFrom.x, pFrom.y);
      ctx.lineTo(pTo.x, pTo.y);
      ctx.stroke();

      // Arrowhead at destination
      const angle = Math.atan2(pTo.y - pFrom.y, pTo.x - pFrom.x);
      ctx.fillStyle = '#f59e0b';
      ctx.setLineDash([]);
      ctx.beginPath();
      ctx.moveTo(pTo.x, pTo.y);
      ctx.lineTo(pTo.x - 12 * Math.cos(angle - Math.PI / 6), pTo.y - 12 * Math.sin(angle - Math.PI / 6));
      ctx.lineTo(pTo.x - 12 * Math.cos(angle + Math.PI / 6), pTo.y - 12 * Math.sin(angle + Math.PI / 6));
      ctx.closePath();
      ctx.fill();

      // Label
      ctx.font = 'bold 11px JetBrains Mono, monospace';
      ctx.fillStyle = '#fbbf24';
      ctx.fillText(`Relocate ${rv.id}`, (pFrom.x + pTo.x) / 2 + 8, (pFrom.y + pTo.y) / 2 - 8);
      ctx.restore();
    });

    // E. Nearest Query Vector (Query 2)
    if (state.nearestQuery && state.nearestQuery.targetRes) {
      const nq = state.nearestQuery;
      const p1 = kmToPx(nq.x, nq.y);
      const p2 = kmToPx(nq.targetRes.x, nq.targetRes.y);

      ctx.save();
      ctx.strokeStyle = '#38bdf8';
      ctx.lineWidth = 2;
      ctx.setLineDash([5, 3]);
      ctx.lineDashOffset = -state.dashOffset;
      ctx.beginPath();
      ctx.moveTo(p1.x, p1.y);
      ctx.lineTo(p2.x, p2.y);
      ctx.stroke();

      // Midpoint distance tag
      ctx.setLineDash([]);
      ctx.font = 'bold 11px JetBrains Mono, monospace';
      ctx.fillStyle = '#38bdf8';
      const midX = (p1.x + p2.x) / 2;
      const midY = (p1.y + p2.y) / 2;
      ctx.fillText(`${nq.distance} km`, midX + 6, midY - 6);
      ctx.restore();
    }

    // F. Dispatch Vectors (Query 4)
    if (state.activePlan && state.activePlan.incident) {
      const inc = state.activePlan.incident;
      const incPos = kmToPx(inc.x, inc.y);

      // Trajectories from all allocated units
      const units = [];
      if (state.activePlan.ambulances) {
        state.activePlan.ambulances.forEach(a => units.push({ res: a.resource, dist: a.distance, color: '#10b981', label: 'Ambulance' }));
      }
      if (state.activePlan.hospital) {
        units.push({ res: state.activePlan.hospital.resource, dist: state.activePlan.hospital.distance, color: '#3b82f6', label: 'Hospital' });
      }
      if (state.activePlan.fireTruck) {
        units.push({ res: state.activePlan.fireTruck.resource, dist: state.activePlan.fireTruck.distance, color: '#f97316', label: 'Fire' });
      }
      if (state.activePlan.police) {
        units.push({ res: state.activePlan.police.resource, dist: state.activePlan.police.distance, color: '#eab308', label: 'Police' });
      }

      units.forEach(u => {
        const uPos = kmToPx(u.res.x, u.res.y);
        ctx.save();
        ctx.strokeStyle = u.color;
        ctx.lineWidth = 2;
        ctx.setLineDash([6, 3]);
        ctx.lineDashOffset = -state.dashOffset;
        ctx.beginPath();
        ctx.moveTo(uPos.x, uPos.y);
        ctx.lineTo(incPos.x, incPos.y);
        ctx.stroke();

        // Distance marker
        ctx.font = '10px JetBrains Mono, monospace';
        ctx.fillStyle = u.color;
        ctx.fillText(`${u.dist}km`, (uPos.x + incPos.x) / 2 + 5, (uPos.y + incPos.y) / 2 - 5);
        ctx.restore();
      });

      // Draw Incident Beacon
      ctx.save();
      const pulseRad = 14 + 4 * Math.sin(state.dashOffset * 0.15);
      ctx.fillStyle = 'rgba(244, 63, 94, 0.25)';
      ctx.beginPath();
      ctx.arc(incPos.x, incPos.y, pulseRad, 0, Math.PI * 2);
      ctx.fill();

      ctx.fillStyle = '#f43f5e';
      ctx.beginPath();
      ctx.arc(incPos.x, incPos.y, 8, 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = '#fff';
      ctx.lineWidth = 1.5;
      ctx.stroke();

      ctx.font = 'bold 11px JetBrains Mono, monospace';
      ctx.fillStyle = '#fff';
      ctx.textAlign = 'center';
      ctx.fillText('🚨 ' + inc.id, incPos.x, incPos.y - 14);
      ctx.restore();
    }

    // 5. Draw Emergency Resources
    state.resources.forEach(r => {
      const pos = kmToPx(r.x, r.y);
      const isHighlighted = state.highlightedIds.has(r.id);

      ctx.save();

      // Highlight halo if selected
      if (isHighlighted) {
        ctx.fillStyle = 'rgba(6, 182, 212, 0.35)';
        ctx.beginPath();
        ctx.arc(pos.x, pos.y, 16, 0, Math.PI * 2);
        ctx.fill();
        ctx.strokeStyle = '#06b6d4';
        ctx.lineWidth = 2;
        ctx.stroke();
      }

      // Draw icon and symbol based on resource type
      if (r.type === 'AMBULANCE') {
        const isFree = r.available;
        ctx.fillStyle = isFree ? '#10b981' : '#f43f5e';
        ctx.beginPath();
        ctx.arc(pos.x, pos.y, 7, 0, Math.PI * 2);
        ctx.fill();
        ctx.strokeStyle = isFree ? '#34d399' : '#991b1b';
        ctx.lineWidth = 1.5;
        ctx.stroke();

        // Label
        ctx.font = 'bold 10px JetBrains Mono, monospace';
        ctx.fillStyle = isFree ? '#a7f3d0' : '#fca5a5';
        ctx.textAlign = 'center';
        ctx.fillText(r.id, pos.x, pos.y + 16);

      } else if (r.type === 'HOSPITAL') {
        // Hospital square cross badge
        ctx.fillStyle = '#1e3a8a';
        ctx.fillRect(pos.x - 8, pos.y - 8, 16, 16);
        ctx.strokeStyle = '#3b82f6';
        ctx.lineWidth = 1.5;
        ctx.strokeRect(pos.x - 8, pos.y - 8, 16, 16);

        // White cross
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(pos.x - 2, pos.y - 6, 4, 12);
        ctx.fillRect(pos.x - 6, pos.y - 2, 12, 4);

        // Name & ICU Badge
        ctx.font = 'bold 9px JetBrains Mono, monospace';
        ctx.fillStyle = '#93c5fd';
        ctx.textAlign = 'center';
        ctx.fillText(`${r.id} (${r.icuBeds} ICU)`, pos.x, pos.y + 18);

      } else if (r.type === 'FIRE_TRUCK') {
        ctx.fillStyle = r.available ? '#ea580c' : '#7c2d12';
        ctx.beginPath();
        ctx.moveTo(pos.x, pos.y - 8);
        ctx.lineTo(pos.x + 7, pos.y + 6);
        ctx.lineTo(pos.x - 7, pos.y + 6);
        ctx.closePath();
        ctx.fill();
        ctx.strokeStyle = '#fdba74';
        ctx.lineWidth = 1.5;
        ctx.stroke();

        ctx.font = 'bold 9px JetBrains Mono, monospace';
        ctx.fillStyle = '#fdba74';
        ctx.textAlign = 'center';
        ctx.fillText(r.id, pos.x, pos.y + 17);

      } else if (r.type === 'POLICE_UNIT') {
        ctx.fillStyle = r.available ? '#ca8a04' : '#713f12';
        ctx.beginPath();
        ctx.arc(pos.x, pos.y, 6.5, 0, Math.PI * 2);
        ctx.fill();
        ctx.strokeStyle = '#fde047';
        ctx.lineWidth = 1.5;
        ctx.stroke();

        ctx.font = 'bold 9px JetBrains Mono, monospace';
        ctx.fillStyle = '#fde047';
        ctx.textAlign = 'center';
        ctx.fillText(r.id, pos.x, pos.y + 17);
      }

      ctx.restore();
    });
  }

  // Animation loop for pulsing beacons and moving dashes
  function animate() {
    state.dashOffset = (state.dashOffset + 0.5) % 100;
    renderMap();
    state.animationFrameId = requestAnimationFrame(animate);
  }

  // --- Canvas Mouse Events ---
  canvas.addEventListener('mousemove', (e) => {
    const rect = canvas.getBoundingClientRect();
    const px = e.clientX - rect.left;
    const py = e.clientY - rect.top;
    const km = pxToKm(px, py);

    cursorCoords.textContent = `${km.x.toFixed(1)} , ${km.y.toFixed(1)}`;
    cursorSector.textContent = getZoneAt(km.x, km.y);

    if (state.dragStart) {
      state.dragEnd = km;
    }
  });

  canvas.addEventListener('mousedown', (e) => {
    if (state.clickMode === 'range') {
      const rect = canvas.getBoundingClientRect();
      const km = pxToKm(e.clientX - rect.left, e.clientY - rect.top);
      state.dragStart = km;
      state.dragEnd = km;
    }
  });

  window.addEventListener('mouseup', () => {
    if (state.clickMode === 'range' && state.dragStart && state.dragEnd) {
      const minX = Math.min(state.dragStart.x, state.dragEnd.x);
      const maxX = Math.max(state.dragStart.x, state.dragEnd.x);
      const minY = Math.min(state.dragStart.y, state.dragEnd.y);
      const maxY = Math.max(state.dragStart.y, state.dragEnd.y);

      if (maxX - minX > 0.5 && maxY - minY > 0.5) {
        rangeMinX.value = minX.toFixed(1);
        rangeMinY.value = minY.toFixed(1);
        rangeMaxX.value = maxX.toFixed(1);
        rangeMaxY.value = maxY.toFixed(1);
        handleRangeQuery(minX, minY, maxX, maxY);
      }
      state.dragStart = null;
      state.dragEnd = null;
    }
  });

  canvas.addEventListener('click', (e) => {
    if (state.clickMode === 'range') return; // Handled by drag

    const rect = canvas.getBoundingClientRect();
    const km = pxToKm(e.clientX - rect.left, e.clientY - rect.top);

    if (state.clickMode === 'dispatch') {
      dispX.value = km.x.toFixed(1);
      dispY.value = km.y.toFixed(1);
      switchTab('dispatch');
      log(`Incident coordinates set to (${km.x}, ${km.y}) in ${getZoneAt(km.x, km.y)}`);
    } else if (state.clickMode === 'nearest') {
      nearX.value = km.x.toFixed(1);
      nearY.value = km.y.toFixed(1);
      switchTab('nearest');
      handleNearestQuery(km.x, km.y);
    } else if (state.clickMode === 'radius') {
      radX.value = km.x.toFixed(1);
      radY.value = km.y.toFixed(1);
      switchTab('radius');
      handleRadiusQuery(km.x, km.y, parseFloat(radiusKm.value));
    }
  });

  // Mode button toggle
  document.querySelectorAll('.mode-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.mode-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      state.clickMode = btn.dataset.mode;
      log(`Map click mode switched to: [${btn.textContent.trim()}]`);
    });
  });

  document.getElementById('btnClearOverlays').addEventListener('click', () => {
    state.activePlan = null;
    state.activeRangeBox = null;
    state.activeRadiusCircle = null;
    state.nearestQuery = null;
    state.relocationVectors = [];
    state.highlightedIds.clear();
    dispatchResult.style.display = 'none';
    nearestResult.style.display = 'none';
    rangeResult.style.display = 'none';
    radiusResult.style.display = 'none';
    redistributeResult.style.display = 'none';
    renderMap();
    log('Cleared all map overlays.');
  });

  // --- Tabs Navigation ---
  function switchTab(tabId) {
    document.querySelectorAll('.deck-tab').forEach(t => {
      t.classList.toggle('active', t.dataset.tab === tabId);
    });
    document.querySelectorAll('.tab-pane').forEach(p => {
      p.classList.toggle('active', p.id === `pane-${tabId}`);
    });
  }

  document.querySelectorAll('.deck-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      switchTab(tab.dataset.tab);
    });
  });

  // Severity Picker Buttons
  document.querySelectorAll('.sev-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.sev-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      dispSeverity.value = btn.dataset.sev;
    });
  });

  // Radius Slider Update
  radiusKm.addEventListener('input', () => {
    radiusKmVal.textContent = `${parseFloat(radiusKm.value).toFixed(1)} km`;
    if (state.activeRadiusCircle) {
      state.activeRadiusCircle.radiusKm = parseFloat(radiusKm.value);
    }
  });

  // --- Handlers for Queries 1 - 6 ---

  // Query 4 & Point Location: Dispatch Incident
  formDispatch.addEventListener('submit', async (e) => {
    e.preventDefault();
    const x = parseFloat(dispX.value);
    const y = parseFloat(dispY.value);
    const type = dispType.value;
    const severity = dispSeverity.value;
    const id = 'E' + (Math.floor(Math.random() * 900) + 100);

    const payload = { id, x, y, type, severity };
    const res = await apiCall('/api/dispatch', 'POST', payload);
    if (!res || !res.success) {
      alert('Dispatch failed: ' + (res ? res.error : 'Network error'));
      return;
    }

    playDispatchChime();
    state.activePlan = res.plan;
    state.resources = res.state.resources;
    state.coverage = res.state.coverage;

    // Highlight all dispatched units
    state.highlightedIds.clear();
    if (res.plan.ambulances) res.plan.ambulances.forEach(a => state.highlightedIds.add(a.resource.id));
    if (res.plan.hospital) state.highlightedIds.add(res.plan.hospital.resource.id);
    if (res.plan.fireTruck) state.highlightedIds.add(res.plan.fireTruck.resource.id);
    if (res.plan.police) state.highlightedIds.add(res.plan.police.resource.id);

    // Update Result Box
    resZone.textContent = res.plan.zone ? res.plan.zone.name : 'Unknown';
    resZoneVal.textContent = res.plan.zone ? res.plan.zone.name : 'Outside mapped sectors';

    allocatedUnitsList.innerHTML = '';
    if (res.plan.ambulances) {
      res.plan.ambulances.forEach(a => {
        allocatedUnitsList.innerHTML += `
          <div class="allocated-unit">
            <span class="unit-icon">🚑</span>
            <div class="unit-details">
              <div class="unit-name">${a.resource.name} (${a.resource.id})</div>
              <div class="unit-sub">Coordinates: (${a.resource.x}, ${a.resource.y})</div>
            </div>
            <div class="unit-dist">${a.distance} km</div>
          </div>`;
      });
    }

    if (res.plan.hospital) {
      const h = res.plan.hospital;
      allocatedUnitsList.innerHTML += `
        <div class="allocated-unit" style="border-left-color: #3b82f6;">
          <span class="unit-icon">🏥</span>
          <div class="unit-details">
            <div class="unit-name">${h.resource.name} (${h.resource.id})</div>
            <div class="unit-sub">ICU Bed Reserved (${h.resource.icuBeds} remaining)</div>
          </div>
          <div class="unit-dist">${h.distance} km</div>
        </div>`;
    }

    if (res.plan.fireTruck) {
      const f = res.plan.fireTruck;
      allocatedUnitsList.innerHTML += `
        <div class="allocated-unit" style="border-left-color: #f97316;">
          <span class="unit-icon">🚒</span>
          <div class="unit-details">
            <div class="unit-name">${f.resource.name} (${f.resource.id})</div>
            <div class="unit-sub">Assigned Fire Engine</div>
          </div>
          <div class="unit-dist">${f.distance} km</div>
        </div>`;
    }

    if (res.plan.police) {
      const p = res.plan.police;
      allocatedUnitsList.innerHTML += `
        <div class="allocated-unit" style="border-left-color: #eab308;">
          <span class="unit-icon">🚓</span>
          <div class="unit-details">
            <div class="unit-name">${p.resource.name} (${p.resource.id})</div>
            <div class="unit-sub">Assigned Patrol Unit</div>
          </div>
          <div class="unit-dist">${p.distance} km</div>
        </div>`;
    }

    unmetAlerts.innerHTML = '';
    if (res.plan.unmet && res.plan.unmet.length > 0) {
      res.plan.unmet.forEach(u => {
        unmetAlerts.innerHTML += `<div class="badge-alert" style="margin-top:4px; display:inline-block;">⚠️ ${u}</div>`;
      });
    }

    dispatchResult.style.display = 'block';
    updateRosterView();
    updateCoverageView();
    renderMap();

    log(`🚨 Dispatched [${id}]: ${type} (${severity}) at (${x}, ${y}) in ${res.plan.zone ? res.plan.zone.name : 'Unknown'}.`, 'danger');
  });

  // Query 2: Nearest Available Ambulance
  async function handleNearestQuery(x, y) {
    const res = await apiCall('/api/query/nearest', 'POST', { x, y });
    if (!res || !res.success) return;

    if (res.nearest) {
      state.nearestQuery = { x, y, targetRes: res.nearest, distance: res.distance };
      state.highlightedIds.clear();
      state.highlightedIds.add(res.nearest.id);

      nearResName.textContent = `${res.nearest.id} (${res.nearest.name})`;
      nearResPos.textContent = `(${res.nearest.x}, ${res.nearest.y})`;
      nearResDist.textContent = `${res.distance} km`;
      nearestResult.style.display = 'block';

      playTone(660, 0.15, 'sine');
      log(`Nearest ambulance to (${x}, ${y}): ${res.nearest.id} at distance ${res.distance} km.`, 'highlight');
    } else {
      nearResName.textContent = 'None Available';
      nearResPos.textContent = '--';
      nearResDist.textContent = '--';
      nearestResult.style.display = 'block';
      log(`No available ambulance found near (${x}, ${y})!`, 'warn');
    }
    renderMap();
  }

  formNearest.addEventListener('submit', (e) => {
    e.preventDefault();
    handleNearestQuery(parseFloat(nearX.value), parseFloat(nearY.value));
  });

  // Query 1: Orthogonal Range Search
  async function handleRangeQuery(minX, minY, maxX, maxY) {
    const res = await apiCall('/api/query/range', 'POST', { minX, minY, maxX, maxY });
    if (!res || !res.success) return;

    state.activeRangeBox = { minX, minY, maxX, maxY };
    state.highlightedIds.clear();
    res.ambulances.forEach(a => state.highlightedIds.add(a.id));

    rangeCount.textContent = res.ambulances.length;
    rangeList.innerHTML = '';

    if (res.ambulances.length === 0) {
      rangeList.innerHTML = '<div style="color: var(--text-muted); padding: 8px;">No available ambulances in this region.</div>';
    } else {
      res.ambulances.forEach(a => {
        rangeList.innerHTML += `
          <div class="result-item">
            <span class="result-label">🚑 ${a.name} (${a.id})</span>
            <span class="result-value">(${a.x}, ${a.y})</span>
          </div>`;
      });
    }

    rangeResult.style.display = 'block';
    renderMap();
    log(`Range query [${minX}..${maxX}] × [${minY}..${maxY}] reported ${res.ambulances.length} available ambulances.`, 'highlight');
  }

  formRange.addEventListener('submit', (e) => {
    e.preventDefault();
    handleRangeQuery(
      parseFloat(rangeMinX.value),
      parseFloat(rangeMinY.value),
      parseFloat(rangeMaxX.value),
      parseFloat(rangeMaxY.value)
    );
  });

  // Query 3: ICU Hospitals within Radius
  async function handleRadiusQuery(x, y, radius) {
    const res = await apiCall('/api/query/radius', 'POST', { x, y, radiusKm: radius });
    if (!res || !res.success) return;

    state.activeRadiusCircle = { x, y, radiusKm: radius };
    state.highlightedIds.clear();
    res.hospitals.forEach(h => state.highlightedIds.add(h.resource.id));

    radiusCount.textContent = res.hospitals.length;
    radiusList.innerHTML = '';

    if (res.hospitals.length === 0) {
      radiusList.innerHTML = '<div style="color: var(--text-muted); padding: 8px;">No hospitals with ICU within radius.</div>';
    } else {
      res.hospitals.forEach(h => {
        radiusList.innerHTML += `
          <div class="result-item">
            <div>
              <div style="font-weight:600; color:#93c5fd;">🏥 ${h.resource.name} (${h.resource.id})</div>
              <div style="font-size:0.7rem; color:var(--text-muted);">ICU beds: ${h.resource.icuBeds} free</div>
            </div>
            <span class="result-value" style="color: #38bdf8;">${h.distance} km</span>
          </div>`;
      });
    }

    radiusResult.style.display = 'block';
    renderMap();
    log(`Radius scan around (${x}, ${y}) within ${radius} km found ${res.hospitals.length} ICU hospitals.`, 'highlight');
  }

  formRadius.addEventListener('submit', (e) => {
    e.preventDefault();
    handleRadiusQuery(parseFloat(radX.value), parseFloat(radY.value), parseFloat(radiusKm.value));
  });

  // Query 5: Dynamic Redistribution
  document.getElementById('btnRedistribute').addEventListener('click', async () => {
    const res = await apiCall('/api/redistribute', 'POST');
    if (!res || !res.success) {
      alert('Redistribution failed: ' + (res ? res.error : 'Network error'));
      return;
    }

    state.resources = res.state.resources;
    state.coverage = res.state.coverage;

    redistributeMoves.innerHTML = '';
    state.relocationVectors = [];

    if (!res.moves || res.moves.length === 0) {
      redistributeMoves.innerHTML = '<div style="color: #34d399; padding: 6px;">All sectors covered! No relocations required.</div>';
    } else {
      res.moves.forEach(m => {
        redistributeMoves.innerHTML += `<div style="padding: 4px 0; border-bottom: 1px solid rgba(255,255,255,0.05);">${m}</div>`;

        // Parse move string for animation: "Sector: relocating A6 from (27.0,20.0) to (22.5,7.5)"
        const match = m.match(/relocating\s+([A-Z0-9]+)\s+from\s+\(([\d.]+),([\d.]+)\)\s+to\s+\(([\d.]+),([\d.]+)\)/i);
        if (match) {
          state.relocationVectors.push({
            id: match[1],
            from: { x: parseFloat(match[2]), y: parseFloat(match[3]) },
            to: { x: parseFloat(match[4]), y: parseFloat(match[5]) }
          });
        }
      });
      playTone(523.25, 0.2, 'square');
      log(`Dynamic redistribution completed: ${res.moves.length} relocation action(s).`, 'warn');
    }

    redistributeResult.style.display = 'block';
    updateRosterView();
    updateCoverageView();
    renderMap();
  });

  document.getElementById('btnRefreshCoverage').addEventListener('click', () => {
    fetchState();
    log('Coverage map refreshed.');
  });

  // Query 6 & Roster: Update UI Tables
  function updateCoverageView() {
    coverageTableBody.innerHTML = '';
    let underCount = 0;
    state.coverage.forEach(c => {
      if (c.underCovered) underCount++;
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td style="font-weight:600; color:#f1f5f9;">${c.zone}</td>
        <td><span style="font-weight:700; color:${c.ambulances === 0 ? '#f43f5e' : '#34d399'}">${c.ambulances}</span></td>
        <td>${c.hasIcu ? '<span style="color:#34d399">Yes</span>' : '<span style="color:#f43f5e">No</span>'}</td>
        <td>${c.nearestAmbulance ? `${c.nearestAmbulance} @ ${c.nearestDistance}km` : 'none'}</td>
        <td>${c.underCovered ? '<span class="badge-alert">UNDER-COVERED</span>' : '<span class="badge-ok">OK</span>'}</td>
      `;
      coverageTableBody.appendChild(tr);
    });

    if (underCount > 0) {
      systemStatusText.textContent = `ALERT: ${underCount} SECTOR UNDER-COVERED`;
      systemStatusText.parentElement.style.borderColor = '#f43f5e';
      systemStatusText.parentElement.style.color = '#fb7185';
    } else {
      systemStatusText.textContent = `ONLINE · ${state.resources.length} UNITS`;
      systemStatusText.parentElement.style.borderColor = 'rgba(16, 185, 129, 0.4)';
      systemStatusText.parentElement.style.color = '#34d399';
    }
  }

  function updateRosterView() {
    rosterList.innerHTML = '';
    state.resources.forEach(r => {
      const isMobile = r.type !== 'HOSPITAL';
      const item = document.createElement('div');
      item.className = 'result-item';
      item.style.padding = '8px';
      item.style.background = 'rgba(30, 41, 59, 0.3)';
      item.style.borderRadius = '6px';
      item.style.marginBottom = '4px';

      let statusBadge = '';
      if (isMobile) {
        statusBadge = r.available
          ? '<span class="badge-ok">AVAILABLE</span>'
          : '<span class="badge-alert">BUSY</span>';
      } else {
        statusBadge = `<span class="tag cyan">${r.icuBeds} ICU beds</span>`;
      }

      let actionBtn = '';
      if (isMobile && !r.available) {
        actionBtn = `<button class="btn btn-secondary btn-sm" style="font-size:0.68rem; padding: 2px 6px;" onclick="app.returnToService('${r.id}')">Return Free</button>`;
      }

      item.innerHTML = `
        <div style="display:flex; align-items:center; gap:8px;">
          <span>${r.type === 'AMBULANCE' ? '🚑' : r.type === 'HOSPITAL' ? '🏥' : r.type === 'FIRE_TRUCK' ? '🚒' : '🚓'}</span>
          <div>
            <div style="font-weight:600; font-size:0.8rem;">${r.id} · ${r.name}</div>
            <div style="font-family:var(--font-mono); font-size:0.7rem; color:var(--text-muted);">(${r.x}, ${r.y}) · ${getZoneAt(r.x, r.y)}</div>
          </div>
        </div>
        <div style="display:flex; align-items:center; gap:8px;">
          ${statusBadge}
          ${actionBtn}
        </div>
      `;
      rosterList.appendChild(item);
    });
  }

  // --- Reset & Return to Service ---
  document.getElementById('btnResetCity').addEventListener('click', async () => {
    if (!confirm('Reset city to initial reference state? All dispatches and relocations will be reset.')) return;
    const res = await apiCall('/api/reset', 'POST');
    if (!res || !res.success) return;

    state.resources = res.state.resources;
    state.coverage = res.state.coverage;
    state.activePlan = null;
    state.activeRangeBox = null;
    state.activeRadiusCircle = null;
    state.nearestQuery = null;
    state.relocationVectors = [];
    state.highlightedIds.clear();

    dispatchResult.style.display = 'none';
    nearestResult.style.display = 'none';
    rangeResult.style.display = 'none';
    radiusResult.style.display = 'none';
    redistributeResult.style.display = 'none';

    updateRosterView();
    updateCoverageView();
    renderMap();
    log('City model reset to initial state from CSV.', 'highlight');
  });

  document.getElementById('btnReturnAll').addEventListener('click', async () => {
    const res = await apiCall('/api/return-service', 'POST', { all: true });
    if (!res || !res.success) return;
    state.resources = res.state.resources;
    state.coverage = res.state.coverage;
    updateRosterView();
    updateCoverageView();
    renderMap();
    log('All mobile units returned to available service.', 'highlight');
  });

  // --- Modals ---
  document.getElementById('btnTheory').addEventListener('click', () => {
    theoryModal.classList.add('active');
  });
  document.getElementById('modalClose').addEventListener('click', () => {
    theoryModal.classList.remove('active');
  });
  theoryModal.addEventListener('click', (e) => {
    if (e.target === theoryModal) theoryModal.classList.remove('active');
  });

  document.getElementById('btnScenarios').addEventListener('click', () => {
    scenariosModal.classList.add('active');
  });
  document.getElementById('scenariosClose').addEventListener('click', () => {
    scenariosModal.classList.remove('active');
  });
  scenariosModal.addEventListener('click', (e) => {
    if (e.target === scenariosModal) scenariosModal.classList.remove('active');
  });

  // --- Public Methods (Attached to window.app) ---
  window.app = {
    setRangePreset: function (minX, minY, maxX, maxY) {
      rangeMinX.value = minX.toFixed(1);
      rangeMinY.value = minY.toFixed(1);
      rangeMaxX.value = maxX.toFixed(1);
      rangeMaxY.value = maxY.toFixed(1);
      handleRangeQuery(minX, minY, maxX, maxY);
    },

    returnToService: async function (id) {
      const res = await apiCall('/api/return-service', 'POST', { resourceId: id });
      if (!res || !res.success) return;
      state.resources = res.state.resources;
      state.coverage = res.state.coverage;
      updateRosterView();
      updateCoverageView();
      renderMap();
      log(`Unit ${id} returned to available service.`, 'highlight');
    },

    runScenario: async function (scenarioNum) {
      scenariosModal.classList.remove('active');

      if (scenarioNum === 1) {
        // Scenario 1: Critical Highway Pileup in North-East
        dispX.value = '19.0';
        dispY.value = '24.0';
        dispType.value = 'ROAD_ACCIDENT';
        dispSeverity.value = 'CRITICAL';
        document.querySelectorAll('.sev-btn').forEach(b => b.classList.toggle('active', b.dataset.sev === 'CRITICAL'));
        switchTab('dispatch');
        formDispatch.dispatchEvent(new Event('submit'));

      } else if (scenarioNum === 2) {
        // Scenario 2: Double Incident in South-East -> Depletes A7 and A8 -> Under-covered alert
        log('Executing Scenario 2: Double incident in South-East...', 'warn');
        await apiCall('/api/dispatch', 'POST', { id: 'E4', x: 20, y: 6, type: 'MEDICAL', severity: 'MODERATE' });
        const res2 = await apiCall('/api/dispatch', 'POST', { id: 'E5', x: 26, y: 9, type: 'MEDICAL', severity: 'MODERATE' });
        if (res2) {
          state.resources = res2.state.resources;
          state.coverage = res2.state.coverage;
          updateRosterView();
          updateCoverageView();
          renderMap();
          switchTab('coverage');
          playAlertChime();
          log('South-East ambulances A7 and A8 dispatched. Sector is now UNDER-COVERED! Click "Rebalance Coverage Now" to redistribute.', 'danger');
        }

      } else if (scenarioNum === 3) {
        // Scenario 3: Factory Chemical Fire in South-West
        dispX.value = '5.0';
        dispY.value = '6.0';
        dispType.value = 'FIRE';
        dispSeverity.value = 'HIGH';
        document.querySelectorAll('.sev-btn').forEach(b => b.classList.toggle('active', b.dataset.sev === 'HIGH'));
        switchTab('dispatch');
        formDispatch.dispatchEvent(new Event('submit'));
      }
    }
  };

  // --- Initialize ---
  resizeCanvas();
  fetchState();
  animate();

})();
