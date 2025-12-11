import type { ButtonHTMLAttributes } from 'react'
import { cn } from '../../lib/utils'

const baseStyles = 'inline-flex items-center justify-center rounded-full px-4 py-2 text-sm font-semibold transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 disabled:opacity-60 disabled:cursor-not-allowed'

const variantStyles: Record<string, string> = {
  solid: 'bg-gradient-to-r from-sky-500 to-cyan-400 text-slate-950 shadow-lg shadow-cyan-500/30 hover:from-sky-400 hover:to-cyan-300',
  ghost: 'border border-white/20 text-white hover:bg-white/5',
  outline: 'border border-white/40 text-white hover:border-white/60',
}

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: keyof typeof variantStyles
}

export function Button({ className, variant = 'solid', ...props }: ButtonProps) {
  return (
    <button className={cn(baseStyles, variantStyles[variant], className)} {...props} />
  )
}
