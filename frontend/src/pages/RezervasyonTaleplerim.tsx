import { type FormEvent, useCallback, useEffect, useRef, useState } from "react";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import IzinNotuModali from "../components/izin-talebi/IzinNotuModali";
import { Modal } from "../components/ui/modal";
import Badge from "../components/ui/badge/Badge";
import { Table, TableBody, TableCell, TableHeader, TableRow } from "../components/ui/table";
import { useAuth } from "../context/AuthContext";
import { ApiError, adminRezervasyonGecmisiniGetir, odalariGetir, rezervasyonGecmisimiGetir, rezervasyonlariGetir, rezervasyonOlustur, rezervasyonuIptalEt, tatilleriGetir } from "../lib/api";
import { bugununTarihi, enIleriRezervasyonTarihi, formdanGridSecimiOlustur, gridSecimindenRezervasyonAraligiOlustur, gunlukOdaGridiniOlustur, iptalNedeniniDogrula, rezervasyonFormunuDogrula, rezervasyonIstekGövdesiniOlustur } from "../lib/rezervasyon";
import { rezervasyonOlusturanAdi, saatiBicimlendir, saatAraliginiBicimlendir, takvimTarihiniBicimlendir } from "../lib/rezervasyonTakvimi";
import { tarihteTatilBul } from "../lib/tatilTakvimi";
import type { Oda, Rezervasyon, RezervasyonDurumu, RezervasyonGecmisi, RezervasyonGecmisiSayfasi, RezervasyonOlusturmaIstek } from "../types/rezervasyon";
import type { TatilGunSuresi } from "../types/tatil";

type RezervasyonFormuDegerleri = {
  odaId: string;
  tarih: string;
  baslangicSaat: string;
  bitisSaat: string;
  baslik: string;
  katilimciSayisi: string;
};

type GecmisFiltreleri = {
  durum: "" | RezervasyonDurumu;
  odaId: string;
  baslangic: string;
  bitis: string;
  olusturanId: string;
};

type GridSecimi = { odaId: number; ilkSaat: number; sonSaat: number };

const SAYFA_BOYUTU = 20;
const BOS_FORM: RezervasyonFormuDegerleri = { odaId: "", tarih: "", baslangicSaat: "", bitisSaat: "", baslik: "", katilimciSayisi: "" };
const BOS_FILTRE: GecmisFiltreleri = { durum: "", odaId: "", baslangic: "", bitis: "", olusturanId: "" };

function hataMesajiniGetir(error: unknown, fallback: string) {
  return error instanceof ApiError && error.message ? error.message : fallback;
}

function anlikSaatiGetir() {
  return new Date().getHours();
}

function durumRozeti(durum: RezervasyonDurumu) {
  return durum === "AKTIF" ? { metin: "Aktif", renk: "success" as const } : { metin: "İptal edildi", renk: "error" as const };
}

function tarihSaatiniBicimlendir(tarih: string | null) {
  if (!tarih) return "-";
  return new Intl.DateTimeFormat("tr-TR", { dateStyle: "medium", timeStyle: "short", timeZone: "Europe/Istanbul" }).format(new Date(tarih));
}

