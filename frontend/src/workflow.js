/* ═══════════════════════════════════════════════════════════
   Career Copilot — Automation Workflow Simulation Logic (n8n Style)
   ═══════════════════════════════════════════════════════════ */

'use strict';

(function () {
  // Scenario configurations defining nodes, durations, visual states, and console logs
  const SCENARIOS = {
    happy: {
      name: 'Happy Path (Direct Submit)',
      steps: [
        { node: 'node-discovery', tag: 'discovery', status: 'Syncing Greenhouse feed...', log: 'Fetching new posts from Greenhouse board "google"...', duration: 1500, stateClass: 'active', nodeStatusText: 'Syncing...' },
        { node: 'node-discovery', tag: 'discovery', status: '12 jobs found', log: 'Discovery completed. 12 potential roles loaded into cache.', duration: 500, stateClass: 'success', nodeStatusText: '12 found' },
        
        { node: 'node-matching', tag: 'matching', status: 'Scoring match...', log: 'Evaluating "Senior Backend Engineer" at Northstar Systems. Checking work authorization (PASSED). Checking seniority (PASSED). Computing vector cosine similarity...', duration: 2000, stateClass: 'active', nodeStatusText: 'Scoring...' },
        { node: 'node-matching', tag: 'matching', status: 'Score: 92 (Match)', log: 'Cosine similarity: 0.92. Keyword intersection match: 90%. Computed score: 92/100 (Threshold: 85). MATCH VALIDATED.', duration: 500, stateClass: 'success', nodeStatusText: 'Match (92)' },
        
        { node: 'node-generation', tag: 'generation', status: 'Generating docs...', log: 'Compiling facts profile. Synthesizing tailored cover letter. Running GroundednessVerifier (Passed: 100% verified facts, 0 hallucinations).', duration: 1800, stateClass: 'active', nodeStatusText: 'Generating...' },
        { node: 'node-generation', tag: 'generation', status: 'Docs compiled', log: 'Tailored resume and cover letter successfully compiled and saved in memory.', duration: 500, stateClass: 'success', nodeStatusText: 'Ready' },
        
        { node: 'node-autopilot', tag: 'autopilot', status: 'Submitting...', log: 'Initializing TypeScript Playwright headless worker. Launching browser. Filling form fields. Executing submission handlers...', duration: 2200, stateClass: 'active', nodeStatusText: 'Submitting...' },
        { node: 'node-autopilot', tag: 'autopilot', status: 'Submitted', log: 'Application successfully submitted to Northstar Systems. Saved confirmation screenshot. Status updated to SUBMITTED.', duration: 1000, stateClass: 'success', nodeStatusText: 'Submitted' }
      ]
    },
    shadow: {
      name: 'Shadow Mode (Pause for Review)',
      steps: [
        { node: 'node-discovery', tag: 'discovery', status: 'Syncing Greenhouse feed...', log: 'Fetching new posts from Greenhouse board "google"...', duration: 1500, stateClass: 'active', nodeStatusText: 'Syncing...' },
        { node: 'node-discovery', tag: 'discovery', status: '12 jobs found', log: 'Discovery completed. 12 potential roles loaded.', duration: 500, stateClass: 'success', nodeStatusText: '12 found' },
        
        { node: 'node-matching', tag: 'matching', status: 'Scoring match...', log: 'Evaluating "Platform Engineer" at BrightLayer Health. Checking work auth (PASSED). Checking location preference (PASSED: Hybrid). Computing similarity score...', duration: 2000, stateClass: 'active', nodeStatusText: 'Scoring...' },
        { node: 'node-matching', tag: 'matching', status: 'Score: 88 (Match)', log: 'Similarity: 0.88. Match score: 88/100 (Threshold: 85). MATCH VALIDATED.', duration: 500, stateClass: 'success', nodeStatusText: 'Match (88)' },
        
        { node: 'node-generation', tag: 'generation', status: 'Generating docs...', log: 'Compiling facts. Synthesizing tailored cover letter. Running GroundednessVerifier (Passed).', duration: 1800, stateClass: 'active', nodeStatusText: 'Generating...' },
        { node: 'node-generation', tag: 'generation', status: 'Docs compiled', log: 'Tailored resume and cover letter created.', duration: 500, stateClass: 'success', nodeStatusText: 'Ready' },
        
        { node: 'node-autopilot', tag: 'autopilot', status: 'Filling form (Shadow)...', log: 'Initializing Playwright worker in SHADOW mode. Launching browser. Filling form fields. Taking mock confirmation screenshot. Halting submission due to Shadow Mode active.', duration: 2200, stateClass: 'active', nodeStatusText: 'Filling...' },
        { node: 'node-autopilot', tag: 'autopilot', status: 'Pending Review', log: 'Shadow run complete. Saved filled form preview. Halting for manual approval before clicking Submit.', duration: 1000, stateClass: 'warning', nodeStatusText: 'Pending' }
      ]
    },
    skipped: {
      name: 'Low Match Score (Skipped)',
      steps: [
        { node: 'node-discovery', tag: 'discovery', status: 'Syncing Greenhouse feed...', log: 'Fetching new posts from Greenhouse board "google"...', duration: 1500, stateClass: 'active', nodeStatusText: 'Syncing...' },
        { node: 'node-discovery', tag: 'discovery', status: '12 jobs found', log: 'Discovery completed. 12 potential roles loaded.', duration: 500, stateClass: 'success', nodeStatusText: '12 found' },
        
        { node: 'node-matching', tag: 'matching', status: 'Scoring match...', log: 'Evaluating "Staff Java Engineer" at Atlas Grid. Checking seniority requirements (FAILED: Requires 10+ years experience, candidate has 6). Computing vector similarity...', duration: 2000, stateClass: 'active', nodeStatusText: 'Scoring...' },
        { node: 'node-matching', tag: 'matching', status: 'Score: 74 (Skipped)', log: 'Match score: 74/100. Autonomy threshold is 85. Halting workflow. Application marked as SKIPPED.', duration: 1000, stateClass: 'warning', nodeStatusText: 'Skipped (74)' }
      ]
    },
    blocked: {
      name: 'Blocklisted Company (Blocked)',
      steps: [
        { node: 'node-discovery', tag: 'discovery', status: 'Syncing Greenhouse feed...', log: 'Fetching new posts...', duration: 1500, stateClass: 'active', nodeStatusText: 'Syncing...' },
        { node: 'node-discovery', tag: 'discovery', status: '12 jobs found', log: 'Discovery completed.', duration: 500, stateClass: 'success', nodeStatusText: '12 found' },
        
        { node: 'node-matching', tag: 'matching', status: 'Checking blocklist...', log: 'Evaluating "Backend Lead" at BlockedCo. Checking company blocklist rules...', duration: 1500, stateClass: 'active', nodeStatusText: 'Checking...' },
        { node: 'node-matching', tag: 'matching', status: 'Blocked Company', log: 'MATCH FAILURE: Company "BlockedCo" matches blacklisted keyword rules. Halting workflow immediately. Status marked as BLOCKED.', duration: 1000, stateClass: 'blocked', nodeStatusText: 'Blocked' }
      ]
    }
  };

  let activeTimeout = null;
  let isSimulating = false;
  let autoCycleInterval = null;

  // Render curved lines connecting input/output ports of nodes dynamically
  function updateConnections() {
    const canvas = document.querySelector('.workflow-canvas');
    if (!canvas || canvas.offsetParent === null) return; // Hidden or not rendered

    const canvasRect = canvas.getBoundingClientRect();
    const connections = [
      { from: 'node-discovery', to: 'node-matching', pathId: 'wf-path-disc-match', pulseId: 'wf-pulse-disc-match' },
      { from: 'node-matching', to: 'node-generation', pathId: 'wf-path-match-gen', pulseId: 'wf-pulse-match-gen' },
      { from: 'node-generation', to: 'node-autopilot', pathId: 'wf-path-gen-auto', pulseId: 'wf-pulse-gen-auto' }
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
          
          // Sync connection colors based on node statuses
          const fromState = fromEl.className;
          pathEl.className.baseVal = 'workflow-path';
          if (pulseEl) pulseEl.className.baseVal = 'workflow-pulse';

          if (fromState.includes('success')) {
            // Check target state to set success or active line
            const toState = toEl.className;
            if (toState.includes('success') || toState.includes('warning') || toState.includes('blocked')) {
              pathEl.classList.add(toState.includes('success') ? 'success' : (toState.includes('blocked') ? 'blocked' : 'warning'));
            } else if (toState.includes('active')) {
              pathEl.classList.add('active');
              if (pulseEl) pulseEl.classList.add('active');
            } else {
              // from is success, target is idle/pending
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
      const nodeEl = document.getElementById(step.node);

      if (nodeEl) {
        // Set state class
        if (step.stateClass === 'active') {
          nodeEl.className = 'workflow-node active';
          nodeEl.querySelector('.node-status-badge').innerHTML = '<div class="node-spinner"></div>';
        } else if (step.stateClass === 'success') {
          nodeEl.className = 'workflow-node success';
          nodeEl.querySelector('.node-status-badge').innerHTML = '<i data-lucide="check-circle-2" class="icon-xs" style="color:var(--accent-green)"></i>';
        } else if (step.stateClass === 'warning') {
          nodeEl.className = 'workflow-node warning';
          nodeEl.querySelector('.node-status-badge').innerHTML = '<i data-lucide="alert-triangle" class="icon-xs" style="color:var(--accent-yellow)"></i>';
        } else if (step.stateClass === 'blocked') {
          nodeEl.className = 'workflow-node blocked';
          nodeEl.querySelector('.node-status-badge').innerHTML = '<i data-lucide="slash" class="icon-xs" style="color:var(--accent-red)"></i>';
        }

        nodeEl.querySelector('.node-status').textContent = step.nodeStatusText;
        if (window.lucide) window.lucide.createIcons();
        
        addConsoleLog(step.tag, step.log);
        updateConnections();
      }

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
        // Update scenario dropdown selection
        const selector = document.getElementById('wfScenarioSelect');
        if (selector) selector.value = keys[index];
        index = (index + 1) % keys.length;
      }
    }

    cycle(); // Initial run immediately
    autoCycleInterval = setInterval(cycle, 14000); // Cycle every 14 seconds (sim durations total ~8-9s)
  }

  function stopAutoCycle() {
    if (autoCycleInterval) {
      clearInterval(autoCycleInterval);
      autoCycleInterval = null;
    }
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
          addConsoleLog('system', 'Auto-cycle disabled via manual trigger.');
        }
        runScenario(scenarioSelect.value);
      });
    }

    if (autoCheckbox) {
      autoCheckbox.addEventListener('change', (e) => {
        if (e.target.checked) {
          startAutoCycle();
        } else {
          stopAutoCycle();
          addConsoleLog('system', 'Auto-cycle disabled.');
        }
      });
    }

    // Interactivity: clicking a node switches pages/tabs
    const nodeRoutes = {
      'node-discovery': 'discovery',
      'node-matching': 'matching',
      'node-generation': 'generation',
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

    // Handle initial drawing and resize
    window.addEventListener('resize', updateConnections);
    
    // Draw initial lines
    setTimeout(() => {
      updateConnections();
      if (autoCheckbox && autoCheckbox.checked) {
        startAutoCycle();
      } else {
        runScenario('happy');
      }
    }, 100);
  }

  // Make updateConnections accessible globally so app.js can trigger it when showing automation tab
  window.updateWorkflowConnections = updateConnections;

  // Auto-init when DOM is loaded or when app.js manually flags
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initWorkflow);
  } else {
    initWorkflow();
  }
})();
