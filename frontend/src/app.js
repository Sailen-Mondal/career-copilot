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
  inspectorAuditTrail:    $('inspectorAuditTrail'),
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
  profileSection:         $('profile-section'),
  settingsSection:        $('settings-section'),
  automationCanvasSection: $('automation-canvas-section'),
  discoveredJobRows:      $('discoveredJobRows'),
  syncBoardInput:         $('syncBoardInput'),
  triggerSyncBtn:         $('triggerSyncBtn'),
  triggerCrawlBtn:        $('triggerCrawlBtn'),
  profileForm:            $('profileForm'),
  profileName:            $('profileName'),
  profileEmail:           $('profileEmail'),
  profilePhone:           $('profilePhone'),
  profileWorkAuth:        $('profileWorkAuth'),
  profileLocations:       $('profileLocations'),
  settingsFactsList:      $('settingsFactsList'),
  settingsForm:           $('settingsForm'),
  settingsThreshold:      $('settingsThreshold'),
  settingsCap:            $('settingsCap'),
  settingsBlocklist:      $('settingsBlocklist'),
  settingsBreakersList:   $('settingsBreakersList'),
  resetBreakersBtn:       $('resetBreakersBtn'),
  navProfile:             $('nav-profile'),
  factModalOverlay:       $('factModalOverlay'),
  factModalTitle:         $('factModalTitle'),
  factForm:               $('factForm'),
  factId:                 $('factId'),
  factType:               $('factType'),
  factTitle:              $('factTitle'),
  factEmployer:           $('factEmployer'),
  factStartDate:          $('factStartDate'),
  factEndDate:            $('factEndDate'),
  factBulletText:         $('factBulletText'),
  factSkills:             $('factSkills'),
  factCancelBtn:          $('factCancelBtn'),
  addFactBtn:             $('addFactBtn'),
  docModalOverlay:        $('docModalOverlay'),
  docModalTitle:          $('docModalTitle'),
  docModalContent:        $('docModalContent'),
  docModalCloseBtn:       $('docModalCloseBtn')
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

  // Update live workflow canvas with real database records
  if (typeof window.updateWorkflowFromRealData === 'function') {
    window.updateWorkflowFromRealData(apps);
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
    const hasResume = !!app.resumeVersionId;
    const hasCoverLetter = !!app.coverLetterVersionId;

    if (hasResume) {
      resumeBadge = `<span class="inspector-doc-status" style="font-size: 11px; padding: 2.5px 8px; border-radius: 4px; background: rgba(59,130,246,0.15); color: var(--accent-blue); display: inline-flex; align-items: center; gap: 4px; border: 1px solid rgba(59,130,246,0.3); font-weight: 500;"><i data-lucide="eye" style="width: 12px; height: 12px;"></i> View</span>`;
    } else if (app.status === 'generating') {
      resumeBadge = `<span class="inspector-doc-status" style="font-size: 11px; padding: 2.5px 8px; border-radius: 4px; background: rgba(245,158,11,0.15); color: var(--accent-orange); display: inline-flex; align-items: center; gap: 4px; border: 1px solid rgba(245,158,11,0.3); font-weight: 500;"><i data-lucide="loader-2" class="animate-spin" style="width: 12px; height: 12px;"></i> Generating</span>`;
    } else {
      resumeBadge = `<span class="inspector-doc-status" style="font-size: 11px; padding: 2.5px 8px; border-radius: 4px; background: rgba(239,68,68,0.1); color: var(--accent-red); font-weight: 500;">Not created</span>`;
    }

    if (hasCoverLetter) {
      coverLetterBadge = `<span class="inspector-doc-status" style="font-size: 11px; padding: 2.5px 8px; border-radius: 4px; background: rgba(59,130,246,0.15); color: var(--accent-blue); display: inline-flex; align-items: center; gap: 4px; border: 1px solid rgba(59,130,246,0.3); font-weight: 500;"><i data-lucide="eye" style="width: 12px; height: 12px;"></i> View</span>`;
    } else if (app.status === 'generating') {
      coverLetterBadge = `<span class="inspector-doc-status" style="font-size: 11px; padding: 2.5px 8px; border-radius: 4px; background: rgba(245,158,11,0.15); color: var(--accent-orange); display: inline-flex; align-items: center; gap: 4px; border: 1px solid rgba(245,158,11,0.3); font-weight: 500;"><i data-lucide="loader-2" class="animate-spin" style="width: 12px; height: 12px;"></i> Generating</span>`;
    } else {
      coverLetterBadge = `<span class="inspector-doc-status" style="font-size: 11px; padding: 2.5px 8px; border-radius: 4px; background: rgba(239,68,68,0.1); color: var(--accent-red); font-weight: 500;">Not created</span>`;
    }

    docsEl.innerHTML = `
      <div class="inspector-doc-item clickable-doc" data-type="resume" style="cursor: pointer; transition: background 0.2s; border-radius: 6px; padding: 6px 8px; display: flex; align-items: center; justify-content: space-between; border: 1px dashed var(--border-color);" onmouseover="this.style.background='rgba(255,255,255,0.03)'" onmouseout="this.style.background='transparent'">
        <div style="display: flex; align-items: center; gap: 8px;">
          <i data-lucide="file-text" class="icon-sm"></i>
          <span>Tailored Resume</span>
        </div>
        ${resumeBadge}
      </div>
      <div class="inspector-doc-item clickable-doc" data-type="cover_letter" style="margin-top: 8px; cursor: pointer; transition: background 0.2s; border-radius: 6px; padding: 6px 8px; display: flex; align-items: center; justify-content: space-between; border: 1px dashed var(--border-color);" onmouseover="this.style.background='rgba(255,255,255,0.03)'" onmouseout="this.style.background='transparent'">
        <div style="display: flex; align-items: center; gap: 8px;">
          <i data-lucide="mail" class="icon-sm"></i>
          <span>Cover Letter</span>
        </div>
        ${coverLetterBadge}
      </div>
    `;

    docsEl.querySelectorAll('.clickable-doc').forEach(item => {
      item.addEventListener('click', async () => {
        const type = item.getAttribute('data-type');
        const versionId = type === 'resume' ? app.resumeVersionId : app.coverLetterVersionId;
        if (!versionId) {
          alert('This document was not generated.');
          return;
        }
        try {
          const detail = await apiFetch(`/api/applications/${app.id}`);
          const content = type === 'resume' ? detail.resumeContent : detail.coverLetterContent;
          const title = type === 'resume' ? 'Tailored Resume' : 'Tailored Cover Letter';
          openDocumentViewer(title, content);
        } catch (err) {
          console.error('Failed to fetch document:', err);
          alert('Could not retrieve document content. Make sure generation succeeded.');
        }
      });
    });
  }

  // Render application audit trail
  if (els.inspectorAuditTrail) {
    const trail = app.auditTrail ?? [];
    if (trail.length === 0) {
      els.inspectorAuditTrail.innerHTML = `<li style="padding: 4px 0; border-bottom: 1px dashed rgba(255,255,255,0.05); color: var(--text-muted);">No audit trail recorded.</li>`;
    } else {
      els.inspectorAuditTrail.innerHTML = trail.map(entry => {
        return `<li style="padding: 4px 0; border-bottom: 1px dashed rgba(255,255,255,0.05);">${escHtml(entry)}</li>`;
      }).join('');
    }
  }

  if (window.lucide) window.lucide.createIcons();
}

