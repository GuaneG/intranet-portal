import { Link } from "react-router";
import Badge from "../ui/badge/Badge";
import { saatiBicimlendir } from "../../lib/rezervasyonTakvimi";
import type { OdaDoluluk } from "../../types/rezervasyon";

type OdaDolulukKartiProps = {
  odalar: OdaDoluluk[];
  yukleniyor: boolean;
};

const DURUM_BILGISI = {
  MUSAIT: { etiket: "Müsait", renk: "success" as const },
  DOLU: { etiket: "Dolu", renk: "error" as const },
  KAPALI: { etiket: "Kapalı", renk: "warning" as const },
};

/** Dashboard özetinden gelen, bugünkü oda doluluk bilgisini gösterir. */
export default function OdaDolulukKarti({ odalar, yukleniyor }: OdaDolulukKartiProps) {
  if (yukleniyor) {
    return <div aria-label="Oda doluluk bilgisi yükleniyor" className="animate-pulse rounded-2xl border border-gray-200 bg-white p-6 dark:border-gray-800 dark:bg-white/[0.03]"><div className="h-5 w-36 rounded bg-gray-100 dark:bg-white/[0.08]" /><div className="mt-5 space-y-4">{Array.from({ length: 2 }, (_, index) => <div key={index} className="h-20 rounded-xl bg-gray-100 dark:bg-white/[0.08]" />)}</div></div>;
  }

  return <Link to="/talepler/rezervasyonlar" aria-label="Rezervasyon taleplerimi aç" className="group block rounded-2xl border border-gray-200 bg-white p-5 transition-colors hover:border-brand-300 hover:bg-brand-50/30 focus:outline-none focus:ring-3 focus:ring-brand-500/10 dark:border-gray-800 dark:bg-white/[0.03] dark:hover:border-brand-500/50 dark:hover:bg-brand-500/5 sm:p-6">
    <div className="flex items-start justify-between gap-3"><div><h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">Oda doluluğu</h2><p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Bugünkü toplantı odası durumu.</p></div><span className="text-xs font-medium text-brand-500 group-hover:underline">Rezervasyonlar</span></div>
    {odalar.length === 0 ? <p className="py-8 text-center text-sm text-gray-500 dark:text-gray-400">Gösterilecek oda bilgisi bulunmuyor.</p> : <div className="mt-5 space-y-3">{odalar.map((oda) => {
      const durum = DURUM_BILGISI[oda.anlikDurum];
      return <div key={oda.odaId} className="rounded-xl border border-gray-100 p-3.5 dark:border-gray-800"><div className="flex items-center justify-between gap-3"><div><p className="text-sm font-medium text-gray-800 dark:text-white/90">{oda.odaAd}</p><p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">{oda.kapasite} kişilik</p></div><Badge size="sm" color={durum.renk}>{durum.etiket}</Badge></div><div className="mt-3 h-1.5 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800"><div className="h-full rounded-full bg-brand-500" style={{ width: `${Math.min(100, Math.max(0, oda.dolulukYuzdesi))}%` }} /></div>{oda.doluAraliklar.length > 0 && <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">Dolu: {oda.doluAraliklar.map((aralik) => `${saatiBicimlendir(aralik.baslangicSaat)}–${saatiBicimlendir(aralik.bitisSaat)}`).join(", ")}</p>}</div>;
    })}</div>}
  </Link>;
}
