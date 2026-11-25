package com.jforce.backend;

import com.jforce.backend.model.dto.request.EkipmanDurumUpdateRequest;
import com.jforce.backend.model.dto.request.EkipmanTalepOnayRequest;
import com.jforce.backend.model.dto.request.EkipmanTalepRedRequest;
import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.DurumTuru;
import com.jforce.backend.model.entity.EkipmanBilgileri;
import com.jforce.backend.model.entity.EkipmanTalep;
import com.jforce.backend.model.entity.EkipmanTipi;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.entity.ZimmetlemeBilgileri;
import com.jforce.backend.model.enums.EkipmanDurum;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.DurumTuruRepository;
import com.jforce.backend.repository.EkipmanBilgileriRepository;
import com.jforce.backend.repository.EkipmanTalepRepository;
import com.jforce.backend.repository.EkipmanTipiRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.repository.ZimmetlemeBilgileriRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class EkipmanRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EkipmanTipiRepository ekipmanTipiRepository;
    @Autowired
    private EkipmanBilgileriRepository ekipmanBilgileriRepository;
    @Autowired
    private EkipmanTalepRepository ekipmanTalepRepository;
    @Autowired
    private ZimmetlemeBilgileriRepository zimmetlemeBilgileriRepository;
    @Autowired
    private PersonelRepository personelRepository;
    @Autowired
    private DepartmanRepository departmanRepository;
    @Autowired
    private RolRepository rolRepository;
    @Autowired
    private DurumTuruRepository durumTuruRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private Validator validator;

    private EkipmanTipi monitorTipi;
    private EkipmanTipi bilgisayarTipi;
    private Personel yonetici;
    private Personel dogrudanAktif;
    private Personel dogrudanPasif;
    private Personel dolayliAktif;
    private Personel baskaTakim;
    private DurumTuru bekliyor;
    private DurumTuru onaylandi;

    @BeforeEach
    void setUp() {
        Rol personelRolu = rolKaydet(9101, RolAdi.PERSONEL);
        Rol yoneticiRolu = rolKaydet(9102, RolAdi.YONETICI);
        Departman departman = departmanKaydet("Ekipman repository testi");

        yonetici = personelKaydet(
                "repo.yonetici",
                yoneticiRolu,
                departman,
                true,
                null
        );
        dogrudanAktif = personelKaydet(
                "repo.dogrudan.aktif",
                personelRolu,
                departman,
                true,
                yonetici
        );
        dogrudanPasif = personelKaydet(
                "repo.dogrudan.pasif",
                personelRolu,
                departman,
                false,
                yonetici
        );
        dolayliAktif = personelKaydet(
                "repo.dolayli.aktif",
                personelRolu,
                departman,
                true,
                dogrudanAktif
        );
        baskaTakim = personelKaydet(
                "repo.baska.takim",
                personelRolu,
                departman,
                true,
                null
        );

        bekliyor = durumKaydet(TalepDurum.ONAY_BEKLIYOR);
        onaylandi = durumKaydet(TalepDurum.ONAYLANDI);
        monitorTipi = tipKaydet("Monitör", true);
        bilgisayarTipi = tipKaydet("Bilgisayar", true);
    }

    @Test
    void tipSorgulariAktifAlfabetikAdminFiltreVeCaseInsensitiveDuplicateSozlesmesiniKorur() {
        tipKaydet("Klavye", false);

        List<EkipmanTipi> aktifTipler = ekipmanTipiRepository
                .findAllByAktifMiTrueOrderByTipAdiAscEkipmanTipiIdAsc();
        Page<EkipmanTipi> adminSayfasi = ekipmanTipiRepository.findAdminSayfasi(
                "mon",
                true,
                PageRequest.of(0, 10)
        );

        assertEquals(List.of("Bilgisayar", "Monitör"), aktifTipler.stream()
                .map(EkipmanTipi::getTipAdi)
                .toList());
        assertEquals(List.of("Monitör"), adminSayfasi.getContent().stream()
                .map(EkipmanTipi::getTipAdi)
                .toList());
        assertEquals(1, adminSayfasi.getTotalElements());
        assertTrue(ekipmanTipiRepository.existsByTipAdiIgnoreCase("MONİTÖR"));
        assertEquals(
                monitorTipi.getEkipmanTipiId(),
                ekipmanTipiRepository.findByIdForUpdate(
                        monitorTipi.getEkipmanTipiId()
                ).orElseThrow().getEkipmanTipiId()
        );
    }

    @Test
    void uygunCihazSorgusuYalnizAyniTipDepodaKayitlariKararliVeFetchEdilmisDoner() {
        cihazKaydet(monitorTipi, "Dell", "P2422", "SER-003", EkipmanDurum.DEPODA);
        cihazKaydet(monitorTipi, "AOC", "24G2", "SER-002", EkipmanDurum.DEPODA);
        cihazKaydet(monitorTipi, "Dell", "P2422", "SER-001", EkipmanDurum.PERSONELDE);
        cihazKaydet(bilgisayarTipi, "Acer", "Swift", "SER-000", EkipmanDurum.DEPODA);
        flushVeTemizle();

        Page<EkipmanBilgileri> sonuc = ekipmanBilgileriRepository.findUygunCihazlar(
                monitorTipi.getEkipmanTipiId(),
                EkipmanDurum.DEPODA,
                PageRequest.of(0, 20)
        );
        PersistenceUnitUtil persistence = entityManager.getEntityManagerFactory()
                .getPersistenceUnitUtil();

        assertEquals(List.of("SER-002", "SER-003"), sonuc.getContent().stream()
                .map(EkipmanBilgileri::getSeriNo)
                .toList());
        assertEquals(2, sonuc.getTotalElements());
        assertTrue(sonuc.getContent().stream()
                .allMatch(e -> e.getEkipmanDurum() == EkipmanDurum.DEPODA));
        assertTrue(sonuc.getContent().stream()
                .allMatch(e -> e.getEkipmanTipiId().getEkipmanTipiId()
                        .equals(monitorTipi.getEkipmanTipiId())));
        assertTrue(sonuc.getContent().stream()
                .allMatch(e -> persistence.isLoaded(e, "ekipmanTipiId")));
    }

    @Test
    void cihazLockDuplicateVeAdminAndFiltreleriDogruCalisir() {
        EkipmanBilgileri hedef = cihazKaydet(
                monitorTipi,
                "Dell",
                "P2422",
                "LOCK-SER-1",
                EkipmanDurum.DEPODA
        );
        cihazKaydet(monitorTipi, "Dell", "U2720", "LOCK-SER-2", EkipmanDurum.KULLANIM_DISI);
        cihazKaydet(bilgisayarTipi, "Dell", "Latitude", "LOCK-SER-3", EkipmanDurum.DEPODA);
        flushVeTemizle();

        Page<EkipmanBilgileri> filtreli = ekipmanBilgileriRepository.findAdminSayfasi(
                monitorTipi.getEkipmanTipiId(),
                EkipmanDurum.DEPODA,
                "p2422",
                PageRequest.of(0, 1)
        );

        assertEquals(hedef.getEkipmanId(), ekipmanBilgileriRepository
                .findByIdForUpdate(hedef.getEkipmanId())
                .orElseThrow()
                .getEkipmanId());
        assertTrue(ekipmanBilgileriRepository.existsBySeriNoIgnoreCase("lock-ser-1"));
        assertFalse(ekipmanBilgileriRepository
                .existsBySeriNoIgnoreCaseAndEkipmanIdNot(
                        "lock-ser-1",
                        hedef.getEkipmanId()
                ));
        assertEquals(1, filtreli.getTotalElements());
        assertEquals(List.of("LOCK-SER-1"), filtreli.getContent().stream()
                .map(EkipmanBilgileri::getSeriNo)
                .toList());
    }

    @Test
    void talepSnapshotLockDuplicateVePendingOwnerListesiDogruCalisir() {
        EkipmanTalep eski = talepKaydet(
                dogrudanAktif,
                monitorTipi,
                bekliyor,
                LocalDateTime.of(2026, 8, 28, 9, 0)
        );
        EkipmanTalep yeni = talepKaydet(
                dogrudanAktif,
                bilgisayarTipi,
                bekliyor,
                LocalDateTime.of(2026, 8, 28, 10, 0)
        );
        talepKaydet(dogrudanAktif, monitorTipi, onaylandi, LocalDateTime.of(2026, 8, 27, 9, 0));
        flushVeTemizle();

        assertEquals(
                dogrudanAktif.getPersonelId(),
                ekipmanTalepRepository.findOwnerIdByTalepId(eski.getTalepId()).orElseThrow()
        );
        assertEquals(eski.getTalepId(), ekipmanTalepRepository
                .findByIdForUpdate(eski.getTalepId())
                .orElseThrow()
                .getTalepId());
        assertTrue(ekipmanTalepRepository.existsPendingByPersonelAndTip(
                dogrudanAktif.getPersonelId(),
                monitorTipi.getEkipmanTipiId(),
                TalepDurum.ONAY_BEKLIYOR
        ));
        assertFalse(ekipmanTalepRepository.existsPendingByPersonelAndTip(
                dogrudanAktif.getPersonelId(),
                monitorTipi.getEkipmanTipiId(),
                TalepDurum.REDDEDILDI
        ));

        List<String> bekleyenIds = ekipmanTalepRepository
                .findPendingByPersonelForUpdate(
                        dogrudanAktif.getPersonelId(),
                        TalepDurum.ONAY_BEKLIYOR
                )
                .stream()
                .map(EkipmanTalep::getTalepId)
                .toList();
        assertEquals(
                List.of(eski.getTalepId(), yeni.getTalepId()).stream().sorted().toList(),
                bekleyenIds
        );
    }

    @Test
    void myTeamVeAdminTalepSayfalariScopeAndFiltreCountVeFetchSozlesmesiniKorur() {
        EkipmanTalep kendiYeni = talepKaydet(
                dogrudanAktif,
                monitorTipi,
                bekliyor,
                LocalDateTime.of(2026, 8, 28, 12, 0)
        );
        EkipmanTalep kendiEski = talepKaydet(
                dogrudanAktif,
                monitorTipi,
                bekliyor,
                LocalDateTime.of(2026, 8, 28, 11, 0)
        );
        talepKaydet(dogrudanPasif, monitorTipi, bekliyor, LocalDateTime.of(2026, 8, 28, 10, 0));
        talepKaydet(dolayliAktif, monitorTipi, bekliyor, LocalDateTime.of(2026, 8, 28, 9, 0));
        talepKaydet(baskaTakim, monitorTipi, bekliyor, LocalDateTime.of(2026, 8, 28, 8, 0));
        EkipmanTalep kararliTalep = talepKaydet(
                dogrudanAktif,
                bilgisayarTipi,
                onaylandi,
                LocalDateTime.of(2026, 8, 27, 8, 0)
        );
        kararliTalep.setIslemYapanId(yonetici);
        ekipmanTalepRepository.saveAndFlush(kararliTalep);
        flushVeTemizle();

        Page<EkipmanTalep> kendi = ekipmanTalepRepository.findKendiTalepleri(
                dogrudanAktif.getPersonelId(),
                TalepDurum.ONAY_BEKLIYOR,
                monitorTipi.getEkipmanTipiId(),
                PageRequest.of(0, 1)
        );
        Page<EkipmanTalep> takim = ekipmanTalepRepository.findTakimTalepleri(
                yonetici.getPersonelId(),
                TalepDurum.ONAY_BEKLIYOR,
                monitorTipi.getEkipmanTipiId(),
                PageRequest.of(0, 20)
        );
        Page<EkipmanTalep> admin = ekipmanTalepRepository.findAdminTalepleri(
                TalepDurum.ONAY_BEKLIYOR,
                monitorTipi.getEkipmanTipiId(),
                dogrudanPasif.getPersonelId(),
                PageRequest.of(0, 20)
        );
        Page<EkipmanTalep> kararliKendi = ekipmanTalepRepository.findKendiTalepleri(
                dogrudanAktif.getPersonelId(),
                TalepDurum.ONAYLANDI,
                bilgisayarTipi.getEkipmanTipiId(),
                PageRequest.of(0, 20)
        );
        Page<EkipmanTalep> kararliTakim = ekipmanTalepRepository.findTakimTalepleri(
                yonetici.getPersonelId(),
                TalepDurum.ONAYLANDI,
                bilgisayarTipi.getEkipmanTipiId(),
                PageRequest.of(0, 20)
        );
        Page<EkipmanTalep> kararliAdmin = ekipmanTalepRepository.findAdminTalepleri(
                TalepDurum.ONAYLANDI,
                bilgisayarTipi.getEkipmanTipiId(),
                dogrudanAktif.getPersonelId(),
                PageRequest.of(0, 20)
        );
        PersistenceUnitUtil persistence = entityManager.getEntityManagerFactory()
                .getPersistenceUnitUtil();

        assertEquals(2, kendi.getTotalElements());
        assertEquals(List.of(kendiYeni.getTalepId()), kendi.getContent().stream()
                .map(EkipmanTalep::getTalepId)
                .toList());
        assertEquals(2, takim.getTotalElements());
        assertEquals(List.of(kendiYeni.getTalepId(), kendiEski.getTalepId()), takim.getContent().stream()
                .map(EkipmanTalep::getTalepId)
                .toList());
        assertEquals(1, admin.getTotalElements());
        assertEquals(dogrudanPasif.getPersonelId(), admin.getContent().getFirst()
                .getPersonel().getPersonelId());
        assertTrue(takim.getContent().stream().allMatch(t ->
                persistence.isLoaded(t, "personel")
                        && persistence.isLoaded(t.getPersonel(), "departman")
                        && persistence.isLoaded(t, "ekipmanTipi")
                        && persistence.isLoaded(t, "talepDurum")
        ));
        assertEquals(List.of(kararliTalep.getTalepId()), kararliKendi.getContent().stream()
                .map(EkipmanTalep::getTalepId)
                .toList());
        assertEquals(List.of(kararliTalep.getTalepId()), kararliTakim.getContent().stream()
                .map(EkipmanTalep::getTalepId)
                .toList());
        assertEquals(List.of(kararliTalep.getTalepId()), kararliAdmin.getContent().stream()
                .map(EkipmanTalep::getTalepId)
                .toList());
        assertTrue(kararliKendi.getContent().stream().allMatch(t ->
                persistence.isLoaded(t, "islemYapanId")
                        && yonetici.getPersonelId().equals(
                        t.getIslemYapanId().getPersonelId())
        ));
        assertTrue(kararliTakim.getContent().stream().allMatch(t ->
                persistence.isLoaded(t, "islemYapanId")
                        && yonetici.getPersonelId().equals(
                        t.getIslemYapanId().getPersonelId())
        ));
        assertTrue(kararliAdmin.getContent().stream().allMatch(t ->
                persistence.isLoaded(t, "islemYapanId")
                        && yonetici.getPersonelId().equals(
                        t.getIslemYapanId().getPersonelId())
        ));
    }

    @Test
    void zimmetSnapshotLockAcikAyrimiVeMyAdminScopeAndFiltreleriDogruCalisir() {
        EkipmanBilgileri acikCihaz = cihazKaydet(
                monitorTipi,
                "Dell",
                "P2422",
                "ZIMMET-SER-1",
                EkipmanDurum.PERSONELDE
        );
        EkipmanBilgileri kapaliCihaz = cihazKaydet(
                bilgisayarTipi,
                "Lenovo",
                "T14",
                "ZIMMET-SER-2",
                EkipmanDurum.DEPODA
        );
        EkipmanTalep acikTalep = talepKaydet(
                dogrudanAktif,
                monitorTipi,
                onaylandi,
                LocalDateTime.of(2026, 8, 20, 9, 0)
        );
        ZimmetlemeBilgileri acik = zimmetKaydet(
                dogrudanAktif,
                acikCihaz,
                acikTalep,
                LocalDateTime.of(2026, 8, 21, 9, 0),
                null
        );
        zimmetKaydet(
                dogrudanAktif,
                kapaliCihaz,
                null,
                LocalDateTime.of(2026, 8, 19, 9, 0),
                LocalDateTime.of(2026, 8, 20, 9, 0)
        );
        EkipmanBilgileri pasifCihaz = cihazKaydet(
                monitorTipi,
                "AOC",
                "24G2",
                "ZIMMET-SER-3",
                EkipmanDurum.PERSONELDE
        );
        zimmetKaydet(
                dogrudanPasif,
                pasifCihaz,
                null,
                LocalDateTime.of(2026, 8, 22, 9, 0),
                null
        );
        flushVeTemizle();

        Page<ZimmetlemeBilgileri> kendi = zimmetlemeBilgileriRepository
                .findKendiZimmetleri(
                        dogrudanAktif.getPersonelId(),
                        PageRequest.of(0, 1)
                );
        Page<ZimmetlemeBilgileri> pasifAdmin = zimmetlemeBilgileriRepository
                .findAdminZimmetleri(
                        false,
                        dogrudanPasif.getPersonelId(),
                        monitorTipi.getEkipmanTipiId(),
                        PageRequest.of(0, 20)
                );
        PersistenceUnitUtil persistence = entityManager.getEntityManagerFactory()
                .getPersistenceUnitUtil();

        assertEquals(dogrudanAktif.getPersonelId(), zimmetlemeBilgileriRepository
                .findOwnerIdByZimmetId(acik.getZimmetId())
                .orElseThrow());
        assertEquals(acik.getZimmetId(), zimmetlemeBilgileriRepository
                .findByIdForUpdate(acik.getZimmetId())
                .orElseThrow()
                .getZimmetId());
        assertTrue(zimmetlemeBilgileriRepository
                .existsByEkipmanIdAndIadeTarihiIsNull(acikCihaz.getEkipmanId()));
        assertFalse(zimmetlemeBilgileriRepository
                .existsByEkipmanIdAndIadeTarihiIsNull(kapaliCihaz.getEkipmanId()));
        assertEquals(
                List.of(acik.getZimmetId()),
                zimmetlemeBilgileriRepository.findAllByTalepIds(
                                List.of(acikTalep.getTalepId(), "olmayan-talep")
                        )
                        .stream()
                        .map(ZimmetlemeBilgileri::getZimmetId)
                        .toList()
        );
        assertEquals(
                List.of(acikCihaz.getEkipmanId()),
                zimmetlemeBilgileriRepository.findOpenEquipmentIds(
                        List.of(acikCihaz.getEkipmanId(), kapaliCihaz.getEkipmanId())
                )
        );
        assertEquals(2, kendi.getTotalElements());
        assertEquals(acik.getZimmetId(), kendi.getContent().getFirst().getZimmetId());
        assertEquals(1, pasifAdmin.getTotalElements());
        assertEquals(dogrudanPasif.getPersonelId(), pasifAdmin.getContent()
                .getFirst().getPersonelId().getPersonelId());
        assertTrue(kendi.getContent().stream().allMatch(z ->
                persistence.isLoaded(z, "ekipmanId")
                        && persistence.isLoaded(z.getEkipmanId(), "ekipmanTipiId")
        ));
    }

    @Test
    void requestDtoValidationSozlesmeleriBosAlanlariReddederUzunRedNotunuServiceeBirakir() {
        assertFalse(validator.validate(new EkipmanTalepOnayRequest("")).isEmpty());
        assertFalse(validator.validate(new EkipmanTalepRedRequest(" ")).isEmpty());
        assertTrue(validator.validate(new EkipmanTalepRedRequest("x".repeat(501))).isEmpty());
        assertFalse(validator.validate(new EkipmanDurumUpdateRequest(null, null)).isEmpty());
        assertTrue(validator.validate(
                new EkipmanDurumUpdateRequest(EkipmanDurum.KULLANIM_DISI, "Arızalı")
        ).isEmpty());
    }

    private Rol rolKaydet(int id, RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(id);
        rol.setRolAdi(rolAdi);
        return rolRepository.saveAndFlush(rol);
    }

    private Departman departmanKaydet(String ad) {
        Departman departman = new Departman();
        departman.setDepartmanAdi(ad);
        return departmanRepository.saveAndFlush(departman);
    }

    private Personel personelKaydet(
            String kullaniciAdi,
            Rol rol,
            Departman departman,
            boolean aktif,
            Personel yonetici) {
        Personel personel = new Personel();
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setParolaHash("hash");
        personel.setYonetici(yonetici);
        personel.setAdi("Test");
        personel.setSoyadi(kullaniciAdi);
        personel.setDogumTarihi(LocalDate.of(1990, 1, 1));
        personel.setePosta(kullaniciAdi + "@example.com");
        personel.setDepartman(departman);
        personel.setRol(rol);
        personel.setCalisiyorMu(aktif);
        personel.setIsGirisTarihi(LocalDate.of(2020, 1, 1));
        return personelRepository.saveAndFlush(personel);
    }

    private DurumTuru durumKaydet(TalepDurum durum) {
        DurumTuru entity = new DurumTuru();
        entity.setDurumKodu(durum);
        return durumTuruRepository.saveAndFlush(entity);
    }

    private EkipmanTipi tipKaydet(String ad, boolean aktif) {
        EkipmanTipi tip = new EkipmanTipi();
        tip.setTipAdi(ad);
        tip.setAktifMi(aktif);
        return ekipmanTipiRepository.saveAndFlush(tip);
    }

    private EkipmanBilgileri cihazKaydet(
            EkipmanTipi tip,
            String marka,
            String model,
            String seriNo,
            EkipmanDurum durum) {
        EkipmanBilgileri cihaz = new EkipmanBilgileri();
        cihaz.setEkipmanTipiId(tip);
        cihaz.setMarka(marka);
        cihaz.setModel(model);
        cihaz.setSeriNo(seriNo);
        cihaz.setEkipmanDurum(durum);
        return ekipmanBilgileriRepository.saveAndFlush(cihaz);
    }

    private EkipmanTalep talepKaydet(
            Personel owner,
            EkipmanTipi tip,
            DurumTuru durum,
            LocalDateTime talepTarihi) {
        EkipmanTalep talep = new EkipmanTalep();
        talep.setPersonel(owner);
        talep.setEkipmanTipi(tip);
        talep.setTalepDurum(durum);
        talep.setTalepTarih(talepTarihi);
        return ekipmanTalepRepository.saveAndFlush(talep);
    }

    private ZimmetlemeBilgileri zimmetKaydet(
            Personel owner,
            EkipmanBilgileri cihaz,
            EkipmanTalep talep,
            LocalDateTime teslimTarihi,
            LocalDateTime iadeTarihi) {
        ZimmetlemeBilgileri zimmet = new ZimmetlemeBilgileri();
        zimmet.setPersonelId(owner);
        zimmet.setEkipmanId(cihaz);
        zimmet.setTalep(talep);
        zimmet.setTeslimTarihi(teslimTarihi);
        zimmet.setIadeTarihi(iadeTarihi);
        return zimmetlemeBilgileriRepository.saveAndFlush(zimmet);
    }

    private void flushVeTemizle() {
        entityManager.flush();
        entityManager.clear();
    }
}
