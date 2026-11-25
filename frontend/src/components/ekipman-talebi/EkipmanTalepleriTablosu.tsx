import { useState } from "react";
import IzinNotuModali from "../izin-talebi/IzinNotuModali";
import Badge from "../ui/badge/Badge";
import { Table, TableBody, TableCell, TableHeader, TableRow } from "../ui/table";
import type { EkipmanTalebi, SayfaliEkipmanTalepResponse } from "../../types/ekipman";

type Props = {
    sayfaVerisi: SayfaliEkipmanTalepResponse | null;
    yukleniyor: boolean;
    hata: string | null;
    islemYapilanTalepId: string | null;
    onTekrarDene: () => void;
    onOncekiSayfa: () => void;
    onSonrakiSayfa: () => void;
    onDuzenle: (talep: EkipmanTalebi) => void;
    onIptal: (talepId: string) => void;
};

function durumRenginiGetir(durum: EkipmanTalebi["durum"]) {
    if (durum === "ONAYLANDI") return "success" as const;
    if (durum === "REDDEDILDI") return "error" as const;
    if (durum === "IPTAL_EDILDI") return "light" as const;
    return "warning" as const;
}

function tarihiBicimlendir(tarih: string) {
    return new Intl.DateTimeFormat("tr-TR", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }).format(new Date(tarih));
}

/** Kullanıcının kendi ekipman talep geçmişini, backend sayfalamasını koruyarak gösterir. */
export default function EkipmanTalepleriTablosu({ sayfaVerisi, yukleniyor, hata, islemYapilanTalepId, onTekrarDene, onOncekiSayfa, onSonrakiSayfa, onDuzenle, onIptal }: Props) {
    const [acikNot, setAcikNot] = useState<{ baslik: string; aciklama: string; not: string } | null>(null);
    const ilkYukleme = yukleniyor && !sayfaVerisi && !hata;
    const talepler = sayfaVerisi?.icerik ?? [];

    return <section className="rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]">
        <div className="border-b border-gray-100 px-5 py-5 sm:px-6 dark:border-gray-800"><h3 className="text-base font-semibold text-gray-800 dark:text-white/90">Taleplerim</h3><p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Ekipman talepleriniz en yeni kayıttan başlayarak listelenir.</p></div>
        {hata ? <div className="p-6"><p role="alert" className="text-sm text-error-600 dark:text-error-400">{hata}</p><button type="button" onClick={onTekrarDene} disabled={yukleniyor} className="mt-3 text-sm font-medium text-brand-500 underline disabled:cursor-not-allowed disabled:opacity-50">Tekrar dene</button></div> : ilkYukleme ? <p role="status" className="p-6 text-sm text-gray-500 dark:text-gray-400">Ekipman talepleriniz yükleniyor…</p> : talepler.length === 0 ? <p className="p-6 text-sm text-gray-500 dark:text-gray-400">Bu filtreyle eşleşen ekipman talebiniz bulunmuyor.</p> : <div className="max-w-full overflow-x-auto"><Table><TableHeader className="border-b border-gray-100 dark:border-white/[0.05]"><TableRow><TableCell isHeader className="px-5 py-3 text-start text-xs font-medium text-gray-500 dark:text-gray-400">Ekipman tipi</TableCell><TableCell isHeader className="px-5 py-3 text-start text-xs font-medium text-gray-500 dark:text-gray-400">Durum</TableCell><TableCell isHeader className="hidden px-5 py-3 text-start text-xs font-medium text-gray-500 lg:table-cell dark:text-gray-400">Talep tarihi</TableCell><TableCell isHeader className="px-5 py-3 text-end text-xs font-medium text-gray-500 dark:text-gray-400">İşlem</TableCell></TableRow></TableHeader><TableBody className="divide-y divide-gray-100 dark:divide-white/[0.05]">{talepler.map((talep) => { const bekliyor = talep.durum === "ONAY_BEKLIYOR"; const islemSuruyor = islemYapilanTalepId === talep.id; return <TableRow key={talep.id}><TableCell className="px-5 py-4"><p className="text-sm font-medium text-gray-800 dark:text-white/90">{talep.ekipmanTipi}</p>{talep.aciklama && <button type="button" onClick={() => setAcikNot({ baslik: "Talep açıklaması", aciklama: "Ekipman talebini oluştururken eklediğiniz açıklama.", not: talep.aciklama ?? "" })} className="mt-2 text-xs font-medium text-brand-500 hover:text-brand-600 hover:underline">Açıklamayı gör</button>}{talep.zimmet && <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">Zimmet: {talep.zimmet.marka} {talep.zimmet.model ?? ""} · {talep.zimmet.seriNo}</p>}</TableCell><TableCell className="px-5 py-4"><Badge size="sm" color={durumRenginiGetir(talep.durum)}>{talep.durumAdi}</Badge>{talep.kararNotu && <button type="button" onClick={() => setAcikNot({ baslik: "Red nedeni", aciklama: "Ekipman talebiniz için verilen karar notu.", not: talep.kararNotu ?? "" })} className="mt-2 block text-xs font-medium text-error-600 hover:text-error-700 hover:underline dark:text-error-400">Red nedenini gör</button>}</TableCell><TableCell className="hidden px-5 py-4 text-sm text-gray-500 lg:table-cell dark:text-gray-400">{tarihiBicimlendir(talep.talepTarihi)}</TableCell><TableCell className="px-5 py-4 text-end">{bekliyor ? <div className="flex justify-end gap-2"><button type="button" onClick={() => onDuzenle(talep)} disabled={islemSuruyor} className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-xs font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300">Düzenle</button><button type="button" onClick={() => onIptal(talep.id)} disabled={islemSuruyor} className="rounded-lg border border-error-300 px-3 py-2 text-xs font-medium text-error-600 hover:bg-error-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-error-500/40 dark:text-error-400">{islemSuruyor ? "İptal ediliyor…" : "İptal et"}</button></div> : <span className="text-xs text-gray-400 dark:text-gray-500">İşlem yok</span>}</TableCell></TableRow>; })}</TableBody></Table></div>}
        {sayfaVerisi && <div className="flex flex-col gap-3 border-t border-gray-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between dark:border-gray-800"><p className="text-sm text-gray-500 dark:text-gray-400">Toplam {sayfaVerisi.toplamKayit} kayıt · Sayfa {sayfaVerisi.sayfa + 1}/{Math.max(sayfaVerisi.toplamSayfa, 1)}</p><div className="flex gap-3"><button type="button" disabled={yukleniyor || sayfaVerisi.sayfa === 0} onClick={onOncekiSayfa} className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300">Önceki</button><button type="button" disabled={yukleniyor || sayfaVerisi.sayfa + 1 >= sayfaVerisi.toplamSayfa} onClick={onSonrakiSayfa} className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300">Sonraki</button></div></div>}
        <IzinNotuModali isOpen={acikNot !== null} personelAdi="" not={acikNot?.not ?? ""} baslik={acikNot?.baslik} aciklama={acikNot?.aciklama} onKapat={() => setAcikNot(null)} />
    </section>;
}
