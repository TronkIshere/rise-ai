type EvidenceQuery = {
  data?: { data?: { id?: string }[]; totalElements?: number };
  isSuccess: boolean;
  isError: boolean;
  isFetching: boolean;
};

export function creatorEvidenceSummary(query: EvidenceQuery): {
  status: 'loading' | 'error' | 'unknown' | 'empty' | 'ready';
  hasEvidence: boolean;
  count?: number;
} {
  // A failed/refetching query must not authorize a proposal from stale cache data.
  if (query.isFetching) return { status: 'loading', hasEvidence: false };
  if (query.isError) return { status: 'error', hasEvidence: false };
  if (!query.isSuccess) return { status: 'loading', hasEvidence: false };
  const { data, totalElements } = query.data ?? {};
  if (!Array.isArray(data)) return { status: 'unknown', hasEvidence: false };
  if (data.some(item => typeof item?.id !== 'string' || !item.id.trim())) {
    return { status: 'unknown', hasEvidence: false };
  }
  const count = typeof totalElements === 'number' && Number.isSafeInteger(totalElements) && totalElements >= 0 ? totalElements : undefined;
  const hasRows = data.length > 0;
  if ((totalElements !== undefined && count === undefined) || (count === 0 && data.length > 0)) {
    return { status: 'unknown', hasEvidence: false };
  }
  if (hasRows || (count !== undefined && count > 0)) return { status: 'ready', hasEvidence: true, count };
  if (count === 0 && data.length === 0) return { status: 'empty', hasEvidence: false, count: 0 };
  return { status: 'unknown', hasEvidence: false };
}
