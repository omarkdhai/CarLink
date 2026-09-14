import { QueryClient } from '@tanstack/react-query'

/**
 * Global TanStack Query client.
 * - Stale data refreshed on window focus keeps dashboards fresh.
 * - Retries are kept low (the API layer already handles token refresh once).
 * - 429/401 are surfaced fast rather than retried blindly.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      retry: (failureCount, error) => {
        const status = (error as { status?: number })?.status
        if (status === 401 || status === 403 || status === 429) return false
        return failureCount < 2
      },
      refetchOnWindowFocus: true,
    },
    mutations: {
      retry: false,
    },
  },
})