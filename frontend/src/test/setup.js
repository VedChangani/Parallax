import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// jsdom implements neither API; uPlot (EquityCurve.jsx) touches matchMedia at
// module-load time to detect the device pixel ratio, and ResizeObserver to
// track its container's size - both are real browser APIs the production
// app always has, stubbed here only so the chart can mount under jsdom.
if (typeof window.matchMedia !== 'function') {
  window.matchMedia = () => ({
    matches: false,
    media: '',
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })
}

if (typeof window.ResizeObserver !== 'function') {
  window.ResizeObserver = class ResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}

afterEach(() => {
  cleanup()
})
