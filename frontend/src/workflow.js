/* ═══════════════════════════════════════════════════════════
   Career Copilot — Automation Workflow Simulation & Live Logic (Option B)
   ═══════════════════════════════════════════════════════════ */

'use strict';

(function () {
  // Scenario configurations for the branching workflow layout
  const SCENARIOS = {
    happy: {
      name: 'Happy Path (Direct Submit)',
      steps: [
        { node: 'node-discovery', tag: 'discovery', log: 'Ingesting Greenhouse board token "google"...', duration: 1500, stateClass: 'active', nodeStatusText: 'Syncing...' },
        { node: 'node-discovery', tag: 'discovery', log: 'Discovery completed. 12 potential roles loaded into cache.', duration: 500, stateClass: 'success', nodeStatusText: '12 found' },
        
        { node: 'node-matching', tag: 'matching', log: 'Evaluating "Senior Backend Engineer" at Northstar Systems. Checking work auth (PASSED) and seniority (PASSED). Evaluating vector cosine similarity...', duration: 2000, stateClass: 'active', nodeStatusText: 'Scoring...' },
        { node: 'node-matching', tag: 'matching', log: 'Similarity score: 92/100 (Threshold: 85) -> Match confirmed. Routing to document generators...', duration: 500, stateClass: 'success', nodeStatusText: 'Match (92)' },
        
        {
          tag: 'generation',
          log: 'Initializing parallel AI tasks: Resume Tailoring & Cover Letter Synthesizer...',
          duration: 1800,
          updates: [
            { node: 'node-resume', stateClass: 'active', nodeStatusText: 'Tailoring...' },
            { node: 'node-coverletter', stateClass: 'active', nodeStatusText: 'Synthesizing...' }
          ]
        },
        {
          tag: 'generation',
          log: 'Groundedness check PASSED. Document generation tasks completed successfully.',
          duration: 500,
          updates: [
            { node: 'node-resume', stateClass: 'success', nodeStatusText: 'Tailored' },
            { node: 'node-coverletter', stateClass: 'success', nodeStatusText: 'Ready' }
          ]
        },
        
        { node: 'node-autopilot', tag: 'autopilot', log: 'Launching headless Playwright browser. Navigating form and auto-filling contact fields and answers...', duration: 2200, stateClass: 'active', nodeStatusText: 'Submitting...' },
        { node: 'node-autopilot', tag: 'autopilot', log: 'Form submitted. Captured confirmation screenshot. Autopilot workflow completed.', duration: 1000, stateClass: 'success', nodeStatusText: 'Submitted' }
      ]
    },
    shadow: {
      name: 'Shadow Mode (Pause for Review)',
      steps: [
        { node: 'node-discovery', tag: 'discovery', log: 'Fetching new posts from Greenhouse board...', duration: 1500, stateClass: 'active', nodeStatusText: 'Syncing...' },
        { node: 'node-discovery', tag: 'discovery', log: 'Discovery completed. 12 potential roles loaded.', duration: 500, stateClass: 'success', nodeStatusText: '12 found' },
        
        { node: 'node-matching', tag: 'matching', log: 'Evaluating "Platform Engineer" at BrightLayer Health. Checking work auth (PASSED) and location type (PASSED). Scoring vector distance...', duration: 2000, stateClass: 'active', nodeStatusText: 'Scoring...' },
        { node: 'node-matching', tag: 'matching', log: 'Similarity score: 88/100 (Threshold: 85) -> Match confirmed.', duration: 500, stateClass: 'success', nodeStatusText: 'Match (88)' },
        
        {
          tag: 'generation',
          log: 'Generating tailored materials in parallel...',
          duration: 1800,
          updates: [
            { node: 'node-resume', stateClass: 'active', nodeStatusText: 'Tailoring...' },
            { node: 'node-coverletter', stateClass: 'active', nodeStatusText: 'Synthesizing...' }
          ]
        },
        {
          tag: 'generation',
          log: 'Resume and cover letter generated. Groundedness checks completed.',
          duration: 500,
          updates: [
            { node: 'node-resume', stateClass: 'success', nodeStatusText: 'Tailored' },
            { node: 'node-coverletter', stateClass: 'success', nodeStatusText: 'Ready' }
          ]
        },
        
        { node: 'node-autopilot', tag: 'autopilot', log: 'Running Playwright worker in Shadow mode. Filling Greenhouse form. Saving mock screenshot. Halting submission...', duration: 2200, stateClass: 'active', nodeStatusText: 'Filling...' },
        { node: 'node-autopilot', tag: 'autopilot', log: 'Shadow run complete. Form screenshot saved. Pausing for user manual review.', duration: 1000, stateClass: 'warning', nodeStatusText: 'Pending' }
      ]
    },
    skipped: {
      name: 'Low Match Score (Skipped)',
      steps: [
        { node: 'node-discovery', tag: 'discovery', log: 'Fetching Greenhouse board posts...', duration: 1500, stateClass: 'active', nodeStatusText: 'Syncing...' },
        { node: 'node-discovery', tag: 'discovery', log: 'Discovery completed. 12 potential roles loaded.', duration: 500, stateClass: 'success', nodeStatusText: '12 found' },
        
        { node: 'node-matching', tag: 'matching', log: 'Evaluating "Staff Java Engineer" at Atlas Grid. Checking seniority limits (FAILED: Candidate has 6 years, role requires 10+).', duration: 2000, stateClass: 'active', nodeStatusText: 'Scoring...' },
        { node: 'node-matching', tag: 'matching', log: 'Score: 74/100. (Threshold: 85). Autopilot policy: REJECT. Routing to skip path...', duration: 500, stateClass: 'success', nodeStatusText: 'Match (74)' },
        
        { node: 'node-filter', tag: 'matching', log: 'Application skipped: Match score below autonomy threshold (85). Marked in database as SKIPPED.', duration: 1000, stateClass: 'warning', nodeStatusText: 'Skipped (74)' }
      ]
    },
    blocked: {
      name: 'Blocklisted Company (Blocked)',
      steps: [
        { node: 'node-discovery', tag: 'discovery', log: 'Fetching new posts...', duration: 1500, stateClass: 'active', nodeStatusText: 'Syncing...' },
        { node: 'node-discovery', tag: 'discovery', log: 'Discovery completed.', duration: 500, stateClass: 'success', nodeStatusText: '12 found' },
        
        { node: 'node-matching', tag: 'matching', log: 'Evaluating "Backend Lead" at BlockedCo. Checking company blocklist rules...', duration: 1500, stateClass: 'active', nodeStatusText: 'Checking...' },
        { node: 'node-matching', tag: 'matching', log: 'MATCH FAILURE: Company "BlockedCo" matches blacklisted keyword rules. Routing to block path...', duration: 500, stateClass: 'success', nodeStatusText: 'Match (0)' },
        
        { node: 'node-filter', tag: 'matching', log: 'Application blocked: Company "BlockedCo" is blocklisted. Marked in database as BLOCKED.', duration: 1000, stateClass: 'blocked', nodeStatusText: 'Blocked' }
      ]
    }
  };

  let activeTimeout = null;
  let isSimulating = false;
  let autoCycleInterval = null;

  // Render curved lines connecting input/output ports of nodes dynamically
  function updateConnections() {
    const canvas = document.querySelector('.workflow-canvas');
    if (!canvas || canvas.offsetParent === null) return;

    const canvasRect = canvas.getBoundingClientRect();
    const connections = [
      { from: 'node-discovery', to: 'node-matching', pathId: 'wf-path-disc-match', pulseId: 'wf-pulse-disc-match' },
      { from: 'node-matching', to: 'node-resume', pathId: 'wf-path-match-resume', pulseId: 'wf-pulse-match-resume' },
      { from: 'node-matching', to: 'node-coverletter', pathId: 'wf-path-match-cover', pulseId: 'wf-pulse-match-cover' },
      { from: 'node-matching', to: 'node-filter', pathId: 'wf-path-match-filter', pulseId: 'wf-pulse-match-filter' },
      { from: 'node-resume', to: 'node-autopilot', pathId: 'wf-path-resume-auto', pulseId: 'wf-pulse-resume-auto' },
      { from: 'node-coverletter', to: 'node-autopilot', pathId: 'wf-path-cover-auto', pulseId: 'wf-pulse-cover-auto' }
    ];

    connections.forEach(conn => {
      const fromEl = document.getElementById(conn.from);
      const toEl = document.getElementById(conn.to);
      const pathEl = document.getElementById(conn.pathId);
      const pulseEl = document.getElementById(conn.pulseId);

      if (fromEl && toEl && pathEl) {
        const fromPort = fromEl.querySelector('.node-port-output');
        const toPort = toEl.querySelector('.node-port-input');

        if (fromPort && toPort) {
          const fromRect = fromPort.getBoundingClientRect();
          const toRect = toPort.getBoundingClientRect();

          const x1 = fromRect.left + fromRect.width / 2 - canvasRect.left;
          const y1 = fromRect.top + fromRect.height / 2 - canvasRect.top;
          const x2 = toRect.left + toRect.width / 2 - canvasRect.left;
          const y2 = toRect.top + toRect.height / 2 - canvasRect.top;

          const controlOffset = Math.abs(x2 - x1) * 0.45;
          const d = `M ${x1} ${y1} C ${x1 + controlOffset} ${y1}, ${x2 - controlOffset} ${y2}, ${x2} ${y2}`;

          pathEl.setAttribute('d', d);
          if (pulseEl) pulseEl.setAttribute('d', d);
          
          const fromState = fromEl.className;
          const toState = toEl.className;
          pathEl.className.baseVal = 'workflow-path';
          if (pulseEl) pulseEl.className.baseVal = 'workflow-pulse';

          if (fromState.includes('success')) {
            if (toState.includes('success')) {
              pathEl.classList.add('success');
            } else if (toState.includes('active')) {
              pathEl.classList.add('active');
              if (pulseEl) pulseEl.classList.add('active');
            } else if (toState.includes('blocked')) {
              pathEl.classList.add('blocked');
            } else if (toState.includes('warning')) {
              pathEl.classList.add('warning');
            } else {
              pathEl.classList.add('success');
            }
          } else if (fromState.includes('active')) {
            pathEl.classList.add('active');
            if (pulseEl) pulseEl.classList.add('active');
          }
        }
      }
    });
  }

  // Console output helper
  function addConsoleLog(tag, message) {
    const consoleBody = document.getElementById('wfConsoleBody');
    if (!consoleBody) return;

    const timeStr = new Date().toLocaleTimeString();
    const line = document.createElement('div');
    line.className = 'console-line';
    line.innerHTML = `
      <span class="console-ts">${timeStr}</span>
      <span class="console-tag ${tag}">${tag}</span>
      <span class="console-msg">${message}</span>
    `;

    consoleBody.appendChild(line);
    consoleBody.scrollTop = consoleBody.scrollHeight;
  }

  // Clear visual status of all nodes and lines
  function resetCanvas() {
    document.querySelectorAll('.workflow-node').forEach(node => {
      node.className = 'workflow-node';
      node.querySelector('.node-status').textContent = 'Idle';
      node.querySelector('.node-status-badge').innerHTML = '';
    });
    
    document.querySelectorAll('.workflow-path').forEach(p => p.className.baseVal = 'workflow-path');
    document.querySelectorAll('.workflow-pulse').forEach(p => p.className.baseVal = 'workflow-pulse');
  }

  // Execute a scenario step-by-step
  function runScenario(scenarioKey) {
    if (isSimulating) {
      clearTimeout(activeTimeout);
    }
    isSimulating = true;
    resetCanvas();
    
    const consoleBody = document.getElementById('wfConsoleBody');
    if (consoleBody) consoleBody.innerHTML = '';
    
    addConsoleLog('system', `Starting automation scenario: "${SCENARIOS[scenarioKey].name}"`);
    
    const steps = SCENARIOS[scenarioKey].steps;
    let currentStepIndex = 0;

    function executeNextStep() {
      if (currentStepIndex >= steps.length) {
        isSimulating = false;
        addConsoleLog('system', 'Workflow run simulation finished.');
        updateConnections();
        return;
      }

      const step = steps[currentStepIndex];
      const updates = step.updates || [step];

      updates.forEach(up => {
        const nodeEl = document.getElementById(up.node);
        if (nodeEl) {
          if (up.stateClass === 'active') {
            nodeEl.className = 'workflow-node active';
            nodeEl.querySelector('.node-status-badge').innerHTML = '<div class="node-spinner"></div>';
          } else if (up.stateClass === 'success') {
            nodeEl.className = 'workflow-node success';
            nodeEl.querySelector('.node-status-badge').innerHTML = '<i data-lucide="check-circle-2" class="icon-xs" style="color:var(--accent-green)"></i>';
          } else if (up.stateClass === 'warning') {
            nodeEl.className = 'workflow-node warning';
            nodeEl.querySelector('.node-status-badge').innerHTML = '<i data-lucide="alert-triangle" class="icon-xs" style="color:var(--accent-yellow)"></i>';
          } else if (up.stateClass === 'blocked') {
            nodeEl.className = 'workflow-node blocked';
            nodeEl.querySelector('.node-status-badge').innerHTML = '<i data-lucide="slash" class="icon-xs" style="color:var(--accent-red)"></i>';
          }
          nodeEl.querySelector('.node-status').textContent = up.nodeStatusText || 'Idle';
        }
      });

      if (window.lucide) window.lucide.createIcons();
      if (step.log) addConsoleLog(step.tag || 'system', step.log);
      updateConnections();

      currentStepIndex++;
      activeTimeout = setTimeout(executeNextStep, step.duration);
    }

    executeNextStep();
  }

  // Timer-driven simulation cycle
  function startAutoCycle() {
    stopAutoCycle();
    const keys = Object.keys(SCENARIOS);
    let index = 0;

    function cycle() {
      if (!isSimulating) {
        runScenario(keys[index]);
        const selector = document.getElementById('wfScenarioSelect');
        if (selector) selector.value = keys[index];
        index = (index + 1) % keys.length;
      }
    }

    cycle();
    autoCycleInterval = setInterval(cycle, 15000);
  }

  function stopAutoCycle() {
    if (autoCycleInterval) {
      clearInterval(autoCycleInterval);
      autoCycleInterval = null;
    }
  }

  // Live Mode: updates nodes and logs dynamically based on actual database records
  function updateWorkflowFromRealData(apps) {
    const selector = document.getElementById('wfScenarioSelect');
    if (!selector || selector.value !== 'live') return;

    resetCanvas();
    const consoleBody = document.getElementById('wfConsoleBody');
    if (consoleBody) consoleBody.innerHTML = '';

    if (!apps || apps.length === 0) {
      addConsoleLog('system', 'Monitoring live system... No active job applications found.');
      updateConnections();
      return;
    }

    const latestApp = apps[0];
    const status = latestApp.status ? latestApp.status.toLowerCase() : 'ready';
    const score = latestApp.matchScore ?? 0;

    // Node elements references
    const discNode = document.getElementById('node-discovery');
    const matchNode = document.getElementById('node-matching');
    const resumeNode = document.getElementById('node-resume');
    const coverNode = document.getElementById('node-coverletter');
    const filterNode = document.getElementById('node-filter');
    const autoNode = document.getElementById('node-autopilot');

    // 1. Discovery node is always green success (since we fetched application list)
    discNode.className = 'workflow-node success';
    discNode.querySelector('.node-status').textContent = 'Completed';
    discNode.querySelector('.node-status-badge').innerHTML = '<i data-lucide="check-circle-2" class="icon-xs" style="color:var(--accent-green)"></i>';

    // 2. Matching Node
    matchNode.className = 'workflow-node success';
    matchNode.querySelector('.node-status').textContent = `Match (${score})`;
    matchNode.querySelector('.node-status-badge').innerHTML = '<i data-lucide="check-circle-2" class="icon-xs" style="color:var(--accent-green)"></i>';

    // 3. Status logic
    if (status === 'blocked' || status === 'skipped') {
      // Route to filter node
      const isBlocked = status === 'blocked';
      filterNode.className = `workflow-node ${isBlocked ? 'blocked' : 'warning'}`;
      filterNode.querySelector('.node-status').textContent = isBlocked ? 'Blocked' : 'Skipped';
      filterNode.querySelector('.node-status-badge').innerHTML = isBlocked ? 
        '<i data-lucide="slash" class="icon-xs" style="color:var(--accent-red)"></i>' :
        '<i data-lucide="alert-triangle" class="icon-xs" style="color:var(--accent-yellow)"></i>';
      
      resumeNode.className = 'workflow-node';
      coverNode.className = 'workflow-node';
      autoNode.className = 'workflow-node';
    } else {
      // Normal flow (queued, generating, verifying, ready, submitted, failed)
      if (status === 'queued' || status === 'generating' || status === 'verifying') {
        resumeNode.className = 'workflow-node active';
        resumeNode.querySelector('.node-status').textContent = 'Tailoring...';
        resumeNode.querySelector('.node-status-badge').innerHTML = '<div class="node-spinner"></div>';

        coverNode.className = 'workflow-node active';
        coverNode.querySelector('.node-status').textContent = 'Synthesizing...';
        coverNode.querySelector('.node-status-badge').innerHTML = '<div class="node-spinner"></div>';

        autoNode.className = 'workflow-node';
      } else {
        // ready, submitted, failed
        resumeNode.className = 'workflow-node success';
        resumeNode.querySelector('.node-status').textContent = 'Tailored';
        resumeNode.querySelector('.node-status-badge').innerHTML = '<i data-lucide="check-circle-2" class="icon-xs" style="color:var(--accent-green)"></i>';

        coverNode.className = 'workflow-node success';
        coverNode.querySelector('.node-status').textContent = 'Ready';
        coverNode.querySelector('.node-status-badge').innerHTML = '<i data-lucide="check-circle-2" class="icon-xs" style="color:var(--accent-green)"></i>';

        if (status === 'ready') {
          autoNode.className = 'workflow-node warning';
          autoNode.querySelector('.node-status').textContent = 'Pending Review';
          autoNode.querySelector('.node-status-badge').innerHTML = '<i data-lucide="alert-triangle" class="icon-xs" style="color:var(--accent-yellow)"></i>';
        } else if (status === 'submitted') {
          autoNode.className = 'workflow-node success';
          autoNode.querySelector('.node-status').textContent = 'Submitted';
          autoNode.querySelector('.node-status-badge').innerHTML = '<i data-lucide="check-circle-2" class="icon-xs" style="color:var(--accent-green)"></i>';
        } else if (status === 'failed') {
          autoNode.className = 'workflow-node blocked';
          autoNode.querySelector('.node-status').textContent = 'Failed';
          autoNode.querySelector('.node-status-badge').innerHTML = '<i data-lucide="slash" class="icon-xs" style="color:var(--accent-red)"></i>';
        }
      }
    }

    if (window.lucide) window.lucide.createIcons();

    // 4. Render real audit trail logs
    const audit = latestApp.auditTrail || [];
    if (audit.length === 0) {
      addConsoleLog('system', `Live monitoring: "${latestApp.company}" (Role: ${latestApp.title}). Status: ${status.toUpperCase()}`);
    } else {
      audit.forEach(entry => {
        let msg = entry;
        let tsStr = new Date().toLocaleTimeString();
        if (entry.startsWith('[')) {
          const closeIdx = entry.indexOf(']');
          if (closeIdx !== -1) {
            const timePart = entry.substring(1, closeIdx);
            try {
              tsStr = new Date(timePart).toLocaleTimeString();
            } catch (e) {}
            msg = entry.substring(closeIdx + 1).trim();
          }
        }
        
        // classify tag
        let tag = 'system';
        const lowerMsg = msg.toLowerCase();
        if (lowerMsg.includes('greenhouse') || lowerMsg.includes('sync') || lowerMsg.includes('discovery') || lowerMsg.includes('feed')) {
          tag = 'discovery';
        } else if (lowerMsg.includes('matching') || lowerMsg.includes('score') || lowerMsg.includes('autonomy') || lowerMsg.includes('threshold')) {
          tag = 'matching';
        } else if (lowerMsg.includes('groundedness') || lowerMsg.includes('generation') || lowerMsg.includes('resume') || lowerMsg.includes('cover letter')) {
          tag = 'generation';
        } else if (lowerMsg.includes('playwright') || lowerMsg.includes('worker') || lowerMsg.includes('submitted') || lowerMsg.includes('shadow')) {
          tag = 'autopilot';
        }

        const line = document.createElement('div');
        line.className = 'console-line';
        line.innerHTML = `
          <span class="console-ts">${tsStr}</span>
          <span class="console-tag ${tag}">${tag}</span>
          <span class="console-msg">${escHtml(msg)}</span>
        `;
        if (consoleBody) consoleBody.appendChild(line);
      });
      if (consoleBody) consoleBody.scrollTop = consoleBody.scrollHeight;
    }

    updateConnections();
  }

  // Security helper: escape HTML
  function escHtml(str) {
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  // Setup DOM wiring
  function initWorkflow() {
    const triggerBtn = document.getElementById('wfTriggerRun');
    const scenarioSelect = document.getElementById('wfScenarioSelect');
    const autoCheckbox = document.getElementById('wfAutoCycle');

    if (triggerBtn && scenarioSelect) {
      triggerBtn.addEventListener('click', () => {
        if (autoCheckbox && autoCheckbox.checked) {
          autoCheckbox.checked = false;
          stopAutoCycle();
        }
        if (scenarioSelect.value === 'live') {
          if (typeof window.refreshApplications === 'function') {
            window.refreshApplications();
          }
        } else {
          runScenario(scenarioSelect.value);
        }
      });
    }

    if (scenarioSelect) {
      scenarioSelect.addEventListener('change', (e) => {
        if (e.target.value === 'live') {
          if (autoCheckbox) autoCheckbox.checked = false;
          stopAutoCycle();
          
          const currentApps = (typeof appState !== 'undefined') ? appState.applications : [];
          updateWorkflowFromRealData(currentApps);
        } else {
          runScenario(e.target.value);
        }
      });
    }

    if (autoCheckbox) {
      autoCheckbox.addEventListener('change', (e) => {
        if (e.target.checked) {
          if (scenarioSelect && scenarioSelect.value === 'live') {
            scenarioSelect.value = 'happy';
          }
          startAutoCycle();
        } else {
          stopAutoCycle();
        }
      });
    }

    const nodeRoutes = {
      'node-discovery': 'discovery',
      'node-matching': 'matching',
      'node-resume': 'generation',
      'node-coverletter': 'generation',
      'node-filter': 'applications',
      'node-autopilot': 'automation'
    };

    Object.keys(nodeRoutes).forEach(nodeId => {
      const nodeEl = document.getElementById(nodeId);
      if (nodeEl) {
        nodeEl.addEventListener('click', () => {
          if (typeof window.handleNavigation === 'function') {
            window.handleNavigation(nodeRoutes[nodeId]);
          }
        });
      }
    });

    window.addEventListener('resize', updateConnections);
    
    // Initial draw delay to let layout settle
    setTimeout(() => {
      updateConnections();
      if (scenarioSelect && scenarioSelect.value === 'live') {
        const currentApps = (typeof appState !== 'undefined') ? appState.applications : [];
        updateWorkflowFromRealData(currentApps);
      } else {
        runScenario('happy');
      }
    }, 100);
  }

  // Publish global helpers
  window.updateWorkflowConnections = updateConnections;
  window.updateWorkflowFromRealData = updateWorkflowFromRealData;

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initWorkflow);
  } else {
    initWorkflow();
  }
})();
