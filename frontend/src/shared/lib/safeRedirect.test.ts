import { safeRedirectPath } from './safeRedirect';

describe('safeRedirectPath', () => {
  it.each(['/', '/account', '/hotels/7?startDate=2026-12-20', '/search#results'])(
    'accepts the in-app path %s',
    (path) => {
      expect(safeRedirectPath(path)).toBe(path);
    },
  );

  it.each([
    ['another site', 'https://evil.example/login'],
    ['a protocol-relative URL', '//evil.example'],
    ['a backslash trick', '/\\evil.example'],
    ['a relative path', 'account'],
    ['a javascript URL', 'javascript:alert(1)'],
    ['an empty string', ''],
  ])('rejects %s', (_name, value) => {
    expect(safeRedirectPath(value)).toBe('/');
  });

  it.each([undefined, null, 42, {}, ['/account']])('rejects the non-string %j', (value) => {
    expect(safeRedirectPath(value)).toBe('/');
  });

  it('uses the fallback it is given', () => {
    expect(safeRedirectPath('https://evil.example', '/account')).toBe('/account');
  });
});
