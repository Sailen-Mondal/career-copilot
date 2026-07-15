// Ashby Form Filler
// Handles jobs.ashbyhq.com/… — clean React SPA form with predictable structure.

import { Page } from 'playwright';
import { ResumeProfile, FillerResult } from './types';

const FIELD_DELAY = 60;
const TIMEOUT = 15_000;

export async function ashbyFill(
  page: Page,
  resume: ResumeProfile,
  pdfResumePath: string,
  coverLetterPath: string,
  logs: string[],
  isLive: boolean,
): Promise<FillerResult> {
  const fieldsFilled: string[] = [];
  const unsupportedFields: string[] = [];

  logs.push('[ashby] Starting Ashby filler');

  // Wait for the application form to mount
  try {
    await page.waitForSelector('form, [data-ashby-form], .ashby-application-form', { timeout: TIMEOUT });
  } catch {
    logs.push('[ashby] Form not found, using generic filler');
    const { genericFill } = await import('./generic-filler');
    return genericFill(page, resume, pdfResumePath, coverLetterPath, logs, isLive);
  }

  // Ashby typically has predictable input placeholders and labels
  const fieldMapping: Array<{ selector: string; value: string; label: string }> = [
    { selector: 'input[name*="firstName"], input[placeholder*="First"], input[placeholder*="first"]', value: resume.firstName, label: 'firstName' },
    { selector: 'input[name*="lastName"], input[placeholder*="Last"], input[placeholder*="last"]', value: resume.lastName, label: 'lastName' },
    { selector: 'input[name*="email"], input[type="email"], input[placeholder*="email" i]', value: resume.email, label: 'email' },
    { selector: 'input[name*="phone"], input[type="tel"], input[placeholder*="phone" i]', value: resume.phone, label: 'phone' },
    { selector: 'input[name*="linkedin"], input[placeholder*="linkedin" i]', value: resume.linkedIn, label: 'linkedin' },
    { selector: 'input[name*="website"], input[name*="portfolio"], input[placeholder*="website" i]', value: resume.website, label: 'website' },
    { selector: 'input[name*="location"], input[placeholder*="location" i], input[placeholder*="city" i]', value: resume.location, label: 'location' },
  ];

  for (const { selector, value, label } of fieldMapping) {
    if (!value) continue;
    try {
      const el = page.locator(selector).first();
      if ((await el.count()) > 0 && (await el.isVisible())) {
        await el.fill('');
        await el.pressSequentially(value, { delay: FIELD_DELAY });
        fieldsFilled.push(label);
        logs.push(`[ashby] filled: ${label}`);
      }
    } catch (e) {
      logs.push(`[ashby] error on ${label}: ${e}`);
    }
  }

  // Textarea — cover letter / additional info
  for (const ta of await page.locator('textarea').all()) {
    try {
      if (!(await ta.isVisible())) continue;
      const name = (await ta.getAttribute('name')) ?? '';
      await ta.fill(resume.coverLetter);
      fieldsFilled.push(name || 'textarea');
      logs.push(`[ashby] filled textarea: ${name}`);
    } catch { /* skip */ }
  }

  // File upload — resume
  const fileInputs = await page.locator('input[type="file"]').all();
  for (const fi of fileInputs) {
    try {
      const label = await page.locator(`label[for="${await fi.getAttribute('id')}"]`).first().textContent().catch(() => '');
      const combined = `${await fi.getAttribute('name')} ${label}`.toLowerCase();
      if (/resume|cv/.test(combined) || fileInputs.length <= 2) {
        await fi.setInputFiles(pdfResumePath);
        fieldsFilled.push('resume_upload');
        logs.push('[ashby] uploaded resume PDF');
        break; // usually only one resume upload
      }
    } catch (e) {
      logs.push(`[ashby] file upload error: ${e}`);
    }
  }

  // Cover letter file upload (if separate from text area)
  for (const fi of fileInputs.slice(1)) {
    try {
      const label = await page.locator(`label[for="${await fi.getAttribute('id')}"]`).first().textContent().catch(() => '');
      if (/cover/i.test(label)) {
        await fi.setInputFiles(coverLetterPath);
        fieldsFilled.push('cover_letter_upload');
        logs.push('[ashby] uploaded cover letter');
      }
    } catch { /* skip */ }
  }

  // Custom questions — fill text inputs with sensible defaults
  const remainingInputs = await page.locator(
    'input[type="text"]:not([name*="firstName"]):not([name*="lastName"]):not([name*="email"]):not([name*="phone"])'
  ).all();
  for (const input of remainingInputs) {
    try {
      if (!(await input.isVisible()) || !(await input.isEnabled())) continue;
      const val = await input.inputValue();
      if (val) continue; // already filled
      const name = (await input.getAttribute('name')) ?? '';
      const label = ((await page.locator(`label[for="${await input.getAttribute('id')}"]`).first().textContent().catch(() => '')) ?? '').toLowerCase();
      if (/year|experience|salary/i.test(label + name)) {
        await input.fill('3');
      } else {
        await input.fill('Yes');
      }
      fieldsFilled.push(name || 'custom_input');
    } catch { /* skip */ }
  }

  // Dropdowns
  for (const sel of await page.locator('select').all()) {
    try {
      if (!(await sel.isVisible())) continue;
      const options = await sel.locator('option').all();
      for (const opt of options) {
        const val = await opt.getAttribute('value');
        if (val && val !== '' && val !== 'placeholder') {
          await sel.selectOption(val);
          fieldsFilled.push('select');
          break;
        }
      }
    } catch { /* skip */ }
  }

  // Submit
  let submitted = false;
  let confirmationUrl: string | undefined;
  let errorBody: string | undefined;

  if (isLive) {
    const submitBtn = page.locator(
      'button[type="submit"], button:has-text("Submit Application"), button:has-text("Submit"), button:has-text("Apply")'
    ).first();

    if (await submitBtn.count() > 0) {
      logs.push('[ashby] Clicking Submit Application');
      await submitBtn.click();
      await page.waitForTimeout(3000);

      const bodyText = await page.innerText('body').catch(() => '');
      const url = page.url();

      if (/application submitted|thank you|we.ve received|success/i.test(bodyText) || url.includes('thank')) {
        submitted = true;
        confirmationUrl = url;
        logs.push(`[ashby] ✅ Submitted: ${url}`);
      } else {
        errorBody = bodyText.substring(0, 1000);
        logs.push('[ashby] WARN: No confirmation detected after submit');
      }
    } else {
      logs.push('[ashby] WARN: Submit button not found');
      errorBody = 'Submit button not found';
    }
  }

  return { fieldsFilled, unsupportedFields, submitted, confirmationUrl, errorBody };
}
