import { personelYetenekleriniGuncelle } from "../../lib/api";
import type { PersonelYonetimDetayi } from "../../types/personelYonetim";
import { useModal } from "../../hooks/useModal";
import ProfilYetenekDuzenlemeModali from "../UserProfile/ProfilYetenekDuzenlemeModali";

type PersonelYetenekDuzenlemeOnayiProps = {
    detay: PersonelYonetimDetayi;
    onBasarili: () => Promise<void>;
};

/**
 * Admin'in hedef personelin mevcut yetenek kümesini düzenlemesi için ortak seçim penceresini kullanır.
 */
export default function PersonelYetenekDuzenlemeOnayi({
    detay,
    onBasarili,
}: PersonelYetenekDuzenlemeOnayiProps) {
    const { isOpen, openModal, closeModal } = useModal();

    if (detay.rol === "ADMIN" || !detay.aktif) {
        return null;
    }

    return (
        <>
            <button
                type="button"
                onClick={openModal}
                className="rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:bg-transparent dark:text-gray-300 dark:hover:bg-white/[0.03]"
            >
                Yetenekleri düzenle
            </button>

            <ProfilYetenekDuzenlemeModali
                isOpen={isOpen}
                mevcutYetenekIdleri={detay.yetenekler.map((yetenek) => yetenek.id)}
                onKapat={closeModal}
                onBasarili={onBasarili}
                onYetenekleriKaydet={(yetenekIdleri) =>
                    personelYetenekleriniGuncelle(
                        detay.personelId,
                        yetenekIdleri,
                    )
                }
                baslik="Personel yeteneklerini düzenle"
                aciklama={`${detay.ad} ${detay.soyad} için yetenekleri seçin. Seçimi kaldırmak için ilgili kutunun işaretini kaldırabilirsiniz.`}
                alanBasligi="Personelin yetenekleri"
            />
        </>
    );
}
