import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { Mail, Send, MessageCircle } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Textarea } from '@/components/ui/Textarea'
import { Field } from '@/components/ui/Field'
import { Alert } from '@/components/ui/Alert'
import apiClient, { http } from '@/services/apiClient'

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
    <div>
      {/* Header */}
      <section className="bg-white border-b border-border mt-[-40px] py-16 sm:py-20">
        <div className="max-w-2xl mx-auto px-4 sm:px-6 text-center">
          <span className="inline-flex items-center gap-2 rounded-full bg-primary-soft text-primary text-sm font-semibold px-3 py-1 mb-4">
            <Mail className="h-4 w-4" aria-hidden />
            {t('contact.pageBadge')}
          </span>
          <h1 className="text-3xl sm:text-5xl font-extrabold text-heading leading-tight tracking-tight">{t('contact.pageTitle')}</h1>
          <p className="text-muted-fg mt-5 max-w-2xl mx-auto text-lg">{t('contact.pageSubtitle')}</p>
        </div>
      </section>

      {/* Form */}
      <section className="py-16 sm:py-20">
        <div className="max-w-xl mx-auto px-4 sm:px-6">
          {submitMutation.isSuccess ? (
            <Alert tone="success" title={t('contact.sendSuccess')}>
              {t('contact.sendSuccessHint')}
            </Alert>
          ) : (
            <form onSubmit={handleSubmit} className="bg-white rounded-2xl border border-border p-6 sm:p-8 space-y-5" noValidate>
              <div className="grid sm:grid-cols-2 gap-5">
                <Field label={t('contact.nameLabel')} error={fieldErrors.name}>
                  <Input
                    value={form.name}
                    onChange={(e) => set('name', e.target.value)}
                    placeholder={t('contact.namePlaceholder')}
                    error={inputError('name')}
                  />
                </Field>
                <Field label={t('contact.phoneLabel')} optional={t('common.optional')} error={fieldErrors.phone}>
                  <Input
                    value={form.phone}
                    onChange={(e) => set('phone', e.target.value)}
                    placeholder={t('contact.phonePlaceholder')}
                    type="tel"
                    error={inputError('phone')}
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
                />
              </Field>

              <Field label={t('contact.subjectLabel')} error={fieldErrors.subject}>
                <Input
                  value={form.subject}
                  onChange={(e) => set('subject', e.target.value)}
                  placeholder={t('contact.subjectPlaceholder')}
                  error={inputError('subject')}
                />
              </Field>

              <Field label={t('contact.messageLabel')} error={fieldErrors.message}>
                <Textarea
                  value={form.message}
                  onChange={(e) => set('message', e.target.value)}
                  placeholder={t('contact.messagePlaceholder')}
                  rows={6}
                  error={inputError('message')}
                />
              </Field>

              <Button type="submit" size="lg" fullWidth loading={submitMutation.isPending} disabled={submitMutation.isPending}>
                <Send className="h-4 w-4" aria-hidden />
                {t('contact.sendSubmit')}
              </Button>

              {submitMutation.isError && (
                <Alert tone="error" title={t('contact.sendFailed')} className="mt-2">
                  {http.humanizeError(submitMutation.error)}
                </Alert>
              )}
            </form>
          )}
        </div>
      </section>
    </div>
  )
}