import {useCallback, useEffect, useRef, useState,} from "react";
import {Link, useNavigate, useParams,} from "react-router";import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import PersonelYonetimDetayBilgileri from "../components/personel-yonetimi/PersonelYonetimDetayBilgileri";
import PersonelPasiflestirmeOnayi from "../components/personel-yonetimi/PersonelPasiflestirmeOnayi";
import {ApiError, atanabilirYoneticileriGetir, departmanlariGetir, personelDetayiniGetir,} from "../lib/api";
import type {
    AtanabilirYoneticiSecenegi,
    LookupSecenegi,
    PersonelYonetimDetayi,
} from "../types/personelYonetim";
import PersonelAktiflestirmeOnayi from "../components/personel-yonetimi/PersonelAktiflestirmeOnayi";
import PersonelRolDegistirmeOnayi from "../components/personel-yonetimi/PersonelRolDegistirmeOnayi";
import PersonelParolaSifirlamaOnayi from "../components/personel-yonetimi/PersonelParolaSifirlamaOnayi";
import PersonelYetenekDuzenlemeOnayi from "../components/personel-yonetimi/PersonelYetenekDuzenlemeOnayi";
import { useAuth } from "../context/AuthContext";

/**
 * Yalnız API katmanında kullanıcı için hazırlanmış mesajları gösterir.
 * JSON okuma hatası gibi teknik mesajlar ekrana taşınmaz.
 */
function hataMesajiniGetir(error: unknown, fallback: string) {
    return error instanceof ApiError && error.message
        ? error.message
        : fallback;
}

