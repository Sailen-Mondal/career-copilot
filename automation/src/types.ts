/**
 * Shared TypeScript types matching the Java AutomationCommand and AutomationResult records.
 */

export type AutomationMode = 'shadow' | 'live';

export interface AutomationCommand {
  applicationId: string;
  jobUrl: string;
  mode: AutomationMode;
  profileSnapshotId: string;
  resumeDocumentId: string;
  resumeContent: string;
  coverLetterDocumentId?: string;
  coverLetterContent?: string;
  candidateName?: string;
  candidateEmail?: string;
  candidatePhone?: string;
  candidateLinkedin?: string;
  candidateWebsite?: string;
  customAnswersJson?: string;
}

export interface AutomationResult {
  applicationId: string;
  status: 'shadow_completed' | 'submitted' | 'skipped' | 'failed';
  fieldsFilled: string[];
  unsupportedFields: string[];
  screenshotPath?: string;
  platformResponse?: string;
  logs: string[];
}
