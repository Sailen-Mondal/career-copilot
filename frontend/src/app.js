/* ═══════════════════════════════════════════════════════════
   Career Copilot — Dashboard Application Logic
   ═══════════════════════════════════════════════════════════ */

'use strict';

// ── Config ────────────────────────────────────────────────────────────────────
const API_BASE   = 'http://localhost:8080';
const API_HEADERS = { 'Content-Type': 'application/json', 'X-API-Key': 'dev-insecure-key' };
const POLL_MS    = 10_000;

// ── Mock fallback data ────────────────────────────────────────────────────────
const MOCK_APPLICATIONS = [
  {
    id: '1',
    company: 'Northstar Systems',
    title: 'Senior Backend Engineer',
    matchScore: 92,
    status: 'submitted',
    reason: 'All checks passed',
    submittedAt: new Date().toISOString(),
    facts: ['3+ yrs Java Exp', 'Redis Streams', 'Modular Monoliths', 'System Dedup']
  },
  {
    id: '2',
    company: 'BrightLayer Health',
    title: 'Platform Engineer',
    matchScore: 88,
    status: 'generating',
    reason: 'Shadow run in progress',
    submittedAt: null,
    facts: ['Docker/K8s', 'Spring Security', 'Playwright shadow runs', 'API Key Auth']
  },
  {
    id: '3',
    company: 'Atlas Grid',
    title: 'Staff Java Engineer',
    matchScore: 74,
    status: 'skipped',
    reason: 'Below threshold (85)',
    submittedAt: null,
    facts: ['Java Monoliths', 'PostgreSQL', 'High match score required']
  },
  {
    id: '4',
    company: 'BlockedCo',
    title: 'Backend Lead',
    matchScore: 95,
    status: 'blocked',
    reason: 'Company blocklist',
    submittedAt: null,
    facts: ['Spring Boot expert', 'Monolith refactoring', 'System blocklist fail']
  },
  {
    id: '5',
    company: 'Meridian AI',
    title: 'ML Platform Engineer',
    matchScore: 89,
    status: 'submitted',
    reason: 'All checks passed',
    submittedAt: new Date(Date.now() - 3_600_000).toISOString(),
    facts: ['Python & Java APIs', 'Redis streams ingestion', 'Groundedness checks']
  }
];

const MOCK_STATS = {
  totalDiscovered: 47,
  appliedToday: 3,
  avgMatchScore: 84,
  successRate: 60
};

const MOCK_EVENTS = [
  { timestamp: new Date(Date.now() - 120_000).toISOString(), message: 'Application submitted to Northstar Systems (score 92)' },
  { timestamp: new Date(Date.now() - 300_000).toISOString(), message: 'Shadow run completed for BrightLayer Health — ready for submission' },
  { timestamp: new Date(Date.now() - 480_000).toISOString(), message: 'Atlas Grid skipped — match score 74 below threshold 85' },
  { timestamp: new Date(Date.now() - 900_000).toISOString(), message: 'BlockedCo application blocked — found in company blocklist' },
  { timestamp: new Date(Date.now() - 3_600_000).toISOString(), message: 'Application submitted to Meridian AI (score 89)' },
  { timestamp: new Date(Date.now() - 7_200_000).toISOString(), message: 'Autonomy threshold updated to 85' },
  { timestamp: new Date(Date.now() - 10_800_000).toISOString(), message: 'Automation engine started — Shadow mode active' }
];

const MOCK_REASONING_LOGS = [
  { time: 'Just now', text: 'Auto-matcher scanning Greenhouse stream. Discovered 12 potential roles.' },
  { time: '2m ago', text: 'Verified facts for Northstar Systems: Candidate has 3+ yrs Monolith experience.' },
  { time: '5m ago', text: 'GroundednessVerifier passed: Resume facts match verified master profile.' },
  { time: '15m ago', text: 'Autopilot shadow-run enqueued to TypeScript Playwright worker stream.' },
  { time: '1h ago', text: 'BlockedCo skipped: company matched blacklisted keyword rules.' }
];

// ── State ─────────────────────────────────────────────────────────────────────
let appState = {
  halted: false,
  shadowMode: true,
  threshold: 85,
  applications: [],
  selectedApplicationId: null,
  currentView: 'dashboard',
  pollTimer: null,
  thresholdDebounceTimer: null
};

// ── DOM references ────────────────────────────────────────────────────────────
const $ = (id) => document.getElementById(id);

