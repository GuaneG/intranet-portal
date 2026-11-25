import { useRef, useState } from "react";
import type { AuthRole } from "../../context/AuthContext";
import { ApiError, rolDegistir } from "../../lib/api";
import type { PersonelYonetimDetayi } from "../../types/personelYonetim";
import { useModal } from "../../hooks/useModal";
import { Modal } from "../ui/modal";

type AtanabilirRol = Exclude<AuthRole, "ADMIN">;

type PersonelRolDegistirmeOnayiProps = {
    detay: PersonelYonetimDetayi;
    onBasarili: () => Promise<void>;
};

const ROL_SECENEKLERI: Array<{
    deger: AtanabilirRol;
    etiket: string;
}> = [
    { deger: "PERSONEL", etiket: "Personel" },
    { deger: "YONETICI", etiket: "Yönetici" },
    { deger: "HR", etiket: "İnsan Kaynakları" },
];

function rolEtiketi(rol: AuthRole) {
    return (
        ROL_SECENEKLERI.find((secenek) => secenek.deger === rol)
            ?.etiket ?? "Admin"
    );
}

/**
 * Yalnız Admin'in rol değişikliğini açık onayla başlatmasını sağlar.
 * ADMIN seçeneği UI'da sunulmaz; backend de bu değeri ayrıca reddeder.
 */
export default function PersonelRolDegistirmeOnayi({
    detay,
    onBasarili,
}: PersonelRolDegistirmeOnayiProps) {
    const { isOpen, openModal, closeModal } = useModal();
    const [secilenRol, setSecilenRol] = useState<AtanabilirRol>(
        detay.rol === "ADMIN" ? "PERSONEL" : detay.rol,
    );
    const [gonderimSuruyor, setGonderimSuruyor] = useState(false);
    const [hata, setHata] = useState<string | null>(null);
    const gonderimKilidi = useRef(false);

    const rolDegismedi = secilenRol === detay.rol;

    function onayiAc() {
        setSecilenRol(detay.rol === "ADMIN" ? "PERSONEL" : detay.rol);
        setHata(null);
        openModal();
    }

    function modaliKapat() {
        if (gonderimKilidi.current || gonderimSuruyor) {
            return;
        }

        setHata(null);
        closeModal();
    }

    async function degisikligiOnayla() {
        if (gonderimKilidi.current || rolDegismedi) {
            return;
        }

        gonderimKilidi.current = true;
        setGonderimSuruyor(true);
        setHata(null);

        try {
            await rolDegistir(detay.personelId, secilenRol);
            await onBasarili();
            closeModal();
        } catch (error) {
            setHata(
                error instanceof ApiError
                    ? error.message
                    : "Personel rolü değiştirilirken beklenmeyen bir hata oluştu.",
            );
        } finally {
            gonderimKilidi.current = false;
            setGonderimSuruyor(false);
        }
    }

    return (
        <>
            <button
                type="button"
                onClick={onayiAc}
                className="rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:bg-transparent dark:text-gray-300 dark:hover:bg-white/[0.03]"
            >
                Rolü değiştir
            </button>

            <Modal
                isOpen={isOpen}
                onClose={modaliKapat}
                showCloseButton={!gonderimSuruyor}
                className="max-w-[520px] p-6"
            >
                <div className="px-2">
                    <h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">
                        Personel rolünü değiştir
                    </h2>

                    <p className="mt-3 text-sm leading-6 text-gray-600 dark:text-gray-300">
                        <strong>
                            {detay.ad} {detay.soyad}
                        </strong>{" "}
                        için yeni rolü seçin. Rol değişikliğinde mevcut
                        refresh oturumları sonlandırılır.
                    </p>

                    <div className="mt-5">
                        <label
                            htmlFor="personel-yeni-rol"
                            className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300"
                        >
                            Yeni rol
                        </label>
                        <select
                            id="personel-yeni-rol"
                            value={secilenRol}
                            disabled={gonderimSuruyor}
                            onChange={(event) =>
                                setSecilenRol(
                                    event.target.value as AtanabilirRol,
                                )
                            }
                            className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"
                        >
                            {ROL_SECENEKLERI.map((secenek) => (
                                <option
                                    key={secenek.deger}
                                    value={secenek.deger}
                                >
                                    {secenek.etiket}
                                </option>
                            ))}
                        </select>

                        {rolDegismedi && (
                            <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">
                                Mevcut rol: {rolEtiketi(detay.rol)}
                            </p>
                        )}
                    </div>

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
                            disabled={gonderimSuruyor}
                            className="rounded-lg border border-gray-300 px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-white/[0.03]"
                        >
                            Vazgeç
                        </button>

                        <button
                            type="button"
                            onClick={() => void degisikligiOnayla()}
                            disabled={gonderimSuruyor || rolDegismedi}
                            className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            {gonderimSuruyor
                                ? "Rol değiştiriliyor…"
                                : "Rolü değiştir"}
                        </button>
                    </div>
                </div>
            </Modal>
        </>
    );
}
