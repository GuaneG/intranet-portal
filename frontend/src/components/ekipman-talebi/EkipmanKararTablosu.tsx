import Badge from "../ui/badge/Badge";
import ProfilAvatar from "../common/ProfilAvatar";
import { Table, TableBody, TableCell, TableHeader, TableRow } from "../ui/table";
import type { EkipmanTalepDurumu, KararEkipmanTalebi, SayfaliKararEkipmanTalepResponse } from "../../types/ekipman";

type Props = {
    sayfaVerisi: SayfaliKararEkipmanTalepResponse | null;
    yukleniyor: boolean;
    hata: string | null;
    islemYapilanTalepId: string | null;
    pasifEtiketiGoster: boolean;
    onTekrarDene: () => void;
    onOncekiSayfa: () => void;
    onSonrakiSayfa: () => void;
    onAciklamayiGor: (talep: KararEkipmanTalebi) => void;
    onRedNedeniniGor: (talep: KararEkipmanTalebi) => void;
    onOnayIstegi: (talep: KararEkipmanTalebi) => void;
    onRedIstegi: (talep: KararEkipmanTalebi) => void;
};

function durumRenginiGetir(durum: EkipmanTalepDurumu) {
    if (durum === "ONAYLANDI") return "success" as const;
    if (durum === "REDDEDILDI") return "error" as const;
    if (durum === "IPTAL_EDILDI") return "light" as const;
    return "warning" as const;
}

function tarihiBicimlendir(tarih: string) {
    return new Intl.DateTimeFormat("tr-TR", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }).format(new Date(tarih));
}

