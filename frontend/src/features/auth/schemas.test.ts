import { loginSchema, signupSchema } from './schemas';

const issues = (result: {
  success: boolean;
  error?: { issues: { message: string; path: PropertyKey[] }[] };
}) => result.error?.issues.map((issue) => `${issue.path.join('.')}: ${issue.message}`) ?? [];

describe('signupSchema', () => {
  const valid = { name: 'Asha Rao', email: 'asha@example.com', password: 'correct horse', manager: false };

  it('accepts a valid sign-up', () => {
    expect(signupSchema.safeParse(valid).success).toBe(true);
  });

  it('trims the name', () => {
    expect(signupSchema.parse({ ...valid, name: '  Asha  ' }).name).toBe('Asha');
  });

  it('rejects a blank name', () => {
    expect(issues(signupSchema.safeParse({ ...valid, name: '   ' }))).toEqual(['name: Enter your name.']);
  });

  it('rejects an invalid email', () => {
    expect(issues(signupSchema.safeParse({ ...valid, email: 'not-an-email' }))).toEqual([
      'email: Enter a valid email address.',
    ]);
  });

  it('enforces the 8 to 72 character password rule of the backend', () => {
    expect(issues(signupSchema.safeParse({ ...valid, password: 'short' }))).toEqual([
      'password: Use at least 8 characters.',
    ]);
    expect(signupSchema.safeParse({ ...valid, password: 'a'.repeat(72) }).success).toBe(true);
    expect(issues(signupSchema.safeParse({ ...valid, password: 'a'.repeat(73) }))).toEqual([
      'password: Use at most 72 characters.',
    ]);
  });

  it('limits the name to 100 characters', () => {
    expect(signupSchema.safeParse({ ...valid, name: 'a'.repeat(101) }).success).toBe(false);
  });
});

describe('loginSchema', () => {
  it('only asks that a password is present, since the server decides if it is right', () => {
    expect(loginSchema.safeParse({ email: 'a@b.co', password: 'x' }).success).toBe(true);
    expect(issues(loginSchema.safeParse({ email: 'a@b.co', password: '' }))).toEqual([
      'password: Enter your password.',
    ]);
  });

  it('rejects an invalid email', () => {
    expect(loginSchema.safeParse({ email: 'nope', password: 'x' }).success).toBe(false);
  });
});
