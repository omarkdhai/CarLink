import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { Check, LogIn, QrCode, ScanLine, UserRound } from 'lucide-react'
import { Logo } from '@/components/layout/Logo'
import { Button, buttonClasses } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Alert } from '@/components/ui/Alert'
import { useAuth } from '@/features/auth/AuthContext'
import { stickerApi } from '@/services/stickerApi'
import { setNextAfterAuth } from '@/services/nextTarget'
import { http } from '@/services/apiClient'
import {
  activateStickerSchema,
  type ActivateStickerValues,
} from '@/features/stickers/stickerSchemas'
import { cn } from '@/lib/cn'

/**
 * The activation flow behind a scanned virgin sticker (/c/:token when its
 * state is UNBOUND or DEACTIVATED). Guests are sent to create/login an account
 * (remembering this sticker so they land back here); authenticated users fill
 * in their birth date + car details to bind the sticker.
 */
export default function StickerActivation({ token }: { token: string }) {
  const { t } = useTranslation()
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const today = new Date().toISOString().slice(0, 10)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ActivateStickerValues>({
    resolver: zodResolver(activateStickerSchema),
    defaultValues: {
      birthDate: '',
      licensePlate: '',
      nickname: '',
      brand: '',
      model: '',
      color: '',
    },
  })

  const activateMutation = useMutation({
    mutationFn: (values: ActivateStickerValues) =>
      stickerApi.activate(token, {
        birthDate: values.birthDate,
        licensePlate: values.licensePlate.trim(),
        nickname: values.nickname?.trim() || undefined,
        brand: values.brand?.trim() || undefined,
        model: values.model?.trim() || undefined,
        color: values.color?.trim() || undefined,
      }),
    onSuccess: () => setDone(true),
    onError: (err) => setError(http.humanizeError(err)),
  })

  // ---- Activated ----
  if (done) {
    return (
      <Shell>
        <Card>
          <Logo link={false} className="justify-center" />
          <div className="mt-6 text-center">
            <div className="mx-auto h-16 w-16 rounded-full bg-success-soft text-success flex items-center justify-center mb-4">
              <Check className="h-8 w-8" aria-hidden />
            </div>
            <h1 className="text-xl font-bold text-heading">{t('stickers.activated')}</h1>
            <p className="text-sm text-muted-fg mt-2">{t('stickers.activatedHint')}</p>
            <Button className="mt-6 w-full" onClick={() => navigate('/stickers', { replace: true })}>
              {t('stickers.viewMine')}
            </Button>
          </div>
        </Card>
      </Shell>
    )
  }

  // ---- Guests: create an account / log in, remembering this sticker ----
  if (!isAuthenticated) {
    return (
      <Shell>
        <Card>
          <Logo link={false} className="justify-center" />
          <div className="mt-6 text-center">
            <div className="mx-auto h-14 w-14 rounded-full bg-primary-soft text-primary flex items-center justify-center mb-4">
              <ScanLine className="h-7 w-7" aria-hidden />
            </div>
            <h1 className="text-xl font-bold text-heading">{t('contact.activateTitle')}</h1>
            <p className="text-sm text-muted-fg mt-2">{t('contact.activateGuestHint')}</p>
          </div>
          <div className="mt-6 grid gap-3">
            <Button
              className="w-full justify-center"
              onClick={() => {
                setNextAfterAuth(`/c/${token}`)
                navigate('/register')
              }}
            >
              <UserRound className="h-4 w-4" aria-hidden />
              {t('auth.createAccount')}
            </Button>
            <Link
              to="/login"
              onClick={() => setNextAfterAuth(`/c/${token}`)}
              className={cn(buttonClasses({ variant: 'outline' }), 'w-full justify-center')}
            >
              <LogIn className="h-4 w-4" aria-hidden />
              {t('auth.signIn')}
            </Link>
          </div>
          <p className="flex items-center justify-center gap-1.5 text-xs text-muted-fg mt-5">
            <QrCode className="h-3.5 w-3.5" aria-hidden />
            {t('contact.activatePrivacy')}
          </p>
        </Card>
      </Shell>
    )
  }

  // ---- Authenticated: claim the sticker with car details ----
  return (
    <Shell>
      <Card>
        <Logo link={false} className="justify-center" />
        <div className="mt-6 text-center">
          <div className="mx-auto h-14 w-14 rounded-full bg-primary-soft text-primary flex items-center justify-center mb-4">
            <ScanLine className="h-7 w-7" aria-hidden />
          </div>
          <h1 className="text-xl font-bold text-heading">{t('contact.activateTitle')}</h1>
          <p className="text-sm text-muted-fg mt-2">{t('contact.activateHint')}</p>
        </div>

        {error && (
          <div className="mt-4">
            <Alert tone="error">{error}</Alert>
          </div>
        )}

        <form
          onSubmit={handleSubmit((values) => activateMutation.mutate(values))}
          className="mt-5 space-y-4"
          noValidate
        >
          <Field label={t('stickers.birthDate')} error={errors.birthDate?.message}>
            <Input
              type="date"
              max={today}
              error={Boolean(errors.birthDate)}
              {...register('birthDate')}
            />
          </Field>

          <Field label={t('stickers.licensePlate')} hint={t('stickers.licensePlateHint')} error={errors.licensePlate?.message}>
            <Input
              placeholder={t('vehicle.licensePlatePlaceholder')}
              maxLength={20}
              autoCapitalize="characters"
              error={Boolean(errors.licensePlate)}
              {...register('licensePlate')}
            />
          </Field>

          <Field label={t('vehicle.nickname')} optional={t('common.optional')} error={errors.nickname?.message}>
            <Input
              placeholder={t('vehicle.nicknamePlaceholder')}
              maxLength={100}
              error={Boolean(errors.nickname)}
              {...register('nickname')}
            />
          </Field>

          <div className="grid sm:grid-cols-2 gap-4">
            <Field label={t('vehicle.brand')} optional={t('common.optional')} error={errors.brand?.message}>
              <Input
                placeholder={t('vehicle.brandPlaceholder')}
                maxLength={100}
                error={Boolean(errors.brand)}
                {...register('brand')}
              />
            </Field>
            <Field label={t('vehicle.model')} optional={t('common.optional')} error={errors.model?.message}>
              <Input
                placeholder={t('vehicle.modelPlaceholder')}
                maxLength={100}
                error={Boolean(errors.model)}
                {...register('model')}
              />
            </Field>
          </div>

          <Field label={t('vehicle.color')} optional={t('common.optional')} error={errors.color?.message}>
            <Input
              placeholder={t('vehicle.colorPlaceholder')}
              maxLength={50}
              error={Boolean(errors.color)}
              {...register('color')}
            />
          </Field>

          <Button type="submit" className="w-full" loading={activateMutation.isPending}>
            {t('stickers.activate')}
          </Button>
        </form>
      </Card>
    </Shell>
  )
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-dvh bg-gradient-to-b from-primary-soft/60 to-background flex items-center justify-center p-4">
      <div className="w-full max-w-md">{children}</div>
    </div>
  )
}

function Card({ children }: { children: React.ReactNode }) {
  return (
    <div className="bg-surface rounded-2xl border border-border shadow-card p-6 sm:p-7">{children}</div>
  )
}