function openDocumentViewer(title, content) {
  els.docModalTitle.textContent = title;
  els.docModalContent.textContent = content || 'No content generated yet.';
  els.docModalOverlay.hidden = false;
  if (window.lucide) window.lucide.createIcons();
}

function closeDocumentViewer() {
  els.docModalOverlay.hidden = true;
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
  if (typeof updateSettingsStatusSidebar === 'function') {
    updateSettingsStatusSidebar();
  }
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
  els.discoveredJobRows.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:20px;color:var(--text-muted)">Loading jobs...</td></tr>`;
  try {
    const jobs = await apiFetch('/api/jobs');
    // Update stat card
    const totalEl = document.getElementById('discTotalJobs');
    if (totalEl) totalEl.textContent = jobs ? jobs.length : '0';
    if (!jobs || jobs.length === 0) {
      els.discoveredJobRows.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:40px;color:var(--text-muted)">No jobs yet — crawler will populate this automatically.</td></tr>`;
      return;
    }
    const sourceColors = { greenhouse: '#10b981', lever: '#6366f1', remotive: '#f59e0b', himalayas: '#3b82f6', arbeitnow: '#ec4899' };
    els.discoveredJobRows.innerHTML = jobs.map(job => {
      const src = (job.source || 'greenhouse').toLowerCase();
      const color = sourceColors[src] || '#94a3b8';
      return `
      <tr>
        <td><span style="font-size:11px;font-weight:600;padding:2px 8px;border-radius:10px;background:${color}22;color:${color};">${escHtml(src)}</span></td>
        <td class="td-company">${escHtml(job.company ?? '—')}</td>
        <td class="td-role">${escHtml(job.title ?? '—')}</td>
        <td><span style="font-size:11px;color:var(--text-secondary);">${escHtml(job.requiredSkills ? job.requiredSkills.join(', ') : '—')}</span></td>
        <td>${escHtml(job.seniority ?? '—')}</td>
        <td>${escHtml(job.locationType ?? '—')}</td>
      </tr>`;
    }).join('');
  } catch (err) {
    console.error('Failed to load discovered jobs:', err);
    els.discoveredJobRows.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:20px;color:var(--accent-red)">Error loading jobs. Backend might be down.</td></tr>`;
  }
}

async function triggerDiscoverySync() {
  const board = els.syncBoardInput ? (els.syncBoardInput.value.trim() || 'google') : 'google';
  if (els.triggerSyncBtn) { els.triggerSyncBtn.disabled = true; els.triggerSyncBtn.textContent = 'Syncing...'; }
  try {
    await apiFetch(`/api/sources/greenhouse/sync?board=${encodeURIComponent(board)}`, { method: 'POST' });
    await refreshDiscoveredJobs();
  } catch (err) {
    console.error('Sync failed:', err);
    alert('Sync failed. Please check backend logs.');
  } finally {
    if (els.triggerSyncBtn) { els.triggerSyncBtn.disabled = false; els.triggerSyncBtn.textContent = 'Sync Feed'; }
  }
}

async function triggerAutonomousCrawl() {
  const btn = els.triggerCrawlBtn;
  if (btn) { btn.disabled = true; btn.textContent = '⏳ Crawling…'; }
  try {
    await apiFetch('/api/discovery/trigger', { method: 'POST' });
    // Give the backend 5s head start then reload jobs
    setTimeout(async () => {
      await refreshDiscoveredJobs();
      if (btn) { btn.disabled = false; btn.textContent = '⚡ Trigger Crawl Now'; }
    }, 5000);
  } catch (err) {
    console.error('Crawl trigger failed:', err);
    alert('Could not trigger crawl. Is the backend running?');
    if (btn) { btn.disabled = false; btn.textContent = '⚡ Trigger Crawl Now'; }
  }
}

