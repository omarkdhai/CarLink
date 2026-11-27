import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { z } from 'zod'
import { ChevronDown, KeyRound, ShieldCheck } from 'lucide-react'
import { Sheet } from '@/components/ui/Sheet'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Alert } from '@/components/ui/Alert'
import { stickerApi, stickerQueryKeys } from '@/services/stickerApi'
import { vehicleQueryKeys } from '@/services/vehicleApi'
import { http } from '@/services/apiClient'
import { activateStickerSchema } from '@/features/stickers/stickerSchemas'
import i18n from '@/lib/i18n'

const schema = activateStickerSchema.extend({
  code: z.string().trim().min(4, i18n.t('validation.stickerCodeRequired')).max(32),
})
type Values = z.infer<typeof schema>

const EMPTY: Values = { code: '', licensePlate: '', nickname: '', brand: '', model: '', color: '' }

/**
 * Signed-in owners claim a new sticker by typing the activation code printed
 * under it — no need to scan their own QR. Links it to the car whose plate is
 * entered (an existing active car with that plate is reused).
 */
export function ActivateStickerSheet({ open, onClose, onDone }: { open: boolean; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [more, setMore] = useState(false)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: EMPTY })

  const activate = useMutation({
    mutationFn: (v: Values) =>
      stickerApi.activateByCode(v.code.trim().toUpperCase(), {
        licensePlate: v.licensePlate.trim(),
        nickname: v.nickname?.trim() || undefined,
        brand: v.brand?.trim() || undefined,
        model: v.model?.trim() || undefined,
        color: v.color?.trim() || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: stickerQueryKeys.mine })
      queryClient.invalidateQueries({ queryKey: vehicleQueryKeys.all })
      reset(EMPTY)
      setMore(false)
      onDone()
    },
  })

  const close = () => {
    activate.reset()
    onClose()
  }

  return (
    <Sheet
      open={open}
      onClose={close}
      width="md"
      title={t('stickers.activateTitle')}
      subtitle={t('stickers.activateSubtitle')}
      footer={
        <>
          <Button variant="outline" onClick={close}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="activate-sticker-form" loading={activate.isPending}>
            <KeyRound className="h-4 w-4" aria-hidden />
            {t('stickers.activate')}
          </Button>
        </>
      }
    >
      <form id="activate-sticker-form" noValidate onSubmit={handleSubmit((v) => activate.mutate(v))} className="space-y-5">
        {activate.isError && <Alert tone="error">{http.humanizeError(activate.error)}</Alert>}

        <Field label={t('order.activationCode')} hint={t('stickers.codeHint')} error={errors.code?.message}>
          <Input
            autoFocus
            autoComplete="off"
            autoCapitalize="characters"
            spellCheck={false}
            placeholder="ABCD-1234"
            className="font-mono uppercase tracking-widest"
            error={Boolean(errors.code)}
            {...register('code')}
          />
        </Field>

        <Field label={t('stickers.licensePlate')} error={errors.licensePlate?.message}>
          <Input dir="ltr" placeholder={t('vehicle.licensePlatePlaceholder')} error={Boolean(errors.licensePlate)} {...register('licensePlate')} />
        </Field>

        <button
          type="button"
          onClick={() => setMore((m) => !m)}
          aria-expanded={more}
          className="inline-flex items-center gap-1.5 text-sm font-semibold text-primary hover:underline"
        >
          <ChevronDown className={`h-4 w-4 transition-transform ${more ? 'rotate-180' : ''}`} aria-hidden />
          {t('stickers.moreDetails')}
        </button>

        {more && (
          <div className="space-y-5">
            <Field label={t('vehicle.nickname')} optional={t('common.optional')}>
              <Input placeholder={t('vehicle.nicknamePlaceholder')} {...register('nickname')} />
            </Field>
            <div className="grid gap-5 sm:grid-cols-3">
              <Field label={t('vehicle.brand')} optional={t('common.optional')}>
                <Input placeholder={t('vehicle.brandPlaceholder')} {...register('brand')} />
              </Field>
              <Field label={t('vehicle.model')} optional={t('common.optional')}>
                <Input placeholder={t('vehicle.modelPlaceholder')} {...register('model')} />
              </Field>
              <Field label={t('vehicle.color')} optional={t('common.optional')}>
                <Input placeholder={t('vehicle.colorPlaceholder')} {...register('color')} />
              </Field>
            </div>
          </div>
        )}

        <p className="flex items-start gap-2 text-xs text-muted-fg leading-relaxed">
          <ShieldCheck className="h-4 w-4 shrink-0 text-success" aria-hidden />
          {t('contact.activatePrivacy')}
        </p>
      </form>
    </Sheet>
  )
}
