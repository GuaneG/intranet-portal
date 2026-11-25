import { useEffect, useRef, useState } from "react";
import { Modal } from "../ui/modal";
import type {
    KatalogKayit,
    KatalogTuru,
} from "../../types/katalogYonetimi";

type KatalogKayitDuzenlemeModaliProps = {
    tur: KatalogTuru;
    kayit: KatalogKayit | null;
    gonderimSuruyor: boolean;
    hata: string | null;
    onKapat: () => void;
    onKaydet: (ad: string) => Promise<void>;
};

function alanEtiketi(tur: KatalogTuru) {
    return tur === "departman" ? "Departman adı" : "Yetenek adı";
}

/**
 * Tek bir katalog kaydının yeniden adlandırılmasını açık onayla başlatır.
 */
export default function KatalogKayitDuzenlemeModali({
    tur,
    kayit,
    gonderimSuruyor,
    hata,
    onKapat,
    onKaydet,
}: KatalogKayitDuzenlemeModaliProps) {
    const [ad, setAd] = useState("");
    const gonderimKilidi = useRef(false);
    const trimlenmisAd = ad.trim();
    const modalAcik = kayit !== null;

    useEffect(() => {
        setAd(kayit?.ad ?? "");
        gonderimKilidi.current = false;
    }, [kayit]);

    function kapat() {
        if (gonderimSuruyor || gonderimKilidi.current) {
            return;
        }

        onKapat();
    }

    async function kaydet() {
        if (!trimlenmisAd || gonderimSuruyor || gonderimKilidi.current) {
            return;
        }

        gonderimKilidi.current = true;

        try {
            await onKaydet(trimlenmisAd);
        } finally {
            gonderimKilidi.current = false;
        }
    }

    return (
        <Modal
            isOpen={modalAcik}
            onClose={kapat}
            showCloseButton={!gonderimSuruyor}
            className="max-w-[520px] p-6"
        >
            <div className="px-2">
                <h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">
                    {alanEtiketi(tur)} düzenle
                </h2>

                <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">
                    Güncel adı girip kaydederek katalog kaydını yeniden
                    adlandırabilirsiniz.
                </p>

                <div className="mt-5">
                    <label
                        htmlFor="katalog-kayit-adi"
                        className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300"
                    >
                        {alanEtiketi(tur)}
                    </label>
                    <input
                        id="katalog-kayit-adi"
                        value={ad}
                        disabled={gonderimSuruyor}
                        onChange={(event) => setAd(event.target.value)}
                        className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"
                    />
                </div>

                {hata && (
                    <p role="alert" className="mt-4 text-sm text-error-500">
                        {hata}
                    </p>
                )}

                <div className="mt-6 flex flex-wrap justify-end gap-3">
                    <button
                        type="button"
                        onClick={kapat}
                        disabled={gonderimSuruyor}
                        className="rounded-lg border border-gray-300 px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-white/[0.03]"
                    >
                        Vazgeç
                    </button>
                    <button
                        type="button"
                        onClick={() => void kaydet()}
                        disabled={gonderimSuruyor || !trimlenmisAd}
                        className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                        {gonderimSuruyor ? "Kaydediliyor…" : "Kaydet"}
                    </button>
                </div>
            </div>
        </Modal>
    );
}
