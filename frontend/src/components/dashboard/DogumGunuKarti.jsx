import { useEffect, useState } from "react";
import { apiFetch } from "../../lib/api";

// Bugün doğum günü olanları gösteren kart.
// Veriyi backend'in /api/dashboard/ozet endpoint'inden apiFetch ile çeker.
// apiFetch: Authorization: Bearer header'ını otomatik ekler, 401 gelirse sessizce refresh dener.
export default function DogumGunuKarti() {
  const [dogumGunleri, setDogumGunleri] = useState([]); // gelen liste (başta boş)
  const [yukleniyor, setYukleniyor] = useState(true); // istek sürüyor mu
  const [hata, setHata] = useState(""); // hata mesajı

  useEffect(() => {
    // component ekrana ilk geldiğinde BİR kez çalışır ([] boş bağımlılık listesi)
    async function veriyiGetir() {
      try {
        const cevap = await apiFetch("/api/dashboard/ozet");
        if (!cevap.ok) throw new Error("Veri alınamadı.");
        const data = await cevap.json();
        setDogumGunleri(data.bugunDogumGunleri || []); // sadece doğum günü kısmını al
      } catch (e) {
        setHata(e.message);
      } finally {
        setYukleniyor(false); // başarı da olsa hata da olsa yükleniyor'u kapat
      }
    }
    veriyiGetir();
  }, []);

  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] md:p-6">
      <h3 className="mb-4 text-lg font-semibold text-gray-800 dark:text-white/90">
        Bugün Doğanlar
      </h3>

      {/* durumlar: yükleniyor / hata / boş / dolu */}
      {yukleniyor && <p className="text-sm text-gray-500">Yükleniyor...</p>}
      {hata && <p className="text-sm text-error-500">{hata}</p>}
      {!yukleniyor && !hata && dogumGunleri.length === 0 && (
        <p className="text-sm text-gray-500">Bugün doğum günü olan yok.</p>
      )}

      <ul className="flex flex-col gap-3">
        {dogumGunleri.map((kisi, i) => (
          <li key={i} className="flex items-center gap-3">
            {/* profil foto yerine şimdilik baş harf; ileride gerçek foto bağlanır */}
            <span className="flex h-10 w-10 items-center justify-center rounded-full bg-brand-50 text-sm font-medium text-brand-500 dark:bg-brand-500/10">
              {kisi.adSoyad.charAt(0)}
            </span>
            <div>
              <p className="text-sm font-medium text-gray-800 dark:text-white/90">
                {kisi.adSoyad}
              </p>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                {kisi.departman}
              </p>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
