import { useEffect, useState } from "react";
import { profilFotografiBlobunuGetir } from "../../lib/api";
import {
    profilFotoAnahtariGecerliMi,
    profilFotoBasHarfleriniOlustur,
} from "../../lib/profilFoto";

type ProfilAvatarProps = {
    ad: string;
    soyad: string;
    profilFoto: string | null | undefined;
    boyutSinifi: string;
    fallbackSinifi?: string;
    alt?: string;
};

/**
 * Backend görselleri Authorization header istediği için dosya anahtarını URL'ye
 * doğrudan bağlamaz; güvenli API isteğinin Blob sonucunu geçici URL ile gösterir.
 */
export default function ProfilAvatar({
    ad,
    soyad,
    profilFoto,
    boyutSinifi,
    fallbackSinifi = "bg-brand-50 text-brand-500 dark:bg-brand-500/15 dark:text-brand-400",
    alt = "Profil fotoğrafı",
}: ProfilAvatarProps) {
    const [fotoUrl, setFotoUrl] = useState<string | null>(null);

    useEffect(() => {
        let aktif = true;
        let olusturulanUrl: string | null = null;

        setFotoUrl(null);

        if (!profilFotoAnahtariGecerliMi(profilFoto)) {
            return () => {
                aktif = false;
            };
        }

        void profilFotografiBlobunuGetir(profilFoto)
            .then((blob) => {
                if (!blob) {
                    return;
                }

                olusturulanUrl = URL.createObjectURL(blob);

                if (!aktif) {
                    URL.revokeObjectURL(olusturulanUrl);
                    return;
                }

                setFotoUrl(olusturulanUrl);
            })
            .catch(() => {
                // Fotoğraf okunamazsa kişisel veri veya teknik hata göstermeden avatar fallback'ine dönülür.
                if (aktif) {
                    setFotoUrl(null);
                }
            });

        return () => {
            aktif = false;

            if (olusturulanUrl) {
                URL.revokeObjectURL(olusturulanUrl);
            }
        };
    }, [profilFoto]);

    if (fotoUrl) {
        return (
            <span className={`block shrink-0 overflow-hidden rounded-full ${boyutSinifi}`}>
                <img
                    src={fotoUrl}
                    alt={alt}
                    className="h-full w-full object-cover"
                    onError={() => setFotoUrl(null)}
                />
            </span>
        );
    }

    return (
        <span
            aria-hidden="true"
            className={`flex shrink-0 items-center justify-center rounded-full font-semibold ${boyutSinifi} ${fallbackSinifi}`}
        >
            {profilFotoBasHarfleriniOlustur(ad, soyad)}
        </span>
    );
}
