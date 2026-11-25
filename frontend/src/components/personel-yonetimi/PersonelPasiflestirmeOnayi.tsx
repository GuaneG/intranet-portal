import { useRef, useState } from "react";
import { Modal } from "../ui/modal";
import { useModal } from "../../hooks/useModal";
import {
    ApiError,
    personelPasiflestir,
} from "../../lib/api";
import type { PersonelYonetimDetayi } from "../../types/personelYonetim";

type PersonelPasiflestirmeOnayiProps = {
    detay: PersonelYonetimDetayi;
    onBasarili: () => void;
};

/**
 * Geri alınması kolay olmayan pasifleştirme işlemi için
 * kullanıcıdan açık onay alır.
 */
export default function PersonelPasiflestirmeOnayi({
                                                       detay,
                                                       onBasarili,
                                                   }: PersonelPasiflestirmeOnayiProps) {
    const {isOpen, openModal, closeModal,} = useModal();

    const [pasiflestirmeSuruyor, setPasiflestirmeSuruyor,] = useState(false);
    const [hata, setHata] = useState<string | null>(null);
    // State güncellenmeden gelen hızlı ikinci tetiklemeyi de engeller.
    const pasiflestirmeKilidi = useRef(false);

    // Zaten pasif kayıt için yeniden işlem başlatılmaz.
    if (!detay.aktif) {
        return null;
    }

    function modalıKapat() {
        // Ref, React yeniden render etmeden önce başlayan isteği de yakalar.
        if (
            pasiflestirmeKilidi.current ||
            pasiflestirmeSuruyor
        ) {
            return;
        }

        setHata(null);
        closeModal();
    }

    function onayiAc() {
        setHata(null);
        openModal();
    }

    async function pasiflestirmeyiOnayla() {
        if (pasiflestirmeKilidi.current) {
            return;
        }

        pasiflestirmeKilidi.current = true;
        setPasiflestirmeSuruyor(true);
        setHata(null);

        try {
            await personelPasiflestir(detay.personelId);
            onBasarili();
        } catch (error) {
            setHata(
                error instanceof ApiError
                    ? error.message
                    : "Personel pasifleştirilirken beklenmeyen bir hata oluştu.",
            );
        } finally {
            pasiflestirmeKilidi.current = false;
            setPasiflestirmeSuruyor(false);
        }
    }

    return (
        <>
            <button
                type="button"
                onClick={onayiAc}
                className="rounded-lg bg-error-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-error-600"
            >
                Pasifleştir
            </button>

            <Modal
                isOpen={isOpen}
                onClose={modalıKapat}
                showCloseButton={!pasiflestirmeSuruyor}
                className="max-w-[520px] p-6"
            >
                <div className="px-2">
                    <h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">
                        Personeli pasifleştir
                    </h2>

                    <p className="mt-3 text-sm leading-6 text-gray-600 dark:text-gray-300">
                        <strong>
                            {detay.ad} {detay.soyad}
                        </strong>{" "}
                        pasif duruma alınacak. Mevcut oturumları
                        sonlandırılacak.
                    </p>

                    {hata && (
                        <p
                            role="alert"
                            className="mt-4 text-sm text-error-500"
                        >
                            {hata}
                        </p>
                    )}

                    <div className="mt-6 flex flex-wrap justify-end gap-3">
                        <button
                            type="button"
                            onClick={modalıKapat}
                            disabled={pasiflestirmeSuruyor}
                            className="rounded-lg border border-gray-300 px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-white/[0.03]"
                        >
                            Vazgeç
                        </button>

                        <button
                            type="button"
                            onClick={() =>
                                void pasiflestirmeyiOnayla()
                            }
                            disabled={pasiflestirmeSuruyor}
                            className="rounded-lg bg-error-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-error-600 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            {pasiflestirmeSuruyor
                                ? "Pasifleştiriliyor…"
                                : "Pasifleştir"}
                        </button>
                    </div>
                </div>
            </Modal>
        </>
    );
}