import {
    type FormEvent,
    useCallback,
    useEffect,
    useRef,
    useState,
} from "react";
import { Link } from "react-router";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import PersonelYonetimTablosu from "../components/personel-yonetimi/PersonelYonetimTablosu";
import { useAuth, type AuthRole } from "../context/AuthContext";
import {
    ApiError,
    departmanlariGetir,
    personelleriGetir,
} from "../lib/api";
import type {
    LookupSecenegi,
    PersonelYonetimFiltreleri,
    PersonelYonetimSayfasi,
} from "../types/personelYonetim";

type AktiflikFiltresi = "" | "true" | "false";

type FormFiltreleri = {
    arama: string;
    departmanId: string;
    rol: "" | AuthRole;
    aktiflik: AktiflikFiltresi;
};

const SAYFA_BOYUTU = 10;

const BASLANGIC_FILTRELERI: FormFiltreleri = {
    arama: "",
    departmanId: "",
    rol: "",
    aktiflik: "true",
};

const ADMIN_ROL_SECENEKLERI = [
    { value: "PERSONEL", label: "Personel" },
    { value: "YONETICI", label: "Yönetici" },
    { value: "HR", label: "İnsan Kaynakları" },
] as const;

const HR_ROL_SECENEKLERI = [
    { value: "PERSONEL", label: "Personel" },
    { value: "HR", label: "İnsan Kaynakları" },
] as const;

function apiFiltreleriniOlustur(
    filtreler: FormFiltreleri,
    page: number,
): PersonelYonetimFiltreleri {
    return {
        page,
        size: SAYFA_BOYUTU,
        arama: filtreler.arama.trim() || undefined,
        departmanId: filtreler.departmanId
            ? Number(filtreler.departmanId)
            : undefined,
        rol: filtreler.rol || undefined,

        // Boş değer "Tümü" anlamına gelir.
        // false geçerli bir filtre olduğu için açık karşılaştırma kullanılır.
        aktif:
            filtreler.aktiflik === ""
                ? undefined
                : filtreler.aktiflik === "true",
    };
}

function hataMesajiniGetir(error: unknown, fallback: string) {
    // Yalnız API katmanında kullanıcı için hazırlanmış mesajlara güveniriz.
    // JSON okuma gibi beklenmeyen teknik hatalar ekrana taşınmaz.
    return error instanceof ApiError && error.message
        ? error.message
        : fallback;
}

function filtreUygulanmisMi(filtreler: FormFiltreleri) {
    return (
        filtreler.arama.trim() !== "" ||
        filtreler.departmanId !== "" ||
        filtreler.rol !== "" ||
        filtreler.aktiflik !== BASLANGIC_FILTRELERI.aktiflik
    );
}

