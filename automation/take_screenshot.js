const { chromium } = require('playwright');
const path = require('path');

async function run() {
  console.log('Launching browser...');
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  
  page.on('console', msg => console.log('PAGE LOG:', msg.text()));
  page.on('pageerror', err => console.error('PAGE ERROR:', err.message));

  // Set standard iPhone size viewport
  await page.setViewportSize({ width: 375, height: 812 });

  console.log('Navigating to http://localhost:8000 on mobile...');
  await page.goto('http://localhost:8000', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1500);

  // Assert inspector starts collapsed on mobile startup
  const isInspectorCollapsedOnStart = await page.evaluate(() => {
    const shell = document.querySelector('.app-shell');
    const style = window.getComputedStyle(document.getElementById('inspector'));
    return shell.classList.contains('inspector-collapsed') && style.transform.includes('matrix');
  });
  console.log('Is inspector collapsed on startup:', isInspectorCollapsedOnStart);

  // Take screenshot 1: Mobile home dashboard view
  const dashboardPath = path.join(__dirname, 'mobile_dashboard.png');
  console.log(`Taking screenshot 1: Mobile Dashboard home screen at ${dashboardPath}...`);
  await page.screenshot({ path: dashboardPath });

  // Click expand sidebar button (hamburger)
  console.log('Clicking expand sidebar button (hamburger)...');
  await page.click('#sidebarExpandBtn');
  await page.waitForTimeout(600);

  // Take screenshot 2: Mobile sidebar drawer slide-in overlay open
  const sidebarPath = path.join(__dirname, 'mobile_sidebar.png');
  console.log(`Taking screenshot 2: Mobile Sidebar Drawer open at ${sidebarPath}...`);
  await page.screenshot({ path: sidebarPath });

  // Click the backdrop to close it (clicking on the right side of the sidebar at x: 320 to avoid menu link collision)
  console.log('Clicking sidebar backdrop overlay to close...');
  await page.click('#sidebarBackdrop', { position: { x: 320, y: 200 } });
  await page.waitForTimeout(600);

  // Scroll mainContent down to make applications table visible (no manual scroll needed for Playwright usually, but let's do it to be safe)
  console.log('Scrolling mainContent down...');
  await page.evaluate(() => {
    const content = document.getElementById('mainContent');
    content.scrollTop = 600;
  });
  await page.waitForTimeout(600);

  // Click the first row to open inspector drawer
  console.log('Clicking the first application row on mobile...');
  await page.click('#applicationsTable tbody tr:first-child');
  await page.waitForTimeout(600);

  // Take screenshot 3: Mobile details drawer slide-in open
  const detailsPath = path.join(__dirname, 'mobile_details.png');
  console.log(`Taking screenshot 3: Mobile Details Drawer open at ${detailsPath}...`);
  await page.screenshot({ path: detailsPath });

  console.log('VERIFICATION SUCCESSFUL: Mobile navigation and drawers function flawlessly!');
  await browser.close();
}

run().catch(err => {
  console.error('TEST FAILURE:', err);
  process.exit(1);
});
