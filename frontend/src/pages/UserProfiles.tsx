import { useEffect, useRef, useState } from "react";
import { Link } from "react-router";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import ProfilDuzenleForm from "../components/UserProfile/ProfilDuzenleForm";
import UserEducationSkillsCard from "../components/UserProfile/UserEducationSkillsCard";
import UserInfoCard from "../components/UserProfile/UserInfoCard";
import UserMetaCard from "../components/UserProfile/UserMetaCard";
import ProfilYetenekDuzenlemeModali from "../components/UserProfile/ProfilYetenekDuzenlemeModali";
import { ApiError, profilFotografimiGuncelle, profilFotografimiSil, profilimiGetir } from "../lib/api.js";
import type { ProfilBilgileri } from "../types/profil";

function profilHataMesajiOlustur(hata: unknown): string {
    if (!(hata instanceof ApiError)) {
        return "Profil bilgileri alınamadı. Lütfen tekrar deneyin.";
    }

    if (hata.status === 0) {
        return "Sunucuya ulaşılamadı. Bağlantınızı kontrol edip tekrar deneyin.";
    }

    if (hata.status === 404) {
        return "Profil bilgileriniz bulunamadı.";
    }

    if (hata.status >= 500) {
        return "Profil bilgileri şu anda alınamıyor. Lütfen daha sonra tekrar deneyin.";
    }

    return "Profil bilgileri alınamadı. Lütfen tekrar deneyin.";
}

