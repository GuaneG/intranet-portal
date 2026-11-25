import { useCallback, useEffect, useRef, useState } from "react";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import KatalogKayitDuzenlemeModali from "../components/katalog-yonetimi/KatalogKayitDuzenlemeModali";
import KatalogKayitSilmeOnayi from "../components/katalog-yonetimi/KatalogKayitSilmeOnayi";
import KatalogListesi from "../components/katalog-yonetimi/KatalogListesi";
import {
    ApiError,
    departmanSil,
    departmanGuncelle,
    departmanOlustur,
    departmanlariGetir,
    yetenekGuncelle,
    yetenekSil,
    yetenekOlustur,
    yetenekleriGetir,
} from "../lib/api";
import type {
    KatalogKayit,
    KatalogTuru,
} from "../types/katalogYonetimi";

const KATALOG_SEKMELERI: Array<{
    tur: KatalogTuru;
    etiket: string;
    tekilEtiket: string;
}> = [
    {
        tur: "departman",
        etiket: "Departmanlar",
        tekilEtiket: "Departman",
    },
    {
        tur: "yetenek",
        etiket: "Yetenekler",
        tekilEtiket: "Yetenek",
    },
];

function katalogHataMesaji(error: unknown, varsayilanMesaj: string) {
    if (!(error instanceof ApiError)) {
        return varsayilanMesaj;
    }

    if (error.status === 0) {
        return "Sunucuya ulaşılamadı. Bağlantınızı kontrol edip tekrar deneyin.";
    }

    if (error.status === 403) {
        return "Bu işlem için yetkiniz bulunmuyor.";
    }

    if (error.status >= 500) {
        return varsayilanMesaj;
    }

    return error.message || varsayilanMesaj;
}

function aktifKayitlariGetir(
    tur: KatalogTuru,
    departmanlar: KatalogKayit[],
    yetenekler: KatalogKayit[],
) {
    return tur === "departman" ? departmanlar : yetenekler;
}

/**
 * HR/Admin katalog işlemlerinin istek, sonuç ve hata durumlarını koordine eder.
 */
