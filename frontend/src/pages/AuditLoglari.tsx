import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { Link } from "react-router";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import Badge from "../components/ui/badge/Badge";
import { Table, TableBody, TableCell, TableHeader, TableRow } from "../components/ui/table";
import { ApiError, auditLoglariniGetir } from "../lib/api";
import { AUDIT_EYLEM_SECENEKLERI, auditAktoruEtiketi, auditEylemEtiketi, auditHataMesaji, auditHedefEtiketi, auditTarihiniTurkiyeSaatindeBicimlendir } from "../lib/auditLog";
import type { AuditLogFiltreleri, AuditLogSayfasi } from "../types/auditLog";

const SAYFA_BOYUTU = 20;

type FiltreFormu = Omit<AuditLogFiltreleri, "page" | "size">;

const BOS_FILTRELER: FiltreFormu = {
    baslangicTarihi: "",
    bitisTarihi: "",
    eylemTipi: "",
    aktorPersonelId: "",
    hedefPersonelId: "",
};

function hataMesajiniGetir(error: unknown) {
    if (error instanceof ApiError) {
        return auditHataMesaji(error.status, error.message);
    }

    return "Audit kayıtları alınamadı. Lütfen tekrar deneyin.";
}

/** Admin'in backend tarafından sıralanmış audit kayıtlarını filtreleyerek görüntüler. */
export default function AuditLoglari() {
    const [form, setForm] = useState<FiltreFormu>(BOS_FILTRELER);
    const [uygulananFiltreler, setUygulananFiltreler] = useState<FiltreFormu>(BOS_FILTRELER);
    const [page, setPage] = useState(0);
    const [sayfaVerisi, setSayfaVerisi] = useState<AuditLogSayfasi | null>(null);
    const [yukleniyor, setYukleniyor] = useState(false);
    const [hata, setHata] = useState<string | null>(null);
    const [yetkisiz, setYetkisiz] = useState(false);
    const sonIstekNumarasi = useRef(0);

    const kayitlariYukle = useCallback(async (filtreler: FiltreFormu, istenenPage: number) => {
        const istekNumarasi = ++sonIstekNumarasi.current;
        setYukleniyor(true);
        setHata(null);

        try {
            const sonuc = await auditLoglariniGetir({
                ...filtreler,
                aktorPersonelId: filtreler.aktorPersonelId?.trim(),
                hedefPersonelId: filtreler.hedefPersonelId?.trim(),
                page: istenenPage,
                size: SAYFA_BOYUTU,
            });
            if (istekNumarasi === sonIstekNumarasi.current) setSayfaVerisi(sonuc);
        } catch (error) {
            if (istekNumarasi === sonIstekNumarasi.current) {
                setSayfaVerisi(null);
                setHata(hataMesajiniGetir(error));
                if (error instanceof ApiError && error.status === 403) setYetkisiz(true);
            }
        } finally {
            if (istekNumarasi === sonIstekNumarasi.current) setYukleniyor(false);
        }
    }, []);

    useEffect(() => {
        void kayitlariYukle(uygulananFiltreler, page);
    }, [kayitlariYukle, page, uygulananFiltreler]);

    const filtreyiUygula = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (yukleniyor) return;
        setPage(0);
        setUygulananFiltreler({ ...form });
    };

    const filtreleriTemizle = () => {
        if (yukleniyor) return;
        setForm(BOS_FILTRELER);
        setUygulananFiltreler(BOS_FILTRELER);
        setPage(0);
    };

    if (yetkisiz) {
        return <><PageMeta title="Yetkisiz Erişim | Intranet Portal" description="Audit kayıtları için erişim yetkisi yok" /><PageBreadcrumb pageTitle="Audit Log" /><section className="rounded-2xl border border-error-200 bg-error-50 p-6 text-error-700 dark:border-error-500/20 dark:bg-error-500/10 dark:text-error-400"><h2 className="text-lg font-semibold">Yetkisiz erişim</h2><p className="mt-2 text-sm">Audit kayıtlarını görüntüleme yetkiniz yok.</p><Link to="/" className="mt-4 inline-block text-sm font-medium underline">Ana sayfaya dön</Link></section></>;
    }

    const kayitlar = sayfaVerisi?.icerik ?? [];
    const ilkYukleme = yukleniyor && sayfaVerisi === null && hata === null;

    return <>
        <PageMeta title="Audit Log | Intranet Portal" description="Sistem işlem kayıtlarını görüntüleme ekranı" />
        <PageBreadcrumb pageTitle="Audit Log" />
        <div className="space-y-6">
            <div><h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">Audit Log</h2><p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Sistem işlemlerini en yeni kayıttan başlayarak takip edin.</p></div>
            <section className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] sm:p-6">
                <form onSubmit={filtreyiUygula} className="space-y-5">
                    <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-5">
                        <div><label htmlFor="audit-baslangic" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Başlangıç tarihi</label><input id="audit-baslangic" type="date" value={form.baslangicTarihi} disabled={yukleniyor} onChange={(event) => setForm((onceki) => ({ ...onceki, baslangicTarihi: event.target.value }))} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-3 text-sm text-gray-800 outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" /></div>
                        <div><label htmlFor="audit-bitis" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Bitiş tarihi</label><input id="audit-bitis" type="date" value={form.bitisTarihi} disabled={yukleniyor} onChange={(event) => setForm((onceki) => ({ ...onceki, bitisTarihi: event.target.value }))} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-3 text-sm text-gray-800 outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" /></div>
                        <div><label htmlFor="audit-eylem" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Eylem</label><select id="audit-eylem" value={form.eylemTipi} disabled={yukleniyor} onChange={(event) => setForm((onceki) => ({ ...onceki, eylemTipi: event.target.value }))} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-3 text-sm text-gray-800 outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"><option value="">Tüm eylemler</option>{AUDIT_EYLEM_SECENEKLERI.map((secenek) => <option key={secenek.deger} value={secenek.deger}>{secenek.etiket}</option>)}</select></div>
                        <div><label htmlFor="audit-aktor" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Aktör UUID</label><input id="audit-aktor" type="text" value={form.aktorPersonelId} disabled={yukleniyor} onChange={(event) => setForm((onceki) => ({ ...onceki, aktorPersonelId: event.target.value }))} placeholder="Personel UUID" className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-3 text-sm text-gray-800 outline-none placeholder:text-gray-400 focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" /></div>
                        <div><label htmlFor="audit-hedef" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Hedef UUID</label><input id="audit-hedef" type="text" value={form.hedefPersonelId} disabled={yukleniyor} onChange={(event) => setForm((onceki) => ({ ...onceki, hedefPersonelId: event.target.value }))} placeholder="Personel UUID" className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-3 text-sm text-gray-800 outline-none placeholder:text-gray-400 focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" /></div>
                    </div>
                    <div className="flex flex-wrap gap-3"><button type="submit" disabled={yukleniyor} className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50">{yukleniyor ? "Yükleniyor…" : "Filtrele"}</button><button type="button" onClick={filtreleriTemizle} disabled={yukleniyor} className="rounded-lg border border-gray-300 px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-white/[0.05]">Temizle</button></div>
                    {hata && <p role="alert" className="text-sm text-error-600 dark:text-error-400">{hata}</p>}
                </form>
            </section>
            <section className="overflow-hidden rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]">
                <div className="border-b border-gray-100 px-5 py-5 dark:border-gray-800 sm:px-6"><h3 className="text-base font-semibold text-gray-800 dark:text-white/90">İşlem kayıtları</h3></div>
                {ilkYukleme ? <p role="status" className="p-6 text-sm text-gray-500 dark:text-gray-400">Audit kayıtları yükleniyor…</p> : !hata && kayitlar.length === 0 ? <p className="p-6 text-sm text-gray-500 dark:text-gray-400">Bu filtrelerle eşleşen audit kaydı bulunmuyor.</p> : !hata && <div className="max-w-full overflow-x-auto"><Table className="min-w-[980px]"><TableHeader className="border-b border-gray-100 dark:border-white/[0.05]"><TableRow>{["Tarih/saat", "Eylem", "Aktör", "Hedef", "Detay"].map((baslik) => <TableCell key={baslik} isHeader className="px-5 py-3 text-start text-xs font-medium text-gray-500 dark:text-gray-400">{baslik}</TableCell>)}</TableRow></TableHeader><TableBody className="divide-y divide-gray-100 dark:divide-white/[0.05]">{kayitlar.map((kayit) => <TableRow key={kayit.auditId}><TableCell className="whitespace-nowrap px-5 py-4 text-sm text-gray-600 dark:text-gray-300">{auditTarihiniTurkiyeSaatindeBicimlendir(kayit.olusmaZamani)}</TableCell><TableCell className="px-5 py-4"><Badge size="sm" color="light">{auditEylemEtiketi(kayit.eylemTipi)}</Badge></TableCell><TableCell className="px-5 py-4 text-sm text-gray-700 dark:text-gray-300"><p>{auditAktoruEtiketi(kayit.aktor)}</p>{kayit.aktor?.adSoyad && kayit.aktor.kullaniciAdi && <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">@{kayit.aktor.kullaniciAdi}</p>}</TableCell><TableCell className="px-5 py-4 text-sm text-gray-700 dark:text-gray-300">{auditHedefEtiketi(kayit.hedef)}</TableCell><TableCell className="max-w-md break-words px-5 py-4 text-sm text-gray-600 dark:text-gray-300">{kayit.detay}</TableCell></TableRow>)}</TableBody></Table></div>}
                {sayfaVerisi && <div className="flex flex-col gap-3 border-t border-gray-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between dark:border-gray-800"><p className="text-sm text-gray-500 dark:text-gray-400">Toplam {sayfaVerisi.toplamKayit} kayıt · Sayfa {sayfaVerisi.sayfa + 1}/{Math.max(sayfaVerisi.toplamSayfa, 1)}</p><div className="flex gap-3"><button type="button" disabled={yukleniyor || sayfaVerisi.sayfa === 0} onClick={() => setPage(sayfaVerisi.sayfa - 1)} className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300">Önceki</button><button type="button" disabled={yukleniyor || sayfaVerisi.sayfa + 1 >= sayfaVerisi.toplamSayfa} onClick={() => setPage(sayfaVerisi.sayfa + 1)} className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300">Sonraki</button></div></div>}
            </section>
        </div>
    </>;
}
