import { useRef, useState } from "react";
import { Modal } from "../ui/modal";
import { useModal } from "../../hooks/useModal";
import {
    ApiError,
    personelAktiflestir,
} from "../../lib/api";
import type { PersonelYonetimDetayi } from "../../types/personelYonetim";

type PersonelAktiflestirmeOnayiProps = {
    detay: PersonelYonetimDetayi;
    onBasarili: () => void;
};

/**
 * Pasif personelin yeniden erişebilir hâle gelmesi için
 * kullanıcıdan açık onay alır.
 */
export default function PersonelAktiflestirmeOnayi({
                                                       detay,
                                                       onBasarili,
                                                   }: PersonelAktiflestirmeOnayiProps) {
    const {
        isOpen,
        openModal,
        closeModal,
    } = useModal();

    const [
        aktiflestirmeSuruyor,
        setAktiflestirmeSuruyor,
    ] = useState(false);
    const [hata, setHata] = useState<string | null>(null);

    // State güncellenmeden gelen hızlı ikinci tetiklemeyi de engeller.
    const aktiflestirmeKilidi = useRef(false);

    // Aktif kayıt için tekrar aktifleştirme aksiyonu sunulmaz.
    if (detay.aktif) {
        return null;
    }

    function modaliKapat() {
        // Ref, React yeniden render etmeden önce başlayan isteği de yakalar.
        if (aktiflestirmeKilidi.current || aktiflestirmeSuruyor) {
            return;
        }

        setHata(null);
        closeModal();
    }

    function onayiAc() {
        setHata(null);
        openModal();
    }

    async function aktiflestirmeyiOnayla() {
        if (aktiflestirmeKilidi.current) {
            return;
        }

        aktiflestirmeKilidi.current = true;
        setAktiflestirmeSuruyor(true);
        setHata(null);

        try {
            await personelAktiflestir(detay.personelId);
            onBasarili();
        } catch (error) {
            setHata(
                error instanceof ApiError
                    ? error.message
                    : "Personel aktifleştirilirken beklenmeyen bir hata oluştu.",
            );
        } finally {
            aktiflestirmeKilidi.current = false;
            setAktiflestirmeSuruyor(false);
        }
    }

    return (
        <>
            <button
                type="button"
                onClick={onayiAc}
                className="rounded-lg bg-success-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-success-600"
            >
                Aktifleştir
            </button>

            <Modal
                isOpen={isOpen}
                onClose={modaliKapat}
                showCloseButton={!aktiflestirmeSuruyor}
                className="max-w-[520px] p-6"
            >
                <div className="px-2">
                    <h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">
                        Personeli aktifleştir
                    </h2>

                    <p className="mt-3 text-sm leading-6 text-gray-600 dark:text-gray-300">
                        <strong>
                            {detay.ad} {detay.soyad}
                        </strong>{" "}
                        adlı personeli yeniden aktifleştirmek
                        istediğinizden emin misiniz?
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
                            onClick={modaliKapat}
                            disabled={aktiflestirmeSuruyor}
                            className="rounded-lg border border-gray-300 px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-white/[0.03]"
                        >
                            Vazgeç
                        </button>

                        <button
                            type="button"
                            onClick={() =>
                                void aktiflestirmeyiOnayla()
                            }
                            disabled={aktiflestirmeSuruyor}
                            className="rounded-lg bg-success-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-success-600 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            {aktiflestirmeSuruyor
                                ? "Aktifleştiriliyor…"
                                : "Aktifleştir"}
                        </button>
                    </div>
                </div>
            </Modal>
        </>
    );
}