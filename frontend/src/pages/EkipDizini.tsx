import {
    type FormEvent,
    useCallback,
    useEffect,
    useRef,
    useState,
} from "react";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import EkipDiziniTablosu from "../components/ekip-dizini/EkipDiziniTablosu";
import { useAuth } from "../context/AuthContext";
import {
    ApiError,
    departmanlariGetir,
    ekipDizininiGetir,
    ekipHiyerarsisiniGetir,
    yetenekleriGetir,
    yoneticiHiyerarsimiGetir,
    yoneticiZincirimiGetir,
} from "../lib/api";
import { yoneticiZinciriniAgacaDonustur } from "../lib/yoneticiHiyerarsisi";
import {
    departmanFiltresiSecilebilirMi,
    hiyerarsiTablosuGosterilmeli,
} from "../lib/ekipDiziniGorunurluk";
import type {
    EkipDiziniFiltreleri,
    EkipDiziniSayfasi,
    HiyerarsiResponse,
    YoneticiHiyerarsisiResponse,
    YoneticiZinciriResponse,
} from "../types/ekipDizini";
import type { LookupSecenegi } from "../types/personelYonetim";

type FormFiltreleri = {
    arama: string;
    departmanId: string;
    yetenekId: string;
};

const SAYFA_BOYUTU = 12;
const BASLANGIC_FILTRELERI: FormFiltreleri = {
    arama: "",
    departmanId: "",
    yetenekId: "",
};

function apiFiltreleriniOlustur(
    filtreler: FormFiltreleri,
    page: number,
): EkipDiziniFiltreleri {
    return {
        page,
        size: SAYFA_BOYUTU,
        arama: filtreler.arama.trim() || undefined,
        departmanId: filtreler.departmanId
            ? Number(filtreler.departmanId)
            : undefined,
        yetenekId: filtreler.yetenekId
            ? Number(filtreler.yetenekId)
            : undefined,
    };
}

function hiyerarsiFiltreleriniOlustur(
    filtreler: FormFiltreleri,
): Omit<EkipDiziniFiltreleri, "page" | "size"> {
    return {
        arama: filtreler.arama.trim() || undefined,
        departmanId: filtreler.departmanId
            ? Number(filtreler.departmanId)
            : undefined,
        yetenekId: filtreler.yetenekId
            ? Number(filtreler.yetenekId)
            : undefined,
    };
}

function hataMesajiniGetir(error: unknown, fallback: string) {
    return error instanceof ApiError && error.message
        ? error.message
        : fallback;
}

function filtreUygulanmisMi(filtreler: FormFiltreleri) {
    return (
        filtreler.arama.trim() !== "" ||
        filtreler.departmanId !== "" ||
        filtreler.yetenekId !== ""
    );
}

/**
 * Tüm authenticated roller için ekip arkadaşlarını arama ve filtreleme ekranı.
 */
