import { chromium, Page, BrowserContext } from 'playwright';
import * as fs from 'fs';
import * as path from 'path';
import { AutomationCommand, AutomationResult } from './types';

// ─── Constants ───────────────────────────────────────────────────────────────

const SCREENSHOTS_DIR = path.resolve(process.cwd(), 'screenshots');
const NAVIGATION_TIMEOUT_MS = 30_000;

/** Regex matching button text / type that we must NEVER click (shadow-mode safety). */
const SUBMIT_PATTERN = /submit|apply|send|continue|next/i;

// ─── Helpers ─────────────────────────────────────────────────────────────────

function ts(): string {
  return `[${new Date().toISOString()}]`;
}

function log(msg: string): string {
  const line = `${ts()} ${msg}`;
  console.log(line);
  return line;
}

/** Ensure screenshots directory exists. */
function ensureScreenshotsDir(): void {
  if (!fs.existsSync(SCREENSHOTS_DIR)) {
    fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
  }
}

/**
 * Build a simple stub resume payload from the command's profile/document IDs.
 * In a real implementation these would be fetched from the profile service.
 */
function buildStubResumeData(command: AutomationCommand): Record<string, string> {
  const namePrefix = command.profileSnapshotId.substring(0, 8);
  return {
    name: `Candidate ${namePrefix}`,
    firstName: `Candidate`,
    lastName: namePrefix,
    fullName: `Candidate ${namePrefix}`,
    email: `candidate-${namePrefix}@example.com`,
    phone: '+1-555-000-0000',
    address: '123 Main St, Springfield, USA',
    linkedIn: `https://linkedin.com/in/candidate-${namePrefix}`,
    website: `https://portfolio-${namePrefix}.example.com`,
    coverLetter: `I am writing to express my strong interest in this position. ` +
      `My profile (ref: ${command.profileSnapshotId}) and resume ` +
      `(ref: ${command.resumeDocumentId}) demonstrate the skills required. ` +
      `I look forward to discussing how I can contribute to your team.`,
    summary: `Experienced professional (profile: ${command.profileSnapshotId}) ` +
      `seeking to bring value through technical and collaborative skills.`,
  };
}

/**
 * Determine which stub value to fill for a given field based on its name/id/placeholder/label.
 */
function chooseValue(
  resume: Record<string, string>,
  identifier: string,
): string | null {
  const id = identifier.toLowerCase();

  if (/\bfirst[\s_-]?name\b/.test(id)) return resume.firstName;
  if (/\blast[\s_-]?name\b/.test(id)) return resume.lastName;
  if (/\b(full[\s_-]?name|your[\s_-]?name)\b/.test(id)) return resume.fullName;
  if (/\bname\b/.test(id)) return resume.fullName;
  if (/\bemail\b/.test(id)) return resume.email;
  if (/\bphone|mobile|tel\b/.test(id)) return resume.phone;
  if (/\baddress\b/.test(id)) return resume.address;
  if (/\blinkedin\b/.test(id)) return resume.linkedIn;
  if (/\bwebsite|portfolio|url\b/.test(id)) return resume.website;
  if (/\bcover[\s_-]?letter\b/.test(id)) return resume.coverLetter;
  if (/\bsummary|objective|bio|about\b/.test(id)) return resume.summary;

  // Default: for any textarea, provide a generic message
  return null;
}

/**
 * Collect all visible, editable text inputs and textareas on the page.
 * Returns an array of {locator, identifier} pairs.
 */
async function collectFillableFields(page: Page): Promise<
  Array<{ selector: string; identifier: string; type: 'input' | 'textarea' }>
> {
  const fields: Array<{ selector: string; identifier: string; type: 'input' | 'textarea' }> = [];

  // Text inputs (excluding hidden, submit, button, file, checkbox, radio, image, reset)
  const inputHandles = await page.locator(
    'input:not([type="hidden"]):not([type="submit"]):not([type="button"])' +
    ':not([type="file"]):not([type="checkbox"]):not([type="radio"])' +
    ':not([type="image"]):not([type="reset"])',
  ).all();

  for (const handle of inputHandles) {
    try {
      const visible = await handle.isVisible();
      const enabled = await handle.isEnabled();
      if (!visible || !enabled) continue;

      const name = (await handle.getAttribute('name')) ?? '';
      const id = (await handle.getAttribute('id')) ?? '';
      const placeholder = (await handle.getAttribute('placeholder')) ?? '';
      const ariaLabel = (await handle.getAttribute('aria-label')) ?? '';
      const identifier = name || id || ariaLabel || placeholder || 'input';

      // Build a deterministic selector
      const selector = name
        ? `input[name="${name}"]`
        : id
          ? `#${id}`
          : `input[placeholder="${placeholder}"]`;

      fields.push({ selector, identifier, type: 'input' });
    } catch {
      // Skip inaccessible handles
    }
  }

  // Textareas
  const textareaHandles = await page.locator('textarea').all();
  for (const handle of textareaHandles) {
    try {
      const visible = await handle.isVisible();
      const enabled = await handle.isEnabled();
      if (!visible || !enabled) continue;

      const name = (await handle.getAttribute('name')) ?? '';
      const id = (await handle.getAttribute('id')) ?? '';
      const placeholder = (await handle.getAttribute('placeholder')) ?? '';
      const ariaLabel = (await handle.getAttribute('aria-label')) ?? '';
      const identifier = name || id || ariaLabel || placeholder || 'textarea';

      const selector = name
        ? `textarea[name="${name}"]`
        : id
          ? `#${id}`
          : 'textarea';

      fields.push({ selector, identifier, type: 'textarea' });
    } catch {
      // Skip inaccessible handles
    }
  }

  return fields;
}

