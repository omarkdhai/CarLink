import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import { vehicleSchema, type VehicleFormValues } from '@/features/vehicles/vehicleSchemas'
import { vehicleApi, vehicleQueryKeys } from '@/services/vehicleApi'
import { http } from '@/services/apiClient'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card'
import { Alert } from '@/components/ui/Alert'
import { ErrorState } from '@/components/shared/ErrorState'

/** Create (/vehicles/new) + edit (/vehicles/:id/edit) vehicle form. */
export default function VehicleFormPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { id } = useParams<{ id?: string }>()
  const isEdit = Boolean(id)

  const [submissionError, setSubmissionError] = useState<string | null>(null)

  // Preload existing values in edit mode.
  const existing = useQuery({
    queryKey: vehicleQueryKeys.detail(id ?? ''),
    queryFn: () => vehicleApi.get(id!),
    enabled: isEdit,
    retry: false,
  })

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<VehicleFormValues>({
    resolver: zodResolver(vehicleSchema),
    defaultValues: { nickname: '', brand: '', model: '', color: '', licensePlate: '' },
  })

  useEffect(() => {
    if (existing.data) {
      reset({
        nickname: existing.data.nickname ?? '',
        brand: existing.data.brand ?? '',
        model: existing.data.model ?? '',
        color: existing.data.color ?? '',
        licensePlate: existing.data.licensePlate,
      })
    }
  }, [existing.data, reset])

  const saveMutation = useMutation({
    mutationFn: (values: VehicleFormValues) => {
      const payload = {
        nickname: values.nickname?.trim() || undefined,
        brand: values.brand?.trim() || undefined,
        model: values.model?.trim() || undefined,
        color: values.color?.trim() || undefined,
        licensePlate: values.licensePlate.trim(),
      }
      return isEdit ? vehicleApi.update(id!, payload) : vehicleApi.create(payload)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: vehicleQueryKeys.all })
      navigate(isEdit ? `/vehicles/${id}` : '/vehicles', { replace: true })
    },
    onError: (err) => setSubmissionError(http.humanizeError(err)),
  })

  function onSubmit(values: VehicleFormValues) {
    setSubmissionError(null)
    saveMutation.mutate(values)
  }

  if (isEdit && existing.isPending) {
    return (
      <div className="max-w-2xl mx-auto">
        <div className="bg-surface border border-border rounded-lg p-8 space-y-3">
          <div className="h-5 w-1/3 rounded bg-muted animate-pulse" />
          <div className="h-4 w-2/3 rounded bg-muted animate-pulse" />
        </div>
      </div>
    )
  }

  if (isEdit && existing.isError) {
    return <ErrorState message={t('errors.notFound')} />
  }

  return (
    <div className="max-w-2xl mx-auto">
      <Link
        to={isEdit ? `/vehicles/${id}` : '/vehicles'}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-fg hover:text-primary mb-4 transition-colors"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden />
        {isEdit ? t('vehicle.details') : t('vehicle.title')}
      </Link>

      <Card>
        <CardHeader>
          <div>
            <CardTitle>{isEdit ? t('vehicle.edit') : t('vehicle.new')}</CardTitle>
            <CardDescription>{isEdit ? existing.data?.licensePlate : t('vehicle.subtitle')}</CardDescription>
          </div>
        </CardHeader>
        <CardContent>
          {submissionError && (
            <div className="mb-4">
              <Alert tone="error">{submissionError}</Alert>
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <Field label={t('vehicle.nickname')} optional={t('common.optional')} hint={t('vehicle.nicknameHint')} error={errors.nickname?.message}>
              <Input placeholder={t('vehicle.nicknamePlaceholder')} maxLength={100} error={Boolean(errors.nickname)} {...register('nickname')} />
            </Field>

            <div className="grid sm:grid-cols-2 gap-4">
              <Field label={t('vehicle.brand')} optional={t('common.optional')} error={errors.brand?.message}>
                <Input placeholder={t('vehicle.brandPlaceholder')} maxLength={100} error={Boolean(errors.brand)} {...register('brand')} />
              </Field>
              <Field label={t('vehicle.model')} optional={t('common.optional')} error={errors.model?.message}>
                <Input placeholder={t('vehicle.modelPlaceholder')} maxLength={100} error={Boolean(errors.model)} {...register('model')} />
              </Field>
            </div>

            <div className="grid sm:grid-cols-2 gap-4">
              <Field label={t('vehicle.color')} optional={t('common.optional')} error={errors.color?.message}>
                <Input placeholder={t('vehicle.colorPlaceholder')} maxLength={50} error={Boolean(errors.color)} {...register('color')} />
              </Field>
              <Field label={t('vehicle.licensePlate')} error={errors.licensePlate?.message}>
                <Input
                  placeholder={t('vehicle.licensePlatePlaceholder')}
                  maxLength={20}
                  autoCapitalize="characters"
                  error={Boolean(errors.licensePlate)}
                  {...register('licensePlate')}
                />
              </Field>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Link to={isEdit ? `/vehicles/${id}` : '/vehicles'} className="inline-flex items-center justify-center h-11 px-4 rounded-lg border border-border font-semibold text-sm text-foreground hover:bg-muted">
                {t('common.cancel')}
              </Link>
              <Button type="submit" loading={saveMutation.isPending}>
                {isEdit ? t('vehicle.save') : t('vehicle.create')}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}