/** Tek ekranda rezervasyon oluşturma, doluluk takvimi ve geçmiş akışlarını koordine eder. */
export default function RezervasyonTaleplerim() {
  const { kullanici } = useAuth();
  const adminMi = kullanici?.rol === "ADMIN";
  const bugun = bugununTarihi();
  const [odalar, setOdalar] = useState<Oda[]>([]);
  const [odalarYukleniyor, setOdalarYukleniyor] = useState(true);
  const [odaHatasi, setOdaHatasi] = useState<string | null>(null);
  const [form, setForm] = useState<RezervasyonFormuDegerleri>(BOS_FORM);
  const [tatilGunSuresi, setTatilGunSuresi] = useState<TatilGunSuresi | null>(null);
  const [tatilUyarisi, setTatilUyarisi] = useState<string | null>(null);
  const [formHatasi, setFormHatasi] = useState<string | null>(null);
  const [gonderimSuruyor, setGonderimSuruyor] = useState(false);
  const [basariMesaji, setBasariMesaji] = useState<string | null>(null);
  const [seciliTarih, setSeciliTarih] = useState(bugun);
  const [gridTatilGunSuresi, setGridTatilGunSuresi] = useState<TatilGunSuresi | null>(null);
  const [gridTatilHatasi, setGridTatilHatasi] = useState<string | null>(null);
  const [gridSecimi, setGridSecimi] = useState<GridSecimi | null>(null);
  const [rezervasyonlar, setRezervasyonlar] = useState<Rezervasyon[] | null>(null);
  const [takvimYukleniyor, setTakvimYukleniyor] = useState(false);
  const [takvimHatasi, setTakvimHatasi] = useState<string | null>(null);
  const [seciliRezervasyon, setSeciliRezervasyon] = useState<Rezervasyon | null>(null);
  const [gecmisKapsami, setGecmisKapsami] = useState<"KENDI" | "SIRKET">("KENDI");
  const [filtreFormu, setFiltreFormu] = useState<GecmisFiltreleri>(BOS_FILTRE);
  const [uygulananFiltre, setUygulananFiltre] = useState<GecmisFiltreleri>(BOS_FILTRE);
  const [gecmisSayfasi, setGecmisSayfasi] = useState(0);
  const [gecmis, setGecmis] = useState<RezervasyonGecmisiSayfasi | null>(null);
  const [gecmisYukleniyor, setGecmisYukleniyor] = useState(false);
  const [gecmisHatasi, setGecmisHatasi] = useState<string | null>(null);
  const [iptalEdilenId, setIptalEdilenId] = useState<string | null>(null);
  const [adminIptalTalebi, setAdminIptalTalebi] = useState<Rezervasyon | RezervasyonGecmisi | null>(null);
  const [iptalNedeni, setIptalNedeni] = useState("");
  const [iptalHatasi, setIptalHatasi] = useState<string | null>(null);
  const [notuGoster, setNotuGoster] = useState<{ baslik: string; aciklama: string; not: string } | null>(null);
  const sonTakvimIstegi = useRef(0);
  const sonGecmisIstegi = useRef(0);

  const seciliOda = odalar.find((oda) => oda.id === Number(form.odaId)) ?? null;

  const odalariYukle = useCallback(async () => {
    setOdalarYukleniyor(true);
    setOdaHatasi(null);
    try { setOdalar(await odalariGetir()); }
    catch (error) { setOdalar([]); setOdaHatasi(hataMesajiniGetir(error, "Odalar alınamadı. Lütfen tekrar deneyin.")); }
    finally { setOdalarYukleniyor(false); }
  }, []);

  const takvimiYukle = useCallback(async (tarih: string) => {
    const istekNo = ++sonTakvimIstegi.current;
    setTakvimYukleniyor(true); setTakvimHatasi(null);
    try {
      const sonuc = await rezervasyonlariGetir(tarih, tarih);
      if (istekNo === sonTakvimIstegi.current) setRezervasyonlar(sonuc);
    } catch (error) {
      if (istekNo === sonTakvimIstegi.current) { setRezervasyonlar(null); setTakvimHatasi(hataMesajiniGetir(error, "Rezervasyon takvimi alınamadı. Lütfen tekrar deneyin.")); }
    } finally { if (istekNo === sonTakvimIstegi.current) setTakvimYukleniyor(false); }
  }, []);

  const gecmisiYukle = useCallback(async (kapsam: "KENDI" | "SIRKET", filtre: GecmisFiltreleri, page: number) => {
    const istekNo = ++sonGecmisIstegi.current;
    setGecmisYukleniyor(true); setGecmisHatasi(null);
    const params = { page, size: SAYFA_BOYUTU, durum: filtre.durum || undefined, odaId: filtre.odaId || undefined, baslangic: filtre.baslangic || undefined, bitis: filtre.bitis || undefined, olusturanId: filtre.olusturanId || undefined };
    try {
      const sonuc = kapsam === "SIRKET" ? await adminRezervasyonGecmisiniGetir(params) : await rezervasyonGecmisimiGetir(params);
      if (istekNo === sonGecmisIstegi.current) setGecmis(sonuc);
    } catch (error) {
      if (istekNo === sonGecmisIstegi.current) { setGecmis(null); setGecmisHatasi(hataMesajiniGetir(error, "Rezervasyon geçmişi alınamadı. Lütfen tekrar deneyin.")); }
    } finally { if (istekNo === sonGecmisIstegi.current) setGecmisYukleniyor(false); }
  }, []);

  useEffect(() => { void odalariYukle(); }, [odalariYukle]);
  useEffect(() => { void gecmisiYukle(gecmisKapsami, uygulananFiltre, gecmisSayfasi); }, [gecmisKapsami, gecmisSayfasi, gecmisiYukle, uygulananFiltre]);
  useEffect(() => { void takvimiYukle(seciliTarih); }, [seciliTarih, takvimiYukle]);

  useEffect(() => {
    let iptalMi = false;
    setGridTatilHatasi(null);
    void tatilleriGetir(seciliTarih, seciliTarih).then((tatiller) => {
      if (!iptalMi) setGridTatilGunSuresi(tarihteTatilBul(tatiller, seciliTarih)?.gunSuresi ?? null);
    }).catch(() => {
      if (!iptalMi) {
        setGridTatilGunSuresi(null);
        setGridTatilHatasi("Tatil bilgisi alınamadı. Boş saat seçimi backend tarafından yine doğrulanır.");
      }
    });
    return () => { iptalMi = true; };
  }, [seciliTarih]);

  useEffect(() => {
    if (!form.tarih) { setTatilGunSuresi(null); setTatilUyarisi(null); return; }
    let iptalMi = false;
    void tatilleriGetir(form.tarih, form.tarih).then((tatiller) => {
      if (!iptalMi) { setTatilGunSuresi(tarihteTatilBul(tatiller, form.tarih)?.gunSuresi ?? null); setTatilUyarisi(null); }
    }).catch(() => { if (!iptalMi) { setTatilGunSuresi(null); setTatilUyarisi("Tatil bilgisi kontrol edilemedi. Rezervasyon kuralları gönderim sırasında backend tarafından yeniden doğrulanır."); } });
    return () => { iptalMi = true; };
  }, [form.tarih]);

  const formuGuncelle = (alan: keyof RezervasyonFormuDegerleri, deger: string) => {
    setForm((onceki) => ({ ...onceki, [alan]: deger }));
    if (alan === "tarih" && deger) seciliTarihiDegistir(deger);
    setFormHatasi(null);
    setBasariMesaji(null);
  };

  function seciliTarihiDegistir(yeniTarih: string) {
    setSeciliTarih(yeniTarih);
    setGridSecimi(null);
    setForm((onceki) => ({ ...onceki, tarih: yeniTarih }));
  }

  function seciliTarihiKaydir(gunFarki: number) {
    const [yil, ay, gun] = seciliTarih.split("-").map(Number);
    const tarih = new Date(yil, ay - 1, gun);
    tarih.setDate(tarih.getDate() + gunFarki);
    seciliTarihiDegistir(`${tarih.getFullYear()}-${String(tarih.getMonth() + 1).padStart(2, "0")}-${String(tarih.getDate()).padStart(2, "0")}`);
  }

  function gridHucresineTikla(odaId: number, baslangicSaat: number, durum: string, rezervasyon: Rezervasyon | null, hucreler: Array<{ baslangicSaat: number; durum: string }>) {
    if (durum === "DOLU" && rezervasyon) {
      setSeciliRezervasyon(rezervasyon);
      return;
    }
    if (durum !== "MUSAIT") return;

    let yeniSecim: GridSecimi = { odaId, ilkSaat: baslangicSaat, sonSaat: baslangicSaat };
    if (gridSecimi?.odaId === odaId) {
      if (baslangicSaat === gridSecimi.sonSaat + 1 && gridSecimi.sonSaat - gridSecimi.ilkSaat < 3) yeniSecim = { ...gridSecimi, sonSaat: baslangicSaat };
      else if (baslangicSaat === gridSecimi.ilkSaat - 1 && gridSecimi.sonSaat - gridSecimi.ilkSaat < 3) yeniSecim = { ...gridSecimi, ilkSaat: baslangicSaat };
    }

    const aralik = gridSecimindenRezervasyonAraligiOlustur(odaId, yeniSecim.ilkSaat, yeniSecim.sonSaat, hucreler);
    if (!aralik) {
      setGridSecimi(null);
      return;
    }

    setGridSecimi(yeniSecim);
    setForm((onceki) => ({ ...onceki, ...aralik, tarih: seciliTarih }));
    setFormHatasi(null);
    setBasariMesaji(null);
  }

  async function formuGonder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (gonderimSuruyor) return;
    const hata = rezervasyonFormunuDogrula(form, { bugun, mevcutSaat: anlikSaatiGetir(), odaKapasitesi: seciliOda?.kapasite ?? null, tatilGunSuresi });
    if (hata) { setFormHatasi(hata); return; }
    setGonderimSuruyor(true); setFormHatasi(null); setBasariMesaji(null);
    try {
      const olusturulan = await rezervasyonOlustur(rezervasyonIstekGövdesiniOlustur(form) as RezervasyonOlusturmaIstek);
      setForm(BOS_FORM); setTatilGunSuresi(null); setBasariMesaji(`${olusturulan.oda.ad} için rezervasyonunuz oluşturuldu.`);
      await takvimiYukle(seciliTarih);
      await gecmisiYukle(gecmisKapsami, uygulananFiltre, 0); setGecmisSayfasi(0);
    } catch (error) { setFormHatasi(hataMesajiniGetir(error, "Rezervasyon oluşturulamadı. Lütfen tekrar deneyin.")); }
    finally { setGonderimSuruyor(false); }
  }

  function filtreyiUygula(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if ((filtreFormu.baslangic && !filtreFormu.bitis) || (!filtreFormu.baslangic && filtreFormu.bitis)) { setGecmisHatasi("Başlangıç ve bitiş tarihi birlikte seçilmelidir."); return; }
    if (filtreFormu.baslangic && filtreFormu.bitis && filtreFormu.bitis < filtreFormu.baslangic) { setGecmisHatasi("Bitiş tarihi başlangıçtan önce olamaz."); return; }
    setGecmisHatasi(null); setUygulananFiltre(filtreFormu); setGecmisSayfasi(0);
  }

  function filtreyiTemizle() { setFiltreFormu(BOS_FILTRE); setUygulananFiltre(BOS_FILTRE); setGecmisSayfasi(0); }

  function kapsamiDegistir(kapsam: "KENDI" | "SIRKET") { setGecmisKapsami(kapsam); setFiltreFormu(BOS_FILTRE); setUygulananFiltre(BOS_FILTRE); setGecmisSayfasi(0); }

  async function iptalEt(rezervasyon: { id: string }, neden?: string) {
    if (iptalEdilenId) return;
    setIptalEdilenId(rezervasyon.id); setIptalHatasi(null); setBasariMesaji(null);
    try {
      await rezervasyonuIptalEt(rezervasyon.id, neden);
      setAdminIptalTalebi(null); setIptalNedeni(""); setBasariMesaji("Rezervasyon iptal edildi.");
      await gecmisiYukle(gecmisKapsami, uygulananFiltre, gecmisSayfasi);
      setSeciliRezervasyon(null);
      await takvimiYukle(seciliTarih);
    } catch (error) {
      setIptalHatasi(hataMesajiniGetir(error, "Rezervasyon iptal edilemedi. Lütfen tekrar deneyin."));
      if (error instanceof ApiError && error.status === 409) await gecmisiYukle(gecmisKapsami, uygulananFiltre, gecmisSayfasi);
    } finally { setIptalEdilenId(null); }
  }

  function kendiRezervasyonunuIptalEt(rezervasyon: RezervasyonGecmisi) {
    if (window.confirm("Başlamamış bu rezervasyonu iptal etmek istiyor musunuz?")) void iptalEt(rezervasyon);
  }

  function seciliRezervasyonuIptalEt() {
    if (!seciliRezervasyon || !(seciliRezervasyon.benMiOlusturdum || adminMi)) return;
    if (adminMi && !seciliRezervasyon.benMiOlusturdum) {
      setAdminIptalTalebi(seciliRezervasyon);
      setIptalNedeni("");
      setIptalHatasi(null);
      return;
    }
    if (window.confirm("Başlamamış bu rezervasyonu iptal etmek istiyor musunuz?")) void iptalEt(seciliRezervasyon);
  }

  function adminIptaliniKaydet(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!adminIptalTalebi) return;
    const hata = iptalNedeniniDogrula(iptalNedeni);
    if (hata) { setIptalHatasi(hata); return; }
    void iptalEt(adminIptalTalebi, iptalNedeni.trim());
  }

  const gridSatirlari = gunlukOdaGridiniOlustur(odalar, rezervasyonlar ?? [], {
    tarih: seciliTarih,
    tatilGunSuresi: gridTatilGunSuresi,
    bugun,
    mevcutSaat: anlikSaatiGetir(),
  });

  useEffect(() => {
    const yeniSecim = formdanGridSecimiOlustur(form, seciliTarih, gridSatirlari);
    setGridSecimi((onceki) => (
      onceki?.odaId === yeniSecim?.odaId
      && onceki?.ilkSaat === yeniSecim?.ilkSaat
      && onceki?.sonSaat === yeniSecim?.sonSaat
        ? onceki
        : yeniSecim
    ));
  }, [form, gridSatirlari, seciliTarih]);

  return <>
    <PageMeta title="Rezervasyon Taleplerim | Intranet Portal" description="Oda rezervasyonu oluşturma, doluluk takvimi ve rezervasyon geçmişi" />
    <PageBreadcrumb pageTitle="Rezervasyon Taleplerim" />
    <div className="space-y-6">
      <div><h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">Oda rezervasyonların</h2><p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Uygun bir toplantı odasını ayırtın, doluluğu inceleyin ve geçmiş kayıtlarınızı takip edin.</p></div>
      {basariMesaji && <div role="status" className="rounded-xl border border-success-200 bg-success-50 px-5 py-4 text-sm text-success-700 dark:border-success-500/20 dark:bg-success-500/10 dark:text-success-400">{basariMesaji}</div>}

      <section className="rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]">
        <div className="border-b border-gray-100 px-5 py-5 dark:border-gray-800 sm:px-6">
          <h3 className="text-base font-semibold text-gray-800 dark:text-white/90">Yeni rezervasyon</h3>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Çalışma saatleri 08:00–18:00 arasındadır; kurallar gönderim sırasında backend tarafından da doğrulanır.</p>
        </div>
        <form onSubmit={(event) => void formuGonder(event)} className="space-y-5 p-5 sm:p-6">
          <div className="grid gap-5 md:grid-cols-3">
            <div><label htmlFor="rezervasyon-oda" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Oda</label><select id="rezervasyon-oda" value={form.odaId} onChange={(event) => formuGuncelle("odaId", event.target.value)} disabled={odalarYukleniyor || odalar.length === 0 || gonderimSuruyor} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"><option value="">{odalarYukleniyor ? "Odalar yükleniyor…" : "Oda seçin"}</option>{odalar.map((oda) => <option key={oda.id} value={oda.id}>{oda.ad} ({oda.kapasite})</option>)}</select></div>
            <div><label htmlFor="rezervasyon-tarih" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Tarih</label><input id="rezervasyon-tarih" type="date" min={bugun} max={enIleriRezervasyonTarihi(bugun)} value={form.tarih} onChange={(event) => formuGuncelle("tarih", event.target.value)} disabled={gonderimSuruyor} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" /></div>
            <div><label htmlFor="rezervasyon-katilimci-sayisi" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Katılımcı sayısı</label><input id="rezervasyon-katilimci-sayisi" type="number" min="1" max={seciliOda?.kapasite} value={form.katilimciSayisi} onChange={(event) => formuGuncelle("katilimciSayisi", event.target.value)} disabled={gonderimSuruyor} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" placeholder={seciliOda ? `En fazla ${seciliOda.kapasite}` : "Önce oda seçin"} /></div>
            <div><label htmlFor="rezervasyon-baslangic-saati" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Başlangıç saati</label><select id="rezervasyon-baslangic-saati" value={form.baslangicSaat} onChange={(event) => formuGuncelle("baslangicSaat", event.target.value)} disabled={gonderimSuruyor} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"><option value="">Saat seçin</option>{Array.from({ length: 10 }, (_, index) => index + 8).map((saat) => <option key={saat} value={saat}>{saatiBicimlendir(saat)}</option>)}</select></div>
            <div><label htmlFor="rezervasyon-bitis-saati" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Bitiş saati</label><select id="rezervasyon-bitis-saati" value={form.bitisSaat} onChange={(event) => formuGuncelle("bitisSaat", event.target.value)} disabled={gonderimSuruyor} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"><option value="">Saat seçin</option>{Array.from({ length: 10 }, (_, index) => index + 9).map((saat) => <option key={saat} value={saat}>{saatiBicimlendir(saat)}</option>)}</select></div>
            <div><label htmlFor="rezervasyon-baslik" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Rezervasyon başlığı</label><input id="rezervasyon-baslik" maxLength={50} value={form.baslik} onChange={(event) => formuGuncelle("baslik", event.target.value)} disabled={gonderimSuruyor} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" placeholder="Örn. Sprint planlama" /></div>
          </div>
          {odaHatasi && <p role="alert" className="text-sm text-error-600 dark:text-error-400">{odaHatasi} <button type="button" onClick={() => void odalariYukle()} className="font-medium underline">Tekrar dene</button></p>}
          {tatilGunSuresi && <p className="rounded-lg bg-warning-50 px-4 py-3 text-sm text-warning-700 dark:bg-warning-500/10 dark:text-warning-400">Seçilen gün {tatilGunSuresi === "TAM_GUN" ? "tam gün tatildir; rezervasyon oluşturulamaz." : "yarım gün tatildir; rezervasyon en geç 13:00'te bitmelidir."}</p>}
          {tatilUyarisi && <p className="text-sm text-warning-600 dark:text-warning-400">{tatilUyarisi}</p>}
          {formHatasi && <p role="alert" className="text-sm text-error-600 dark:text-error-400">{formHatasi}</p>}
          <button type="submit" disabled={gonderimSuruyor || odalarYukleniyor || odalar.length === 0} className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50">{gonderimSuruyor ? "Oluşturuluyor…" : "Rezervasyon oluştur"}</button>
        </form>
      </section>

      <section className="rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]"><div className="flex flex-col gap-4 border-b border-gray-100 px-5 py-5 dark:border-gray-800 lg:flex-row lg:items-center lg:justify-between sm:px-6"><div><h3 className="text-base font-semibold text-gray-800 dark:text-white/90">Günlük oda doluluğu</h3><p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Boş ve bitişik saatleri seçerek formu doldurun; dolu saatler ayrıntıları açar.</p></div><div className="flex flex-wrap items-center justify-end gap-2"><button type="button" onClick={() => seciliTarihiKaydir(-1)} className="h-11 w-11 rounded-lg border border-gray-300 bg-white text-lg leading-none text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-3 focus:ring-brand-500/10 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-300" aria-label="Önceki gün">‹</button><input type="date" value={seciliTarih} onChange={(event) => seciliTarihiDegistir(event.target.value)} className="h-11 w-44 rounded-lg border border-gray-300 bg-transparent px-3 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" aria-label="Doluluk tarihi" /><button type="button" onClick={() => seciliTarihiKaydir(1)} className="h-11 w-11 rounded-lg border border-gray-300 bg-white text-lg leading-none text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-3 focus:ring-brand-500/10 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-300" aria-label="Sonraki gün">›</button><button type="button" onClick={() => seciliTarihiDegistir(bugun)} className="h-11 rounded-lg border border-gray-300 bg-white px-4 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-3 focus:ring-brand-500/10 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-300">Bugün</button></div></div>{gridSecimi && <p className="mx-5 mt-5 rounded-lg bg-success-50 px-4 py-3 text-sm text-success-700 dark:bg-success-500/10 dark:text-success-400">Seçim: {saatiBicimlendir(gridSecimi.ilkSaat)}–{saatiBicimlendir(gridSecimi.sonSaat + 1)}. Rezervasyon formu güncellendi.</p>}{gridTatilGunSuresi && <div className="mx-5 mt-5 rounded-xl border border-warning-200 bg-warning-50 px-4 py-3 text-sm text-warning-700 dark:border-warning-500/20 dark:bg-warning-500/10 dark:text-warning-400">{gridTatilGunSuresi === "TAM_GUN" ? "Bu gün tam gün tatildir; seçim kapalıdır." : "Bu gün yarım gün tatildir; yalnız 08:00–13:00 arası seçilebilir."}</div>}{gridTatilHatasi && <p className="mx-5 mt-5 text-sm text-warning-600 dark:text-warning-400">{gridTatilHatasi}</p>}{takvimHatasi && <div role="alert" className="mx-5 mt-5 rounded-xl border border-error-200 bg-error-50 px-4 py-3 text-sm text-error-700 dark:border-error-500/20 dark:bg-error-500/10 dark:text-error-400">{takvimHatasi} <button type="button" onClick={() => void takvimiYukle(seciliTarih)} className="font-medium underline">Tekrar dene</button></div>}{takvimYukleniyor && <p role="status" className="px-5 pt-5 text-sm text-gray-500 dark:text-gray-400">Doluluk bilgisi yükleniyor…</p>}{rezervasyonlar?.length === 0 && !takvimYukleniyor && !takvimHatasi && <p className="px-5 pt-5 text-sm text-gray-500 dark:text-gray-400">Bu gün için aktif rezervasyon yok. Boş saatleri seçerek yeni rezervasyon başlatabilirsiniz.</p>}<GunlukOdaDolulukGrid satirlar={gridSatirlari} secim={gridSecimi} onHucreTikla={gridHucresineTikla} /></section>

      <form
        onSubmit={filtreyiUygula}
        className="rounded-xl border border-gray-200 bg-white p-5 dark:border-white/[0.05] dark:bg-white/[0.03]"
      >
        <div className={`grid grid-cols-1 gap-4 ${gecmisKapsami === "SIRKET" ? "md:grid-cols-2 xl:grid-cols-5" : "md:grid-cols-2 xl:grid-cols-4"}`}>
          <div><label htmlFor="rezervasyon-durum-filtresi" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Durum</label><select id="rezervasyon-durum-filtresi" value={filtreFormu.durum} disabled={gecmisYukleniyor} onChange={(event) => setFiltreFormu((onceki) => ({ ...onceki, durum: event.target.value as GecmisFiltreleri["durum"] }))} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"><option value="">Tüm durumlar</option><option value="AKTIF">Aktif</option><option value="IPTAL_EDILDI">İptal edildi</option></select></div>
          <div><label htmlFor="rezervasyon-oda-filtresi" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Oda</label><select id="rezervasyon-oda-filtresi" value={filtreFormu.odaId} disabled={gecmisYukleniyor} onChange={(event) => setFiltreFormu((onceki) => ({ ...onceki, odaId: event.target.value }))} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"><option value="">Tüm odalar</option>{odalar.map((oda) => <option key={oda.id} value={oda.id}>{oda.ad}</option>)}</select></div>
          <div><label htmlFor="rezervasyon-baslangic-filtresi" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Başlangıç tarihi</label><input id="rezervasyon-baslangic-filtresi" type="date" value={filtreFormu.baslangic} disabled={gecmisYukleniyor} onChange={(event) => setFiltreFormu((onceki) => ({ ...onceki, baslangic: event.target.value }))} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" /></div>
          <div><label htmlFor="rezervasyon-bitis-filtresi" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Bitiş tarihi</label><input id="rezervasyon-bitis-filtresi" type="date" value={filtreFormu.bitis} disabled={gecmisYukleniyor} onChange={(event) => setFiltreFormu((onceki) => ({ ...onceki, bitis: event.target.value }))} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" /></div>
          {gecmisKapsami === "SIRKET" && <div><label htmlFor="rezervasyon-olusturan-filtresi" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Oluşturan UUID</label><input id="rezervasyon-olusturan-filtresi" value={filtreFormu.olusturanId} disabled={gecmisYukleniyor} onChange={(event) => setFiltreFormu((onceki) => ({ ...onceki, olusturanId: event.target.value }))} placeholder="Oluşturan UUID" className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" /></div>}
        </div>
        <div className="mt-5 flex flex-wrap gap-3"><button type="submit" disabled={gecmisYukleniyor} className="rounded-lg bg-brand-500 px-5 py-3 text-sm font-medium text-white hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50">Filtrele</button><button type="button" disabled={gecmisYukleniyor} onClick={filtreyiTemizle} className="rounded-lg border border-gray-300 bg-white px-5 py-3 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300">Temizle</button></div>
      </form>

      <section className="rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]">
        <div className="flex flex-col gap-4 border-b border-gray-100 px-5 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-6 dark:border-gray-800">
          <div>
            <h3 className="text-base font-semibold text-gray-800 dark:text-white/90">{gecmisKapsami === "SIRKET" ? "Şirket rezervasyon geçmişi" : "Rezervasyon geçmişim"}</h3>
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">{gecmisKapsami === "SIRKET" ? "Şirket genelindeki aktif ve iptal edilmiş rezervasyonları değerlendirin." : "Aktif, tamamlanmış ve iptal edilmiş rezervasyonlarınızı görüntüleyin."}</p>
            {adminMi && <div className="mt-4 flex flex-wrap gap-2"><KapsamButonu aktif={gecmisKapsami === "KENDI"} onClick={() => kapsamiDegistir("KENDI")}>Rezervasyonlarım</KapsamButonu><KapsamButonu aktif={gecmisKapsami === "SIRKET"} onClick={() => kapsamiDegistir("SIRKET")}>Şirket geçmişi</KapsamButonu></div>}
          </div>
        </div>
        {gecmisHatasi ? <div role="alert" className="p-6 text-sm text-error-600 dark:text-error-400"><p>{gecmisHatasi}</p><button type="button" onClick={() => void gecmisiYukle(gecmisKapsami, uygulananFiltre, gecmisSayfasi)} className="mt-3 font-medium underline">Tekrar dene</button></div> : gecmisYukleniyor && gecmis === null ? <p role="status" className="p-6 text-sm text-gray-500 dark:text-gray-400">Rezervasyon geçmişi yükleniyor…</p> : gecmis?.icerik.length === 0 ? <p className="p-6 text-sm text-gray-500 dark:text-gray-400">Bu filtreyle eşleşen rezervasyon bulunmuyor.</p> : <GecmisTablosu gecmis={gecmis} kapsam={gecmisKapsami} iptalEdilenId={iptalEdilenId} onKendiIptal={kendiRezervasyonunuIptalEt} onAdminIptal={(rezervasyon) => { setAdminIptalTalebi(rezervasyon); setIptalNedeni(""); setIptalHatasi(null); }} onNotuGor={setNotuGoster} />}
        {gecmis && <Sayfalama gecmis={gecmis} yukleniyor={gecmisYukleniyor} onOnceki={() => setGecmisSayfasi((sayfa) => Math.max(0, sayfa - 1))} onSonraki={() => setGecmisSayfasi((sayfa) => sayfa + 1)} />}
      </section>
    </div>
    <Modal isOpen={seciliRezervasyon !== null} onClose={() => setSeciliRezervasyon(null)} className="max-w-lg p-6 sm:p-8"><h3 className="text-lg font-semibold text-gray-800 dark:text-white/90">Rezervasyon ayrıntısı</h3>{seciliRezervasyon && <div className="mt-5 space-y-3 text-sm text-gray-600 dark:text-gray-300"><p><span className="font-medium text-gray-800 dark:text-white/90">Oda:</span> {seciliRezervasyon.oda.ad}</p><p><span className="font-medium text-gray-800 dark:text-white/90">Tarih:</span> {takvimTarihiniBicimlendir(seciliRezervasyon.tarih)}</p><p><span className="font-medium text-gray-800 dark:text-white/90">Saat:</span> {saatiBicimlendir(seciliRezervasyon.baslangicSaat)}–{saatiBicimlendir(seciliRezervasyon.bitisSaat)}</p><p><span className="font-medium text-gray-800 dark:text-white/90">Oluşturan:</span> {rezervasyonOlusturanAdi(seciliRezervasyon)}</p>{seciliRezervasyon.baslik !== null && <p><span className="font-medium text-gray-800 dark:text-white/90">Başlık:</span> {seciliRezervasyon.baslik}</p>}{seciliRezervasyon.katilimciSayisi !== null && <p><span className="font-medium text-gray-800 dark:text-white/90">Katılımcı:</span> {seciliRezervasyon.katilimciSayisi}</p>}</div>}<div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">{seciliRezervasyon && (seciliRezervasyon.benMiOlusturdum || adminMi) && <button type="button" disabled={iptalEdilenId !== null} onClick={seciliRezervasyonuIptalEt} className="rounded-lg border border-error-300 px-4 py-2.5 text-sm font-medium text-error-600 hover:bg-error-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-error-500/30 dark:text-error-400">{iptalEdilenId ? "İptal ediliyor…" : "İptal et"}</button>}<button type="button" onClick={() => setSeciliRezervasyon(null)} className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600">Kapat</button></div></Modal>
    <Modal isOpen={adminIptalTalebi !== null} onClose={() => { if (!iptalEdilenId) setAdminIptalTalebi(null); }} className="max-w-lg p-6 sm:p-8"><h3 className="text-lg font-semibold text-gray-800 dark:text-white/90">Rezervasyonu iptal et</h3><p className="mt-2 text-sm text-gray-500 dark:text-gray-400">Başkasının rezervasyonunu iptal etmek için neden girin. Backend, yetkiye göre bu nedeni zorunlu tutar.</p><form onSubmit={adminIptaliniKaydet} className="mt-6"><label htmlFor="rezervasyon-iptal-nedeni" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">İptal nedeni</label><textarea id="rezervasyon-iptal-nedeni" value={iptalNedeni} onChange={(event) => { setIptalNedeni(event.target.value); setIptalHatasi(null); }} maxLength={500} rows={5} disabled={iptalEdilenId !== null} aria-describedby="rezervasyon-iptal-nedeni-sayaci" className="w-full rounded-lg border border-gray-300 bg-transparent px-3 py-2.5 text-sm text-gray-800 outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" placeholder="İptal nedenini yazın" /><div id="rezervasyon-iptal-nedeni-sayaci" className="mt-1 flex justify-between text-xs text-gray-500 dark:text-gray-400"><span>Zorunlu, en fazla 500 karakter</span><span>{iptalNedeni.length}/500</span></div>{iptalHatasi && <p role="alert" className="mt-3 text-sm text-error-600 dark:text-error-400">{iptalHatasi}</p>}<div className="mt-5 flex justify-end gap-3"><button type="button" disabled={iptalEdilenId !== null} onClick={() => setAdminIptalTalebi(null)} className="rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300">Vazgeç</button><button type="submit" disabled={iptalEdilenId !== null} className="rounded-lg bg-error-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-error-600 disabled:cursor-not-allowed disabled:opacity-50">{iptalEdilenId ? "İptal ediliyor…" : "İptal et"}</button></div></form></Modal>
    <IzinNotuModali isOpen={notuGoster !== null} personelAdi="" baslik={notuGoster?.baslik} aciklama={notuGoster?.aciklama} not={notuGoster?.not ?? ""} onKapat={() => setNotuGoster(null)} />
  </>;
}

