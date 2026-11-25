import ProfilAvatar from "../common/ProfilAvatar";

// Dashboard özetini Home yükler; bu bileşen yalnız doğum günü verisini gösterir.
export default function DogumGunuKarti({ dogumGunleri, yukleniyor }) {
  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] md:p-6">
      <h3 className="mb-4 text-lg font-semibold text-gray-800 dark:text-white/90">
        Bugün Doğanlar
      </h3>

      {yukleniyor && <p className="text-sm text-gray-500">Yükleniyor...</p>}
      {!yukleniyor && dogumGunleri.length === 0 && (
        <p className="text-sm text-gray-500">Bugün doğum günü olan yok.</p>
      )}

      <ul className="flex flex-col gap-3">
        {dogumGunleri.map((kisi, i) => (
          <li key={i} className="flex items-center gap-3">
            <ProfilAvatar
              ad={kisi.adSoyad}
              soyad=""
              profilFoto={kisi.profilFoto}
              boyutSinifi="h-10 w-10 text-sm"
              alt={`${kisi.adSoyad} profil fotoğrafı`}
            />
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
