import {
    type Dispatch,
    type FormEvent,
    type SetStateAction,
    useCallback,
    useRef,
    useState,
} from "react";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import timeGridPlugin from "@fullcalendar/timegrid";
import interactionPlugin from "@fullcalendar/interaction";
import type { DateSelectArg, DatesSetArg, EventClickArg, EventContentArg, EventMountArg } from "@fullcalendar/core";
import type { DateClickArg } from "@fullcalendar/interaction";
import { Modal } from "../components/ui/modal";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import { useAuth } from "../context/AuthContext";
import { ApiError, dogumGunleriniGetir, rezervasyonlariGetir, tatilGuncelle, tatilOlustur, tatilSil, tatilleriGetir } from "../lib/api";
import { REZERVASYON_EVENT_TURU, rezervasyonOlusturanAdi, rezervasyonTakvimdeGorunurMu, rezervasyonuTakvimOlayinaDonustur, saatiBicimlendir, takvimTarihiniBicimlendir } from "../lib/rezervasyonTakvimi";
import {
    TAKVIM_EVENT_TURLERI,
    dogumGununuTakvimOlayinaDonustur,
    gorunenTakvimAraliginiOlustur,
    rolTatilYonetebilirMi,
    tatilAraliginiDogrula,
    tatilGunSuresiEtiketi,
    tatilKategoriEtiketi,
    tatiliTakvimOlayinaDonustur,
    tarihiYerelIsoBicimlendir,
    tarihteTatilBul,
} from "../lib/tatilTakvimi";
import { useModal } from "../hooks/useModal";
import type { Rezervasyon } from "../types/rezervasyon";
import type { DogumGunu, Tatil, TatilGunSuresi, TatilKategori, TatilKaydetRequest } from "../types/tatil";

type TakvimAraligi = { baslangicTarihi: string; bitisTarihi: string };

const BOS_TATIL_FORMU: TatilKaydetRequest = {
    ad: "",
    tarih: "",
    bitisTarihi: "",
    kategori: "RESMI_SABIT",
    gunSuresi: "TAM_GUN",
};

const KATEGORILER: Array<{ deger: TatilKategori; etiket: string }> = [
    { deger: "RESMI_SABIT", etiket: "Sabit resmî tatil" },
    { deger: "RESMI_DEGISKEN", etiket: "Değişken resmî tatil" },
    { deger: "SIRKET", etiket: "Şirket günü" },
];
const GUN_SURELERI: Array<{ deger: TatilGunSuresi; etiket: string }> = [
    { deger: "TAM_GUN", etiket: "Tam gün" },
    { deger: "YARIM_GUN", etiket: "Yarım gün" },
];

function hataMesajiniGetir(error: unknown, varsayilanMesaj: string) {
    return error instanceof ApiError && error.message ? error.message : varsayilanMesaj;
}

function tatilGecmisteMi(tarih: string) {
    return tarih < tarihiYerelIsoBicimlendir(new Date());
}

function tatiliFormaDonustur(tatil: Tatil): TatilKaydetRequest {
    return { ad: tatil.ad, tarih: tatil.tarih, bitisTarihi: tatil.bitisTarihi, kategori: tatil.kategori, gunSuresi: tatil.gunSuresi };
}

