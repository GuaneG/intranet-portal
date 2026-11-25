import { useCallback, useEffect, useRef, useState } from "react";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import IzinKararTablosu from "../components/izin-talebi/IzinKararTablosu";
import IzinRedModali from "../components/izin-talebi/IzinRedModali";
import IzinNotuModali from "../components/izin-talebi/IzinNotuModali";
import { adminIzinTalepleriniGetir, ApiError, ekipIzinTalepleriniGetir, izinTalebiniOnayla, izinTalebiniReddet } from "../lib/api";
import type { IzinTalepDurumu, KararBekleyenIzinTalebi, KararBekleyenIzinTalepleriSayfasi } from "../types/izinTalebi";

const SAYFA_BOYUTU = 10;

type Kapsam = "EKIP" | "SIRKET";

type Props = { kapsam: Kapsam };

function hataMesajiniGetir(error: unknown, fallback: string) {
    return error instanceof ApiError && error.message ? error.message : fallback;
}

/** Backend'in yönetici ve admin için ayırdığı iki karar listesini tek izin arayüzünde sunar. */
export default function IzinKararListesi({ kapsam }: Props) {
    const ekipKapsami = kapsam === "EKIP";
    const baslik = ekipKapsami ? "Ekip İzin Talepleri" : "Tüm İzin Talepleri";
    const aciklama = ekipKapsami ? "Doğrudan çalışanlarınızın izin taleplerini değerlendirin." : "Şirket genelindeki izin taleplerini değerlendirin.";
    const [durum, setDurum] = useState<"" | IzinTalepDurumu>("");
    const [sayfaVerisi, setSayfaVerisi] = useState<KararBekleyenIzinTalepleriSayfasi | null>(null);
    const [yukleniyor, setYukleniyor] = useState(false);
    const [hata, setHata] = useState<string | null>(null);
    const [islemHatasi, setIslemHatasi] = useState<string | null>(null);
    const [islemYapilanTalepId, setIslemYapilanTalepId] = useState<string | null>(null);
    const [redTalebi, setRedTalebi] = useState<KararBekleyenIzinTalebi | null>(null);
    const [notTalebi, setNotTalebi] = useState<KararBekleyenIzinTalebi | null>(null);
    const [redNedeniTalebi, setRedNedeniTalebi] = useState<KararBekleyenIzinTalebi | null>(null);
    const sonListeIstegi = useRef(0);

    const talepleriYukle = useCallback(async (yeniDurum: "" | IzinTalepDurumu, page: number) => {
        const istekNumarasi = ++sonListeIstegi.current;
        setYukleniyor(true);
        setHata(null);
        try {
            const params = { page, size: SAYFA_BOYUTU, durum: yeniDurum || undefined };
            const sonuc = ekipKapsami
                ? await ekipIzinTalepleriniGetir(params)
                : await adminIzinTalepleriniGetir(params);
            if (istekNumarasi === sonListeIstegi.current) setSayfaVerisi(sonuc);
        } catch (error) {
            if (istekNumarasi === sonListeIstegi.current) {
                setSayfaVerisi(null);
                setHata(hataMesajiniGetir(error, "İzin talepleri alınamadı. Lütfen tekrar deneyin."));
            }
        } finally {
            if (istekNumarasi === sonListeIstegi.current) setYukleniyor(false);
        }
    }, [ekipKapsami]);

    useEffect(() => { void talepleriYukle("", 0); }, [talepleriYukle]);

    const listeyiYenile = async () => {
        await talepleriYukle(durum, sayfaVerisi?.sayfa ?? 0);
    };

    const islemiYurut = async (talep: KararBekleyenIzinTalebi, islem: () => Promise<void>, basariMesaji: string) => {
        if (islemYapilanTalepId) return;
        setIslemYapilanTalepId(talep.id);
        setIslemHatasi(null);
        try {
            await islem();
            setRedTalebi(null);
            await listeyiYenile();
            setIslemHatasi(basariMesaji);
        } catch (error) {
            const mesaj = hataMesajiniGetir(error, "İzin talebi işlenemedi. Lütfen tekrar deneyin.");
            setIslemHatasi(mesaj);
            if (error instanceof ApiError && error.status === 409) {
                setRedTalebi(null);
                await listeyiYenile();
            }
        } finally {
            setIslemYapilanTalepId(null);
        }
    };

    const onayIstegi = (talep: KararBekleyenIzinTalebi) => {
        if (!window.confirm(`${talep.talepSahibi.ad} ${talep.talepSahibi.soyad} adlı personelin izin talebini onaylamak istiyor musunuz?`)) return;
        void islemiYurut(talep, () => izinTalebiniOnayla(talep.id), "İzin talebi onaylandı.");
    };

    const redTalebiniGonder = (kararNotu: string) => {
        if (!redTalebi) return;
        void islemiYurut(redTalebi, () => izinTalebiniReddet(redTalebi.id, kararNotu), "İzin talebi reddedildi.");
    };

    const durumDegistir = (yeniDurum: "" | IzinTalepDurumu) => {
        setDurum(yeniDurum);
        setIslemHatasi(null);
        void talepleriYukle(yeniDurum, 0);
    };

    return <>
        <PageMeta title={`${baslik} | Intranet Portal`} description={aciklama} />
        <PageBreadcrumb pageTitle={baslik} />
        <div className="space-y-6">
            <div><h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">{baslik}</h2><p className="mt-1 text-sm text-gray-500 dark:text-gray-400">{aciklama}</p></div>
            {islemHatasi && <div role="status" className={`rounded-xl border px-5 py-4 text-sm ${islemHatasi.endsWith("onaylandı.") || islemHatasi.endsWith("reddedildi.") ? "border-success-200 bg-success-50 text-success-700 dark:border-success-500/20 dark:bg-success-500/10 dark:text-success-400" : "border-error-200 bg-error-50 text-error-700 dark:border-error-500/20 dark:bg-error-500/10 dark:text-error-400"}`}>{islemHatasi}</div>}
            <IzinKararTablosu sayfaVerisi={sayfaVerisi} durum={durum} yukleniyor={yukleniyor} hata={hata} islemYapilanTalepId={islemYapilanTalepId} pasifEtiketiGoster={!ekipKapsami} onDurumDegistir={durumDegistir} onTekrarDene={() => void listeyiYenile()} onOncekiSayfa={() => { if (sayfaVerisi && sayfaVerisi.sayfa > 0) void talepleriYukle(durum, sayfaVerisi.sayfa - 1); }} onSonrakiSayfa={() => { if (sayfaVerisi && sayfaVerisi.sayfa + 1 < sayfaVerisi.toplamSayfa) void talepleriYukle(durum, sayfaVerisi.sayfa + 1); }} onNotuGor={setNotTalebi} onRedNedeniniGor={setRedNedeniTalebi} onOnayIstegi={onayIstegi} onRedIstegi={(talep) => { setIslemHatasi(null); setRedTalebi(talep); }} />
        </div>
        <IzinRedModali isOpen={redTalebi !== null} personelAdi={redTalebi ? `${redTalebi.talepSahibi.ad} ${redTalebi.talepSahibi.soyad}` : ""} islemSuruyor={islemYapilanTalepId === redTalebi?.id} hata={redTalebi ? islemHatasi : null} onKapat={() => setRedTalebi(null)} onReddet={redTalebiniGonder} />
        <IzinNotuModali isOpen={notTalebi !== null} personelAdi={notTalebi ? `${notTalebi.talepSahibi.ad} ${notTalebi.talepSahibi.soyad}` : ""} not={notTalebi?.yoneticiyeNot ?? ""} onKapat={() => setNotTalebi(null)} />
        <IzinNotuModali isOpen={redNedeniTalebi !== null} personelAdi={redNedeniTalebi ? `${redNedeniTalebi.talepSahibi.ad} ${redNedeniTalebi.talepSahibi.soyad}` : ""} not={redNedeniTalebi?.kararNotu ?? ""} baslik="Red nedeni" aciklama={redNedeniTalebi ? `${redNedeniTalebi.talepSahibi.ad} ${redNedeniTalebi.talepSahibi.soyad} adlı personelin izin talebine verilen red nedeni.` : undefined} onKapat={() => setRedNedeniTalebi(null)} />
    </>;
}
