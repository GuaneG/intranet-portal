import { useEffect, useRef, useState } from "react";
import {
    ApiError,
    profilYetenekleriniGuncelle,
    yetenekleriGetir,
} from "../../lib/api";
import type { LookupSecenegi } from "../../types/personelYonetim";
import { Modal } from "../ui/modal";

type ProfilYetenekDuzenlemeModaliProps = {
    isOpen: boolean;
    mevcutYetenekIdleri: number[];
    onKapat: () => void;
    onBasarili: () => Promise<void>;
    onYetenekleriKaydet?: (yetenekIdleri: number[]) => Promise<void>;
    baslik?: string;
    aciklama?: string;
    alanBasligi?: string;
};

function hataMesajiniGetir(error: unknown, varsayilanMesaj: string) {
    if (!(error instanceof ApiError)) {
        return varsayilanMesaj;
    }

    if (error.status === 0) {
        return "Sunucuya ulaşılamadı. Bağlantınızı kontrol edip tekrar deneyin.";
    }

    if (error.status >= 500) {
        return varsayilanMesaj;
    }

    return error.message || varsayilanMesaj;
}

/**
 * Katalogdaki yetenek kümesini seçtirir; varsayılan olarak kullanıcının kendi profilini günceller.
 */
export default function ProfilYetenekDuzenlemeModali({
    isOpen,
    mevcutYetenekIdleri,
    onKapat,
    onBasarili,
    onYetenekleriKaydet = profilYetenekleriniGuncelle,
    baslik = "Yetenekleri düzenle",
    aciklama = "Sahip olduğunuz yetenekleri seçin. Seçimi kaldırmak için ilgili kutunun işaretini kaldırabilirsiniz.",
    alanBasligi = "Yetenekleriniz",
}: ProfilYetenekDuzenlemeModaliProps) {
    const [yetenekler, setYetenekler] = useState<LookupSecenegi[]>([]);
    const [secilenIdler, setSecilenIdler] = useState<number[]>([]);
    const [yukleniyor, setYukleniyor] = useState(false);
    const [lookupHatasi, setLookupHatasi] = useState<string | null>(null);
    const [kayitHatasi, setKayitHatasi] = useState<string | null>(null);
    const [gonderimSuruyor, setGonderimSuruyor] = useState(false);
    const gonderimKilidi = useRef(false);
    const istekNumarasi = useRef(0);

    useEffect(() => {
        if (!isOpen) {
            return;
        }

        const mevcutIstekNumarasi = ++istekNumarasi.current;

        setSecilenIdler(mevcutYetenekIdleri);
        setYukleniyor(true);
        setLookupHatasi(null);
        setKayitHatasi(null);

        async function kataloguYukle() {
            try {
                const sonuc = await yetenekleriGetir();

                if (istekNumarasi.current === mevcutIstekNumarasi) {
                    setYetenekler(sonuc);
                }
            } catch (error) {
                if (istekNumarasi.current === mevcutIstekNumarasi) {
                    setYetenekler([]);
                    setLookupHatasi(
                        hataMesajiniGetir(
                            error,
                            "Yetenek seçenekleri alınamadı. Lütfen tekrar deneyin.",
                        ),
                    );
                }
            } finally {
                if (istekNumarasi.current === mevcutIstekNumarasi) {
                    setYukleniyor(false);
                }
            }
        }

        void kataloguYukle();
    }, [isOpen, mevcutYetenekIdleri]);

    function kapat() {
        if (gonderimSuruyor || gonderimKilidi.current) {
            return;
        }

        onKapat();
    }

    function secimiDegistir(yetenekId: number) {
        if (gonderimSuruyor) {
            return;
        }

        setSecilenIdler((onceki) =>
            onceki.includes(yetenekId)
                ? onceki.filter((id) => id !== yetenekId)
                : [...onceki, yetenekId],
        );
    }

    async function kaydet() {
        if (gonderimSuruyor || gonderimKilidi.current) {
            return;
        }

        gonderimKilidi.current = true;
        setGonderimSuruyor(true);
        setKayitHatasi(null);

        try {
            await onYetenekleriKaydet(secilenIdler);
            await onBasarili();
            onKapat();
        } catch (error) {
            setKayitHatasi(
                hataMesajiniGetir(
                    error,
                    "Yetenekler güncellenemedi. Lütfen tekrar deneyin.",
                ),
            );
        } finally {
            gonderimKilidi.current = false;
            setGonderimSuruyor(false);
        }
    }

    function tekrarDene() {
        if (gonderimSuruyor) {
            return;
        }

        setLookupHatasi(null);
        setYukleniyor(true);
        const sonrakiIstekNumarasi = ++istekNumarasi.current;

        void yetenekleriGetir()
            .then((sonuc) => {
                if (istekNumarasi.current === sonrakiIstekNumarasi) {
                    setYetenekler(sonuc);
                }
            })
            .catch((error: unknown) => {
                if (istekNumarasi.current === sonrakiIstekNumarasi) {
                    setYetenekler([]);
                    setLookupHatasi(
                        hataMesajiniGetir(
                            error,
                            "Yetenek seçenekleri alınamadı. Lütfen tekrar deneyin.",
                        ),
                    );
                }
            })
            .finally(() => {
                if (istekNumarasi.current === sonrakiIstekNumarasi) {
                    setYukleniyor(false);
                }
            });
    }

    return (
        <Modal
            isOpen={isOpen}
            onClose={kapat}
            showCloseButton={!gonderimSuruyor}
            className="max-w-[620px] p-6"
        >
            <div className="px-2">
                <h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">
                    {baslik}
                </h2>
                <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">
                    {aciklama}
                </p>

                <div className="mt-5">
                    {yukleniyor ? (
                        <p
                            role="status"
                            className="rounded-xl bg-gray-50 p-4 text-sm text-gray-500 dark:bg-white/[0.04] dark:text-gray-400"
                        >
                            Yetenek seçenekleri yükleniyor…
                        </p>
                    ) : lookupHatasi ? (
                        <div
                            role="alert"
                            className="rounded-xl bg-error-50 p-4 text-sm text-error-600 dark:bg-error-500/10 dark:text-error-400"
                        >
                            <p>{lookupHatasi}</p>
                            <button
                                type="button"
                                onClick={tekrarDene}
                                className="mt-3 font-medium underline"
                            >
                                Tekrar dene
                            </button>
                        </div>
                    ) : yetenekler.length === 0 ? (
                        <p className="rounded-xl bg-gray-50 p-4 text-sm text-gray-500 dark:bg-white/[0.04] dark:text-gray-400">
                            Seçilebilecek yetenek bulunmuyor. Bu durumda boş
                            seçimi kaydedebilirsiniz.
                        </p>
                    ) : (
                        <fieldset>
                            <legend className="mb-3 text-sm font-medium text-gray-700 dark:text-gray-300">
                                {alanBasligi}
                            </legend>
                            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                                {yetenekler.map((yetenek) => {
                                    const secili = secilenIdler.includes(
                                        yetenek.id,
                                    );

                                    return (
                                        <label
                                            key={yetenek.id}
                                            className="flex cursor-pointer items-center gap-3 rounded-lg border border-gray-200 px-3 py-2.5 text-sm text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-white/[0.03]"
                                        >
                                            <input
                                                type="checkbox"
                                                checked={secili}
                                                disabled={gonderimSuruyor}
                                                onChange={() =>
                                                    secimiDegistir(yetenek.id)
                                                }
                                                className="size-4 rounded border-gray-300 text-brand-500 focus:ring-brand-500/20"
                                            />
                                            <span className="min-w-0 break-words">
                                                {yetenek.ad}
                                            </span>
                                        </label>
                                    );
                                })}
                            </div>
                        </fieldset>
                    )}
                </div>

                {kayitHatasi && (
                    <p role="alert" className="mt-4 text-sm text-error-500">
                        {kayitHatasi}
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
                        disabled={yukleniyor || lookupHatasi !== null || gonderimSuruyor}
                        className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                        {gonderimSuruyor ? "Kaydediliyor…" : "Kaydet"}
                    </button>
                </div>
            </div>
        </Modal>
    );
}
