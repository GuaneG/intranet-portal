package com.jforce.backend.service;

import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.model.dto.response.DashboardOzetResponse;
import com.jforce.backend.model.dto.response.DogumGunuItem;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.PersonelRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class DashboardService {
    private final PersonelRepository personelRepository;

    public DashboardService(PersonelRepository personelRepository) {
        this.personelRepository = personelRepository;
    }

    //personel'e özel dashboard olacağı için özet dashboard dışarıdan personelId alıcak.
    //geçici bir place holder RuntimeException koydum TODO: Placeholder exception yerine ne konulabilir ona bak.
    public DashboardOzetResponse ozet(String personelId){

        //----ROL BAZLI DALLANMA YOK----- HERKESİN DASHBOARD'UNDA OLCAK DOĞUM GÜNÜ SECTİON'U
        Personel personel = personelRepository.findById(personelId).orElseThrow(() -> new KaynakBulunamadiException("Böyle bir kaynak yoktur."));

        RolAdi rol = personel.getRol().getRolAdi();

        LocalDate bugun = LocalDate.now();
        // dogum gunu olanları bir DogumGunuItem olarak dashboard'da gösteriyoruz, bu yüzden bunun listesini oluşturduk bu listeyi
        //doldurmak içinde personelRepo'da query çalıştırıp gelen sonucu stream'e dönüştürüp map edip toList etmemiz lazımki listeye
        //değerleri alabilelim.
        List<DogumGunuItem> dogumGunleriOlanlar = personelRepository.findByDogumGunu(bugun.getMonthValue(),bugun.getDayOfMonth())
                .stream() //stream'e çevirdik gelen personelleri
                //doğum günü olan personelleri bir dogumgunuitem'a çevirdik
                .map(p -> new DogumGunuItem(p.getAdi() +" "+p.getSoyadi(),p.getDepartman().getDepartmanAdi(),p.getProfilFoto()))
                //listeye dönüştür //stream bitti
                .toList();

        //-----ROL BAZLI DALLANMA ----
        //TODO: ŞİMDİLİK BÜTÜN ROL BAZLI DALLANMALAR PLACEHOLDER OLARAK KONULCAK MODÜL BİTTİKCE BURAYA DOLDUR
        Integer kalanIzin = (rol == RolAdi.PERSONEL) ? null /*TODO: İZİN HESABI*/  : null;
        //yönetici için bekleyen taleplerim -> onay bekleyen talepleri göster

        // --- Henüz modülü olmayanlar: boş ---
        // yeni modül eklenirken ilgili service'i enjekte et, sonra list.of doldur
        return new DashboardOzetResponse(
                dogumGunleriOlanlar,
                List.of(),   // sonDuyurular       → TODO: Duyuru modülü
                kalanIzin,   //                    → TODO: Yukardaki kalanIzin objesi tamamlanılcak
                0,           // bugunIzinliSayisi  → TODO: izin
                List.of(),   // odaDoluluk         → TODO: rezervasyon
                List.of(),   // sonTakdirler       → TODO: takdir
                List.of()    // bekleyenTaleplerim → TODO: izin/ekipman talep
        );
    }
}
