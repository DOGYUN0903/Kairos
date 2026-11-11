import Link from "next/link";

type Action = { label: string; href: string } | { label: string; onClick: () => void };

export function EmptyState({ message, action }: { message: string; action?: Action }) {
  return (
    <div className="flex flex-col items-center gap-4 px-6 py-16 text-center text-text-2">
      <p>{message}</p>
      {action &&
        ("href" in action ? (
          <Link className="btn btn-primary" href={action.href}>
            {action.label}
          </Link>
        ) : (
          <button type="button" className="btn btn-primary" onClick={action.onClick}>
            {action.label}
          </button>
        ))}
    </div>
  );
}
