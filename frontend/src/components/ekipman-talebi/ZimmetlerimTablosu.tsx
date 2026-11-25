import Badge from "../ui/badge/Badge";
import { Table, TableBody, TableCell, TableHeader, TableRow } from "../ui/table";
import type { SayfaliZimmetResponse } from "../../types/ekipman";

type Props = {
    sayfaVerisi: SayfaliZimmetResponse | null;
    yukleniyor: boolean;
    hata: string | null;
    onTekrarDene: () => void;
    onOncekiSayfa: () => void;
    onSonrakiSayfa: () => void;
};

function tarihiBicimlendir(tarih: string) {
    return new Intl.DateTimeFormat("tr-TR", { day: "2-digit", month: "short", year: "numeric" }).format(new Date(tarih));
}

/** Kullanıcının açık ve kapatılmış zimmetlerini tek geçmiş tablosunda sunar. */
export default function ZimmetlerimTablosu({ sayfaVerisi, yukleniyor, hata, onTekrarDene, onOncekiSayfa, onSonrakiSayfa }: Props) {
    const ilkYukleme = yukleniyor && !sayfaVerisi && !hata;
    const zimmetler = sayfaVerisi?.icerik ?? [];

    return <section className="rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]">
        <div className="border-b border-gray-100 px-5 py-5 sm:px-6 dark:border-gray-800"><h3 className="text-base font-semibold text-gray-800 dark:text-white/90">Zimmetlerim</h3><p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Açık ve iade edilmiş ekipman zimmetleriniz burada listelenir.</p></div>
        {hata ? <div className="p-6"><p role="alert" className="text-sm text-error-600 dark:text-error-400">{hata}</p><button type="button" onClick={onTekrarDene} disabled={yukleniyor} className="mt-3 text-sm font-medium text-brand-500 underline disabled:cursor-not-allowed disabled:opacity-50">Tekrar dene</button></div> : ilkYukleme ? <p role="status" className="p-6 text-sm text-gray-500 dark:text-gray-400">Zimmetleriniz yükleniyor…</p> : zimmetler.length === 0 ? <p className="p-6 text-sm text-gray-500 dark:text-gray-400">Kayıtlı zimmetiniz bulunmuyor.</p> : <div className="max-w-full overflow-x-auto"><Table><TableHeader className="border-b border-gray-100 dark:border-white/[0.05]"><TableRow><TableCell isHeader className="px-5 py-3 text-start text-xs font-medium text-gray-500 dark:text-gray-400">Ekipman</TableCell><TableCell isHeader className="px-5 py-3 text-start text-xs font-medium text-gray-500 dark:text-gray-400">Seri no</TableCell><TableCell isHeader className="hidden px-5 py-3 text-start text-xs font-medium text-gray-500 md:table-cell dark:text-gray-400">Teslim tarihi</TableCell><TableCell isHeader className="px-5 py-3 text-start text-xs font-medium text-gray-500 dark:text-gray-400">Durum</TableCell></TableRow></TableHeader><TableBody className="divide-y divide-gray-100 dark:divide-white/[0.05]">{zimmetler.map((zimmet) => { const acik = zimmet.iadeTarihi === null; return <TableRow key={zimmet.id}><TableCell className="px-5 py-4"><p className="text-sm font-medium text-gray-800 dark:text-white/90">{zimmet.ekipmanTipi}</p><p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{zimmet.marka} {zimmet.model ?? ""}</p></TableCell><TableCell className="px-5 py-4 text-sm text-gray-600 dark:text-gray-300">{zimmet.seriNo}</TableCell><TableCell className="hidden px-5 py-4 text-sm text-gray-500 md:table-cell dark:text-gray-400">{tarihiBicimlendir(zimmet.teslimTarihi)}</TableCell><TableCell className="px-5 py-4"><Badge size="sm" color={acik ? "success" : "light"}>{acik ? "Açık zimmet" : "İade edildi"}</Badge>{zimmet.iadeTarihi && <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">İade: {tarihiBicimlendir(zimmet.iadeTarihi)}</p>}</TableCell></TableRow>; })}</TableBody></Table></div>}
        {sayfaVerisi && <div className="flex flex-col gap-3 border-t border-gray-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between dark:border-gray-800"><p className="text-sm text-gray-500 dark:text-gray-400">Toplam {sayfaVerisi.toplamKayit} kayıt · Sayfa {sayfaVerisi.sayfa + 1}/{Math.max(sayfaVerisi.toplamSayfa, 1)}</p><div className="flex gap-3"><button type="button" disabled={yukleniyor || sayfaVerisi.sayfa === 0} onClick={onOncekiSayfa} className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300">Önceki</button><button type="button" disabled={yukleniyor || sayfaVerisi.sayfa + 1 >= sayfaVerisi.toplamSayfa} onClick={onSonrakiSayfa} className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300">Sonraki</button></div></div>}
    </section>;
}
