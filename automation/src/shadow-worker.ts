import { chromium, Page, BrowserContext } from 'playwright';
import * as fs from 'fs';
import * as path from 'path';
import { AutomationCommand, AutomationResult } from './types';
import { redisClient, publishResult } from './redis-publisher';

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
 * Build real resume/profile payload from the command's fields.
 */
function buildRealResumeData(command: AutomationCommand): Record<string, string> {
  const fullName = command.candidateName || 'Candidate';
  const nameParts = fullName.split(' ');
  const firstName = nameParts[0] || 'Candidate';
  const lastName = nameParts.slice(1).join(' ') || 'Name';

  return {
    name: fullName,
    firstName: firstName,
    lastName: lastName,
    fullName: fullName,
    email: command.candidateEmail || '',
    phone: command.candidatePhone || '',
    address: '123 Main St, Springfield, USA',
    linkedIn: command.candidateLinkedin || '',
    website: command.candidateWebsite || '',
    coverLetter: command.coverLetterContent || '',
    summary: command.resumeContent || '',
  };
}

/**
 * Determine which value to fill for a given field based on its identifier, label, and type.
 */
function chooseValue(
  resume: Record<string, string>,
  identifier: string,
  labelText: string,
  type: string,
  tempResumePath: string,
  tempCoverLetterPath: string
): string | null {
  const combined = `${identifier} ${labelText}`.toLowerCase();

  if (type === 'file') {
    if (/\b(resume|cv)\b/.test(combined)) return tempResumePath;
    if (/\bcover\b/.test(combined)) return tempCoverLetterPath;
    return null;
  }

  if (/\bfirst[\s_-]?name\b/.test(combined)) return resume.firstName;
  if (/\blast[\s_-]?name\b/.test(combined)) return resume.lastName;
  if (/\b(full[\s_-]?name|your[\s_-]?name)\b/.test(combined)) return resume.fullName;
  if (/\bname\b/.test(combined)) return resume.fullName;
  if (/\bemail\b/.test(combined)) return resume.email;
  if (/\bphone|mobile|tel\b/.test(combined)) return resume.phone;
  if (/\baddress\b/.test(combined)) return resume.address;
  if (/\blinkedin\b/.test(combined)) return resume.linkedIn;
  if (/\b(website|portfolio|url|github)\b/.test(combined)) return resume.website;
  if (/\bcover[\s_-]?letter\b/.test(combined)) return resume.coverLetter;
  if (/\bsummary|objective|bio|about\b/.test(combined)) return resume.summary;

  // Custom Greenhouse & EEOC drop-down rules
  if (/\bcountry\b/.test(combined)) return 'India';
  if (/\b(location|city)\b/.test(combined)) return 'Kolkata, West Bengal, India';
  if (/\b(sponsorship|require.*visa)\b/.test(combined)) return 'Yes';
  if (/\bauthorized\b/.test(combined)) return 'No';
  
  // Custom yes/no tech questions
  if (/\b(java|spring|react|typescript|backend|frontend|cloud|aws|gcp|docker|kubernetes|production|experience)\b/.test(combined)) {
    return 'Yes';
  }

  // Voluntary self-identification
  if (/\bgender\b/.test(combined)) return 'Male';
  if (/\b(hispanic|latino)\b/.test(combined)) return 'No';
  if (/\bveteran\b/.test(combined)) return 'No';
  if (/\bdisability\b/.test(combined)) return 'No';

  return null;
}

interface FillableField {
  selector: string;
  identifier: string;
  labelText: string;
  type: 'input' | 'textarea' | 'select' | 'file';
}

/**
 * Scan the page DOM for inputs we want to fill.
 */
