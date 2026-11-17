import { isHttpsUrl } from './url';

describe('isHttpsUrl', () => {
  it.each([
    'https://images.example.com/a.jpg',
    'https://a.co',
    'HTTPS://IMAGES.EXAMPLE.COM/A.JPG',
    'https://x.y/z?q=1#f',
  ])('accepts %s', (value) => {
    expect(isHttpsUrl(value)).toBe(true);
  });

  it.each([
    'http://images.example.com/a.jpg',
    'javascript:alert(1)',
    'data:image/png;base64,AAAA',
    'file:///etc/passwd',
    'ftp://example.com/a.jpg',
    '//images.example.com/a.jpg',
    'images.example.com/a.jpg',
    'https://',
    'not a url',
    '',
  ])('refuses %j', (value) => {
    expect(isHttpsUrl(value)).toBe(false);
  });

  it('refuses an address longer than 2048 characters', () => {
    expect(isHttpsUrl(`https://example.com/${'a'.repeat(2048)}`)).toBe(false);
    expect(isHttpsUrl(`https://example.com/${'a'.repeat(2000)}`)).toBe(true);
  });
});
