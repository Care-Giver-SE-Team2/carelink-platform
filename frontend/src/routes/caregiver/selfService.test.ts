import { act, renderHook } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { useSelfServiceWrite } from './selfService'

afterEach(() => vi.useRealTimers())

it('times out an unconfirmed write once and never retries it automatically', async () => {
  vi.useFakeTimers()
  const saved = vi.fn()
  const write = vi.fn((signal: AbortSignal) => new Promise<unknown>((_, reject) => {
    signal.addEventListener('abort', () => reject(new DOMException('Timed out', 'AbortError')))
  }))
  const { result } = renderHook(useSelfServiceWrite)
  let pending!: Promise<void>
  act(() => { pending = result.current.send(write, saved) })
  expect(result.current.pending).toBe(true)
  await act(async () => { await vi.advanceTimersByTimeAsync(20000); await pending })
  expect(result.current.pending).toBe(false)
  expect(result.current.error).toBeInstanceOf(DOMException)
  expect(write).toHaveBeenCalledTimes(1)
  expect(saved).not.toHaveBeenCalled()
})

it('guards concurrent submissions and drops a late success after leaving', async () => {
  let resolve!: (value: number) => void
  let signal!: AbortSignal
  const write = vi.fn((value: AbortSignal) => { signal = value; return new Promise<number>(r => { resolve = r }) })
  const saved = vi.fn()
  const { result, unmount } = renderHook(useSelfServiceWrite)
  let pending!: Promise<void>
  act(() => { pending = result.current.send(write, saved); void result.current.send(write, saved) })
  expect(write).toHaveBeenCalledTimes(1)
  unmount()
  expect(signal.aborted).toBe(true)
  await act(async () => { resolve(1); await pending })
  expect(saved).not.toHaveBeenCalled()
})
