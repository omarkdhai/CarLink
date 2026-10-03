import type { FieldValues, Path, UseFormRegister } from 'react-hook-form'
import { Input } from '@/components/ui/Input'
import { clsx } from '@/lib/cn'

interface PhoneFieldsProps<T extends FieldValues> {
  register: UseFormRegister<T>
  codeName: Path<T>
  nationalName: Path<T>
  codeError?: string
  nationalError?: string
  /** Prefix displayed inside the national-number input, e.g. "6 12 34 56 78". */
  nationalPlaceholder?: string
  /** RTL-safe: render country code on the right when true. */
  dir?: 'ltr' | 'rtl'
}

/**
 * Country-code (+216) + national-number pair used by both registration and
 * the profile form. Values are combined into a single E.164 phone on submit;
 * see {@code lib/phone.ts}.
 */
export function PhoneFields<T extends FieldValues>({
  register,
  codeName,
  nationalName,
  codeError,
  nationalError,
  nationalPlaceholder,
  dir = 'ltr',
}: PhoneFieldsProps<T>) {
  return (
    <div className="flex gap-2" dir={dir}>
      <Input
        className={clsx('shrink-0', dir === 'rtl' ? 'order-2' : 'order-first')}
        style={{ width: 112 }}
        inputMode="tel"
        autoComplete="tel-country-code"
        maxLength={6}
        placeholder="+216"
        aria-label="Country code"
        error={Boolean(codeError)}
        {...register(codeName)}
      />
      <Input
        className="min-w-0 flex-1"
        type="tel"
        inputMode="tel"
        autoComplete="tel-national"
        maxLength={14}
        placeholder={nationalPlaceholder}
        error={Boolean(nationalError)}
        {...register(nationalName)}
      />
    </div>
  )
}