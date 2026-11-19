import { CircleCheckBig, CircleX } from 'lucide-react';
import { passwordRequirements } from '@/utils/passwordRules';

interface PasswordRequirementListProps {
  password: string;
}

// Live checklist of the password policy, shown under the password field while the user types
export function PasswordRequirementList({ password }: Readonly<PasswordRequirementListProps>) {
  return (
    <div className="mt-2 space-y-1">
      {passwordRequirements.map((req) => {
        const isValid = req.test(password);
        return (
          <div key={req.label} className="flex items-center text-xs">
            {isValid ? (
              <CircleCheckBig className="h-3 w-3 text-green-500 mr-1" />
            ) : (
              <CircleX className="h-3 w-3 text-red-500 mr-1" />
            )}
            <span className={isValid ? 'text-green-600' : 'text-red-600'}>
              {req.label}
            </span>
          </div>
        );
      })}
    </div>
  );
}
