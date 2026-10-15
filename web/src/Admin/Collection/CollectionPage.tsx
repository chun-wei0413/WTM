import { StatsSection } from './StatsSection';
import { UploadSection } from './UploadSection';
import { UrlSection } from './UrlSection';

export function CollectionPage() {
  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>圖庫收集</h1>
          <p className="lead">
            把梗圖收進圖庫。圖片收進來之後,會由視覺模型自動寫下它的意思、使用時機與圖中文字,標記完成並核准後就能被搜尋到。
          </p>
        </div>
      </div>
      <StatsSection />
      <UploadSection />
      <UrlSection />
    </div>
  );
}
