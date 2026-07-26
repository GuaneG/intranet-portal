const BASE_URL = "http://localhost:8080";

let refreshPromise = null;

//single flight prensibi
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
          throw new Error(
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
    //for debug console.log(
    //   "[apiFetch] istekYap çalıştı, token:",
    //   localStorage.getItem("authToken")?.slice(0, 15),
    // );
    return fetch(`${BASE_URL}${path}`, {
      ...options, //çağıranın verdiği method/body vs aynen korunur
      headers: {
        ...(options.headers || {}), // header içinde ayrıyetten ... kullanıyoruz çünkü headers üstüne ekleme yapmak istiyoruz onu ezmek istemiyoruz
        Authorization: `Bearer ${localStorage.getItem("token")}`,
      },
    });
  };

  let cevap = await istekYap();
  //for debug console.log("[apiFetch] ilk cevap status:", cevap.status);

  //401 DEĞİLSE iş bitti access token hala geçerli refresh dünyası hiç devreye girmez
  if (cevap.status !== 401) {
    //for debug console.log("[apiFetch] 401 değil, refresh'e gerek yok, cevap dönülüyor");
    return cevap;
  }

  //for debug console.log("[apiFetch] 401 geldi -> sessiz refresh deneniyor");
  //401 geldi: access token ölmüş olabilir -> sessiz yenilemeyi dene
  try {
    await refreshAccessToken();
    //for debug console.log("[apiFetch] refresh BAŞARILI, yeni token alındı");
  } catch (e) {
    //for debug    //console.log(
    //    //  "[apiFetch] refresh PATLADI -> oturum bitti (sızma / süre dolmuş / logout'lu), login'e atılıyor",
    //    //);
    //refresh de patladı -> oturum GERÇEKTEN bitti (logout'lu / süresi dolmuş / alarm yemiş)
    //temizlik yap, login'e yönlendir
    localStorage.removeItem("token");
    localStorage.removeItem("kullanici");
    sessionStorage.setItem("authMesaji", e.message); //sayfa yenilenince errorresponse message kaybolmasın diye köprü
    window.location.href = "/login";
    return cevap;
  }
  //for debug console.log("[apiFetch] refresh başarıyla tamamlandı istek yapılıyor");
  //refresh başarılı -> orijinal isteği yeni token'la 1 kez daha tekrarla
  return istekYap();
}

//logout: backend DB'deki refresh token satırını siler + Max-Age=0 ile cookie'yi imha ettirir
//cevabı kontrol etmiyoruz: logout idempotent, her durumda 204
export async function logout() {
  await fetch(`${BASE_URL}/api/auth/logout`, {
    method: "POST",
    credentials: "include", //cookie gitmeli ki sunucu hangi oturumun satırını sileceğini bilsin
  });
  //client tarafı temizlik
  localStorage.removeItem("token");
  localStorage.removeItem("kullanici");
}
