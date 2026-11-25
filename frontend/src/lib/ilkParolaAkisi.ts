const ILK_PAROLA_AKISI_ANAHTARI = "ilkParolaKullaniciAdi";

export function ilkParolaAkisiniBaslat(kullaniciAdi: string): void {
  const temizKullaniciAdi = kullaniciAdi.trim();

  if (!temizKullaniciAdi) {
    ilkParolaAkisiniTemizle();
    return;
  }

  sessionStorage.setItem(ILK_PAROLA_AKISI_ANAHTARI, temizKullaniciAdi);
}

export function ilkParolaKullaniciAdiniGetir(): string | null {
  const kullaniciAdi = sessionStorage.getItem(ILK_PAROLA_AKISI_ANAHTARI);
  const temizKullaniciAdi = kullaniciAdi?.trim();

  if (!temizKullaniciAdi) {
    ilkParolaAkisiniTemizle();
    return null;
  }

  return temizKullaniciAdi;
}

export function ilkParolaAkisiniTemizle(): void {
  sessionStorage.removeItem(ILK_PAROLA_AKISI_ANAHTARI);
}