/** Mevcut TailAdmin takvimini gerçek şirket tatili görünümü ve yönetimine dönüştürür. */
export default function Calendar() {
    const { kullanici } = useAuth();
    const { isOpen, openModal, closeModal } = useModal();
    const [tatiller, setTatiller] = useState<Tatil[] | null>(null);
    const [dogumGunleri, setDogumGunleri] = useState<DogumGunu[] | null>(null);
    const [rezervasyonlar, setRezervasyonlar] = useState<Rezervasyon[] | null>(null);
    const [gorunenAralik, setGorunenAralik] = useState<TakvimAraligi | null>(null);
    const [yukleniyor, setYukleniyor] = useState(false);
    const [hata, setHata] = useState<string | null>(null);
    const [dogumGunleriYukleniyor, setDogumGunleriYukleniyor] = useState(false);
    const [dogumGunuHatasi, setDogumGunuHatasi] = useState<string | null>(null);
    const [rezervasyonlarYukleniyor, setRezervasyonlarYukleniyor] = useState(false);
    const [rezervasyonHatasi, setRezervasyonHatasi] = useState<string | null>(null);
    const [seciliTatil, setSeciliTatil] = useState<Tatil | null>(null);
    const [seciliRezervasyon, setSeciliRezervasyon] = useState<Rezervasyon | null>(null);
    const [form, setForm] = useState<TatilKaydetRequest>(BOS_TATIL_FORMU);
    const [formHatasi, setFormHatasi] = useState<string | null>(null);
    const [rezervasyonCakismaHatasi, setRezervasyonCakismaHatasi] = useState<string | null>(null);
    const [islemDevamEdiyor, setIslemDevamEdiyor] = useState(false);
    const sonListeIstegi = useRef(0);
    const sonDogumGunuIstegi = useRef(0);
    const sonRezervasyonIstegi = useRef(0);

    const tatilYonetebilirMi = rolTatilYonetebilirMi(kullanici?.rol);
    const seciliTatilGecmisteMi = seciliTatil !== null && tatilGecmisteMi(seciliTatil.tarih);
    const formDuzenlenebilirMi = tatilYonetebilirMi && !seciliTatilGecmisteMi;
    const takvimYukleniyor = yukleniyor || dogumGunleriYukleniyor || rezervasyonlarYukleniyor;
    const ilkYukleme = takvimYukleniyor && tatiller === null && dogumGunleri === null && rezervasyonlar === null && hata === null && dogumGunuHatasi === null && rezervasyonHatasi === null;

    const tatilleriYukle = useCallback(async (aralik: TakvimAraligi) => {
        const istekNumarasi = ++sonListeIstegi.current;
        setYukleniyor(true);
        setHata(null);
        try {
            const sonuc = await tatilleriGetir(aralik.baslangicTarihi, aralik.bitisTarihi);
            if (istekNumarasi === sonListeIstegi.current) setTatiller(sonuc);
        } catch (error) {
            if (istekNumarasi === sonListeIstegi.current) {
                setHata(hataMesajiniGetir(error, "Tatiller alınamadı. Lütfen tekrar deneyin."));
            }
        } finally {
            if (istekNumarasi === sonListeIstegi.current) setYukleniyor(false);
        }
    }, []);

    const dogumGunleriniYukle = useCallback(async (aralik: TakvimAraligi) => {
        const istekNumarasi = ++sonDogumGunuIstegi.current;
        setDogumGunleriYukleniyor(true);
        setDogumGunuHatasi(null);
        try {
            const sonuc = await dogumGunleriniGetir(aralik.baslangicTarihi, aralik.bitisTarihi);
            if (istekNumarasi === sonDogumGunuIstegi.current) setDogumGunleri(sonuc);
        } catch (error) {
            if (istekNumarasi === sonDogumGunuIstegi.current) {
                setDogumGunleri(null);
                setDogumGunuHatasi(hataMesajiniGetir(error, "Doğum günleri alınamadı. Lütfen tekrar deneyin."));
            }
        } finally {
            if (istekNumarasi === sonDogumGunuIstegi.current) setDogumGunleriYukleniyor(false);
        }
    }, []);

    const rezervasyonlariYukle = useCallback(async (aralik: TakvimAraligi) => {
        const istekNumarasi = ++sonRezervasyonIstegi.current;
        setRezervasyonlarYukleniyor(true);
        setRezervasyonHatasi(null);
        try {
            const sonuc = await rezervasyonlariGetir(aralik.baslangicTarihi, aralik.bitisTarihi);
            if (istekNumarasi === sonRezervasyonIstegi.current) setRezervasyonlar(sonuc);
        } catch (error) {
            if (istekNumarasi === sonRezervasyonIstegi.current) {
                setRezervasyonlar(null);
                setRezervasyonHatasi(hataMesajiniGetir(error, "Rezervasyonlar alınamadı. Lütfen tekrar deneyin."));
            }
        } finally {
            if (istekNumarasi === sonRezervasyonIstegi.current) setRezervasyonlarYukleniyor(false);
        }
    }, []);

    function gorunenTarihleriDegistir(arg: DatesSetArg) {
        const aralik = gorunenTakvimAraliginiOlustur(arg.start, arg.end);
        setGorunenAralik(aralik);
        void tatilleriYukle(aralik);
        void dogumGunleriniYukle(aralik);
        void rezervasyonlariYukle(aralik);
    }

    function modalKapat(zorla = false) {
        if (islemDevamEdiyor && !zorla) return;
        closeModal();
        setSeciliTatil(null);
        setForm(BOS_TATIL_FORMU);
        setFormHatasi(null);
        setRezervasyonCakismaHatasi(null);
    }

    function tatilOlusturmaModaliniAc(tarih = "") {
        setSeciliTatil(null);
        setForm({ ...BOS_TATIL_FORMU, tarih, bitisTarihi: tarih });
        setFormHatasi(null);
        setRezervasyonCakismaHatasi(null);
        openModal();
    }

    function tatilDuzenlemeModaliniAc(tatil: Tatil) {
        setSeciliTatil(tatil);
        setForm(tatiliFormaDonustur(tatil));
        setFormHatasi(null);
        setRezervasyonCakismaHatasi(null);
        openModal();
    }

    function takvimEventineTiklandi(arg: EventClickArg) {
        if (arg.event.extendedProps.tur === REZERVASYON_EVENT_TURU) {
            const rezervasyon = arg.event.extendedProps.rezervasyon as Rezervasyon | undefined;
            if (rezervasyon) setSeciliRezervasyon(rezervasyon);
            return;
        }
        if (arg.event.extendedProps.tur !== TAKVIM_EVENT_TURLERI.TATIL) return;
        const tatil = tatiller?.find((kayit) => kayit.tatilId === Number(arg.event.id));
        if (!tatil) return;
        tatilDuzenlemeModaliniAc(tatil);
    }

    function gunTiklandi(arg: DateClickArg) {
        if (!tatilYonetebilirMi) return;
        const tatil = tarihteTatilBul(tatiller ?? [], arg.dateStr);
        if (tatil) tatilDuzenlemeModaliniAc(tatil);
        else tatilOlusturmaModaliniAc(arg.dateStr);
    }

    function tarihAraligiSecildi(arg: DateSelectArg) {
        if (!tatilYonetebilirMi) return;
        const aralik = gorunenTakvimAraliginiOlustur(arg.start, arg.end);
        arg.view.calendar.unselect();
        setSeciliTatil(null);
        setForm({ ...BOS_TATIL_FORMU, tarih: aralik.baslangicTarihi, bitisTarihi: aralik.bitisTarihi });
        setFormHatasi(null);
        setRezervasyonCakismaHatasi(null);
        openModal();
    }

    async function formuKaydet(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!formDuzenlenebilirMi || islemDevamEdiyor) return;
        const payload = { ...form, ad: form.ad.trim() };
        if (!payload.ad) {
            setRezervasyonCakismaHatasi(null);
            setFormHatasi("Tatil adı zorunludur.");
            return;
        }
        const aralikHatasi = tatilAraliginiDogrula(payload.tarih, payload.bitisTarihi);
        if (aralikHatasi) {
            setRezervasyonCakismaHatasi(null);
            setFormHatasi(aralikHatasi);
            return;
        }
        if (tatilGecmisteMi(payload.tarih)) {
            setRezervasyonCakismaHatasi(null);
            setFormHatasi("Geçmiş tarihli tatil kaydı oluşturulamaz.");
            return;
        }
        setIslemDevamEdiyor(true);
        setFormHatasi(null);
        setRezervasyonCakismaHatasi(null);
        try {
            if (seciliTatil) await tatilGuncelle(seciliTatil.tatilId, payload);
            else await tatilOlustur(payload);
            modalKapat(true);
            if (gorunenAralik) await tatilleriYukle(gorunenAralik);
        } catch (error) {
            if (error instanceof ApiError && error.status === 409) {
                setRezervasyonCakismaHatasi(hataMesajiniGetir(error, "Tatil kaydedilemedi. Lütfen rezervasyonları kontrol edin."));
            } else {
                setFormHatasi(hataMesajiniGetir(error, seciliTatil ? "Tatil güncellenemedi. Lütfen tekrar deneyin." : "Tatil oluşturulamadı. Lütfen tekrar deneyin."));
            }
        } finally {
            setIslemDevamEdiyor(false);
        }
    }

    async function seciliTatiliSil() {
        if (!seciliTatil || seciliTatilGecmisteMi || islemDevamEdiyor) return;
        if (!window.confirm(`“${seciliTatil.ad}” tatil kaydını silmek istediğinize emin misiniz?`)) return;
        setIslemDevamEdiyor(true);
        setFormHatasi(null);
        try {
            await tatilSil(seciliTatil.tatilId);
            modalKapat(true);
            if (gorunenAralik) await tatilleriYukle(gorunenAralik);
        } catch (error) {
            setFormHatasi(hataMesajiniGetir(error, "Tatil silinemedi. Lütfen tekrar deneyin."));
        } finally {
            setIslemDevamEdiyor(false);
        }
    }

    const events = [
        ...(tatiller ?? []).map(tatiliTakvimOlayinaDonustur),
        ...(dogumGunleri ?? []).map(dogumGununuTakvimOlayinaDonustur),
        ...(rezervasyonlar ?? []).filter((rezervasyon) => rezervasyonTakvimdeGorunurMu(rezervasyon, tarihiYerelIsoBicimlendir(new Date()))).map(rezervasyonuTakvimOlayinaDonustur),
    ];

    return <>
        <PageMeta title="Şirket Takvimi | Intranet Portal" description="Şirket tatillerini, doğum günlerini ve oda rezervasyonlarını görüntüleme ekranı" />
        <PageBreadcrumb pageTitle="Takvim" />
        <div className="space-y-6">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                <div><h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">Şirket takvimi</h2><p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Şirket tatillerini, yarım günleri, ekip doğum günlerini ve oda rezervasyonlarını görüntüleyin.</p></div>
                {tatilYonetebilirMi && <button type="button" onClick={() => tatilOlusturmaModaliniAc()} disabled={islemDevamEdiyor} className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50">Tatil ekle</button>}
            </div>
            {hata && <div role="alert" className="rounded-xl border border-error-200 bg-error-50 p-5 text-sm text-error-700 dark:border-error-500/20 dark:bg-error-500/10 dark:text-error-400"><p>{hata}</p>{gorunenAralik && <button type="button" disabled={yukleniyor} onClick={() => void tatilleriYukle(gorunenAralik)} className="mt-3 font-medium underline disabled:cursor-not-allowed disabled:opacity-50">Tekrar dene</button>}</div>}
            {dogumGunuHatasi && <div role="alert" className="rounded-xl border border-warning-200 bg-warning-50 p-5 text-sm text-warning-700 dark:border-warning-500/20 dark:bg-warning-500/10 dark:text-warning-400"><p>{dogumGunuHatasi}</p>{gorunenAralik && <button type="button" disabled={dogumGunleriYukleniyor} onClick={() => void dogumGunleriniYukle(gorunenAralik)} className="mt-3 font-medium underline disabled:cursor-not-allowed disabled:opacity-50">Doğum günlerini tekrar dene</button>}</div>}
            {rezervasyonHatasi && <div role="alert" className="rounded-xl border border-warning-200 bg-warning-50 p-5 text-sm text-warning-700 dark:border-warning-500/20 dark:bg-warning-500/10 dark:text-warning-400"><p>{rezervasyonHatasi}</p>{gorunenAralik && <button type="button" disabled={rezervasyonlarYukleniyor} onClick={() => void rezervasyonlariYukle(gorunenAralik)} className="mt-3 font-medium underline disabled:cursor-not-allowed disabled:opacity-50">Rezervasyonları tekrar dene</button>}</div>}
            <div className="rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]">
                {ilkYukleme && <p role="status" className="px-5 pt-5 text-sm text-gray-500 dark:text-gray-400">Takvim kayıtları yükleniyor…</p>}
                {takvimYukleniyor && !ilkYukleme && <p role="status" className="px-5 pt-5 text-sm text-gray-500 dark:text-gray-400">Takvim güncelleniyor…</p>}
                {tatiller?.length === 0 && dogumGunleri?.length === 0 && rezervasyonlar?.length === 0 && !takvimYukleniyor && !hata && !dogumGunuHatasi && !rezervasyonHatasi && <p className="px-5 pt-5 text-sm text-gray-500 dark:text-gray-400">Bu tarih aralığında takvim kaydı bulunmuyor.</p>}
                <div className="custom-calendar p-3 sm:p-5"><FullCalendar plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]} initialView="dayGridMonth" headerToolbar={{ left: "prev,next today", center: "title", right: "dayGridMonth,timeGridWeek,timeGridDay" }} buttonText={{ today: "Bugün", month: "Ay", week: "Hafta", day: "Gün" }} events={events} selectable={tatilYonetebilirMi} select={tarihAraligiSecildi} dateClick={gunTiklandi} eventClick={takvimEventineTiklandi} eventContent={takvimEventIceriginiRenderEt} eventDidMount={takvimEventYerlestirildi} datesSet={gorunenTarihleriDegistir} height="auto" /></div>
            </div>
        </div>
        <Modal isOpen={isOpen} onClose={modalKapat} className="max-w-[620px] p-6 lg:p-8">
            <div className="max-h-[80vh] overflow-y-auto px-1">
                <div><h3 className="text-xl font-semibold text-gray-800 dark:text-white/90">{seciliTatil ? formDuzenlenebilirMi ? "Tatili düzenle" : "Tatil ayrıntısı" : "Tatil ekle"}</h3><p className="mt-1 text-sm text-gray-500 dark:text-gray-400">{formDuzenlenebilirMi || !seciliTatil ? "Şirket takviminde gösterilecek tatil bilgisini girin." : "Geçmiş tatil kayıtları salt okunur görüntülenir."}</p></div>
                {formDuzenlenebilirMi || !seciliTatil ? <form onSubmit={formuKaydet} className="mt-6 space-y-5">
                    <TatilAlanlari form={form} islemDevamEdiyor={islemDevamEdiyor} formuGuncelle={setForm} />
                    {rezervasyonCakismaHatasi && <div role="alert" className="rounded-xl border border-warning-200 bg-warning-50 px-4 py-3 text-sm text-warning-700 dark:border-warning-500/20 dark:bg-warning-500/10 dark:text-warning-400"><p className="font-medium">Oda rezervasyonu çakışması</p><p className="mt-1">{rezervasyonCakismaHatasi}</p><p className="mt-2">Çakışan rezervasyonları iptal ettikten sonra bu formu yeniden gönderebilirsiniz.</p></div>}
                    {formHatasi && <p role="alert" className="text-sm text-error-600 dark:text-error-400">{formHatasi}</p>}
                    <div className="flex flex-col-reverse gap-3 sm:flex-row sm:items-center sm:justify-between">
                        {seciliTatil ? <button type="button" disabled={islemDevamEdiyor} onClick={() => void seciliTatiliSil()} className="rounded-lg border border-error-300 px-4 py-2.5 text-sm font-medium text-error-600 hover:bg-error-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-error-500/30 dark:text-error-400 dark:hover:bg-error-500/10">Sil</button> : <span />}
                        <div className="flex flex-col-reverse gap-3 sm:flex-row"><button type="button" disabled={islemDevamEdiyor} onClick={modalKapat} className="rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300">Vazgeç</button><button type="submit" disabled={islemDevamEdiyor} className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50">{islemDevamEdiyor ? "Kaydediliyor…" : seciliTatil ? "Güncelle" : "Kaydet"}</button></div>
                    </div>
                </form> : <TatilDetaylari tatil={seciliTatil} onKapat={modalKapat} />}
            </div>
        </Modal>
        <Modal isOpen={seciliRezervasyon !== null} onClose={() => setSeciliRezervasyon(null)} className="max-w-lg p-6 sm:p-8">
            {seciliRezervasyon && <RezervasyonTakvimDetaylari rezervasyon={seciliRezervasyon} onKapat={() => setSeciliRezervasyon(null)} />}
        </Modal>
    </>;
}

