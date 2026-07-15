// Generic form filler — best-effort fill for any unrecognised ATS.
// Extracted from the original shadow-worker field-scanning logic.

import { Page } from 'playwright';
import { ResumeProfile, FillerResult } from './types';

const SUBMIT_PATTERN = /submit|apply|send application/i;

interface FillableField {
  selector: string;
  identifier: string;
  labelText: string;
  type: 'input' | 'textarea' | 'select' | 'file';
}

export function chooseValue(
  resume: ResumeProfile,
  identifier: string,
  labelText: string,
  type: string,
  pdfResumePath: string,
  coverLetterPath: string,
): string | null {
  const combined = `${identifier} ${labelText}`.toLowerCase();

  if (type === 'file') {
    if (/\b(resume|cv)\b/.test(combined)) return pdfResumePath;
    if (/\bcover\b/.test(combined)) return coverLetterPath;
    return null;
  }

  if (/\bfirst[\s_-]?name\b/.test(combined)) return resume.firstName;
  if (/\blast[\s_-]?name\b/.test(combined))  return resume.lastName;
  if (/\b(full[\s_-]?name|your[\s_-]?name)\b/.test(combined)) return resume.fullName;
  if (/\bname\b/.test(combined))             return resume.fullName;
  if (/\bemail\b/.test(combined))            return resume.email;
  if (/\bphone|mobile|tel\b/.test(combined)) return resume.phone;
  if (/\baddress\b/.test(combined))          return resume.address;
  if (/\blinkedin\b/.test(combined))         return resume.linkedIn;
  if (/\b(website|portfolio|url|github)\b/.test(combined)) return resume.website;
  if (/\bcover[\s_-]?letter\b/.test(combined)) return resume.coverLetter;
  if (/\bsummary|objective|bio|about\b/.test(combined)) return resume.summary;

  // Location/country dropdowns
  if (/\bcountry\b/.test(combined))          return 'India';
  if (/\b(location|city)\b/.test(combined))  return resume.location;
  if (/\b(sponsorship|require.*visa)\b/.test(combined)) return 'Yes';
  if (/\bauthorized\b/.test(combined))       return 'No';

  // Tech yes/no questions
  if (/\b(java|spring|react|typescript|backend|frontend|cloud|aws|gcp|docker|kubernetes|production|experience)\b/.test(combined)) {
    return 'Yes';
  }

  // EEOC self-identification
  if (/\bgender\b/.test(combined))           return 'Male';
  if (/\b(hispanic|latino)\b/.test(combined)) return 'No';
  if (/\bveteran\b/.test(combined))          return 'No';
  if (/\bdisability\b/.test(combined))       return 'No';

  return null;
}

async function collectFillableFields(page: Page, logs: string[]): Promise<FillableField[]> {
  const fields: FillableField[] = [];

  // Text inputs
  const inputHandles = await page.locator(
    'input:not([type="hidden"]):not([type="submit"]):not([type="button"])' +
    ':not([type="file"]):not([type="checkbox"]):not([type="radio"])' +
    ':not([type="image"]):not([type="reset"])',
  ).all();

  for (const handle of inputHandles) {
    try {
      if (!(await handle.isVisible()) || !(await handle.isEnabled())) continue;
      const name = (await handle.getAttribute('name')) ?? '';
      const id   = (await handle.getAttribute('id'))   ?? '';
      const ph   = (await handle.getAttribute('placeholder')) ?? '';
      const aria = (await handle.getAttribute('aria-label'))  ?? '';
      const identifier = name || id || aria || ph || 'input';
      let labelText = '';
      if (id) {
        const lh = page.locator(`label[for="${id}"]`);
        if (await lh.count() > 0) labelText = (await lh.first().textContent()) ?? '';
      }
      labelText = labelText.replace(/\s+/g, ' ').replace(/\s*\*$/, '').trim();
      const selector = name ? `input[name="${name}"]` : id ? `#${id}` : `input[placeholder="${ph}"]`;
      fields.push({ selector, identifier, labelText, type: 'input' });
    } catch { /* skip */ }
  }

  // Textareas
  for (const handle of await page.locator('textarea').all()) {
    try {
      if (!(await handle.isVisible()) || !(await handle.isEnabled())) continue;
      const name = (await handle.getAttribute('name')) ?? '';
      const id   = (await handle.getAttribute('id'))   ?? '';
      const identifier = name || id || 'textarea';
      let labelText = '';
      if (id) {
        const lh = page.locator(`label[for="${id}"]`);
        if (await lh.count() > 0) labelText = (await lh.first().textContent()) ?? '';
      }
      labelText = labelText.replace(/\s+/g, ' ').replace(/\s*\*$/, '').trim();
      const selector = name ? `textarea[name="${name}"]` : id ? `#${id}` : 'textarea';
      fields.push({ selector, identifier, labelText, type: 'textarea' });
    } catch { /* skip */ }
  }

  // Select elements
  for (const handle of await page.locator('select').all()) {
    try {
      if (!(await handle.isVisible()) || !(await handle.isEnabled())) continue;
      const name = (await handle.getAttribute('name')) ?? '';
      const id   = (await handle.getAttribute('id'))   ?? '';
      const identifier = name || id || 'select';
      let labelText = '';
      if (id) {
        const lh = page.locator(`label[for="${id}"]`);
        if (await lh.count() > 0) labelText = (await lh.first().textContent()) ?? '';
      }
      const selector = name ? `select[name="${name}"]` : id ? `#${id}` : 'select';
      fields.push({ selector, identifier, labelText: labelText.trim(), type: 'select' });
    } catch { /* skip */ }
  }

  // File inputs
  for (const handle of await page.locator('input[type="file"]').all()) {
    try {
      if (!(await handle.isEnabled())) continue;
      const name = (await handle.getAttribute('name')) ?? '';
      const id   = (await handle.getAttribute('id'))   ?? '';
      const aria = (await handle.getAttribute('aria-label'))  ?? '';
      const identifier = name || id || aria || 'file';
      let labelText = '';
      if (id) {
        const lh = page.locator(`label[for="${id}"]`);
        if (await lh.count() > 0) labelText = (await lh.first().textContent()) ?? '';
      }
      const selector = name ? `input[name="${name}"]` : id ? `#${id}` : 'input[type="file"]';
      fields.push({ selector, identifier, labelText: labelText.trim(), type: 'file' });
    } catch { /* skip */ }
  }

  logs.push(`[generic] Collected ${fields.length} fillable fields`);
  return fields;
}

