import type { ProfilBilgileri } from "../../types/profil";

type UserEducationSkillsCardProps = Pick<
    ProfilBilgileri,
    "mezunOkul" | "mezunBolum" | "yetenekler"
> & {
    onYetenekleriDuzenle: () => void;
};

export default function UserEducationSkillsCard({
                                                    mezunOkul,
                                                    mezunBolum,
                                                    yetenekler,
                                                    onYetenekleriDuzenle,
                                                }: UserEducationSkillsCardProps) {
    const mezunOkulMetni = mezunOkul?.trim() || "Belirtilmemiş";
    const mezunBolumMetni = mezunBolum?.trim() || "Belirtilmemiş";

    return (
        <div className="rounded-2xl border border-gray-200 p-5 dark:border-gray-800 lg:p-6">
            <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <h4 className="text-lg font-semibold text-gray-800 dark:text-white/90">
                    Eğitim ve Yetenekler
                </h4>
                <button
                    type="button"
                    onClick={onYetenekleriDuzenle}
                    className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:bg-transparent dark:text-gray-300 dark:hover:bg-white/[0.03] sm:w-auto"
                >
                    Yetenekleri düzenle
                </button>
            </div>

            <div className="grid grid-cols-1 gap-x-6 gap-y-5 lg:grid-cols-2 lg:gap-y-7">
                <div className="min-w-0">
                    <p className="mb-2 text-xs leading-normal text-gray-500 dark:text-gray-400">
                        Mezun Olunan Okul
                    </p>
                    <p className="break-words text-sm font-medium text-gray-800 [overflow-wrap:anywhere] dark:text-white/90">
                        {mezunOkulMetni}
                    </p>
                </div>

                <div className="min-w-0">
                    <p className="mb-2 text-xs leading-normal text-gray-500 dark:text-gray-400">
                        Mezun Olunan Bölüm
                    </p>
                    <p className="break-words text-sm font-medium text-gray-800 [overflow-wrap:anywhere] dark:text-white/90">
                        {mezunBolumMetni}
                    </p>
                </div>

                <div className="min-w-0 lg:col-span-2">
                    <p className="mb-3 text-xs leading-normal text-gray-500 dark:text-gray-400">
                        Yetenekler
                    </p>

                    {yetenekler.length === 0 ? (
                        <p className="text-sm text-gray-500 dark:text-gray-400">
                            Henüz yetenek bilgisi eklenmemiş.
                        </p>
                    ) : (
                        <div className="flex flex-wrap gap-2">
                            {yetenekler.map((yetenek) => (
                                <span
                                    key={yetenek.id}
                                    className="max-w-full break-words rounded-full border border-gray-200 bg-gray-50 px-3 py-1.5 text-xs font-medium text-gray-700 [overflow-wrap:anywhere] dark:border-gray-700 dark:bg-white/[0.05] dark:text-gray-300"
                                >
                  {yetenek.ad}
                </span>
                            ))}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
