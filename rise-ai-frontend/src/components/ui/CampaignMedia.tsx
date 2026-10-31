import { HeartHandshake, ImageOff } from 'lucide-react';
import { useState } from 'react';

interface Props {
  src?: string;
  alt: string;
  className?: string;
}
export function CampaignMedia({ src, alt, className = '' }: Props) {
  const [failed, setFailed] = useState(false);
  // Only render http(s) or same-origin URLs supplied by the Backend, not sim:// or data:.
  let url: string | undefined;
  if (src) {
    try {
      const parsed = new URL(src, window.location.origin);
      if (['https:', 'http:'].includes(parsed.protocol)) url = parsed.href;
    } catch { url = undefined; }
  }
  return (
    <div className={'relative flex items-center justify-center overflow-hidden bg-soft-mint ' + className}>
      {url && !failed ? (
        <img src={url} alt={alt} className="h-full w-full object-cover" loading="lazy" onError={() => setFailed(true)} />
      ) : (
        <div className="flex h-full w-full flex-col items-center justify-center gap-3 bg-gradient-to-br from-soft-mint via-surface to-soft-sand text-brand" role="img" aria-label={'Chưa có ảnh: ' + alt}>
          {failed ? <ImageOff aria-hidden="true" size={30} /> : <HeartHandshake aria-hidden="true" size={35} />}
          <span className="text-xs font-semibold">Rise AI · Cùng tạo tác động</span>
        </div>
      )}
    </div>
  );
}
