import { ChangeEvent, useRef } from "react";
import type { ProfilRolu } from "../../types/profil";
import { profilFotoAnahtariGecerliMi } from "../../lib/profilFoto";
import ProfilAvatar from "../common/ProfilAvatar";

type UserMetaCardProps = {
    ad: string;
    soyad: string;
    rol: ProfilRolu;
    departman: string;
    profilFoto: string | null;
    fotografYukleniyor: boolean;
    onProfilFotografiSecildi: (dosya: File) => void;
    onProfilFotografiniSil: () => void;
};

const ROL_ETIKETLERI: Record<ProfilRolu, string> = {
    ADMIN: "Admin",
    PERSONEL: "Personel",
    YONETICI: "Yönetici",
    HR: "İnsan Kaynakları",
};

export default function UserMetaCard({
                                         ad,
                                         soyad,
                                         rol,
                                         departman,
                                         profilFoto,
                                         fotografYukleniyor,
                                         onProfilFotografiSecildi,
                                         onProfilFotografiniSil,
                                     }: UserMetaCardProps) {
    const dosyaInputRef = useRef<HTMLInputElement>(null);
    const tamAd = `${ad} ${soyad}`.trim();

    const dosyaSecildi = (event: ChangeEvent<HTMLInputElement>) => {
        const dosya = event.target.files?.[0];

        if (dosya) {
            onProfilFotografiSecildi(dosya);
        }

        // Aynı dosyanın yeniden seçilebilmesi için input değeri temizlenir.
        event.target.value = "";
    };
    return (
        <div className="rounded-2xl border border-gray-200 p-5 dark:border-gray-800 lg:p-6">
            <div className="flex w-full min-w-0 flex-col items-center gap-6 xl:flex-row">
                <div className="relative shrink-0">
                    <ProfilAvatar
                        ad={ad}
                        soyad={soyad}
                        profilFoto={profilFoto}
                        boyutSinifi="h-20 w-20 text-xl"
                        fallbackSinifi="border border-gray-200 bg-gray-100 text-gray-700 dark:border-gray-800 dark:bg-gray-800 dark:text-white/90"
                        alt={`${tamAd} profil fotoğrafı`}
                    />
                    <button
                        type="button"
                        onClick={() => dosyaInputRef.current?.click()}
                        disabled={fotografYukleniyor}
                        aria-label="Profil fotoğrafını düzenle"
                        title="Profil fotoğrafını değiştir"
                        className="absolute -bottom-1 -right-1 flex h-8 w-8 items-center justify-center rounded-full border-2 border-white bg-brand-500 text-white shadow-theme-xs transition hover:bg-brand-600 focus:outline-none focus:ring-3 focus:ring-brand-500/20 disabled:cursor-wait disabled:bg-brand-300 dark:border-gray-900"
                    >
                        {fotografYukleniyor ? (
                            <span
                                aria-label="Profil fotoğrafı yükleniyor"
                                className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white/35 border-t-white"
                            />
                        ) : (
                            <svg
                                aria-hidden="true"
                                width="15"
                                height="15"
                                viewBox="0 0 24 24"
                                fill="none"
                                xmlns="http://www.w3.org/2000/svg"
                            >
                                <path
                                    d="M13.26 3.6a2.12 2.12 0 0 1 3 0l4.14 4.14a2.12 2.12 0 0 1 0 3L9.3 21.84 3 22l.16-6.3L13.26 3.6Z"
                                    stroke="currentColor"
                                    strokeWidth="1.7"
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                />
                                <path
                                    d="m11.5 5.36 7.14 7.14"
                                    stroke="currentColor"
                                    strokeWidth="1.7"
                                    strokeLinecap="round"
                                />
                            </svg>
                        )}
                    </button>
                    {profilFotoAnahtariGecerliMi(profilFoto) && (
                        <button
                            type="button"
                            onClick={onProfilFotografiniSil}
                            disabled={fotografYukleniyor}
                            aria-label="Profil fotoğrafını kaldır"
                            title="Profil fotoğrafını kaldır"
                            className="absolute -bottom-1 -left-1 flex h-8 w-8 items-center justify-center rounded-full border-2 border-white bg-error-500 text-white shadow-theme-xs transition hover:bg-error-600 focus:outline-none focus:ring-3 focus:ring-error-500/20 disabled:cursor-wait disabled:bg-error-300 dark:border-gray-900"
                        >
                            <svg
                                aria-hidden="true"
                                width="15"
                                height="15"
                                viewBox="0 0 24 24"
                                fill="none"
                                xmlns="http://www.w3.org/2000/svg"
                            >
                                <path
                                    d="M4 7h16M10 11v6m4-6v6M9 7l1-3h4l1 3m-9 0 1 13h10l1-13"
                                    stroke="currentColor"
                                    strokeWidth="1.7"
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                />
                            </svg>
                        </button>
                    )}
                    <input
                        ref={dosyaInputRef}
                        type="file"
                        accept="image/jpeg,image/png"
                        onChange={dosyaSecildi}
                        disabled={fotografYukleniyor}
                        className="sr-only"
                    />
                </div>

                <div className="w-full min-w-0 text-center xl:text-left">
                    <h4 className="mb-2 max-w-full break-words text-lg font-semibold text-gray-800 dark:text-white/90">
                        {tamAd}
                    </h4>

                    <div className="flex min-w-0 flex-col items-center gap-1 text-center xl:flex-row xl:gap-3 xl:text-left">
                        <p className="max-w-full break-words text-sm text-gray-500 dark:text-gray-400">
                            {ROL_ETIKETLERI[rol]}
                        </p>
                        <div className="hidden h-3.5 w-px shrink-0 bg-gray-300 dark:bg-gray-700 xl:block" />
                        <p className="max-w-full min-w-0 break-words text-sm text-gray-500 dark:text-gray-400">
                            {departman}
                        </p>
                    </div>
                </div>
            </div>
        </div>
    );
}