/** Yönetici ve Admin'in backend kapsamından gelen ekipman taleplerini gösterir. */
export default function EkipmanKararTablosu({ sayfaVerisi, yukleniyor, hata, islemYapilanTalepId, pasifEtiketiGoster, onTekrarDene, onOncekiSayfa, onSonrakiSayfa, onAciklamayiGor, onRedNedeniniGor, onOnayIstegi, onRedIstegi }: Props) {
    const ilkYukleme = yukleniyor && !sayfaVerisi && !hata;
    const talepler = sayfaVerisi?.icerik ?? [];

    return <section className="rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]">
        <div className="border-b border-gray-100 px-5 py-5 sm:px-6 dark:border-gray-800"><h3 className="text-base font-semibold text-gray-800 dark:text-white/90">Ekipman talepleri</h3><p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Yalnız backend'in erişiminize açtığı talepler gösterilir.</p></div>
        {hata ? <div className="p-6"><p role="alert" className="text-sm text-error-600 dark:text-error-400">{hata}</p><button type="button" onClick={onTekrarDene} disabled={yukleniyor} className="mt-3 text-sm font-medium text-brand-500 underline disabled:cursor-not-allowed disabled:opacity-50">Tekrar dene</button></div> : ilkYukleme ? <p role="status" className="p-6 text-sm text-gray-500 dark:text-gray-400">Ekipman talepleri yükleniyor…</p> : talepler.length === 0 ? <p className="p-6 text-sm text-gray-500 dark:text-gray-400">Bu filtreyle eşleşen ekipman talebi bulunmuyor.</p> : <div className="max-w-full overflow-x-auto"><Table><TableHeader className="border-b border-gray-100 dark:border-white/[0.05]"><TableRow><TableCell isHeader className="px-5 py-3 text-start text-xs font-medium text-gray-500 dark:text-gray-400">Personel</TableCell><TableCell isHeader className="px-5 py-3 text-start text-xs font-medium text-gray-500 dark:text-gray-400">Ekipman</TableCell><TableCell isHeader className="hidden px-5 py-3 text-start text-xs font-medium text-gray-500 lg:table-cell dark:text-gray-400">Talep tarihi</TableCell><TableCell isHeader className="px-5 py-3 text-start text-xs font-medium text-gray-500 dark:text-gray-400">Durum</TableCell><TableCell isHeader className="px-5 py-3 text-end text-xs font-medium text-gray-500 dark:text-gray-400">İşlem</TableCell></TableRow></TableHeader><TableBody className="divide-y divide-gray-100 dark:divide-white/[0.05]">{talepler.map((talep) => { const islemSuruyor = islemYapilanTalepId === talep.id; const kararBekliyor = talep.durum === "ONAY_BEKLIYOR"; return <TableRow key={talep.id}><TableCell className="px-5 py-4"><div className="flex items-center gap-3"><ProfilAvatar ad={talep.talepSahibi.ad} soyad={talep.talepSahibi.soyad} profilFoto={talep.talepSahibi.profilFoto} boyutSinifi="size-10" alt={`${talep.talepSahibi.ad} ${talep.talepSahibi.soyad} profil fotoğrafı`} /><div><div className="flex flex-wrap items-center gap-2 text-sm font-medium text-gray-800 dark:text-white/90"><span>{talep.talepSahibi.ad} {talep.talepSahibi.soyad}</span>{pasifEtiketiGoster && !talep.talepSahibi.aktifMi && <Badge size="sm" color="light">Pasif</Badge>}</div>{talep.talepSahibi.departman && <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">{talep.talepSahibi.departman}</p>}</div></div></TableCell><TableCell className="px-5 py-4"><p className="text-sm font-medium text-gray-800 dark:text-white/90">{talep.ekipmanTipi}</p>{talep.aciklama && <button type="button" onClick={() => onAciklamayiGor(talep)} className="mt-2 text-xs font-medium text-brand-500 hover:text-brand-600 hover:underline">Açıklamayı gör</button>}{talep.zimmet && <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">Zimmet: {talep.zimmet.marka} {talep.zimmet.model ?? ""} · {talep.zimmet.seriNo}</p>}</TableCell><TableCell className="hidden px-5 py-4 text-sm text-gray-500 lg:table-cell dark:text-gray-400">{tarihiBicimlendir(talep.talepTarihi)}</TableCell><TableCell className="px-5 py-4"><Badge size="sm" color={durumRenginiGetir(talep.durum)}>{talep.durumAdi}</Badge>{talep.kararNotu && <button type="button" onClick={() => onRedNedeniniGor(talep)} className="mt-2 block text-xs font-medium text-error-600 hover:text-error-700 hover:underline dark:text-error-400">Red nedenini gör</button>}</TableCell><TableCell className="px-5 py-4 text-end">{kararBekliyor ? <div className="flex justify-end gap-2"><button type="button" disabled={islemSuruyor} onClick={() => onOnayIstegi(talep)} className="rounded-lg bg-brand-500 px-3 py-2 text-xs font-medium text-white hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50">{islemSuruyor ? "İşleniyor…" : "Onayla"}</button><button type="button" disabled={islemSuruyor} onClick={() => onRedIstegi(talep)} className="rounded-lg border border-error-300 px-3 py-2 text-xs font-medium text-error-600 hover:bg-error-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-error-500/40 dark:text-error-400">Reddet</button></div> : <span className="text-xs text-gray-400 dark:text-gray-500">İşlem yok</span>}</TableCell></TableRow>; })}</TableBody></Table></div>}
        {sayfaVerisi && <div className="flex flex-col gap-3 border-t border-gray-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between dark:border-gray-800"><p className="text-sm text-gray-500 dark:text-gray-400">Toplam {sayfaVerisi.toplamKayit} kayıt · Sayfa {sayfaVerisi.sayfa + 1}/{Math.max(sayfaVerisi.toplamSayfa, 1)}</p><div className="flex gap-3"><button type="button" disabled={yukleniyor || sayfaVerisi.sayfa === 0} onClick={onOncekiSayfa} className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300">Önceki</button><button type="button" disabled={yukleniyor || sayfaVerisi.sayfa + 1 >= sayfaVerisi.toplamSayfa} onClick={onSonrakiSayfa} className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300">Sonraki</button></div></div>}
    </section>;
}