export default function UserProfiles() {
    const [profil, setProfil] = useState<ProfilBilgileri | null>(null);
    const [yukleniyor, setYukleniyor] = useState(true);
    const [hata, setHata] = useState<string | null>(null);
    const [retryAnahtari, setRetryAnahtari] = useState(0);
    const [duzenleniyor, setDuzenleniyor] = useState(false);
    const [yeteneklerDuzenleniyor, setYeteneklerDuzenleniyor] =
        useState(false);
    const [fotografYukleniyor, setFotografYukleniyor] = useState(false);
    const [fotografHatasi, setFotografHatasi] = useState<string | null>(null);
    const [basariMesaji, setBasariMesaji] = useState("");
    const fotografGonderimKilidi = useRef(false);

    useEffect(() => {
        let aktif = true;

        async function profiliYukle() {
            setYukleniyor(true);
            setHata(null);
            setProfil(null);

            try {
                const profilBilgileri =
                    (await profilimiGetir()) as ProfilBilgileri;

                if (!aktif) {
                    return;
                }

                setProfil(profilBilgileri);
            } catch (yakalananHata: unknown) {
                if (!aktif) {
                    return;
                }

                setProfil(null);
                setHata(profilHataMesajiOlustur(yakalananHata));
            } finally {
                if (aktif) {
                    setYukleniyor(false);
                }
            }
        }

        void profiliYukle();

        return () => {
            aktif = false;
        };
    }, [retryAnahtari]);

    const tekrarDene = () => {
        setProfil(null);
        setHata(null);
        setYukleniyor(true);
        setRetryAnahtari((mevcutAnahtar) => mevcutAnahtar + 1);
    };

    const duzenlemeyiAc = () => {
        if (!profil || duzenleniyor) {
            return;
        }

        setBasariMesaji("");
        setDuzenleniyor(true);
    };

    const duzenlemeyiKapat = () => {
        setDuzenleniyor(false);
    };

    const handleProfilYenilendi = (guncelProfil: ProfilBilgileri) => {
        setProfil(guncelProfil);
        setDuzenleniyor(false);
        setBasariMesaji("Profil bilgileriniz başarıyla güncellendi.");
    };

    const yetenekDuzenlemeyiAc = () => {
        if (!profil) {
            return;
        }

        setBasariMesaji("");
        setYeteneklerDuzenleniyor(true);
    };

    const yetenekDuzenlemeyiKapat = () => {
        setYeteneklerDuzenleniyor(false);
    };

    const yeteneklerGuncellendi = async () => {
        try {
            const guncelProfil =
                (await profilimiGetir()) as ProfilBilgileri;

            setProfil(guncelProfil);
            setBasariMesaji("Yetenekleriniz başarıyla güncellendi.");
        } catch (yakalananHata: unknown) {
            throw new ApiError(
                yakalananHata instanceof ApiError
                    ? yakalananHata.status
                    : 0,
                profilHataMesajiOlustur(yakalananHata),
            );
        }
    };

    const profilFotografiDegisti = (
        profilFoto: string,
        islem: "guncellendi" | "silindi",
    ) => {
        setProfil((mevcutProfil) =>
            mevcutProfil
                ? { ...mevcutProfil, profilFoto }
                : mevcutProfil,
        );
        setBasariMesaji(
            islem === "silindi"
                ? "Profil fotoğrafınız kaldırıldı."
                : "Profil fotoğrafınız başarıyla güncellendi.",
        );
        window.dispatchEvent(
            new CustomEvent("profil-fotografi-guncellendi", {
                detail: profilFoto,
            }),
        );
    };

    const profilFotografiniYukle = async (dosya: File) => {
        if (fotografGonderimKilidi.current) {
            return;
        }

        if (dosya.type !== "image/jpeg" && dosya.type !== "image/png") {
            setFotografHatasi("Yalnız JPEG veya PNG biçiminde bir fotoğraf seçin.");
            return;
        }

        if (dosya.size > 2 * 1024 * 1024) {
            setFotografHatasi("Fotoğraf en fazla 2 MB olabilir.");
            return;
        }

        fotografGonderimKilidi.current = true;
        setFotografYukleniyor(true);
        setFotografHatasi(null);
        setBasariMesaji("");

        try {
            const response = await profilFotografimiGuncelle(dosya);
            profilFotografiDegisti(response.profilFoto, "guncellendi");
        } catch (yakalananHata: unknown) {
            setFotografHatasi(
                yakalananHata instanceof ApiError && yakalananHata.message
                    ? yakalananHata.message
                    : "Profil fotoğrafı güncellenemedi. Lütfen tekrar deneyin.",
            );
        } finally {
            fotografGonderimKilidi.current = false;
            setFotografYukleniyor(false);
        }
    };

    const profilFotografiniSil = async () => {
        if (fotografGonderimKilidi.current) {
            return;
        }

        fotografGonderimKilidi.current = true;
        setFotografYukleniyor(true);
        setFotografHatasi(null);
        setBasariMesaji("");

        try {
            await profilFotografimiSil();
            profilFotografiDegisti("def_pfp.png", "silindi");
        } catch (yakalananHata: unknown) {
            setFotografHatasi(
                yakalananHata instanceof ApiError && yakalananHata.message
                    ? yakalananHata.message
                    : "Profil fotoğrafı kaldırılamadı. Lütfen tekrar deneyin.",
            );
        } finally {
            fotografGonderimKilidi.current = false;
            setFotografYukleniyor(false);
        }
    };

    return (
        <>
            <PageMeta
                title="Profilim | İntranet Portal"
                description="İntranet Portal kişisel profil bilgileri sayfası."
            />

            <PageBreadcrumb pageTitle="Profilim" />

            <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
                <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between lg:mb-7">
                    <h3 className="text-lg font-semibold text-gray-800 dark:text-white/90">
                        Profil Bilgilerim
                    </h3>

                    <div className="flex w-full flex-col gap-3 sm:w-auto sm:flex-row">
                        {profil && (
                            <button
                                type="button"
                                onClick={duzenlemeyiAc}
                                disabled={duzenleniyor}
                                className="inline-flex w-full items-center justify-center rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white shadow-theme-xs transition hover:bg-brand-600 disabled:cursor-not-allowed disabled:bg-brand-300 disabled:opacity-50 sm:w-auto"
                            >
                                {duzenleniyor
                                    ? "Bilgiler Düzenleniyor"
                                    : "Bilgilerimi Düzenle"}
                            </button>
                        )}

                        <Link
                            to="/profil/parola"
                            className="inline-flex w-full items-center justify-center rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 shadow-theme-xs transition hover:bg-gray-50 hover:text-gray-800 focus:outline-none focus:ring-3 focus:ring-brand-500/20 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-white/[0.03] dark:hover:text-gray-200 sm:w-auto"
                        >
                            Parolamı Değiştir
                        </Link>
                    </div>
                </div>

                {yukleniyor ? (
                    <div
                        role="status"
                        aria-live="polite"
                        className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-8 text-center dark:border-gray-800 dark:bg-white/[0.02]"
                    >
                        <p className="text-sm text-gray-500 dark:text-gray-400">
                            Profil bilgileri yükleniyor...
                        </p>
                    </div>
                ) : hata ? (
                    <div
                        role="alert"
                        className="rounded-xl border border-error-500 bg-error-50 p-4 dark:border-error-500/30 dark:bg-error-500/15"
                    >
                        <p className="break-words text-sm text-error-600 dark:text-error-500">
                            {hata}
                        </p>

                        <button
                            type="button"
                            onClick={tekrarDene}
                            className="mt-4 inline-flex items-center justify-center rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white shadow-theme-xs transition hover:bg-brand-600 focus:outline-none focus:ring-3 focus:ring-brand-500/20"
                        >
                            Tekrar Dene
                        </button>
                    </div>
                ) : profil ? (
                    <div className="space-y-6">
                        {basariMesaji && (
                            <div
                                role="status"
                                aria-live="polite"
                                className="rounded-xl border border-success-500/30 bg-success-50 p-4 dark:bg-success-500/15"
                            >
                                <p className="break-words text-sm text-success-600 dark:text-success-500">
                                    {basariMesaji}
                                </p>
                            </div>
                        )}

                        {fotografHatasi && (
                            <div
                                role="alert"
                                className="rounded-xl border border-error-500/30 bg-error-50 p-4 dark:bg-error-500/15"
                            >
                                <p className="break-words text-sm text-error-600 dark:text-error-500">
                                    {fotografHatasi}
                                </p>
                            </div>
                        )}

                        {duzenleniyor && (
                            <ProfilDuzenleForm
                                baslangicDegerleri={{
                                    telNo: profil.telNo,
                                    mezunOkul: profil.mezunOkul,
                                    mezunBolum: profil.mezunBolum,
                                }}
                                onIptal={duzenlemeyiKapat}
                                onProfilYenilendi={handleProfilYenilendi}
                            />
                        )}

                        <UserMetaCard
                            ad={profil.ad}
                            soyad={profil.soyad}
                            rol={profil.rol}
                            departman={profil.departman}
                            profilFoto={profil.profilFoto}
                            fotografYukleniyor={fotografYukleniyor}
                            onProfilFotografiSecildi={(dosya) =>
                                void profilFotografiniYukle(dosya)
                            }
                            onProfilFotografiniSil={() =>
                                void profilFotografiniSil()
                            }
                        />

                        <UserInfoCard
                            kullaniciAdi={profil.kullaniciAdi}
                            ePosta={profil.ePosta}
                            telNo={profil.telNo}
                            dogumTarihi={profil.dogumTarihi}
                            sicilNo={profil.sicilNo}
                            isGirisTarihi={profil.isGirisTarihi}
                            yonetici={profil.yonetici}
                            rol={profil.rol}
                            yillikIzinHakki={profil.yillikIzinHakki}
                            kalanIzin={profil.kalanIzin}
                        />

                        <UserEducationSkillsCard
                            mezunOkul={profil.mezunOkul}
                            mezunBolum={profil.mezunBolum}
                            yetenekler={profil.yetenekler}
                            onYetenekleriDuzenle={yetenekDuzenlemeyiAc}
                        />
                    </div>
                ) : null}
            </div>

            {profil && (
                <ProfilYetenekDuzenlemeModali
                    isOpen={yeteneklerDuzenleniyor}
                    mevcutYetenekIdleri={profil.yetenekler.map(
                        (yetenek) => yetenek.id,
                    )}
                    onKapat={yetenekDuzenlemeyiKapat}
                    onBasarili={yeteneklerGuncellendi}
                />
            )}
        </>
    );
}
