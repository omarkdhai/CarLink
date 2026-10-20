import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { Check, Loader2, Send } from 'lucide-react'
import { Input } from '@/components/ui/Input'
import { Textarea } from '@/components/ui/Textarea'
import { Field } from '@/components/ui/Field'
import { Alert } from '@/components/ui/Alert'
import apiClient, { http } from '@/services/apiClient'

const KICKER =
  'text-[0.72rem] font-semibold uppercase leading-4 tracking-[0.14em] text-primary/70 rtl:tracking-[0.04em]'

interface ContactForm {
  name: string
  email: string
  subject: string
  phone: string
  message: string
}

/** Contact us — visitors reach the team's mailbox; fields are never stored. */
export default function ContactPage() {
  const { t } = useTranslation()

  const [form, setForm] = useState<ContactForm>({
    name: '',
    email: '',
    subject: '',
    phone: '',
    message: '',
  })
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof ContactForm, string>>>({})

  const submitMutation = useMutation({
    mutationFn: () =>
      apiClient.post('/public/contact', {
        name: form.name.trim(),
        email: form.email.trim(),
        subject: form.subject.trim(),
        phone: form.phone.trim() || undefined,
        message: form.message.trim(),
      }),
    onError: (err) => setFieldErrors({ message: http.humanizeError(err) }),
  })

  function set<K extends keyof ContactForm>(key: K, value: string) {
    setForm((prev) => ({ ...prev, [key]: value }))
    setFieldErrors((prev) => ({ ...prev, [key]: undefined }))
  }

  function validate(): boolean {
    const errors: Partial<Record<keyof ContactForm, string>> = {}
    if (!form.name.trim()) errors.name = t('contact.nameRequired')
    if (!form.email.trim()) errors.email = t('contact.emailRequired')
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) errors.email = t('contact.emailInvalid')
    if (!form.subject.trim()) errors.subject = t('contact.subjectRequired')
    if (!form.message.trim()) errors.message = t('contact.messageRequired')
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!validate()) return
    submitMutation.mutate()
  }

  const inputError = (k: keyof ContactForm) => Boolean(fieldErrors[k])

  return (
    <div className="pt-12 pb-16 sm:pt-16 sm:pb-20 lg:pb-24">
      <div className="mx-auto w-[min(1120px,calc(100%_-_1.5rem))]">
        <div className="overflow-hidden rounded-[1.5rem] border border-[#f1f5f9] bg-white shadow-[0_8px_24px_rgba(26,26,26,0.06)] md:rounded-[2rem]">
          <div className="flex flex-col gap-6 px-5 py-6 sm:px-6 sm:py-7 md:gap-8 md:px-8 md:py-10 lg:gap-10 lg:px-12 lg:py-14 xl:px-14 xl:py-16">
            {/* Header */}
            <div className="max-w-3xl space-y-3 lg:space-y-4">
              <p className={KICKER}>{t('contact.pageBadge')}</p>
              <h1 className="text-3xl font-bold tracking-tight text-heading sm:text-4xl md:text-6xl">
                {t('contact.pageTitle')}
              </h1>
              <p className="max-w-2xl text-sm leading-7 text-muted-fg sm:text-base md:text-lg">
                {t('contact.pageSubtitle')}
              </p>
            </div>

            {/* Form */}
            <div className="mx-auto w-full max-w-3xl">
              {submitMutation.isSuccess ? (
                <div className="rounded-2xl border border-success/25 bg-success-soft/60 p-6 sm:p-8">
                  <div className="flex items-start gap-3">
                    <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-success-soft text-success">
                      <Check className="h-5 w-5" aria-hidden />
                    </span>
                    <div>
                      <h2 className="text-lg font-semibold tracking-tight text-heading sm:text-xl">
                        {t('contact.sendSuccess')}
                      </h2>
                      <p className="mt-1 text-sm leading-7 text-muted-fg sm:text-base">
                        {t('contact.sendSuccessHint')}
                      </p>
                    </div>
                  </div>
                </div>
              ) : (
                <form
                  onSubmit={handleSubmit}
                  className="space-y-5 rounded-2xl border border-border bg-background p-5 sm:p-7"
                  noValidate
                >
                  <div className="grid gap-5 sm:grid-cols-2">
                    <Field label={t('contact.nameLabel')} error={fieldErrors.name}>
                      <Input
                        value={form.name}
                        onChange={(e) => set('name', e.target.value)}
                        placeholder={t('contact.namePlaceholder')}
                        error={inputError('name')}
                        className="h-12 rounded-xl"
                      />
                    </Field>
                    <Field label={t('contact.phoneLabel')} optional={t('common.optional')} error={fieldErrors.phone}>
                      <Input
                        value={form.phone}
                        onChange={(e) => set('phone', e.target.value)}
                        placeholder={t('contact.phonePlaceholder')}
                        type="tel"
                        error={inputError('phone')}
                        className="h-12 rounded-xl"
                      />
                    </Field>
                  </div>

                  <Field label={t('contact.emailLabel')} error={fieldErrors.email}>
                    <Input
                      value={form.email}
                      onChange={(e) => set('email', e.target.value)}
                      placeholder={t('contact.emailPlaceholder')}
                      type="email"
                      error={inputError('email')}
                      className="h-12 rounded-xl"
                    />
                  </Field>

                  <Field label={t('contact.subjectLabel')} error={fieldErrors.subject}>
                    <Input
                      value={form.subject}
                      onChange={(e) => set('subject', e.target.value)}
                      placeholder={t('contact.subjectPlaceholder')}
                      error={inputError('subject')}
                      className="h-12 rounded-xl"
                    />
                  </Field>

                  <Field label={t('contact.messageLabel')} error={fieldErrors.message}>
                    <Textarea
                      value={form.message}
                      onChange={(e) => set('message', e.target.value)}
                      placeholder={t('contact.messagePlaceholder')}
                      rows={6}
                      error={inputError('message')}
                      className="rounded-xl"
                    />
                  </Field>

                  <div className="flex sm:justify-end">
                    <button
                      type="submit"
                      disabled={submitMutation.isPending}
                      className="inline-flex h-[50px] w-full items-center justify-center gap-2 rounded-full bg-primary px-7 text-sm font-bold text-white transition-colors hover:bg-primary-hover disabled:pointer-events-none disabled:opacity-50 sm:w-auto"
                    >
                      {submitMutation.isPending ? (
                        <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                      ) : (
                        <Send className="h-4 w-4" aria-hidden />
                      )}
                      {t('contact.sendSubmit')}
                    </button>
                  </div>

                  {submitMutation.isError && (
                    <Alert tone="error" title={t('contact.sendFailed')}>
                      {http.humanizeError(submitMutation.error)}
                    </Alert>
                  )}
                </form>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