// Fact State
let currentFacts = [];

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
    currentFacts = facts || [];
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
        <div style="display:flex; gap:8px; margin-left: 12px; align-self: flex-start;">
          <button class="edit-fact-btn btn" data-id="${fact.id}" style="padding:4px;" title="Edit Fact"><i data-lucide="pencil" class="icon-sm" style="color:var(--text-secondary);"></i></button>
          <button class="delete-fact-btn btn" data-id="${fact.id}" style="padding:4px;" title="Delete Fact"><i data-lucide="trash-2" class="icon-sm" style="color:var(--accent-red);"></i></button>
        </div>
      </div>
    `).join('');

    // Wire up fact card actions
    els.settingsFactsList.querySelectorAll('.edit-fact-btn').forEach(btn => {
      btn.addEventListener('click', () => openFactEditor(btn.getAttribute('data-id')));
    });
    els.settingsFactsList.querySelectorAll('.delete-fact-btn').forEach(btn => {
      btn.addEventListener('click', () => deleteFact(btn.getAttribute('data-id')));
    });

  } catch (err) {
    console.warn('Failed to load profile facts:', err);
  }
  if (window.lucide) window.lucide.createIcons();
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

// ── Facts Editor Overlay logic ────────────────────────────────────────────────
async function openFactEditor(factId = null) {
  els.factModalOverlay.hidden = false;
  
  if (factId) {
    els.factModalTitle.textContent = 'Edit Profile Fact';
    const fact = currentFacts.find(f => f.id === factId);
    if (fact) {
      els.factId.value = fact.id;
      els.factType.value = fact.type;
      els.factTitle.value = fact.title ?? '';
      els.factEmployer.value = fact.employer ?? '';
      els.factStartDate.value = fact.startDate ?? '';
      els.factEndDate.value = fact.endDate ?? '';
      els.factBulletText.value = fact.bulletText ?? '';
      els.factSkills.value = fact.skills ? fact.skills.join(', ') : '';
    }
  } else {
    els.factModalTitle.textContent = 'Add Profile Fact';
    els.factId.value = '';
    els.factType.value = 'EXPERIENCE';
    els.factTitle.value = '';
    els.factEmployer.value = '';
    els.factStartDate.value = '';
    els.factEndDate.value = '';
    els.factBulletText.value = '';
    els.factSkills.value = '';
  }
  if (window.lucide) window.lucide.createIcons();
}

function closeFactEditor() {
  els.factModalOverlay.hidden = true;
}

async function saveFact(e) {
  e.preventDefault();
  const id = els.factId.value;
  const skills = els.factSkills.value.split(',').map(s => s.trim()).filter(s => s.length > 0);
  const body = {
    type: els.factType.value,
    employer: els.factEmployer.value,
    title: els.factTitle.value,
    startDate: els.factStartDate.value,
    endDate: els.factEndDate.value || null,
    bulletText: els.factBulletText.value,
    skills: skills
  };

  try {
    if (id) {
      // Update
      await apiFetch(`/api/profile/facts/${id}`, {
        method: 'PUT',
        body: JSON.stringify(body)
      });
      alert('Fact updated successfully!');
    } else {
      // Create
      await apiFetch('/api/profile/facts', {
        method: 'POST',
        body: JSON.stringify(body)
      });
      alert('Fact added successfully!');
    }
    closeFactEditor();
    await loadProfileAndFacts();
  } catch (err) {
    console.error('Failed to save fact:', err);
    alert('Failed to save fact.');
  }
}

async function deleteFact(factId) {
  if (!confirm('Are you sure you want to delete this profile fact?')) return;
  try {
    await apiFetch(`/api/profile/facts/${factId}`, { method: 'DELETE' });
    alert('Fact deleted successfully!');
    await loadProfileAndFacts();
  } catch (err) {
    console.error('Failed to delete fact:', err);
    alert('Failed to delete fact.');
  }
}

// ── System Settings: load and save policy ────────────────────────────────────
async function loadSystemSettings() {
  try {
    const policy = await apiFetch('/api/policy');
    if (policy) {
      if (els.settingsThreshold) els.settingsThreshold.value = policy.autonomyThreshold ?? 85;
      const wtThreshold = document.getElementById('wt-threshold');
      if (wtThreshold) wtThreshold.value = policy.autonomyThreshold ?? 85;

      if (els.settingsCap) els.settingsCap.value = policy.dailyApplicationCap ?? 3;
      const appLimitDaily = document.getElementById('app-limit-daily');
      if (appLimitDaily) appLimitDaily.value = policy.dailyApplicationCap ?? 3;

      if (els.settingsBlocklist) els.settingsBlocklist.value = policy.blocklistCompanies ? Array.from(policy.blocklistCompanies).join(', ') : '';

      // Render circuit breakers list
      if (els.settingsBreakersList && policy.platformBreakerStates) {
        els.settingsBreakersList.innerHTML = Object.entries(policy.platformBreakerStates).map(([scope, status]) => {
          const isClosed = status === 'CLOSED';
          const statusColor = isClosed ? 'var(--accent-green)' : 'var(--accent-red)';
          return `
            <div style="display:flex;justify-content:space-between;align-items:center;padding:10px 16px;background-color:var(--bg-hover);border:1px solid var(--border-color);border-radius:var(--radius-btn);">
              <span style="font-weight:600;font-size:13px;text-transform:capitalize;">${escHtml(scope)} crawler stream</span>
              <span class="status" style="font-size:11px;font-weight:600;padding:2px 8px;border-radius:4px;background-color:var(--bg-panel);color:${statusColor};border:1px solid ${statusColor};">
                ${escHtml(status)}
              </span>
            </div>
          `;
        }).join('');
      }
    }
  } catch (err) {
    console.error('Failed to load system settings:', err);
  }
}

async function saveSystemSettings(e) {
  e.preventDefault();
  let blocklist = [];
  if (els.settingsBlocklist) {
    blocklist = els.settingsBlocklist.value.split(',').map(s => s.trim()).filter(s => s.length > 0);
  } else {
    const blacklistContainer = document.getElementById('disc-tags-companies-blacklist');
    if (blacklistContainer) {
      const tags = blacklistContainer.querySelectorAll('.tag-badge span');
      tags.forEach(span => blocklist.push(span.textContent));
    }
  }

  let threshold = 85;
  if (els.settingsThreshold) {
    threshold = Number(els.settingsThreshold.value);
  } else {
    const wtThreshold = document.getElementById('wt-threshold');
    if (wtThreshold) threshold = Number(wtThreshold.value);
  }

  let cap = 3;
  if (els.settingsCap) {
    cap = Number(els.settingsCap.value);
  } else {
    const appLimitDaily = document.getElementById('app-limit-daily');
    if (appLimitDaily) cap = Number(appLimitDaily.value);
  }

  const body = {
    autonomyThreshold: threshold,
    dailyApplicationCap: cap,
    blocklistCompanies: blocklist
  };

  try {
    const updated = await apiFetch('/api/policy', {
      method: 'PUT',
      body: JSON.stringify(body)
    });
    // Sync slider value as well
    if (updated && typeof updated.autonomyThreshold === 'number') {
      appState.threshold = updated.autonomyThreshold;
      if (els.threshold) els.threshold.value = updated.autonomyThreshold;
      if (els.thresholdValue) els.thresholdValue.value = updated.autonomyThreshold;
      const wtThreshold = document.getElementById('wt-threshold');
      if (wtThreshold) wtThreshold.value = updated.autonomyThreshold;
    }
    alert('System settings saved successfully!');
    loadSystemSettings();
  } catch (err) {
    console.error('Failed to save system settings:', err);
    alert('Failed to save system settings.');
  }
}

async function resetCircuitBreakers() {
  els.resetBreakersBtn.disabled = true;
  els.resetBreakersBtn.textContent = 'Resetting...';
  try {
    await apiFetch('/api/policy/breakers/reset', { method: 'POST' });
    alert('All circuit breakers reset successfully!');
    await loadSystemSettings();
  } catch (err) {
    console.error('Failed to reset breakers:', err);
    alert('Failed to reset circuit breakers.');
  } finally {
    els.resetBreakersBtn.disabled = false;
    els.resetBreakersBtn.innerHTML = `<i data-lucide="refresh-cw" class="icon-sm"></i> Reset All Circuit Breakers`;
    if (window.lucide) window.lucide.createIcons();
  }
}

// ── Navigation Router ─────────────────────────────────────────────────────────
function handleNavigation(viewId, settingsTabId = null) {
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
    els.profileSection.style.display = 'none';
    els.settingsSection.style.display = 'none';
    if (els.automationCanvasSection) els.automationCanvasSection.style.display = 'none';
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
    if (els.automationCanvasSection) els.automationCanvasSection.style.display = '';
    if (typeof window.updateWorkflowConnections === 'function') {
      setTimeout(window.updateWorkflowConnections, 50);
    }
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
  } else if (viewId === 'profile') {
    els.pageSubtitle.textContent = 'Edit candidate profile and verified facts';
    hideCustomViews();
    document.querySelectorAll('.stats-row, .controls-panel, #applications-section, #audit-section').forEach(e => {
      e.style.display = 'none';
    });
    els.profileSection.style.display = '';
    loadProfileAndFacts();
  } else if (viewId === 'settings') {
    els.pageSubtitle.textContent = 'Configure autopilot system settings and limits';
    hideCustomViews();
    document.querySelectorAll('.stats-row, .controls-panel, #applications-section, #audit-section').forEach(e => {
      e.style.display = 'none';
    });
    els.settingsSection.style.display = '';
    loadSystemSettings();

    // Toggle active settings tab pane based on parameter
    const activeTabId = settingsTabId || 'general';
    const settingsTabContents = document.querySelectorAll('.settings-tab-content');
    settingsTabContents.forEach(c => c.classList.remove('active'));
    const targetPane = document.getElementById(`settings-tab-${activeTabId}`);
    if (targetPane) targetPane.classList.add('active');
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
    const itemView = item.getAttribute('data-view');
    const itemTab = item.getAttribute('data-settings-tab');
    
    if (itemView === viewId) {
      if (viewId === 'settings') {
        const activeTabId = settingsTabId || 'general';
        if (itemTab === activeTabId) {
          item.classList.add('active');
        } else {
          item.classList.remove('active');
        }
      } else {
        item.classList.add('active');
      }
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
  const hashVal = viewId === 'settings' ? `settings-${settingsTabId || 'general'}` : viewId;
  if (window.location.hash !== `#${hashVal}`) {
    window.location.hash = hashVal;
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
  if (els.triggerCrawlBtn) els.triggerCrawlBtn.addEventListener('click', triggerAutonomousCrawl);
  if (els.profileForm) els.profileForm.addEventListener('submit', saveProfile);
  if (els.settingsForm) els.settingsForm.addEventListener('submit', saveSystemSettings);
  if (els.resetBreakersBtn) els.resetBreakersBtn.addEventListener('click', resetCircuitBreakers);
  if (els.addFactBtn) els.addFactBtn.addEventListener('click', () => openFactEditor());
  if (els.factCancelBtn) els.factCancelBtn.addEventListener('click', closeFactEditor);
  if (els.factForm) els.factForm.addEventListener('submit', saveFact);
  if (els.docModalCloseBtn) els.docModalCloseBtn.addEventListener('click', closeDocumentViewer);
  if (els.docModalOverlay) els.docModalOverlay.addEventListener('click', (e) => {
    if (e.target === els.docModalOverlay) closeDocumentViewer();
  });

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
      const tabId = item.getAttribute('data-settings-tab');
      
      if (viewId === 'settings' && tabId) {
        handleNavigation('settings', tabId);
      } else {
        handleNavigation(viewId);
      }
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
  if (initialHash) {
    if (initialHash.startsWith('settings-')) {
      const tabId = initialHash.replace('settings-', '');
      handleNavigation('settings', tabId);
    } else if (['dashboard', 'applications', 'discovery', 'matching', 'generation', 'automation', 'audit', 'settings', 'profile'].includes(initialHash)) {
      handleNavigation(initialHash);
    } else {
      handleNavigation('dashboard');
    }
  } else {
    handleNavigation('dashboard');
  }

  // Initialize Settings Dashboard
  initSettingsDashboard();

  // Initialize Lucide Icons
  if (window.lucide) {
    window.lucide.createIcons();
  }

  // Start polling loop
  appState.pollTimer = setInterval(refreshApplications, POLL_MS);
  console.log(`[CareerCopilot] Dashboard ready — polling every ${POLL_MS / 1000}s`);
}

