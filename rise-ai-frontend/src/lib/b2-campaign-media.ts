/**
 * An explicitly generic placeholder service is not a campaign-supplied photo.
 * Only this known demo/media-generator host is suppressed. Ordinary real
 * HTTP(S) cover URLs remain unchanged and are still controlled by Backend.
 */
export function isSyntheticPlaceholderMedia(source?: string): boolean {
  if (!source) return false;
  try {
    const url = new URL(source);
    return (url.protocol === 'https:' || url.protocol === 'http:') &&
      (url.hostname.toLowerCase() === 'placehold.co' || url.hostname.toLowerCase() === 'www.placehold.co');
  } catch {
    return false;
  }
}
