import type { ReactNode } from 'react'
import { cn } from '../../lib/utils'

export interface CardProps {
  children: ReactNode
  className?: string
}

export function Card({ children, className }: CardProps) {
  return (
    <div
      className={cn(
        'bg-white/5 shadow-[0_20px_45px_-15px_rgba(15,23,42,0.8)] backdrop-blur p-6 border border-white/10 rounded-3xl',
        className,
      )}
    >
      {children}
    </div>
  )
}
