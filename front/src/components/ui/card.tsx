import type { CSSProperties, ReactNode, MouseEventHandler } from 'react'
import { cn } from '../../lib/utils'

export interface CardProps {
  readonly children: ReactNode
  readonly className?: string
  readonly style?: CSSProperties
  readonly onMouseDown?: MouseEventHandler<HTMLDivElement>
  readonly onMouseMove?: MouseEventHandler<HTMLDivElement>
  readonly onMouseUp?: MouseEventHandler<HTMLDivElement>
  readonly onMouseLeave?: MouseEventHandler<HTMLDivElement>
}

export function Card({ children, className, style, onMouseDown, onMouseMove, onMouseUp, onMouseLeave }: CardProps) {
  const hasMouseHandlers = onMouseDown || onMouseMove || onMouseUp || onMouseLeave
  return (
    <div
      role={hasMouseHandlers ? 'region' : undefined}
      className={cn(
        'bg-slate-900/70 backdrop-blur-xl shadow-[0_8px_32px_rgba(59,130,246,0.15)] p-6 border border-blue-500/20 rounded-3xl',
        className,
      )}
      style={style}
      onMouseDown={onMouseDown}
      onMouseMove={onMouseMove}
      onMouseUp={onMouseUp}
      onMouseLeave={onMouseLeave}
    >
      {children}
    </div>
  )
}