const els = {
  automationState:        $('automationState'),
  statusDot:              $('statusDot'),
  lastRefreshed:          $('lastRefreshed'),
  statDiscovered:         $('val-discovered'),
  statApplied:            $('val-applied'),
  statAvgScore:           $('val-avg-score'),
  statSuccessRate:        $('val-success-rate'),
  threshold:              $('threshold'),
  thresholdValue:         $('thresholdValue'),
  killSwitch:             $('killSwitch'),
  shadowMode:             $('shadowMode'),
  applicationRows:        $('applicationRows'),
  applicationCount:       $('applicationCount'),
  refreshIndicator:       $('refreshIndicator'),
  auditToggle:            $('auditToggle'),
  auditLogWrap:           $('auditLogWrap'),
  auditLog:               $('auditLog'),
  auditCount:             $('auditCount'),

  // Header inline summary items
  hdrDiscovered:          $('hdrDiscovered'),
  hdrApplied:             $('hdrApplied'),
  hdrScore:               $('hdrScore'),

  // Layout items
  sidebar:                $('sidebar'),
  mainWrapper:            $('mainWrapper'),
  inspector:              $('inspector'),
  sidebarCollapseBtn:     $('sidebarCollapseBtn'),
  sidebarExpandBtn:       $('sidebarExpandBtn'),
  inspectorCloseBtn:      $('inspectorCloseBtn'),
  breadcrumbCurrent:      $('breadcrumbCurrent'),
  pageTitle:              $('pageTitle'),
  pageSubtitle:           $('pageSubtitle'),
  sidebarStatusDot:       $('sidebarStatusDot'),
  sidebarStatusText:      $('sidebarStatusText'),

  // Nav Links
  navDashboard:           $('nav-dashboard'),
  navApplications:        $('nav-applications'),
  navDiscovery:           $('nav-discovery'),
  navMatching:            $('nav-matching'),
  navGeneration:          $('nav-generation'),
  navAutomation:          $('nav-automation'),
  navAudit:               $('nav-audit'),
  navSettings:            $('nav-settings'),
  navAppCount:            $('navAppCount'),

  // Inspector details
  inspectorEmpty:         $('inspectorEmpty'),
  inspectorDetail:        $('inspectorDetail'),
  inspectorCompany:       $('inspectorCompany'),
  inspectorRole:          $('inspectorRole'),
  inspectorScoreWrap:     $('inspectorScoreWrap'),
  inspectorStatus:        $('inspectorStatus'),
  inspectorReason:        $('inspectorReason'),
  inspectorTime:          $('inspectorTime'),
  inspectorFacts:         $('inspectorFacts'),
  liveTimeline:           $('liveTimeline'),

  // Command Palette
  searchBtn:              $('searchBtn'),
  commandPaletteOverlay:  $('commandPaletteOverlay'),
  commandPalette:         $('commandPalette'),
  commandPaletteInput:    $('commandPaletteInput'),
  commandPaletteResults:  $('commandPaletteResults'),
  commandPaletteCloseBtn: $('commandPaletteCloseBtn'),

  // New views elements
  discoverySection:       $('discovery-section'),
  matchingSection:        $('matching-section'),
  generationSection:      $('generation-section'),
  settingsSection:        $('settings-section'),
  discoveredJobRows:      $('discoveredJobRows'),
  syncBoardInput:         $('syncBoardInput'),
  triggerSyncBtn:         $('triggerSyncBtn'),
  profileForm:            $('profileForm'),
  profileName:            $('profileName'),
  profileEmail:           $('profileEmail'),
  profilePhone:           $('profilePhone'),
  profileWorkAuth:        $('profileWorkAuth'),
  profileLocations:       $('profileLocations'),
  settingsFactsList:      $('settingsFactsList')
};

// ── API helpers ───────────────────────────────────────────────────────────────
async function apiFetch(path, options = {}) {
  const url = `${API_BASE}${path}`;
  const res = await fetch(url, { headers: API_HEADERS, ...options });
  if (!res.ok) throw new Error(`HTTP ${res.status} on ${path}`);
  return res.json();
}

// ── Fetch functions (with mock fallbacks) ─────────────────────────────────────
async function fetchApplications() {
  try {
    return await apiFetch('/api/applications');
  } catch {
    return MOCK_APPLICATIONS;
  }
}

async function fetchStats() {
  try {
    return await apiFetch('/api/applications/stats');
  } catch {
    return MOCK_STATS;
  }
}

async function fetchAutomationStatus() {
  try {
    return await apiFetch('/api/automation/status');
  } catch {
    return { halted: false };
  }
}

async function fetchAuditEvents() {
  try {
    const data = await apiFetch('/api/automation/events');
    return Array.isArray(data) ? data : (data.events ?? []);
  } catch {
    return MOCK_EVENTS;
  }
}

// ── Render: Stats cards with count-up animation ───────────────────────────────
function animateCountUp(el, target, suffix = '') {
  const start = 0;
  const duration = 800;
  const startTime = performance.now();

  function step(now) {
    const elapsed = now - startTime;
    const progress = Math.min(elapsed / duration, 1);
    const eased = 1 - Math.pow(1 - progress, 3);
    const current = Math.round(start + (target - start) * eased);
    el.textContent = current + suffix;
    if (progress < 1) requestAnimationFrame(step);
  }
  requestAnimationFrame(step);
}

function renderStats(stats) {
  const discovered  = stats.totalDiscovered  ?? 0;
  const applied     = stats.appliedToday     ?? 0;
  const avgScore    = stats.avgMatchScore    ?? 0;
  const successRate = stats.successRate      ?? 0;

  // Render main metric cards
  animateCountUp(els.statDiscovered,  discovered,  '');
  animateCountUp(els.statApplied,     applied,     '');
  animateCountUp(els.statAvgScore,    avgScore,    '');
  animateCountUp(els.statSuccessRate, successRate, '%');

  // Sync inline page header summaries
  if (els.hdrDiscovered) els.hdrDiscovered.textContent = discovered;
  if (els.hdrApplied) els.hdrApplied.textContent = applied;
  if (els.hdrScore) els.hdrScore.textContent = avgScore + '%';
}

