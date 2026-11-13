import { useSyncExternalStore } from 'react';

const conceptViewport = '(min-width: 1200px)';

function subscribeViewport(onChange: () => void) {
  const media = window.matchMedia(conceptViewport);
  media.addEventListener('change', onChange);
  return () => media.removeEventListener('change', onChange);
}

function isSampleViewport() {
  return window.matchMedia(conceptViewport).matches;
}

export function useB2Desktop() {
  return useSyncExternalStore(subscribeViewport, isSampleViewport);
}
