import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { z } from 'zod'
import { Mail, ShieldCheck, Calendar, BadgeCheck, BadgeX } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { Card, CardContent } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { Alert } from '@/components/ui/Alert'
import { userApi, userQueryKeys } from '@/services/userApi'
import { useAuth } from '@/features/auth/AuthContext'
import { http } from '@/services/apiClient'
import { formatDateTime } from '@/lib/format'

const E164 = /^\+[1-9]\d{1,14}$/

const profileSchema = z.object({
  firstName: z.string().trim().min(1, '').max(100),
  lastName: z.string().trim().min(1, '').max(100),
  phone: z
    .string()
    .optional()
    .refine((v) => !v || v.trim() === '' || E164.test(v.trim()), ''),
})

type ProfileValues = z.infer<typeof profileSchema>

export default function ProfilePage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { user, setUser } = useAuth()
  const [notice, setNotice] = useState<string | null>(null)

  const me = useQuery({
    queryKey: userQueryKeys.me,
    queryFn: () => userApi.me(),
  })

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { firstName: '', lastName: '', phone: '' },
  })

  useEffect(() => {
    if (me.data) {
      reset({
        firstName: me.data.firstName,
        lastName: me.data.lastName,
        phone: '',
      })
    }
  }, [me.data, reset])

  const save = useMutation({
    mutationFn: (values: ProfileValues) =>
      userApi.updateProfile({
        firstName: values.firstName.trim(),
        lastName: values.lastName.trim(),
        phone: values.phone?.trim() || undefined,
      }),
    onSuccess: (updated) => {
      setUser(updated)
      queryClient.setQueryData(userQueryKeys.me, updated)
      setNotice(t('profile.updateSuccess'))
    },
    onError: (err) => setNotice(http.humanizeError(err)),
  })

  const data = me.data ?? user

  return (
    <div>
      <PageHeader title={t('profile.title')} subtitle={t('profile.subtitle')} />

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Profile card */}
        <div className="lg:col-span-2">
          <Card>
            <CardContent>
              {notice && (
                <div className="mb-4">
                  <Alert tone="success">{notice}</Alert>
                </div>
              )}

              <div className="flex items-center gap-4 pb-5 border-b border-border mb-5">
                <div className="h-14 w-14 rounded-full bg-primary-soft text-primary flex items-center justify-center text-lg font-bold">
                  {`${data?.firstName[0] ?? ''}${data?.lastName[0] ?? ''}`.toUpperCase()}
                </div>
                <div className="min-w-0">
                  <p className="text-lg font-bold text-heading">
                    {data?.firstName} {data?.lastName}
                  </p>
                  <p className="text-sm text-muted-fg flex items-center gap-1.5">
                    <Mail className="h-4 w-4" aria-hidden />
                    {data?.email}
                  </p>
                </div>
              </div>

              <form onSubmit={handleSubmit((v) => save.mutate(v))} className="space-y-4" noValidate>
                <div className="grid sm:grid-cols-2 gap-4">
                  <Field label={t('profile.firstName')} error={errors.firstName?.message}>
                    <Input maxLength={100} error={Boolean(errors.firstName)} {...register('firstName')} />
                  </Field>
                  <Field label={t('profile.lastName')} error={errors.lastName?.message}>
                    <Input maxLength={100} error={Boolean(errors.lastName)} {...register('lastName')} />
                  </Field>
                </div>
                <Field label={t('profile.phoneOptional')} hint="+33 6 12 34 56 78" error={errors.phone?.message}>
                  <Input inputMode="tel" placeholder="+33…" error={Boolean(errors.phone)} {...register('phone')} />
                </Field>
                <div className="flex justify-end pt-2">
                  <Button type="submit" loading={save.isPending}>
                    {t('profile.save')}
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        </div>

        {/* Meta column */}
        <div className="space-y-4">
          <Card>
            <CardContent className="space-y-4">
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-fg">{t('profile.emailVerified')}</span>
                {data?.emailVerified ? (
                  <Badge tone="success">
                    <BadgeCheck className="h-3.5 w-3.5" aria-hidden />
                    {t('common.yes')}
                  </Badge>
                ) : (
                  <Badge tone="warning">
                    <BadgeX className="h-3.5 w-3.5" aria-hidden />
                    {t('profile.emailNotVerified')}
                  </Badge>
                )}
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-fg">{t('profile.role')}</span>
                <Badge tone={data?.role === 'ADMIN' ? 'primary' : 'neutral'}>{t(`admin.roles.${data?.role}`)}</Badge>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-fg flex items-center gap-1.5">
                  <Calendar className="h-4 w-4" aria-hidden />
                  {t('profile.memberSince')}
                </span>
                <span className="text-sm font-medium text-foreground">
                  {data?.createdAt ? formatDateTime(data.createdAt) : '—'}
                </span>
              </div>
              {!data?.emailVerified && (
                <p className="text-xs text-muted-fg flex items-start gap-1.5">
                  <ShieldCheck className="h-4 w-4 shrink-0 mt-0.5" aria-hidden />
                  {t('profile.verificationHint')}
                </p>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
