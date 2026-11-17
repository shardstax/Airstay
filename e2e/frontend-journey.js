// The whole product through the web app, in a real browser, against the real running stack:
//   a hotel manager signs up and lists a hotel (place picker, amenities, photo, room type, put on sale);
//   a guest finds it, reserves a room, names a guest, pays on the REAL Stripe checkout page with Stripe's test card,
//   sees the confirmation, then cancels and is refunded; the manager sees the booking and changes prices.
// Needs the stack with real Stripe sandbox keys (.env) and PLACES_PROVIDER=synthetic. See e2e/README.md.
const { chromium } = require('playwright');

const HOST = process.env.WEB_HOST; // the machine running Docker, as seen from this container
const ORIGIN = 'http://localhost:5173';
const OUT = process.env.OUT_DIR || '/tmp';
const KEY = process.env.STRIPE_SECRET_KEY || '';
if (!KEY.startsWith('sk_test_')) {
  console.error('STRIPE_SECRET_KEY must be a Stripe TEST-mode key (sk_test_...). Refusing to run.');
  process.exit(2);
}

let failures = 0;
const step = (ok, text) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${text}`);
  if (!ok) failures++;
};
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function stripe(path) {
  const r = await fetch('https://api.stripe.com/v1' + path, { headers: { Authorization: 'Basic ' + Buffer.from(KEY + ':').toString('base64') } });
  return r.json();
}

// Stripe's hosted checkout keeps its fields in iframes that selectors cannot reach, so this clicks and types at fixed
// positions in a 1280x1300 viewport. If Stripe changes its layout, this is the part to update.
async function payOnCheckout(page, card) {
  await sleep(6000);
  const type = async (x, y, text) => {
    await page.mouse.click(x, y);
    await page.keyboard.type(text, { delay: 40 });
  };
  await type(850, 385, card);
  await type(800, 424, '1234');
  await type(990, 424, '123');
  await type(900, 584, 'E2E Traveller');
  await type(900, 660, '1 Test Street');
  await sleep(1200);
  await page.mouse.click(785, 757); // "Enter address manually"
  await sleep(1500);
  await type(900, 739, 'Pune');
  await type(900, 777, '411001');
  await page.mouse.click(900, 815);
  await sleep(800);
  await page.keyboard.type('Maharashtra');
  await page.keyboard.press('Enter');
  await sleep(1000);
  await page.mouse.click(903, 889); // Pay
}

async function pickPlace(page, query, label) {
  await page.getByRole('combobox', { name: 'City' }).fill(query);
  await page.getByRole('option', { name: label }).click();
}

(async () => {
  const id = Date.now();
  const placeQuery = `Journey${id}`;
  const placeLabel = `${placeQuery}, Test Region, Testland`;
  const managerEmail = `journey-mgr-${id}@example.test`;
  const guestEmail = `journey-guest-${id}@example.test`;
  const password = 'Password123!';

  const browser = await chromium.launch({ args: [`--host-resolver-rules=MAP localhost ${HOST}`] });
  const context = await browser.newContext({ viewport: { width: 1280, height: 1300 } });
  const page = await context.newPage();
  const problems = [];
  // Only our own pages are judged: Stripe's checkout page prints its own warnings, and the example hotel photo points at a
  // host that does not exist (ERR_NAME_NOT_RESOLVED) on purpose.
  page.on('console', (m) => {
    const ours = page.url().startsWith(ORIGIN);
    if (ours && ['error', 'warning'].includes(m.type()) && !m.text().includes('ERR_NAME_NOT_RESOLVED')) {
      problems.push(`${m.type()}: ${m.text().slice(0, 140)}`);
    }
  });
  page.on('pageerror', (e) => problems.push(`pageerror: ${e.message}`));

  async function signUp(name, email, asManager) {
    await page.goto(`${ORIGIN}/signup`);
    await page.getByLabel('Full name').fill(name);
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password').fill(password);
    if (asManager) await page.getByLabel(/I want to list hotels/).check();
    await page.getByRole('button', { name: 'Create account' }).click();
    await page.getByRole('link', { name }).waitFor();
  }

  // ---- 1. A hotel manager lists a hotel -------------------------------------------------------------------
  await signUp('Journey Manager', managerEmail, true);
  step(await page.getByRole('link', { name: 'Manage hotels' }).isVisible(), 'a manager account shows "Manage hotels" in the header');
  await page.getByRole('link', { name: 'Manage hotels' }).click();
  await page.getByRole('heading', { name: 'You have no hotels yet' }).waitFor();
  await page.getByRole('link', { name: 'Add a hotel' }).first().click();
  await page.getByRole('heading', { name: 'Add a hotel' }).first().waitFor();

  await page.getByLabel('Hotel name').fill(`Journey Hotel ${id}`);
  await pickPlace(page, placeQuery, placeLabel);
  step((await page.getByRole('combobox', { name: 'City' }).inputValue()) === placeLabel, 'the city is chosen from the place picker, not typed');
  await page.getByLabel('WiFi').check();
  await page.getByLabel('Pool').check();
  await page.getByLabel('Photo address').fill(`https://images.example.com/${id}.jpg`);
  await page.getByRole('button', { name: 'Add photo' }).click();
  await page.getByLabel('Phone (optional)').fill('+91 98765 43210');
  await page.screenshot({ path: `${OUT}/journey-hotel-form.png`, fullPage: true });
  await page.getByRole('button', { name: 'Create hotel' }).click();
  await page.getByRole('heading', { name: `Journey Hotel ${id}`, level: 1 }).waitFor();
  step(await page.getByText(placeLabel).first().isVisible(), 'the hotel is created in the chosen place, as a draft');
  step(await page.getByText('Draft').isVisible(), 'a new hotel starts as a draft');
  step(await page.getByRole('button', { name: 'Put on sale' }).isDisabled(), 'it cannot be put on sale before it has a room type');

  await page.getByRole('button', { name: 'Add a room type' }).click();
  const roomForm = page.getByRole('form', { name: 'Add a room type' });
  await roomForm.getByLabel('Room type').selectOption('Deluxe');
  await roomForm.getByLabel('Base price per night (₹)').fill('2500');
  await roomForm.getByLabel('Rooms of this type').fill('2');
  await roomForm.getByLabel('Guests per room').fill('2');
  await roomForm.getByLabel('Balcony').check();
  await roomForm.getByRole('button', { name: 'Add room type' }).click();
  await page.getByRole('heading', { name: 'Deluxe' }).waitFor();
  step(true, 'a room type is added, chosen from the fixed list');
  await page.getByRole('button', { name: 'Put on sale' }).click();
  await page.getByText('On sale').first().waitFor();
  step(true, 'the hotel is put on sale');
  await page.screenshot({ path: `${OUT}/journey-dashboard.png`, fullPage: true });

  // ---- 2. A guest finds it, reserves, pays on Stripe ------------------------------------------------------
  await page.getByRole('button', { name: 'Sign out' }).click();
  await page.getByRole('heading', { name: 'Sign in' }).waitFor();
  await signUp('Journey Guest', guestEmail, false);
  step(!(await page.getByRole('link', { name: 'Manage hotels' }).isVisible()), 'a guest account has no manager link');

  await page.goto(ORIGIN);
  await pickPlace(page, placeQuery, placeLabel);
  await page.getByRole('button', { name: 'Search' }).click();
  await page.getByRole('heading', { name: `Hotels in ${placeLabel}` }).waitFor();
  await page.getByRole('link', { name: `Journey Hotel ${id}` }).click();
  await page.getByRole('heading', { name: 'Deluxe' }).waitFor();
  step(true, 'the guest finds the hotel by searching its place');

  await page.getByRole('button', { name: 'Reserve Deluxe' }).click();
  await page.getByRole('heading', { name: 'Complete your booking' }).waitFor();
  const clock = await page.getByRole('timer').innerText();
  step(/\b(9:[3-5]\d|10:00)\b/.test(clock), `the hold clock starts near ten minutes (${clock.replace(/\s+/g, ' ').slice(0, 60)})`);
  step(await page.getByText('Room held').isVisible(), 'the room is held');
  const bookingUrl = new URL(page.url());
  const bookingId = bookingUrl.pathname.split('/').pop();

  await page.getByRole('heading', { name: 'Who is staying?' }).waitFor();
  await page.getByLabel('Full name').fill('Journey Traveller');
  await page.getByRole('button', { name: 'Save guest' }).click();
  await page.getByText('1 of 2 selected').waitFor();
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.getByRole('heading', { name: 'Pay to confirm' }).waitFor();
  step(true, 'a guest is added and the booking moves on to payment');
  await page.screenshot({ path: `${OUT}/journey-booking.png` });

  await page.getByRole('button', { name: /^Pay ₹/ }).click();
  await page.waitForURL(/checkout\.stripe\.com/, { timeout: 30000 });
  step(true, 'the guest is sent to the real Stripe checkout page');
  await payOnCheckout(page, '4242424242424242');
  await page.waitForURL(new RegExp(`/payments/${bookingId}/status`), { timeout: 60000 });
  await page.getByText('Payment received. Your booking is confirmed.').waitFor({ timeout: 60000 });
  step(true, 'after paying with the test card, the return page shows the booking confirmed (real webhook)');
  step(await page.getByRole('link', { name: 'Journey Guest' }).isVisible(), 'the guest is still signed in after the round trip to Stripe');
  await page.screenshot({ path: `${OUT}/journey-confirmed.png` });

  // ---- 3. The guest cancels and is refunded ---------------------------------------------------------------
  await page.goto(`${ORIGIN}/account/bookings`);
  await page.getByRole('heading', { name: 'Upcoming and in progress' }).waitFor();
  await page.getByRole('button', { name: 'Cancel booking' }).click();
  await page.getByRole('group', { name: 'Confirm cancellation' }).waitFor();
  await page.getByRole('button', { name: 'Yes, cancel booking' }).click();
  // Tomorrow's stay carries the last-minute surcharge, so the total is not a round ₹5,000: read what the page says.
  const notice = page.getByText(/was cancelled\. A refund of ₹[\d,]+ has been requested/);
  await notice.waitFor();
  const shown = Number((await notice.innerText()).match(/₹([\d,]+)/)[1].replace(/,/g, ''));
  step(shown > 0, `cancelling shows that a refund of ₹${shown} was requested`);
  let refund;
  for (let i = 0; i < 30 && !refund; i++) {
    await sleep(2000);
    refund = ((await stripe('/refunds?limit=5')).data || []).find((r) => Date.now() / 1000 - r.created < 400);
  }
  step(
    !!refund && refund.status === 'succeeded' && refund.amount / 100 === shown,
    `Stripe really refunded exactly that (${refund ? refund.currency.toUpperCase() + ' ' + refund.amount / 100 : 'none found'} vs ₹${shown})`,
  );

  // ---- 4. The manager sees it ----------------------------------------------------------------------------
  await page.getByRole('button', { name: 'Sign out' }).click();
  await page.getByRole('heading', { name: 'Sign in' }).waitFor();
  await page.getByLabel('Email').fill(managerEmail);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByRole('link', { name: 'Manage hotels' }).click();
  await page.getByRole('link', { name: `Journey Hotel ${id}` }).click();
  const table = page.getByRole('table', { name: 'Bookings at this hotel' });
  await table.waitFor();
  step((await table.innerText()).includes(`#${bookingId}`) && (await table.innerText()).includes('Cancelled'), 'the manager sees the booking, now cancelled');

  await page.getByRole('link', { name: 'Prices and availability' }).first().click();
  await page.getByRole('heading', { name: 'Prices and availability' }).waitFor();
  await page.getByRole('table', { name: 'Nightly prices and availability' }).waitFor();
  const first = (await page.getByRole('table', { name: 'Nightly prices and availability' }).getByRole('row').nth(1).innerText());
  step(/₹\s?2,?5\d\d|₹\s?2,?[0-9]{3}/.test(first), `the nightly prices are listed (${first.replace(/\s+/g, ' ').slice(0, 50)})`);
  await page.getByLabel('Price factor').fill('1.5');
  await page.getByRole('button', { name: 'Apply to these nights' }).click();
  await page.getByText('The nights were updated.').waitFor();
  step(true, 'the manager changes the price factor of a range of nights');
  await page.screenshot({ path: `${OUT}/journey-inventory.png`, fullPage: true });

  // The wrong role gets a clear message, not a blank page.
  await page.getByRole('button', { name: 'Sign out' }).click();
  await page.getByRole('heading', { name: 'Sign in' }).waitFor();
  await page.getByLabel('Email').fill(guestEmail);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByRole('link', { name: 'Journey Guest' }).waitFor();
  await page.goto(`${ORIGIN}/manage`);
  await page.getByRole('heading', { name: 'This area is for hotel managers' }).waitFor();
  step(true, 'a guest opening the manager area is told it is for hotel managers');

  // The browser logs every failed request; none is expected in this journey except nothing at all.
  const unexpected = problems.filter((p) => !/status of 40[134]/.test(p));
  step(unexpected.length === 0, `no console errors or warnings${unexpected.length ? ': ' + unexpected.join(' | ') : ''}`);

  await browser.close();
  console.log(failures === 0 ? '\nALL STEPS PASSED' : `\n${failures} STEP(S) FAILED`);
  process.exit(failures === 0 ? 0 : 1);
})().catch((e) => {
  console.error('ERROR', e.message);
  process.exit(1);
});