// ── Render: Score bar ─────────────────────────────────────────────────────────
function scoreClass(score) {
  if (score >= 80) return 'score-high';
  if (score >= 60) return 'score-mid';
  return 'score-low';
}

function renderScoreBar(score) {
  const cls = scoreClass(score);
  return `
    <div class="score-bar">
      <div class="score-track">
        <div class="score-fill ${cls}" style="width:${score}%"></div>
      </div>
      <span class="score-label ${cls}">${score}</span>
    </div>`;
}

// ── Render: Status badge ──────────────────────────────────────────────────────
const STATUS_LABELS = {
  submitted: 'Submitted',
  generating: 'Generating',
  skipped: 'Skipped',
  blocked: 'Blocked',
  failed: 'Failed',
  ready: 'Ready'
};

function renderStatusBadge(status) {
  const label = STATUS_LABELS[status] ?? status;
  return `<span class="status ${status}"><span class="status-pip" aria-hidden="true"></span>${label}</span>`;
}

// ── Render: Relative time ─────────────────────────────────────────────────────
function relativeTime(isoString) {
  if (!isoString) return '—';
  const diff = Date.now() - new Date(isoString).getTime();
  const mins  = Math.floor(diff / 60_000);
  const hours = Math.floor(diff / 3_600_000);
  if (mins < 1)   return 'Just now';
  if (mins < 60)  return `${mins}m ago`;
  if (hours < 24) return `${hours}h ago`;
  return new Date(isoString).toLocaleDateString();
}

// ── Render: Applications table ────────────────────────────────────────────────
function renderApplications(apps) {
  appState.applications = apps;

  if (els.navAppCount) {
    els.navAppCount.textContent = apps.length;
  }

  if (!apps || apps.length === 0) {
    els.applicationRows.innerHTML = `
      <tr>
        <td colspan="6" style="text-align:center;padding:40px;color:var(--text-muted)">
          No applications found.
        </td>
      </tr>`;
    els.applicationCount.textContent = '0 records';
    return;
  }

  els.applicationCount.textContent = `${apps.length} record${apps.length !== 1 ? 's' : ''}`;

  const html = apps.map((app) => {
    const score  = app.matchScore ?? app.score ?? 0;
    const title  = app.title      ?? app.role  ?? '—';
    const reason = app.reason     ?? '—';
    const time   = relativeTime(app.submittedAt ?? app.createdAt ?? null);
    const isSelected = appState.selectedApplicationId === app.id ? 'class="selected"' : '';

    return `
      <tr data-id="${app.id ?? ''}" ${isSelected}>
        <td class="td-company">${escHtml(app.company ?? '—')}</td>
        <td class="td-role">${escHtml(title)}</td>
        <td>${renderScoreBar(score)}</td>
        <td>${renderStatusBadge(app.status ?? 'ready')}</td>
        <td class="td-reason">${escHtml(reason)}</td>
        <td class="td-time">${escHtml(time)}</td>
      </tr>`;
  }).join('');

  els.applicationRows.innerHTML = html;

  // Add click handlers for the table rows to open inspector
  els.applicationRows.querySelectorAll('tr').forEach((tr) => {
    tr.addEventListener('click', () => {
      const id = tr.getAttribute('data-id');
      selectApplication(id);
    });
  });

  // Re-sync inspector details if one is already selected
  if (appState.selectedApplicationId) {
    const app = apps.find(a => a.id === appState.selectedApplicationId);
    if (app) {
      updateInspectorDetails(app);
    }
  }
}

// ── Render: Live Reasoning Timeline (Brand Identity) ─────────────────────────
function renderReasoningTimeline() {
  if (!els.liveTimeline) return;
  els.liveTimeline.innerHTML = MOCK_REASONING_LOGS.map(log => `
    <div class="timeline-item">
      <div class="timeline-icon-wrap">
        <i data-lucide="check-circle-2" class="icon-xs"></i>
      </div>
      <div class="timeline-content-wrap">
        <span class="timeline-time">${log.time}</span>
        <p class="timeline-text">${log.text}</p>
      </div>
    </div>
  `).join('');
  if (window.lucide) window.lucide.createIcons();
}

// ── Render: Inspector Details ─────────────────────────────────────────────────
function selectApplication(id) {
  appState.selectedApplicationId = id;
  
  // Highlight row in UI
  els.applicationRows.querySelectorAll('tr').forEach((row) => {
    if (row.getAttribute('data-id') === id) {
      row.classList.add('selected');
    } else {
      row.classList.remove('selected');
    }
  });

  // Find application object
  const app = appState.applications.find(a => a.id === id);
  if (app) {
    updateInspectorDetails(app);
    // Expand inspector if collapsed
    document.querySelector('.app-shell').classList.remove('inspector-collapsed');
  }
}

