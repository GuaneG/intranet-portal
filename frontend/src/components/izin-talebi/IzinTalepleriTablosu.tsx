import { useState } from "react";
import Badge from "../ui/badge/Badge";
import IzinNotuModali from "./IzinNotuModali";
import { Table, TableBody, TableCell, TableHeader, TableRow } from "../ui/table";
import type { IzinTalepDurumu, IzinTalepleriSayfasi } from "../../types/izinTalebi";

type IzinTalepleriTablosuProps = {
    sayfaVerisi: IzinTalepleriSayfasi | null;
    durum: "" | IzinTalepDurumu;
    yukleniyor: boolean;
    hata: string | null;
    onDurumDegistir: (durum: "" | IzinTalepDurumu) => void;
    onTekrarDene: () => void;
    onOncekiSayfa: () => void;
    onSonrakiSayfa: () => void;
    iptalEdilenTalepId: string | null;
    onIptalIstegi: (talepId: string) => void;
};

type AcikNot = {
    baslik: string;
    aciklama: string;
    not: string;
};

const DURUM_SECENEKLERI: Array<{ deger: "" | IzinTalepDurumu; etiket: string }> = [
    { deger: "", etiket: "Tüm durumlar" },
    { deger: "ONAY_BEKLIYOR", etiket: "Onay bekliyor" },
    { deger: "ONAYLANDI", etiket: "Onaylandı" },
    { deger: "REDDEDILDI", etiket: "Reddedildi" },
    { deger: "IPTAL_EDILDI", etiket: "İptal edildi" },
];

function durumRenginiGetir(durum: IzinTalepDurumu) {
    if (durum === "ONAYLANDI") return "success" as const;
    if (durum === "REDDEDILDI") return "error" as const;
    if (durum === "IPTAL_EDILDI") return "light" as const;
    return "warning" as const;
}

function tarihiBicimlendir(tarih: string) {
    return new Intl.DateTimeFormat("tr-TR", { day: "2-digit", month: "long", year: "numeric" }).format(new Date(`${tarih}T00:00:00`));
}

function talepTarihiniBicimlendir(tarih: string) {
    return new Intl.DateTimeFormat("tr-TR", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }).format(new Date(tarih));
}

function kararVereniBicimlendir(ad: string, soyad: string) {
    return `${ad} ${soyad}`;
}

function gunSayisiniBicimlendir(deger: number) {
    return new Intl.NumberFormat("tr-TR", { maximumFractionDigits: 1 }).format(deger);
}