// ─── Main Worker ─────────────────────────────────────────────────────────────

export async function runShadowCommand(command: AutomationCommand): Promise<AutomationResult> {
  const logs: string[] = [];
  const fieldsFilled: string[] = [];
  const unsupportedFields: string[] = [];

  logs.push(log(`Starting shadow worker for applicationId=${command.applicationId}`));
  logs.push(log(`Target URL: ${command.jobUrl}`));
  logs.push(log(`Mode: ${command.mode} — submit buttons will NOT be clicked`));

  if (command.mode !== 'shadow') {
    const msg = `Worker only supports shadow mode; received mode="${command.mode}"`;
    logs.push(log(`WARN: ${msg}`));
    // Still proceed in shadow mode for safety — never submit
  }

  ensureScreenshotsDir();
  const screenshotPath = path.join(SCREENSHOTS_DIR, `${command.applicationId}.png`);
  const resume = buildStubResumeData(command);

  let browser = null;
  let context: BrowserContext | null = null;

  try {
    logs.push(log('Launching Chromium browser (headless)…'));
    browser = await chromium.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
    });

    context = await browser.newContext({
      userAgent:
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) ' +
        'AppleWebKit/537.36 (KHTML, like Gecko) ' +
        'Chrome/131.0.0.0 Safari/537.36',
      viewport: { width: 1280, height: 900 },
    });

    const page: Page = await context.newPage();
    page.setDefaultTimeout(NAVIGATION_TIMEOUT_MS);

    // ── Navigate ────────────────────────────────────────────────────────────
    logs.push(log(`Navigating to ${command.jobUrl} …`));
    await page.goto(command.jobUrl, {
      waitUntil: 'domcontentloaded',
      timeout: NAVIGATION_TIMEOUT_MS,
    });
    logs.push(log(`Page loaded: ${page.url()}`));

    // Wait a moment for dynamic content to settle
    await page.waitForTimeout(2000);

    // ── Sanity-check: no submit buttons will be clicked ──────────────────
    // We enumerate all buttons to log what we're explicitly skipping.
    const allButtons = await page.locator('button, input[type="submit"], a[role="button"]').all();
    for (const btn of allButtons) {
      try {
        const text = (await btn.textContent()) ?? '';
        const typeAttr = (await btn.getAttribute('type')) ?? '';
        if (SUBMIT_PATTERN.test(text) || SUBMIT_PATTERN.test(typeAttr)) {
          logs.push(log(`SHADOW SKIP (submit-guard): button text="${text.trim()}" type="${typeAttr}"`));
        }
      } catch {
        /* ignore */
      }
    }

    // ── Collect & fill fields ────────────────────────────────────────────
    logs.push(log('Discovering form fields…'));
    const fields = await collectFillableFields(page);
    logs.push(log(`Found ${fields.length} fillable field(s)`));

    for (const field of fields) {
      let value = chooseValue(resume, field.identifier);

      // For textareas with no specific match, fall back to the cover letter text
      if (value === null && field.type === 'textarea') {
        value = resume.coverLetter;
      }

      if (value === null) {
        unsupportedFields.push(field.identifier);
        logs.push(log(`SKIP (no mapping): ${field.identifier}`));
        continue;
      }

      try {
        const locator = page.locator(field.selector).first();
        const isVisible = await locator.isVisible();
        if (!isVisible) {
          unsupportedFields.push(field.identifier);
          logs.push(log(`SKIP (not visible): ${field.identifier}`));
          continue;
        }

        // Clear existing value then type
        await locator.fill(value);
        fieldsFilled.push(field.identifier);
        logs.push(log(`FILLED: ${field.identifier} = "${value.substring(0, 40)}…"`));
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        unsupportedFields.push(field.identifier);
        logs.push(log(`ERROR filling ${field.identifier}: ${msg}`));
      }
    }

    // ── Screenshot ──────────────────────────────────────────────────────
    logs.push(log(`Taking full-page screenshot → ${screenshotPath}`));
    await page.screenshot({ path: screenshotPath, fullPage: true });
    logs.push(log(`Screenshot saved: ${screenshotPath}`));

    logs.push(log(`Shadow worker complete. fieldsFilled=${fieldsFilled.length}, unsupported=${unsupportedFields.length}`));

    return {
      applicationId: command.applicationId,
      status: 'shadow_completed',
      fieldsFilled,
      unsupportedFields,
      screenshotPath,
      logs,
    };
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    logs.push(log(`FATAL ERROR: ${message}`));
    console.error(`${ts()} [shadow-worker] Fatal error for ${command.applicationId}:`, err);

    return {
      applicationId: command.applicationId,
      status: 'failed',
      fieldsFilled,
      unsupportedFields,
      screenshotPath: undefined,
      logs,
    };
  } finally {
    if (context) {
      try { await context.close(); } catch { /* ignore */ }
    }
    if (browser) {
      try { await browser.close(); } catch { /* ignore */ }
      logs.push(log('Browser closed.'));
    }
  }
}