export async function genericFill(
  page: Page,
  resume: ResumeProfile,
  pdfResumePath: string,
  coverLetterPath: string,
  logs: string[],
  isLive: boolean,
): Promise<FillerResult> {
  const fieldsFilled: string[] = [];
  const unsupportedFields: string[] = [];

  const fields = await collectFillableFields(page, logs);

  for (const field of fields) {
    let value = chooseValue(resume, field.identifier, field.labelText, field.type, pdfResumePath, coverLetterPath);

    if (value === null && field.type === 'textarea') value = resume.coverLetter;
    if (value === null) {
      unsupportedFields.push(field.identifier);
      logs.push(`[generic] SKIP (no mapping): ${field.identifier}`);
      continue;
    }

    try {
      const locator = page.locator(field.selector).first();

      if (field.type === 'file') {
        await locator.setInputFiles(value);
        fieldsFilled.push(field.identifier);
        logs.push(`[generic] UPLOADED: ${field.identifier}`);
        continue;
      }

      if (!(await locator.isVisible())) {
        unsupportedFields.push(field.identifier);
        logs.push(`[generic] SKIP (hidden): ${field.identifier}`);
        continue;
      }

      if (field.type === 'select') {
        const options = await locator.locator('option').all();
        let matchedValue = value;
        for (const opt of options) {
          const text = await opt.textContent();
          const val  = await opt.getAttribute('value');
          if (text && text.toLowerCase().includes(value.toLowerCase())) {
            matchedValue = val ?? text;
            break;
          }
        }
        await locator.selectOption(matchedValue);
        fieldsFilled.push(field.identifier);
        logs.push(`[generic] SELECTED: ${field.identifier} = "${matchedValue}"`);
        continue;
      }

      const isCombobox = (await locator.getAttribute('role')) === 'combobox' ||
                         (await locator.getAttribute('aria-haspopup')) === 'true';
      if (isCombobox) {
        await locator.click();
        await locator.pressSequentially(value, { delay: 80 });
        await page.waitForTimeout(800);
        await locator.press('Enter');
        fieldsFilled.push(field.identifier);
        logs.push(`[generic] COMBOBOX: ${field.identifier}`);
        continue;
      }

      await locator.fill(value);
      fieldsFilled.push(field.identifier);
      logs.push(`[generic] FILLED: ${field.identifier}`);
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      unsupportedFields.push(field.identifier);
      logs.push(`[generic] ERROR filling ${field.identifier}: ${msg}`);
    }
  }

  // Find submit button
  let submitted = false;
  let confirmationUrl: string | undefined;
  let errorBody: string | undefined;

  if (isLive) {
    const allBtns = await page.locator('button, input[type="submit"], a[role="button"]').all();
    for (const btn of allBtns) {
      try {
        const text = ((await btn.textContent()) ?? '').trim();
        if (SUBMIT_PATTERN.test(text) || (await btn.getAttribute('type')) === 'submit') {
          logs.push(`[generic] Clicking submit: "${text}"`);
          await btn.click();
          await page.waitForTimeout(3000);
          confirmationUrl = page.url();
          submitted = true;
          break;
        }
      } catch { /* skip */ }
    }
    if (!submitted) {
      logs.push('[generic] WARN: No submit button found');
      errorBody = 'Submit button not found on page';
    }
  }

  return { fieldsFilled, unsupportedFields, submitted, confirmationUrl, errorBody };
}