function updateInspectorDetails(app) {
  els.inspectorEmpty.hidden = true;
  els.inspectorDetail.hidden = false;

  els.inspectorCompany.textContent = app.company ?? '—';
  els.inspectorRole.textContent = app.title ?? app.role ?? '—';
  els.inspectorScoreWrap.innerHTML = renderScoreBar(app.matchScore ?? app.score ?? 0);
  els.inspectorStatus.innerHTML = renderStatusBadge(app.status ?? 'ready');
  els.inspectorReason.textContent = app.reason ?? '—';
  els.inspectorTime.textContent = app.submittedAt ? new Date(app.submittedAt).toLocaleString() : 'Not submitted yet';

  // Render facts tags dynamically
  if (els.inspectorFacts) {
    const facts = app.facts ?? ['Verified facts', 'Autopilot checks passed'];
    els.inspectorFacts.innerHTML = facts.map(f => `
      <div class="fact-tag"><i data-lucide="check" class="icon-xs"></i> ${escHtml(f)}</div>
    `).join('');
  }

  // Update document statuses dynamically
  const docsEl = $('inspectorDocs');
  if (docsEl) {
    let resumeStatus = 'Pending';
    let coverLetterStatus = 'Pending';

    if (app.status === 'submitted') {
      resumeStatus = 'Sent';
      coverLetterStatus = 'Sent';
    } else if (app.status === 'generating') {
      resumeStatus = 'Generating...';
      coverLetterStatus = 'Generating...';
    } else if (app.status === 'skipped' || app.status === 'blocked' || app.status === 'failed') {
      resumeStatus = 'Not created';
      coverLetterStatus = 'Not created';
    }

    docsEl.innerHTML = `
      <div class="inspector-doc-item">
        <i data-lucide="file-text" class="icon-sm"></i>
        <span>Tailored Resume</span>
        <span class="inspector-doc-status">${resumeStatus}</span>
      </div>
      <div class="inspector-doc-item" style="margin-top: 8px;">
        <i data-lucide="mail" class="icon-sm"></i>
        <span>Cover Letter</span>
        <span class="inspector-doc-status">${coverLetterStatus}</span>
      </div>
    `;
  }
  if (window.lucide) window.lucide.createIcons();
}

function clearInspector() {
  appState.selectedApplicationId = null;
  els.applicationRows.querySelectorAll('tr').forEach(r => r.classList.remove('selected'));
  els.inspectorEmpty.hidden = false;
  els.inspectorDetail.hidden = true;
  renderReasoningTimeline();
}

// ── Render: Audit log ─────────────────────────────────────────────────────────
function renderAuditLog(events) {
  const slice = events.slice(0, 20);
  els.auditCount.textContent = slice.length;

  if (!slice.length) {
    els.auditLog.innerHTML = `<li class="audit-empty">No events recorded yet.</li>`;
    return;
  }

  els.auditLog.innerHTML = slice.map((ev) => {
    const ts  = ev.timestamp  ? new Date(ev.timestamp).toLocaleTimeString() : '';
    const msg = ev.message    ?? ev.event ?? ev.description ?? JSON.stringify(ev);
    return `
      <li class="audit-item">
        <time class="audit-timestamp">${escHtml(ts)}</time>
        <span class="audit-message">${escHtml(msg)}</span>
      </li>`;
  }).join('');
}

// ── Render: Automation state badge ────────────────────────────────────────────
function setAutomationState(halted) {
  appState.halted = halted;
  const label = halted ? 'PAUSED' : 'RUNNING';

  els.automationState.textContent = label;
  els.automationState.classList.toggle('paused', halted);
  els.statusDot.classList.toggle('paused', halted);

  if (els.sidebarStatusDot) {
    els.sidebarStatusDot.style.backgroundColor = halted ? 'var(--accent-red)' : 'var(--accent-green)';
    els.sidebarStatusText.textContent = halted ? 'Autopilot paused' : 'Autopilot Active';
  }

  // Kill switch button
  if (halted) {
    els.killSwitch.classList.add('paused');
    els.killSwitch.innerHTML = `
      <i data-lucide="play" class="icon-sm"></i>
      Resume Autopilot`;
  } else {
    els.killSwitch.classList.remove('paused');
    els.killSwitch.innerHTML = `
      <i data-lucide="pause" class="icon-sm"></i>
      Pause Autopilot`;
  }
  if (window.lucide) window.lucide.createIcons();
}

// ── Kill switch toggle ────────────────────────────────────────────────────────
async function toggleKillSwitch() {
  const action = appState.halted ? 'resume' : 'halt';
  const next   = !appState.halted;

  // Optimistic UI
  setAutomationState(next);

  try {
    await apiFetch('/api/automation/kill-switch', {
      method: 'POST',
      body: JSON.stringify({ action })
    });
  } catch {
    // Revert on error
    setAutomationState(!next);
    console.warn('[CareerCopilot] Kill-switch API unreachable — toggled UI only.');
  }
}

// ── Shadow mode toggle ────────────────────────────────────────────────────────
async function toggleShadowMode() {
  const current = els.shadowMode.getAttribute('aria-pressed') === 'true';
  const next    = !current;
  els.shadowMode.setAttribute('aria-pressed', String(next));
  appState.shadowMode = next;

  if (next) {
    els.shadowMode.innerHTML = `<i data-lucide="moon" class="icon-sm"></i> Shadow Mode`;
  } else {
    els.shadowMode.innerHTML = `<i data-lucide="sun" class="icon-sm"></i> Active Autopilot`;
  }

  if (window.lucide) window.lucide.createIcons();

  try {
    await apiFetch('/api/automation/shadow-mode', {
      method: 'PATCH',
      body: JSON.stringify({ enabled: next })
    });
  } catch {
    console.warn('[CareerCopilot] Shadow mode API unreachable — toggled UI only.');
  }
}

