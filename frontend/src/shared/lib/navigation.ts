/**
 * Leaving the app for a page that is not ours (the payment page). Kept behind an object so tests can replace `redirect`;
 * `window.location.assign` cannot be spied on in a test browser.
 */
export const navigation = {
  /** Opens `url` in this tab. Only https addresses are followed, so a bad value can never send a user to a script or file URL. */
  redirect(url: string): void {
    if (new URL(url).protocol !== 'https:') {
      throw new Error('Refusing to open an address that is not https.');
    }
    window.location.assign(url);
  },
};
