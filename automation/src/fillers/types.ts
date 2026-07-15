// Shared types for all form fillers

export interface ResumeProfile {
  name: string;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string;
  phone: string;
  address: string;
  location: string;  // city-level, e.g. "Kolkata, West Bengal, India"
  linkedIn: string;
  website: string;
  coverLetter: string;
  summary: string;
}

export interface FillerResult {
  fieldsFilled: string[];
  unsupportedFields: string[];
  submitted: boolean;
  confirmationUrl?: string;
  errorBody?: string;
}
