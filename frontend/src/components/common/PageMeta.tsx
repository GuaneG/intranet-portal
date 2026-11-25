import { useEffect } from "react";
import { HelmetProvider, Helmet } from "react-helmet-async";
import { sayfaBasliginiBicimlendir } from "../../lib/navigasyon";

const PageMeta = ({
  title,
  description,
}: {
  title: string;
  description: string;
}) => {
  const sayfaBasligi = sayfaBasliginiBicimlendir(title);

  useEffect(() => {
    document.title = sayfaBasligi;
  }, [sayfaBasligi]);

  return (
    <Helmet>
      <title>{sayfaBasligi}</title>
      <meta name="description" content={description} />
    </Helmet>
  );
};

export const AppWrapper = ({ children }: { children: React.ReactNode }) => (
  <HelmetProvider>{children}</HelmetProvider>
);

export default PageMeta;
