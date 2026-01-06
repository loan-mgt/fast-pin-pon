import type { ButtonHTMLAttributes } from 'react'
import { cn } from '../../lib/utils'

const baseStyles = 'inline-flex items-center justify-center rounded-full px-4 py-2 text-sm font-semibold transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 disabled:opacity-60 disabled:cursor-not-allowed cursor-pointer'

const variantStyles: Record<string, string> = {
  solid: 'bg-sky-500 text-slate-950 shadow-lg shadow-cyan-500/30 hover:bg-sky-400',
  ghost: 'border border-white/20 text-white hover:bg-white/5',
  outline: 'border border-white/40 text-white hover:border-white/60',
  danger: 'bg-red-600 text-white shadow-lg shadow-red-500/30 hover:bg-red-500',
}

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: keyof typeof variantStyles
}

export function Button({ className, variant = 'solid', ...props }: ButtonProps) {
  return (
    <button className={cn(baseStyles, variantStyles[variant], className)} {...props} />
  )
}