// ── Settings Dashboard Custom Logic ───────────────────────────────────────────
function initSettingsDashboard() {

  // Segmented Controls Handlers
  setupSegmentedControl('gen-theme-control', (val) => {
    console.log('Theme changed to:', val);
    document.body.className = `theme-${val}`;
  });

  setupSegmentedControl('ai-reasoning-control', (val) => {
    console.log('Reasoning effort changed to:', val);
  });

  setupSegmentedControl('app-mode-control', (val) => {
    const descEl = document.getElementById('app-mode-desc');
    if (!descEl) return;
    
    if (val === 'manual') {
      descEl.innerHTML = `⚠️ <strong>Manual Review:</strong> The agent compiles listings and displays them. No documents will be generated, and no applications will be submitted without your manual interaction on each item.`;
    } else if (val === 'review') {
      descEl.innerHTML = `📝 <strong>Review Before Apply:</strong> The agent automatically tailors resumes and drafts cover letters. Applications are enqueued for your approval in the queue before final submission.`;
    } else if (val === 'semi') {
      descEl.innerHTML = `⚡ <strong>Semi-Autonomous:</strong> The agent automatically tailors documents and submits applications for roles scoring above 90. Roles between 80-90 will wait for your manual review.`;
    } else if (val === 'autonomous') {
      descEl.innerHTML = `🔥 <strong>Fully Autonomous:</strong> The agent automatically tailors resumes, writes cover letters, answers screening questions, and submits applications for any job matching above the autonomy threshold without notifying you first.`;
    }
  });

  // Slider Live Updates
  setupSliderOutput('ai-temp', 'ai-temp-val');
  setupSliderOutput('wt-skill', 'wt-skill-val', updateMatchingCalculator);
  setupSliderOutput('wt-salary', 'wt-salary-val', updateMatchingCalculator);
  setupSliderOutput('wt-location', 'wt-location-val', updateMatchingCalculator);
  setupSliderOutput('wt-reputation', 'wt-reputation-val', updateMatchingCalculator);
  setupSliderOutput('wt-tech', 'wt-tech-val', updateMatchingCalculator);
  setupSliderOutput('wt-growth', 'wt-growth-val', updateMatchingCalculator);
  setupSliderOutput('wt-age', 'wt-age-val', updateMatchingCalculator);
  setupSliderOutput('wt-benefits', 'wt-benefits-val', updateMatchingCalculator);
  setupSliderOutput('wt-visa', 'wt-visa-val', updateMatchingCalculator);

  // Setup Sample Job selector for calculator
  const jobSelector = document.getElementById('calc-sample-job');
  if (jobSelector) {
    jobSelector.addEventListener('change', updateMatchingCalculator);
  }
  updateMatchingCalculator();

  // Setup Interactive Tag Inputs
  setupTagInput('disc-tags-title-keywords', ['Backend', 'Platform', 'Systems Engineer']);
  setupTagInput('disc-tags-title-excluded', ['Manager', 'Frontend', 'Designer']);
  setupTagInput('disc-tags-skills-required', ['Java', 'Spring Boot', 'SQL']);
  setupTagInput('disc-tags-skills-excluded', ['PHP', 'COBOL']);
  setupTagInput('disc-tags-companies-whitelist', ['Google', 'Stripe', 'Vercel']);
  setupTagInput('disc-tags-companies-blacklist', ['BlockedCo', 'SpamCorp']);

  // Load and setup Observability logs
  renderSystemLogs();
  const logSrc = document.getElementById('log-filter-source');
  const logLvl = document.getElementById('log-filter-level');
  const logSearch = document.getElementById('log-search-input');
  const logExport = document.getElementById('log-btn-export');

  if (logSrc) logSrc.addEventListener('change', renderSystemLogs);
  if (logLvl) logLvl.addEventListener('change', renderSystemLogs);
  if (logSearch) logSearch.addEventListener('input', renderSystemLogs);
  if (logExport) logExport.addEventListener('click', exportSystemLogs);

  // Load automation components
  renderSettingsCircuitBreakers();
  const resetBreakersBtn = document.getElementById('auto-btn-reset-breakers');
  if (resetBreakersBtn) {
    resetBreakersBtn.addEventListener('click', resetSettingsCircuitBreakers);
  }

  // Load application queue lists
  setupQueueVisualizer();

  // Emergency Stop button handler
  const emergencyBtn = document.getElementById('saf-btn-emergency');
  if (emergencyBtn) {
    emergencyBtn.addEventListener('click', () => {
      setAutomationState(true);
      updateSettingsStatusSidebar();
      alert('🚨 EMERGENCY STOP ACTIVATED. Autopilot paused immediately, crawler threads terminated.');
    });
  }

  // Sync settings button click handlers
  const exportBtn = document.getElementById('gen-btn-export');
  if (exportBtn) {
    exportBtn.addEventListener('click', exportAgentSettings);
  }

  const importTrigger = document.getElementById('gen-btn-import-trigger');
  const importInput = document.getElementById('gen-btn-import');
  if (importTrigger && importInput) {
    importTrigger.addEventListener('click', () => importInput.click());
    importInput.addEventListener('change', importAgentSettings);
  }

  const resetBtn = document.getElementById('gen-btn-reset');
  if (resetBtn) {
    resetBtn.addEventListener('click', () => {
      if (confirm('Are you sure you want to reset all general settings to default?')) {
        alert('General settings reset completed.');
      }
    });
  }

  // Setup sidebar live updates on provider/model change
  document.getElementById('ai-provider')?.addEventListener('change', updateSettingsStatusSidebar);
  document.getElementById('ai-model')?.addEventListener('change', updateSettingsStatusSidebar);

  // Sync initial status sidebar values
  updateSettingsStatusSidebar();
  
  // Connect/Reconnect button handlers inside integrations
  document.querySelectorAll('.integration-card .int-action').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const card = btn.closest('.integration-card');
      const brand = card.querySelector('.int-name').textContent;
      const keyInput = card.querySelector('.int-key-input');

      btn.disabled = true;
      btn.textContent = 'Syncing...';
      
      setTimeout(() => {
        btn.disabled = false;
        if (card.classList.contains('disconnected')) {
          card.classList.remove('disconnected');
          card.classList.add('connected');
          const badge = card.querySelector('.int-badge');
          badge.className = 'int-badge connected';
          badge.textContent = 'Connected';
          btn.className = 'btn btn-secondary int-action';
          btn.textContent = 'Reconnect';
          if (keyInput) keyInput.disabled = true;
          alert(`Successfully authenticated and connected to ${brand}.`);
        } else {
          btn.textContent = 'Reconnect';
          alert(`Synced integration parameters for ${brand}.`);
        }
      }, 1200);
    });
  });
}

