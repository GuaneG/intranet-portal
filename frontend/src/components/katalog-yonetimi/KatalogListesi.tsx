import type {
    KatalogKayit,
    KatalogTuru,
} from "../../types/katalogYonetimi";

type KatalogListesiProps = {
    tur: KatalogTuru;
    kayitlar: KatalogKayit[];
    yukleniyor: boolean;
    islemSuruyor: boolean;
    onDuzenle: (kayit: KatalogKayit) => void;
    onSil: (kayit: KatalogKayit) => void;
};

function katalogEtiketi(tur: KatalogTuru) {
    return tur === "departman" ? "departman" : "yetenek";
}

/**
 * Katalog kayıtlarının görüntüsünden sorumludur; veri alma ve yazma sayfada kalır.
 */
export default function KatalogListesi({
    tur,
    kayitlar,
    yukleniyor,
    islemSuruyor,
    onDuzenle,
    onSil,
}: KatalogListesiProps) {
    if (yukleniyor) {
        return (
            <div
                role="status"
                className="rounded-xl border border-gray-200 bg-white p-8 text-center text-sm text-gray-500 dark:border-white/[0.05] dark:bg-white/[0.03] dark:text-gray-400"
            >
                {katalogEtiketi(tur)} listesi yükleniyor…
            </div>
        );
    }

    if (kayitlar.length === 0) {
        return (
            <div className="rounded-xl border border-gray-200 bg-white p-8 text-center dark:border-white/[0.05] dark:bg-white/[0.03]">
                <p className="text-sm font-medium text-gray-800 dark:text-white/90">
                    Henüz {katalogEtiketi(tur)} bulunmuyor.
                </p>
                <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                    Yukarıdaki formu kullanarak ilk kaydı ekleyebilirsiniz.
                </p>
            </div>
        );
    }

    return (
        <ul className="divide-y divide-gray-100 overflow-hidden rounded-xl border border-gray-200 bg-white dark:divide-white/[0.05] dark:border-white/[0.05] dark:bg-white/[0.03]">
            {kayitlar.map((kayit) => (
                <li
                    key={kayit.id}
                    className="flex items-center justify-between gap-4 px-4 py-3 sm:px-5"
                >
                    <span className="min-w-0 break-words text-sm font-medium text-gray-800 dark:text-white/90">
                        {kayit.ad}
                    </span>
                    <div className="flex shrink-0 flex-wrap justify-end gap-2">
                        <button
                            type="button"
                            onClick={() => onDuzenle(kayit)}
                            disabled={islemSuruyor}
                            className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-transparent dark:text-gray-300 dark:hover:bg-white/[0.03]"
                        >
                            Düzenle
                        </button>
                        <button
                            type="button"
                            onClick={() => onSil(kayit)}
                            disabled={islemSuruyor}
                            className="rounded-lg border border-error-200 bg-error-50 px-3 py-2 text-sm font-medium text-error-600 hover:bg-error-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-error-500/20 dark:bg-error-500/10 dark:text-error-400 dark:hover:bg-error-500/15"
                        >
                            Sil
                        </button>
                    </div>
                </li>
            ))}
        </ul>
    );
}
