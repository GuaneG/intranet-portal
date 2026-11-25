import type { AuthRole } from "../../context/AuthContext";
import type { PersonelYonetimDetayi } from "../../types/personelYonetim";
import Badge from "../ui/badge/Badge";
import ProfilAvatar from "../common/ProfilAvatar";

type LookupAlanGorunumu = {
    metin: string;
    yukleniyor: boolean;
    hata: string | null;
    tekrarDenemeDevreDisi: boolean;
    onTekrarDene: () => void;
};

type PersonelYonetimDetayBilgileriProps = {
    detay: PersonelYonetimDetayi;
    departman: LookupAlanGorunumu;
    yonetici: LookupAlanGorunumu;
};

const ROL_GORUNUMU = {
    PERSONEL: {
        etiket: "Personel",
        renk: "light",
    },
    YONETICI: {
        etiket: "Yönetici",
        renk: "info",
    },
    HR: {
        etiket: "İnsan Kaynakları",
        renk: "warning",
    },
    ADMIN: {
        etiket: "Admin",
        renk: "primary",
    },
} as const satisfies Record<
    AuthRole,
    {
        etiket: string;
        renk: "primary" | "warning" | "info" | "light";
    }
>;

/**
 * YYYY-MM-DD tarihini parçalara ayırarak saat dilimi nedeniyle
 * bir gün ileri veya geri kaymasını engeller.
 */
function tarihiFormatla(tarih: string) {
    const [yil, ay, gun] = tarih.split("-").map(Number);

    return new Intl.DateTimeFormat("tr-TR").format(
        new Date(yil, ay - 1, gun),
    );
}

/**
 * Boş veya null alanların kullanıcıya tutarlı gösterilmesini sağlar.
 */
function metinVeyaBelirtilmemis(deger: string | number | null) {
    if (typeof deger === "string") {
        return deger.trim() || "Belirtilmemiş";
    }

    return deger ?? "Belirtilmemiş";
}

/**
 * Departman ve yönetici lookup alanlarının loading, error ve
 * başarılı değerlerini aynı görsel yapıda sunar.
 */
function LookupDegeri({
                          alanAdi,
                          gorunum,
                      }: {
    alanAdi: string;
    gorunum: LookupAlanGorunumu;
}) {
    if (gorunum.yukleniyor) {
        return (
            <span
                role="status"
                aria-live="polite"
                className="text-gray-500 dark:text-gray-400"
            >
            Yükleniyor…
            </span>
        );
    }

    if (gorunum.hata) {
        return (
            <div
                role="alert"
                className="flex flex-wrap items-center gap-3"
                >
                <span className="text-error-600 dark:text-error-400">
                    {gorunum.hata}
                </span>

                <button
                    type="button"
                    onClick={gorunum.onTekrarDene}
                    disabled={gorunum.tekrarDenemeDevreDisi}
                    aria-label={`${alanAdi} bilgisi için tekrar dene`}
                    className="text-sm font-medium text-brand-500 hover:text-brand-600 disabled:cursor-not-allowed disabled:opacity-50 dark:text-brand-400 dark:hover:text-brand-300"
                >
                    Tekrar dene
                </button>
            </div>
        );
    }

    return (
        <span aria-live="polite">
            {metinVeyaBelirtilmemis(gorunum.metin)}
        </span>
    );
}

function BilgiGrubu({
                        baslik,
                        children,
                    }: {
    baslik: string;
    children: React.ReactNode;
}) {
    const baslikId =
        `${baslik
            .trim()
            .toLocaleLowerCase("tr-TR")
            .replace(/\s+/g, "-")}-baslik`;

    return (
        <section
            aria-labelledby={baslikId}
            className="rounded-xl border border-gray-200 bg-white p-5 dark:border-white/[0.05] dark:bg-white/[0.03]"
        >
            <h2
                id={baslikId}
                className="mb-4 text-base font-semibold text-gray-800 dark:text-white/90"
            >
                {baslik}
            </h2>

            <dl className="space-y-4">
                {children}
            </dl>
        </section>
    );
}

function BilgiSatiri({
                         etiket,
                         children,
                     }: {
    etiket: string;
    children: React.ReactNode;
}) {
    return (
        <div>
            <dt className="text-theme-xs font-medium text-gray-500 dark:text-gray-400">
                {etiket}
            </dt>

            <dd className="mt-1 break-words text-sm text-gray-800 dark:text-white/90">
                {children}
            </dd>
        </div>
    );
}

