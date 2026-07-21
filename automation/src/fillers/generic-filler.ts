// Generic form filler — best-effort fill for any unrecognised ATS.
// Centralized, AI-driven form-filling brain.

import { Page, Frame } from 'playwright';
import { ResumeProfile, FillerResult } from './types';

const SUBMIT_PATTERN = /submit|apply|send application/i;

interface FillableField {
  selector: string;
  identifier: string;
  labelText: string;
  type: 'input' | 'textarea' | 'select' | 'file' | 'checkbox' | 'radio';
  options?: string[];
  required?: boolean;
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

  // Intercept with custom answers if populated
  if (resume.customAnswers) {
    if (resume.customAnswers[identifier] !== undefined) {
      return resume.customAnswers[identifier];
    }
    if (resume.customAnswers[labelText] !== undefined) {
      return resume.customAnswers[labelText];
    }
    const cleanLabel = labelText.replace(/\s+/g, ' ').replace(/\s*\*$/, '').trim();
    if (resume.customAnswers[cleanLabel] !== undefined) {
      return resume.customAnswers[cleanLabel];
    }
    // Case-insensitive clean match
    for (const [key, value] of Object.entries(resume.customAnswers)) {
      if (key.toLowerCase().trim() === cleanLabel.toLowerCase().trim()) {
        return value;
      }
    }
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

  // Fallback default prompts
  if (/\b(why.*join|interest.*in.*company)\b/.test(combined)) {
    return 'I am excited to apply because my background matches the requirements and I am looking for a challenging role.';
  }
  if (/\b(from where.*intend|where.*intend.*work|intend.*reside)\b/.test(combined)) {
    return resume.location;
  }
  if (/\b(ever worked.*before|former.*employee)\b/.test(combined)) {
    return 'No';
  }
  if (/\b(preferred.*first.*name|nickname)\b/.test(combined)) {
    return resume.firstName;
  }
  if (/\bpronouns\b/.test(combined)) {
    return 'He/Him';
  }
  if (/\bhow.*pronounce\b/.test(combined)) {
    return resume.firstName;
  }
  if (/\bwhere.*hear\b/.test(combined)) {
    return 'LinkedIn';
  }
  if (/\bundergraduate.*gpa\b/.test(combined)) {
    return '3.8';
  }

  // Location/country dropdowns
  if (/\bcountry\b/.test(combined))          return 'India';
  if (/\b(location|city)\b/.test(combined))  return resume.location;
  if (/\b(sponsorship|require.*visa)\b/.test(combined)) return 'Yes';
  if (/\bauthorized\b/.test(combined))       return 'Yes';

  // EEOC self-identification
  if (/\bgender\b/.test(combined))           return 'Male';
  if (/\b(hispanic|latino)\b/.test(combined)) return 'No';
  if (/\bveteran\b/.test(combined))          return 'No';
  if (/\bdisability\b/.test(combined))       return 'No';

  // Terms, consent, declarations, and privacy policy checkboxes
  if (/\b(privacy|policy|terms|condition|consent|agree|acknowledge|certify|declaration|statement|correct|accurate)\b/.test(combined)) {
    return 'Yes';
  }

  return null;
}

async function collectFillableFields(page: Page | Frame, logs: string[]): Promise<FillableField[]> {
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
      const required = (await handle.getAttribute('required')) !== null ||
                       (await handle.getAttribute('aria-required')) === 'true' ||
                       labelText.includes('*');
      labelText = labelText.replace(/\s+/g, ' ').replace(/\s*\*$/, '').trim();
      const selector = name ? `input[name="${name}"]` : id ? `#${id}` : `input[placeholder="${ph}"]`;
      fields.push({ selector, identifier, labelText, type: 'input', required });
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
      const required = (await handle.getAttribute('required')) !== null ||
                       (await handle.getAttribute('aria-required')) === 'true' ||
                       labelText.includes('*');
      labelText = labelText.replace(/\s+/g, ' ').replace(/\s*\*$/, '').trim();
      const selector = name ? `textarea[name="${name}"]` : id ? `#${id}` : 'textarea';
      fields.push({ selector, identifier, labelText, type: 'textarea', required });
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
      const required = (await handle.getAttribute('required')) !== null ||
                       (await handle.getAttribute('aria-required')) === 'true' ||
                       labelText.includes('*');
      labelText = labelText.replace(/\s+/g, ' ').replace(/\s*\*$/, '').trim();

      const optionTexts: string[] = [];
      const options = await handle.locator('option').all();
      for (const opt of options) {
        const txt = (await opt.textContent()) ?? '';
        if (txt.trim()) optionTexts.push(txt.trim());
      }

      const selector = name ? `select[name="${name}"]` : id ? `#${id}` : 'select';
      fields.push({ selector, identifier, labelText, type: 'select', options: optionTexts, required });
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
      const required = (await handle.getAttribute('required')) !== null ||
                       (await handle.getAttribute('aria-required')) === 'true' ||
                       labelText.includes('*');
      labelText = labelText.replace(/\s+/g, ' ').replace(/\s*\*$/, '').trim();
      const selector = name ? `input[name="${name}"]` : id ? `#${id}` : 'input[type="file"]';
      fields.push({ selector, identifier, labelText, type: 'file', required });
    } catch { /* skip */ }
  }

  // Checkbox inputs
  for (const handle of await page.locator('input[type="checkbox"]').all()) {
    try {
      if (!(await handle.isVisible()) || !(await handle.isEnabled())) continue;
      const name = (await handle.getAttribute('name')) ?? '';
      const id   = (await handle.getAttribute('id'))   ?? '';
      const valueAttr = (await handle.getAttribute('value')) ?? '';
      const identifier = name || id || 'checkbox';
      let labelText = '';
      if (id) {
        const lh = page.locator(`label[for="${id}"]`);
        if (await lh.count() > 0) labelText = (await lh.first().textContent()) ?? '';
      }
      const required = (await handle.getAttribute('required')) !== null ||
                       (await handle.getAttribute('aria-required')) === 'true' ||
                       labelText.includes('*');
      labelText = labelText.replace(/\s+/g, ' ').replace(/\s*\*$/, '').trim();
      const selector = id ? `#${id}` : name ? `input[name="${name}"][value="${valueAttr}"]` : 'input[type="checkbox"]';
      fields.push({ selector, identifier: id || name || 'checkbox', labelText: labelText || identifier, type: 'checkbox', required });
    } catch { /* skip */ }
  }

  // Radio inputs
  for (const handle of await page.locator('input[type="radio"]').all()) {
    try {
      if (!(await handle.isVisible()) || !(await handle.isEnabled())) continue;
      const name = (await handle.getAttribute('name')) ?? '';
      const id   = (await handle.getAttribute('id'))   ?? '';
      const valueAttr = (await handle.getAttribute('value')) ?? '';
      const identifier = name || id || 'radio';
      let labelText = '';
      if (id) {
        const lh = page.locator(`label[for="${id}"]`);
        if (await lh.count() > 0) labelText = (await lh.first().textContent()) ?? '';
      }
      const required = (await handle.getAttribute('required')) !== null ||
                       (await handle.getAttribute('aria-required')) === 'true' ||
                       labelText.includes('*');
      labelText = labelText.replace(/\s+/g, ' ').replace(/\s*\*$/, '').trim();
      const selector = id ? `#${id}` : name ? `input[name="${name}"][value="${valueAttr}"]` : 'input[type="radio"]';
      fields.push({ selector, identifier: id || name || 'radio', labelText: labelText || identifier, type: 'radio', required });
    } catch { /* skip */ }
  }

  logs.push(`[generic] Collected ${fields.length} fillable fields`);
  return fields;
}

