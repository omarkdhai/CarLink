import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import {
  Camera,
  CameraOff,
  Check,
  LogIn,
  QrCode,
  ScanLine,
  ShieldCheck,
  UserRound,
} from 'lucide-react'
import { Logo } from '@/components/layout/Logo'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { PhoneFields } from '@/components/ui/PhoneFields'
import { Alert } from '@/components/ui/Alert'
import { useAuth } from '@/features/auth/AuthContext'
import { registerSchema, type RegisterValues } from '@/features/auth/authSchemas'
import {
  activateStickerSchema,
  type ActivateStickerValues,
} from '@/features/stickers/stickerSchemas'
import { stickerApi } from '@/services/stickerApi'
import { toApiError } from '@/services/errors'
import { http } from '@/services/apiClient'
import { joinPhone } from '@/lib/phone'
import { useQrScanner } from '@/features/stickers/useQrScanner'
import { cn } from '@/lib/cn'

type Step = 'entry' | 'claimable' | 'details' | 'done'

/**
 * Public sticker registration entry: scan the printed QR with the camera or
 * type the short activation code, confirm it's still claimable, then create an
 * account / sign in and link the sticker to a car. The code is carried inline
 * across steps so the visitor never loses their place.
 */
export default function RegisterStickerPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()
  const [step, setStep] = useState<Step>('entry')
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)

  const resolveMutation = useMutation({
    mutationFn: (value: string) => stickerApi.resolve(value),
    onSuccess: (data) => {
      if (data.state === 'BOUND') {
        setError('bound')
      } else {
        setError(null)
        setStep('claimable')
      }
    },
    onError: (err) => setError(errCode(err)),
  })

  function submitCode(value: string) {
    const trimmed = value.trim()
    if (!trimmed) return
    setError(null)
    setCode(trimmed)
    resolveMutation.mutate(trimmed)
  }

  // Already signed in: skip the auth step and go straight to car details.
  const effectiveStep: Step = isAuthenticated && step === 'claimable' ? 'details' : step

  return (
    <Shell>
      <Card>
        <Logo link={false} className="justify-center" />

        {effectiveStep === 'entry' && (
          <EntryStep
            initialCode={code}
            error={error}
            loading={resolveMutation.isPending}
            onSubmit={submitCode}
          />
        )}

        {effectiveStep === 'claimable' && (
          <AuthStep
            code={code}
            error={error}
            setError={setError}
            onAuthed={() => {
              setError(null)
              setStep('details')
            }}
          />
        )}

        {effectiveStep === 'details' && (
          <DetailsStep
            code={code}
            onDone={() => setStep('done')}
          />
        )}

        {effectiveStep === 'done' && (
          <DoneStep onViewStickers={() => navigate('/stickers', { replace: true })} />
        )}
      </Card>
      <p className="flex items-center justify-center gap-1.5 text-xs text-muted-fg mt-5">
        <ShieldCheck className="h-3.5 w-3.5" aria-hidden />
        {t('contact.activatePrivacy')}
      </p>
    </Shell>
  )
}

/* --------------------------------- entry ---------------------------------- */