function KapsamButonu({ aktif, onClick, children }: { aktif: boolean; onClick: () => void; children: React.ReactNode }) { return <button type="button" onClick={onClick} className={`rounded-lg px-3.5 py-2 text-sm font-medium ${aktif ? "bg-brand-50 text-brand-600 dark:bg-brand-500/15 dark:text-brand-400" : "text-gray-500 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-white/[0.03]"}`}>{children}</button>; }

function GecmisTablosu({ gecmis, kapsam, iptalEdilenId, onKendiIptal, onAdminIptal, onNotuGor }: { gecmis: RezervasyonGecmisiSayfasi | null; kapsam: "KENDI" | "SIRKET"; iptalEdilenId: string | null; onKendiIptal: (rezervasyon: RezervasyonGecmisi) => void; onAdminIptal: (rezervasyon: RezervasyonGecmisi) => void; onNotuGor: (not: { baslik: string; aciklama: string; not: string }) => void }) {
  const kayitlar = gecmis?.icerik ?? [];
  return <div className="max-w-full overflow-x-auto"><Table><TableHeader className="border-b border-gray-100 dark:border-white/[0.05]"><TableRow>{kapsam === "SIRKET" && <TableCell isHeader className="px-5 py-3 text-start text-xs font-medium text-gray-500 dark:text-gray-400">Oluşturan</TableCell>}<TableCell isHeader className="px-5 py-3 text-start text-xs font-medium text-gray-500 dark:text-gray-400">Oda</TableCell><TableCell isHeader className="px-5 py-3 text-start text-xs font-medium text-gray-500 dark:text-gray-400">Tarih / saat</TableCell><TableCell isHeader className="px-5 py-3 text-start text-xs font-medium text-gray-500 dark:text-gray-400">Ayrıntı</TableCell><TableCell isHeader className="px-5 py-3 text-start text-xs font-medium text-gray-500 dark:text-gray-400">Durum</TableCell><TableCell isHeader className="px-5 py-3 text-end text-xs font-medium text-gray-500 dark:text-gray-400">İşlem</TableCell></TableRow></TableHeader><TableBody className="divide-y divide-gray-100 dark:divide-white/[0.05]">{kayitlar.map((rezervasyon) => { const durum = durumRozeti(rezervasyon.durum); const iptalEdilebilir = rezervasyon.durum === "AKTIF" && !rezervasyon.gecmisMi; return <TableRow key={rezervasyon.id}>{kapsam === "SIRKET" && <TableCell className="px-5 py-4 text-sm text-gray-700 dark:text-gray-300">{rezervasyonOlusturanAdi(rezervasyon)}</TableCell>}<TableCell className="px-5 py-4 text-sm text-gray-700 dark:text-gray-300"><p className="font-medium">{rezervasyon.oda.ad}</p><p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{rezervasyon.oda.kapasite} kişilik</p></TableCell><TableCell className="px-5 py-4 text-sm text-gray-600 dark:text-gray-300"><p>{takvimTarihiniBicimlendir(rezervasyon.tarih)}</p><p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{saatiBicimlendir(rezervasyon.baslangicSaat)}–{saatiBicimlendir(rezervasyon.bitisSaat)}</p></TableCell><TableCell className="px-5 py-4 text-sm text-gray-600 dark:text-gray-300">{rezervasyon.baslik !== null ? <><p>{rezervasyon.baslik}</p>{rezervasyon.katilimciSayisi !== null && <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{rezervasyon.katilimciSayisi} katılımcı</p>}</> : <span className="text-xs text-gray-400 dark:text-gray-500">Ayrıntı bulunmuyor</span>}</TableCell><TableCell className="px-5 py-4"><Badge size="sm" color={durum.renk}>{durum.metin}</Badge>{rezervasyon.durum === "IPTAL_EDILDI" && <div className="mt-2 space-y-1 text-xs text-gray-500 dark:text-gray-400">{rezervasyon.iptalNedeni && <button type="button" onClick={() => onNotuGor({ baslik: "İptal nedeni", aciklama: `Rezervasyon iptal edildi: ${tarihSaatiniBicimlendir(rezervasyon.iptalZamani)}.`, not: rezervasyon.iptalNedeni ?? "" })} className="block font-medium text-error-600 hover:underline dark:text-error-400">İptal nedenini gör</button>}{rezervasyon.iptalEden && <p>İptal eden: {rezervasyonOlusturanAdi({ olusturan: rezervasyon.iptalEden })}</p>}</div>}</TableCell><TableCell className="px-5 py-4 text-end">{iptalEdilebilir ? <button type="button" disabled={iptalEdilenId !== null} onClick={() => kapsam === "SIRKET" ? onAdminIptal(rezervasyon) : onKendiIptal(rezervasyon)} className="rounded-lg border border-error-300 px-3 py-2 text-xs font-medium text-error-600 hover:bg-error-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-error-500/30 dark:text-error-400">{iptalEdilenId === rezervasyon.id ? "İptal ediliyor…" : "İptal et"}</button> : <span className="text-xs text-gray-400 dark:text-gray-500">İşlem yok</span>}</TableCell></TableRow>; })}</TableBody></Table></div>;
}

