const rows = [
  {
    company: "Northstar Systems",
    role: "Senior Backend Engineer",
    score: 92,
    status: "submitted",
    reason: "All automated checks passed"
  },
  {
    company: "BrightLayer Health",
    role: "Platform Engineer",
    score: 88,
    status: "ready",
    reason: "Shadow run completed"
  },
  {
    company: "Atlas Grid",
    role: "Staff Java Engineer",
    score: 81,
    status: "skipped",
    reason: "Below threshold"
  },
  {
    company: "BlockedCo",
    role: "Backend Lead",
    score: 95,
    status: "blocked",
    reason: "Company blocklist"
  }
];

const applicationRows = document.querySelector("#applicationRows");
const threshold = document.querySelector("#threshold");
const thresholdValue = document.querySelector("#thresholdValue");
const killSwitch = document.querySelector("#killSwitch");
const automationState = document.querySelector("#automationState");
const shadowMode = document.querySelector("#shadowMode");

function renderRows() {
  applicationRows.innerHTML = rows
    .map((row) => `
      <tr>
        <td>${row.company}</td>
        <td class="role">${row.role}</td>
        <td class="score">${row.score}</td>
        <td><span class="status ${row.status}">${row.status}</span></td>
        <td>${row.reason}</td>
      </tr>
    `)
    .join("");
}

threshold.addEventListener("input", () => {
  thresholdValue.value = threshold.value;
});

killSwitch.addEventListener("click", () => {
  const paused = automationState.classList.toggle("paused");
  automationState.textContent = paused ? "Paused" : "Running";
  killSwitch.textContent = paused ? "Resume automation" : "Pause all automation";
});

shadowMode.addEventListener("click", () => {
  const pressed = shadowMode.getAttribute("aria-pressed") === "true";
  shadowMode.setAttribute("aria-pressed", String(!pressed));
});

renderRows();
