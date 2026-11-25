import {
    useCallback,
    useEffect,
    useRef,
    useState,
} from "react";
import {
    Link,
    useNavigate,
    useParams,
} from "react-router";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import PersonelYonetimDuzenleFormu from "../components/personel-yonetimi/PersonelYonetimDuzenleFormu";
import {
    ApiError,
    atanabilirYoneticileriGetir,
    departmanlariGetir,
    personelDetayiniGetir,
} from "../lib/api";
import type {
    AtanabilirYoneticiSecenegi,
    LookupSecenegi,
    PersonelYonetimDetayi,
} from "../types/personelYonetim";

function hataMesajiniGetir(
    error: unknown,
    fallback: string,
) {
    return error instanceof ApiError && error.message
        ? error.message
        : fallback;
}

export default function PersonelYonetimDuzenle() {
    const { personelId } = useParams<{
        personelId: string;
    }>();
    const navigate = useNavigate();

    const [detay, setDetay] =
        useState<PersonelYonetimDetayi | null>(null);
    const [detayYukleniyor, setDetayYukleniyor] =
        useState(true);
    const [detayHatasi, setDetayHatasi] =
        useState<string | null>(null);

    const [departmanlar, setDepartmanlar] =
        useState<LookupSecenegi[]>([]);
    const [departmanlarYukleniyor, setDepartmanlarYukleniyor] =
        useState(false);
    const [departmanHatasi, setDepartmanHatasi] =
        useState<string | null>(null);

    const [yoneticiler, setYoneticiler] =
        useState<AtanabilirYoneticiSecenegi[]>([]);
    const [yoneticilerYukleniyor, setYoneticilerYukleniyor] =
        useState(false);
    const [yoneticiHatasi, setYoneticiHatasi] =
        useState<string | null>(null);

    // Her istek türünün kendi sayacı vardır. Eski response yeni ekran
    // state'ini ezemez.
    const sonDetayIstegi = useRef(0);
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
        async (
            departmanId: number,
            haricPersonelId: string,
        ) => {
            const istekNumarasi =
                ++sonYoneticiIstegi.current;

            setYoneticilerYukleniyor(true);
            setYoneticiHatasi(null);

            try {
                const sonuc =
                    await atanabilirYoneticileriGetir(
                        departmanId,
                        haricPersonelId,
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

    const detayiYukle = useCallback(
        async (gecerliPersonelId: string) => {
            const istekNumarasi =
                ++sonDetayIstegi.current;

            // Yeni personel için eski lookup response'ları geçersiz olur.
            ++sonDepartmanIstegi.current;
            ++sonYoneticiIstegi.current;

            setDetay(null);
            setDetayYukleniyor(true);
            setDetayHatasi(null);

            setDepartmanlar([]);
            setDepartmanlarYukleniyor(false);
            setDepartmanHatasi(null);

            setYoneticiler([]);
            setYoneticilerYukleniyor(false);
            setYoneticiHatasi(null);

            try {
                const sonuc = await personelDetayiniGetir(
                    gecerliPersonelId,
                );

                if (
                    istekNumarasi !==
                    sonDetayIstegi.current
                ) {
                    return;
                }

                setDetay(sonuc);

                // Düzenleme formu, mevcut yönetici olmasa da yönetici
                // seçebilmek için aday listesini yükler.
                void departmanlariYukle();
                void yoneticileriYukle(
                    sonuc.departmanId,
                    sonuc.personelId,
                );
            } catch (error) {
                if (
                    istekNumarasi !==
                    sonDetayIstegi.current
                ) {
                    return;
                }

                setDetay(null);
                setDetayHatasi(
                    hataMesajiniGetir(
                        error,
                        "Personel bilgileri alınamadı. Lütfen tekrar deneyin.",
                    ),
                );
            } finally {
                if (
                    istekNumarasi ===
                    sonDetayIstegi.current
                ) {
                    setDetayYukleniyor(false);
                }
            }
        },
        [departmanlariYukle, yoneticileriYukle],
    );

    useEffect(() => {
        const gecerliPersonelId = personelId?.trim();

        if (!gecerliPersonelId) {
            ++sonDetayIstegi.current;
            ++sonDepartmanIstegi.current;
            ++sonYoneticiIstegi.current;

            setDetay(null);
            setDetayYukleniyor(false);
            setDetayHatasi(
                "Geçersiz personel bağlantısı.",
            );

            return;
        }

        void detayiYukle(gecerliPersonelId);
    }, [detayiYukle, personelId]);

    const detayYolu = detay
        ? `/yonetim/personel/${encodeURIComponent(
            detay.personelId,
        )}`
        : "/yonetim/personel";

    const sayfaBasligi = detay
        ? `${detay.ad} ${detay.soyad} | Personel Düzenle`
        : "Personel Düzenle | Intranet Portal";

    return (
        <>
            <PageMeta
                title={sayfaBasligi}
                description="Personel yönetimi güncelleme ekranı"
            />

            <PageBreadcrumb pageTitle="Personel Düzenle" />

            <div className="mb-5">
                <Link
                    to={detayYolu}
                    className="text-sm font-medium text-brand-500 hover:text-brand-600 dark:text-brand-400 dark:hover:text-brand-300"
                >
                    ← Personel detayına dön
                </Link>
            </div>

            <section
                aria-busy={detayYukleniyor}
                className="min-h-[160px]"
            >
                {detayYukleniyor && (
                    <div
                        role="status"
                        aria-live="polite"
                        className="rounded-xl border border-gray-200 bg-white p-8 text-center text-sm text-gray-500 dark:border-white/[0.05] dark:bg-white/[0.03] dark:text-gray-400"
                    >
                        Personel bilgileri yükleniyor…
                    </div>
                )}

                {!detayYukleniyor &&
                    detayHatasi && (
                        <div
                            role="alert"
                            className="rounded-xl border border-error-200 bg-error-50 p-5 text-sm text-error-700 dark:border-error-500/20 dark:bg-error-500/10 dark:text-error-400"
                        >
                            <p>{detayHatasi}</p>

                            <div className="mt-4 flex flex-wrap gap-3">
                                {personelId?.trim() && (
                                    <button
                                        type="button"
                                        onClick={() =>
                                            void detayiYukle(
                                                personelId.trim(),
                                            )
                                        }
                                        className="font-medium underline"
                                    >
                                        Tekrar dene
                                    </button>
                                )}

                                <Link
                                    to="/yonetim/personel"
                                    className="font-medium underline"
                                >
                                    Personel listesine dön
                                </Link>
                            </div>
                        </div>
                    )}

                {!detayYukleniyor &&
                    !detayHatasi &&
                    detay && (
                        <PersonelYonetimDuzenleFormu
                            key={detay.personelId}
                            detay={detay}
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
                                void yoneticileriYukle(
                                    departmanId,
                                    detay.personelId,
                                )
                            }
                            onYoneticileriTekrarDene={(
                                departmanId,
                            ) =>
                                void yoneticileriYukle(
                                    departmanId,
                                    detay.personelId,
                                )
                            }
                            onIptal={() =>
                                navigate(detayYolu)
                            }
                            onKaydedildi={() =>
                                navigate(detayYolu, {
                                    replace: true,
                                })
                            }
                        />
                    )}
            </section>
        </>
    );
}