function Sayfalama({ gecmis, yukleniyor, onOnceki, onSonraki }: { gecmis: RezervasyonGecmisiSayfasi; yukleniyor: boolean; onOnceki: () => void; onSonraki: () => void }) { return <div className="flex flex-col gap-3 border-t border-gray-100 px-5 py-4 text-sm text-gray-500 dark:border-gray-800 dark:text-gray-400 sm:flex-row sm:items-center sm:justify-between"><span>Toplam {gecmis.toplamKayit} kayıt · Sayfa {gecmis.sayfa + 1}/{Math.max(gecmis.toplamSayfa, 1)}</span><div className="flex gap-3"><button type="button" disabled={yukleniyor || gecmis.sayfa === 0} onClick={onOnceki} className="rounded-lg border border-gray-300 px-3 py-2 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700">Önceki</button><button type="button" disabled={yukleniyor || gecmis.sayfa + 1 >= gecmis.toplamSayfa} onClick={onSonraki} className="rounded-lg border border-gray-300 px-3 py-2 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700">Sonraki</button></div></div>; }

type GridHucresi = {
  baslangicSaat: number;
  durum: string;
  rezervasyon: Rezervasyon | null;
};

type GridSatiri = {
  oda: Oda;
  hucreler: GridHucresi[];
};

