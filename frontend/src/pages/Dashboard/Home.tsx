import { useCallback, useEffect, useRef, useState } from "react";
import PageMeta from "../../components/common/PageMeta";
import DogumGunuKarti from "../../components/dashboard/DogumGunuKarti";
import EkipDiziniOzetiKarti from "../../components/dashboard/EkipDiziniOzetiKarti";
import KalanIzinKarti from "../../components/dashboard/KalanIzinKarti";
import OdaDolulukKarti from "../../components/dashboard/OdaDolulukKarti";
import { ApiError, dashboardOzetiniGetir } from "../../lib/api";
import type { DashboardOzeti } from "../../types/dashboard";

function hataMesajiniGetir(error: unknown) {
  return error instanceof ApiError && error.message
    ? error.message
    : "Dashboard bilgileri alınamadı. Lütfen tekrar deneyin.";
}

export default function Home() {
  const [ozet, setOzet] = useState<DashboardOzeti | null>(null);
  const [yukleniyor, setYukleniyor] = useState(true);
  const [hata, setHata] = useState<string | null>(null);
  const sonIstekNumarasi = useRef(0);

  const ozetiYukle = useCallback(async () => {
    const istekNumarasi = ++sonIstekNumarasi.current;

    setYukleniyor(true);
    setHata(null);

    try {
      const sonuc = await dashboardOzetiniGetir();

      if (istekNumarasi === sonIstekNumarasi.current) {
        setOzet(sonuc);
      }
    } catch (error) {
      if (istekNumarasi === sonIstekNumarasi.current) {
        setOzet(null);
        setHata(hataMesajiniGetir(error));
      }
    } finally {
      if (istekNumarasi === sonIstekNumarasi.current) {
        setYukleniyor(false);
      }
    }
  }, []);

  useEffect(() => {
    void ozetiYukle();
  }, [ozetiYukle]);

  return (
    <>
      <PageMeta
        title="Ana Sayfa | Intranet Portal"
        description="Intranet Portal özet ekranı"
      />
      <div className="grid grid-cols-12 gap-4 md:gap-6">
        {hata ? (
          <div
            role="alert"
            className="col-span-12 rounded-2xl border border-error-200 bg-error-50 p-5 text-sm text-error-700 dark:border-error-500/20 dark:bg-error-500/10 dark:text-error-400"
          >
            <p>{hata}</p>
            <button
              type="button"
              onClick={() => void ozetiYukle()}
              className="mt-3 font-medium underline"
            >
              Tekrar dene
            </button>
          </div>
        ) : (
          <>
            <div className="col-span-12 h-fit self-start xl:col-span-4">
              <DogumGunuKarti
                dogumGunleri={ozet?.bugunDogumGunleri ?? []}
                yukleniyor={yukleniyor}
              />
            </div>

            {(yukleniyor || ozet?.izinBakiyesi != null) && (
              <div className="col-span-12 h-fit self-start xl:col-span-4">
                <KalanIzinKarti
                  izinBakiyesi={ozet?.izinBakiyesi ?? null}
                  yukleniyor={yukleniyor}
                />
              </div>
            )}

            <div className="col-span-12 xl:col-span-4">
              <EkipDiziniOzetiKarti
                aktifEkipUyesiSayisi={ozet?.aktifEkipUyesiSayisi ?? null}
                kisiler={ozet?.ekipDiziniOzeti ?? []}
                yukleniyor={yukleniyor}
              />
            </div>

            <div className="col-span-12 h-fit self-start xl:col-span-4">
              <OdaDolulukKarti
                odalar={ozet?.odaDoluluk ?? []}
                yukleniyor={yukleniyor}
              />
            </div>
          </>
        )}
      </div>
    </>
  );
}
