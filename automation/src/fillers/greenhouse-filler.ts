// Greenhouse V2 Form Filler
// Handles the modern Greenhouse Apply SPA at boards.greenhouse.io
// Supports multi-step forms, file uploads, custom questions, EEOC dropdowns.

import { Page } from 'playwright';
import { ResumeProfile, FillerResult } from './types';
import { chooseValue } from './generic-filler';

const STEP_TIMEOUT = 15_000;
const FIELD_DELAY = 60; // ms between keystrokes (light human simulation)

/** Wait for the Greenhouse form to be interactive */
async function waitForForm(page: Page): Promise<boolean> {
  try {
    await page.waitForSelector(
      '#application_form, .application-form, form[data-application-form]',
      { timeout: STEP_TIMEOUT },
    );
    return true;
  } catch {
    // Maybe this is an "Apply" button page that opens the form
    const applyBtn = page.locator('a:has-text("Apply"), button:has-text("Apply for this job"), .btn-apply').first();
    if (await applyBtn.count() > 0) {
      await applyBtn.click();
      await page.waitForTimeout(2000);
      return page.locator('#application_form, .application-form').count().then(c => c > 0);
    }
    return false;
  }
}

/** Fill one Greenhouse step and click the Next/Continue button */
async function fillStep(
  page: Page,
  resume: ResumeProfile,
  pdfResumePath: string,
  coverLetterPath: string,
  logs: string[],
  fieldsFilled: string[],
  unsupportedFields: string[],
): Promise<boolean> {

  // ── Text & email inputs ──────────────────────────────────────────────────
  const inputs = await page.locator(
    'input[type="text"], input[type="email"], input[type="tel"], input[type="url"], input[type="number"]'
  ).all();

  for (const input of inputs) {
    try {
      if (!(await input.isVisible()) || !(await input.isEnabled())) continue;
      const name = (await input.getAttribute('name')) ?? '';
      const id   = (await input.getAttribute('id'))   ?? '';
      const label = id
        ? ((await page.locator(`label[for="${id}"]`).first().textContent().catch(() => '')) ?? '').trim()
        : '';
      const value = chooseValue(resume, name || id, label, 'input', pdfResumePath, coverLetterPath);
      if (!value) { unsupportedFields.push(name || id || 'input'); continue; }

      await input.fill('');
      await input.pressSequentially(value, { delay: FIELD_DELAY });
      fieldsFilled.push(name || id || 'input');
      logs.push(`[greenhouse] filled: ${name || id} = "${value.substring(0, 40)}"`);
    } catch (e) {
      logs.push(`[greenhouse] error on input: ${e}`);
    }
  }

  // ── Textareas ────────────────────────────────────────────────────────────
  for (const ta of await page.locator('textarea').all()) {
    try {
      if (!(await ta.isVisible())) continue;
      const name  = (await ta.getAttribute('name')) ?? '';
      const id    = (await ta.getAttribute('id'))   ?? '';
      const label = id
        ? ((await page.locator(`label[for="${id}"]`).first().textContent().catch(() => '')) ?? '').trim()
        : '';
      let value = chooseValue(resume, name || id, label, 'textarea', pdfResumePath, coverLetterPath)
        ?? resume.coverLetter;
      await ta.fill(value);
      fieldsFilled.push(name || id || 'textarea');
      logs.push(`[greenhouse] filled textarea: ${name || id}`);
    } catch (e) {
      logs.push(`[greenhouse] error on textarea: ${e}`);
    }
  }

  // ── Native selects ───────────────────────────────────────────────────────
  for (const sel of await page.locator('select').all()) {
    try {
      if (!(await sel.isVisible())) continue;
      const name  = (await sel.getAttribute('name')) ?? '';
      const id    = (await sel.getAttribute('id'))   ?? '';
      const label = id
        ? ((await page.locator(`label[for="${id}"]`).first().textContent().catch(() => '')) ?? '').trim()
        : '';
      const desired = chooseValue(resume, name || id, label, 'select', pdfResumePath, coverLetterPath);
      if (!desired) { unsupportedFields.push(name || id || 'select'); continue; }

      // Find closest matching option
      const options = await sel.locator('option').all();
      let matched = desired;
      for (const opt of options) {
        const text = await opt.textContent();
        const val  = await opt.getAttribute('value');
        if (text && text.toLowerCase().includes(desired.toLowerCase())) {
          matched = val ?? text;
          break;
        }
      }
      await sel.selectOption(matched).catch(() => sel.selectOption({ index: 1 }));
      fieldsFilled.push(name || id || 'select');
      logs.push(`[greenhouse] selected: ${name || id} = "${matched}"`);
    } catch (e) {
      logs.push(`[greenhouse] error on select: ${e}`);
    }
  }

  // ── File uploads ─────────────────────────────────────────────────────────
  for (const fi of await page.locator('input[type="file"]').all()) {
    try {
      const name  = (await fi.getAttribute('name')) ?? '';
      const id    = (await fi.getAttribute('id'))   ?? '';
      const label = id
        ? ((await page.locator(`label[for="${id}"]`).first().textContent().catch(() => '')) ?? '').trim()
        : '';
      const combined = `${name} ${id} ${label}`.toLowerCase();

      if (/\b(resume|cv)\b/.test(combined)) {
        await fi.setInputFiles(pdfResumePath);
        fieldsFilled.push('resume_upload');
        logs.push('[greenhouse] uploaded resume PDF');
      } else if (/\bcover\b/.test(combined)) {
        await fi.setInputFiles(coverLetterPath);
        fieldsFilled.push('cover_letter_upload');
        logs.push('[greenhouse] uploaded cover letter');
      }
    } catch (e) {
      logs.push(`[greenhouse] error on file upload: ${e}`);
    }
  }

  // ── Radio / checkbox groups (Yes/No questions) ───────────────────────────
  const radioGroups = await page.locator('fieldset, .field--radio, .radio-group').all();
  for (const group of radioGroups) {
    try {
      const legend = ((await group.locator('legend, .label').first().textContent().catch(() => '')) ?? '').toLowerCase();
      // For yes/no tech questions, always pick "Yes"
      if (/\b(experience|worked|familiar|proficient|years|authorized)\b/.test(legend)) {
        const yesOpt = group.locator('input[type="radio"][value="yes"], input[type="radio"][value="1"], label:has-text("Yes") input').first();
        if (await yesOpt.count() > 0) {
          await yesOpt.check().catch(() => {});
          fieldsFilled.push('radio-yes');
        }
      }
      // Sponsorship / authorized: answer "No" for "do you require sponsorship"
      if (/\b(sponsor|visa)\b/.test(legend)) {
        const noOpt = group.locator('input[type="radio"][value="no"], input[type="radio"][value="0"], label:has-text("No") input').first();
        if (await noOpt.count() > 0) {
          await noOpt.check().catch(() => {});
          fieldsFilled.push('radio-no');
        }
      }
    } catch { /* skip */ }
  }

  await page.waitForTimeout(500);

  // ── Click Next/Continue/Submit button ────────────────────────────────────
  const nextBtn = page.locator(
    'button[data-qa="btn-submit"], button:has-text("Next"), button:has-text("Continue"), ' +
    'button:has-text("Submit Application"), input[type="submit"]'
  ).first();

  if (await nextBtn.count() > 0) {
    const btnText = ((await nextBtn.textContent()) ?? '').trim();
    logs.push(`[greenhouse] clicking: "${btnText}"`);
    await nextBtn.click();
    await page.waitForTimeout(2000);
    return true;
  }

  logs.push('[greenhouse] WARN: no Next/Submit button found on this step');
  return false;
}