function GunlukOdaDolulukGrid({ satirlar, secim, onHucreTikla }: { satirlar: GridSatiri[]; secim: GridSecimi | null; onHucreTikla: (odaId: number, baslangicSaat: number, durum: string, rezervasyon: Rezervasyon | null, hucreler: GridHucresi[]) => void }) {
  return <div className="max-w-full overflow-x-auto p-5 sm:p-6"><div className="min-w-[1080px] overflow-hidden rounded-xl border border-gray-100 dark:border-gray-800"><div className="grid grid-cols-[170px_repeat(10,minmax(88px,1fr))] border-b border-gray-100 bg-gray-50 text-xs font-medium text-gray-500 dark:border-gray-800 dark:bg-white/[0.03] dark:text-gray-400"><div className="px-4 py-3">Oda</div>{Array.from({ length: 10 }, (_, index) => index + 8).map((saat) => <div key={saat} className="border-l border-gray-100 px-2 py-3 text-center dark:border-gray-800">{saatAraliginiBicimlendir(saat)}</div>)}</div>{satirlar.map((satir) => <div key={satir.oda.id} className="grid grid-cols-[170px_repeat(10,minmax(88px,1fr))] border-b border-gray-100 last:border-b-0 dark:border-gray-800"><div className="flex min-h-20 flex-col justify-center px-4"><p className="text-sm font-medium text-gray-800 dark:text-white/90">{satir.oda.ad}</p><p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{satir.oda.kapasite} kişilik</p></div>{satir.hucreler.map((hucre) => { const seciliMi = secim?.odaId === satir.oda.id && hucre.baslangicSaat >= secim.ilkSaat && hucre.baslangicSaat <= secim.sonSaat; const rezervasyonBaslangiciMi = hucre.rezervasyon?.baslangicSaat === hucre.baslangicSaat; const sinif = hucre.durum === "DOLU" ? "bg-brand-100 text-brand-700 hover:bg-brand-200 dark:bg-brand-500/15 dark:text-brand-300 dark:hover:bg-brand-500/25" : hucre.durum === "KAPALI" ? "cursor-not-allowed bg-gray-100 text-gray-400 dark:bg-white/[0.04] dark:text-gray-600" : seciliMi ? "bg-success-100 text-success-700 ring-2 ring-inset ring-success-500 dark:bg-success-500/15 dark:text-success-400" : "bg-white text-gray-600 hover:bg-brand-50 dark:bg-transparent dark:text-gray-300 dark:hover:bg-brand-500/10"; const etiket = hucre.durum === "DOLU" ? hucre.rezervasyon?.baslik ?? "Dolu" : hucre.durum === "KAPALI" ? "Kapalı" : seciliMi ? "Seçili" : "Müsait"; return <button key={hucre.baslangicSaat} type="button" disabled={hucre.durum === "KAPALI"} onClick={() => onHucreTikla(satir.oda.id, hucre.baslangicSaat, hucre.durum, hucre.rezervasyon, satir.hucreler)} aria-label={`${satir.oda.ad}, ${saatAraliginiBicimlendir(hucre.baslangicSaat)}: ${etiket}`} aria-pressed={seciliMi} className={`min-h-20 border-l border-gray-100 p-1.5 text-left text-xs transition-colors disabled:opacity-80 dark:border-gray-800 ${sinif}`}>{hucre.durum === "DOLU" && rezervasyonBaslangiciMi && <span className="block truncate font-medium">{etiket}</span>}{hucre.durum === "MUSAIT" && seciliMi && <span className="block text-center font-medium">Seçili</span>}</button>; })}</div>)}</div></div>;
}
