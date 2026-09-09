import { useMemo } from 'react'

export interface RgbColor {
  main: string
  soft: string
}

/** Maps a human color word to a soft brand-tinted pair for vehicle avatars. */
const palette: Record<string, RgbColor> = {
  rouge: { main: '#DC2626', soft: '#FEF2F2' },
  red: { main: '#DC2626', soft: '#FEF2F2' },
  reda: { main: '#DC2626', soft: '#FEF2F2' },
  bleu: { main: '#2563EB', soft: '#EFF6FF' },
  blue: { main: '#2563EB', soft: '#EFF6FF' },
  noir: { main: '#1E293B', soft: '#F1F5F9' },
  black: { main: '#1E293B', soft: '#F1F5F9' },
  blanc: { main: '#64748B', soft: '#F8FAFC' },
  white: { main: '#64748B', soft: '#F8FAFC' },
  vert: { main: '#10B981', soft: '#ECFDF5' },
  green: { main: '#10B981', soft: '#ECFDF5' },
  gris: { main: '#64748B', soft: '#F1F5F9' },
  gray: { main: '#64748B', soft: '#F1F5F9' },
  silver: { main: '#94A3B8', soft: '#F8FAFC' },
  jaune: { main: '#F59E0B', soft: '#FFFBEB' },
  yellow: { main: '#F59E0B', soft: '#FFFBEB' },
  orange: { main: '#EA580C', soft: '#FFF7ED' },
  marron: { main: '#92400E', soft: '#FEF3C7' },
  brown: { main: '#92400E', soft: '#FEF3C7' },
  violet: { main: '#7C3AED', soft: '#F5F3FF' },
  purple: { main: '#7C3AED', soft: '#F5F3FF' },
  rose: { main: '#DB2777', soft: '#FDF2F8' },
  pink: { main: '#DB2777', soft: '#FDF2F8' },
}

const fallback: RgbColor = { main: '#475569', soft: '#E9EFF8' }

/** Case/accent-insensitive lookup → colored tint for vehicle cards. */
export function useCarColor(color: string | null): RgbColor {
  return useMemo(() => {
    if (!color) return fallback
    const key = color.toLowerCase().trim()
    return palette[key] ?? fallback
  }, [color])
}

export default useCarColor