function setupSegmentedControl(elementId, callback) {
  const container = document.getElementById(elementId);
  if (!container) return;

  const buttons = container.querySelectorAll('.segmented-btn');
  buttons.forEach(btn => {
    btn.addEventListener('click', () => {
      buttons.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      const val = btn.getAttribute('data-val');
      if (callback) callback(val);
    });
  });
}

function setupSliderOutput(sliderId, outputId, callback) {
  const slider = document.getElementById(sliderId);
  const output = document.getElementById(outputId);
  if (!slider || !output) return;

  slider.addEventListener('input', () => {
    output.textContent = slider.value;
    if (callback) callback();
  });
}

// ── Interactive Matching Calculator ──────────────────────────────────────────
function updateMatchingCalculator() {
  const jobVal = document.getElementById('calc-sample-job')?.value || 'backend';
  const skillWt = Number(document.getElementById('wt-skill')?.value || 9);
  const salaryWt = Number(document.getElementById('wt-salary')?.value || 8);
  const locationWt = Number(document.getElementById('wt-location')?.value || 7);
  const reputationWt = Number(document.getElementById('wt-reputation')?.value || 5);
  const techWt = Number(document.getElementById('wt-tech')?.value || 9);
  const growthWt = Number(document.getElementById('wt-growth')?.value || 6);
  const ageWt = Number(document.getElementById('wt-age')?.value || 3);
  const benefitsWt = Number(document.getElementById('wt-benefits')?.value || 4);
  const visaWt = Number(document.getElementById('wt-visa')?.value || 8);

  let scores = {};
  let explanation = '';

  if (jobVal === 'backend') {
    scores = { skill: 9.5, salary: 9.0, location: 8.5, reputation: 6.0, tech: 10.0, growth: 7.0, age: 4.0, benefits: 5.0, visa: 10.0 };
    explanation = 'Excellent skills and tech stack overlap. Standard corporate reputation and average benefits, but salary matches expectations and candidate work auth checks out.';
  } else if (jobVal === 'ml') {
    scores = { skill: 8.0, salary: 9.5, location: 8.0, reputation: 8.5, tech: 8.0, growth: 9.0, age: 7.0, benefits: 7.5, visa: 9.0 };
    explanation = 'Strong career growth and high starting salary. Solid reputation with modern stack, though minor mismatch in candidate preferred frameworks.';
  } else {
    // junior
    scores = { skill: 2.0, salary: 4.0, location: 3.0, reputation: 3.0, tech: 1.0, growth: 2.0, age: 9.0, benefits: 2.0, visa: 5.0 };
    explanation = 'Poor skills overlap. Required design credentials are missing from John Doe facts profile. Technology requirements (Figma/Tailwind) are not satisfied.';
  }

  // Calculate weighted score
  const totalWeight = skillWt + salaryWt + locationWt + reputationWt + techWt + growthWt + ageWt + benefitsWt + visaWt;
  const weightedSum = (scores.skill * skillWt) + 
                      (scores.salary * salaryWt) + 
                      (scores.location * locationWt) + 
                      (scores.reputation * reputationWt) + 
                      (scores.tech * techWt) + 
                      (scores.growth * growthWt) + 
                      (scores.age * ageWt) + 
                      (scores.benefits * benefitsWt) + 
                      (scores.visa * visaWt);
  
  const finalScore = totalWeight > 0 ? Math.round((weightedSum / totalWeight) * 10) : 0;

  // Update DOM
  const scoreNum = document.getElementById('calc-score-num');
  const scoreCircle = document.getElementById('calc-score-circle');
  const scoreExpl = document.getElementById('calc-score-explanation');

  if (scoreNum) scoreNum.textContent = finalScore;
  if (scoreExpl) scoreExpl.textContent = explanation + ` (Final Score: ${finalScore}/100)`;
  if (scoreCircle) {
    // Circle circumference is 2 * pi * r = 2 * 3.1415 * 15.9155 = 100
    scoreCircle.setAttribute('stroke-dasharray', `${finalScore}, 100`);
    
    // Update color based on score class
    scoreCircle.style.stroke = finalScore >= 80 ? 'var(--accent-green)' : (finalScore >= 60 ? 'var(--accent-blue)' : 'var(--accent-red)');
  }

  // Update Settings status sidebar score
  const sidebarScore = document.getElementById('set-status-score');
  if (sidebarScore) sidebarScore.textContent = `${finalScore}%`;
}

