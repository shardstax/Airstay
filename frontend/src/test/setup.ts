import '@testing-library/jest-dom/vitest';

import { setAccessToken } from '@/shared/session/accessToken';

// The access token is module-level state; every test starts signed out and with the real fetch restored.
afterEach(() => {
  setAccessToken(null);
  vi.restoreAllMocks();
});
