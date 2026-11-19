import '@testing-library/jest-dom/vitest'

// jsdom has no matchMedia, but GSAP's ScrollTrigger needs it at registration.
// Nothing matches here, so every gsap.matchMedia() motion branch stays off in
// tests and components render their final, static state.
if (typeof window !== 'undefined' && typeof window.matchMedia !== 'function') {
  window.matchMedia = (query: string) =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList
}
