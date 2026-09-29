// capture-components.mjs — component-level golden screenshots (robust)
import { setup, login, shot, shotEl, BASE } from './lib.mjs';

const { browser, page } = await setup();
const out = [];
const log = (m) => { console.log(m); out.push(m); };

await login(page);
await page.goto(BASE + '/#/index', { waitUntil: 'networkidle', timeout: 40000 });
await page.waitForTimeout(2500);

// 1. left sidebar / calendar / composer (collapsed + expanded)
log('leftbox=' + await shotEl(page, '.left-box', 'component-sidebar-leftbox-1440x900.png'));
log('calendar=' + await shotEl(page, '.calendar-box-content', 'component-calendar-card-1440x900.png'));
log('composer=' + await shotEl(page, '.t-b-input-box', 'component-composer-1440x900.png'));
await page.locator('.t-b-input-box input').first().click();
await page.waitForTimeout(1200);
log('composer-expanded cfg=' + await page.locator('.t-b-i-box-config:visible').count());
await shotEl(page, '.t-b-input-box', 'component-composer-expanded-1440x900.png');
await page.mouse.click(3, 300);
await page.waitForTimeout(800);

async function closeDrawer() {
  if (await page.locator('.dialog-right-box:visible').count()) {
    await page.locator('.drb-footer .el-icon').first().click({ force: true }).catch(() => {});
    await page.waitForTimeout(600);
  }
}
async function openDrawer() {
  await closeDrawer();
  await page.locator('.task-card .truncate').first().click();
  await page.waitForTimeout(1600);
  return await page.locator('.dialog-right-box:visible').count();
}

const POP = '.el-popper:visible, .el-dropdown__popper:visible, .el-select-dropdown:visible';

async function drawerDropdownByIndex(i, label, file) {
  const open = await openDrawer();
  const dd = page.locator('.dialog-right-box .el-dropdown').nth(i);
  const n = await dd.count();
  if (n) await dd.locator('.flex.items-center').first().dispatchEvent('click').catch((e) => log('  click err ' + e.message));
  await page.waitForTimeout(1400);
  const pop = await page.locator('.el-dropdown__popper:visible').count();
  await shot(page, file);
  log(`${label} drawer=${open} dd=${n} pop=${pop} -> ${file}`);
}

await drawerDropdownByIndex(0, 'date', 'popover-drawer-date-1440x900.png');
await drawerDropdownByIndex(1, 'remind', 'popover-drawer-remind-1440x900.png');
await drawerDropdownByIndex(2, 'repeat', 'popover-drawer-repeat-1440x900.png');

// tags select
{
  const open = await openDrawer();
  const sel = page.locator('.dialog-right-box .el-select .el-select__wrapper').first();
  const n = await sel.count();
  const disabled = n ? await sel.evaluate((e) => e.className.includes('is-disabled')) : null;
  if (n) await sel.click({ force: true }).catch((e) => log('  tags click err ' + e.message));
  await page.waitForTimeout(1400);
  const pop = await page.locator('.el-select__popper:visible, .el-popper:visible').count();
  await shot(page, 'popover-drawer-tags-1440x900.png');
  log(`tags drawer=${open} trigger=${n} disabled=${disabled} pop=${pop}`);
}

// select user
{
  const open = await openDrawer();
  const t = page.locator('.dialog-right-box .drbb-item:has-text("@") .cursor-pointer').first();
  const n = await t.count();
  if (n) await t.dispatchEvent('click').catch((e) => log('  user click err ' + e.message));
  await page.waitForTimeout(1600);
  const dlg = await page.locator('.el-dialog:visible').count();
  await shot(page, 'dialog-select-user-1440x900.png');
  log(`selectUser drawer=${open} trigger=${n} dialog=${dlg}`);
  await page.keyboard.press('Escape');
  await page.waitForTimeout(500);
}

// tag config dropdown (top bar)
await closeDrawer();
await page.waitForTimeout(400);
{
  const btn = page.locator('.r-b-t-r-item').first();
  const n = await btn.count();
  if (n) await btn.click({ force: true }).catch((e) => log('  tagcfg click err ' + e.message));
  await page.waitForTimeout(1400);
  const pop = await page.locator(POP).count();
  await shot(page, 'popover-tag-config-1440x900.png');
  log(`tagConfig trigger=${n} pop=${pop}`);
}

await browser.close();
console.log('done');