// ── Threshold slider ──────────────────────────────────────────────────────────
function onThresholdInput() {
  const val = Number(els.threshold.value);
  els.thresholdValue.value = val;
  appState.threshold = val;
}

function onThresholdChange() {
  clearTimeout(appState.thresholdDebounceTimer);
  appState.thresholdDebounceTimer = setTimeout(async () => {
    const val = appState.threshold;
    try {
      await apiFetch('/api/profile/default/autonomy-threshold', {
        method: 'PATCH',
        body: JSON.stringify({ threshold: val })
      });
      console.log(`[CareerCopilot] Threshold saved: ${val}`);
    } catch {
      console.warn('[CareerCopilot] Threshold PATCH failed — backend unreachable.');
    }
  }, 600);
}

// ── Audit toggle ──────────────────────────────────────────────────────────────
function toggleAuditLog() {
  const expanded = els.auditToggle.getAttribute('aria-expanded') === 'true';
  const next     = !expanded;
  els.auditToggle.setAttribute('aria-expanded', String(next));
  els.auditLogWrap.hidden = !next;
}

// ── Last-refreshed timestamp ──────────────────────────────────────────────────
function updateLastRefreshed() {
  const now = new Date();
  els.lastRefreshed.textContent = `Refreshed ${now.toLocaleTimeString()}`;
  els.lastRefreshed.setAttribute('datetime', now.toISOString());
}

// ── Refresh indicator ─────────────────────────────────────────────────────────
function setRefreshing(active) {
  els.refreshIndicator.hidden = !active;
}

// ── Security helper: escape HTML ──────────────────────────────────────────────
function escHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

// ── Full data refresh ─────────────────────────────────────────────────────────
async function refreshApplications() {
  setRefreshing(true);
  try {
    const apps = await fetchApplications();
    renderApplications(apps);
    updateLastRefreshed();
  } finally {
    setRefreshing(false);
  }
}

// ── Discovery: sync and render jobs ──────────────────────────────────────────
async function refreshDiscoveredJobs() {
  els.discoveredJobRows.innerHTML = `<tr><td colspan="5" style="text-align:center;padding:20px;color:var(--text-muted)">Loading jobs...</td></tr>`;
  try {
    const jobs = await apiFetch('/api/jobs');
    if (!jobs || jobs.length === 0) {
      els.discoveredJobRows.innerHTML = `<tr><td colspan="5" style="text-align:center;padding:20px;color:var(--text-muted)">No discovered jobs in database. Click Sync Feed to retrieve.</td></tr>`;
      return;
    }
    els.discoveredJobRows.innerHTML = jobs.map(job => `
      <tr>
        <td class="td-company">${escHtml(job.company ?? '—')}</td>
        <td class="td-role">${escHtml(job.title ?? '—')}</td>
        <td><span style="font-size:11px;color:var(--text-secondary);">${escHtml(job.skillsRequired ? job.skillsRequired.join(', ') : '—')}</span></td>
        <td>${escHtml(job.seniority ?? '—')}</td>
        <td>${escHtml(job.locationType ?? '—')}</td>
      </tr>
    `).join('');
  } catch (err) {
    console.error('Failed to load discovered jobs:', err);
    els.discoveredJobRows.innerHTML = `<tr><td colspan="5" style="text-align:center;padding:20px;color:var(--accent-red)">Error loading jobs. Backend might be down.</td></tr>`;
  }
}

async function triggerDiscoverySync() {
  const board = els.syncBoardInput.value.trim() || 'google';
  els.triggerSyncBtn.disabled = true;
  els.triggerSyncBtn.textContent = 'Syncing...';
  try {
    await apiFetch(`/api/sources/greenhouse/sync?board=${encodeURIComponent(board)}`, { method: 'POST' });
    await refreshDiscoveredJobs();
  } catch (err) {
    console.error('Sync failed:', err);
    alert('Sync failed. Please check backend logs.');
  } finally {
    els.triggerSyncBtn.disabled = false;
    els.triggerSyncBtn.textContent = 'Sync Feed';
  }
}

// ── Settings: load and save profile/facts ──────────────────────────────────
async function loadProfileAndFacts() {
  try {
    const profile = await apiFetch('/api/profile');
    if (profile) {
      els.profileName.value = profile.name ?? '';
      els.profileEmail.value = profile.email ?? '';
      els.profilePhone.value = profile.phone ?? '';
      els.profileWorkAuth.value = profile.workAuthorization ?? 'OTHER';
      els.profileLocations.value = profile.locations ? Array.from(profile.locations).join(', ') : '';
    }
  } catch (err) {
    console.warn('Failed to load master profile:', err);
  }

  try {
    const facts = await apiFetch('/api/profile/facts');
    if (!facts || facts.length === 0) {
      els.settingsFactsList.innerHTML = `<p style="font-size:13px;color:var(--text-muted);">No facts uploaded yet.</p>`;
      return;
    }
    els.settingsFactsList.innerHTML = facts.map(fact => `
      <div class="card" style="padding:16px;background-color:var(--bg-hover);border:1px solid var(--border-color);display:flex;justify-content:space-between;align-items:flex-start;">
        <div style="width: 100%;">
          <span style="font-size:10px;font-weight:600;text-transform:uppercase;color:var(--accent-blue);letter-spacing:0.05em;">${escHtml(fact.type)}</span>
          <h4 style="font-size:14px;font-weight:600;margin:4px 0 2px;">${escHtml(fact.title)}</h4>
          <p style="font-size:12px;color:var(--text-muted);font-style:italic;">${escHtml(fact.employer)} (${escHtml(fact.startDate)} - ${escHtml(fact.endDate ?? 'Present')})</p>
          <p style="font-size:13px;color:var(--text-secondary);margin-top:8px;line-height:1.5;">${escHtml(fact.bulletText)}</p>
          <div style="display:flex;flex-wrap:wrap;gap:6px;margin-top:8px;">
            ${fact.skills ? fact.skills.map(s => `<span style="font-size:10px;background-color:var(--bg-panel);border:1px solid var(--border-color);border-radius:4px;padding:2px 6px;color:var(--text-muted);">${escHtml(s)}</span>`).join('') : ''}
          </div>
        </div>
      </div>
    `).join('');
  } catch (err) {
    console.warn('Failed to load profile facts:', err);
  }
}

