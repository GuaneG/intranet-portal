import { type FormEvent, useCallback, useEffect, useRef, useState } from "react";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import EkipmanTalebiFormu from "../components/ekipman-talebi/EkipmanTalebiFormu";
import EkipmanTalepleriTablosu from "../components/ekipman-talebi/EkipmanTalepleriTablosu";
import ZimmetlerimTablosu from "../components/ekipman-talebi/ZimmetlerimTablosu";
import EkipmanKararListesi from "./EkipmanKararListesi";
import { useAuth } from "../context/AuthContext";
import { ApiError, ekipmanTalebiOlustur, ekipmanTalebiniGuncelle, ekipmanTalebiniIptalEt, ekipmanTaleplerimiGetir, ekipmanTipleriniGetir, zimmetlerimiGetir } from "../lib/api";
import { ekipmanTalepFormunuDogrula, ekipmanTalepIstekGövdesiniOlustur } from "../lib/ekipmanTalebi";
import type { EkipmanTalebi, EkipmanTalebiFormu as FormDegerleri, EkipmanTalepDurumu, EkipmanTipi, SayfaliEkipmanTalepResponse, SayfaliZimmetResponse } from "../types/ekipman";

const SAYFA_BOYUTU = 20;
const BOS_FORM: FormDegerleri = { ekipmanTipiId: "", aciklama: "" };
const DURUM_SECENEKLERI: Array<{ deger: "" | EkipmanTalepDurumu; etiket: string }> = [
    { deger: "", etiket: "Tüm durumlar" },
    { deger: "ONAY_BEKLIYOR", etiket: "Onay bekliyor" },
    { deger: "ONAYLANDI", etiket: "Onaylandı" },
    { deger: "REDDEDILDI", etiket: "Reddedildi" },
    { deger: "IPTAL_EDILDI", etiket: "İptal edildi" },
];

function hataMesajiniGetir(error: unknown, fallback: string) {
    return error instanceof ApiError && error.message ? error.message : fallback;
}

/** PERSONEL ve HR'nin kendi ekipman talepleri ile zimmet geçmişini koordine eder. */
export default function EkipmanTaleplerim() {
    const { kullanici } = useAuth();
    if (kullanici?.rol === "YONETICI") return <EkipmanKararListesi kapsam="EKIP" />;
    if (kullanici?.rol === "ADMIN") return <EkipmanKararListesi kapsam="SIRKET" />;

    return <KendiEkipmanTaleplerim />;
}

