import '@testing-library/jest-dom/vitest';
import { configure } from '@testing-library/react';

import { setAccessToken } from '@/shared/session/accessToken';

// Waiting for something that depends on a debounce and a request (the place suggestions) can take longer than the default
// second on a busy machine. A generous limit costs nothing when things are fast and stops tests failing for the wrong reason.
configure({ asyncUtilTimeout: 4000 });

// The access token is module-level state; every test starts signed out and with the real fetch restored.
afterEach(() => {
  setAccessToken(null);
  vi.restoreAllMocks();
});