async function saveProfile(e) {
  e.preventDefault();
  const locs = els.profileLocations.value.split(',').map(s => s.trim()).filter(s => s.length > 0);
  const body = {
    name: els.profileName.value,
    email: els.profileEmail.value,
    phone: els.profilePhone.value,
    workAuthorization: els.profileWorkAuth.value,
    locations: locs,
    visaSponsorshipNeeded: false,
    salaryFloor: 0,
    remotePreference: 'HYBRID',
    blocklistCompanies: ['BlockedCo'],
    dailyApplicationCap: 5,
    autonomyThreshold: appState.threshold
  };

  try {
    await apiFetch('/api/profile', {
      method: 'POST',
      body: JSON.stringify(body)
    });
    alert('Profile saved successfully!');
  } catch (err) {
    console.error('Failed to save profile:', err);
    alert('Failed to save profile.');
  }
}

// ── Navigation Router ─────────────────────────────────────────────────────────
function handleNavigation(viewId) {
  appState.currentView = viewId;

  // Update Breadcrumb and Page Header Titles
  const viewName = viewId.charAt(0).toUpperCase() + viewId.slice(1);
  els.breadcrumbCurrent.textContent = viewName === 'Audit' ? 'Audit Log' : viewName;
  els.pageTitle.textContent = viewName === 'Audit' ? 'Audit Log' : viewName;

  // Helper to hide all custom views
  const hideCustomViews = () => {
    els.discoverySection.style.display = 'none';
    els.matchingSection.style.display = 'none';
    els.generationSection.style.display = 'none';
    els.settingsSection.style.display = 'none';
  };

  // Hide/show elements based on active view
  if (viewId === 'dashboard') {
    els.pageSubtitle.textContent = 'Monitor your automated job search pipeline';
    hideCustomViews();
    document.querySelectorAll('.stats-row, .controls-panel, #applications-section, #audit-section').forEach(e => {
      e.style.display = '';
    });
  } else if (viewId === 'applications') {
    els.pageSubtitle.textContent = 'Manage and audit your active applications';
    hideCustomViews();
    document.querySelector('.stats-row').style.display = 'none';
    document.querySelector('.controls-panel').style.display = 'none';
    document.querySelector('#applications-section').style.display = '';
    document.querySelector('#audit-section').style.display = 'none';
  } else if (viewId === 'automation') {
    els.pageSubtitle.textContent = 'Configure automation threshold and state';
    hideCustomViews();
    document.querySelector('.stats-row').style.display = 'none';
    document.querySelector('.controls-panel').style.display = '';
    document.querySelector('#applications-section').style.display = 'none';
    document.querySelector('#audit-section').style.display = 'none';
  } else if (viewId === 'audit') {
    els.pageSubtitle.textContent = 'System events and execution logs';
    hideCustomViews();
    document.querySelector('.stats-row').style.display = 'none';
    document.querySelector('.controls-panel').style.display = 'none';
    document.querySelector('#applications-section').style.display = 'none';
    document.querySelector('#audit-section').style.display = '';
    // Expand audit log if switching to this view
    els.auditToggle.setAttribute('aria-expanded', 'true');
    els.auditLogWrap.hidden = false;
  } else if (viewId === 'discovery') {
    els.pageSubtitle.textContent = 'Ingest and monitor synced jobs';
    hideCustomViews();
    document.querySelectorAll('.stats-row, .controls-panel, #applications-section, #audit-section').forEach(e => {
      e.style.display = 'none';
    });
    els.discoverySection.style.display = '';
    refreshDiscoveredJobs();
  } else if (viewId === 'matching') {
    els.pageSubtitle.textContent = 'Configure matching rules and pre-filters';
    hideCustomViews();
    document.querySelectorAll('.stats-row, .controls-panel, #applications-section, #audit-section').forEach(e => {
      e.style.display = 'none';
    });
    els.matchingSection.style.display = '';
  } else if (viewId === 'generation') {
    els.pageSubtitle.textContent = 'View Tailoring and Generation rules';
    hideCustomViews();
    document.querySelectorAll('.stats-row, .controls-panel, #applications-section, #audit-section').forEach(e => {
      e.style.display = 'none';
    });
    els.generationSection.style.display = '';
  } else if (viewId === 'settings') {
    els.pageSubtitle.textContent = 'Edit candidate profile and verified facts';
    hideCustomViews();
    document.querySelectorAll('.stats-row, .controls-panel, #applications-section, #audit-section').forEach(e => {
      e.style.display = 'none';
    });
    els.settingsSection.style.display = '';
    loadProfileAndFacts();
  } else {
    // Settings or other stub views
    els.pageSubtitle.textContent = `Configure your system settings.`;
    hideCustomViews();
    document.querySelectorAll('.stats-row, .controls-panel, #applications-section, #audit-section').forEach(e => {
      e.style.display = 'none';
    });
  }

  // Update active sidebar nav items
  document.querySelectorAll('.nav-item').forEach((item) => {
    if (item.getAttribute('data-view') === viewId) {
      item.classList.add('active');
    } else {
      item.classList.remove('active');
    }
  });

  // Update active topbar tabs
  document.querySelectorAll('.topbar-tab').forEach((tab) => {
    if (tab.getAttribute('data-view') === viewId) {
      tab.classList.add('active');
    } else {
      tab.classList.remove('active');
    }
  });

  // Track Hash in URL
  if (window.location.hash !== `#${viewId}`) {
    window.location.hash = viewId;
  }
}
// ── Sidebar Collapse/Expand handlers ──────────────────────────────────────────
function collapseSidebar() {
  const shell = document.querySelector('.app-shell');
  shell.classList.add('sidebar-collapsed');
  els.sidebarExpandBtn.hidden = false;
}

