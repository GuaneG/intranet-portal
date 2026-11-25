import type { ProfilBilgileri } from "../../types/profil";
import { gunDegeriniTurkceBicimlendir } from "../../lib/tatilTakvimi";

type UserInfoCardProps = Pick<
    ProfilBilgileri,
    | "kullaniciAdi"
    | "ePosta"
    | "telNo"
    | "dogumTarihi"
    | "sicilNo"
    | "isGirisTarihi"
    | "yonetici"
    | "rol"
    | "yillikIzinHakki"
    | "kalanIzin"
>;

const tarihBicimlendirici = new Intl.DateTimeFormat("tr-TR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    timeZone: "UTC",
});

function localDateBicimlendir(tarih: string): string {
    const eslesme = /^(\d{4})-(\d{2})-(\d{2})$/.exec(tarih.trim());

    if (!eslesme) {
        return "Belirtilmemiş";
    }

    const yil = Number(eslesme[1]);
    const ay = Number(eslesme[2]);
    const gun = Number(eslesme[3]);
    const tarihDegeri = new Date(Date.UTC(yil, ay - 1, gun));

    const gecerliMi =
        tarihDegeri.getUTCFullYear() === yil &&
        tarihDegeri.getUTCMonth() === ay - 1 &&
        tarihDegeri.getUTCDate() === gun;

    return gecerliMi
        ? tarihBicimlendirici.format(tarihDegeri)
        : "Belirtilmemiş";
}

export default function UserInfoCard({
                                         kullaniciAdi,
                                         ePosta,
                                         telNo,
                                         dogumTarihi,
                                         sicilNo,
                                         isGirisTarihi,
                                         yonetici,
                                         rol,
                                         yillikIzinHakki,
                                         kalanIzin,
                                     }: UserInfoCardProps) {
    const izinBilgileriGosterilsin = rol === "PERSONEL" || rol === "HR";
    const telNoMetni = telNo?.trim() || "Belirtilmemiş";
    const yoneticiMetni = yonetici?.trim() || "Belirtilmemiş";

    return (
        <div className="rounded-2xl border border-gray-200 p-5 dark:border-gray-800 lg:p-6">
            <h4 className="mb-6 text-lg font-semibold text-gray-800 dark:text-white/90">
                Kişisel ve İş Bilgileri
            </h4>

            <div className="grid grid-cols-1 gap-x-6 gap-y-5 lg:grid-cols-2 lg:gap-y-7">
                <div className="min-w-0">
                    <p className="mb-2 text-xs leading-normal text-gray-500 dark:text-gray-400">
                        Kullanıcı Adı
                    </p>
                    <p className="break-words text-sm font-medium text-gray-800 [overflow-wrap:anywhere] dark:text-white/90">
                        {kullaniciAdi}
                    </p>
                </div>

                <div className="min-w-0">
                    <p className="mb-2 text-xs leading-normal text-gray-500 dark:text-gray-400">
                        E-posta
                    </p>
                    <p className="break-words text-sm font-medium text-gray-800 [overflow-wrap:anywhere] dark:text-white/90">
                        {ePosta}
                    </p>
                </div>

                <div className="min-w-0">
                    <p className="mb-2 text-xs leading-normal text-gray-500 dark:text-gray-400">
                        Telefon
                    </p>
                    <p className="break-words text-sm font-medium text-gray-800 dark:text-white/90">
                        {telNoMetni}
                    </p>
                </div>

                <div className="min-w-0">
                    <p className="mb-2 text-xs leading-normal text-gray-500 dark:text-gray-400">
                        Doğum Tarihi
                    </p>
                    <p className="text-sm font-medium text-gray-800 dark:text-white/90">
                        {localDateBicimlendir(dogumTarihi)}
                    </p>
                </div>

                <div className="min-w-0">
                    <p className="mb-2 text-xs leading-normal text-gray-500 dark:text-gray-400">
                        Sicil Numarası
                    </p>
                    <p className="text-sm font-medium text-gray-800 dark:text-white/90">
                        {sicilNo}
                    </p>
                </div>

                <div className="min-w-0">
                    <p className="mb-2 text-xs leading-normal text-gray-500 dark:text-gray-400">
                        İşe Giriş Tarihi
                    </p>
                    <p className="text-sm font-medium text-gray-800 dark:text-white/90">
                        {localDateBicimlendir(isGirisTarihi)}
                    </p>
                </div>

                <div className="min-w-0">
                    <p className="mb-2 text-xs leading-normal text-gray-500 dark:text-gray-400">
                        Yönetici
                    </p>
                    <p className="break-words text-sm font-medium text-gray-800 dark:text-white/90">
                        {yoneticiMetni}
                    </p>
                </div>

                {izinBilgileriGosterilsin && (
                    <>
                        <div className="min-w-0">
                            <p className="mb-2 text-xs leading-normal text-gray-500 dark:text-gray-400">
                                Yıllık İzin Hakkı
                            </p>
                            <p className="text-sm font-medium text-gray-800 dark:text-white/90">
                                {`${gunDegeriniTurkceBicimlendir(yillikIzinHakki)} gün`}
                            </p>
                        </div>

                        <div className="min-w-0">
                            <p className="mb-2 text-xs leading-normal text-gray-500 dark:text-gray-400">
                                Kalan İzin
                            </p>
                            <p className="text-sm font-medium text-gray-800 dark:text-white/90">
                                {kalanIzin === null
                                    ? "Belirtilmemiş"
                                    : `${gunDegeriniTurkceBicimlendir(kalanIzin)} gün`}
                            </p>
                        </div>
                    </>
                )}
            </div>
        </div>
    );
}
