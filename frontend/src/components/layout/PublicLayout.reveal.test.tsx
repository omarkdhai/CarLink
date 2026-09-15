import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { useState } from 'react'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { PublicLayout } from '@/components/layout/PublicLayout'
import '@/lib/i18n'

/**
 * Regression test for the blank-screen bug on the order stepper:
 * sections mounted AFTER the initial page render (internal state swap, no
 * route change) were never observed by PublicLayout's scroll-reveal
 * IntersectionObserver, so they stayed at `opacity: 0` — an invisible page.
 * A MutationObserver now re-scan on DOM mutations and reveals them.
 */

type IOCallback = (entries: IntersectionObserverEntry[], observer: IntersectionObserver) => void

class FakeIntersectionObserver {
  static instances: FakeIntersectionObserver[] = []
  observed: Element[] = []
  private cb: IOCallback

  constructor(cb: IOCallback) {
    this.cb = cb
    FakeIntersectionObserver.instances.push(this)
  }

  observe(el: Element) {
    this.observed.push(el)
    // The element is right under the viewport, so reveal it immediately.
    this.cb([{ isIntersecting: true, target: el } as unknown as IntersectionObserverEntry], this as unknown as IntersectionObserver)
  }

  unobserve() {}
  disconnect() {}
  takeRecords() {
    return []
  }
  root = null
  rootMargin = '0px 0px -60px 0px'
  thresholds: number[] = []
}

class FakeMutationObserver {
  static instances: FakeMutationObserver[] = []
  private cb: MutationCallback

  constructor(cb: MutationCallback) {
    this.cb = cb
    FakeMutationObserver.instances.push(this)
  }

  /** Simulate the browser queueing a mutation record for the new <section>. */
  notify(newSection: Element) {
    this.cb(
      [{ addedNodes: [newSection], type: 'childList' } as unknown as MutationRecord],
      this as unknown as MutationObserver,
    )
  }

  observe() {}
  disconnect() {}
  takeRecords() {
    return []
  }
}

function StepperDemo() {
  const [step, setStep] = useState(1)
  return (
    <>
      <button type="button" onClick={() => setStep(2)}>
        continue
      </button>
      {step === 1 && <section id="step-1">step one</section>}
      {step === 2 && <section id="step-2">step two</section>}
    </>
  )
}

describe('PublicLayout scroll-reveal', () => {
  beforeEach(() => {
    FakeIntersectionObserver.instances = []
    FakeMutationObserver.instances = []
    vi.stubGlobal('IntersectionObserver', FakeIntersectionObserver)
    vi.stubGlobal('MutationObserver', FakeMutationObserver)
    window.scrollTo = vi.fn()
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  test('reveals a section mounted after the initial render (internal step swap)', () => {
    render(
      <MemoryRouter>
        <PublicLayout>
          <StepperDemo />
        </PublicLayout>
      </MemoryRouter>,
    )

    // Route-mount sections are revealed.
    expect(screen.getByText('step one').closest('section')).toHaveClass('is-revealed')

    // Swap to step 2 without navigating — this is the previously-blank case.
    fireEvent.click(screen.getByText('continue'))
    const stepTwo = screen.getByText('step two').closest('section')!
    expect(stepTwo).toBeInTheDocument()

    // The MutationObserver must have re-scanned and revealed it…
    const mo = FakeMutationObserver.instances.at(-1)
    expect(mo).toBeDefined()
    mo!.notify(stepTwo)

    expect(stepTwo).toHaveClass('is-revealed')
    // …and it was actually observed (not the ancient-browser fallback).
    const observed = FakeIntersectionObserver.instances.flatMap((i) => i.observed)
    expect(observed).toContain(stepTwo)
  })

  test('does not re-trigger the ancient-browser fallback when observers exist', () => {
    render(
      <MemoryRouter>
        <PublicLayout>
          <section id="plain">plain</section>
        </PublicLayout>
      </MemoryRouter>,
    )
    const observed = FakeIntersectionObserver.instances.flatMap((i) => i.observed)
    expect(observed.map((el) => el.id)).toContain('plain')
  })
})