import { navigation } from './navigation';

describe('navigation.redirect', () => {
  it.each(['http://pay.example/x', 'javascript:alert(1)', 'data:text/html,hi', 'file:///etc/passwd'])(
    'refuses %s',
    (url) => {
      expect(() => {
        navigation.redirect(url);
      }).toThrow(/not https/);
    },
  );

  it('refuses text that is not an address at all', () => {
    expect(() => {
      navigation.redirect('not a url');
    }).toThrow();
  });
});
