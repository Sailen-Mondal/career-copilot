// Lever Form Filler
// Handles jobs.lever.co/… job pages.
// Lever shows a description page first — we must click Apply to reach the form.

import { Page } from 'playwright';
import { ResumeProfile, FillerResult } from './types';

const TIMEOUT = 20_000;
const FIELD_DELAY = 70;

export async function leverFill(
  page: Page,
  resume: ResumeProfile,
  pdfResumePath: string,
  coverLetterPath: string,
  logs: string[],
  isLive: boolean,
): Promise<FillerResult> {
  const fieldsFilled: string[] = [];
  const unsupportedFields: string[] = [];

  logs.push('[lever] Starting Lever filler');

  // ── Step 1: Navigate to the apply form ───────────────────────────────────
  // Lever shows a job description; we click the Apply button
  const applyBtn = page.locator(
    'a:has-text("Apply for this job"), a:has-text("Apply Now"), a[href*="/apply"], button:has-text("Apply")'
  ).first();

  if (await applyBtn.count() > 0) {
    const href = await applyBtn.getAttribute('href');
    if (href && href.startsWith('http')) {
      logs.push(`[lever] Navigating to apply URL: ${href}`);
      await page.goto(href, { waitUntil: 'domcontentloaded', timeout: TIMEOUT });
    } else {
      logs.push('[lever] Clicking Apply button');
      await applyBtn.click();
      await page.waitForTimeout(2000);
    }
  } else {
    // Check if we're already on the apply page
    const alreadyApply = (await page.locator('form, input[type="text"]').count()) > 0;
    if (!alreadyApply) {
      logs.push('[lever] WARN: No Apply button found, attempting to fill current page');
    }
  }

  await page.waitForTimeout(1500);

  // ── Step 2: Fill personal info fields ────────────────────────────────────
  const fieldMap: Record<string, string> = {
    'name':            resume.fullName,
    'fullName':        resume.fullName,
    'email':           resume.email,
    'phone':           resume.phone,
    'org':             'Self-employed',
    'currentCompany':  'Self-employed',
    'linkedin':        resume.linkedIn,
    'twitter':         '',
    'github':          resume.website,
    'portfolio':       resume.website,
    'website':         resume.website,
    'location':        resume.location,
    'city':            resume.location,
    'summary':         resume.summary,
    'coverLetter':     resume.coverLetter,
  };

  // Generic text input scan using field name hints
  const inputs = await page.locator('input[type="text"], input[type="email"], input[type="tel"]').all();
  for (const input of inputs) {
    try {
      if (!(await input.isVisible())) continue;
      const name = (await input.getAttribute('name')) ?? '';
      const id   = (await input.getAttribute('id'))   ?? '';
      const ph   = (await input.getAttribute('placeholder')) ?? '';
      const key  = name || id || ph;
      const lowerKey = key.toLowerCase();

      let value: string | undefined;
      for (const [k, v] of Object.entries(fieldMap)) {
        if (lowerKey.includes(k.toLowerCase()) && v) { value = v; break; }
      }

      if (value) {
        await input.fill('');
        await input.pressSequentially(value, { delay: FIELD_DELAY });
        fieldsFilled.push(key);
        logs.push(`[lever] filled: ${key}`);
      } else {
        unsupportedFields.push(key);
      }
    } catch (e) {
      logs.push(`[lever] error on input: ${e}`);
    }
  }

  // Textareas (cover letter / additional info)
  for (const ta of await page.locator('textarea').all()) {
    try {
      if (!(await ta.isVisible())) continue;
      const name = (await ta.getAttribute('name')) ?? '';
      const value = resume.coverLetter;
      await ta.fill(value);
      fieldsFilled.push(name || 'textarea');
      logs.push(`[lever] filled textarea: ${name}`);
    } catch (e) {
      logs.push(`[lever] error on textarea: ${e}`);
    }
  }

  // ── Step 3: File upload (resume) ──────────────────────────────────────────
  const fileInputs = await page.locator('input[type="file"]').all();
  for (const fi of fileInputs) {
    try {
      const name = ((await fi.getAttribute('name')) ?? '').toLowerCase();
      const label = await page.locator(`label[for="${await fi.getAttribute('id')}"]`).first().textContent().catch(() => '');
      const combined = `${name} ${label}`.toLowerCase();
      if (/resume|cv/.test(combined) || fileInputs.length === 1) {
        await fi.setInputFiles(pdfResumePath);
        fieldsFilled.push('resume_upload');
        logs.push('[lever] uploaded resume PDF');
      }
    } catch (e) {
      logs.push(`[lever] file upload error: ${e}`);
    }
  }

  // ── Step 4: "How did you hear about us?" select ───────────────────────────
  for (const sel of await page.locator('select').all()) {
    try {
      if (!(await sel.isVisible())) continue;
      // Default to first non-empty option
      const options = await sel.locator('option').all();
      for (const opt of options) {
        const val = await opt.getAttribute('value');
        if (val && val !== '') {
          await sel.selectOption(val);
          fieldsFilled.push('select');
          break;
        }
      }
    } catch { /* skip */ }
  }

  // ── Step 5: Submit ─────────────────────────────────────────────────────────
  let submitted = false;
  let confirmationUrl: string | undefined;
  let errorBody: string | undefined;

  if (isLive) {
    const submitBtn = page.locator(
      'button[type="submit"], input[type="submit"], button:has-text("Submit Application"), button:has-text("Apply")'
    ).first();

    if (await submitBtn.count() > 0) {
      const btnText = ((await submitBtn.textContent()) ?? '').trim();
      logs.push(`[lever] Clicking submit: "${btnText}"`);
      await submitBtn.click();
      await page.waitForTimeout(3000);

      const url = page.url();
      const bodyText = await page.innerText('body').catch(() => '');

      if (
        url.includes('/thanks') ||
        url.includes('/confirmation') ||
        /thank you|application submitted|we.ve received/i.test(bodyText)
      ) {
        submitted = true;
        confirmationUrl = url;
        logs.push(`[lever] ✅ Submitted successfully: ${url}`);
      } else {
        errorBody = bodyText.substring(0, 1000);
        logs.push('[lever] WARN: Submit clicked but no confirmation detected');
      }
    } else {
      logs.push('[lever] WARN: Submit button not found');
      errorBody = 'Submit button not found';
    }
  }

  return { fieldsFilled, unsupportedFields, submitted, confirmationUrl, errorBody };
}