function TatilAlanlari({ form, islemDevamEdiyor, formuGuncelle }: { form: TatilKaydetRequest; islemDevamEdiyor: boolean; formuGuncelle: Dispatch<SetStateAction<TatilKaydetRequest>> }) {
    return <>
        <div><label htmlFor="tatil-adi" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Tatil adı</label><input id="tatil-adi" type="text" maxLength={100} value={form.ad} disabled={islemDevamEdiyor} onChange={(event) => formuGuncelle((onceki) => ({ ...onceki, ad: event.target.value }))} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" /></div>
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2"><div><label htmlFor="tatil-tarihi" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Başlangıç tarihi</label><input id="tatil-tarihi" type="date" min={tarihiYerelIsoBicimlendir(new Date())} value={form.tarih} disabled={islemDevamEdiyor} onChange={(event) => formuGuncelle((onceki) => ({ ...onceki, tarih: event.target.value, bitisTarihi: !onceki.bitisTarihi || onceki.bitisTarihi < event.target.value ? event.target.value : onceki.bitisTarihi }))} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" /></div><div><label htmlFor="tatil-bitis-tarihi" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Bitiş tarihi</label><input id="tatil-bitis-tarihi" type="date" min={form.tarih || tarihiYerelIsoBicimlendir(new Date())} value={form.bitisTarihi} disabled={islemDevamEdiyor} onChange={(event) => formuGuncelle((onceki) => ({ ...onceki, bitisTarihi: event.target.value }))} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" /></div></div>
        <div><label htmlFor="tatil-gun-suresi" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Gün süresi</label><select id="tatil-gun-suresi" value={form.gunSuresi} disabled={islemDevamEdiyor} onChange={(event) => formuGuncelle((onceki) => ({ ...onceki, gunSuresi: event.target.value as TatilGunSuresi }))} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90">{GUN_SURELERI.map((gunSuresi) => <option key={gunSuresi.deger} value={gunSuresi.deger}>{gunSuresi.etiket}</option>)}</select></div>
        <div><label htmlFor="tatil-kategori" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Kategori</label><select id="tatil-kategori" value={form.kategori} disabled={islemDevamEdiyor} onChange={(event) => formuGuncelle((onceki) => ({ ...onceki, kategori: event.target.value as TatilKategori }))} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90">{KATEGORILER.map((kategori) => <option key={kategori.deger} value={kategori.deger}>{kategori.etiket}</option>)}</select></div>
    </>;
}