/** Kullanıcının kendi izin taleplerini backend sayfalamasını koruyarak gösterir. */
export default function IzinTalepleriTablosu({ sayfaVerisi, durum, yukleniyor, hata, onDurumDegistir, onTekrarDene, onOncekiSayfa, onSonrakiSayfa, iptalEdilenTalepId, onIptalIstegi }: IzinTalepleriTablosuProps) {
    const [acikNot, setAcikNot] = useState<AcikNot | null>(null);
    const ilkYukleme = yukleniyor && sayfaVerisi === null && hata === null;
    const talepler = sayfaVerisi?.icerik ?? [];

    return <section className="rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]">
        <div className="flex flex-col gap-4 border-b border-gray-100 px-5 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-6 dark:border-gray-800">
            <div><h3 className="text-base font-semibold text-gray-800 dark:text-white/90">Taleplerim</h3><p className="mt-1 text-sm text-gray-500 dark:text-gray-400">İzin talepleriniz en yeni kayıttan başlayarak listelenir.</p></div>
            <div className="w-full sm:w-52"><label htmlFor="izin-durum-filtresi" className="sr-only">Talep durumu</label><select id="izin-durum-filtresi" value={durum} disabled={yukleniyor} onChange={(event) => onDurumDegistir(event.target.value as "" | IzinTalepDurumu)} className="h-10 w-full rounded-lg border border-gray-300 bg-transparent px-3 text-sm text-gray-800 outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90">{DURUM_SECENEKLERI.map((secenek) => <option key={secenek.deger || "tumu"} value={secenek.deger}>{secenek.etiket}</option>)}</select></div>
        </div>

        {hata ? <div className="p-6"><p role="alert" className="text-sm text-error-600 dark:text-error-400">{hata}</p><button type="button" disabled={yukleniyor} onClick={onTekrarDene} className="mt-3 text-sm font-medium text-brand-500 underline disabled:cursor-not-allowed disabled:opacity-50">Tekrar dene</button></div> : ilkYukleme ? <p role="status" className="p-6 text-sm text-gray-500 dark:text-gray-400">İzin talepleriniz yükleniyor…</p> : talepler.length === 0 ? <p className="p-6 text-sm text-gray-500 dark:text-gray-400">Bu filtreyle eşleşen izin talebiniz bulunmuyor.</p> : <div className="max-w-full overflow-x-auto"><Table><TableHeader className="border-b border-gray-100 dark:border-white/[0.05]"><TableRow><TableCell isHeader className="px-5 py-3 text-start text-xs font-medium text-gray-500 dark:text-gray-400">İzin türü</TableCell><TableCell isHeader className="px-5 py-3 text-start text-xs font-medium text-gray-500 dark:text-gray-400">Tarih aralığı</TableCell><TableCell isHeader className="px-5 py-3 text-start text-xs font-medium text-gray-500 dark:text-gray-400">İş günü</TableCell><TableCell isHeader className="px-5 py-3 text-start text-xs font-medium text-gray-500 dark:text-gray-400">Durum</TableCell><TableCell isHeader className="hidden px-5 py-3 text-start text-xs font-medium text-gray-500 xl:table-cell dark:text-gray-400">Talep tarihi</TableCell></TableRow></TableHeader><TableBody className="divide-y divide-gray-100 dark:divide-white/[0.05]">{talepler.map((talep) => { const iptalSuruyor = iptalEdilenTalepId === talep.id; return <TableRow key={talep.id}><TableCell className="px-5 py-4 text-sm font-medium text-gray-800 dark:text-white/90"><p>{talep.izinTuru}</p>{talep.yoneticiyeNot && <button type="button" onClick={() => setAcikNot({ baslik: "Yöneticiye notun", aciklama: "İzin talebini oluştururken eklediğin not.", not: talep.yoneticiyeNot ?? "" })} className="mt-2 text-xs font-medium text-brand-500 hover:text-brand-600 hover:underline">Notumu gör</button>}</TableCell><TableCell className="px-5 py-4 text-sm text-gray-600 dark:text-gray-300">{tarihiBicimlendir(talep.baslangicTarihi)} – {tarihiBicimlendir(talep.bitisTarihi)}</TableCell><TableCell className="px-5 py-4 text-sm text-gray-600 dark:text-gray-300">{gunSayisiniBicimlendir(talep.isGunuSayisi)} gün</TableCell><TableCell className="px-5 py-4"><Badge size="sm" color={durumRenginiGetir(talep.durum)}>{talep.durumAdi}</Badge>{talep.durum === "ONAY_BEKLIYOR" && <button type="button" disabled={iptalSuruyor} onClick={() => onIptalIstegi(talep.id)} className="mt-2 block text-xs font-medium text-error-600 hover:text-error-700 hover:underline disabled:cursor-not-allowed disabled:opacity-50 dark:text-error-400">{iptalSuruyor ? "İptal ediliyor…" : "İptal et"}</button>}{talep.durum === "REDDEDILDI" && talep.kararNotu && <button type="button" onClick={() => setAcikNot({ baslik: "Red nedeni", aciklama: "İzin talebinin reddedilme nedeni.", not: talep.kararNotu ?? "" })} className="mt-2 block text-xs font-medium text-error-600 hover:text-error-700 hover:underline dark:text-error-400">Red nedenini gör</button>}{talep.kararVeren && <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">İşlem: {kararVereniBicimlendir(talep.kararVeren.ad, talep.kararVeren.soyad)}{talep.islemTarihi ? ` · ${talepTarihiniBicimlendir(talep.islemTarihi)}` : ""}</p>}</TableCell><TableCell className="hidden px-5 py-4 text-sm text-gray-500 xl:table-cell dark:text-gray-400">{talepTarihiniBicimlendir(talep.talepTarihi)}</TableCell></TableRow>; })}</TableBody></Table></div>}

        {sayfaVerisi && <div className="flex flex-col gap-3 border-t border-gray-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between dark:border-gray-800"><p className="text-sm text-gray-500 dark:text-gray-400">Toplam {sayfaVerisi.toplamKayit} kayıt · Sayfa {sayfaVerisi.sayfa + 1}/{Math.max(sayfaVerisi.toplamSayfa, 1)}</p><div className="flex gap-3"><button type="button" disabled={yukleniyor || sayfaVerisi.sayfa === 0} onClick={onOncekiSayfa} className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300">Önceki</button><button type="button" disabled={yukleniyor || sayfaVerisi.sayfa + 1 >= sayfaVerisi.toplamSayfa} onClick={onSonrakiSayfa} className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300">Sonraki</button></div></div>}
        <IzinNotuModali isOpen={acikNot !== null} personelAdi="" not={acikNot?.not ?? ""} baslik={acikNot?.baslik} aciklama={acikNot?.aciklama} onKapat={() => setAcikNot(null)} />
    </section>;
}
