import type { StatusFilter } from '../../lib/templateList';

const TABS: Array<{ value: StatusFilter; label: string }> = [
  { value: 'APPROVED', label: '已核准' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'RETIRED', label: '已下架' },
  { value: 'ALL', label: '全部' },
];

interface Props {
  status: StatusFilter;
  query: string;
  /** How many templates each tab holds; null until the list is loaded. */
  counts: Record<StatusFilter, number> | null;
  onStatus: (status: StatusFilter) => void;
  onQuery: (text: string) => void;
}

/** The tabs that choose which status to list, and the box that searches the list. */
export function TemplateFilters({ status, query, counts, onStatus, onQuery }: Props) {
  return (
    <div className="list-tools">
      <div className="tabs" role="tablist" aria-label="狀態篩選">
        {TABS.map((tab) => (
          <button
            key={tab.value}
            type="button"
            role="tab"
            aria-selected={status === tab.value}
            onClick={() => onStatus(tab.value)}
          >
            {tab.label}
            {counts && <span className="tab-count">{counts[tab.value]}</span>}
          </button>
        ))}
      </div>
      <input
        type="search"
        className="list-search"
        value={query}
        onChange={(e) => onQuery(e.target.value)}
        placeholder="搜尋名稱、意思、標籤、來源"
        aria-label="搜尋圖庫"
      />
    </div>
  );
}