function expandSidebar() {
  const shell = document.querySelector('.app-shell');
  if (window.innerWidth <= 768) {
    shell.classList.add('sidebar-open');
    const backdrop = document.getElementById('sidebarBackdrop');
    if (backdrop) backdrop.hidden = false;
  } else {
    shell.classList.remove('sidebar-collapsed');
    els.sidebarExpandBtn.hidden = true;
  }
}

function closeSidebarMobile() {
  const shell = document.querySelector('.app-shell');
  shell.classList.remove('sidebar-open');
  const backdrop = document.getElementById('sidebarBackdrop');
  if (backdrop) backdrop.hidden = true;
}
// ── Command Palette handlers (Raycast spotlight styling groups) ───────────────
function showCommandPalette() {
  els.commandPaletteOverlay.hidden = false;
  els.commandPaletteInput.focus();
  els.commandPaletteInput.value = '';
  renderCommandPaletteResults('');
}

function hideCommandPalette() {
  els.commandPaletteOverlay.hidden = true;
}

function renderCommandPaletteResults(query) {
  const resultsEl = els.commandPaletteResults;
  query = query.toLowerCase().trim();

  // Spotlight/Raycast grouped styling
  if (!query) {
    resultsEl.innerHTML = `
      <div class="command-palette-group-title">Autopilot System Actions</div>
      <div class="command-palette-item" data-action="toggle-kill">
        <i data-lucide="pause-circle" class="command-palette-item-icon icon-sm"></i>
        <div class="command-palette-item-text">Pause / Resume Autopilot Active Mode</div>
        <kbd class="command-palette-item-meta">Action</kbd>
      </div>
      <div class="command-palette-item" data-action="toggle-shadow">
        <i data-lucide="moon" class="command-palette-item-icon icon-sm"></i>
        <div class="command-palette-item-text">Toggle Autopilot Shadow Mode</div>
        <kbd class="command-palette-item-meta">Action</kbd>
      </div>

      <div class="command-palette-group-title">Navigation Links</div>
      <div class="command-palette-item" data-view="dashboard">
        <i data-lucide="layout-dashboard" class="command-palette-item-icon icon-sm"></i>
        <div class="command-palette-item-text">Navigate to Autopilot Dashboard</div>
        <kbd class="command-palette-item-meta">Navigation</kbd>
      </div>
      <div class="command-palette-item" data-view="applications">
        <i data-lucide="file-text" class="command-palette-item-icon icon-sm"></i>
        <div class="command-palette-item-text">Browse Active Applications View</div>
        <kbd class="command-palette-item-meta">Navigation</kbd>
      </div>
      <div class="command-palette-item" data-view="settings">
        <i data-lucide="settings" class="command-palette-item-icon icon-sm"></i>
        <div class="command-palette-item-text">Edit Autopilot Account Settings</div>
        <kbd class="command-palette-item-meta">Navigation</kbd>
      </div>
    `;
  } else {
    // Filter matching applications
    const filteredApps = appState.applications.filter(app => 
      app.company.toLowerCase().includes(query) || 
      (app.title ?? app.role ?? '').toLowerCase().includes(query)
    );

    if (filteredApps.length === 0) {
      resultsEl.innerHTML = `<div class="command-palette-empty">No results found for "${escHtml(query)}"</div>`;
    } else {
      resultsEl.innerHTML = `
        <div class="command-palette-group-title">Matching Application Audits</div>
        ` + filteredApps.map(app => `
          <div class="command-palette-item" data-app-id="${app.id}">
            <i data-lucide="file-text" class="command-palette-item-icon icon-sm"></i>
            <div class="command-palette-item-text">${escHtml(app.company)} — ${escHtml(app.title ?? app.role)}</div>
            <span class="command-palette-item-meta">Score: ${app.matchScore ?? app.score}</span>
          </div>
        `).join('');
    }
  }

  if (window.lucide) window.lucide.createIcons();
}