function EntryStep({
  initialCode,
  error,
  loading,
  onSubmit,
}: {
  initialCode: string
  error: string | null
  loading: boolean
  onSubmit: (value: string) => void
}) {
  const { t } = useTranslation()
  const [manual, setManual] = useState(initialCode)

  const scanner = useQrScanner((text) => {
    const parsed = extractCode(text)
    setManual(parsed)
    onSubmit(parsed)
  })

  const scanning = scanner.status === 'scanning' || scanner.status === 'starting'

  return (
    <>
      <div className="mt-6 text-center">
        <div className="mx-auto h-14 w-14 rounded-full bg-primary-soft text-primary flex items-center justify-center mb-4">
          <ScanLine className="h-7 w-7" aria-hidden />
        </div>
        <h1 className="text-xl font-bold text-heading">{t('registerSticker.title')}</h1>
        <p className="text-sm text-muted-fg mt-2">{t('registerSticker.subtitle')}</p>
      </div>

      {error === 'bound' && (
        <div className="mt-4">
          <Alert tone="warning">{t('registerSticker.alreadyBound')}</Alert>
        </div>
      )}
      {error && error !== 'bound' && (
        <div className="mt-4">
          <Alert tone="error">
            {error === 'notFound' ? t('registerSticker.notFound') : t('errors.generic')}
          </Alert>
        </div>
      )}

      {/* Camera scan */}
      <div className="mt-6">
        <div
          className={cn(
            'relative aspect-square w-full overflow-hidden rounded-2xl border border-border bg-black/90',
            !scanning && 'flex items-center justify-center',
          )}
        >
          <video
            ref={scanner.videoRef}
            className={cn('h-full w-full object-cover', !scanning && 'hidden')}
            muted
            playsInline
          />
          {!scanning && (
            <div className="text-center px-6">
              <Camera className="h-10 w-10 text-white/70 mx-auto" aria-hidden />
              <p className="text-xs text-white/60 mt-2">{t('registerSticker.cameraIdle')}</p>
            </div>
          )}
        </div>

        {scanner.status === 'error' && (
          <div className="mt-3">
            <Alert tone="error">{t('registerSticker.cameraDenied')}</Alert>
          </div>
        )}

        <Button
          type="button"
          variant={scanning ? 'outline' : 'primary'}
          className="w-full mt-3"
          onClick={() => (scanning ? scanner.stop() : void scanner.start())}
        >
          {scanning ? (
            <>
              <CameraOff className="h-4 w-4" aria-hidden />
              {t('registerSticker.stopCamera')}
            </>
          ) : (
            <>
              <Camera className="h-4 w-4" aria-hidden />
              {t('registerSticker.scanQr')}
            </>
          )}
        </Button>
      </div>

      <div className="flex items-center gap-3 my-5">
        <span className="h-px flex-1 bg-border" />
        <span className="text-xs text-muted-fg">{t('registerSticker.or')}</span>
        <span className="h-px flex-1 bg-border" />
      </div>

      {/* Manual code */}
      <form
        onSubmit={(e) => {
          e.preventDefault()
          onSubmit(manual)
        }}
        className="space-y-4"
      >
        <Field label={t('registerSticker.codeLabel')} hint={t('registerSticker.codeHint')}>
          <Input
            value={manual}
            onChange={(e) => setManual(e.target.value.toUpperCase())}
            placeholder={t('registerSticker.codePlaceholder')}
            autoCapitalize="characters"
            spellCheck={false}
            maxLength={16}
          />
        </Field>
        <Button type="submit" className="w-full" loading={loading} disabled={!manual.trim()}>
          {t('registerSticker.continue')}
        </Button>
      </form>
    </>
  )
}

/* --------------------------------- auth ----------------------------------- */

