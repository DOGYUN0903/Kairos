import { CircleAlert } from "lucide-react";

export function FormNotice({ message }: { message?: string }) {
  if (!message) return null;
  return (
    <div className="notice notice-warn" role="alert">
      <CircleAlert className="h-4 w-4 flex-none text-warning" aria-hidden />
      <span>{message}</span>
    </div>
  );
}