export default function PersonelYonetimDetayi() {
    const { personelId } = useParams<{
        personelId: string;
    }>();
    const navigate = useNavigate();
    const { kullanici } = useAuth();

    const [detay, setDetay] =
        useState<PersonelYonetimDetayi | null>(null);
    const [detayYukleniyor, setDetayYukleniyor] =
        useState(true);
    const [detayHatasi, setDetayHatasi] =
        useState<string | null>(null);
    const [islemBasariMesaji, setIslemBasariMesaji] =
        useState<string | null>(null);

    const [departmanlar, setDepartmanlar] =
        useState<LookupSecenegi[]>([]);
    const [departmanYukleniyor, setDepartmanYukleniyor] =
        useState(false);
    const [departmanHatasi, setDepartmanHatasi] =
        useState<string | null>(null);

    const [yoneticiler, setYoneticiler] =
        useState<AtanabilirYoneticiSecenegi[]>([]);
    const [yoneticiYukleniyor, setYoneticiYukleniyor] =
        useState(false);
    const [yoneticiHatasi, setYoneticiHatasi] =
        useState<string | null>(null);

    /**
     * Her istek türünün kendi sayacı vardır.
     * Eski veya geç dönen cevap yalnız kendi türündeki yeni isteği
     * ezemez.
     */
    const sonDetayIstegi = useRef(0);
    const sonDepartmanIstegi = useRef(0);
    const sonYoneticiIstegi = useRef(0);

    const departmanlariYukle = useCallback(async () => {
        const istekNumarasi =
            ++sonDepartmanIstegi.current;

        setDepartmanYukleniyor(true);
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
                    "Departman bilgisi alınamadı.",
                ),
            );
        } finally {
            if (
                istekNumarasi ===
                sonDepartmanIstegi.current
            ) {
                setDepartmanYukleniyor(false);
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

            setYoneticiYukleniyor(true);
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
                        "Yönetici bilgisi alınamadı.",
                    ),
                );
            } finally {
                if (
                    istekNumarasi ===
                    sonYoneticiIstegi.current
                ) {
                    setYoneticiYukleniyor(false);
                }
            }
        },
        [],
    );

    const detayiYukle = useCallback(
        async (gecerliPersonelId: string) => {
            const istekNumarasi =
                ++sonDetayIstegi.current;

            /**
             * Yeni personel isteği başladığında önceki personelin
             * devam eden lookup cevapları da geçersiz kılınır.
             */
            ++sonDepartmanIstegi.current;
            ++sonYoneticiIstegi.current;

            setDetay(null);
            setDetayYukleniyor(true);
            setDetayHatasi(null);

            setDepartmanlar([]);
            setDepartmanYukleniyor(false);
            setDepartmanHatasi(null);

            setYoneticiler([]);
            setYoneticiYukleniyor(false);
            setYoneticiHatasi(null);

            try {
                const sonuc =
                    await personelDetayiniGetir(
                        gecerliPersonelId,
                    );

                if (
                    istekNumarasi !==
                    sonDetayIstegi.current
                ) {
                    return;
                }

                setDetay(sonuc);

                /**
                 * Departman lookup her başarılı detay için gerekir.
                 * Yönetici lookup yalnız atanmış yönetici varsa
                 * gönderilir.
                 */
                void departmanlariYukle();

                if (sonuc.yoneticiId !== null) {
                    void yoneticileriYukle(
                        sonuc.departmanId,
                        sonuc.personelId,
                    );
                }
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
        const gecerliPersonelId =
            personelId?.trim();

        if (!gecerliPersonelId) {
            ++sonDetayIstegi.current;
            ++sonDepartmanIstegi.current;
            ++sonYoneticiIstegi.current;

            setDetay(null);
            setDetayYukleniyor(false);
            setDetayHatasi(
                "Geçersiz personel bağlantısı.",
            );
            setIslemBasariMesaji(null);

            return;
        }

        setIslemBasariMesaji(null);
        void detayiYukle(gecerliPersonelId);


    }, [detayiYukle, personelId]);

    const detayiTekrarYukle = () => {
        const gecerliPersonelId =
            personelId?.trim();

        if (gecerliPersonelId) {
            void detayiYukle(gecerliPersonelId);
        }
    };

    const yoneticileriTekrarYukle = () => {
        if (
            detay &&
            detay.yoneticiId !== null
        ) {
            void yoneticileriYukle(
                detay.departmanId,
                detay.personelId,
            );
        }
    };

    const departmanMetni =
        detay
            ? departmanlar.find(
                (departman) =>
                    departman.id ===
                    detay.departmanId,
            )?.ad ??
            "Departman bilgisi görüntülenemiyor"
            : "";

    const yonetici =
        detay?.yoneticiId
            ? yoneticiler.find(
                (secenek) =>
                    secenek.personelId ===
                    detay.yoneticiId,
            )
            : undefined;

    const yoneticiMetni =
        detay?.yoneticiId === null
            ? "Atanmamış"
            : yonetici
                ? `${yonetici.ad} ${yonetici.soyad} (@${yonetici.kullaniciAdi})`
                : "Yönetici bilgisi görüntülenemiyor";

    const sayfaBasligi = detay
        ? `${detay.ad} ${detay.soyad} | Personel Yönetimi`
        : "Personel Detayı | Intranet Portal";

    return (
        <>
            <PageMeta
                title={sayfaBasligi}
                description="Personel yönetimi detay ekranı"
            />

            <PageBreadcrumb pageTitle="Personel Detayı" />

            <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
                <Link
                    to="/yonetim/personel"
                    className="text-sm font-medium text-brand-500 hover:text-brand-600 dark:text-brand-400 dark:hover:text-brand-300"
                >
                    ← Personel listesine dön
                </Link>

                {detay && (
                    <div className="flex flex-wrap items-center gap-3">
                        <Link
                            to={`/yonetim/personel/${encodeURIComponent(
                                detay.personelId,
                            )}/duzenle`}
                            className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600"
                        >
                            Düzenle
                        </Link>

                        <PersonelPasiflestirmeOnayi
                            detay={detay}
                            onBasarili={() =>
                                navigate("/yonetim/personel", {
                                    replace: true,
                                })
                            }
                        />
                        <PersonelAktiflestirmeOnayi
                            detay={detay}
                            onBasarili={() =>
                                navigate("/yonetim/personel", {
                                    replace: true,
                                })
                            }
                        />
                        {kullanici?.rol === "ADMIN" && (
                            <PersonelParolaSifirlamaOnayi
                                detay={detay}
                                onBasarili={() =>
                                    setIslemBasariMesaji(
                                        "Parola sıfırlandı. Kullanıcı sonraki girişinde parolasını değiştirmelidir.",
                                    )
                                }
                            />
                        )}
                        {kullanici?.rol === "ADMIN" && (
                            <PersonelYetenekDuzenlemeOnayi
                                detay={detay}
                                onBasarili={async () => {
                                    await detayiYukle(detay.personelId);
                                    setIslemBasariMesaji(
                                        "Personelin yetenekleri güncellendi.",
                                    );
                                }}
                            />
                        )}
                        {kullanici?.rol === "ADMIN" && (
                            <PersonelRolDegistirmeOnayi
                                detay={detay}
                                onBasarili={async () => {
                                    await detayiYukle(
                                        detay.personelId,
                                    );
                                }}
                            />
                        )}
                    </div>
                )}
            </div>

            <section
                aria-busy={detayYukleniyor}
                className="min-h-[160px]"
            >
                {detayYukleniyor && (
                    <div
                        role="status"
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
                                        onClick={
                                            detayiTekrarYukle
                                        }
                                        disabled={
                                            detayYukleniyor
                                        }
                                        className="font-medium underline disabled:cursor-not-allowed disabled:opacity-50"
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
                        <>
                            {islemBasariMesaji && (
                                <p
                                    role="status"
                                    className="mb-4 rounded-xl border border-success-200 bg-success-50 p-4 text-sm text-success-700 dark:border-success-500/20 dark:bg-success-500/10 dark:text-success-400"
                                >
                                    {islemBasariMesaji}
                                </p>
                            )}

                            <PersonelYonetimDetayBilgileri
                                detay={detay}
                                departman={{
                                    metin: departmanMetni,
                                    yukleniyor:
                                    departmanYukleniyor,
                                    hata: departmanHatasi,
                                    tekrarDenemeDevreDisi:
                                    departmanYukleniyor,
                                    onTekrarDene: () =>
                                        void departmanlariYukle(),
                                }}
                                yonetici={{
                                    metin: yoneticiMetni,
                                    yukleniyor:
                                    yoneticiYukleniyor,
                                    hata: yoneticiHatasi,
                                    tekrarDenemeDevreDisi:
                                        yoneticiYukleniyor ||
                                        detay.yoneticiId ===
                                        null,
                                    onTekrarDene:
                                    yoneticileriTekrarYukle,
                                }}
                            />
                        </>
                    )}
            </section>
        </>
    );
}
