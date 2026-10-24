import { useCallback, useEffect, useRef, useState } from 'react'
import { BrowserQRCodeReader, type IScannerControls } from '@zxing/browser'

export type ScannerStatus = 'idle' | 'starting' | 'scanning' | 'error'

interface UseQrScannerResult {
  videoRef: React.RefObject<HTMLVideoElement | null>
  status: ScannerStatus
  error: string | null
  start: () => Promise<void>
  stop: () => void
}

/**
 * Thin wrapper around ZXing's BrowserQRCodeReader for one-shot QR scanning.
 * Requests the rear camera when available, stops the media stream on teardown,
 * and invokes `onResult` with the decoded payload exactly once per start().
 */
export function useQrScanner(onResult: (text: string) => void): UseQrScannerResult {
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const controlsRef = useRef<IScannerControls | null>(null)
  const [status, setStatus] = useState<ScannerStatus>('idle')
  const [error, setError] = useState<string | null>(null)
  const cbRef = useRef(onResult)
  cbRef.current = onResult

  const stop = useCallback(() => {
    controlsRef.current?.stop()
    controlsRef.current = null
    setStatus((s) => (s === 'scanning' ? 'idle' : s))
  }, [])

  const start = useCallback(async () => {
    const video = videoRef.current
    if (!video || controlsRef.current) return
    setStatus('starting')
    setError(null)
    try {
      const reader = new BrowserQRCodeReader()
      const controls = await reader.decodeFromConstraints(
        { video: { facingMode: { ideal: 'environment' } } },
        video,
        (result) => {
          if (result) {
            cbRef.current(result.getText())
            stop()
          }
        },
      )
      controlsRef.current = controls
      setStatus('scanning')
    } catch (err) {
      setStatus('error')
      setError(err instanceof Error ? err.message : 'camera')
    }
  }, [stop])

  useEffect(() => stop, [stop])

  return { videoRef, status, error, start, stop }
}
