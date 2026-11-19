import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { ArrowRight, Clock, HelpCircle, Loader2, PackageSearch, Send, ShieldCheck } from 'lucide-react'
import { Input } from '@/components/ui/Input'
import { Textarea } from '@/components/ui/Textarea'
import { Field } from '@/components/ui/Field'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { PageIntro, PublicContainer } from '@/components/shared/PublicPage'
import apiClient, { http } from '@/services/apiClient'
import { cn } from '@/lib/cn'
import { SuccessMark } from '@/components/shared/SuccessMark'
import { useCaptcha } from '@/components/shared/useCaptcha'

interface ContactForm {
  name: string
  email: string
  subject: string
  phone: string
  message: string
}

const EMPTY: ContactForm = { name: '', email: '', subject: '', phone: '', message: '' }
const TOPICS = ['order', 'sticker', 'account', 'partnership', 'other'] as const

/** Contact us — visitors reach the team's mailbox. */
export default function ContactPage() {
  const { t } = useTranslation()

  const [form, setForm] = useState<ContactForm>(EMPTY)
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof ContactForm, string>>>({})

  const captcha = useCaptcha()
  const submitMutation = useMutation({
    mutationFn: () =>
      apiClient.post('/public/contact', {
        name: form.name.trim(),
        email: form.email.trim(),
        subject: form.subject.trim(),
        phone: form.phone.trim() || undefined,
        message: form.message.trim(),
      }, { headers: captcha.headers() }),
    onSettled: () => captcha.reset(),
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
    <PublicContainer>
      <PageIntro eyebrow={t('contact.pageBadge')} title={t('contact.pageTitle')} lead={t('contact.pageSubtitle')} />

      <div className="motion-stagger grid gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.6fr)] lg:gap-12">
        {/* Help column */}
        <aside className="motion-stagger space-y-4 lg:order-none order-last">
          <InfoCard icon={Clock} tone="bg-primary-soft text-primary" title={t('contact.responseTitle')} text={t('contact.responseText')} />
          <InfoCard icon={PackageSearch} tone="bg-accent-soft text-accent" title={t('contact.orderHelpTitle')} text={t('contact.orderHelpText')} />
          <Link
            to="/#faq"
            className="hover-lift group flex items-start gap-4 rounded-2xl border border-border bg-surface p-5 hover:border-primary/40"
          >
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-success-soft text-success" aria-hidden>
              <HelpCircle className="h-5 w-5" />
            </span>
            <span className="min-w-0">
              <span className="block font-semibold text-heading">{t('contact.faqTitle')}</span>
              <span className="mt-1 block text-sm leading-relaxed text-muted-fg">{t('contact.faqText')}</span>
              <span className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-primary">
                {t('contact.faqCta')}
                <ArrowRight className="nudge h-4 w-4 rtl:rotate-180" aria-hidden />
              </span>
            </span>
          </Link>
          <p className="flex items-start gap-2 px-1 text-sm leading-relaxed text-muted-fg">
            <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-success" aria-hidden />
            {t('contact.formPrivacy')}
          </p>
        </aside>

        {/* Form */}
        <div>
          {submitMutation.isSuccess ? (
            <div className="rounded-3xl border border-success/25 bg-success-soft/60 p-6 sm:p-10 text-center" role="status">
              <SuccessMark className="h-14 w-14" onSurface />
              <h2 className="mt-5 text-2xl font-bold text-heading">{t('contact.sendSuccess')}</h2>
              <p className="mx-auto mt-2 max-w-[45ch] text-base leading-relaxed text-muted-fg">{t('contact.sendSuccessHint')}</p>
              <Button
                variant="outline"
                className="mt-6"
                onClick={() => {
                  setForm(EMPTY)
                  submitMutation.reset()
                }}
              >
                {t('contact.sendAnother')}
              </Button>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-6 rounded-3xl border border-border bg-surface p-5 shadow-card sm:p-8" noValidate>
              <div className="grid gap-5 sm:grid-cols-2">
                <Field label={t('contact.nameLabel')} error={fieldErrors.name}>
                  <Input
                    value={form.name}
                    onChange={(e) => set('name', e.target.value)}
                    placeholder={t('contact.namePlaceholder')}
                    autoComplete="name"
                    error={inputError('name')}
                  />
                </Field>
                <Field label={t('contact.emailLabel')} error={fieldErrors.email}>
                  <Input
                    value={form.email}
                    onChange={(e) => set('email', e.target.value)}
                    placeholder={t('contact.emailPlaceholder')}
                    type="email"
                    autoComplete="email"
                    error={inputError('email')}
                  />
                </Field>
              </div>

              <Field label={t('contact.phoneLabel')} optional={t('common.optional')} error={fieldErrors.phone}>
                <Input
                  value={form.phone}
                  onChange={(e) => set('phone', e.target.value)}
                  placeholder={t('contact.phonePlaceholder')}
                  type="tel"
                  autoComplete="tel"
                  error={inputError('phone')}
                />
              </Field>

              <div>
                <Field label={t('contact.subjectLabel')} error={fieldErrors.subject}>
                  <Input
                    value={form.subject}
                    onChange={(e) => set('subject', e.target.value)}
                    placeholder={t('contact.subjectPlaceholder')}
                    error={inputError('subject')}
                  />
                </Field>
                <div className="mt-3 flex flex-wrap gap-2" role="group" aria-label={t('contact.topicsLabel')}>
                  {TOPICS.map((topic) => {
                    const label = t(`contact.topics.${topic}`)
                    const active = form.subject === label
                    return (
                      <button
                        key={topic}
                        type="button"
                        onClick={() => set('subject', label)}
                        aria-pressed={active}
                        className={cn(
                          'h-9 rounded-full border px-3.5 text-sm font-medium transition-colors',
                          active ? 'border-primary bg-primary-soft text-primary' : 'border-border text-muted-fg hover:border-primary/40 hover:text-foreground',
                        )}
                      >
                        {label}
                      </button>
                    )
                  })}
                </div>
              </div>

              <Field label={t('contact.messageLabel')} error={fieldErrors.message}>
                <Textarea
                  value={form.message}
                  onChange={(e) => set('message', e.target.value)}
                  placeholder={t('contact.messagePlaceholder')}
                  rows={6}
                  error={inputError('message')}
                />
              </Field>

              {submitMutation.isError && (
                <Alert tone="error" title={t('contact.sendFailed')}>
                  {http.humanizeError(submitMutation.error)}
                </Alert>
              )}

              {captcha.widget}
              <div className="flex flex-col-reverse gap-3 sm:flex-row sm:items-center sm:justify-between">
                <p className="text-sm text-muted-fg">{t('contact.requiredNote')}</p>
                <button
                  type="submit"
                  disabled={submitMutation.isPending || (captcha.required && !captcha.token)}
                  className="inline-flex h-12 w-full items-center justify-center gap-2 rounded-full bg-primary px-7 text-base font-semibold text-white transition-colors hover:bg-primary-hover disabled:pointer-events-none disabled:opacity-50 sm:w-auto"
                >
                  {submitMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Send className="h-4 w-4" aria-hidden />}
                  {t('contact.sendSubmit')}
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </PublicContainer>
  )
}

function InfoCard({ icon: Icon, tone, title, text }: { icon: typeof Clock; tone: string; title: string; text: string }) {
  return (
    <div className="flex items-start gap-4 rounded-2xl border border-border bg-surface p-5">
      <span className={cn('grid h-10 w-10 shrink-0 place-items-center rounded-xl', tone)} aria-hidden>
        <Icon className="h-5 w-5" />
      </span>
      <div className="min-w-0">
        <p className="font-semibold text-heading">{title}</p>
        <p className="mt-1 text-sm leading-relaxed text-muted-fg">{text}</p>
      </div>
    </div>
  )
}
