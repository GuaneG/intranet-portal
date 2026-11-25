package com.jforce.backend.service;

import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.model.dto.response.DashboardOzetResponse;
import com.jforce.backend.model.dto.response.DogumGunuItem;
import com.jforce.backend.model.dto.response.EkipDiziniResponse;
import com.jforce.backend.model.dto.response.IzinBakiyesiOzetResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.repository.IzinBilgileriRepository;
import com.jforce.backend.repository.PersonelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class DashboardService {
    private final PersonelRepository personelRepository;
    private final EkipDiziniService ekipDiziniService;
    private final IzinBilgileriRepository izinBilgileriRepository;
    private final RezervasyonService rezervasyonService;
    private final Clock clock;

    public DashboardService(
            PersonelRepository personelRepository,
            EkipDiziniService ekipDiziniService,
            IzinBilgileriRepository izinBilgileriRepository,
            RezervasyonService rezervasyonService,
            Clock clock
    ) {
        this.personelRepository = personelRepository;
        this.ekipDiziniService = ekipDiziniService;
        this.izinBilgileriRepository = izinBilgileriRepository;
        this.rezervasyonService = rezervasyonService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardOzetResponse ozet(String personelId){

        //----ROL BAZLI DALLANMA YOK----- HERKESİN DASHBOARD'UNDA OLCAK DOĞUM GÜNÜ SECTİON'U
        Personel personel = personelRepository.findById(personelId).orElseThrow(() -> new KaynakBulunamadiException("Böyle bir kaynak yoktur."));

        RolAdi rol = personel.getRol().getRolAdi();

        LocalDate bugun = LocalDate.now(clock);
        // dogum gunu olanları bir DogumGunuItem olarak dashboard'da gösteriyoruz, bu yüzden bunun listesini oluşturduk bu listeyi
        //doldurmak içinde personelRepo'da query çalıştırıp gelen sonucu stream'e dönüştürüp map edip toList etmemiz lazımki listeye
        //değerleri alabilelim.
        List<DogumGunuItem> dogumGunleriOlanlar = personelRepository.findByDogumGunu(bugun.getMonthValue(),bugun.getDayOfMonth())
                .stream() //stream'e çevirdik gelen personelleri
                //doğum günü olan personelleri bir dogumgunuitem'a çevirdik
                .map(p -> new DogumGunuItem(p.getAdi() +" "+p.getSoyadi(),p.getDepartman().getDepartmanAdi(),p.getProfilFoto()))
                //listeye dönüştür //stream bitti
                .toList();

        SayfaliResponse<EkipDiziniResponse> ekipDiziniOzeti =
                ekipDiziniService.ekipDizininiGetir(
                        personelId,
                        0,
                        5,
                        null,
                        null,
                        null
                );

        IzinBakiyesiOzetResponse izinBakiyesi = izinBakiyesiOlustur(personel, rol);
        //yönetici için bekleyen taleplerim -> onay bekleyen talepleri göster

        // --- Henüz modülü olmayanlar: boş ---
        // yeni modül eklenirken ilgili service'i enjekte et, sonra list.of doldur
        return new DashboardOzetResponse(
                dogumGunleriOlanlar,
                ekipDiziniOzeti.toplamKayit(),
                ekipDiziniOzeti.icerik(),
                List.of(),   // sonDuyurular       → TODO: Duyuru modülü
                izinBakiyesi,
                0,           // bugunIzinliSayisi  → TODO: izin
                rezervasyonService.bugunkuOdaDoluluklariniGetir(),
                List.of(),   // sonTakdirler       → TODO: takdir
                List.of()    // bekleyenTaleplerim → TODO: izin/ekipman talep
        );
    }

    private IzinBakiyesiOzetResponse izinBakiyesiOlustur(
            Personel personel,
            RolAdi rol
    ) {
        if (rol == RolAdi.ADMIN
                || (rol == RolAdi.YONETICI && personel.getYonetici() == null)) {
            return null;
        }

        BigDecimal toplamHak = BigDecimal
                .valueOf(personel.getYillikIzinHakki())
                .setScale(1);
        BigDecimal kullanilan = izinBilgileriRepository
                .yillikHaktanDusenToplam(
                        personel.getPersonelId(),
                        List.of(TalepDurum.ONAYLANDI)
                )
                .setScale(1);
        BigDecimal onayBekleyen = izinBilgileriRepository
                .yillikHaktanDusenToplam(
                        personel.getPersonelId(),
                        List.of(TalepDurum.ONAY_BEKLIYOR)
                )
                .setScale(1);

        return new IzinBakiyesiOzetResponse(
                toplamHak,
                kullanilan,
                onayBekleyen,
                toplamHak.subtract(kullanilan).subtract(onayBekleyen)
        );
    }
}