// ── Interactive Tag Inputs ───────────────────────────────────────────────────
function setupTagInput(containerId, initialTags = []) {
  const container = document.getElementById(containerId);
  if (!container) return;

  const wrapper = container.querySelector('.tags-wrapper');
  const input = container.querySelector('.tag-input-field');
  let tags = [...initialTags];

  function renderTags() {
    wrapper.innerHTML = tags.map((tag, idx) => `
      <span class="tag-badge">
        <span>${escHtml(tag)}</span>
        <button type="button" class="tag-close-btn" data-idx="${idx}">&times;</button>
      </span>
    `).join('');

    wrapper.querySelectorAll('.tag-close-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const idx = parseInt(btn.getAttribute('data-idx'));
        tags.splice(idx, 1);
        renderTags();
      });
    });
  }

  container.addEventListener('click', () => input.focus());

  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      const val = input.value.trim().replace(/,/g, '');
      if (val && !tags.includes(val)) {
        tags.push(val);
        input.value = '';
        renderTags();
      }
    } else if (e.key === 'Backspace' && !input.value && tags.length > 0) {
      tags.pop();
      renderTags();
    }
  });

  renderTags();
}

// ── Observability Logs ────────────────────────────────────────────────────────
const MOCK_SYSTEM_LOGS = [
  { time: '20:09:12', source: 'AI Logs', level: 'info', message: 'Inference request completed for "BrightLayer Health" (model: Gemini 1.5 Flash)' },
  { time: '20:08:45', source: 'Applications Logs', level: 'info', message: 'Compiled resume Facts profile: john_doe_java_backend.pdf' },
  { time: '20:07:33', source: 'Discovery Logs', level: 'info', message: 'Completed parsing Greenhouse feed for board "google". 12 jobs ingested.' },
  { time: '20:07:12', source: 'Matching Logs', level: 'info', message: 'Computed match score 92% for Senior Backend Engineer at Northstar Systems.' },
  { time: '20:06:50', source: 'API Logs', level: 'info', message: 'API handshake successful with Greenhouse board stream.' },
  { time: '20:05:12', source: 'Discovery Logs', level: 'info', message: 'Discovery cron triggered. Workers initialized: 1.' },
  { time: '20:04:10', source: 'Matching Logs', level: 'warn', message: 'Salary range not provided in LinkedIn job description. Using default matching average.' },
  { time: '20:02:15', source: 'API Logs', level: 'warn', message: 'Temporary rate limit (HTTP 429) hit on LinkedIn crawler. Cooldown enqueued.' },
  { time: '19:55:00', source: 'Error Logs', level: 'error', message: 'Connection timed out while simulating browser login on Workday portal.' },
  { time: '19:48:33', source: 'Applications Logs', level: 'info', message: 'Auto-retry enqueued for Atlas Grid backend developer application.' },
  { time: '19:45:10', source: 'AI Logs', level: 'info', message: 'Tailored resume generated grounding in 5 facts from candidate profile.' },
  { time: '19:40:02', source: 'Matching Logs', level: 'info', message: 'Filtered out company "Oracle" because it matched candidate blocklist.' },
  { time: '19:35:15', source: 'Discovery Logs', level: 'info', message: 'Detected duplicate listing for Platform Engineer at BrightLayer Health. Ingestion skipped.' },
  { time: '19:30:00', source: 'Applications Logs', level: 'info', message: 'Autonomous submission complete: submitted candidate profile to Meridian AI.' }
];

