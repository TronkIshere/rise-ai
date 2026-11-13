import type { MouseEvent, ReactNode } from 'react';
import { Link, useNavigate } from 'react-router';

interface B2SectionLinkProps {
  path: '/' | '/campaigns';
  target: string;
  className?: string;
  children: ReactNode;
  label?: string;
}

export function B2SectionLink({ path, target, className, children, label }: B2SectionLinkProps) {
  const navigate = useNavigate();
  const to = path + '#' + target;

  function openSection(event: MouseEvent<HTMLAnchorElement>) {
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    const observer = new MutationObserver(scrollToSection);
    const timeout = window.setTimeout(() => observer.disconnect(), 2000);

    function scrollToSection() {
      if (window.location.pathname !== path || window.location.hash !== '#' + target) return;
      const section = document.getElementById(target);
      if (!section) return;
      observer.disconnect();
      window.clearTimeout(timeout);
      section.scrollIntoView();
    }

    // A route transition can resolve before React mounts its destination.
    observer.observe(document.body, { childList: true, subtree: true });
    void Promise.resolve(navigate(to, { flushSync: true })).then(scrollToSection);
  }

  return <Link to={to} onClick={openSection} className={className} aria-label={label}>{children}</Link>;
}