export default function PersonelYonetimDetayBilgileri({
                                                          detay,
                                                          departman,
                                                          yonetici,
                                                      }: PersonelYonetimDetayBilgileriProps) {
    const rolGorunumu = ROL_GORUNUMU[detay.rol];
    const tamAd =
        `${detay.ad.trim()} ${detay.soyad.trim()}`.trim();

    return (
        <div className="space-y-6">
            <section className="rounded-xl border border-gray-200 bg-white p-5 dark:border-white/[0.05] dark:bg-white/[0.03] sm:p-6">
                <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
                    <ProfilAvatar
                        ad={detay.ad}
                        soyad={detay.soyad}
                        profilFoto={detay.profilFoto}
                        boyutSinifi="h-16 w-16 text-lg"
                        alt={`${tamAd} profil fotoğrafı`}
                    />

                    <div className="min-w-0">
                        <h1 className="truncate text-xl font-semibold text-gray-800 dark:text-white/90">
                            {metinVeyaBelirtilmemis(tamAd)}
                        </h1>

                        <p className="mt-1 truncate text-sm text-gray-500 dark:text-gray-400">
                            @{metinVeyaBelirtilmemis(detay.kullaniciAdi)}
                        </p>

                        <div className="mt-3 flex flex-wrap gap-2">
                            <Badge
                                size="sm"
                                color={rolGorunumu.renk}
                            >
                                {rolGorunumu.etiket}
                            </Badge>

                            <Badge
                                size="sm"
                                color={
                                    detay.aktif
                                        ? "success"
                                        : "error"
                                }
                            >
                                {detay.aktif ? "Aktif" : "Pasif"}
                            </Badge>
                        </div>
                    </div>
                </div>
            </section>

            <div className="grid gap-6 xl:grid-cols-3">
                <BilgiGrubu baslik="İletişim">
                    <BilgiSatiri etiket="E-posta">
                        {metinVeyaBelirtilmemis(detay.ePosta)}
                    </BilgiSatiri>

                    <BilgiSatiri etiket="Telefon">
                        {metinVeyaBelirtilmemis(detay.telNo)}
                    </BilgiSatiri>

                    <BilgiSatiri etiket="Doğum tarihi">
                        {tarihiFormatla(detay.dogumTarihi)}
                    </BilgiSatiri>
                </BilgiGrubu>

                <BilgiGrubu baslik="İş bilgileri">
                    <BilgiSatiri etiket="Departman">
                        <LookupDegeri
                            alanAdi="Departman"
                            gorunum={departman}
                        />
                    </BilgiSatiri>

                    <BilgiSatiri etiket="Yönetici">
                        <LookupDegeri
                            alanAdi="Yönetici"
                            gorunum={yonetici}
                        />
                    </BilgiSatiri>

                    <BilgiSatiri etiket="İşe giriş tarihi">
                        {tarihiFormatla(detay.isGirisTarihi)}
                    </BilgiSatiri>
                </BilgiGrubu>

                <BilgiGrubu baslik="Eğitim">
                    <BilgiSatiri etiket="Okul">
                        {metinVeyaBelirtilmemis(
                            detay.mezunOkul,
                        )}
                    </BilgiSatiri>

                    <BilgiSatiri etiket="Bölüm">
                        {metinVeyaBelirtilmemis(
                            detay.mezunBolum,
                        )}
                    </BilgiSatiri>

                    <BilgiSatiri etiket="Mezuniyet yılı">
                        {metinVeyaBelirtilmemis(
                            detay.mezuniyetYili,
                        )}
                    </BilgiSatiri>
                </BilgiGrubu>

                <BilgiGrubu baslik="Yetenekler">
                    <BilgiSatiri etiket="Atanmış yetenekler">
                        {detay.yetenekler.length === 0 ? (
                            "Belirtilmemiş"
                        ) : (
                            <div className="flex flex-wrap gap-2">
                                {detay.yetenekler.map((yetenek) => (
                                    <span
                                        key={yetenek.id}
                                        className="max-w-full break-words rounded-full border border-gray-200 bg-gray-50 px-3 py-1.5 text-xs font-medium text-gray-700 [overflow-wrap:anywhere] dark:border-gray-700 dark:bg-white/[0.05] dark:text-gray-300"
                                    >
                                        {yetenek.ad}
                                    </span>
                                ))}
                            </div>
                        )}
                    </BilgiSatiri>
                </BilgiGrubu>
            </div>
        </div>
    );
}
