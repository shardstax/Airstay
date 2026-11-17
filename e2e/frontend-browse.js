// Browser check of Stage 2 (search, results, paging, hotel page) against the real running stack.
// It creates its own hotels through the API first. See e2e/README.md. PASS/FAIL per step; non-zero exit on any failure.
const { chromium } = require('playwright');

const HOST = process.env.WEB_HOST; // the machine running Docker, as seen from this container
const ORIGIN = 'http://localhost:5173';
const API = `http://${HOST}:8080/api/v1`;
const OUT = process.env.OUT_DIR || '/tmp';

let failures = 0;
const step = (ok, text) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${text}`);
  if (!ok) failures++;
};

async function api(method, path, token, body) {
  const response = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let json = null;
  try {
    json = JSON.parse(text);
  } catch {
    /* not JSON */
  }
  return { status: response.status, json, text };
}

const day = (offset) => {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() + offset);
  return d.toISOString().slice(0, 10);
};

async function signUp(email, name, accountType) {
  let r = await api('POST', '/auth/signup', null, { email, password: 'Password123!', name, accountType });
  if (r.status !== 201) throw new Error(`signup failed: ${r.status} ${r.text}`);
  r = await api('POST', '/auth/login', null, { email, password: 'Password123!' });
  return r.json.data.accessToken;
}

async function createHotel(manager, name, city, rooms, amenities) {
  let r = await api('POST', '/admin/hotels', manager, { name, city, amenities });
  if (r.status !== 201) throw new Error(`create hotel failed: ${r.status} ${r.text}`);
  const hotelId = r.json.data.id;
  const roomIds = [];
  for (const room of rooms) {
    r = await api('POST', `/admin/hotels/${hotelId}/rooms`, manager, { ...room, capacity: 2 });
    if (r.status !== 201) throw new Error(`create room failed: ${r.status} ${r.text}`);
    roomIds.push(r.json.data.id);
  }
  r = await api('PATCH', `/admin/hotels/${hotelId}/activate`, manager);
  if (r.status !== 204) throw new Error(`activate failed: ${r.status} ${r.text}`);
  return { hotelId, roomIds };
}

(async () => {
  const id = Date.now();
  const city = `Browse${id}`;
  const startDate = day(30);
  const endDate = day(32);

  // ---- test data, through the API --------------------------------------------------------------------------
  const manager = await signUp(`e2e-browse-mgr-${id}@example.test`, 'Browse Manager', 'HOTEL_MANAGER');
  const guest = await signUp(`e2e-browse-guest-${id}@example.test`, 'Browse Guest');
  const amenityPool = ['WiFi', 'Pool', 'Gym', 'Spa', 'Parking', 'Breakfast'];
  for (let i = 1; i <= 12; i++) {
    await createHotel(
      manager,
      `Browse Hotel ${String(i).padStart(2, '0')}`,
      city,
      [
        { type: 'Standard', basePrice: 1500 + i * 100, totalCount: 3 },
        { type: 'Suite', basePrice: 3000 + i * 100, totalCount: 2 },
      ],
      amenityPool.slice(0, 1 + (i % 6)),
    );
  }
  const soldOutCity = `SoldOut${id}`;
  const soldOut = await createHotel(manager, 'Fully Booked Inn', soldOutCity, [{ type: 'Only Room', basePrice: 2000, totalCount: 1 }], ['WiFi']);
  const guestId = (await api('POST', '/users/guests', guest, { name: 'Browse Traveller' })).json.data.id;
  void guestId;
  const hold = await api('POST', '/bookings/init', guest, {
    hotelId: soldOut.hotelId,
    roomId: soldOut.roomIds[0],
    checkInDate: startDate,
    checkOutDate: endDate,
    roomsCount: 1,
  });
  step(hold.status === 201, 'test data created: 12 hotels in one city and one fully booked hotel');

  // ---- the browser -----------------------------------------------------------------------------------------
  const browser = await chromium.launch({ args: [`--host-resolver-rules=MAP localhost ${HOST}`] });
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 800 } })).newPage();
  const problems = [];
  page.on('console', (m) => ['error', 'warning'].includes(m.type()) && problems.push(`${m.type()}: ${m.text().slice(0, 120)}`));
  page.on('pageerror', (e) => problems.push(`pageerror: ${e.message}`));

  // 1. Search from the home page.
  await page.goto(ORIGIN);
  await page.getByRole('heading', { name: 'Find a place to stay.' }).waitFor();
  await page.screenshot({ path: `${OUT}/browse-home.png` });
  await page.getByRole('button', { name: 'Search' }).click();
  step(await page.getByText('Enter a city.').isVisible(), 'searching without a city shows a validation message');
  await page.getByLabel('City').fill(city);
  await page.getByLabel('Check-in').fill(startDate);
  await page.getByLabel('Check-out').fill(endDate);
  await page.getByRole('button', { name: 'Search' }).click();
  await page.getByRole('heading', { name: `Hotels in ${city}` }).waitFor();
  await page.getByText('12 hotels found').waitFor();
  const url = new URL(page.url());
  step(url.pathname === '/search' && url.searchParams.get('city') === city, 'results page opens with the search in the URL');

  // 2. First page: 10 results, paging to the second.
  const firstPage = await page.getByRole('list').filter({ has: page.getByRole('heading', { level: 2 }) }).first().getByRole('heading', { level: 2 }).count();
  step(firstPage === 10, `first page shows 10 hotels (got ${firstPage})`);
  await page.screenshot({ path: `${OUT}/browse-results.png`, fullPage: true });
  step(await page.getByText('Page 1 of 2').isVisible(), 'pager shows "Page 1 of 2"');
  await page.getByRole('link', { name: 'Next' }).click();
  await page.getByText('Page 2 of 2').waitFor();
  // The previous page stays on screen (dimmed) while the next one loads, so wait until the list has actually changed.
  // (Polling a locator, not page.waitForFunction: the app's CSP forbids the eval that function would need.)
  for (let tries = 0; tries < 40 && (await page.getByRole('heading', { level: 2 }).count()) !== 2; tries++) {
    await page.waitForTimeout(250);
  }
  const secondPage = await page.getByRole('heading', { level: 2 }).count();
  step(secondPage === 2 && new URL(page.url()).searchParams.get('page') === '2', 'next page shows the remaining 2 hotels and page=2 in the URL');
  await page.getByRole('link', { name: 'Previous' }).click();
  await page.getByText('Page 1 of 2').waitFor();
  step(!new URL(page.url()).searchParams.has('page'), 'Previous returns to page 1 with no page parameter');

  // 3. A reload keeps the search (deep link).
  await page.reload();
  await page.getByText('12 hotels found').waitFor();
  step((await page.getByLabel('City').inputValue()) === city, 'reloading the results page keeps the search');

  // 4. Open a hotel.
  await page.getByRole('link', { name: 'Browse Hotel 03' }).first().click();
  await page.getByRole('heading', { name: 'Browse Hotel 03', level: 1 }).waitFor();
  await page.getByRole('heading', { name: 'Standard' }).waitFor();
  await page.screenshot({ path: `${OUT}/browse-hotel.png`, fullPage: true });
  const roomHeadings = await page.getByRole('heading', { level: 3 }).allTextContents();
  step(roomHeadings.includes('Standard') && roomHeadings.includes('Suite'), `hotel page lists both room types (${roomHeadings.join(', ')})`);
  // The first amount in a piece of text, e.g. "₹1,800 per night" gives 1800.
  const rupees = (text) => Number(/₹\s*([\d,]+)/.exec(text)[1].replace(/,/g, ''));
  const perNight = rupees(await page.locator('p', { hasText: 'per night' }).first().innerText());
  const totalText = await page.getByText(/for 2 nights/).first().innerText();
  step(
    perNight > 0 && rupees(totalText) === perNight * 2,
    `total for 2 nights (${rupees(totalText)}) is twice the nightly price (${perNight})`,
  );
  await page.getByRole('link', { name: 'Back to results' }).click();
  await page.getByText('12 hotels found').waitFor();
  step(true, 'Back to results returns to the same search');

  // 5. Sold out.
  await page.goto(`${ORIGIN}/hotels/${soldOut.hotelId}?startDate=${startDate}&endDate=${endDate}&rooms=1`);
  await page.getByRole('heading', { name: 'No rooms available for these dates' }).waitFor();
  step(true, 'a fully booked hotel says no rooms are available for those dates');
  await page.goto(`${ORIGIN}/search?city=${soldOutCity}&startDate=${startDate}&endDate=${endDate}&rooms=1`);
  await page.getByRole('heading', { name: 'No hotels available for these dates' }).waitFor();
  step(true, 'and it is left out of the search results');

  // 6. Unknown things.
  await page.goto(`${ORIGIN}/search?city=Nowhereville${id}&startDate=${startDate}&endDate=${endDate}`);
  await page.getByRole('heading', { name: 'No hotels available for these dates' }).waitFor();
  step(true, 'a city with no hotels shows the empty state');
  await page.goto(`${ORIGIN}/hotels/999999999?startDate=${startDate}&endDate=${endDate}&rooms=1`);
  await page.getByRole('heading', { name: 'Hotel not found' }).waitFor();
  step(true, 'an unknown hotel shows "Hotel not found"');
  await page.goto(`${ORIGIN}/search?city=Pune&startDate=2020-01-01&endDate=2020-01-03`);
  await page.getByRole('heading', { name: 'Start a new search' }).waitFor();
  step(true, 'a link with past dates asks for a new search instead of failing');

  // 7. Phone width: no sideways scrolling on the three screens.
  await page.setViewportSize({ width: 390, height: 800 });
  for (const [name, path] of [
    ['home', '/'],
    ['results', `/search?city=${city}&startDate=${startDate}&endDate=${endDate}&rooms=1`],
    ['hotel', `/hotels/${soldOut.hotelId}?startDate=${startDate}&endDate=${endDate}&rooms=1`],
  ]) {
    await page.goto(ORIGIN + path);
    await page.waitForLoadState('networkidle');
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
    step(!overflow, `no horizontal scrolling on the ${name} page at 390px wide`);
  }
  await page.goto(`${ORIGIN}/search?city=${city}&startDate=${startDate}&endDate=${endDate}&rooms=1`);
  await page.getByText('12 hotels found').waitFor();
  await page.screenshot({ path: `${OUT}/browse-mobile.png` });

  // The browser logs every failed request: exactly one is expected here (the unknown hotel, HTTP 404).
  const unexpected = problems.filter((p) => !p.includes('status of 404'));
  step(unexpected.length === 0, `no console errors or warnings${unexpected.length ? ': ' + unexpected.join(' | ') : ''}`);

  await browser.close();
  console.log(failures === 0 ? '\nALL STEPS PASSED' : `\n${failures} STEP(S) FAILED`);
  process.exit(failures === 0 ? 0 : 1);
})().catch((e) => {
  console.error('ERROR', e.message);
  process.exit(1);
});
