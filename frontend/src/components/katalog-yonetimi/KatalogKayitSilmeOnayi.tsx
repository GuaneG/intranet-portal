import type {
    KatalogKayit,
    KatalogTuru,
} from "../../types/katalogYonetimi";
import { Modal } from "../ui/modal";

type KatalogKayitSilmeOnayiProps = {
    kayit: KatalogKayit | null;
    tur: KatalogTuru | null;
    gonderimSuruyor: boolean;
    hata: string | null;
    onKapat: () => void;
    onOnayla: () => void;
};

function katalogEtiketi(tur: KatalogTuru | null) {
    return tur === "yetenek" ? "yeteneği" : "departmanı";
}

/**
 * Katalog kaydının geri döndürülemez silinmesini açık kullanıcı onayıyla başlatır.
 */
export default function KatalogKayitSilmeOnayi({
    kayit,
    tur,
    gonderimSuruyor,
    hata,
    onKapat,
    onOnayla,
}: KatalogKayitSilmeOnayiProps) {
    return (
        <Modal
            isOpen={kayit !== null && tur !== null}
            onClose={onKapat}
            showCloseButton={!gonderimSuruyor}
            className="max-w-[480px] p-6"
        >
            <div className="px-2">
                <h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">
                    Kaydı sil
                </h2>

                <p className="mt-3 text-sm leading-6 text-gray-600 dark:text-gray-300">
                    <strong>{kayit?.ad}</strong> {katalogEtiketi(tur)}
                    silmek istediğinizden emin misiniz? Kullanımdaki kayıtlar
                    silinemez.
                </p>

                {hata && (
                    <p role="alert" className="mt-4 break-words text-sm text-error-500">
                        {hata}
                    </p>
                )}

                <div className="mt-6 flex flex-wrap justify-end gap-3">
                    <button
                        type="button"
                        onClick={onKapat}
                        disabled={gonderimSuruyor}
                        className="rounded-lg border border-gray-300 px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-white/[0.03]"
                    >
                        Vazgeç
                    </button>

                    <button
                        type="button"
                        onClick={onOnayla}
                        disabled={gonderimSuruyor}
                        className="rounded-lg bg-error-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-error-600 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                        {gonderimSuruyor ? "Siliniyor…" : "Sil"}
                    </button>
                </div>
            </div>
        </Modal>
    );
}