function TatilDetaylari({ tatil, onKapat }: { tatil: Tatil; onKapat: () => void }) {
    const satirlar = [["Tatil adı", tatil.ad], ["Başlangıç tarihi", tatil.tarih], ["Bitiş tarihi", tatil.bitisTarihi], ["Kategori", tatilKategoriEtiketi(tatil.kategori)], ["Gün süresi", tatilGunSuresiEtiketi(tatil.gunSuresi)]];
    return <div className="mt-6 space-y-4 rounded-xl border border-gray-200 p-5 dark:border-gray-800">{satirlar.map(([etiket, deger]) => <div key={etiket} className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between"><span className="text-sm text-gray-500 dark:text-gray-400">{etiket}</span><span className="text-sm font-medium text-gray-800 dark:text-white/90">{deger}</span></div>)}<div className="pt-2 text-right"><button type="button" onClick={onKapat} className="rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300">Kapat</button></div></div>;
}

function RezervasyonTakvimDetaylari({ rezervasyon, onKapat }: { rezervasyon: Rezervasyon; onKapat: () => void }) {
    return <>
        <h3 className="text-lg font-semibold text-gray-800 dark:text-white/90">Rezervasyon ayrıntısı</h3>
        <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">Bu rezervasyon genel oda takviminde hatırlatıcı olarak gösterilir.</p>
        <div className="mt-5 space-y-3 rounded-xl border border-gray-200 p-5 text-sm text-gray-600 dark:border-gray-800 dark:text-gray-300">
            <p><span className="font-medium text-gray-800 dark:text-white/90">Oda:</span> {rezervasyon.oda.ad}</p>
            <p><span className="font-medium text-gray-800 dark:text-white/90">Tarih:</span> {takvimTarihiniBicimlendir(rezervasyon.tarih)}</p>
            <p><span className="font-medium text-gray-800 dark:text-white/90">Saat:</span> {saatiBicimlendir(rezervasyon.baslangicSaat)}–{saatiBicimlendir(rezervasyon.bitisSaat)}</p>
            <p><span className="font-medium text-gray-800 dark:text-white/90">Oluşturan:</span> {rezervasyonOlusturanAdi(rezervasyon)}</p>
            {rezervasyon.baslik !== null && <p><span className="font-medium text-gray-800 dark:text-white/90">Başlık:</span> {rezervasyon.baslik}</p>}
            {rezervasyon.katilimciSayisi !== null && <p><span className="font-medium text-gray-800 dark:text-white/90">Katılımcı:</span> {rezervasyon.katilimciSayisi}</p>}
        </div>
        <div className="mt-6 text-right"><button type="button" onClick={onKapat} className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600">Kapat</button></div>
    </>;
}

function takvimEventIceriginiRenderEt(eventInfo: EventContentArg) {
    if (eventInfo.event.extendedProps.tur === TAKVIM_EVENT_TURLERI.DOGUM_GUNU) {
        return <div className="flex min-w-0 items-center gap-1.5 text-xs"><span aria-hidden="true">🎂</span><span className="truncate font-semibold">{eventInfo.event.title}</span><span className="sr-only">{eventInfo.event.extendedProps.departman as string}</span></div>;
    }

    if (eventInfo.event.extendedProps.tur === REZERVASYON_EVENT_TURU) {
        return <div className="flex min-w-0 items-center gap-1.5 text-xs"><span aria-hidden="true">📍</span><span className="truncate font-semibold">{eventInfo.event.title}</span></div>;
    }

    const kategori = eventInfo.event.extendedProps.kategori as TatilKategori;
    const gunSuresi = eventInfo.event.extendedProps.gunSuresi as TatilGunSuresi;
    return <div className="flex min-w-0 items-center gap-1.5 text-xs"><span className="truncate font-semibold">{eventInfo.event.title}</span>{gunSuresi === "YARIM_GUN" && <span className="shrink-0 rounded-full bg-gray-900/10 px-1.5 py-0.5 text-[10px] font-semibold text-gray-700">Yarım gün</span>}<span className="sr-only">{tatilKategoriEtiketi(kategori)}</span></div>;
}

function takvimEventYerlestirildi(arg: EventMountArg) {
    if (arg.event.extendedProps.tur === TAKVIM_EVENT_TURLERI.DOGUM_GUNU) {
        arg.el.title = `${arg.event.title} · ${arg.event.extendedProps.departman as string}`;
    }
}