function KendiEkipmanTaleplerim() {
    const [form, setForm] = useState<FormDegerleri>(BOS_FORM);
    const [duzenlenenTalep, setDuzenlenenTalep] = useState<EkipmanTalebi | null>(null);
    const [ekipmanTipleri, setEkipmanTipleri] = useState<EkipmanTipi[]>([]);
    const [tiplerYukleniyor, setTiplerYukleniyor] = useState(false);
    const [tiplerHatasi, setTiplerHatasi] = useState<string | null>(null);
    const [formHatasi, setFormHatasi] = useState<string | null>(null);
    const [gonderimSuruyor, setGonderimSuruyor] = useState(false);
    const [basariMesaji, setBasariMesaji] = useState<string | null>(null);
    const [listeIslemHatasi, setListeIslemHatasi] = useState<string | null>(null);
    const [filtreFormu, setFiltreFormu] = useState({ durum: "" as "" | EkipmanTalepDurumu, ekipmanTipiId: "" });
    const [uygulananFiltre, setUygulananFiltre] = useState({ durum: "" as "" | EkipmanTalepDurumu, ekipmanTipiId: "" });
    const [talepSayfasi, setTalepSayfasi] = useState<SayfaliEkipmanTalepResponse | null>(null);
    const [taleplerYukleniyor, setTaleplerYukleniyor] = useState(false);
    const [taleplerHatasi, setTaleplerHatasi] = useState<string | null>(null);
    const [zimmetSayfasi, setZimmetSayfasi] = useState<SayfaliZimmetResponse | null>(null);
    const [zimmetlerYukleniyor, setZimmetlerYukleniyor] = useState(false);
    const [zimmetlerHatasi, setZimmetlerHatasi] = useState<string | null>(null);
    const [islemYapilanTalepId, setIslemYapilanTalepId] = useState<string | null>(null);
    const sonTalepIstegi = useRef(0);
    const sonZimmetIstegi = useRef(0);

    const tipleriYukle = useCallback(async () => {
        setTiplerYukleniyor(true);
        setTiplerHatasi(null);
        try {
            setEkipmanTipleri(await ekipmanTipleriniGetir());
        } catch (error) {
            setEkipmanTipleri([]);
            setTiplerHatasi(hataMesajiniGetir(error, "Ekipman tipleri alınamadı. Lütfen tekrar deneyin."));
        } finally {
            setTiplerYukleniyor(false);
        }
    }, []);

    const talepleriYukle = useCallback(async (filtre: typeof uygulananFiltre, page: number) => {
        const istekNumarasi = ++sonTalepIstegi.current;
        setTaleplerYukleniyor(true);
        setTaleplerHatasi(null);
        try {
            const sonuc = await ekipmanTaleplerimiGetir({ page, size: SAYFA_BOYUTU, durum: filtre.durum || undefined, ekipmanTipiId: filtre.ekipmanTipiId || undefined });
            if (istekNumarasi === sonTalepIstegi.current) setTalepSayfasi(sonuc);
        } catch (error) {
            if (istekNumarasi === sonTalepIstegi.current) {
                setTalepSayfasi(null);
                setTaleplerHatasi(hataMesajiniGetir(error, "Ekipman talepleriniz alınamadı. Lütfen tekrar deneyin."));
            }
        } finally {
            if (istekNumarasi === sonTalepIstegi.current) setTaleplerYukleniyor(false);
        }
    }, []);

    const zimmetleriYukle = useCallback(async (page: number) => {
        const istekNumarasi = ++sonZimmetIstegi.current;
        setZimmetlerYukleniyor(true);
        setZimmetlerHatasi(null);
        try {
            const sonuc = await zimmetlerimiGetir({ page, size: SAYFA_BOYUTU });
            if (istekNumarasi === sonZimmetIstegi.current) setZimmetSayfasi(sonuc);
        } catch (error) {
            if (istekNumarasi === sonZimmetIstegi.current) {
                setZimmetSayfasi(null);
                setZimmetlerHatasi(hataMesajiniGetir(error, "Zimmetleriniz alınamadı. Lütfen tekrar deneyin."));
            }
        } finally {
            if (istekNumarasi === sonZimmetIstegi.current) setZimmetlerYukleniyor(false);
        }
    }, []);

    useEffect(() => {
        void tipleriYukle();
        void talepleriYukle({ durum: "", ekipmanTipiId: "" }, 0);
        void zimmetleriYukle(0);
    }, [talepleriYukle, tipleriYukle, zimmetleriYukle]);

    const formuGuncelle = (alan: keyof FormDegerleri, deger: string) => {
        setForm((onceki) => ({ ...onceki, [alan]: deger }));
        setFormHatasi(null);
        setBasariMesaji(null);
    };

    const duzenlemeyiIptalEt = () => {
        setDuzenlenenTalep(null);
        setForm(BOS_FORM);
        setFormHatasi(null);
    };

    const formuGonder = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (gonderimSuruyor) return;
        const hata = ekipmanTalepFormunuDogrula(form);
        if (hata) {
            setFormHatasi(hata);
            return;
        }

        setGonderimSuruyor(true);
        setFormHatasi(null);
        setBasariMesaji(null);
        try {
            const payload = ekipmanTalepIstekGövdesiniOlustur(form);
            const sonuc = duzenlenenTalep ? await ekipmanTalebiniGuncelle(duzenlenenTalep.id, payload) : await ekipmanTalebiOlustur(payload);
            setBasariMesaji(duzenlenenTalep ? `${sonuc.ekipmanTipi} talebiniz güncellendi.` : `${sonuc.ekipmanTipi} talebiniz oluşturuldu.`);
            duzenlemeyiIptalEt();
            await talepleriYukle(uygulananFiltre, 0);
        } catch (error) {
            setFormHatasi(hataMesajiniGetir(error, duzenlenenTalep ? "Ekipman talebi güncellenemedi. Lütfen tekrar deneyin." : "Ekipman talebi oluşturulamadı. Lütfen tekrar deneyin."));
        } finally {
            setGonderimSuruyor(false);
        }
    };

    const filtreyiUygula = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const yeniFiltre = { ...filtreFormu };
        setUygulananFiltre(yeniFiltre);
        void talepleriYukle(yeniFiltre, 0);
    };

    const filtreyiTemizle = () => {
        const bosFiltre = { durum: "" as const, ekipmanTipiId: "" };
        setFiltreFormu(bosFiltre);
        setUygulananFiltre(bosFiltre);
        void talepleriYukle(bosFiltre, 0);
    };

    const talebiDuzenle = (talep: EkipmanTalebi) => {
        setDuzenlenenTalep(talep);
        setForm({ ekipmanTipiId: String(talep.ekipmanTipiId), aciklama: talep.aciklama ?? "" });
        setFormHatasi(null);
        setBasariMesaji(null);
        window.scrollTo({ top: 0, behavior: "smooth" });
    };

    const talebiIptalEt = async (talepId: string) => {
        if (islemYapilanTalepId || !window.confirm("Bu bekleyen ekipman talebini iptal etmek istiyor musunuz?")) return;

        setIslemYapilanTalepId(talepId);
        setListeIslemHatasi(null);
        setBasariMesaji(null);
        try {
            await ekipmanTalebiniIptalEt(talepId);
            await talepleriYukle(uygulananFiltre, talepSayfasi?.sayfa ?? 0);
            setBasariMesaji("Ekipman talebiniz iptal edildi.");
        } catch (error) {
            setListeIslemHatasi(hataMesajiniGetir(error, "Ekipman talebi iptal edilemedi. Lütfen tekrar deneyin."));
            if (error instanceof ApiError && error.status === 409) await talepleriYukle(uygulananFiltre, talepSayfasi?.sayfa ?? 0);
        } finally {
            setIslemYapilanTalepId(null);
        }
    };

    return <>
        <PageMeta title="Ekipman Taleplerim | Intranet Portal" description="Ekipman talebi oluşturma, talep takibi ve zimmet geçmişi" />
        <PageBreadcrumb pageTitle="Ekipman Taleplerim" />
        <div className="space-y-6">
            <div><h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">Ekipman ihtiyaçların</h2><p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Ekipman talebi oluşturun, taleplerinizi ve zimmet geçmişinizi takip edin.</p></div>
            {basariMesaji && <div role="status" className="rounded-xl border border-success-200 bg-success-50 px-5 py-4 text-sm text-success-700 dark:border-success-500/20 dark:bg-success-500/10 dark:text-success-400">{basariMesaji}</div>}
            {listeIslemHatasi && <div role="alert" className="rounded-xl border border-error-200 bg-error-50 px-5 py-4 text-sm text-error-700 dark:border-error-500/20 dark:bg-error-500/10 dark:text-error-400">{listeIslemHatasi}</div>}
            <EkipmanTalebiFormu form={form} ekipmanTipleri={ekipmanTipleri} tiplerYukleniyor={tiplerYukleniyor} tiplerHatasi={tiplerHatasi} formHatasi={formHatasi} gonderimSuruyor={gonderimSuruyor} duzenlemeModunda={duzenlenenTalep !== null} onDegisiklik={formuGuncelle} onTekrarDene={() => void tipleriYukle()} onIptal={duzenlemeyiIptalEt} onSubmit={(event) => void formuGonder(event)} />
            <section className="rounded-xl border border-gray-200 bg-white p-5 dark:border-white/[0.05] dark:bg-white/[0.03]"><form onSubmit={filtreyiUygula} className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]"><div><label htmlFor="ekipman-talep-durum-filtresi" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Durum</label><select id="ekipman-talep-durum-filtresi" value={filtreFormu.durum} disabled={taleplerYukleniyor} onChange={(event) => setFiltreFormu((onceki) => ({ ...onceki, durum: event.target.value as "" | EkipmanTalepDurumu }))} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90">{DURUM_SECENEKLERI.map((secenek) => <option key={secenek.deger || "tumu"} value={secenek.deger}>{secenek.etiket}</option>)}</select></div><div><label htmlFor="ekipman-talep-tipi-filtresi" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Ekipman tipi</label><select id="ekipman-talep-tipi-filtresi" value={filtreFormu.ekipmanTipiId} disabled={taleplerYukleniyor || tiplerYukleniyor} onChange={(event) => setFiltreFormu((onceki) => ({ ...onceki, ekipmanTipiId: event.target.value }))} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"><option value="">Tüm ekipman tipleri</option>{ekipmanTipleri.map((tip) => <option key={tip.id} value={tip.id}>{tip.ad}</option>)}</select></div><div className="flex items-end gap-3"><button type="submit" disabled={taleplerYukleniyor} className="rounded-lg bg-brand-500 px-5 py-3 text-sm font-medium text-white hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50">Filtrele</button><button type="button" onClick={filtreyiTemizle} disabled={taleplerYukleniyor} className="rounded-lg border border-gray-300 bg-white px-5 py-3 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300">Temizle</button></div></form></section>
            <EkipmanTalepleriTablosu sayfaVerisi={talepSayfasi} yukleniyor={taleplerYukleniyor} hata={taleplerHatasi} islemYapilanTalepId={islemYapilanTalepId} onTekrarDene={() => void talepleriYukle(uygulananFiltre, 0)} onOncekiSayfa={() => { if (talepSayfasi && talepSayfasi.sayfa > 0) void talepleriYukle(uygulananFiltre, talepSayfasi.sayfa - 1); }} onSonrakiSayfa={() => { if (talepSayfasi && talepSayfasi.sayfa + 1 < talepSayfasi.toplamSayfa) void talepleriYukle(uygulananFiltre, talepSayfasi.sayfa + 1); }} onDuzenle={talebiDuzenle} onIptal={(talepId) => void talebiIptalEt(talepId)} />
            <ZimmetlerimTablosu sayfaVerisi={zimmetSayfasi} yukleniyor={zimmetlerYukleniyor} hata={zimmetlerHatasi} onTekrarDene={() => void zimmetleriYukle(0)} onOncekiSayfa={() => { if (zimmetSayfasi && zimmetSayfasi.sayfa > 0) void zimmetleriYukle(zimmetSayfasi.sayfa - 1); }} onSonrakiSayfa={() => { if (zimmetSayfasi && zimmetSayfasi.sayfa + 1 < zimmetSayfasi.toplamSayfa) void zimmetleriYukle(zimmetSayfasi.sayfa + 1); }} />
        </div>
    </>;
}