async function getAnswersFromBrain(
  applicationId: string,
  fields: FillableField[],
  pageTitle: string,
  pageUrl: string,
): Promise<Record<string, string>> {
  const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080';
  const apiKey = process.env.API_KEY || 'dev-insecure-key';

  const body = fields.map(f => ({
    selector: f.selector,
    identifier: f.identifier,
    labelText: f.labelText,
    type: f.type,
    options: f.options || [],
    required: f.required || false
  }));

  try {
    const res = await fetch(`${backendUrl}/api/applications/${applicationId}/fill`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-API-Key': apiKey
      },
      body: JSON.stringify(body)
    });

    if (!res.ok) {
      throw new Error(`HTTP error: ${res.status}`);
    }

    const data = await res.json() as Record<string, string>;
    return data || {};
  } catch (err) {
    console.error('[brain] Failed to get answers from form-filling brain:', err);
    return {};
  }
}

export async function genericFill(
  page: Page | Frame,
  resume: ResumeProfile,
  pdfResumePath: string,
  coverLetterPath: string,
  logs: string[],
  isLive: boolean,
): Promise<FillerResult> {
  const fieldsFilled: string[] = [];
  const unsupportedFields: string[] = [];

  const fields = await collectFillableFields(page, logs);
  if (fields.length === 0) {
    return { fieldsFilled, unsupportedFields, submitted: false };
  }

  // Synchronously fetch mapped values from the AI Brain!
  const brainAnswers = resume.applicationId 
    ? await getAnswersFromBrain(resume.applicationId, fields, 'title' in page ? await (page as any).title() : 'Form Page', page.url())
    : {};

  for (const field of fields) {
    // Check if the brain resolved it, otherwise fallback to chooseValue
    let value: string | null = brainAnswers[field.identifier] || brainAnswers[field.labelText];
    if (value === undefined || value === null) {
      value = chooseValue(resume, field.identifier, field.labelText, field.type, pdfResumePath, coverLetterPath);
    }

    if (value === null && field.type === 'textarea') value = resume.coverLetter;
    if (value === null) {
      if (field.required) {
        unsupportedFields.push(`${field.identifier}::${field.labelText}`);
      }
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
        if (field.required) {
          unsupportedFields.push(`${field.identifier}::${field.labelText}`);
        }
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

      if (field.type === 'checkbox') {
        const isChecked = await locator.isChecked();
        const shouldCheck = value.toLowerCase() === 'yes' || value.toLowerCase() === 'true' || value.toLowerCase() === 'check';
        if (isChecked !== shouldCheck) {
          await locator.click();
        }
        fieldsFilled.push(field.identifier);
        logs.push(`[generic] CHECKBOX: ${field.identifier} = ${shouldCheck}`);
        continue;
      }

      if (field.type === 'radio') {
        const shouldSelect = value.toLowerCase() === 'yes' || value.toLowerCase() === 'true' || value.toLowerCase() === 'select';
        if (shouldSelect) {
          await locator.click();
        }
        fieldsFilled.push(field.identifier);
        logs.push(`[generic] RADIO: ${field.identifier} = ${shouldSelect}`);
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
      if (field.required) {
        unsupportedFields.push(`${field.identifier}::${field.labelText}`);
      }
      logs.push(`[generic] ERROR filling ${field.identifier}: ${msg}`);
    }
  }

  // Find next/continue button to support multi-page forms
  let submitted = false;
  let confirmationUrl: string | undefined;
  let errorBody: string | undefined;

  const allBtns = await page.locator('button, input[type="submit"], a[role="button"]').all();
  let nextBtn = null;
  for (const btn of allBtns) {
    try {
      const text = ((await btn.textContent()) ?? '').trim().toLowerCase();
      if (text.includes('next') || text.includes('continue') || text.includes('step') || text.includes('proceed')) {
        nextBtn = btn;
        break;
      }
    } catch {}
  }

  if (nextBtn) {
    logs.push('[generic] Clicking Next/Continue button to proceed to next page...');
    await nextBtn.click();
    await page.waitForTimeout(3000);
    const nextResult = await genericFill(page, resume, pdfResumePath, coverLetterPath, logs, isLive);
    fieldsFilled.push(...nextResult.fieldsFilled);
    unsupportedFields.push(...nextResult.unsupportedFields);
    submitted = nextResult.submitted;
    confirmationUrl = nextResult.confirmationUrl;
    errorBody = nextResult.errorBody;
  } else if (isLive) {
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
