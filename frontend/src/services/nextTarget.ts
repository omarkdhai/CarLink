/**
 * Persists a post-auth redirect target across the login/register flow.
 * Stored in sessionStorage so it is scoped to the current browser tab
 * and cleared after use.
 */
const STORAGE_KEY = 'carlink.next'

export function setNextAfterAuth(path: string): void {
  sessionStorage.setItem(STORAGE_KEY, path)
}

export function takeNextAfterAuth(): string | null {
  const value = sessionStorage.getItem(STORAGE_KEY)
  if (value) sessionStorage.removeItem(STORAGE_KEY)
  return value || null
}
