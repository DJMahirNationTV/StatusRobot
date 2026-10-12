interface PaginationProps {
  label: string
  page: number
  totalPages: number
  onChange: (page: number) => void
  disabled?: boolean
}

export function Pagination({ label, page, totalPages, onChange, disabled }: PaginationProps) {
  if (totalPages <= 1) return null
  return <nav aria-label={label} className="mt-4 flex items-center justify-between gap-3">
    <button type="button" disabled={disabled || page === 0} onClick={() => onChange(page - 1)} className="dashboard-action">Previous</button>
    <p className="text-xs text-muted">Page {page + 1} of {totalPages}</p>
    <button type="button" disabled={disabled || page + 1 >= totalPages} onClick={() => onChange(page + 1)} className="dashboard-action">Next</button>
  </nav>
}
