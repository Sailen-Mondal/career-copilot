type AutomationMode = "shadow" | "live";

type AutomationCommand = {
  applicationId: string;
  jobUrl: string;
  mode: AutomationMode;
  profileSnapshotId: string;
  resumeDocumentId: string;
  coverLetterDocumentId?: string;
};

type AutomationResult = {
  applicationId: string;
  status: "shadow_completed" | "submitted" | "skipped" | "failed";
  fieldsFilled: string[];
  unsupportedFields: string[];
  screenshotPath?: string;
  platformResponse?: string;
};

export async function runShadowCommand(command: AutomationCommand): Promise<AutomationResult> {
  if (command.mode !== "shadow") {
    throw new Error("This starter worker only supports shadow mode.");
  }

  return {
    applicationId: command.applicationId,
    status: "shadow_completed",
    fieldsFilled: [],
    unsupportedFields: [],
    screenshotPath: undefined,
    platformResponse: undefined
  };
}
