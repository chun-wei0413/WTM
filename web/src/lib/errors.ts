import { ApiError } from '../api/client';

export interface FriendlyError {
  /** What went wrong, in plain Traditional Chinese. */
  title: string;
  /** The server's own (English) explanation, shown smaller; absent when there is nothing useful to add. */
  detail?: string;
}

/** How long a rate-limit wait is, as something a person can read. */
export function describeWait(seconds: number): string {
  if (seconds < 60) return `${seconds} 秒`;
  const minutes = Math.ceil(seconds / 60);
  if (minutes < 60) return `${minutes} 分鐘`;
  return `${Math.ceil(minutes / 60)} 小時`;
}

export function friendlyError(error: unknown, context: 'login' | 'general' = 'general'): FriendlyError {
  if (!(error instanceof ApiError)) {
    return { title: '發生未預期的錯誤,請稍後再試' };
  }
  const detail = error.detail || undefined;
  switch (error.status) {
    case 0:
      return { title: '連不上伺服器,請檢查網路或稍後再試' };
    case 400:
      return { title: '輸入的內容不符合規定', detail };
    case 401:
      return { title: context === 'login' ? '帳號或密碼錯誤' : '登入已過期,請重新登入' };
    case 403:
      return { title: '沒有權限執行這個操作', detail };
    case 404:
      return { title: '找不到這筆資料' };
    case 409:
      return { title: '這個操作和目前的狀態衝突', detail };
    case 413:
      return { title: '檔案太大了' };
    case 429: {
      const wait = error.retryAfterSeconds ? `請 ${describeWait(error.retryAfterSeconds)}後再試` : '請稍後再試';
      return { title: `操作太頻繁,${wait}`, detail };
    }
    case 502:
    case 503:
    case 504:
      return { title: '服務暫時無法使用,請稍後再試', detail };
    default:
      return { title: '發生未預期的錯誤,請稍後再試', detail };
  }
}