export async function greenhouseFill(
  page: Page,
  resume: ResumeProfile,
  pdfResumePath: string,
  coverLetterPath: string,
  logs: string[],
  isLive: boolean,
): Promise<FillerResult> {
  const fieldsFilled: string[] = [];
  const unsupportedFields: string[] = [];

  logs.push('[greenhouse] Starting Greenhouse V2 filler');

  const formFound = await waitForForm(page);
  if (!formFound) {
    logs.push('[greenhouse] Form not found — falling back to generic filler');
    // Import lazily to avoid circular dependency
    const { genericFill } = await import('./generic-filler');
    return genericFill(page, resume, pdfResumePath, coverLetterPath, logs, isLive);
  }

  let maxSteps = 6; // guard against infinite loops
  let confirmationUrl: string | undefined;
  let submitted = false;

  while (maxSteps-- > 0) {
    await fillStep(page, resume, pdfResumePath, coverLetterPath, logs, fieldsFilled, unsupportedFields);
    await page.waitForTimeout(1500);

    const url = page.url();
    const bodyText = await page.innerText('body').catch(() => '');

    // Detect success
    if (
      url.includes('/confirmation') ||
      url.includes('/thank') ||
      /your application has been (received|submitted)|thank you for applying/i.test(bodyText)
    ) {
      confirmationUrl = url;
      submitted = isLive;
      logs.push(`[greenhouse] ✅ Application confirmed at ${url}`);
      break;
    }

    // Detect if still on a form step
    const stillHasForm = (await page.locator(
      '#application_form, .application-form, button:has-text("Next"), button:has-text("Continue")'
    ).count()) > 0;

    if (!stillHasForm) {
      logs.push('[greenhouse] No more steps detected');
      break;
    }
  }

  let errorBody: string | undefined;
  if (!submitted && isLive) {
    errorBody = (await page.innerText('body').catch(() => '')).substring(0, 1000);
    logs.push('[greenhouse] WARN: Submitted flag not set — possible failure');
  }

  return { fieldsFilled, unsupportedFields, submitted, confirmationUrl, errorBody };
}
