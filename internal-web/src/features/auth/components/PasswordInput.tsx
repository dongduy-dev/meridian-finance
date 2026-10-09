import { Eye, EyeOff } from 'lucide-react'
import { useState, type ComponentProps } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/cn'

export function PasswordInput({ className, disabled, ...props }: Omit<ComponentProps<'input'>, 'type'>) {
  const [visible, setVisible] = useState(false)
  const action = visible ? 'Hide password' : 'Show password'

  return (
    <div className="relative">
      <Input {...props} disabled={disabled} type={visible ? 'text' : 'password'} className={cn('pr-14', className)} />
      <Button type="button" variant="ghost" size="icon" className="absolute right-0 top-0"
        aria-label={action} title={action} aria-controls={props.id} disabled={disabled} onClick={() => setVisible((value) => !value)}>
        {visible ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
      </Button>
    </div>
  )
}
