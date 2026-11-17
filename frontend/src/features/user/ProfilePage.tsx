import { useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { z } from 'zod'
import { BadgeCheck, BadgeX, Calendar, ChevronRight, EyeOff, LogOut, Mail, PhoneOff, Settings, ShieldCheck } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { PhoneFields } from '@/components/ui/PhoneFields'
import { Badge } from '@/components/ui/Badge'
import { Alert } from '@/components/ui/Alert'
import { useToast } from '@/components/ui/Toast'
import { SectionCard } from '@/features/account/OwnerUI'
import { userApi, userQueryKeys } from '@/services/userApi'
import { useAuth } from '@/features/auth/AuthContext'
import { http } from '@/services/apiClient'
import { joinPhone, splitPhone, DEFAULT_PHONE_CODE } from '@/lib/phone'
import { formatDate } from '@/lib/format'

const phoneCodeRegex = /^\+[1-9][0-9]{0,3}$/
const nationalRegex = /^[0-9]{6,14}$/

const profileSchema = z.object({
  firstName: z.string().trim().min(1, '').max(100),
  lastName: z.string().trim().min(1, '').max(100),
  phoneCode: z.string().refine((v) => v.trim() === '' || phoneCodeRegex.test(v.trim()), ''),
  phoneNumber: z.string().optional().refine((v) => !v || v.trim() === '' || nationalRegex.test(v.trim()), ''),
})

type ProfileValues = z.infer<typeof profileSchema>

export default function ProfilePage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const toast = useToast()
  const { user, setUser, isAdmin, signOut } = useAuth()

  const me = useQuery({
    queryKey: userQueryKeys.me,
    queryFn: () => userApi.me(),
  })

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { firstName: '', lastName: '', phoneCode: DEFAULT_PHONE_CODE, phoneNumber: '' },
  })

  useEffect(() => {
    if (me.data) {
      const { code, national } = splitPhone(me.data.phone)
      reset({
        firstName: me.data.firstName,
        lastName: me.data.lastName,
        phoneCode: code,
        phoneNumber: national,
      })
    }
  }, [me.data, reset])

  const save = useMutation({
    mutationFn: (values: ProfileValues) =>
      userApi.updateProfile({
        firstName: values.firstName.trim(),
        lastName: values.lastName.trim(),
        phone: joinPhone(values.phoneCode, values.phoneNumber) || undefined,
      }),
    onSuccess: (updated) => {
      setUser(updated)
      queryClient.setQueryData(userQueryKeys.me, updated)
      toast.show(t('profile.updateSuccess'))
    },
  })

  const data = me.data ?? user
  const missingPhone = me.isSuccess && !me.data.phone
  const initials = `${data?.firstName?.[0] ?? ''}${data?.lastName?.[0] ?? ''}`.toUpperCase()

  async function handleSignOut() {
    await signOut()
    navigate('/login', { replace: true })
  }

  return (
    <div className="space-y-6 sm:space-y-8">
      <PageHeader title={t('profile.title')} subtitle={t('profile.subtitle')} />

      {/* Identity */}
      <section className="bg-surface border border-border rounded-2xl shadow-card p-5 sm:p-6 flex flex-col sm:flex-row sm:items-center gap-4 sm:gap-5">
        <span className="h-16 w-16 shrink-0 rounded-full bg-primary-soft text-primary flex items-center justify-center text-xl font-bold" aria-hidden>
          {initials || '?'}
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="text-xl font-bold text-heading truncate">
            {data?.firstName} {data?.lastName}
          </h2>
          <p className="text-sm text-muted-fg mt-1 flex items-center gap-1.5 min-w-0">
            <Mail className="h-4 w-4 shrink-0" aria-hidden />
            <span className="truncate">{data?.email}</span>
          </p>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            {data?.emailVerified ? (
              <Badge tone="success">
                <BadgeCheck className="h-3.5 w-3.5 me-1" aria-hidden />
                {t('profile.emailVerified')}
              </Badge>
            ) : (
              <Badge tone="warning">
                <BadgeX className="h-3.5 w-3.5 me-1" aria-hidden />
                {t('profile.emailNotVerified')}
              </Badge>
            )}
            {data?.createdAt && (
              <span className="inline-flex items-center gap-1.5 text-xs text-muted-fg">
                <Calendar className="h-3.5 w-3.5" aria-hidden />
                {t('profile.memberSinceDate', { date: formatDate(data.createdAt) })}
              </span>
            )}
          </div>
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-3 lg:items-start">
        <SectionCard title={t('profile.personalInfo')} description={t('profile.personalInfoHint')} className="lg:col-span-2">
          <form onSubmit={handleSubmit((v) => save.mutate(v))} className="space-y-5" noValidate>
            {save.isError && <Alert tone="error">{http.humanizeError(save.error)}</Alert>}
            {missingPhone && (
              <div className="flex items-start gap-3 rounded-xl border border-destructive/30 bg-destructive-soft p-4">
                <PhoneOff className="h-5 w-5 shrink-0 text-destructive" aria-hidden />
                <div>
                  <p className="text-sm font-semibold text-heading">{t('notifications.notice.addPhone')}</p>
                  <p className="text-sm text-muted-fg mt-1 leading-relaxed">{t('notifications.notice.addPhoneHint')}</p>
                </div>
              </div>
            )}
            <div className="grid sm:grid-cols-2 gap-5">
              <Field label={t('profile.firstName')} error={errors.firstName?.message}>
                <Input maxLength={100} autoComplete="given-name" error={Boolean(errors.firstName)} {...register('firstName')} />
              </Field>
              <Field label={t('profile.lastName')} error={errors.lastName?.message}>
                <Input maxLength={100} autoComplete="family-name" error={Boolean(errors.lastName)} {...register('lastName')} />
              </Field>
            </div>
            <Field
              label={t('profile.phone')}
              hint={t('profile.phoneHint')}
              error={errors.phoneCode?.message || errors.phoneNumber?.message}
            >
              <PhoneFields
                register={register}
                codeName="phoneCode"
                nationalName="phoneNumber"
                codeError={errors.phoneCode?.message}
                nationalError={errors.phoneNumber?.message}
                nationalPlaceholder={t('profile.phoneNumberPlaceholder')}
              />
            </Field>
            <div className="flex flex-col-reverse sm:flex-row sm:items-center sm:justify-end gap-3 pt-1">
              {isDirty && <span className="text-sm text-muted-fg sm:me-auto">{t('profile.unsaved')}</span>}
              <Button type="submit" loading={save.isPending} disabled={!isDirty}>
                {t('profile.save')}
              </Button>
            </div>
          </form>
        </SectionCard>

        <div className="space-y-6">
          <section className="bg-surface border border-border rounded-2xl shadow-card p-5 sm:p-6">
            <div className="flex items-start gap-3">
              <span className="h-10 w-10 shrink-0 rounded-xl bg-success-soft text-success flex items-center justify-center" aria-hidden>
                <EyeOff className="h-5 w-5" />
              </span>
              <div>
                <h2 className="font-bold text-heading">{t('profile.privacyTitle')}</h2>
                <p className="text-sm text-muted-fg mt-1 leading-relaxed">{t('profile.privacyHint')}</p>
              </div>
            </div>
          </section>

          <nav aria-label={t('nav.account')} className="bg-surface border border-border rounded-2xl shadow-card overflow-hidden divide-y divide-border">
            <AccountLink to="/settings" icon={Settings} label={t('nav.settings')} />
            {isAdmin && <AccountLink to="/admin" icon={ShieldCheck} label={t('nav.adminDashboard')} />}
            <button
              type="button"
              onClick={handleSignOut}
              className="w-full flex items-center gap-3 px-5 h-14 text-sm font-semibold text-destructive hover:bg-destructive-soft transition-colors"
            >
              <LogOut className="h-5 w-5" aria-hidden />
              {t('auth.logout')}
            </button>
          </nav>
        </div>
      </div>
      {toast.node}
    </div>
  )
}

function AccountLink({ to, icon: Icon, label }: { to: string; icon: typeof Settings; label: string }) {
  return (
    <Link to={to} className="flex items-center gap-3 px-5 h-14 text-sm font-semibold text-foreground hover:bg-muted/50 transition-colors">
      <Icon className="h-5 w-5 text-muted-fg" aria-hidden />
      <span className="flex-1">{label}</span>
      <ChevronRight className="h-4 w-4 text-muted-fg rtl:rotate-180" aria-hidden />
    </Link>
  )
}
