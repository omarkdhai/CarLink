/**
 * Tiny classnames helper — no external dependency.
 * Joins truthy values and ignores falsy ones.
 */
export function clsx(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(' ')
}

/** Convenience alias — both `cn` and `clsx` are used across the codebase. */
export const cn = clsx

export default clsx