import type { JSX } from 'react'
import { Button } from './button'
import { Card } from './card'
import { cn } from '../../lib/utils'

interface ConfirmationDialogProps {
  readonly isOpen: boolean
  readonly title: string
  readonly description: string
  readonly onConfirm: () => void | Promise<void>
  readonly onCancel?: () => void
  readonly confirmLabel?: string
  readonly cancelLabel?: string
  readonly variant?: 'danger' | 'info'
  readonly isSubmitting?: boolean
}

export function ConfirmationDialog({
  isOpen,
  title,
  description,
  onConfirm,
  onCancel,
  confirmLabel = 'Confirmer',
  cancelLabel = 'Annuler',
  variant = 'info',
  isSubmitting = false,
}: ConfirmationDialogProps): JSX.Element | null {
  if (!isOpen) return null

  return (
    <div className="z-[100] fixed inset-0 flex justify-center items-center bg-slate-950/80 backdrop-blur-sm p-6">
      <Card
        className={cn(
          'space-y-4 shadow-2xl px-6 pt-6 pb-6 w-full max-w-md',
          variant === 'danger' ? 'border-red-500/40' : 'border-blue-500/30'
        )}
      >
        <div className="space-y-1">
          <p
            className={cn(
              'font-bold text-[0.65rem] uppercase tracking-[0.3em]',
              variant === 'danger' ? 'text-red-400' : 'text-cyan-400'
            )}
          >
            {variant === 'danger' ? 'ATTENTION' : 'CONFIRMATION'}
          </p>
          <h2 className="font-semibold text-white text-xl tracking-tight">{title}</h2>
          <div className="pt-2">
            <p className="text-slate-300 text-sm leading-relaxed">{description}</p>
          </div>
        </div>

        <div className="flex justify-end gap-3 pt-4">
          {onCancel && (
            <Button
              type="button"
              variant="ghost"
              onClick={onCancel}
              disabled={isSubmitting}
              className="hover:bg-slate-800 border-slate-700 text-slate-300"
            >
              {cancelLabel}
            </Button>
          )}
          <Button
            type="button"
            variant={variant === 'danger' ? 'danger' : 'solid'}
            className="min-w-[100px]"
            onClick={onConfirm}
            disabled={isSubmitting}
          >
            {isSubmitting ? 'Action en cours...' : confirmLabel}
          </Button>
        </div>
      </Card>
    </div>
  )
}
