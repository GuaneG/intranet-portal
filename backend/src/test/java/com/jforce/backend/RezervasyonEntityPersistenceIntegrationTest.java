package com.jforce.backend;

import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Oda;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rezervasyon;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.RezervasyonDurum;
import com.jforce.backend.model.enums.RolAdi;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RezervasyonEntityPersistenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void rezervasyonDurumKatilimciVeIptalMetadataAlanlariniKaliciSaklar() {
        Rol rol = new Rol();
        rol.setRolId(901);
        rol.setRolAdi(RolAdi.PERSONEL);
        entityManager.persist(rol);

        Departman departman = new Departman();
        departman.setDepartmanAdi("Rezervasyon Test Departmanı");
        entityManager.persist(departman);

        Personel owner = personelOlustur(
                "rezervasyon_owner",
                "rezervasyon-owner@test.local",
                rol,
                departman
        );
        Personel iptalEden = personelOlustur(
                "rezervasyon_iptal_eden",
                "rezervasyon-iptal@test.local",
                rol,
                departman
        );
        entityManager.persist(owner);
        entityManager.persist(iptalEden);

        Oda oda = new Oda();
        oda.setOdaAd("Persistence Test Odası");
        oda.setOdaKapasite(7);
        entityManager.persist(oda);

        LocalDateTime olusturmaZamani = LocalDateTime.of(2026, 9, 1, 9, 15);
        LocalDateTime iptalZamani = LocalDateTime.of(2026, 9, 1, 9, 30);

        Rezervasyon rezervasyon = new Rezervasyon();
        rezervasyon.setOlusturan(owner);
        rezervasyon.setBaslik("Sprint planlama");
        rezervasyon.setTarih(LocalDate.of(2026, 9, 15));
        rezervasyon.setBaslangicSaat(10);
        rezervasyon.setBitisSaat(12);
        rezervasyon.setOda(oda);
        rezervasyon.setKatilimciSayisi(6);
        rezervasyon.setDurum(RezervasyonDurum.IPTAL_EDILDI);
        rezervasyon.setOlusturmaZamani(olusturmaZamani);
        rezervasyon.setIptalZamani(iptalZamani);
        rezervasyon.setIptalEden(iptalEden);
        rezervasyon.setIptalNedeni("Plan değişti.");

        entityManager.persist(rezervasyon);
        entityManager.flush();
        String rezervasyonId = rezervasyon.getRezervasyonId();
        entityManager.clear();

        Rezervasyon kayitli = entityManager.find(Rezervasyon.class, rezervasyonId);
        assertEquals(owner.getPersonelId(), kayitli.getOlusturan().getPersonelId());
        assertEquals("Sprint planlama", kayitli.getBaslik());
        assertEquals(LocalDate.of(2026, 9, 15), kayitli.getTarih());
        assertEquals(10, kayitli.getBaslangicSaat());
        assertEquals(12, kayitli.getBitisSaat());
        assertEquals(oda.getOdaId(), kayitli.getOda().getOdaId());
        assertEquals(6, kayitli.getKatilimciSayisi());
        assertEquals(RezervasyonDurum.IPTAL_EDILDI, kayitli.getDurum());
        assertEquals(olusturmaZamani, kayitli.getOlusturmaZamani());
        assertEquals(iptalZamani, kayitli.getIptalZamani());
        assertEquals(iptalEden.getPersonelId(), kayitli.getIptalEden().getPersonelId());
        assertEquals("Plan değişti.", kayitli.getIptalNedeni());
    }

    private Personel personelOlustur(
            String kullaniciAdi,
            String ePosta,
            Rol rol,
            Departman departman
    ) {
        Personel personel = new Personel();
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setParolaHash("hash");
        personel.setAdi("Test");
        personel.setSoyadi("Personel");
        personel.setDogumTarihi(LocalDate.of(1990, 1, 1));
        personel.setePosta(ePosta);
        personel.setDepartman(departman);
        personel.setRol(rol);
        personel.setCalisiyorMu(true);
        personel.setIsGirisTarihi(LocalDate.of(2020, 1, 1));
        return personel;
    }
}