// ── Bootstrap ─────────────────────────────────────────────────────────────────
async function init() {
  // Wire up event listeners
  els.killSwitch.addEventListener('click', toggleKillSwitch);
  els.shadowMode.addEventListener('click', toggleShadowMode);
  els.threshold.addEventListener('input',  onThresholdInput);
  els.threshold.addEventListener('change', onThresholdChange);
  els.auditToggle.addEventListener('click', toggleAuditLog);

  // New views event listeners
  if (els.triggerSyncBtn) els.triggerSyncBtn.addEventListener('click', triggerDiscoverySync);
  if (els.profileForm) els.profileForm.addEventListener('submit', saveProfile);

  // Layout actions
  els.sidebarCollapseBtn.addEventListener('click', collapseSidebar);
  els.sidebarExpandBtn.addEventListener('click', expandSidebar);
  els.inspectorCloseBtn.addEventListener('click', () => {
    document.querySelector('.app-shell').classList.add('inspector-collapsed');
    clearInspector();
  });

  // Command Palette
  els.searchBtn.addEventListener('click', showCommandPalette);
  els.commandPaletteCloseBtn.addEventListener('click', hideCommandPalette);
  els.commandPaletteOverlay.addEventListener('click', (e) => {
    if (e.target === els.commandPaletteOverlay) hideCommandPalette();
  });



  document.addEventListener('keydown', (e) => {
    if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
      e.preventDefault();
      if (els.commandPaletteOverlay.hidden) {
        showCommandPalette();
      } else {
        hideCommandPalette();
      }
    }
  });

  els.commandPaletteInput.addEventListener('input', (e) => {
    renderCommandPaletteResults(e.target.value);
  });
  // Sidebar navigation routes click handlers
  document.querySelectorAll('.nav-item').forEach((item) => {
    item.addEventListener('click', (e) => {
      e.preventDefault();
      const viewId = item.getAttribute('data-view');
      handleNavigation(viewId);
      closeSidebarMobile();
    });
  });

  const mobileBackdrop = $('sidebarBackdrop');
  if (mobileBackdrop) {
    mobileBackdrop.addEventListener('click', closeSidebarMobile);
  }
  // Topbar navigation tabs click handlers
  document.querySelectorAll('.topbar-tab').forEach((tab) => {
    tab.addEventListener('click', (e) => {
      e.preventDefault();
      const viewId = tab.getAttribute('data-view');
      handleNavigation(viewId);
    });
  });

  // Command palette results click handler
  els.commandPaletteResults.addEventListener('click', (e) => {
    const item = e.target.closest('.command-palette-item');
    if (!item) return;

    const action = item.getAttribute('data-action');
    const view = item.getAttribute('data-view');
    const appId = item.getAttribute('data-app-id');

    if (action === 'toggle-kill') {
      toggleKillSwitch();
    } else if (action === 'toggle-shadow') {
      toggleShadowMode();
    } else if (view) {
      handleNavigation(view);
    } else if (appId) {
      selectApplication(appId);
    }
    hideCommandPalette();
  });

  // Initial data load — fire all requests in parallel
  const [stats, apps, status, events] = await Promise.all([
    fetchStats(),
    fetchApplications(),
    fetchAutomationStatus(),
    fetchAuditEvents()
  ]);

  // Render everything
  renderStats(stats);
  renderApplications(apps);
  renderAuditLog(events);
  setAutomationState(status.halted ?? false);
  updateLastRefreshed();
  renderReasoningTimeline();

  // Apply persisted threshold if returned by status
  if (typeof status.threshold === 'number') {
    appState.threshold       = status.threshold;
    els.threshold.value      = status.threshold;
    els.thresholdValue.value = status.threshold;
  }

  // Apply shadow mode state from server
  if (typeof status.shadowMode === 'boolean') {
    appState.shadowMode = status.shadowMode;
    els.shadowMode.setAttribute('aria-pressed', String(status.shadowMode));
    if (status.shadowMode) {
      els.shadowMode.innerHTML = `<i data-lucide="moon" class="icon-sm"></i> Shadow Mode`;
    } else {
      els.shadowMode.innerHTML = `<i data-lucide="sun" class="icon-sm"></i> Active Autopilot`;
    }
  }


  // Handle mobile startup state
  if (window.innerWidth <= 768) {
    document.querySelector('.app-shell').classList.add('inspector-collapsed');
  }

  // Handle Initial Route from Location Hash
  const initialHash = window.location.hash.substring(1);
  if (initialHash && ['dashboard', 'applications', 'discovery', 'matching', 'generation', 'automation', 'audit', 'settings'].includes(initialHash)) {
    handleNavigation(initialHash);
  } else {
    handleNavigation('dashboard');
  }

  // Initialize Lucide Icons
  if (window.lucide) {
    window.lucide.createIcons();
  }

  // Start polling loop
  appState.pollTimer = setInterval(refreshApplications, POLL_MS);
  console.log(`[CareerCopilot] Dashboard ready — polling every ${POLL_MS / 1000}s`);
}

document.addEventListener('DOMContentLoaded', init);
