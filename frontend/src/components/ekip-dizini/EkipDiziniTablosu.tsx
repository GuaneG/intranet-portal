import { useState } from "react";
import type {
    EkipDiziniKisi,
    HiyerarsiDugumu,
} from "../../types/ekipDizini";
import { useAuth } from "../../context/AuthContext";
import {
    hiyerarsiDisiKisileriGetir,
    hiyerarsiSatirlariniOlustur,
} from "../../lib/yoneticiHiyerarsisi";
import { yoneticiEtiketiGorunurMu } from "../../lib/ekipDiziniGorunurluk";
import Badge from "../ui/badge/Badge";
import ProfilAvatar from "../common/ProfilAvatar";
import { bugunIzinEtiketi, bugunIzinRozetRengi } from "../../lib/dashboardIzinBakiyesi";
import {
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableRow,
} from "../ui/table";

type EkipDiziniTablosuProps = {
    kisiler: EkipDiziniKisi[];
    hiyerarsiKokleri?: HiyerarsiDugumu[];
};

/**
 * Yalnız ekip dizini response'unu gösterir; filtre ve istek state'i sayfada kalır.
 */
export default function EkipDiziniTablosu({
    kisiler,
    hiyerarsiKokleri,
}: EkipDiziniTablosuProps) {
    const { kullanici } = useAuth();
    const [acikYollar, setAcikYollar] = useState<Set<string>>(new Set());
    const yoneticiEtiketiGorunur = yoneticiEtiketiGorunurMu(kullanici?.rol);
    const hiyerarsiSatirlari = hiyerarsiKokleri
        ? hiyerarsiSatirlariniOlustur(hiyerarsiKokleri, acikYollar)
        : [];
    const gosterilecekKisiler = hiyerarsiKokleri
        ? hiyerarsiDisiKisileriGetir(kisiler, hiyerarsiKokleri)
        : kisiler;

    function dugumuAcKapat(yol: string) {
        setAcikYollar((onceki) => {
            const sonraki = new Set(onceki);

            if (sonraki.has(yol)) {
                sonraki.delete(yol);
            } else {
                sonraki.add(yol);
            }

            return sonraki;
        });
    }

    return (
        <div className="overflow-hidden rounded-xl border border-gray-200 bg-white dark:border-white/[0.05] dark:bg-white/[0.03]">
            <div className="max-w-full overflow-x-auto">
                <Table className="min-w-[680px]">
                    <TableHeader className="border-b border-gray-100 dark:border-white/[0.05]">
                        <TableRow>
                            <TableCell
                                isHeader
                                className="px-5 py-3 text-start text-theme-xs font-medium text-gray-500 dark:text-gray-400"
                            >
                                Personel
                            </TableCell>
                            <TableCell
                                isHeader
                                className="px-5 py-3 text-start text-theme-xs font-medium text-gray-500 dark:text-gray-400"
                            >
                                Departman
                            </TableCell>
                            <TableCell
                                isHeader
                                className="px-5 py-3 text-start text-theme-xs font-medium text-gray-500 dark:text-gray-400"
                            >
                                Yetenekler
                            </TableCell>
                        </TableRow>
                    </TableHeader>

                    <TableBody className="divide-y divide-gray-100 dark:divide-white/[0.05]">
                        {hiyerarsiKokleri
                            ? hiyerarsiSatirlari.map(({ dugum, yol, seviye }) => {
                                  const baglisiVar =
                                      dugum.dogrudanBaglilar.length > 0;
                                  const acik = acikYollar.has(yol);

                                  return (
                                      <TableRow key={yol}>
                                          <TableCell
                                              className="px-5 py-4 text-start sm:px-6"
                                          >
                                              <div
                                                  className="flex items-center gap-3"
                                                  style={{
                                                      marginLeft: `${seviye * 32}px`,
                                                  }}
                                              >
                                                  {baglisiVar ? (
                                                      <button
                                                          type="button"
                                                          aria-label={`${dugum.ad} ${dugum.soyad} altındakileri ${
                                                              acik ? "gizle" : "göster"
                                                          }`}
                                                          aria-expanded={acik}
                                                          onClick={() => dugumuAcKapat(yol)}
                                                          className="flex h-7 w-7 shrink-0 items-center justify-center rounded text-lg text-gray-500 hover:bg-gray-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 dark:text-gray-400 dark:hover:bg-white/[0.05]"
                                                      >
                                                          <span
                                                              aria-hidden="true"
                                                              className={`transition-transform ${
                                                                  acik
                                                                      ? "rotate-180"
                                                                      : ""
                                                              }`}
                                                          >
                                                              ⌄
                                                          </span>
                                                      </button>
                                                  ) : (
                                                      <span className="h-7 w-7 shrink-0" />
                                                  )}
                                                  <ProfilAvatar
                                                      ad={dugum.ad}
                                                      soyad={dugum.soyad}
                                                      profilFoto={dugum.profilFoto}
                                                      boyutSinifi="h-10 w-10 text-sm"
                                                      alt={`${dugum.ad} ${dugum.soyad} profil fotoğrafı`}
                                                  />
                                                  <div className="flex flex-wrap items-center gap-2">
                                                      <span className="text-theme-sm font-medium text-gray-800 dark:text-white/90">
                                                          {dugum.ad} {dugum.soyad}
                                                      </span>
                                                      {yoneticiEtiketiGorunur &&
                                                          dugum.yoneticinMi && (
                                                              <Badge color="primary" size="sm">
                                                                  Yöneticin
                                                              </Badge>
                                                          )}
                                                      {bugunIzinEtiketi(dugum.bugunIzinDurumu) && (
                                                          <Badge color={bugunIzinRozetRengi(dugum.bugunIzinDurumu)} size="sm">
                                                              {bugunIzinEtiketi(dugum.bugunIzinDurumu)}
                                                          </Badge>
                                                      )}
                                                  </div>
                                              </div>
                                          </TableCell>

                                          <TableCell className="px-5 py-4 text-start text-theme-sm text-gray-500 dark:text-gray-400">
                                              {dugum.departman}
                                          </TableCell>

                                          <TableCell className="px-5 py-4 text-start">
                                              <span className="text-theme-sm text-gray-500 dark:text-gray-400">
                                                  Yetenek bilgisi yok
                                              </span>
                                          </TableCell>
                                      </TableRow>
                                  );
                              })
                            : null}

                        {gosterilecekKisiler.map((kisi, index) => (
                                  <TableRow
                                      key={`${kisi.ad}-${kisi.soyad}-${index}`}
                                  >
                                      <TableCell className="px-5 py-4 text-start sm:px-6">
                                          <div className="flex items-center gap-3">
                                              <ProfilAvatar
                                                  ad={kisi.ad}
                                                  soyad={kisi.soyad}
                                                  profilFoto={kisi.profilFoto}
                                                  boyutSinifi="h-10 w-10 text-sm"
                                                  alt={`${kisi.ad} ${kisi.soyad} profil fotoğrafı`}
                                              />
                                              <div className="flex flex-wrap items-center gap-2">
                                                  <span className="text-theme-sm font-medium text-gray-800 dark:text-white/90">
                                                      {kisi.ad} {kisi.soyad}
                                                  </span>
                                                  {yoneticiEtiketiGorunur &&
                                                      kisi.yoneticinMi && (
                                                          <Badge color="primary" size="sm">
                                                              Yöneticin
                                                          </Badge>
                                                      )}
                                                  {bugunIzinEtiketi(kisi.bugunIzinDurumu) && (
                                                      <Badge color={bugunIzinRozetRengi(kisi.bugunIzinDurumu)} size="sm">
                                                          {bugunIzinEtiketi(kisi.bugunIzinDurumu)}
                                                      </Badge>
                                                  )}
                                              </div>
                                          </div>
                                      </TableCell>

                                      <TableCell className="px-5 py-4 text-start text-theme-sm text-gray-500 dark:text-gray-400">
                                          {kisi.departman}
                                      </TableCell>

                                      <TableCell className="px-5 py-4 text-start">
                                          {kisi.yetenekler.length === 0 ? (
                                              <span className="text-theme-sm text-gray-500 dark:text-gray-400">
                                                  Yetenek bilgisi yok
                                              </span>
                                          ) : (
                                              <div className="flex flex-wrap gap-2">
                                                  {kisi.yetenekler.map((yetenek) => (
                                                      <span
                                                          key={yetenek.id}
                                                          className="rounded-full border border-gray-200 bg-gray-50 px-2.5 py-1 text-xs font-medium text-gray-700 dark:border-gray-700 dark:bg-white/[0.05] dark:text-gray-300"
                                                      >
                                                          {yetenek.ad}
                                                      </span>
                                                  ))}
                                              </div>
                                          )}
                                      </TableCell>
                                  </TableRow>
                              ))}
                    </TableBody>
                </Table>
            </div>
        </div>
    );
}
