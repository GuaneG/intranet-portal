import {
    useCallback,
    useEffect,
    useRef,
    useState,
} from "react";
import {
    Link,
    useNavigate,
} from "react-router";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import PersonelYonetimOlusturmaFormu from "../components/personel-yonetimi/PersonelYonetimOlusturmaFormu";
import {
    ApiError,
    atanabilirYoneticileriGetir,
    departmanlariGetir,
} from "../lib/api";
import type {
    AtanabilirYoneticiSecenegi,
    LookupSecenegi,
    PersonelOlusturmaResponse,
} from "../types/personelYonetim";

function hataMesajiniGetir(
    error: unknown,
    fallback: string,
) {
    return error instanceof ApiError && error.message
        ? error.message
        : fallback;
}

/**
 * Yeni personel formunun ihtiyaç duyduğu lookup verilerini yükler
 * ve başarılı oluşturma sonrasında bilgilendirme ekranını gösterir.
 */
export default function PersonelYonetimOlustur() {
    const navigate = useNavigate();

    const [departmanlar, setDepartmanlar] =
        useState<LookupSecenegi[]>([]);
    const [
        departmanlarYukleniyor,
        setDepartmanlarYukleniyor,
    ] = useState(true);
    const [departmanHatasi, setDepartmanHatasi] =
        useState<string | null>(null);

    const [yoneticiler, setYoneticiler] =
        useState<AtanabilirYoneticiSecenegi[]>([]);
    const [
        yoneticilerYukleniyor,
        setYoneticilerYukleniyor,
    ] = useState(false);
    const [yoneticiHatasi, setYoneticiHatasi] =
        useState<string | null>(null);

    const [olusturulanPersonel, setOlusturulanPersonel] =
        useState<PersonelOlusturmaResponse | null>(null);

    // Geç dönen eski lookup cevaplarının yeni seçimi ezmesini engeller.
    const sonDepartmanIstegi = useRef(0);
    const sonYoneticiIstegi = useRef(0);

    const departmanlariYukle = useCallback(async () => {
        const istekNumarasi =
            ++sonDepartmanIstegi.current;

        setDepartmanlarYukleniyor(true);
        setDepartmanHatasi(null);

        try {
            const sonuc = await departmanlariGetir();

            if (
                istekNumarasi !==
                sonDepartmanIstegi.current
            ) {
                return;
            }

            setDepartmanlar(sonuc);
        } catch (error) {
            if (
                istekNumarasi !==
                sonDepartmanIstegi.current
            ) {
                return;
            }

            setDepartmanlar([]);
            setDepartmanHatasi(
                hataMesajiniGetir(
                    error,
                    "Departman bilgileri alınamadı.",
                ),
            );
        } finally {
            if (
                istekNumarasi ===
                sonDepartmanIstegi.current
            ) {
                setDepartmanlarYukleniyor(false);
            }
        }
    }, []);

    const yoneticileriYukle = useCallback(
        async (departmanId: number) => {
            const istekNumarasi =
                ++sonYoneticiIstegi.current;

            setYoneticiler([]);
            setYoneticilerYukleniyor(true);
            setYoneticiHatasi(null);

            try {
                const sonuc =
                    await atanabilirYoneticileriGetir(
                        departmanId,
                        "",
                    );

                if (
                    istekNumarasi !==
                    sonYoneticiIstegi.current
                ) {
                    return;
                }

                setYoneticiler(sonuc);
            } catch (error) {
                if (
                    istekNumarasi !==
                    sonYoneticiIstegi.current
                ) {
                    return;
                }

                setYoneticiler([]);
                setYoneticiHatasi(
                    hataMesajiniGetir(
                        error,
                        "Yönetici bilgileri alınamadı.",
                    ),
                );
            } finally {
                if (
                    istekNumarasi ===
                    sonYoneticiIstegi.current
                ) {
                    setYoneticilerYukleniyor(false);
                }
            }
        },
        [],
    );

    useEffect(() => {
        void departmanlariYukle();
    }, [departmanlariYukle]);

    const ilkLookupHatasi =
        !departmanlarYukleniyor &&
        departmanHatasi !== null &&
        departmanlar.length === 0;

    return (
        <>
            <PageMeta
                title="Yeni Personel | Personel Yönetimi"
                description="Personel yönetiminde yeni personel oluşturma ekranı"
            />

            <PageBreadcrumb pageTitle="Yeni Personel" />

            <div className="mb-5">
                <Link
                    to="/yonetim/personel"
                    className="text-sm font-medium text-brand-500 hover:text-brand-600 dark:text-brand-400 dark:hover:text-brand-300"
                >
                    ← Personel listesine dön
                </Link>
            </div>

            {olusturulanPersonel ? (
                <section className="rounded-xl border border-success-200 bg-success-50 p-6 dark:border-success-500/20 dark:bg-success-500/10">
                    <h2 className="text-lg font-semibold text-success-800 dark:text-success-300">
                        Personel oluşturuldu
                    </h2>

                    <p className="mt-3 text-sm leading-6 text-success-700 dark:text-success-400">
                        @{olusturulanPersonel.kullaniciAdi} ilk
                        girişte kullanıcı adıyla giriş yapmalı ve
                        parolasını değiştirmelidir.
                    </p>

                    <div className="mt-6 flex flex-wrap gap-3">
                        <Link
                            to={`/yonetim/personel/${encodeURIComponent(
                                olusturulanPersonel.personelId,
                            )}`}
                            className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600"
                        >
                            Personel detayını aç
                        </Link>

                        <Link
                            to="/yonetim/personel"
                            className="rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:bg-transparent dark:text-gray-300 dark:hover:bg-white/[0.03]"
                        >
                            Listeye dön
                        </Link>
                    </div>
                </section>
            ) : ilkLookupHatasi ? (
                <section
                    role="alert"
                    className="rounded-xl border border-error-200 bg-error-50 p-5 text-sm text-error-700 dark:border-error-500/20 dark:bg-error-500/10 dark:text-error-400"
                >
                    <p>{departmanHatasi}</p>

                    <div className="mt-4 flex flex-wrap gap-3">
                        <button
                            type="button"
                            onClick={() =>
                                void departmanlariYukle()
                            }
                            className="font-medium underline"
                        >
                            Tekrar dene
                        </button>

                        <button
                            type="button"
                            onClick={() =>
                                navigate("/yonetim/personel")
                            }
                            className="font-medium underline"
                        >
                            Personel listesine dön
                        </button>
                    </div>
                </section>
            ) : (
                <PersonelYonetimOlusturmaFormu
                    departmanlar={departmanlar}
                    departmanlarYukleniyor={
                        departmanlarYukleniyor
                    }
                    departmanHatasi={departmanHatasi}
                    onDepartmanlariTekrarDene={() =>
                        void departmanlariYukle()
                    }
                    yoneticiler={yoneticiler}
                    yoneticilerYukleniyor={
                        yoneticilerYukleniyor
                    }
                    yoneticiHatasi={yoneticiHatasi}
                    onDepartmanDegisti={(departmanId) =>
                        void yoneticileriYukle(departmanId)
                    }
                    onYoneticileriTekrarDene={(
                        departmanId,
                    ) => void yoneticileriYukle(departmanId)}
                    onIptal={() =>
                        navigate("/yonetim/personel")
                    }
                    onOlusturuldu={setOlusturulanPersonel}
                />
            )}
        </>
    );
}