export default function PersonelYonetimi() {
    const { kullanici } = useAuth();

    // Taslak filtreler formda yazılan değerleri tutar.
    // Uygulanan filtreler ise son API isteğinde kullanılan değerlerdir.
    const [taslakFiltreler, setTaslakFiltreler] = useState<FormFiltreleri>({
        ...BASLANGIC_FILTRELERI,
    });
    const [uygulananFiltreler, setUygulananFiltreler] =
        useState<FormFiltreleri>({
            ...BASLANGIC_FILTRELERI,
        });

    const [aktifSayfa, setAktifSayfa] = useState(0);
    const [sayfaVerisi, setSayfaVerisi] =
        useState<PersonelYonetimSayfasi | null>(null);

    const [listeIstegiSuruyor, setListeIstegiSuruyor] = useState(false);
    const [listeHatasi, setListeHatasi] = useState<string | null>(null);

    const [departmanlar, setDepartmanlar] = useState<LookupSecenegi[]>([]);
    const [departmanlarYukleniyor, setDepartmanlarYukleniyor] =
        useState(false);
    const [departmanHatasi, setDepartmanHatasi] = useState<string | null>(
        null,
    );

    // Her isteğe artan bir numara verilir.
    // Eski bir istek geç dönerse yeni filtre sonucunun üzerine yazamaz.
    const sonListeIstegi = useRef(0);
    const sonDepartmanIstegi = useRef(0);

    const personelListesiniGetir = useCallback(
        async (filtreler: FormFiltreleri, page: number) => {
            const istekNumarasi = ++sonListeIstegi.current;

            setListeIstegiSuruyor(true);
            setListeHatasi(null);
            setAktifSayfa(page);

            try {
                const sonuc = await personelleriGetir(
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

                // Yeni sorgu başarısız olduğunda eski ve artık geçersiz listeyi göstermiyoruz.
                setSayfaVerisi(null);
                setListeHatasi(
                    hataMesajiniGetir(
                        error,
                        "Personel listesi alınamadı. Lütfen tekrar deneyin.",
                    ),
                );
            } finally {
                if (istekNumarasi === sonListeIstegi.current) {
                    setListeIstegiSuruyor(false);
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

            if (istekNumarasi !== sonDepartmanIstegi.current) {
                return;
            }

            setDepartmanlar(sonuc);
        } catch (error) {
            if (istekNumarasi !== sonDepartmanIstegi.current) {
                return;
            }

            setDepartmanlar([]);
            setDepartmanHatasi(
                hataMesajiniGetir(
                    error,
                    "Departman seçenekleri alınamadı.",
                ),
            );
        } finally {
            if (istekNumarasi === sonDepartmanIstegi.current) {
                setDepartmanlarYukleniyor(false);
            }
        }
    }, []);

    useEffect(() => {
        void departmanlariYukle();
        void personelListesiniGetir(BASLANGIC_FILTRELERI, 0);
    }, [departmanlariYukle, personelListesiniGetir]);

    const rolSecenekleri =
        kullanici?.rol === "ADMIN"
            ? ADMIN_ROL_SECENEKLERI
            : kullanici?.rol === "HR"
                ? HR_ROL_SECENEKLERI
                : [];

    const filtreleriUygula = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();

        const yeniFiltreler = {
            ...taslakFiltreler,
            arama: taslakFiltreler.arama.trim(),
        };

        setTaslakFiltreler(yeniFiltreler);
        setUygulananFiltreler(yeniFiltreler);
        void personelListesiniGetir(yeniFiltreler, 0);
    };

    const filtreleriTemizle = () => {
        const baslangicFiltreleri = { ...BASLANGIC_FILTRELERI };

        setTaslakFiltreler(baslangicFiltreleri);
        setUygulananFiltreler(baslangicFiltreleri);
        void personelListesiniGetir(baslangicFiltreleri, 0);
    };

    const oncekiSayfayaGit = () => {
        if (aktifSayfa > 0) {
            void personelListesiniGetir(
                uygulananFiltreler,
                aktifSayfa - 1,
            );
        }
    };

    const sonrakiSayfayaGit = () => {
        if (
            sayfaVerisi &&
            aktifSayfa + 1 < sayfaVerisi.toplamSayfa
        ) {
            void personelListesiniGetir(
                uygulananFiltreler,
                aktifSayfa + 1,
            );
        }
    };

    const ilkYukleme =
        listeIstegiSuruyor && sayfaVerisi === null && listeHatasi === null;
    const listeYenileniyor =
        listeIstegiSuruyor && sayfaVerisi !== null;
    const filtreliBosSonuc =
        sayfaVerisi?.icerik.length === 0 &&
        filtreUygulanmisMi(uygulananFiltreler);

    return (
        <>
            <PageMeta
                title="Personel Yönetimi | Intranet Portal"
                description="Personel kayıtlarını görüntüleme ve filtreleme ekranı"
            />

            <PageBreadcrumb pageTitle="Personel Yönetimi" />

            <div className="space-y-6">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                        <h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">
                            Personel kayıtları
                        </h2>
                        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                            Personel kayıtlarını arayabilir ve filtreleyebilirsiniz.
                        </p>
                    </div>

                    <Link
                        to="/yonetim/personel/yeni"
                        className="inline-flex w-full items-center justify-center rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600 sm:w-auto"
                    >
                        Yeni Personel
                    </Link>
                </div>

                <form
                    onSubmit={filtreleriUygula}
                    className="rounded-xl border border-gray-200 bg-white p-5 dark:border-white/[0.05] dark:bg-white/[0.03]"
                >
                    <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                        <div>
                            <label
                                htmlFor="personel-arama"
                                className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300"
                            >
                                Personel ara
                            </label>
                            <input
                                id="personel-arama"
                                type="search"
                                value={taslakFiltreler.arama}
                                disabled={listeIstegiSuruyor}
                                placeholder="Ad, soyad veya kullanıcı adı"
                                onChange={(event) =>
                                    setTaslakFiltreler((onceki) => ({
                                        ...onceki,
                                        arama: event.target.value,
                                    }))
                                }
                                className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"
                            />
                        </div>

                        <div>
                            <label
                                htmlFor="personel-departman"
                                className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300"
                            >
                                Departman
                            </label>
                            <select
                                id="personel-departman"
                                value={taslakFiltreler.departmanId}
                                disabled={
                                    listeIstegiSuruyor ||
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
                                    <option key={departman.id} value={departman.id}>
                                        {departman.ad}
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div>
                            <label
                                htmlFor="personel-rol"
                                className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300"
                            >
                                Rol
                            </label>
                            <select
                                id="personel-rol"
                                value={taslakFiltreler.rol}
                                disabled={listeIstegiSuruyor}
                                onChange={(event) =>
                                    setTaslakFiltreler((onceki) => ({
                                        ...onceki,
                                        rol: event.target.value as "" | AuthRole,
                                    }))
                                }
                                className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"
                            >
                                <option value="">Tüm roller</option>
                                {rolSecenekleri.map((secenek) => (
                                    <option key={secenek.value} value={secenek.value}>
                                        {secenek.label}
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div>
                            <label
                                htmlFor="personel-aktiflik"
                                className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300"
                            >
                                Aktiflik
                            </label>
                            <select
                                id="personel-aktiflik"
                                value={taslakFiltreler.aktiflik}
                                disabled={listeIstegiSuruyor}
                                onChange={(event) =>
                                    setTaslakFiltreler((onceki) => ({
                                        ...onceki,
                                        aktiflik: event.target.value as AktiflikFiltresi,
                                    }))
                                }
                                className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"
                            >
                                <option value="true">Yalnız aktifler</option>
                                <option value="false">Yalnız pasifler</option>
                                <option value="">Aktif ve pasif</option>
                            </select>
                        </div>
                    </div>

                    {departmanHatasi && (
                        <div
                            role="alert"
                            className="mt-4 flex flex-wrap items-center gap-3 rounded-lg bg-warning-50 px-4 py-3 text-sm text-warning-700 dark:bg-warning-500/10 dark:text-warning-400"
                        >
                            <span>{departmanHatasi}</span>
                            <button
                                type="button"
                                disabled={
                                    departmanlarYukleniyor || listeIstegiSuruyor
                                }
                                onClick={() => void departmanlariYukle()}
                                className="font-medium underline disabled:cursor-not-allowed disabled:opacity-50"
                            >
                                Tekrar dene
                            </button>
                        </div>
                    )}

                    <div className="mt-5 flex flex-wrap gap-3">
                        <button
                            type="submit"
                            disabled={listeIstegiSuruyor}
                            className="rounded-lg bg-brand-500 px-5 py-3 text-sm font-medium text-white hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            Filtrele
                        </button>

                        <button
                            type="button"
                            disabled={listeIstegiSuruyor}
                            onClick={filtreleriTemizle}
                            className="rounded-lg border border-gray-300 bg-white px-5 py-3 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300"
                        >
                            Temizle
                        </button>
                    </div>
                </form>

                <section aria-busy={listeIstegiSuruyor}>
                    {listeYenileniyor && (
                        <p
                            role="status"
                            className="mb-3 text-sm text-gray-500 dark:text-gray-400"
                        >
                            Personel listesi yenileniyor…
                        </p>
                    )}

                    {ilkYukleme && (
                        <div
                            role="status"
                            className="rounded-xl border border-gray-200 bg-white p-8 text-center text-sm text-gray-500 dark:border-white/[0.05] dark:bg-white/[0.03] dark:text-gray-400"
                        >
                            Personel listesi yükleniyor…
                        </div>
                    )}

                    {listeHatasi && (
                        <div
                            role="alert"
                            className="rounded-xl border border-error-200 bg-error-50 p-5 text-sm text-error-700 dark:border-error-500/20 dark:bg-error-500/10 dark:text-error-400"
                        >
                            <p>{listeHatasi}</p>
                            <button
                                type="button"
                                disabled={listeIstegiSuruyor}
                                onClick={() =>
                                    void personelListesiniGetir(
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

                    {!ilkYukleme && !listeHatasi && sayfaVerisi && sayfaVerisi.icerik.length === 0 && (
                            <div className="rounded-xl border border-gray-200 bg-white p-8 text-center dark:border-white/[0.05] dark:bg-white/[0.03]">
                                <p className="font-medium text-gray-800 dark:text-white/90">
                                    {filtreliBosSonuc
                                        ? "Filtrelere uygun personel bulunamadı."
                                        : "Henüz görüntülenecek personel bulunmuyor."}
                                </p>
                                {filtreliBosSonuc && (
                                    <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                                        Filtreleri değiştirerek tekrar deneyebilirsin.
                                    </p>
                                )}
                                {sayfaVerisi.sayfa > 0 && (
                                    <button
                                        type="button"
                                        disabled={listeIstegiSuruyor}
                                        onClick={oncekiSayfayaGit}
                                        className="mt-4 rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300"
                                    >
                                        Önceki sayfaya dön
                                    </button>
                                )}
                            </div>
                        )}

                    {!listeHatasi &&
                        sayfaVerisi &&
                        sayfaVerisi.icerik.length > 0 && (
                            <>
                                <PersonelYonetimTablosu
                                    personeller={sayfaVerisi.icerik}
                                />

                                <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                                    <p className="text-sm text-gray-500 dark:text-gray-400">
                                        Toplam {sayfaVerisi.toplamKayit} kayıt · Sayfa{" "}
                                        {sayfaVerisi.sayfa + 1} /{" "}
                                        {sayfaVerisi.toplamSayfa}
                                    </p>

                                    <div className="flex gap-3">
                                        <button
                                            type="button"
                                            disabled={
                                                listeIstegiSuruyor ||
                                                sayfaVerisi.sayfa === 0
                                            }
                                            onClick={oncekiSayfayaGit}
                                            className="rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300"
                                        >
                                            Önceki
                                        </button>

                                        <button
                                            type="button"
                                            disabled={
                                                listeIstegiSuruyor ||
                                                sayfaVerisi.sayfa + 1 >=
                                                sayfaVerisi.toplamSayfa
                                            }
                                            onClick={sonrakiSayfayaGit}
                                            className="rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300"
                                        >
                                            Sonraki
                                        </button>
                                    </div>
                                </div>
                            </>
                        )}
                </section>
            </div>
        </>
    );
}
