// Browser check of Stage 1 (sign up, sign in, session restore, sign out) against the real running stack.
// See e2e/README.md. Prints a PASS/FAIL line per step and exits non-zero if any failed.
const { chromium } = require('playwright');

const HOST = process.env.WEB_HOST; // the machine running Docker, as seen from this container
const ORIGIN = 'http://localhost:5173';
const OUT = process.env.OUT_DIR || '/tmp';

let failures = 0;
const step = (ok, text) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${text}`);
  if (!ok) failures++;
};

(async () => {
  // The refresh cookie is Secure, which browsers only accept on https or on localhost. Map "localhost" to the Docker host
  // so the page is a real localhost origin, exactly like opening http://localhost:5173 on your own machine.
  const browser = await chromium.launch({ args: [`--host-resolver-rules=MAP localhost ${HOST}`] });
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const page = await context.newPage();
  const problems = [];
  page.on('console', (m) => ['error', 'warning'].includes(m.type()) && problems.push(`${m.type()}: ${m.text().slice(0, 120)}`));
  page.on('pageerror', (e) => problems.push(`pageerror: ${e.message}`));

  const id = Date.now();
  const email = `e2e-web-${id}@example.test`;
  const password = 'Password123!';

  // 1. A protected page sends a visitor to sign in.
  await page.goto(`${ORIGIN}/account`);
  await page.getByRole('heading', { name: 'Sign in' }).waitFor();
  step(new URL(page.url()).pathname === '/login', 'visiting /account while signed out redirects to /login');
  await page.screenshot({ path: `${OUT}/auth-login.png` });

  // 2. Form validation happens before any request.
  await page.getByRole('button', { name: 'Sign in' }).click();
  step(await page.getByText('Enter a valid email address.').isVisible(), 'empty sign-in form shows validation messages');

  // 3. Wrong password.
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill('not-the-password');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByRole('alert').filter({ hasText: 'incorrect' }).waitFor();
  step(true, 'unknown account / wrong password shows "The email or password is incorrect."');

  // 4. Sign up from the sign-in page; the user was heading to /account, so they land there.
  await page.getByRole('link', { name: 'Create an account' }).click();
  await page.getByRole('heading', { name: 'Create your account' }).waitFor();
  await page.screenshot({ path: `${OUT}/auth-signup.png` });
  await page.getByLabel('Full name').fill('Web Tester');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.getByRole('heading', { name: 'My account' }).waitFor();
  step(await page.getByText(email).isVisible(), 'sign-up signs the user in and returns to the page they wanted (/account)');
  step(await page.getByRole('link', { name: 'Web Tester' }).isVisible(), 'header shows the user name');
  await page.screenshot({ path: `${OUT}/auth-account.png` });

  // 5. The session survives a reload: restored from the httpOnly refresh cookie, not from storage.
  await page.reload();
  await page.getByRole('heading', { name: 'My account' }).waitFor();
  step(true, 'a page reload keeps the user signed in (session restored from the refresh cookie)');
  const stored = await page.evaluate(() => JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage }));
  step(!/eyJ/.test(stored), 'no token is stored in localStorage or sessionStorage');
  const cookies = await context.cookies();
  const refresh = cookies.find((c) => c.name === 'refreshToken');
  step(!!refresh && refresh.httpOnly && refresh.secure && refresh.sameSite === 'Strict' && refresh.path === '/api/v1/auth',
    'refresh cookie is httpOnly, Secure, SameSite=Strict, scoped to /api/v1/auth');

  // 6. Sign out.
  await page.getByRole('button', { name: 'Sign out' }).click();
  await page.getByRole('heading', { name: 'Sign in' }).waitFor();
  step(true, 'sign out returns to the sign-in page');
  await page.reload();
  await page.getByRole('heading', { name: 'Sign in' }).waitFor();
  step(!(await context.cookies()).some((c) => c.name === 'refreshToken' && c.value), 'after sign out the refresh cookie is gone and a reload stays signed out');

  // 7. Sign in again.
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByRole('link', { name: 'Web Tester' }).waitFor();
  step(true, 'signing in again works');

  // 8. A signed-in user does not see the sign-in page.
  await page.goto(`${ORIGIN}/login`);
  await page.getByRole('link', { name: 'Web Tester' }).waitFor();
  step(new URL(page.url()).pathname === '/', 'a signed-in user opening /login is sent to the start page');

  // The browser logs every failed request. Exactly one is expected: the deliberate wrong-password attempt (HTTP 401).
  // An anonymous visit must not produce one of its own (no pointless refresh request).
  const unexpected = problems.filter((p) => !p.includes("status of 401"));
  const failedRequests = problems.length - unexpected.length;
  step(unexpected.length === 0, `no console errors or warnings${unexpected.length ? ": " + unexpected.join(" | ") : ""}`);
  step(failedRequests === 1, `exactly one failed request was logged (the wrong password), got ${failedRequests}`);

  await browser.close();
  console.log(failures === 0 ? '\nALL STEPS PASSED' : `\n${failures} STEP(S) FAILED`);
  process.exit(failures === 0 ? 0 : 1);
})().catch((e) => {
  console.error('ERROR', e.message);
  process.exit(1);
});