function renderSystemLogs() {
  const container = document.getElementById('log-rows-container');
  if (!container) return;

  const srcVal = document.getElementById('log-filter-source')?.value || 'all';
  const lvlVal = document.getElementById('log-filter-level')?.value || 'all';
  const query = (document.getElementById('log-search-input')?.value || '').toLowerCase();

  const filtered = MOCK_SYSTEM_LOGS.filter(log => {
    // Source filter
    if (srcVal !== 'all') {
      const formattedSrc = log.source.toLowerCase().replace(/\s+logs$/, '');
      if (formattedSrc !== srcVal) return false;
    }
    // Level filter
    if (lvlVal !== 'all' && log.level !== lvlVal) return false;
    // Query search
    if (query) {
      const matchMsg = log.message.toLowerCase().includes(query);
      const matchSrc = log.source.toLowerCase().includes(query);
      if (!matchMsg && !matchSrc) return false;
    }
    return true;
  });

  if (filtered.length === 0) {
    container.innerHTML = `<tr><td colspan="4" style="text-align:center;color:var(--text-muted);padding:40px;">No logs match your filter criteria.</td></tr>`;
    return;
  }

  container.innerHTML = filtered.map(log => `
    <tr>
      <td class="col-log-time">${escHtml(log.time)}</td>
      <td class="col-log-src">${escHtml(log.source)}</td>
      <td class="col-log-lvl ${log.level}">${escHtml(log.level.toUpperCase())}</td>
      <td class="col-log-msg">${escHtml(log.message)}</td>
    </tr>
  `).join('');
}

function exportSystemLogs() {
  const format = confirm('Export as CSV? (Cancel for JSON)') ? 'csv' : 'json';
  let dataStr = '';
  let filename = '';

  if (format === 'csv') {
    const csvRows = ['Time,Source,Level,Message'];
    MOCK_SYSTEM_LOGS.forEach(log => {
      csvRows.push(`"${log.time}","${log.source}","${log.level}","${log.message.replace(/"/g, '""')}"`);
    });
    dataStr = 'data:text/csv;charset=utf-8,' + encodeURIComponent(csvRows.join('\n'));
    filename = 'career_copilot_logs.csv';
  } else {
    dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(MOCK_SYSTEM_LOGS, null, 2));
    filename = 'career_copilot_logs.json';
  }

  const downloadAnchor = document.createElement('a');
  downloadAnchor.setAttribute('href', dataStr);
  downloadAnchor.setAttribute('download', filename);
  document.body.appendChild(downloadAnchor);
  downloadAnchor.click();
  downloadAnchor.remove();
}

// ── Automation Circuit Breakers ──────────────────────────────────────────────
function renderSettingsCircuitBreakers() {
  const container = document.getElementById('auto-breakers-list');
  if (!container) return;

  const breakers = {
    Greenhouse: 'CLOSED',
    LinkedIn: 'CLOSED',
    Lever: 'CLOSED',
    Workday: 'CLOSED',
    Indeed: 'CLOSED',
    Glassdoor: 'OPEN'
  };

  container.innerHTML = Object.entries(breakers).map(([platform, status]) => {
    const isClosed = status === 'CLOSED';
    const classBadge = isClosed ? 'closed' : 'open';
    return `
      <div class="breaker-card-row">
        <span class="breaker-title">${escHtml(platform)} sync connector</span>
        <span class="breaker-status-badge ${classBadge}">${escHtml(status)}</span>
      </div>
    `;
  }).join('');
}

function resetSettingsCircuitBreakers() {
  const btn = document.getElementById('auto-btn-reset-breakers');
  if (btn) {
    btn.disabled = true;
    btn.textContent = 'Resetting...';
  }
  
  setTimeout(() => {
    if (btn) {
      btn.disabled = false;
      btn.innerHTML = `<i data-lucide="refresh-cw" class="icon-sm"></i> Reset All Circuit Breakers`;
    }
    renderSettingsCircuitBreakers();
    if (window.lucide) window.lucide.createIcons();
    alert('All Crawler circuit breakers reset successfully!');
  }, 1000);
}

// ── Application Queues Visualizer ────────────────────────────────────────────
const MOCK_QUEUES = {
  pending: [
    { company: 'BrightLayer Health', role: 'Platform Engineer', reason: 'Waiting for autonomous worker availability.' },
    { company: 'Atlas Grid', role: 'Lead Java Architect', reason: 'Enqueued in submission scheduler.' }
  ],
  retry: [
    { company: 'GitHub', role: 'Developer Advocate', reason: 'Connection timeout on form submit. Retrying in 12m.' }
  ],
  failed: [
    { company: 'SpamCorp', role: 'Java Monolith Spammer', reason: 'Permanently blocked: Captcha solver failed repeatedly.' }
  ],
  priority: []
};

function setupQueueVisualizer() {
  const queueTabs = document.querySelectorAll('.queue-tab');
  const listContainer = document.getElementById('queue-list');
  if (!listContainer) return;

  queueTabs.forEach(tab => {
    tab.addEventListener('click', () => {
      queueTabs.forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      const queueName = tab.getAttribute('data-queue');
      renderQueueList(queueName);
    });
  });

  renderQueueList('pending');
}

