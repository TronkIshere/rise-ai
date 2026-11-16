import { HeartHandshake, ImageOff } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { safeImageUrl } from '@/lib/campaign-display';

interface Props {
  src?: string;
  alt: string;
  className?: string;
  fallback?: ReactNode;
  renderCaption?: (hasImage: boolean) => ReactNode;
}
export function CampaignMedia({ src, alt, className = '', fallback, renderCaption }: Props) {
  const url = safeImageUrl(src);
  const [failedUrl, setFailedUrl] = useState<string>();
  const hasImage = Boolean(url && url !== failedUrl);
  return (
    <>
      {!hasImage && fallback ? fallback : (
        <div className={'relative flex items-center justify-center overflow-hidden bg-soft-mint ' + className}>
          {hasImage ? (
            <img src={url} alt={alt} className="h-full w-full object-cover" loading="lazy" onError={() => setFailedUrl(url)} />
          ) : (
            <div className="flex h-full w-full flex-col items-center justify-center gap-3 bg-gradient-to-br from-soft-mint via-surface to-soft-sand text-brand" role="img" aria-label={'Chưa có ảnh: ' + alt}>
              {url ? <ImageOff aria-hidden="true" size={30} /> : <HeartHandshake aria-hidden="true" size={35} />}
              <span className="text-xs font-semibold">Rise AI · Cùng tạo tác động</span>
            </div>
          )}
        </div>
      )}
      {renderCaption?.(hasImage)}
    </>
  );
}
