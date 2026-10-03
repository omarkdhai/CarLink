import type { ReactNode } from 'react'

interface FieldProps {
  label: string
  hint?: string
  error?: string
  /** Optional-marker text (e.g. "facultatif"), rendered after the label. */
  optional?: string
  /** Renders a red `*` after the label to mark the field as required. */
  required?: boolean
  children: ReactNode
}

/**
 * Form field wrapper. Nests the control inside the <label> so the label is
 * implicitly associated with the input — correct for screen readers without
 * relying on id wiring, which react-hook-form inputs don't expose directly.
 */
export function Field({ label, hint, error, optional, required, children }: FieldProps) {
  return (
    <div className="space-y-1.5">
      <label className="flex flex-col gap-1.5 cursor-text">
        <span className="text-sm font-semibold text-heading">
          {label}
          {required && (
            <span className="ms-0.5 text-destructive" aria-hidden="true">
              *
            </span>
          )}
          {optional && (
            <span className="ms-1 font-normal text-muted-fg text-xs" aria-hidden="true">
              · {optional}
            </span>
          )}
        </span>
        {children}
      </label>
      {error ? (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      ) : hint ? (
        <p className="text-xs text-muted-fg">{hint}</p>
      ) : null}
    </div>
  )
}