async function collectFillableFields(
  page: Page,
  logs: string[],
): Promise<FillableField[]> {
  const fields: FillableField[] = [];

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

      let labelText = '';
      if (id) {
        const labelHandle = page.locator(`label[for="${id}"]`);
        if (await labelHandle.count() > 0) {
          labelText = (await labelHandle.first().textContent()) ?? '';
        }
      }
      labelText = labelText.replace(/\s+/g, ' ').replace(/\s*\*$/, '').trim();

      // Build a deterministic selector
      const selector = name
        ? `input[name="${name}"]`
        : id
          ? `#${id}`
          : `input[placeholder="${placeholder}"]`;

      fields.push({ selector, identifier, labelText, type: 'input' });
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

      let labelText = '';
      if (id) {
        const labelHandle = page.locator(`label[for="${id}"]`);
        if (await labelHandle.count() > 0) {
          labelText = (await labelHandle.first().textContent()) ?? '';
        }
      }
      labelText = labelText.replace(/\s+/g, ' ').replace(/\s*\*$/, '').trim();

      const selector = name
        ? `textarea[name="${name}"]`
        : id
          ? `#${id}`
          : 'textarea';

      fields.push({ selector, identifier, labelText, type: 'textarea' });
    } catch {
      // Skip inaccessible handles
    }
  }

  // Select elements (native HTML selects)
  const selectHandles = await page.locator('select').all();
  for (const handle of selectHandles) {
    try {
      const visible = await handle.isVisible();
      const enabled = await handle.isEnabled();
      if (!visible || !enabled) continue;

      const name = (await handle.getAttribute('name')) ?? '';
      const id = (await handle.getAttribute('id')) ?? '';
      const ariaLabel = (await handle.getAttribute('aria-label')) ?? '';
      const identifier = name || id || ariaLabel || 'select';

      let labelText = '';
      if (id) {
        const labelHandle = page.locator(`label[for="${id}"]`);
        if (await labelHandle.count() > 0) {
          labelText = (await labelHandle.first().textContent()) ?? '';
        }
      }
      labelText = labelText.replace(/\s+/g, ' ').replace(/\s*\*$/, '').trim();

      const selector = name
        ? `select[name="${name}"]`
        : id
          ? `#${id}`
          : 'select';

      fields.push({ selector, identifier, labelText, type: 'select' });
    } catch {
      // Skip inaccessible handles
    }
  }

  // File inputs (attaching resume & cover letter)
  const fileHandles = await page.locator('input[type="file"]').all();
  for (const handle of fileHandles) {
    try {
      const enabled = await handle.isEnabled();
      if (!enabled) continue;

      const name = (await handle.getAttribute('name')) ?? '';
      const id = (await handle.getAttribute('id')) ?? '';
      const ariaLabel = (await handle.getAttribute('aria-label')) ?? '';
      const identifier = name || id || ariaLabel || 'file';

      let labelText = '';
      if (id) {
        const labelHandle = page.locator(`label[for="${id}"]`);
        if (await labelHandle.count() > 0) {
          labelText = (await labelHandle.first().textContent()) ?? '';
        }
      }
      labelText = labelText.replace(/\s+/g, ' ').replace(/\s*\*$/, '').trim();

      const selector = name
        ? `input[name="${name}"]`
        : id
          ? `#${id}`
          : 'input[type="file"]';

      fields.push({ selector, identifier, labelText, type: 'file' });
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

  const isLive = command.mode === 'live';
  logs.push(log(`Starting worker for applicationId=${command.applicationId}`));
  logs.push(log(`Target URL: ${command.jobUrl}`));
  logs.push(log(`Mode: ${command.mode}`));

  ensureScreenshotsDir();
  const screenshotPath = path.join(SCREENSHOTS_DIR, `${command.applicationId}.png`);
  const resume = buildRealResumeData(command);

  // Write temporary files for upload fields
  const tempResumePath = path.resolve(process.cwd(), `resume_${command.applicationId}.txt`);
  const tempCoverLetterPath = path.resolve(process.cwd(), `cover_letter_${command.applicationId}.txt`);
  fs.writeFileSync(tempResumePath, command.resumeContent || '');
  fs.writeFileSync(tempCoverLetterPath, command.coverLetterContent || '');

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

    // ── Find submit button candidate ──────────────────
    let submitButtonSelector: string | null = null;
    const allButtons = await page.locator('button, input[type="submit"], a[role="button"]').all();
    const candidates: Array<{ selector: string; text: string; type: string; score: number }> = [];

    for (const btn of allButtons) {
      try {
        const text = (await btn.textContent()) ?? '';
        const typeAttr = (await btn.getAttribute('type')) ?? '';
        const trimmedText = text.trim();

        if (SUBMIT_PATTERN.test(trimmedText) || SUBMIT_PATTERN.test(typeAttr)) {
          if (!isLive) {
            logs.push(log(`SHADOW SKIP (submit-guard): button text="${trimmedText}" type="${typeAttr}"`));
          } else {
            logs.push(log(`LIVE SUBMIT CANDIDATE: button text="${trimmedText}" type="${typeAttr}"`));
            
            const id = await btn.getAttribute('id');
            const name = await btn.getAttribute('name');
            let selector = '';
            if (id) {
              selector = `#${id}`;
            } else if (name) {
              selector = `button[name="${name}"], input[name="${name}"]`;
            } else if (typeAttr === 'submit') {
              selector = 'button[type="submit"], input[type="submit"]';
            } else {
              selector = `button:has-text("${trimmedText}")`;
            }

            // Calculate score (higher is better)
            let score = 0;
            if (typeAttr === 'submit') {
              score += 100;
            }
            if (/submit/i.test(trimmedText)) {
              score += 50;
            }
            if (/apply/i.test(trimmedText)) {
              score += 10;
            }

            candidates.push({ selector, text: trimmedText, type: typeAttr, score });
          }
        }
      } catch {
        /* ignore */
      }
    }

    if (isLive && candidates.length > 0) {
      candidates.sort((a, b) => b.score - a.score);
      submitButtonSelector = candidates[0].selector;
      logs.push(log(`Selected best submit button: selector="${submitButtonSelector}" text="${candidates[0].text}" score=${candidates[0].score}`));
    }

    // ── Collect & fill fields ────────────────────────────────────────────
    logs.push(log('Discovering form fields…'));
    const fields = await collectFillableFields(page, logs);
    logs.push(log(`Found ${fields.length} fillable field(s)`));

    for (const field of fields) {
      let value = chooseValue(
        resume,
        field.identifier,
        field.labelText,
        field.type,
        tempResumePath,
        tempCoverLetterPath
      );

      // For textareas with no specific match, fall back to the cover letter text
      if (value === null && field.type === 'textarea') {
        value = resume.coverLetter;
      }

      if (value === null) {
        unsupportedFields.push(field.identifier);
        logs.push(log(`SKIP (no mapping): ${field.identifier} (label: "${field.labelText}")`));
        continue;
      }

      try {
        const locator = page.locator(field.selector).first();
        
        // File inputs might not be visible using .isVisible() but are still interactable
        if (field.type !== 'file') {
          const isVisible = await locator.isVisible();
          if (!isVisible) {
            unsupportedFields.push(field.identifier);
            logs.push(log(`SKIP (not visible): ${field.identifier}`));
            continue;
          }
        }

        if (field.type === 'file') {
          await locator.setInputFiles(value);
          fieldsFilled.push(field.identifier);
          logs.push(log(`UPLOADED FILE: ${field.identifier} = "${value}"`));
          continue;
        }

        if (field.type === 'select') {
          const options = await locator.locator('option').all();
          let matchedValue = value;
          for (const opt of options) {
            const text = await opt.textContent();
            const val = await opt.getAttribute('value');
            if (text && text.toLowerCase().includes(value.toLowerCase())) {
              matchedValue = val ?? text;
              break;
            }
          }
          await locator.selectOption(matchedValue);
          fieldsFilled.push(field.identifier);
          logs.push(log(`SELECTED OPTION: ${field.identifier} = "${matchedValue}"`));
          continue;
        }

        // Check if React-Select / Combobox
        const isCombobox = (await locator.getAttribute('role')) === 'combobox' ||
                           (await locator.getAttribute('aria-haspopup')) === 'true';

        if (isCombobox) {
          await locator.click();
          await locator.pressSequentially(value, { delay: 100 });
          await page.waitForTimeout(1000);
          await locator.press('Enter');
          fieldsFilled.push(field.identifier);
          logs.push(log(`FILLED COMBOBOX: ${field.identifier} = "${value}"`));
          continue;
        }

        // Standard text input / textarea
        await locator.fill(value);
        fieldsFilled.push(field.identifier);
        logs.push(log(`FILLED: ${field.identifier} = "${value.substring(0, 40)}…"`));
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        unsupportedFields.push(field.identifier);
        logs.push(log(`ERROR filling ${field.identifier}: ${msg}`));
      }
    }

    let finalStatus = isLive ? 'submitted' : 'shadow_completed';

    // ── Live submit if active mode ─────────────────────────────────────────
    if (isLive && submitButtonSelector) {
      logs.push(log(`LIVE MODE: Clicking submit button: ${submitButtonSelector}`));
      const submitBtn = page.locator(submitButtonSelector).first();
      await submitBtn.click();
      
      logs.push(log('Waiting for submission navigation…'));
      try {
        await page.waitForNavigation({ timeout: 15000 });
      } catch {
        logs.push(log('Timeout waiting for navigation, checking page content…'));
      }
      
      await page.waitForTimeout(3000);

      // Check if OTP / Security code is requested
      const pageText = await page.innerText('body');
      const isOtpRequired = 
        /security[\s_-]?code/i.test(pageText) || 
        /confirm[\s_-]?you[\s_-]?are[\s_-]?a[\s_-]?human/i.test(pageText) ||
        (await page.locator('input[id*="security"]').count()) > 0 ||
        (await page.locator('input[id*="verification"]').count()) > 0 ||
        (await page.locator('input[id*="otp"]').count()) > 0;

      if (isOtpRequired) {
        logs.push(log('OTP verification requested by the platform!'));
        
        // Take and save screenshot immediately so user sees the OTP prompt
        await page.screenshot({ path: screenshotPath, fullPage: true });
        logs.push(log(`Saved OTP prompt screenshot: ${screenshotPath}`));

        // 1. Notify backend by publishing a "verifying" status
        const interimResult: AutomationResult = {
          applicationId: command.applicationId,
          status: 'verifying',
          fieldsFilled,
          unsupportedFields,
          screenshotPath,
          logs: [...logs, log('Automation state: verifying (awaiting OTP entry)')],
        };
        await publishResult(interimResult);
        logs.push(log('Published verifying status to Redis.'));

        // 2. Poll Redis key for OTP code (up to 3 minutes)
        const otpKey = `cc:otp:${command.applicationId}`;
        const timeoutMs = 180000;
        const start = Date.now();
        let otpCode: string | null = null;
        
        logs.push(log(`Waiting up to 3 minutes for OTP code in Redis key: ${otpKey}`));
        while (Date.now() - start < timeoutMs) {
          otpCode = await redisClient.get(otpKey);
          if (otpCode) {
            logs.push(log(`Retrieved OTP code: ${otpCode}`));
            break;
          }
          await page.waitForTimeout(3000);
        }

        if (otpCode) {
          // 3. Find and fill OTP input boxes
          let inputs = await page.locator('input[type="text"], input[type="tel"], input:not([type="hidden"])').all();
          const visibleInputs: any[] = [];
          for (const input of inputs) {
            if (await input.isVisible()) {
              visibleInputs.push(input);
            }
          }
          logs.push(log(`Found ${visibleInputs.length} visible inputs for OTP entry.`));

          if (visibleInputs.length === 1) {
            await visibleInputs[0].fill(otpCode);
          } else if (visibleInputs.length > 1) {
            const digits = otpCode.split('');
            for (let i = 0; i < Math.min(visibleInputs.length, digits.length); i++) {
              await visibleInputs[i].fill(digits[i]);
              await visibleInputs[i].press('ArrowRight');
            }
          }

          // Click OTP submit button
          const otpSubmitBtn = page.locator('button[type="submit"], input[type="submit"], button:has-text("Submit")').first();
          if (await otpSubmitBtn.count() > 0) {
            logs.push(log('Clicking OTP submit button...'));
            await otpSubmitBtn.click();
          } else {
            logs.push(log('OTP submit button not found, pressing Enter...'));
            await page.keyboard.press('Enter');
          }

          // Wait for final navigation / submission to settle
          try {
            await page.waitForNavigation({ timeout: 15000 });
          } catch {
            logs.push(log('Timeout waiting for final submission navigation.'));
          }
          await page.waitForTimeout(3000);
          finalStatus = 'submitted';
        } else {
          logs.push(log('Timeout waiting for OTP code. Submission failed.'));
          finalStatus = 'failed';
        }
      }
    } else if (isLive) {
      logs.push(log('WARN: Live mode active, but no submit button was found.'));
    }

    // ── Screenshot ──────────────────────────────────────────────────────
    logs.push(log(`Taking full-page screenshot → ${screenshotPath}`));
    await page.screenshot({ path: screenshotPath, fullPage: true });
    logs.push(log(`Screenshot saved: ${screenshotPath}`));

    logs.push(log(`Worker complete. fieldsFilled=${fieldsFilled.length}, unsupported=${unsupportedFields.length}`));

    return {
      applicationId: command.applicationId,
      status: finalStatus,
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
    if (fs.existsSync(tempResumePath)) {
      try { fs.unlinkSync(tempResumePath); } catch { /* ignore */ }
    }
    if (fs.existsSync(tempCoverLetterPath)) {
      try { fs.unlinkSync(tempCoverLetterPath); } catch { /* ignore */ }
    }
    if (context) {
      try { await context.close(); } catch { /* ignore */ }
    }
    if (browser) {
      try { await browser.close(); } catch { /* ignore */ }
      logs.push(log('Browser closed.'));
    }
  }
}
