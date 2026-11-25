import { profilFotoAnahtariGecerliMi } from "./profilFoto.js";

const BASE_URL = "http://localhost:8080";

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

async function apiErrorOlustur(response, fallbackMessage) {
  const body = await response.json().catch(() => ({}));
  return new ApiError(response.status, body.message || fallbackMessage);
}

export async function login({ kullaniciAdi, parola }) {
  let response;

  try {
    response = await fetch(`${BASE_URL}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ kullaniciAdi, parola }),
    });
  } catch {
    throw new ApiError(
      0,
      "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
    );
  }

  if (!response.ok) {
    throw await apiErrorOlustur(response, "Giriş başarısız.");
  }

  return response.json();
}

export async function ilkParolayiDegistir({
  kullaniciAdi,
  mevcutParola,
  yeniParola,
}) {
  let response;

  try {
    response = await fetch(`${BASE_URL}/api/auth/ilk-parola`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ kullaniciAdi, mevcutParola, yeniParola }),
    });
  } catch {
    throw new ApiError(
      0,
      "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
    );
  }

  if (!response.ok) {
    throw await apiErrorOlustur(
      response,
      "Parola değiştirilemedi. Bilgilerinizi kontrol edip tekrar deneyin.",
    );
  }
}

export function clearStoredSession() {
  localStorage.removeItem("token");
  localStorage.removeItem("kullanici");
}

let refreshPromise = null;

//single flight prensibi (aynı sekme içinde yollanılan paralel isteklerin hepsinin işlenilmesini engeller)
//bu func birden fazla istek 401 yiyince hepsi aynı anda çalışmasın,herkes kendinden önce gelen isteği beklesin diye var.
function refreshAccessToken() {
  //devam eden bir refresh varmı kontrolü
  if (refreshPromise === null) {
    //yoksa yeni refresh promise oluştur
    refreshPromise = fetch(`${BASE_URL}/api/auth/refresh`, {
      method: "POST",
      credentials: "include",
    })
      //cevabı bekle
      .then(async (response) => {
        //cevap gelmezse error at
        if (!response.ok) {
          const err = await response.json().catch(() => ({})); //backend'in ErrorResponse'unu oku
            throw new ApiError(
                response.status,
                err.message || "Oturumunuz sonlandı, tekrar giriş yapın.",
            );
        }
        //gelen access token'ı localstorage'da sakla
        const data = await response.json();
        localStorage.setItem("token", data.token);
        return data.token;
      })
      //işlem başarılı yada başarısız olduktan sonra sonradan gelcek işlemler için refreshPromise'i null'a eşitle
      .finally(() => {
        refreshPromise = null;
      });
  }
  //varsa yeni işlem başlatma bitmesini bekle
  return refreshPromise;
}

//fetch sarmalayıcısı: component'ler fetch yerine bunu kullanacak
//işi: Authorization header'ını otomatik ekle, 401 gelirse sessizce refresh dene, isteği BİR kez tekrarla
export async function apiFetch(path, options = {}) {
  //istekYap bir İÇ FONKSİYON: her çağrıldığında header'ları YENİDEN kurar
  //bu önemli çünkü tekrar denemede localStorage'daki YENİ token gitmeli (eskisi değil)
  //DİKKAT: gövde birden çok satır olduğu için süslü parantez + return ŞART
  const istekYap = () => {
    return fetch(`${BASE_URL}${path}`, {
      ...options, //çağıranın verdiği method/body vs aynen korunur
      headers: {
        ...(options.headers || {}), // header içinde ayrıyetten ... kullanıyoruz çünkü headers üstüne ekleme yapmak istiyoruz onu ezmek istemiyoruz
        Authorization: `Bearer ${localStorage.getItem("token")}`,
      },
    });
  };

  let cevap = await istekYap();

  //401 DEĞİLSE iş bitti access token hala geçerli refresh dünyası hiç devreye girmez
  if (cevap.status !== 401) {
    return cevap;
  }

  //401 geldi: access token ölmüş olabilir -> sessiz yenilemeyi dene
  try {
    await refreshAccessToken();
  } catch (e) {
    //refresh de patladı -> oturum GERÇEKTEN bitti (logout'lu / süresi dolmuş / alarm yemiş)
    //temizlik yap, login'e yönlendir
    clearStoredSession();
      const authMesaji =
          e instanceof ApiError && e.message
              ? e.message
              : "Oturumunuz sonlandı, tekrar giriş yapın.";

      sessionStorage.setItem("authMesaji", authMesaji); //sayfa yenilenince errorresponse message kaybolmasın diye köprü
    window.location.href = "/signin"; // TailAdmin'de login rotası /signin
    return cevap;
  }
  //refresh başarılı -> orijinal isteği yeni token'la 1 kez daha tekrarla
  return istekYap();
}

let profilIstegi = null;

export function profilimiGetir() {
    const token = localStorage.getItem("token");

    if (profilIstegi !== null && profilIstegi.token === token) {
        return profilIstegi.promise;
    }

    const istek = {
        token,
        promise: (async () => {
            let response;

            try {
                response = await apiFetch("/api/profil");
            } catch {
                throw new ApiError(
                    0,
                    "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
                );
            }

            if (!response.ok) {
                throw await apiErrorOlustur(
                    response,
                    "Profil bilgileri alınamadı.",
                );
            }

            return response.json();
        })(),
    };

    istek.promise = istek.promise.finally(() => {
        if (profilIstegi === istek) {
            profilIstegi = null;
        }
    });

    profilIstegi = istek;
    return istek.promise;
}

/**
 * @param {import("../types/profil").ProfilGuncellemeRequest} payload
 * @returns {Promise<void>}
 */
export async function profilimiGuncelle(payload) {
    let response;

    try {
        response = await apiFetch("/api/profil", {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
        });
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Profil bilgileri güncellenemedi. Lütfen tekrar deneyin.",
        );
    }
}

export async function profilYetenekleriniGuncelle(yetenekIdleri) {
    let response;

    try {
        response = await apiFetch("/api/profil/yetenekler", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ yetenekIdleri }),
        });
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Yetenekler güncellenemedi. Lütfen tekrar deneyin.",
        );
    }
}

/**
 * Kullanıcının seçtiği profil fotoğrafını multipart olarak yükler.
 * FormData sınırını tarayıcının oluşturması için Content-Type elle verilmez.
 */
export async function profilFotografimiGuncelle(dosya) {
    const formData = new FormData();
    formData.append("dosya", dosya);

    let response;

    try {
        response = await apiFetch("/api/profil/fotograf", {
            method: "PUT",
            body: formData,
        });
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Profil fotoğrafı güncellenemedi. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

export async function profilFotografimiSil() {
    let response;

    try {
        response = await apiFetch("/api/profil/fotograf", {
            method: "DELETE",
        });
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Profil fotoğrafı kaldırılamadı. Lütfen tekrar deneyin.",
        );
    }
}

/**
 * Görsel endpoint'i bearer token istediği için <img> kaynağı olarak doğrudan
 * kullanılmaz; yetkili response Blob'a çevrilerek component'a verilir.
 */
export async function profilFotografiBlobunuGetir(dosyaAnahtari) {
    if (!profilFotoAnahtariGecerliMi(dosyaAnahtari)) {
        return null;
    }

    const encodedDosyaAnahtari = encodeURIComponent(dosyaAnahtari);
    let response;

    try {
        response = await apiFetch(
            `/api/profil-fotograflari/${encodedDosyaAnahtari}`,
        );
    } catch {
        throw new ApiError(
            0,
            "Profil fotoğrafı alınamadı. Lütfen tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Profil fotoğrafı alınamadı. Lütfen tekrar deneyin.",
        );
    }

    return response.blob();
}

export async function parolamiDegistir({ mevcutParola, yeniParola }) {
    let response;

    try {
        response = await apiFetch("/api/profil/parola", {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ mevcutParola, yeniParola }),
        });
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Parola değiştirilemedi. Bilgilerinizi kontrol edip tekrar deneyin.",
        );
    }
}

/**
 * Filtrelenmiş ve sayfalanmış personel listesini getirir.
 *
 * @param {import("../types/personelYonetim").PersonelYonetimFiltreleri} params
 * @returns {Promise<import("../types/personelYonetim").PersonelYonetimSayfasi>}
 */
export async function personelleriGetir(params) {
    const query = new URLSearchParams();

    // Sayfalama değerleri her istekte gönderilir.
    query.set("page", String(params.page));
    query.set("size", String(params.size));

    const arama = params.arama?.trim();

    // Boş arama metni query string'e eklenmez.
    if (arama) {
        query.set("arama", arama);
    }

    if (params.departmanId !== undefined) {
        query.set("departmanId", String(params.departmanId));
    }

    if (params.rol) {
        query.set("rol", params.rol);
    }

    // false geçerli bir filtre olduğu için truthy kontrolü kullanılmaz.
    if (params.aktif !== undefined) {
        query.set("aktif", String(params.aktif));
    }

    let response;

    try {
        response = await apiFetch(`/api/hr/personel?${query.toString()}`);
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Personel listesi alınamadı. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

/**
 * Yönetim ekranında seçilen personelin detayını getirir.
 *
 * @param {string} personelId
 * @returns {Promise<import("../types/personelYonetim").PersonelYonetimDetayi>}
 */
export async function personelDetayiniGetir(personelId) {
    // Path parametresi URL içinde güvenli kullanılacak biçime dönüştürülür.
    const encodedPersonelId = encodeURIComponent(personelId);

    let response;

    try {
        response = await apiFetch(
            `/api/hr/personel/${encodedPersonelId}`,
        );
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Personel bilgileri alınamadı. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

/**
 * Yalnız Admin'in seçilen aktif personelin yetenek kümesini güncellemesi için kullanılır.
 * Hedef matrisi ve küme doğrulaması backend tarafından ayrıca uygulanır.
 *
 * @param {string} personelId
 * @param {number[]} yetenekIdleri
 * @returns {Promise<void>}
 */
export async function personelYetenekleriniGuncelle(
    personelId,
    yetenekIdleri,
) {
    const encodedPersonelId = encodeURIComponent(personelId);
    let response;

    try {
        response = await apiFetch(
            `/api/hr/personel/${encodedPersonelId}/yetenekler`,
            {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ yetenekIdleri }),
            },
        );
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Personelin yetenekleri güncellenemedi. Lütfen tekrar deneyin.",
        );
    }
}

/**
 * HR/Admin yetkisiyle yeni PERSONEL kaydı oluşturur.
 *
 * @param {import("../types/personelYonetim").PersonelOlusturmaRequest} payload
 * @returns {Promise<import("../types/personelYonetim").PersonelOlusturmaResponse>}
 */
export async function personelOlustur(payload) {
    let response;

    try {
        response = await apiFetch("/api/hr/personel", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
        });
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Personel oluşturulamadı. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

/**
 * Yalnız Admin'in seçilen personelin rolünü değiştirmesi için kullanılır.
 * Backend, hedef matrisi ve ADMIN rolüne geçiş kuralını ayrıca doğrular.
 *
 * @param {string} personelId
 * @param {"PERSONEL" | "YONETICI" | "HR"} rol
 * @returns {Promise<void>}
 */
export async function rolDegistir(personelId, rol) {
    const encodedPersonelId = encodeURIComponent(personelId);
    let response;

    try {
        response = await apiFetch(
            `/api/admin/personel/${encodedPersonelId}/rol`,
            {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ rol }),
            },
        );
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Personel rolü değiştirilemedi. Lütfen tekrar deneyin.",
        );
    }
}

/**
 * Yalnız Admin'in hedef personel için geçici parola atamasını sağlar.
 * Yetki ve parola politikası kararları backend tarafından verilir.
 *
 * @param {string} personelId
 * @param {string} geciciParola
 * @returns {Promise<void>}
 */
export async function adminParolaSifirla(personelId, geciciParola) {
    const encodedPersonelId = encodeURIComponent(personelId);
    let response;

    try {
        response = await apiFetch(
            `/api/admin/personel/${encodedPersonelId}/parola-sifirla`,
            {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ geciciParola }),
            },
        );
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Parola sıfırlanamadı. Lütfen tekrar deneyin.",
        );
    }
}

/**
 * HR/Admin yetkisiyle personelin izin verilen alanlarını günceller.
 *
 * @param {string} personelId
 * @param {import("../types/personelYonetim").PersonelYonetimGuncellemeRequest} payload
 * @returns {Promise<void>}
 */
export async function personelGuncelle(personelId, payload) {
    // Path parametresi URL içinde güvenli kullanılacak biçime dönüştürülür.
    const encodedPersonelId = encodeURIComponent(personelId);

    let response;

    try {
        response = await apiFetch(
            `/api/hr/personel/${encodedPersonelId}`,
            {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            },
        );
    } catch {
        throw new ApiError(0, "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",);
    }

    if (!response.ok) {
        throw await apiErrorOlustur(response, "Personel bilgileri güncellenemedi. Lütfen tekrar deneyin.",);
    }
}

/**
 * HR/Admin yetkisiyle aktif bir personeli pasifleştirir.
 * Endpoint body istemez; başarılı cevap 204 No Content döner.
 *
 * @param {string} personelId
 * @returns {Promise<void>}
 */
export async function personelPasiflestir(personelId) {
    const encodedPersonelId = encodeURIComponent(personelId);

    let response;

    try {
        response = await apiFetch(
            `/api/hr/personel/${encodedPersonelId}/pasiflestir`,
            { method: "PATCH" },
        );
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Personel pasifleştirilemedi. Lütfen tekrar deneyin.",
        );
    }
}

/**
 * HR/Admin yetkisiyle pasif bir personeli yeniden aktifleştirir.
 * Endpoint body istemez; başarılı cevap 204 No Content döner.
 *
 * @param {string} personelId
 * @returns {Promise<void>}
 */
export async function personelAktiflestir(personelId) {
    const encodedPersonelId = encodeURIComponent(personelId);

    let response;

    try {
        response = await apiFetch(
            `/api/hr/personel/${encodedPersonelId}/aktiflestir`,
            { method: "PATCH" },
        );
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Personel aktifleştirilemedi. Lütfen tekrar deneyin.",
        );
    }
}

/**
 * Seçilen departmandaki aktif ve atanabilir yöneticileri getirir.
 *
 * @param {number} departmanId
 * @param {string} haricPersonelId
 * @returns {Promise<import("../types/personelYonetim").AtanabilirYoneticiSecenegi[]>}
 */
export async function atanabilirYoneticileriGetir(
    departmanId,
    haricPersonelId,
) {
    const query = new URLSearchParams();

    query.set("departmanId", String(departmanId));

    // Detayı görüntülenen personel yönetici seçeneklerinden hariç tutulur.
    query.set("haricPersonelId", haricPersonelId);

    let response;

    try {
        response = await apiFetch(
            `/api/hr/personel/atanabilir-yoneticiler?${query.toString()}`,
        );
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Yönetici bilgileri alınamadı. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

/**
 * Personel filtresinde kullanılacak departman seçeneklerini getirir.
 *
 * @returns {Promise<import("../types/personelYonetim").LookupSecenegi[]>}
 */
export async function departmanlariGetir() {
    let response;

    try {
        response = await apiFetch("/api/departments");
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Departman bilgileri alınamadı. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

/**
 * Ekip dizini filtresinde kullanılacak yetenek seçeneklerini getirir.
 *
 * @returns {Promise<import("../types/personelYonetim").LookupSecenegi[]>}
 */
export async function yetenekleriGetir() {
    let response;

    try {
        response = await apiFetch("/api/skills");
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Yetenek bilgileri alınamadı. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

export async function departmanOlustur(departmanAdi) {
    let response;

    try {
        response = await apiFetch("/api/departments", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ departmanAdi }),
        });
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Departman eklenemedi. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

export async function departmanGuncelle(departmanId, departmanAdi) {
    const encodedDepartmanId = encodeURIComponent(String(departmanId));
    let response;

    try {
        response = await apiFetch(
            `/api/departments/${encodedDepartmanId}`,
            {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ departmanAdi }),
            },
        );
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Departman güncellenemedi. Lütfen tekrar deneyin.",
        );
    }
}

export async function departmanSil(departmanId) {
    const encodedDepartmanId = encodeURIComponent(String(departmanId));
    let response;

    try {
        response = await apiFetch(`/api/departments/${encodedDepartmanId}`, {
            method: "DELETE",
        });
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Departman silinemedi. Lütfen tekrar deneyin.",
        );
    }
}

export async function yetenekOlustur(yetenekAdi) {
    let response;

    try {
        response = await apiFetch("/api/skills", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ yetenekAdi }),
        });
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Yetenek eklenemedi. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

export async function yetenekGuncelle(yetenekId, yetenekAdi) {
    const encodedYetenekId = encodeURIComponent(String(yetenekId));
    let response;

    try {
        response = await apiFetch(`/api/skills/${encodedYetenekId}`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ yetenekAdi }),
        });
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Yetenek güncellenemedi. Lütfen tekrar deneyin.",
        );
    }
}

export async function yetenekSil(yetenekId) {
    const encodedYetenekId = encodeURIComponent(String(yetenekId));
    let response;

    try {
        response = await apiFetch(`/api/skills/${encodedYetenekId}`, {
            method: "DELETE",
        });
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Yetenek silinemedi. Lütfen tekrar deneyin.",
        );
    }
}

/**
 * Aktif personellerden oluşan ekip dizinini filtreli ve sayfalı getirir.
 *
 * @param {import("../types/ekipDizini").EkipDiziniFiltreleri} params
 * @returns {Promise<import("../types/ekipDizini").EkipDiziniSayfasi>}
 */
export async function ekipDizininiGetir(params) {
    const query = new URLSearchParams();

    query.set("page", String(params.page));
    query.set("size", String(params.size));

    const arama = params.arama?.trim();

    if (arama) {
        query.set("arama", arama);
    }

    if (params.departmanId !== undefined) {
        query.set("departmanId", String(params.departmanId));
    }

    if (params.yetenekId !== undefined) {
        query.set("yetenekId", String(params.yetenekId));
    }

    let response;

    try {
        response = await apiFetch(
            `/api/ekip-dizini?${query.toString()}`,
        );
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Ekip dizini alınamadı. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

function tarihAraligiSorgusunuOlustur(baslangicTarihi, bitisTarihi) {
    const query = new URLSearchParams({ baslangicTarihi, bitisTarihi });
    return query.toString();
}

/**
 * @param {string} baslangicTarihi ISO YYYY-MM-DD
 * @param {string} bitisTarihi ISO YYYY-MM-DD
 * @returns {Promise<import("../types/tatil").Tatil[]>}
 */
export async function tatilleriGetir(baslangicTarihi, bitisTarihi) {
    let response;

    try {
        response = await apiFetch(
            `/api/holidays?${tarihAraligiSorgusunuOlustur(baslangicTarihi, bitisTarihi)}`,
        );
    } catch {
        throw new ApiError(
            0,
            "Takvim verisine ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Tatiller alınamadı. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

/**
 * @param {string} baslangicTarihi ISO YYYY-MM-DD
 * @param {string} bitisTarihi ISO YYYY-MM-DD
 * @returns {Promise<import("../types/tatil").DogumGunu[]>}
 */
export async function dogumGunleriniGetir(baslangicTarihi, bitisTarihi) {
    let response;

    try {
        response = await apiFetch(
            `/api/birthdays?${tarihAraligiSorgusunuOlustur(baslangicTarihi, bitisTarihi)}`,
        );
    } catch {
        throw new ApiError(
            0,
            "Doğum günü verisine ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Doğum günleri alınamadı. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

/** @returns {Promise<import("../types/rezervasyon").Oda[]>} */
export async function odalariGetir() {
    let response;

    try {
        response = await apiFetch("/api/rooms");
    } catch {
        throw new ApiError(
            0,
            "Odalar alınamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Odalar alınamadı. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

/**
 * @param {string} baslangicTarihi ISO YYYY-MM-DD
 * @param {string} bitisTarihi ISO YYYY-MM-DD
 * @param {number | undefined} odaId
 * @returns {Promise<import("../types/rezervasyon").Rezervasyon[]>}
 */
export async function rezervasyonlariGetir(baslangicTarihi, bitisTarihi, odaId) {
    const query = new URLSearchParams({ baslangicTarihi, bitisTarihi });
    if (odaId !== undefined) query.set("odaId", String(odaId));

    let response;

    try {
        response = await apiFetch(`/api/reservations?${query.toString()}`);
    } catch {
        throw new ApiError(
            0,
            "Rezervasyon takvimine ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Rezervasyonlar alınamadı. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

/**
 * @param {import("../types/rezervasyon").RezervasyonOlusturmaIstek} payload
 * @returns {Promise<import("../types/rezervasyon").Rezervasyon>}
 */
export async function rezervasyonOlustur(payload) {
    let response;

    try {
        response = await apiFetch("/api/reservations", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
        });
    } catch {
        throw new ApiError(0, "Rezervasyon oluşturulamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) {
        throw await apiErrorOlustur(response, "Rezervasyon oluşturulamadı. Lütfen tekrar deneyin.");
    }

    return response.json();
}

function rezervasyonGecmisiSorgusunuOlustur(params) {
    const query = new URLSearchParams({ page: String(params.page), size: String(params.size) });
    ["durum", "odaId", "olusturanId", "baslangic", "bitis"].forEach((alan) => {
        if (params[alan] !== undefined && params[alan] !== "") query.set(alan, String(params[alan]));
    });
    return query.toString();
}

async function rezervasyonGecmisiniGetir(url, fallbackMessage) {
    let response;

    try {
        response = await apiFetch(url);
    } catch {
        throw new ApiError(0, "Rezervasyon geçmişine ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) {
        throw await apiErrorOlustur(response, fallbackMessage);
    }

    return response.json();
}

/** @returns {Promise<import("../types/rezervasyon").RezervasyonGecmisiSayfasi>} */
export function rezervasyonGecmisimiGetir(params) {
    return rezervasyonGecmisiniGetir(
        `/api/reservations/my?${rezervasyonGecmisiSorgusunuOlustur(params)}`,
        "Rezervasyon geçmişiniz alınamadı. Lütfen tekrar deneyin.",
    );
}

/** @returns {Promise<import("../types/rezervasyon").RezervasyonGecmisiSayfasi>} */
export function adminRezervasyonGecmisiniGetir(params) {
    return rezervasyonGecmisiniGetir(
        `/api/admin/reservations?${rezervasyonGecmisiSorgusunuOlustur(params)}`,
        "Şirket rezervasyon geçmişi alınamadı. Lütfen tekrar deneyin.",
    );
}

export async function rezervasyonuIptalEt(rezervasyonId, iptalNedeni) {
    const options = { method: "PATCH" };
    if (iptalNedeni !== undefined) {
        options.headers = { "Content-Type": "application/json" };
        options.body = JSON.stringify({ iptalNedeni });
    }

    let response;

    try {
        response = await apiFetch(`/api/reservations/${encodeURIComponent(rezervasyonId)}/cancel`, options);
    } catch {
        throw new ApiError(0, "Rezervasyon iptal edilemedi. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) {
        throw await apiErrorOlustur(response, "Rezervasyon iptal edilemedi. Lütfen tekrar deneyin.");
    }
}

/**
 * @param {import("../types/tatil").TatilKaydetRequest} payload
 * @returns {Promise<import("../types/tatil").Tatil>}
 */
export async function tatilOlustur(payload) {
    let response;

    try {
        response = await apiFetch("/api/holidays", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
        });
    } catch {
        throw new ApiError(
            0,
            "Tatil oluşturulamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Tatil oluşturulamadı. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

/**
 * @param {number} tatilId
 * @param {import("../types/tatil").TatilKaydetRequest} payload
 * @returns {Promise<void>}
 */
export async function tatilGuncelle(tatilId, payload) {
    const encodedTatilId = encodeURIComponent(String(tatilId));
    let response;

    try {
        response = await apiFetch(`/api/holidays/${encodedTatilId}`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
        });
    } catch {
        throw new ApiError(
            0,
            "Tatil güncellenemedi. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Tatil güncellenemedi. Lütfen tekrar deneyin.",
        );
    }
}

/**
 * @param {number} tatilId
 * @returns {Promise<void>}
 */
export async function tatilSil(tatilId) {
    const encodedTatilId = encodeURIComponent(String(tatilId));
    let response;

    try {
        response = await apiFetch(`/api/holidays/${encodedTatilId}`, {
            method: "DELETE",
        });
    } catch {
        throw new ApiError(
            0,
            "Tatil silinemedi. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Tatil silinemedi. Lütfen tekrar deneyin.",
        );
    }
}

/**
 * @param {string} baslangicTarihi ISO YYYY-MM-DD
 * @param {string} bitisTarihi ISO YYYY-MM-DD
 * @returns {Promise<import("../types/tatil").IsGunuOnizlemeResponse>}
 */
export async function isGunuOnizle(baslangicTarihi, bitisTarihi) {
    let response;

    try {
        response = await apiFetch(
            `/api/leave-requests/workday-preview?${tarihAraligiSorgusunuOlustur(baslangicTarihi, bitisTarihi)}`,
        );
    } catch {
        throw new ApiError(
            0,
            "İş günü önizlemesi alınamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "İş günü önizlemesi alınamadı. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

/** @returns {Promise<import("../types/izinTalebi").IzinTuru[]>} */
export async function izinTurleriniGetir() {
    let response;

    try {
        response = await apiFetch("/api/leave-types");
    } catch {
        throw new ApiError(
            0,
            "İzin türleri alınamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "İzin türleri alınamadı. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

/**
 * @param {import("../types/izinTalebi").IzinTalebiOlusturmaRequest} payload
 * @returns {Promise<import("../types/izinTalebi").IzinTalebi>}
 */
export async function izinTalebiOlustur(payload) {
    let response;

    try {
        response = await apiFetch("/api/leave-requests", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
        });
    } catch {
        throw new ApiError(
            0,
            "İzin talebi oluşturulamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "İzin talebi oluşturulamadı. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

/**
 * @param {import("../types/izinTalebi").IzinTalepleriFiltreleri} params
 * @returns {Promise<import("../types/izinTalebi").IzinTalepleriSayfasi>}
 */
export async function izinTaleplerimiGetir(params) {
    const query = new URLSearchParams({
        page: String(params.page),
        size: String(params.size),
    });

    if (params.durum) query.set("durum", params.durum);

    let response;

    try {
        response = await apiFetch(`/api/leave-requests/my?${query.toString()}`);
    } catch {
        throw new ApiError(
            0,
            "İzin talepleriniz alınamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "İzin talepleriniz alınamadı. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

function izinTalebiListeSorgusunuOlustur(params) {
    const query = new URLSearchParams({
        page: String(params.page),
        size: String(params.size),
    });

    if (params.durum) query.set("durum", params.durum);
    return query.toString();
}

async function izinKararListesiniGetir(url, fallbackMessage) {
    let response;

    try {
        response = await apiFetch(url);
    } catch {
        throw new ApiError(
            0,
            "İzin talepleri alınamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(response, fallbackMessage);
    }

    return response.json();
}

/** @returns {Promise<import("../types/izinTalebi").KararBekleyenIzinTalepleriSayfasi>} */
export function ekipIzinTalepleriniGetir(params) {
    return izinKararListesiniGetir(
        `/api/leave-requests/team?${izinTalebiListeSorgusunuOlustur(params)}`,
        "Ekip izin talepleri alınamadı. Lütfen tekrar deneyin.",
    );
}

/** @returns {Promise<import("../types/izinTalebi").KararBekleyenIzinTalepleriSayfasi>} */
export function adminIzinTalepleriniGetir(params) {
    return izinKararListesiniGetir(
        `/api/admin/leave-requests?${izinTalebiListeSorgusunuOlustur(params)}`,
        "Şirket izin talepleri alınamadı. Lütfen tekrar deneyin.",
    );
}

export async function izinTalebiniOnayla(talepId) {
    let response;

    try {
        response = await apiFetch(
            `/api/leave-requests/${encodeURIComponent(talepId)}/approve`,
            { method: "PATCH" },
        );
    } catch {
        throw new ApiError(
            0,
            "İzin talebi onaylanamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "İzin talebi onaylanamadı. Lütfen tekrar deneyin.",
        );
    }
}

export async function izinTalebiniReddet(talepId, kararNotu) {
    let response;

    try {
        response = await apiFetch(
            `/api/leave-requests/${encodeURIComponent(talepId)}/reject`,
            {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ kararNotu }),
            },
        );
    } catch {
        throw new ApiError(
            0,
            "İzin talebi reddedilemedi. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "İzin talebi reddedilemedi. Lütfen tekrar deneyin.",
        );
    }
}

export async function izinTalebiniIptalEt(talepId) {
    let response;

    try {
        response = await apiFetch(
            `/api/leave-requests/${encodeURIComponent(talepId)}/cancel`,
            { method: "PATCH" },
        );
    } catch {
        throw new ApiError(
            0,
            "İzin talebi iptal edilemedi. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "İzin talebi iptal edilemedi. Lütfen tekrar deneyin.",
        );
    }
}

/** @returns {Promise<import("../types/ekipman").EkipmanTipi[]>} */
export async function ekipmanTipleriniGetir() {
    let response;

    try {
        response = await apiFetch("/api/equipment-types");
    } catch {
        throw new ApiError(0, "Ekipman tipleri alınamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) {
        throw await apiErrorOlustur(response, "Ekipman tipleri alınamadı. Lütfen tekrar deneyin.");
    }

    return response.json();
}

/**
 * @param {import("../types/ekipman").EkipmanTalebiOlusturmaIstek} payload
 * @returns {Promise<import("../types/ekipman").EkipmanTalebi>}
 */
export async function ekipmanTalebiOlustur(payload) {
    let response;

    try {
        response = await apiFetch("/api/equipment-requests", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
        });
    } catch {
        throw new ApiError(0, "Ekipman talebi oluşturulamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) {
        throw await apiErrorOlustur(response, "Ekipman talebi oluşturulamadı. Lütfen tekrar deneyin.");
    }

    return response.json();
}

/**
 * @param {string} talepId
 * @param {import("../types/ekipman").EkipmanTalebiOlusturmaIstek} payload
 * @returns {Promise<import("../types/ekipman").EkipmanTalebi>}
 */
export async function ekipmanTalebiniGuncelle(talepId, payload) {
    let response;

    try {
        response = await apiFetch(`/api/equipment-requests/${encodeURIComponent(talepId)}`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
        });
    } catch {
        throw new ApiError(0, "Ekipman talebi güncellenemedi. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) {
        throw await apiErrorOlustur(response, "Ekipman talebi güncellenemedi. Lütfen tekrar deneyin.");
    }

    return response.json();
}

export async function ekipmanTalebiniIptalEt(talepId) {
    let response;

    try {
        response = await apiFetch(`/api/equipment-requests/${encodeURIComponent(talepId)}/cancel`, { method: "PATCH" });
    } catch {
        throw new ApiError(0, "Ekipman talebi iptal edilemedi. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) {
        throw await apiErrorOlustur(response, "Ekipman talebi iptal edilemedi. Lütfen tekrar deneyin.");
    }
}

function ekipmanTalepSorgusunuOlustur(params) {
    const query = new URLSearchParams({ page: String(params.page), size: String(params.size) });

    if (params.durum) query.set("durum", params.durum);
    if (params.ekipmanTipiId) query.set("ekipmanTipiId", String(params.ekipmanTipiId));
    return query.toString();
}

/** @returns {Promise<import("../types/ekipman").SayfaliEkipmanTalepResponse>} */
export async function ekipmanTaleplerimiGetir(params) {
    let response;

    try {
        response = await apiFetch(`/api/equipment-requests/my?${ekipmanTalepSorgusunuOlustur(params)}`);
    } catch {
        throw new ApiError(0, "Ekipman talepleriniz alınamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) {
        throw await apiErrorOlustur(response, "Ekipman talepleriniz alınamadı. Lütfen tekrar deneyin.");
    }

    return response.json();
}

/** @returns {Promise<import("../types/ekipman").SayfaliKararEkipmanTalepResponse>} */
export async function ekipEkipmanTalepleriniGetir(params) {
    let response;

    try {
        response = await apiFetch(`/api/equipment-requests/team?${ekipmanTalepSorgusunuOlustur(params)}`);
    } catch {
        throw new ApiError(0, "Ekip ekipman talepleri alınamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) {
        throw await apiErrorOlustur(response, "Ekip ekipman talepleri alınamadı. Lütfen tekrar deneyin.");
    }

    return response.json();
}

/** @returns {Promise<import("../types/ekipman").SayfaliKararEkipmanTalepResponse>} */
export async function adminEkipmanTalepleriniGetir(params) {
    let response;

    try {
        response = await apiFetch(`/api/admin/equipment-requests?${ekipmanTalepSorgusunuOlustur(params)}`);
    } catch {
        throw new ApiError(0, "Ekipman talepleri alınamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) {
        throw await apiErrorOlustur(response, "Ekipman talepleri alınamadı. Lütfen tekrar deneyin.");
    }

    return response.json();
}

/** @returns {Promise<import("../types/ekipman").SayfaliUygunEkipmanResponse>} */
export async function uygunEkipmanlariGetir(talepId, params) {
    let response;

    try {
        response = await apiFetch(
            `/api/equipment-requests/${encodeURIComponent(talepId)}/available-equipment?page=${encodeURIComponent(String(params.page))}&size=${encodeURIComponent(String(params.size))}`,
        );
    } catch {
        throw new ApiError(0, "Uygun ekipmanlar alınamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) {
        throw await apiErrorOlustur(response, "Uygun ekipmanlar alınamadı. Lütfen tekrar deneyin.");
    }

    return response.json();
}

export async function ekipmanTalebiniOnayla(talepId, ekipmanId) {
    let response;

    try {
        response = await apiFetch(`/api/equipment-requests/${encodeURIComponent(talepId)}/approve`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ ekipmanId }),
        });
    } catch {
        throw new ApiError(0, "Ekipman talebi onaylanamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) {
        throw await apiErrorOlustur(response, "Ekipman talebi onaylanamadı. Lütfen tekrar deneyin.");
    }
}

export async function ekipmanTalebiniReddet(talepId, kararNotu) {
    let response;

    try {
        response = await apiFetch(`/api/equipment-requests/${encodeURIComponent(talepId)}/reject`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ kararNotu }),
        });
    } catch {
        throw new ApiError(0, "Ekipman talebi reddedilemedi. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) {
        throw await apiErrorOlustur(response, "Ekipman talebi reddedilemedi. Lütfen tekrar deneyin.");
    }
}

function sayfaliEkipmanYonetimSorgusunuOlustur(params, alanlar) {
    const query = new URLSearchParams({ page: String(params.page), size: String(params.size) });

    alanlar.forEach((alan) => {
        if (params[alan] !== undefined && params[alan] !== null && params[alan] !== "") {
            query.set(alan, String(params[alan]));
        }
    });

    return query.toString();
}

/** @returns {Promise<import("../types/ekipman").SayfaliAdminEkipmanTipiResponse>} */
export async function adminEkipmanTipleriniGetir(params) {
    let response;

    try {
        response = await apiFetch(`/api/admin/equipment-types?${sayfaliEkipmanYonetimSorgusunuOlustur(params, ["aktif", "arama"])}`);
    } catch {
        throw new ApiError(0, "Ekipman tipleri alınamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) throw await apiErrorOlustur(response, "Ekipman tipleri alınamadı. Lütfen tekrar deneyin.");
    return response.json();
}

export async function ekipmanTipiOlustur(payload) {
    let response;

    try {
        response = await apiFetch("/api/admin/equipment-types", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
    } catch {
        throw new ApiError(0, "Ekipman tipi oluşturulamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) throw await apiErrorOlustur(response, "Ekipman tipi oluşturulamadı. Lütfen tekrar deneyin.");
    return response.json();
}

export async function ekipmanTipiniGuncelle(ekipmanTipiId, payload) {
    let response;

    try {
        response = await apiFetch(`/api/admin/equipment-types/${encodeURIComponent(String(ekipmanTipiId))}`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
    } catch {
        throw new ApiError(0, "Ekipman tipi güncellenemedi. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) throw await apiErrorOlustur(response, "Ekipman tipi güncellenemedi. Lütfen tekrar deneyin.");
}

/** @returns {Promise<import("../types/ekipman").SayfaliEkipmanResponse>} */
export async function adminEkipmanlariGetir(params) {
    let response;

    try {
        response = await apiFetch(`/api/admin/equipment?${sayfaliEkipmanYonetimSorgusunuOlustur(params, ["ekipmanTipiId", "durum", "arama"])}`);
    } catch {
        throw new ApiError(0, "Envanter alınamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) throw await apiErrorOlustur(response, "Envanter alınamadı. Lütfen tekrar deneyin.");
    return response.json();
}

export async function ekipmanOlustur(payload) {
    let response;

    try {
        response = await apiFetch("/api/admin/equipment", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
    } catch {
        throw new ApiError(0, "Ekipman oluşturulamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) throw await apiErrorOlustur(response, "Ekipman oluşturulamadı. Lütfen tekrar deneyin.");
    return response.json();
}

export async function ekipmanBilgileriniGuncelle(ekipmanId, payload) {
    let response;

    try {
        response = await apiFetch(`/api/admin/equipment/${encodeURIComponent(ekipmanId)}`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
    } catch {
        throw new ApiError(0, "Ekipman bilgileri güncellenemedi. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) throw await apiErrorOlustur(response, "Ekipman bilgileri güncellenemedi. Lütfen tekrar deneyin.");
}

export async function ekipmanDurumunuGuncelle(ekipmanId, payload) {
    let response;

    try {
        response = await apiFetch(`/api/admin/equipment/${encodeURIComponent(ekipmanId)}/status`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
    } catch {
        throw new ApiError(0, "Ekipman durumu güncellenemedi. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) throw await apiErrorOlustur(response, "Ekipman durumu güncellenemedi. Lütfen tekrar deneyin.");
}

/** @returns {Promise<import("../types/ekipman").SayfaliAdminZimmetResponse>} */
export async function adminZimmetleriGetir(params) {
    let response;

    try {
        response = await apiFetch(`/api/admin/assignments?${sayfaliEkipmanYonetimSorgusunuOlustur(params, ["aktif", "personelId", "ekipmanTipiId"])}`);
    } catch {
        throw new ApiError(0, "Zimmet kayıtları alınamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) throw await apiErrorOlustur(response, "Zimmet kayıtları alınamadı. Lütfen tekrar deneyin.");
    return response.json();
}

export async function zimmetiIadeAl(zimmetId) {
    let response;

    try {
        response = await apiFetch(`/api/admin/assignments/${encodeURIComponent(zimmetId)}/return`, { method: "PATCH" });
    } catch {
        throw new ApiError(0, "Zimmet iade alınamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) throw await apiErrorOlustur(response, "Zimmet iade alınamadı. Lütfen tekrar deneyin.");
}

/** @returns {Promise<import("../types/ekipman").SayfaliZimmetResponse>} */
export async function zimmetlerimiGetir(params) {
    let response;

    try {
        response = await apiFetch(`/api/assignments/my?page=${encodeURIComponent(String(params.page))}&size=${encodeURIComponent(String(params.size))}`);
    } catch {
        throw new ApiError(0, "Zimmetleriniz alınamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.");
    }

    if (!response.ok) {
        throw await apiErrorOlustur(response, "Zimmetleriniz alınamadı. Lütfen tekrar deneyin.");
    }

    return response.json();
}

/**
 * @param {import("../types/auditLog").AuditLogFiltreleri} params
 * @returns {Promise<import("../types/auditLog").AuditLogSayfasi>}
 */
export async function auditLoglariniGetir(params) {
    const query = new URLSearchParams();
    const filtreAlanlari = ["baslangicTarihi", "bitisTarihi", "eylemTipi", "aktorPersonelId", "hedefPersonelId"];

    filtreAlanlari.forEach((alan) => {
        if (params[alan]) query.set(alan, params[alan]);
    });
    query.set("page", String(params.page));
    query.set("size", String(params.size));

    let response;

    try {
        response = await apiFetch(`/api/admin/audit-logs?${query.toString()}`);
    } catch {
        throw new ApiError(
            0,
            "Audit kayıtları alınamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Audit kayıtları alınamadı. Lütfen tekrar deneyin.",
        );
    }

    return response.json();
}

const ekipHiyerarsisiIstekleri = new Map();
const yoneticiHiyerarsisiIstekleri = new Map();
let yoneticiZinciriIstegi = null;

function hiyerarsiSorgusunuOlustur(params = {}) {
    const query = new URLSearchParams();
    const arama = params.arama?.trim();

    if (arama) {
        query.set("arama", arama);
    }

    if (params.departmanId !== undefined) {
        query.set("departmanId", String(params.departmanId));
    }

    if (params.yetenekId !== undefined) {
        query.set("yetenekId", String(params.yetenekId));
    }

    const sorgu = query.toString();
    return sorgu ? `?${sorgu}` : "";
}

function filtreliHiyerarsiIsteginiPaylas(istekler, url) {
    const devamEdenIstek = istekler.get(url);

    if (devamEdenIstek) {
        return devamEdenIstek;
    }

    const istek = (async () => {
        let response;

        try {
            response = await apiFetch(url);
        } catch {
            throw new ApiError(
                0,
                "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
            );
        }

        if (!response.ok) {
            throw await apiErrorOlustur(
                response,
                "Yönetici hiyerarşisi alınamadı. Lütfen tekrar deneyin.",
            );
        }

        return response.json();
    })();

    const paylasilanIstek = istek.finally(() => {
        if (istekler.get(url) === paylasilanIstek) {
            istekler.delete(url);
        }
    });

    istekler.set(url, paylasilanIstek);
    return paylasilanIstek;
}

/**
 * HR/Admin için aktif organizasyon ağacını opsiyonel filtrelerle getirir.
 * Aynı filtreyle aynı anda gelen çağrılar tek API isteğinin sonucunu paylaşır.
 *
 * @param {{ arama?: string, departmanId?: number, yetenekId?: number }} [params]
 * @returns {Promise<import("../types/ekipDizini").HiyerarsiResponse>}
 */
export function ekipHiyerarsisiniGetir(params = {}) {
    const url = `/api/ekip-dizini/hiyerarsi${hiyerarsiSorgusunuOlustur(params)}`;
    return filtreliHiyerarsiIsteginiPaylas(ekipHiyerarsisiIstekleri, url);
}

/**
 * Yönetici için yalnız güvenli üst zinciri ve kendi ast ağacını getirir.
 * Aynı filtreyle aynı anda gelen çağrılar tek API isteğinin sonucunu paylaşır.
 *
 * @param {{ arama?: string, departmanId?: number, yetenekId?: number }} [params]
 * @returns {Promise<import("../types/ekipDizini").YoneticiHiyerarsisiResponse>}
 */
export function yoneticiHiyerarsimiGetir(params = {}) {
    const url = `/api/ekip-dizini/yonetici-hiyerarsim${hiyerarsiSorgusunuOlustur(params)}`;
    return filtreliHiyerarsiIsteginiPaylas(
        yoneticiHiyerarsisiIstekleri,
        url,
    );
}

/**
 * Personel/Yönetici için en yakın iki üst yönetici bilgisini getirir.
 * Aynı anda gelen çağrılar tek API isteğinin sonucunu paylaşır.
 *
 * @returns {Promise<import("../types/ekipDizini").YoneticiZinciriResponse>}
 */
export function yoneticiZincirimiGetir() {
    if (yoneticiZinciriIstegi !== null) {
        return yoneticiZinciriIstegi;
    }

    const istek = (async () => {
        let response;

        try {
            response = await apiFetch("/api/ekip-dizini/yonetici-zincirim");
        } catch {
            throw new ApiError(
                0,
                "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
            );
        }

        if (!response.ok) {
            throw await apiErrorOlustur(
                response,
                "Yönetici zinciri alınamadı. Lütfen tekrar deneyin.",
            );
        }

        return response.json();
    })();

    const paylasilanIstek = istek.finally(() => {
        if (yoneticiZinciriIstegi === paylasilanIstek) {
            yoneticiZinciriIstegi = null;
        }
    });

    yoneticiZinciriIstegi = paylasilanIstek;

    return yoneticiZinciriIstegi;
}

//logout: backend DB'deki refresh token satırını siler + Max-Age=0 ile cookie'yi imha ettirir
//cevabı kontrol etmiyoruz: logout idempotent, her durumda 204
let dashboardOzetIstegi = null;

/**
 * Dashboard kartlarının kullandığı tek özet isteğini getirir.
 * Aynı anda birden fazla kart isterse mevcut promise paylaşılır.
 *
 * @returns {Promise<import("../types/dashboard").DashboardOzeti>}
 */
export function dashboardOzetiniGetir() {
    if (dashboardOzetIstegi !== null) {
        return dashboardOzetIstegi;
    }

    dashboardOzetIstegi = (async () => {
        let response;

        try {
            response = await apiFetch("/api/dashboard/ozet");
        } catch {
            throw new ApiError(
                0,
                "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
            );
        }

        if (!response.ok) {
            throw await apiErrorOlustur(
                response,
                "Dashboard bilgileri alınamadı. Lütfen tekrar deneyin.",
            );
        }

        return response.json();
    })();

    dashboardOzetIstegi = dashboardOzetIstegi.finally(() => {
        dashboardOzetIstegi = null;
    });

    return dashboardOzetIstegi;
}

export async function logout() {
  try {
    await fetch(`${BASE_URL}/api/auth/logout`, {
      method: "POST",
      credentials: "include", //cookie gitmeli ki sunucu hangi oturumun satırını sileceğini bilsin
    });
  } finally {
    clearStoredSession();
  }
}
