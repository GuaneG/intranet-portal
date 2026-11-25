import { Link } from "react-router";
import { useAuth } from "../../context/AuthContext";
import { GroupIcon } from "../../icons";
import type { EkipDiziniKisi } from "../../types/ekipDizini";
import Badge from "../ui/badge/Badge";
import ProfilAvatar from "../common/ProfilAvatar";
import { bugunIzinEtiketi, bugunIzinRozetRengi } from "../../lib/dashboardIzinBakiyesi";

type EkipDiziniOzetiKartiProps = {
    aktifEkipUyesiSayisi: number | null;
    kisiler: EkipDiziniKisi[];
    yukleniyor: boolean;
};

/**
 * Dashboard response'undaki sıralamayı değiştirmeden ilk beş görünür ekip üyesini gösterir.
 */
export default function EkipDiziniOzetiKarti({
    aktifEkipUyesiSayisi,
    kisiler,
    yukleniyor,
}: EkipDiziniOzetiKartiProps) {
    const { kullanici } = useAuth();
    const gosterilenKisiler = kisiler.slice(0, 5);
    const sayi = aktifEkipUyesiSayisi ?? 0;
    const yoneticiEtiketiGorunur =
        kullanici?.rol === "PERSONEL" || kullanici?.rol === "HR";

    return (
        <Link
            to="/ekip-dizini"
            aria-label="Ekip dizinini aç"
            className="group block rounded-2xl border border-gray-200 bg-white p-5 transition-colors hover:border-brand-300 hover:bg-brand-50/30 focus:outline-none focus:ring-3 focus:ring-brand-500/10 dark:border-gray-800 dark:bg-white/[0.03] dark:hover:border-brand-500/50 dark:hover:bg-brand-500/5 md:p-6"
        >
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                    <h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">
                        Ekip özeti
                    </h2>
                    <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                        Görüntüleyebildiğin ekipten ilk kişiler
                    </p>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                    <div className="flex items-center gap-2.5 rounded-xl bg-brand-50 px-3 py-2 dark:bg-brand-500/10">
                        <GroupIcon className="size-5 text-brand-500" />
                        <div>
                            <p className="text-xs text-gray-500 dark:text-gray-400">
                                Ekip kişi sayısı
                            </p>
                            {yukleniyor ? (
                                <span
                                    aria-label="Yükleniyor"
                                    className="mt-1 block h-5 w-9 animate-pulse rounded bg-brand-100 dark:bg-brand-500/20"
                                />
                            ) : (
                                <p className="text-lg font-semibold leading-5 text-gray-800 dark:text-white/90">
                                    {new Intl.NumberFormat("tr-TR").format(sayi)}
                                </p>
                            )}
                        </div>
                    </div>
                </div>
            </div>

            {yukleniyor ? (
                <ul
                    aria-label="Ekip özeti yükleniyor"
                    className="mt-5 divide-y divide-gray-100 overflow-hidden rounded-xl border border-gray-100 dark:divide-white/[0.05] dark:border-white/[0.05]"
                >
                    {Array.from({ length: 5 }, (_, index) => (
                        <li
                            key={index}
                            aria-hidden="true"
                            className="flex animate-pulse items-center gap-3 px-4 py-3"
                        >
                            <div className="h-10 w-10 rounded-full bg-gray-100 dark:bg-white/[0.08]" />
                            <div className="min-w-0 flex-1">
                                <div className="h-3 w-3/4 rounded bg-gray-100 dark:bg-white/[0.08]" />
                                <div className="mt-2 h-3 w-1/2 rounded bg-gray-100 dark:bg-white/[0.08]" />
                            </div>
                        </li>
                    ))}
                </ul>
            ) : gosterilenKisiler.length === 0 ? (
                <p className="mt-5 rounded-xl bg-gray-50 p-4 text-sm text-gray-500 dark:bg-white/[0.04] dark:text-gray-400">
                    Görüntüleyebileceğin aktif ekip üyesi bulunmuyor.
                </p>
            ) : (
                <ul className="mt-5 divide-y divide-gray-100 overflow-hidden rounded-xl border border-gray-100 dark:divide-white/[0.05] dark:border-white/[0.05]">
                    {gosterilenKisiler.map((kisi, index) => (
                        <li
                            key={`${kisi.ad}-${kisi.soyad}-${index}`}
                            className="flex flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
                        >
                            <div className="flex items-center gap-3">
                                <ProfilAvatar
                                    ad={kisi.ad}
                                    soyad={kisi.soyad}
                                    profilFoto={kisi.profilFoto}
                                    boyutSinifi="h-10 w-10 text-sm"
                                    alt={`${kisi.ad} ${kisi.soyad} profil fotoğrafı`}
                                />
                                <div className="min-w-0">
                                    <div className="flex flex-wrap items-center gap-1.5">
                                        <p className="truncate text-sm font-medium text-gray-800 dark:text-white/90">
                                            {kisi.ad} {kisi.soyad}
                                        </p>
                                        {yoneticiEtiketiGorunur &&
                                            kisi.yoneticinMi && (
                                                <Badge color="primary" size="sm">
                                                    Yöneticin
                                                </Badge>
                                            )}
                                        {bugunIzinEtiketi(kisi.bugunIzinDurumu) && (
                                            <Badge color={bugunIzinRozetRengi(kisi.bugunIzinDurumu)} size="sm">
                                                {bugunIzinEtiketi(kisi.bugunIzinDurumu)}
                                            </Badge>
                                        )}
                                    </div>
                                    <p className="truncate text-xs text-gray-500 dark:text-gray-400">
                                        {kisi.departman}
                                    </p>
                                </div>
                            </div>

                            {kisi.yetenekler.length > 0 && (
                                <div className="flex flex-wrap gap-1.5 sm:justify-end">
                                    {kisi.yetenekler.map((yetenek) => (
                                        <span
                                            key={yetenek.id}
                                            className="rounded-full bg-gray-50 px-2 py-1 text-xs text-gray-600 dark:bg-white/[0.05] dark:text-gray-300"
                                        >
                                            {yetenek.ad}
                                        </span>
                                    ))}
                                </div>
                            )}
                        </li>
                    ))}
                </ul>
            )}
        </Link>
    );
}
