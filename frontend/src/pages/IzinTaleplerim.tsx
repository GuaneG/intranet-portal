import { type FormEvent, useCallback, useEffect, useRef, useState } from "react";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import IzinTalebiFormu from "../components/izin-talebi/IzinTalebiFormu";
import IzinTalepleriTablosu from "../components/izin-talebi/IzinTalepleriTablosu";
import { ApiError, isGunuOnizle, izinTalebiOlustur, izinTalebiniIptalEt, izinTaleplerimiGetir, izinTurleriniGetir } from "../lib/api";
import { bugununTarihi, izinTalepFormunuDogrula, izinTalepIstekGövdesiniOlustur } from "../lib/izinTalebi";
import type { IzinTalepDurumu, IzinTalebiFormu as IzinTalebiFormuDegerleri, IzinTalepleriSayfasi, IzinTuru } from "../types/izinTalebi";

const SAYFA_BOYUTU = 10;
const BOS_FORM: IzinTalebiFormuDegerleri = { izinTuruId: "", baslangicTarihi: "", bitisTarihi: "", yoneticiyeNot: "" };

function hataMesajiniGetir(error: unknown, fallback: string) {
    return error instanceof ApiError && error.message ? error.message : fallback;
}

/** İzin oluşturma formunu ve kullanıcının kendi talep listesini koordine eder. */
export default function IzinTaleplerim() {
    const [form, setForm] = useState<IzinTalebiFormuDegerleri>(BOS_FORM);
    const [izinTurleri, setIzinTurleri] = useState<IzinTuru[]>([]);
    const [izinTurleriYukleniyor, setIzinTurleriYukleniyor] = useState(false);
    const [izinTurleriHatasi, setIzinTurleriHatasi] = useState<string | null>(null);
    const [onizleme, setOnizleme] = useState<number | null>(null);
    const [onizlemeYukleniyor, setOnizlemeYukleniyor] = useState(false);
    const [onizlemeHatasi, setOnizlemeHatasi] = useState<string | null>(null);
    const [formHatasi, setFormHatasi] = useState<string | null>(null);
    const [gonderimSuruyor, setGonderimSuruyor] = useState(false);
    const [basariMesaji, setBasariMesaji] = useState<string | null>(null);
    const [durum, setDurum] = useState<"" | IzinTalepDurumu>("");
    const [sayfaVerisi, setSayfaVerisi] = useState<IzinTalepleriSayfasi | null>(null);
    const [listeYukleniyor, setListeYukleniyor] = useState(false);
    const [listeHatasi, setListeHatasi] = useState<string | null>(null);
    const [listeIslemHatasi, setListeIslemHatasi] = useState<string | null>(null);
    const [iptalEdilenTalepId, setIptalEdilenTalepId] = useState<string | null>(null);
    const sonListeIstegi = useRef(0);
    const sonOnizlemeIstegi = useRef(0);
    const bugun = bugununTarihi();

    const izinTurleriniYukle = useCallback(async () => {
        setIzinTurleriYukleniyor(true);
        setIzinTurleriHatasi(null);
        try {
            setIzinTurleri(await izinTurleriniGetir());
        } catch (error) {
            setIzinTurleri([]);
            setIzinTurleriHatasi(hataMesajiniGetir(error, "İzin türleri alınamadı. Lütfen tekrar deneyin."));
        } finally {
            setIzinTurleriYukleniyor(false);
        }
    }, []);

    const talepleriYukle = useCallback(async (yeniDurum: "" | IzinTalepDurumu, page: number) => {
        const istekNumarasi = ++sonListeIstegi.current;
        setListeYukleniyor(true);
        setListeHatasi(null);
        try {
            const sonuc = await izinTaleplerimiGetir({ page, size: SAYFA_BOYUTU, durum: yeniDurum || undefined });
            if (istekNumarasi === sonListeIstegi.current) setSayfaVerisi(sonuc);
        } catch (error) {
            if (istekNumarasi === sonListeIstegi.current) {
                setSayfaVerisi(null);
                setListeHatasi(hataMesajiniGetir(error, "İzin talepleriniz alınamadı. Lütfen tekrar deneyin."));
            }
        } finally {
            if (istekNumarasi === sonListeIstegi.current) setListeYukleniyor(false);
        }
    }, []);

    useEffect(() => {
        void izinTurleriniYukle();
        void talepleriYukle("", 0);
    }, [izinTurleriniYukle, talepleriYukle]);

    useEffect(() => {
        const tarihlerHazir = form.baslangicTarihi !== "" && form.bitisTarihi !== "" && form.baslangicTarihi >= bugun && form.bitisTarihi >= form.baslangicTarihi;
        if (!tarihlerHazir) {
            setOnizleme(null);
            setOnizlemeHatasi(null);
            setOnizlemeYukleniyor(false);
            return;
        }

        const istekNumarasi = ++sonOnizlemeIstegi.current;
        const zamanlayici = window.setTimeout(() => {
            setOnizlemeYukleniyor(true);
            setOnizlemeHatasi(null);
            void isGunuOnizle(form.baslangicTarihi, form.bitisTarihi).then((sonuc) => {
                if (istekNumarasi === sonOnizlemeIstegi.current) setOnizleme(sonuc.isGunuSayisi);
            }).catch((error: unknown) => {
                if (istekNumarasi === sonOnizlemeIstegi.current) {
                    setOnizleme(null);
                    setOnizlemeHatasi(hataMesajiniGetir(error, "İş günü önizlemesi alınamadı. Talebi yine de gönderebilirsiniz."));
                }
            }).finally(() => {
                if (istekNumarasi === sonOnizlemeIstegi.current) setOnizlemeYukleniyor(false);
            });
        }, 300);

        return () => window.clearTimeout(zamanlayici);
    }, [bugun, form.baslangicTarihi, form.bitisTarihi]);

    const formuGuncelle = (alan: keyof IzinTalebiFormuDegerleri, deger: string) => {
        setForm((onceki) => ({ ...onceki, [alan]: deger }));
        setFormHatasi(null);
        setBasariMesaji(null);
    };

    const formuGonder = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (gonderimSuruyor) return;
        const hata = izinTalepFormunuDogrula(form, bugun);
        if (hata) {
            setFormHatasi(hata);
            return;
        }

        setGonderimSuruyor(true);
        setFormHatasi(null);
        setBasariMesaji(null);
        try {
            const olusturulanTalep = await izinTalebiOlustur(izinTalepIstekGövdesiniOlustur(form));
            setForm(BOS_FORM);
            setBasariMesaji(`${olusturulanTalep.izinTuru} talebiniz oluşturuldu.`);
            await talepleriYukle(durum, 0);
        } catch (error) {
            setFormHatasi(hataMesajiniGetir(error, "İzin talebi oluşturulamadı. Lütfen tekrar deneyin."));
        } finally {
            setGonderimSuruyor(false);
        }
    };

    const durumDegistir = (yeniDurum: "" | IzinTalepDurumu) => {
        setDurum(yeniDurum);
        void talepleriYukle(yeniDurum, 0);
    };

    const talebiIptalEt = async (talepId: string) => {
        if (iptalEdilenTalepId) return;
        if (!window.confirm("Bu bekleyen izin talebini iptal etmek istiyor musunuz?")) return;

        setIptalEdilenTalepId(talepId);
        setListeIslemHatasi(null);
        setBasariMesaji(null);

        try {
            await izinTalebiniIptalEt(talepId);
            await talepleriYukle(durum, sayfaVerisi?.sayfa ?? 0);
            setBasariMesaji("İzin talebiniz iptal edildi.");
        } catch (error) {
            setListeIslemHatasi(hataMesajiniGetir(error, "İzin talebi iptal edilemedi. Lütfen tekrar deneyin."));

            if (error instanceof ApiError && error.status === 409) {
                await talepleriYukle(durum, sayfaVerisi?.sayfa ?? 0);
            }
        } finally {
            setIptalEdilenTalepId(null);
        }
    };

    return <>
        <PageMeta title="İzin Taleplerim | Intranet Portal" description="İzin talebi oluşturma ve kendi izin taleplerini görüntüleme ekranı" />
        <PageBreadcrumb pageTitle="İzin Taleplerim" />
        <div className="space-y-6">
            <div><h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">İzin planın</h2><p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Yeni izin talebi oluşturun ve önceki taleplerinizin durumunu takip edin.</p></div>
            {basariMesaji && <div role="status" className="rounded-xl border border-success-200 bg-success-50 px-5 py-4 text-sm text-success-700 dark:border-success-500/20 dark:bg-success-500/10 dark:text-success-400">{basariMesaji}</div>}
            {listeIslemHatasi && <div role="alert" className="rounded-xl border border-error-200 bg-error-50 px-5 py-4 text-sm text-error-700 dark:border-error-500/20 dark:bg-error-500/10 dark:text-error-400">{listeIslemHatasi}</div>}
            <IzinTalebiFormu form={form} izinTurleri={izinTurleri} izinTurleriYukleniyor={izinTurleriYukleniyor} izinTurleriHatasi={izinTurleriHatasi} onDegisiklik={formuGuncelle} onIzinTurleriniTekrarDene={() => void izinTurleriniYukle()} onSubmit={(event) => void formuGonder(event)} isGunuSayisi={onizleme} onizlemeYukleniyor={onizlemeYukleniyor} onizlemeHatasi={onizlemeHatasi} gonderimSuruyor={gonderimSuruyor} formHatasi={formHatasi} bugun={bugun} />
            <IzinTalepleriTablosu sayfaVerisi={sayfaVerisi} durum={durum} yukleniyor={listeYukleniyor} hata={listeHatasi} onDurumDegistir={durumDegistir} onTekrarDene={() => void talepleriYukle(durum, 0)} onOncekiSayfa={() => { if (sayfaVerisi && sayfaVerisi.sayfa > 0) void talepleriYukle(durum, sayfaVerisi.sayfa - 1); }} onSonrakiSayfa={() => { if (sayfaVerisi && sayfaVerisi.sayfa + 1 < sayfaVerisi.toplamSayfa) void talepleriYukle(durum, sayfaVerisi.sayfa + 1); }} iptalEdilenTalepId={iptalEdilenTalepId} onIptalIstegi={(talepId) => void talebiIptalEt(talepId)} />
        </div>
    </>;
}