function renderQueueList(queueName) {
  const container = document.getElementById('queue-list');
  if (!container) return;

  const items = MOCK_QUEUES[queueName] || [];

  if (items.length === 0) {
    container.innerHTML = `<p style="font-size:12px;color:var(--text-muted);padding:16px;text-align:center;">This queue is currently empty.</p>`;
    return;
  }

  container.innerHTML = items.map(item => `
    <div class="queue-item-row">
      <div class="q-meta">
        <span class="q-company">${escHtml(item.company)}</span>
        <span class="q-role">${escHtml(item.role)}</span>
        <span class="q-reason">${escHtml(item.reason)}</span>
      </div>
      <div class="q-actions">
        <button type="button" class="btn" style="padding:4px;" title="Promote to top"><i data-lucide="arrow-up" class="icon-sm"></i></button>
        <button type="button" class="btn btn-danger-icon" style="padding:4px;" title="Remove from queue"><i data-lucide="trash-2" class="icon-sm"></i></button>
      </div>
    </div>
  `).join('');
  if (window.lucide) window.lucide.createIcons();
}

// ── Settings status sidebar sync ──────────────────────────────────────────────
function updateSettingsStatusSidebar() {
  const setStatusState = document.getElementById('set-status-state');
  const setStatusDot = document.getElementById('set-status-dot');
  const setStatusTask = document.getElementById('set-status-task');
  const setStatusProvider = document.getElementById('set-status-provider');
  const setStatusModel = document.getElementById('set-status-model');

  if (setStatusState) {
    setStatusState.textContent = appState.halted ? 'PAUSED' : 'RUNNING';
    setStatusState.style.color = appState.halted ? 'var(--accent-red)' : 'var(--accent-green)';
  }

  if (setStatusDot) {
    setStatusDot.className = appState.halted ? 'pulse-glow-dot red' : 'pulse-glow-dot green';
  }

  if (setStatusTask) {
    setStatusTask.textContent = appState.halted ? 'Idle. Autopilot engines paused.' : 'Ingesting Greenhouse jobs...';
  }

  // Model updates
  const primaryProvider = document.getElementById('ai-provider')?.value || 'gemini';
  const primaryModel = document.getElementById('ai-model')?.value || 'gemini-1.5-flash';

  const providerMap = { gemini: 'Google Gemini', openai: 'OpenAI', anthropic: 'Anthropic Claude', openrouter: 'OpenRouter API', ollama: 'Ollama (Local)' };
  const modelMap = { 
    'gemini-1.5-flash': 'Gemini 1.5 Flash',
    'gemini-1.5-pro': 'Gemini 1.5 Pro',
    'gpt-4o': 'GPT-4o',
    'claude-3-5-sonnet': 'Claude 3.5 Sonnet'
  };

  if (setStatusProvider) setStatusProvider.textContent = providerMap[primaryProvider] || primaryProvider;
  if (setStatusModel) setStatusModel.textContent = modelMap[primaryModel] || primaryModel;
}

// ── Export and Import Settings ───────────────────────────────────────────────
function exportAgentSettings() {
  const currentSettings = {
    agentName: document.getElementById('gen-agent-name')?.value || '',
    timezone: document.getElementById('gen-timezone')?.value || 'UTC',
    language: document.getElementById('gen-language')?.value || 'en',
    autosave: document.getElementById('gen-autosave')?.checked || false,
    aiProvider: document.getElementById('ai-provider')?.value || 'gemini',
    aiModel: document.getElementById('ai-model')?.value || 'gemini-1.5-flash',
    aiFallback: document.getElementById('ai-fallback')?.value || '',
    aiTemperature: Number(document.getElementById('ai-temp')?.value || 0.2),
    autonomyThreshold: appState.threshold,
    salaryMin: Number(document.getElementById('disc-salary-min')?.value || 120000),
    salaryMax: Number(document.getElementById('disc-salary-max')?.value || 250000)
  };

  const dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(currentSettings, null, 2));
  const downloadAnchor = document.createElement('a');
  downloadAnchor.setAttribute('href', dataStr);
  downloadAnchor.setAttribute('download', 'career_copilot_settings_export.json');
  document.body.appendChild(downloadAnchor);
  downloadAnchor.click();
  downloadAnchor.remove();
  alert('Settings exported successfully!');
}

function importAgentSettings(e) {
  const file = e.target.files[0];
  if (!file) return;

  const reader = new FileReader();
  reader.onload = function(evt) {
    try {
      const imported = JSON.parse(evt.target.result);
      
      // Update form values
      if (imported.agentName && document.getElementById('gen-agent-name')) document.getElementById('gen-agent-name').value = imported.agentName;
      if (imported.timezone && document.getElementById('gen-timezone')) document.getElementById('gen-timezone').value = imported.timezone;
      if (imported.language && document.getElementById('gen-language')) document.getElementById('gen-language').value = imported.language;
      if (typeof imported.autosave === 'boolean' && document.getElementById('gen-autosave')) document.getElementById('gen-autosave').checked = imported.autosave;
      if (imported.aiProvider && document.getElementById('ai-provider')) document.getElementById('ai-provider').value = imported.aiProvider;
      if (imported.aiModel && document.getElementById('ai-model')) document.getElementById('ai-model').value = imported.aiModel;
      if (imported.aiFallback && document.getElementById('ai-fallback')) document.getElementById('ai-fallback').value = imported.aiFallback;
      if (typeof imported.aiTemperature === 'number' && document.getElementById('ai-temp')) {
        document.getElementById('ai-temp').value = imported.aiTemperature;
        const tempVal = document.getElementById('ai-temp-val');
        if (tempVal) tempVal.textContent = imported.aiTemperature;
      }
      if (typeof imported.autonomyThreshold === 'number') {
        appState.threshold = imported.autonomyThreshold;
        if (document.getElementById('wt-threshold')) document.getElementById('wt-threshold').value = imported.autonomyThreshold;
        if (els.threshold) els.threshold.value = imported.autonomyThreshold;
        if (els.thresholdValue) els.thresholdValue.value = imported.autonomyThreshold;
      }

      updateSettingsStatusSidebar();
      updateMatchingCalculator();
      alert('Settings imported and applied successfully!');
    } catch(err) {
      alert('Error: Failed to parse settings file. Make sure it is a valid JSON export.');
    }
  };
  reader.readAsText(file);
}

document.addEventListener('DOMContentLoaded', init);