export default function KatalogYonetimi() {
    const [aktifTur, setAktifTur] = useState<KatalogTuru>("departman");
    const [departmanlar, setDepartmanlar] = useState<KatalogKayit[]>([]);
    const [yetenekler, setYetenekler] = useState<KatalogKayit[]>([]);
    const [yuklenenTur, setYuklenenTur] = useState<KatalogTuru | null>(
        null,
    );
    const [listeHatasi, setListeHatasi] = useState<string | null>(null);
    const [yeniAd, setYeniAd] = useState("");
    const [gonderimSuruyor, setGonderimSuruyor] = useState(false);
    const [eklemeHatasi, setEklemeHatasi] = useState<string | null>(null);
    const [duzenlemeHatasi, setDuzenlemeHatasi] = useState<string | null>(
        null,
    );
    const [basariMesaji, setBasariMesaji] = useState<string | null>(null);
    const [duzenlenenKayit, setDuzenlenenKayit] =
        useState<KatalogKayit | null>(null);
    const [silinecekKayit, setSilinecekKayit] = useState<{
        tur: KatalogTuru;
        kayit: KatalogKayit;
    } | null>(null);
    const [silmeHatasi, setSilmeHatasi] = useState<string | null>(null);
    const aktifTurRef = useRef<KatalogTuru>("departman");
    const istekNumaralari = useRef<Record<KatalogTuru, number>>({
        departman: 0,
        yetenek: 0,
    });
    const gonderimKilidi = useRef(false);

    const aktifSekme = KATALOG_SEKMELERI.find(
        (sekme) => sekme.tur === aktifTur,
    )!;
    const aktifKayitlar = aktifKayitlariGetir(
        aktifTur,
        departmanlar,
        yetenekler,
    );
    const listeYukleniyor = yuklenenTur === aktifTur;
    const trimlenmisYeniAd = yeniAd.trim();

    const katalogYukle = useCallback(async (tur: KatalogTuru) => {
        const istekNumarasi = ++istekNumaralari.current[tur];

        if (aktifTurRef.current === tur) {
            setYuklenenTur(tur);
            setListeHatasi(null);
        }

        try {
            const sonuc =
                tur === "departman"
                    ? await departmanlariGetir()
                    : await yetenekleriGetir();

            if (istekNumaralari.current[tur] !== istekNumarasi) {
                return;
            }

            if (tur === "departman") {
                setDepartmanlar(sonuc);
            } else {
                setYetenekler(sonuc);
            }
        } catch (error) {
            if (
                istekNumaralari.current[tur] !== istekNumarasi ||
                aktifTurRef.current !== tur
            ) {
                return;
            }

            setListeHatasi(
                katalogHataMesaji(
                    error,
                    `${tur === "departman" ? "Departman" : "Yetenek"} listesi alınamadı. Lütfen tekrar deneyin.`,
                ),
            );
        } finally {
            if (
                istekNumaralari.current[tur] === istekNumarasi &&
                aktifTurRef.current === tur
            ) {
                setYuklenenTur(null);
            }
        }
    }, []);

    useEffect(() => {
        void katalogYukle(aktifTur);
    }, [aktifTur, katalogYukle]);

    function sekmeyiDegistir(tur: KatalogTuru) {
        if (gonderimSuruyor || silinecekKayit || tur === aktifTur) {
            return;
        }

        aktifTurRef.current = tur;
        setAktifTur(tur);
        setBasariMesaji(null);
        setEklemeHatasi(null);
        setDuzenlemeHatasi(null);
        setSilmeHatasi(null);
        setYeniAd("");
    }

    async function yeniKaydiEkle(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();

        if (
            !trimlenmisYeniAd ||
            gonderimSuruyor ||
            gonderimKilidi.current
        ) {
            return;
        }

        gonderimKilidi.current = true;
        setGonderimSuruyor(true);
        setEklemeHatasi(null);
        setBasariMesaji(null);

        try {
            if (aktifTur === "departman") {
                await departmanOlustur(trimlenmisYeniAd);
            } else {
                await yetenekOlustur(trimlenmisYeniAd);
            }

            setYeniAd("");
            setBasariMesaji(`${aktifSekme.tekilEtiket} eklendi.`);
            await katalogYukle(aktifTur);
        } catch (error) {
            setEklemeHatasi(
                katalogHataMesaji(
                    error,
                    `${aktifSekme.tekilEtiket} eklenemedi. Lütfen tekrar deneyin.`,
                ),
            );
        } finally {
            gonderimKilidi.current = false;
            setGonderimSuruyor(false);
        }
    }

    function duzenlemeyiAc(kayit: KatalogKayit) {
        if (gonderimSuruyor) {
            return;
        }

        setDuzenlemeHatasi(null);
        setBasariMesaji(null);
        setDuzenlenenKayit(kayit);
    }

    function duzenlemeyiKapat() {
        if (gonderimSuruyor) {
            return;
        }

        setDuzenlemeHatasi(null);
        setDuzenlenenKayit(null);
    }

    async function kaydiGuncelle(ad: string) {
        if (!duzenlenenKayit || gonderimKilidi.current) {
            return;
        }

        gonderimKilidi.current = true;
        setGonderimSuruyor(true);
        setDuzenlemeHatasi(null);
        setBasariMesaji(null);

        try {
            if (aktifTur === "departman") {
                await departmanGuncelle(duzenlenenKayit.id, ad);
            } else {
                await yetenekGuncelle(duzenlenenKayit.id, ad);
            }

            setDuzenlenenKayit(null);
            setBasariMesaji(`${aktifSekme.tekilEtiket} güncellendi.`);
            await katalogYukle(aktifTur);
        } catch (error) {
            setDuzenlemeHatasi(
                katalogHataMesaji(
                    error,
                    `${aktifSekme.tekilEtiket} güncellenemedi. Lütfen tekrar deneyin.`,
                ),
            );
        } finally {
            gonderimKilidi.current = false;
            setGonderimSuruyor(false);
        }
    }

    function silmeyiAc(kayit: KatalogKayit) {
        if (gonderimSuruyor) {
            return;
        }

        setBasariMesaji(null);
        setSilmeHatasi(null);
        setSilinecekKayit({ tur: aktifTur, kayit });
    }

    function silmeyiKapat() {
        if (gonderimSuruyor) {
            return;
        }

        setSilmeHatasi(null);
        setSilinecekKayit(null);
    }

    async function kaydiSil() {
        if (!silinecekKayit || gonderimKilidi.current) {
            return;
        }

        const { tur, kayit } = silinecekKayit;
        const tekilEtiket = KATALOG_SEKMELERI.find(
            (sekme) => sekme.tur === tur,
        )!.tekilEtiket;

        gonderimKilidi.current = true;
        setGonderimSuruyor(true);
        setSilmeHatasi(null);
        setBasariMesaji(null);

        try {
            if (tur === "departman") {
                await departmanSil(kayit.id);
            } else {
                await yetenekSil(kayit.id);
            }

            setSilinecekKayit(null);
            setBasariMesaji(`${tekilEtiket} silindi.`);
            await katalogYukle(tur);
        } catch (error) {
            setSilmeHatasi(
                katalogHataMesaji(
                    error,
                    `${tekilEtiket} silinemedi. Lütfen tekrar deneyin.`,
                ),
            );
        } finally {
            gonderimKilidi.current = false;
            setGonderimSuruyor(false);
        }
    }

    return (
        <>
            <PageMeta
                title="Katalog Yönetimi | Intranet Portal"
                description="Departman ve yetenek kataloglarını yönetme ekranı"
            />
            <PageBreadcrumb pageTitle="Katalog Yönetimi" />

            <div className="space-y-6">
                <section className="rounded-xl border border-gray-200 bg-white p-5 dark:border-white/[0.05] dark:bg-white/[0.03]">
                    <div>
                        <h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">
                            Katalog Yönetimi
                        </h2>
                        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                            Departman ve yetenek tanımlarını ekleyebilir veya
                            yeniden adlandırabilir, kullanılmayan kayıtları
                            silebilirsiniz.
                        </p>
                    </div>

                    <div
                        role="tablist"
                        aria-label="Katalog türü"
                        className="mt-5 flex flex-wrap gap-2"
                    >
                        {KATALOG_SEKMELERI.map((sekme) => (
                            <button
                                key={sekme.tur}
                                id={`${sekme.tur}-sekmesi`}
                                type="button"
                                role="tab"
                                aria-selected={aktifTur === sekme.tur}
                                aria-controls={`${sekme.tur}-paneli`}
                                disabled={gonderimSuruyor || silinecekKayit !== null}
                                onClick={() => sekmeyiDegistir(sekme.tur)}
                                className={`rounded-lg px-4 py-2.5 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50 ${
                                    aktifTur === sekme.tur
                                        ? "bg-brand-500 text-white"
                                        : "border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:bg-transparent dark:text-gray-300 dark:hover:bg-white/[0.03]"
                                }`}
                            >
                                {sekme.etiket}
                            </button>
                        ))}
                    </div>

                    <form
                        onSubmit={yeniKaydiEkle}
                        className="mt-5 flex flex-col gap-3 sm:flex-row"
                    >
                        <div className="min-w-0 flex-1">
                            <label
                                htmlFor="katalog-yeni-kayit"
                                className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300"
                            >
                                Yeni {aktifSekme.tekilEtiket.toLocaleLowerCase("tr-TR")}
                            </label>
                            <input
                                id="katalog-yeni-kayit"
                                value={yeniAd}
                                disabled={gonderimSuruyor}
                                onChange={(event) => setYeniAd(event.target.value)}
                                placeholder={`${aktifSekme.tekilEtiket} adı`}
                                className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"
                            />
                        </div>
                        <button
                            type="submit"
                            disabled={gonderimSuruyor || !trimlenmisYeniAd}
                            className="mt-6 h-11 rounded-lg bg-brand-500 px-5 text-sm font-medium text-white hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            {gonderimSuruyor ? "Kaydediliyor…" : "Ekle"}
                        </button>
                    </form>

                    {eklemeHatasi && (
                        <p role="alert" className="mt-4 text-sm text-error-500">
                            {eklemeHatasi}
                        </p>
                    )}
                </section>

                {basariMesaji && (
                    <div
                        role="status"
                        aria-live="polite"
                        className="rounded-xl border border-success-500/30 bg-success-50 p-4 text-sm text-success-600 dark:bg-success-500/15 dark:text-success-500"
                    >
                        {basariMesaji}
                    </div>
                )}

                <section
                    id={`${aktifTur}-paneli`}
                    role="tabpanel"
                    aria-labelledby={`${aktifTur}-sekmesi`}
                >
                    {listeHatasi ? (
                        <div
                            role="alert"
                            className="rounded-xl border border-error-200 bg-error-50 p-5 text-sm text-error-700 dark:border-error-500/20 dark:bg-error-500/10 dark:text-error-400"
                        >
                            <p>{listeHatasi}</p>
                            <button
                                type="button"
                                disabled={listeYukleniyor || gonderimSuruyor}
                                onClick={() => void katalogYukle(aktifTur)}
                                className="mt-3 font-medium underline disabled:cursor-not-allowed disabled:opacity-50"
                            >
                                Tekrar dene
                            </button>
                        </div>
                    ) : (
                <KatalogListesi
                    tur={aktifTur}
                    kayitlar={aktifKayitlar}
                    yukleniyor={listeYukleniyor}
                    islemSuruyor={gonderimSuruyor}
                    onDuzenle={duzenlemeyiAc}
                    onSil={silmeyiAc}
                />
                    )}
                </section>
            </div>

                <KatalogKayitDuzenlemeModali
                tur={aktifTur}
                kayit={duzenlenenKayit}
                gonderimSuruyor={gonderimSuruyor}
                hata={duzenlemeHatasi}
                onKapat={duzenlemeyiKapat}
                onKaydet={kaydiGuncelle}
                />

                <KatalogKayitSilmeOnayi
                    kayit={silinecekKayit?.kayit ?? null}
                    tur={silinecekKayit?.tur ?? null}
                    gonderimSuruyor={gonderimSuruyor}
                    hata={silmeHatasi}
                    onKapat={silmeyiKapat}
                    onOnayla={() => void kaydiSil()}
                />
        </>
    );
}