function AuthStep({
  code,
  error,
  setError,
  onAuthed,
}: {
  code: string
  error: string | null
  setError: (v: string | null) => void
  onAuthed: () => void
}) {
  const { t } = useTranslation()
  const { signIn, signUp } = useAuth()
  const [mode, setMode] = useState<'create' | 'login'>('create')
  const [submitting, setSubmitting] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { email: '', password: '', firstName: '', lastName: '', phoneCode: '+216', phoneNumber: '' },
  })

  const [loginValues, setLoginValues] = useState({ email: '', password: '' })

  async function onCreate(values: RegisterValues) {
    setSubmitting(true)
    setError(null)
    try {
      await signUp({
        email: values.email,
        password: values.password,
        firstName: values.firstName,
        lastName: values.lastName,
        phone: joinPhone(values.phoneCode, values.phoneNumber) || undefined,
        stickerCode: code,
      })
      onAuthed()
    } catch (err) {
      setError(errCode(err))
    } finally {
      setSubmitting(false)
    }
  }

  async function onLogin(e: React.FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await signIn(loginValues.email, loginValues.password)
      onAuthed()
    } catch (err) {
      setError(errCode(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <div className="mt-6 text-center">
        <div className="mx-auto h-14 w-14 rounded-full bg-success-soft text-success flex items-center justify-center mb-4">
          <Check className="h-7 w-7" aria-hidden />
        </div>
        <h1 className="text-xl font-bold text-heading">{t('registerSticker.claimableTitle')}</h1>
        <p className="text-sm text-muted-fg mt-2">{t('registerSticker.claimableHint')}</p>
        <p className="mt-3 inline-flex items-center gap-1.5 rounded-full bg-primary-soft text-primary px-3 py-1 text-sm font-mono font-semibold">
          <QrCode className="h-3.5 w-3.5" aria-hidden />
          {code}
        </p>
      </div>

      <div className="mt-6 grid grid-cols-2 gap-2 rounded-xl bg-muted p-1">
        <ModeTab active={mode === 'create'} onClick={() => { setMode('create'); setError(null) }}>
          <UserRound className="h-4 w-4" aria-hidden />
          {t('auth.createAccount')}
        </ModeTab>
        <ModeTab active={mode === 'login'} onClick={() => { setMode('login'); setError(null) }}>
          <LogIn className="h-4 w-4" aria-hidden />
          {t('auth.signIn')}
        </ModeTab>
      </div>

      {error && (
        <div className="mt-4">
          <Alert tone="error">{authErrorMessage(error, t)}</Alert>
        </div>
      )}

      {mode === 'create' ? (
        <form onSubmit={handleSubmit(onCreate)} className="mt-5 space-y-4" noValidate>
          <div className="grid sm:grid-cols-2 gap-4">
            <Field label={t('auth.firstName')} error={errors.firstName?.message}>
              <Input placeholder={t('auth.firstNamePlaceholder')} autoComplete="given-name" {...register('firstName')} />
            </Field>
            <Field label={t('auth.lastName')} error={errors.lastName?.message}>
              <Input placeholder={t('auth.lastNamePlaceholder')} autoComplete="family-name" {...register('lastName')} />
            </Field>
          </div>
          <Field label={t('auth.email')} error={errors.email?.message}>
            <Input type="email" autoComplete="email" placeholder={t('auth.emailPlaceholder')} {...register('email')} />
          </Field>
          <Field label={t('auth.password')} hint={t('auth.passwordRule')} error={errors.password?.message}>
            <Input type="password" autoComplete="new-password" placeholder={t('auth.passwordPlaceholder')} {...register('password')} />
          </Field>
          <Field label={t('auth.phone')} optional={t('common.optional')} error={errors.phoneCode?.message || errors.phoneNumber?.message}>
            <PhoneFields
              register={register}
              codeName="phoneCode"
              nationalName="phoneNumber"
              codeError={errors.phoneCode?.message}
              nationalError={errors.phoneNumber?.message}
              nationalPlaceholder={t('auth.phoneNumberPlaceholder')}
            />
          </Field>
          <input type="hidden" {...register('stickerCode', { value: code })} />
          <Button type="submit" fullWidth loading={submitting} className="mt-2">
            {t('registerSticker.createAndContinue')}
          </Button>
        </form>
      ) : (
        <form onSubmit={onLogin} className="mt-5 space-y-4">
          <Field label={t('auth.email')}>
            <Input
              type="email"
              autoComplete="email"
              placeholder={t('auth.emailPlaceholder')}
              value={loginValues.email}
              onChange={(e) => setLoginValues((v) => ({ ...v, email: e.target.value }))}
            />
          </Field>
          <Field label={t('auth.password')}>
            <Input
              type="password"
              autoComplete="current-password"
              placeholder={t('auth.passwordPlaceholder')}
              value={loginValues.password}
              onChange={(e) => setLoginValues((v) => ({ ...v, password: e.target.value }))}
            />
          </Field>
          <Button type="submit" fullWidth loading={submitting} className="mt-2">
            {t('registerSticker.signInAndContinue')}
          </Button>
          <p className="text-center text-sm text-muted-fg">
            <Link to="/forgot-password" className="font-semibold text-primary hover:underline">
              {t('auth.forgotPassword')}
            </Link>
          </p>
        </form>
      )}
    </>
  )
}

/* -------------------------------- details --------------------------------- */

function DetailsStep({ code, onDone }: { code: string; onDone: () => void }) {
  const { t } = useTranslation()
  const [error, setError] = useState<string | null>(null)
  const today = new Date().toISOString().slice(0, 10)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ActivateStickerValues>({
    resolver: zodResolver(activateStickerSchema),
    defaultValues: { birthDate: '', licensePlate: '', nickname: '', brand: '', model: '', color: '' },
  })

  const activateMutation = useMutation({
    mutationFn: (values: ActivateStickerValues) =>
      stickerApi.activateByCode(code, {
        birthDate: values.birthDate,
        licensePlate: values.licensePlate.trim(),
        nickname: values.nickname?.trim() || undefined,
        brand: values.brand?.trim() || undefined,
        model: values.model?.trim() || undefined,
        color: values.color?.trim() || undefined,
      }),
    onSuccess: () => onDone(),
    onError: (err) => setError(http.humanizeError(err)),
  })

  return (
    <>
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
          <Input type="date" max={today} error={Boolean(errors.birthDate)} {...register('birthDate')} />
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
          <Input placeholder={t('vehicle.nicknamePlaceholder')} maxLength={100} {...register('nickname')} />
        </Field>
        <div className="grid sm:grid-cols-2 gap-4">
          <Field label={t('vehicle.brand')} optional={t('common.optional')} error={errors.brand?.message}>
            <Input placeholder={t('vehicle.brandPlaceholder')} maxLength={100} {...register('brand')} />
          </Field>
          <Field label={t('vehicle.model')} optional={t('common.optional')} error={errors.model?.message}>
            <Input placeholder={t('vehicle.modelPlaceholder')} maxLength={100} {...register('model')} />
          </Field>
        </div>
        <Field label={t('vehicle.color')} optional={t('common.optional')} error={errors.color?.message}>
          <Input placeholder={t('vehicle.colorPlaceholder')} maxLength={50} {...register('color')} />
        </Field>
        <Button type="submit" className="w-full" loading={activateMutation.isPending}>
          {t('stickers.activate')}
        </Button>
      </form>
    </>
  )
}

/* ---------------------------------- done ---------------------------------- */

function DoneStep({ onViewStickers }: { onViewStickers: () => void }) {
  const { t } = useTranslation()
  return (
    <div className="mt-6 text-center">
      <div className="mx-auto h-16 w-16 rounded-full bg-success-soft text-success flex items-center justify-center mb-4">
        <Check className="h-8 w-8" aria-hidden />
      </div>
      <h1 className="text-xl font-bold text-heading">{t('stickers.activated')}</h1>
      <p className="text-sm text-muted-fg mt-2">{t('stickers.activatedHint')}</p>
      <Button className="mt-6 w-full" onClick={onViewStickers}>
        {t('stickers.viewMine')}
      </Button>
    </div>
  )
}

/* ---------------------------------- bits ---------------------------------- */

function ModeTab({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'flex items-center justify-center gap-2 h-10 rounded-lg text-sm font-semibold transition-colors',
        active ? 'bg-surface text-primary shadow-sm' : 'text-muted-fg hover:text-heading',
      )}
    >
      {children}
    </button>
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
  return <div className="bg-surface rounded-2xl border border-border shadow-card p-6 sm:p-7">{children}</div>
}

/** Extract a sticker code/raw token from a scanned payload (URL or plain text). */
function extractCode(text: string): string {
  const trimmed = text.trim()
  // A scanned QR usually encodes the public URL /c/{token}.
  const match = trimmed.match(/\/c\/([^/?#]+)/)
  return (match ? match[1] : trimmed).trim()
}

function errCode(err: unknown): string {
  const normalized = toApiError(err)
  if (normalized.status === 404) return 'notFound'
  if (normalized.code === 'STICKER_ALREADY_BOUND') return 'bound'
  if (normalized.code === 'INVALID_STICKER_CODE') return 'invalidCode'
  if (normalized.code === 'EMAIL_ALREADY_REGISTERED') return 'emailTaken'
  return normalized.message || 'error'
}

function authErrorMessage(code: string, t: (k: string) => string): string {
  switch (code) {
    case 'invalidCode':
      return t('registerSticker.invalidCode')
    case 'bound':
      return t('registerSticker.alreadyBound')
    case 'emailTaken':
      return t('auth.emailAlreadyRegistered')
    case 'notFound':
      return t('registerSticker.notFound')
    default:
      return code === 'error' ? t('errors.generic') : code
  }
}

export { RegisterStickerPage }