export default function EkipDizini() {
    const { kullanici } = useAuth();
    const [taslakFiltreler, setTaslakFiltreler] =
        useState<FormFiltreleri>({ ...BASLANGIC_FILTRELERI });
    const [uygulananFiltreler, setUygulananFiltreler] =
        useState<FormFiltreleri>({ ...BASLANGIC_FILTRELERI });
    const [aktifSayfa, setAktifSayfa] = useState(0);
    const [sayfaVerisi, setSayfaVerisi] =
        useState<EkipDiziniSayfasi | null>(null);
    const [listeYukleniyor, setListeYukleniyor] = useState(false);
    const [listeHatasi, setListeHatasi] = useState<string | null>(null);

    const [departmanlar, setDepartmanlar] = useState<LookupSecenegi[]>([]);
    const [departmanlarYukleniyor, setDepartmanlarYukleniyor] =
        useState(false);
    const [departmanHatasi, setDepartmanHatasi] = useState<string | null>(
        null,
    );
    const [yetenekler, setYetenekler] = useState<LookupSecenegi[]>([]);
    const [yeteneklerYukleniyor, setYeteneklerYukleniyor] = useState(false);
    const [yetenekHatasi, setYetenekHatasi] = useState<string | null>(null);

    const [hiyerarsi, setHiyerarsi] = useState<HiyerarsiResponse | null>(
        null,
    );
    const [hiyerarsiYukleniyor, setHiyerarsiYukleniyor] = useState(false);
    const [hiyerarsiHatasi, setHiyerarsiHatasi] = useState<string | null>(
        null,
    );
    const [yoneticiDepartmani, setYoneticiDepartmani] =
        useState<LookupSecenegi | null>(null);
    const [yoneticiZinciri, setYoneticiZinciri] =
        useState<YoneticiZinciriResponse | null>(null);
    const [zincirYukleniyor, setZincirYukleniyor] = useState(false);
    const [zincirHatasi, setZincirHatasi] = useState<string | null>(null);

    const sonListeIstegi = useRef(0);
    const sonDepartmanIstegi = useRef(0);
    const sonYetenekIstegi = useRef(0);
    const sonHiyerarsiIstegi = useRef(0);
    const sonZincirIstegi = useRef(0);

    const yoneticiHiyerarsisiGorunur = kullanici?.rol === "YONETICI";
    const tamHiyerarsiGorunur =
        kullanici?.rol === "HR" || kullanici?.rol === "ADMIN";
    const personelZinciriGorunur = kullanici?.rol === "PERSONEL";
    const hiyerarsiGorunur =
        yoneticiHiyerarsisiGorunur || tamHiyerarsiGorunur;
    const gosterilenHiyerarsi = hiyerarsiGorunur
        ? hiyerarsi
        : yoneticiZinciri
          ? { kokler: yoneticiZinciriniAgacaDonustur(yoneticiZinciri.yoneticiler) }
          : null;
    const hiyerarsiYuklemeAktif = hiyerarsiGorunur
        ? hiyerarsiYukleniyor
        : zincirYukleniyor;
    const hiyerarsiHatasiAktif = hiyerarsiGorunur
        ? hiyerarsiHatasi
        : zincirHatasi;

    const diziniYukle = useCallback(
        async (filtreler: FormFiltreleri, page: number) => {
            const istekNumarasi = ++sonListeIstegi.current;

            setListeYukleniyor(true);
            setListeHatasi(null);
            setAktifSayfa(page);

            try {
                const sonuc = await ekipDizininiGetir(
                    apiFiltreleriniOlustur(filtreler, page),
                );

                if (istekNumarasi !== sonListeIstegi.current) {
                    return;
                }

                setSayfaVerisi(sonuc);
                setAktifSayfa(sonuc.sayfa);
            } catch (error) {
                if (istekNumarasi !== sonListeIstegi.current) {
                    return;
                }

                setSayfaVerisi(null);
                setListeHatasi(
                    hataMesajiniGetir(
                        error,
                        "Ekip dizini alınamadı. Lütfen tekrar deneyin.",
                    ),
                );
            } finally {
                if (istekNumarasi === sonListeIstegi.current) {
                    setListeYukleniyor(false);
                }
            }
        },
        [],
    );

    const departmanlariYukle = useCallback(async () => {
        const istekNumarasi = ++sonDepartmanIstegi.current;

        setDepartmanlarYukleniyor(true);
        setDepartmanHatasi(null);

        try {
            const sonuc = await departmanlariGetir();

            if (istekNumarasi === sonDepartmanIstegi.current) {
                setDepartmanlar(sonuc);
            }
        } catch (error) {
            if (istekNumarasi === sonDepartmanIstegi.current) {
                setDepartmanlar([]);
                setDepartmanHatasi(
                    hataMesajiniGetir(
                        error,
                        "Departman seçenekleri alınamadı.",
                    ),
                );
            }
        } finally {
            if (istekNumarasi === sonDepartmanIstegi.current) {
                setDepartmanlarYukleniyor(false);
            }
        }
    }, []);

    const yetenekleriYukle = useCallback(async () => {
        const istekNumarasi = ++sonYetenekIstegi.current;

        setYeteneklerYukleniyor(true);
        setYetenekHatasi(null);

        try {
            const sonuc = await yetenekleriGetir();

            if (istekNumarasi === sonYetenekIstegi.current) {
                setYetenekler(sonuc);
            }
        } catch (error) {
            if (istekNumarasi === sonYetenekIstegi.current) {
                setYetenekler([]);
                setYetenekHatasi(
                    hataMesajiniGetir(
                        error,
                        "Yetenek seçenekleri alınamadı.",
                    ),
                );
            }
        } finally {
            if (istekNumarasi === sonYetenekIstegi.current) {
                setYeteneklerYukleniyor(false);
            }
        }
    }, []);

    const hiyerarsiyiYukle = useCallback(async (filtreler: FormFiltreleri) => {
        const istekNumarasi = ++sonHiyerarsiIstegi.current;

        setHiyerarsiYukleniyor(true);
        setHiyerarsiHatasi(null);

        try {
            const hiyerarsiFiltreleri = hiyerarsiFiltreleriniOlustur(filtreler);
            const sonuc: HiyerarsiResponse | YoneticiHiyerarsisiResponse =
                yoneticiHiyerarsisiGorunur
                    ? await yoneticiHiyerarsimiGetir(hiyerarsiFiltreleri)
                    : await ekipHiyerarsisiniGetir(hiyerarsiFiltreleri);

            if (istekNumarasi === sonHiyerarsiIstegi.current) {
                setHiyerarsi(sonuc);
                if (yoneticiHiyerarsisiGorunur) {
                    setYoneticiDepartmani(
                        (sonuc as YoneticiHiyerarsisiResponse).aktorDepartman,
                    );
                }
            }
        } catch (error) {
            if (istekNumarasi === sonHiyerarsiIstegi.current) {
                setHiyerarsi(null);
                setHiyerarsiHatasi(
                    hataMesajiniGetir(
                        error,
                        "Yönetici hiyerarşisi alınamadı. Lütfen tekrar deneyin.",
                    ),
                );
            }
        } finally {
            if (istekNumarasi === sonHiyerarsiIstegi.current) {
                setHiyerarsiYukleniyor(false);
            }
        }
    }, [yoneticiHiyerarsisiGorunur]);

    const yoneticiZinciriniYukle = useCallback(async () => {
        const istekNumarasi = ++sonZincirIstegi.current;

        setZincirYukleniyor(true);
        setZincirHatasi(null);

        try {
            const sonuc = await yoneticiZincirimiGetir();

            if (istekNumarasi === sonZincirIstegi.current) {
                setYoneticiZinciri(sonuc);
            }
        } catch (error) {
            if (istekNumarasi === sonZincirIstegi.current) {
                setYoneticiZinciri(null);
                setZincirHatasi(
                    hataMesajiniGetir(
                        error,
                        "Yönetici zinciri alınamadı. Lütfen tekrar deneyin.",
                    ),
                );
            }
        } finally {
            if (istekNumarasi === sonZincirIstegi.current) {
                setZincirYukleniyor(false);
            }
        }
    }, []);

    useEffect(() => {
        if (personelZinciriGorunur) {
            void diziniYukle(BASLANGIC_FILTRELERI, 0);
        }
        if (!personelZinciriGorunur) {
            void departmanlariYukle();
        }
        void yetenekleriYukle();
    }, [
        departmanlariYukle,
        diziniYukle,
        personelZinciriGorunur,
        yoneticiHiyerarsisiGorunur,
        yetenekleriYukle,
    ]);

    useEffect(() => {
        if (personelZinciriGorunur) {
            void yoneticiZinciriniYukle();
        }
    }, [personelZinciriGorunur, yoneticiZinciriniYukle]);

    useEffect(() => {
        if (hiyerarsiGorunur) {
            void hiyerarsiyiYukle(BASLANGIC_FILTRELERI);
        }
    }, [hiyerarsiGorunur, hiyerarsiyiYukle]);

    useEffect(() => {
        if (
            !yoneticiHiyerarsisiGorunur ||
            yoneticiDepartmani === null ||
            uygulananFiltreler.departmanId !== ""
        ) {
            return;
        }

        const baslangicFiltreleri = {
            ...BASLANGIC_FILTRELERI,
            departmanId: String(yoneticiDepartmani.id),
        };

        setTaslakFiltreler(baslangicFiltreleri);
        setUygulananFiltreler(baslangicFiltreleri);
        void hiyerarsiyiYukle(baslangicFiltreleri);
    }, [
        hiyerarsiyiYukle,
        uygulananFiltreler.departmanId,
        yoneticiDepartmani,
        yoneticiHiyerarsisiGorunur,
    ]);

    function filtreleriUygula(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        const yeniFiltreler = {
            ...taslakFiltreler,
            arama: taslakFiltreler.arama.trim(),
        };

        setTaslakFiltreler(yeniFiltreler);
        setUygulananFiltreler(yeniFiltreler);
        if (hiyerarsiGorunur) {
            void hiyerarsiyiYukle(yeniFiltreler);
        } else {
            void diziniYukle(yeniFiltreler, 0);
        }
    }

    function filtreleriTemizle() {
        const baslangicFiltreleri = yoneticiHiyerarsisiGorunur && yoneticiDepartmani
            ? {
                  ...BASLANGIC_FILTRELERI,
                  departmanId: String(yoneticiDepartmani.id),
              }
            : { ...BASLANGIC_FILTRELERI };

        setTaslakFiltreler(baslangicFiltreleri);
        setUygulananFiltreler(baslangicFiltreleri);
        if (hiyerarsiGorunur) {
            void hiyerarsiyiYukle(baslangicFiltreleri);
        } else {
            void diziniYukle(baslangicFiltreleri, 0);
        }
    }

    const ilkYukleme =
        listeYukleniyor && sayfaVerisi === null && listeHatasi === null;
    const filtreliBosSonuc =
        sayfaVerisi?.icerik.length === 0 &&
        filtreUygulanmisMi(uygulananFiltreler);
    const hiyerarsiTablosuGosterilir = hiyerarsiTablosuGosterilmeli(
        kullanici?.rol,
    );
    const formYukleniyor = hiyerarsiGorunur
        ? hiyerarsiYukleniyor
        : listeYukleniyor;
    const hiyerarsiVeListeIcerigiVar =
        (gosterilenHiyerarsi?.kokler.length ?? 0) > 0 ||
        (personelZinciriGorunur && (sayfaVerisi?.icerik.length ?? 0) > 0);

    return (
        <>
            <PageMeta
                title="Ekip Dizini | Intranet Portal"
                description="Şirket çalışanlarını arama ve filtreleme ekranı"
            />
            <PageBreadcrumb pageTitle="Ekip Dizini" />

            <div className="space-y-6">
                <div>
                    <h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">
                        Ekip arkadaşlarını bul
                    </h2>
                    <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                        Ad, departman veya yeteneğe göre arama yapabilirsiniz.
                    </p>
                </div>

                <form
                    onSubmit={filtreleriUygula}
                    className="rounded-xl border border-gray-200 bg-white p-5 dark:border-white/[0.05] dark:bg-white/[0.03]"
                >
                    <div
                        className={`grid grid-cols-1 gap-4 ${
                            personelZinciriGorunur ? "md:grid-cols-2" : "md:grid-cols-3"
                        }`}
                    >
                        <div>
                            <label
                                htmlFor="ekip-dizini-arama"
                                className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300"
                            >
                                Personel ara
                            </label>
                            <input
                                id="ekip-dizini-arama"
                                type="search"
                                value={taslakFiltreler.arama}
                                disabled={formYukleniyor}
                                placeholder="Ad veya soyad"
                                onChange={(event) =>
                                    setTaslakFiltreler((onceki) => ({
                                        ...onceki,
                                        arama: event.target.value,
                                    }))
                                }
                                className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"
                            />
                        </div>

                        {!personelZinciriGorunur && (
                        <div>
                            <label
                                htmlFor="ekip-dizini-departman"
                                className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300"
                            >
                                Departman
                            </label>
                            <select
                                id="ekip-dizini-departman"
                                value={taslakFiltreler.departmanId}
                                disabled={
                                    formYukleniyor ||
                                    !departmanFiltresiSecilebilirMi(kullanici?.rol) ||
                                    departmanlarYukleniyor ||
                                    departmanHatasi !== null
                                }
                                onChange={(event) =>
                                    setTaslakFiltreler((onceki) => ({
                                        ...onceki,
                                        departmanId: event.target.value,
                                    }))
                                }
                                className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"
                            >
                                <option value="">Tüm departmanlar</option>
                                {departmanlar.map((departman) => (
                                    <option
                                        key={departman.id}
                                        value={departman.id}
                                    >
                                        {yoneticiDepartmani?.id === departman.id
                                            ? `${departman.ad} (senin departmanın)`
                                            : departman.ad}
                                    </option>
                                ))}
                            </select>
                            {departmanHatasi && (
                                <button
                                    type="button"
                                    disabled={formYukleniyor || departmanlarYukleniyor}
                                    onClick={() => void departmanlariYukle()}
                                    className="mt-2 text-sm font-medium text-brand-500 underline disabled:cursor-not-allowed disabled:opacity-50"
                                >
                                    Departmanları tekrar dene
                                </button>
                            )}
                        </div>
                        )}

                        <div>
                            <label
                                htmlFor="ekip-dizini-yetenek"
                                className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300"
                            >
                                Yetenek
                            </label>
                            <select
                                id="ekip-dizini-yetenek"
                                value={taslakFiltreler.yetenekId}
                                disabled={
                                    formYukleniyor ||
                                    yeteneklerYukleniyor ||
                                    yetenekHatasi !== null
                                }
                                onChange={(event) =>
                                    setTaslakFiltreler((onceki) => ({
                                        ...onceki,
                                        yetenekId: event.target.value,
                                    }))
                                }
                                className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"
                            >
                                <option value="">Tüm yetenekler</option>
                                {yetenekler.map((yetenek) => (
                                    <option
                                        key={yetenek.id}
                                        value={yetenek.id}
                                    >
                                        {yetenek.ad}
                                    </option>
                                ))}
                            </select>
                            {yetenekHatasi && (
                                <button
                                    type="button"
                                    disabled={
                                        formYukleniyor ||
                                        yeteneklerYukleniyor
                                    }
                                    onClick={() => void yetenekleriYukle()}
                                    className="mt-2 text-sm font-medium text-brand-500 underline disabled:cursor-not-allowed disabled:opacity-50"
                                >
                                    Yetenekleri tekrar dene
                                </button>
                            )}
                        </div>
                    </div>

                    <div className="mt-5 flex flex-wrap gap-3">
                        <button
                            type="submit"
                            disabled={formYukleniyor}
                            className="rounded-lg bg-brand-500 px-5 py-3 text-sm font-medium text-white hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            Filtrele
                        </button>
                        <button
                            type="button"
                            disabled={formYukleniyor}
                            onClick={filtreleriTemizle}
                            className="rounded-lg border border-gray-300 bg-white px-5 py-3 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300"
                        >
                            Temizle
                        </button>
                    </div>
                </form>

                <section aria-busy={formYukleniyor}>
                    {hiyerarsiTablosuGosterilir &&
                        hiyerarsiYuklemeAktif &&
                        gosterilenHiyerarsi === null && (
                            <div
                                role="status"
                                className="rounded-xl border border-gray-200 bg-white p-8 text-center text-sm text-gray-500 dark:border-white/[0.05] dark:bg-white/[0.03] dark:text-gray-400"
                            >
                                Yönetici hiyerarşisi yükleniyor…
                            </div>
                        )}

                    {hiyerarsiTablosuGosterilir && hiyerarsiHatasiAktif && (
                        <div
                            role="alert"
                            className="mb-4 rounded-xl border border-error-200 bg-error-50 p-5 text-sm text-error-700 dark:border-error-500/20 dark:bg-error-500/10 dark:text-error-400"
                        >
                            <p>{hiyerarsiHatasiAktif}</p>
                            <button
                                type="button"
                                disabled={hiyerarsiYuklemeAktif}
                                onClick={() =>
                                    hiyerarsiGorunur
                                        ? void hiyerarsiyiYukle(uygulananFiltreler)
                                        : void yoneticiZinciriniYukle()
                                }
                                className="mt-3 font-medium underline disabled:cursor-not-allowed disabled:opacity-50"
                            >
                                Tekrar dene
                            </button>
                        </div>
                    )}

                    {personelZinciriGorunur && listeHatasi && (
                        <div
                            role="alert"
                            className="mb-4 rounded-xl border border-error-200 bg-error-50 p-5 text-sm text-error-700 dark:border-error-500/20 dark:bg-error-500/10 dark:text-error-400"
                        >
                            <p>{listeHatasi}</p>
                            <button
                                type="button"
                                disabled={listeYukleniyor}
                                onClick={() =>
                                    void diziniYukle(
                                        uygulananFiltreler,
                                        aktifSayfa,
                                    )
                                }
                                className="mt-3 font-medium underline disabled:cursor-not-allowed disabled:opacity-50"
                            >
                                Tekrar dene
                            </button>
                        </div>
                    )}

                    {hiyerarsiTablosuGosterilir &&
                        !hiyerarsiHatasiAktif &&
                        gosterilenHiyerarsi &&
                        hiyerarsiVeListeIcerigiVar && (
                            <>
                                {formYukleniyor && (
                                    <p
                                        role="status"
                                        className="mb-3 text-sm text-gray-500 dark:text-gray-400"
                                    >
                                        Ekip dizini yenileniyor…
                                    </p>
                                )}
                                <EkipDiziniTablosu
                                    kisiler={
                                        personelZinciriGorunur
                                            ? sayfaVerisi?.icerik ?? []
                                            : []
                                    }
                                    hiyerarsiKokleri={gosterilenHiyerarsi.kokler}
                                />
                            </>
                        )}

                    {personelZinciriGorunur &&
                        !hiyerarsiYuklemeAktif &&
                        !hiyerarsiHatasiAktif &&
                        gosterilenHiyerarsi === null &&
                        sayfaVerisi &&
                        sayfaVerisi.icerik.length > 0 && (
                            <EkipDiziniTablosu kisiler={sayfaVerisi.icerik} />
                        )}

                    {hiyerarsiTablosuGosterilir &&
                        !hiyerarsiYuklemeAktif &&
                        !hiyerarsiHatasiAktif &&
                        gosterilenHiyerarsi &&
                        !hiyerarsiVeListeIcerigiVar && (
                            <div className="rounded-xl border border-gray-200 bg-white p-8 text-center dark:border-white/[0.05] dark:bg-white/[0.03]">
                                <p className="text-sm text-gray-500 dark:text-gray-400">
                                    Görüntülenecek yönetici hiyerarşisi bulunmuyor.
                                </p>
                            </div>
                        )}

                    {personelZinciriGorunur && ilkYukleme && (
                        <div
                            role="status"
                            className="rounded-xl border border-gray-200 bg-white p-8 text-center text-sm text-gray-500 dark:border-white/[0.05] dark:bg-white/[0.03] dark:text-gray-400"
                        >
                            Ekip dizini yükleniyor…
                        </div>
                    )}

                    {personelZinciriGorunur &&
                        !ilkYukleme &&
                        !listeHatasi &&
                        sayfaVerisi?.icerik.length === 0 &&
                        !hiyerarsiVeListeIcerigiVar && (
                            <div className="rounded-xl border border-gray-200 bg-white p-8 text-center dark:border-white/[0.05] dark:bg-white/[0.03]">
                                <p className="font-medium text-gray-800 dark:text-white/90">
                                    {filtreliBosSonuc
                                        ? "Filtrelere uygun ekip arkadaşı bulunamadı."
                                        : "Henüz görüntülenecek ekip arkadaşı bulunmuyor."}
                                </p>
                                {filtreliBosSonuc && (
                                    <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                                        Filtreleri değiştirerek tekrar deneyebilirsiniz.
                                    </p>
                                )}
                            </div>
                        )}

                    {personelZinciriGorunur &&
                        !listeHatasi &&
                        sayfaVerisi &&
                        sayfaVerisi.icerik.length > 0 && (
                            <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                                <p className="text-sm text-gray-500 dark:text-gray-400">
                                    Toplam {sayfaVerisi.toplamKayit} kayıt · Sayfa {sayfaVerisi.sayfa + 1} / {" "}
                                    {sayfaVerisi.toplamSayfa}
                                </p>
                                <div className="flex gap-3">
                                    <button
                                        type="button"
                                        disabled={listeYukleniyor || sayfaVerisi.sayfa === 0}
                                        onClick={() =>
                                            void diziniYukle(
                                                uygulananFiltreler,
                                                aktifSayfa - 1,
                                            )
                                        }
                                        className="rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300"
                                    >
                                        Önceki
                                    </button>
                                    <button
                                        type="button"
                                        disabled={
                                            listeYukleniyor ||
                                            sayfaVerisi.sayfa + 1 >= sayfaVerisi.toplamSayfa
                                        }
                                        onClick={() =>
                                            void diziniYukle(
                                                uygulananFiltreler,
                                                aktifSayfa + 1,
                                            )
                                        }
                                        className="rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300"
                                    >
                                        Sonraki
                                    </button>
                                </div>
                            </div>
                        )}
                </section>
            </div>
        </>
    );
}
