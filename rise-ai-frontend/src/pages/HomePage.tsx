import { useState } from 'react';
import type { CampaignCategory } from '@/api/contracts';
import { B2Hero } from '@/components/ui/B2Hero';
import { B2Categories } from '@/components/ui/B2Categories';
import { B2Story } from '@/components/ui/B2Story';
import { B2Milestones } from '@/components/ui/B2Milestones';
import { B2Transparency } from '@/components/ui/B2Transparency';
import { B2Footer } from '@/components/ui/B2Footer';
import { B2Campaigns } from './B2Campaigns';

/** Owner-approved high-fidelity Homepage B2 v1.3.2, 1440px desktop baseline. */
export function HomePage() {
  const [selectedCategory, setSelectedCategory] = useState<CampaignCategory>();
  return <div className="b2-homepage">
    <a className="sr-only focus:not-sr-only" href="#b2-main-content">Bỏ qua điều hướng</a>
    <div className="preview-ribbon" role="note"><span className="ribbon-dot" aria-hidden="true" />Riser AI · COMMUNITY SIGNATURE B2 v1.3.2 · MÔ PHỎNG, KHÔNG CHUYỂN TIỀN THẬT</div>
    <B2Hero />
    <main id="b2-main-content">
      <B2Categories selected={selectedCategory} onSelect={setSelectedCategory} />
      <B2Campaigns selected={selectedCategory} onSelect={setSelectedCategory} />
      <B2Story />
      <B2Milestones />
      <B2Transparency />
    </main>
    <B2Footer />
  </div>;
}
