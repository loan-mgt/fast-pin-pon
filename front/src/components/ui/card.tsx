import type { CSSProperties, ReactNode } from 'react'
import { cn } from '../../lib/utils'

export interface CardProps {
  readonly children: ReactNode
  readonly className?: string
  readonly style?: CSSProperties
}

export function Card({ children, className, style }: CardProps) {
  return (
    <section
      aria-label="Card"
      className={cn(
        'bg-slate-900/70 backdrop-blur-xl shadow-[0_8px_32px_rgba(59,130,246,0.15)] p-3 pb-1 border border-blue-500/20 rounded-3xl',
        className,
      )}
      style={style}
    >
      {children}
    </section>
  )
}
