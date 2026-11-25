import { Link, useLocation } from "react-router";
import { rotaIcinBreadcrumbOgeleri } from "../../lib/navigasyon";

type BreadcrumbOgesi = {
  etiket: string;
  yol?: string;
};

interface BreadcrumbProps {
  pageTitle: string;
  ogeler?: BreadcrumbOgesi[];
}

const PageBreadcrumb: React.FC<BreadcrumbProps> = ({ pageTitle, ogeler }) => {
  const { pathname } = useLocation();
  const breadcrumbOgeleri = ogeler ?? rotaIcinBreadcrumbOgeleri(pathname, pageTitle);

  return (
    <div className="mb-6 flex flex-col items-start justify-between gap-3 sm:flex-row sm:items-center">
      <h2 className="text-xl font-semibold text-gray-800 dark:text-white/90">
        {pageTitle}
      </h2>
      <nav aria-label={`${pageTitle} gezinme yolu`} className="max-w-full overflow-x-auto">
        <ol className="flex min-w-max items-center gap-1.5 whitespace-nowrap">
          {breadcrumbOgeleri.map((oge, index) => {
            const sonOge = index === breadcrumbOgeleri.length - 1;

            return (
              <li key={`${oge.etiket}-${index}`} className="flex items-center gap-1.5">
                {index > 0 && (
                  <svg className="h-4 w-4 text-gray-400 dark:text-gray-500" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                    <path d="m6 3 4 5-4 5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                )}
                {sonOge ? (
                  <span aria-current="page" className="text-sm font-medium text-gray-800 dark:text-white/90">
                    {oge.etiket}
                  </span>
                ) : oge.yol ? (
                  <Link className="text-sm text-gray-500 transition hover:text-brand-500 dark:text-gray-400 dark:hover:text-brand-400" to={oge.yol}>
                    {oge.etiket}
                  </Link>
                ) : (
                  <span className="text-sm text-gray-500 dark:text-gray-400">{oge.etiket}</span>
                )}
              </li>
            );
          })}
        </ol>
      </nav>
    </div>
  );
};

export default PageBreadcrumb;
