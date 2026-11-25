package com.jforce.backend;

import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.model.dto.request.EkipmanTalepCreateRequest;
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
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.DurumTuruRepository;
import com.jforce.backend.repository.EkipmanBilgileriRepository;
import com.jforce.backend.repository.EkipmanTalepRepository;
import com.jforce.backend.repository.EkipmanTipiRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.repository.ZimmetlemeBilgileriRepository;
import com.jforce.backend.service.EkipmanTalepKararService;
import com.jforce.backend.service.EkipmanTalepService;
import com.jforce.backend.service.PersonelService;
import com.jforce.backend.service.ZimmetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;

class EkipmanConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(5);

    @Autowired private EkipmanTalepService talepService;
    @Autowired private EkipmanTalepKararService kararService;
    @Autowired private ZimmetService zimmetService;
    @Autowired private PersonelService personelService;
    @MockitoSpyBean private PersonelRepository personelRepository;
    @MockitoSpyBean private EkipmanTalepRepository talepRepository;
    @MockitoSpyBean private EkipmanBilgileriRepository ekipmanRepository;
    @MockitoSpyBean private ZimmetlemeBilgileriRepository zimmetRepository;
    @Autowired private EkipmanTipiRepository tipRepository;
    @Autowired private DurumTuruRepository durumRepository;
    @Autowired private AuditLogRepository auditRepository;
    @Autowired private DepartmanRepository departmanRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Answer<?> gercekPersonelCevabi;
    private Answer<?> gercekEkipmanCevabi;

    private Rol personelRolu;
    private Rol yoneticiRolu;
    private Rol adminRolu;
    private Departman departman;
    private Personel yonetici;
    private Personel calisanA;
    private Personel calisanB;
    private Personel admin;
    private EkipmanTipi laptop;
    private DurumTuru bekliyor;

    @BeforeEach
    void setUp() {
        gercekPersonelCevabi = gercekCevap(personelRepository);
        gercekEkipmanCevabi = gercekCevap(ekipmanRepository);

        auditRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM zimmetleme_bilgileri");
        talepRepository.deleteAll();
        ekipmanRepository.deleteAll();
        tipRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM rezervasyon");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM personel_yetenek");
        jdbcTemplate.update("DELETE FROM izin_bilgileri");
        jdbcTemplate.update("UPDATE personel SET yonetici_id = NULL");
        personelRepository.deleteAll();
        durumRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();

        personelRolu = rolKaydet(9901, RolAdi.PERSONEL);
        yoneticiRolu = rolKaydet(9902, RolAdi.YONETICI);
        adminRolu = rolKaydet(9903, RolAdi.ADMIN);
        rolKaydet(9904, RolAdi.HR);
        departman = departmanKaydet("Ekipman Yarış Testi");
        yonetici = personelKaydet("concurrency.yonetici", yoneticiRolu, null);
        calisanA = personelKaydet("concurrency.calisan.a", personelRolu, yonetici);
        calisanB = personelKaydet("concurrency.calisan.b", personelRolu, yonetici);
        admin = personelKaydet("concurrency.admin", adminRolu, null);
        laptop = tipKaydet("Concurrency Laptop");
        for (TalepDurum durum : TalepDurum.values()) {
            DurumTuru kayit = new DurumTuru();
            kayit.setDurumKodu(durum);
            kayit = durumRepository.saveAndFlush(kayit);
            if (durum == TalepDurum.ONAY_BEKLIYOR) {
                bekliyor = kayit;
            }
        }
        auditRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        reset(personelRepository, talepRepository, ekipmanRepository, zimmetRepository);
        SecurityContextHolder.clearContext();
    }

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void paralelAyniOwnerTipCreateIslemlerindenYalnizBiriBasariliOlur()
            throws Exception {
        CountDownLatch ilkKilitAlindi = new CountDownLatch(1);
        CountDownLatch ikinciKilitIstegiGeldi = new CountDownLatch(1);
        CountDownLatch ilkTransactioniBirak = new CountDownLatch(1);
        AtomicInteger kilitCagrisi = new AtomicInteger();

        doAnswer(invocation -> siraliKilitCevabi(
                invocation,
                gercekPersonelCevabi,
                kilitCagrisi,
                ilkKilitAlindi,
                ikinciKilitIstegiGeldi,
                ilkTransactioniBirak
        )).when(personelRepository).findByIdForUpdate(calisanA.getPersonelId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Sonuc> ilk = executor.submit(createIstegi(calisanA));
            assertTrue(ilkKilitAlindi.await(10, TimeUnit.SECONDS));
            Future<Sonuc> ikinci = executor.submit(createIstegi(calisanA));
            assertTrue(ikinciKilitIstegiGeldi.await(10, TimeUnit.SECONDS));
            assertTrue(dbKilitBeklemesiniBekle(
                    "personel", calisanA.getPersonelId(), LOCK_TIMEOUT
            ));

            ilkTransactioniBirak.countDown();
            assertSonuclar(ilk, ikinci);
            assertEquals(1L, talepRepository.count());
            assertEquals(1L, auditRepository.count());
        } finally {
            ilkTransactioniBirak.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void approveRejectCancelYarisindaYalnizIptalTamamlanir()
            throws Exception {
        EkipmanTalep talep = talepKaydet(calisanA);
        EkipmanBilgileri cihaz = cihazKaydet("DECISION-1", EkipmanDurum.DEPODA);
        CountDownLatch iptalKilidiAlindi = new CountDownLatch(1);
        CountDownLatch ikiKararKilitIstegiGeldi = new CountDownLatch(2);
        CountDownLatch iptaliBirak = new CountDownLatch(1);
        AtomicInteger kilitCagrisi = new AtomicInteger();

        doAnswer(invocation -> {
            int sira = kilitCagrisi.incrementAndGet();
            if (sira == 1) {
                Object sonuc = gercekPersonelCevabi.answer(invocation);
                iptalKilidiAlindi.countDown();
                assertTrue(iptaliBirak.await(10, TimeUnit.SECONDS));
                return sonuc;
            }
            ikiKararKilitIstegiGeldi.countDown();
            return gercekPersonelCevabi.answer(invocation);
        }).when(personelRepository).findByIdForUpdate(calisanA.getPersonelId());

        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<Sonuc> iptal = executor.submit(iptalIstegi(calisanA, talep));
            assertTrue(iptalKilidiAlindi.await(10, TimeUnit.SECONDS));
            Future<Sonuc> onay = executor.submit(onayIstegi(talep, cihaz));
            Future<Sonuc> red = executor.submit(redIstegi(talep));
            assertTrue(ikiKararKilitIstegiGeldi.await(10, TimeUnit.SECONDS));
            assertTrue(dbKilitBeklemesiniBekle(
                    "personel", calisanA.getPersonelId(), LOCK_TIMEOUT
            ));

            iptaliBirak.countDown();
            assertEquals(Sonuc.BASARILI, iptal.get(10, TimeUnit.SECONDS));
            assertEquals(Sonuc.CAKISMA, onay.get(10, TimeUnit.SECONDS));
            assertEquals(Sonuc.CAKISMA, red.get(10, TimeUnit.SECONDS));
            assertEquals("IPTAL_EDILDI", talepDurumu(talep));
            assertEquals(0L, zimmetRepository.count());
            assertEquals(EkipmanDurum.DEPODA, cihazDurumu(cihaz));
            assertEquals(1L, auditRepository.count());
        } finally {
            iptaliBirak.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void ikiTalepAyniCihaziSecerseYalnizBiriZimmetOlusturur()
            throws Exception {
        EkipmanTalep talepA = talepKaydet(calisanA);
        EkipmanTalep talepB = talepKaydet(calisanB);
        EkipmanBilgileri cihaz = cihazKaydet("DEVICE-RACE-1", EkipmanDurum.DEPODA);
        CountDownLatch ilkCihazKilidiAlindi = new CountDownLatch(1);
        CountDownLatch ikinciCihazKilitIstegiGeldi = new CountDownLatch(1);
        CountDownLatch ilkOnayiBirak = new CountDownLatch(1);
        AtomicInteger kilitCagrisi = new AtomicInteger();

        doAnswer(invocation -> siraliKilitCevabi(
                invocation,
                gercekEkipmanCevabi,
                kilitCagrisi,
                ilkCihazKilidiAlindi,
                ikinciCihazKilitIstegiGeldi,
                ilkOnayiBirak
        )).when(ekipmanRepository).findByIdForUpdate(cihaz.getEkipmanId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Sonuc> ilk = executor.submit(onayIstegi(talepA, cihaz));
            assertTrue(ilkCihazKilidiAlindi.await(10, TimeUnit.SECONDS));
            Future<Sonuc> ikinci = executor.submit(onayIstegi(talepB, cihaz));
            assertTrue(ikinciCihazKilitIstegiGeldi.await(10, TimeUnit.SECONDS));
            assertTrue(dbKilitBeklemesiniBekle(
                    "ekipman_bilgileri", cihaz.getEkipmanId(), LOCK_TIMEOUT
            ));

            ilkOnayiBirak.countDown();
            assertSonuclar(ilk, ikinci);
            assertEquals(1L, zimmetRepository.count());
            assertEquals(1L, auditRepository.count());
            assertEquals(EkipmanDurum.PERSONELDE, cihazDurumu(cihaz));
            assertEquals(1L, jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM ekipman_talep t
                    JOIN durum_turu d ON d.durum_id = t.talep_durum_id
                    WHERE d.durum_adi = 'ONAYLANDI'
                    """,
                    Long.class
            ));
        } finally {
            ilkOnayiBirak.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void paralelCiftIadedenYalnizBiriTamamlanir() throws Exception {
        EkipmanBilgileri cihaz = cihazKaydet("RETURN-RACE-1", EkipmanDurum.PERSONELDE);
        ZimmetlemeBilgileri zimmet = zimmetKaydet(calisanA, cihaz, null);
        CountDownLatch ilkOwnerKilidiAlindi = new CountDownLatch(1);
        CountDownLatch ikinciOwnerKilitIstegiGeldi = new CountDownLatch(1);
        CountDownLatch ilkIadeyiBirak = new CountDownLatch(1);
        AtomicInteger kilitCagrisi = new AtomicInteger();

        doAnswer(invocation -> siraliKilitCevabi(
                invocation,
                gercekPersonelCevabi,
                kilitCagrisi,
                ilkOwnerKilidiAlindi,
                ikinciOwnerKilitIstegiGeldi,
                ilkIadeyiBirak
        )).when(personelRepository).findByIdForUpdate(calisanA.getPersonelId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Sonuc> ilk = executor.submit(iadeIstegi(zimmet));
            assertTrue(ilkOwnerKilidiAlindi.await(10, TimeUnit.SECONDS));
            Future<Sonuc> ikinci = executor.submit(iadeIstegi(zimmet));
            assertTrue(ikinciOwnerKilitIstegiGeldi.await(10, TimeUnit.SECONDS));
            assertTrue(dbKilitBeklemesiniBekle(
                    "personel", calisanA.getPersonelId(), LOCK_TIMEOUT
            ));

            ilkIadeyiBirak.countDown();
            assertSonuclar(ilk, ikinci);
            assertNotNull(jdbcTemplate.queryForObject(
                    "SELECT iade_tarihi FROM zimmetleme_bilgileri WHERE zimmet_id = ?",
                    LocalDateTime.class,
                    zimmet.getZimmetId()
            ));
            assertEquals(EkipmanDurum.DEPODA, cihazDurumu(cihaz));
            assertEquals(1L, auditRepository.count());
        } finally {
            ilkIadeyiBirak.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void onayOnceCommitOlursaPasiflestirmeAcikZimmetiKorur()
            throws Exception {
        EkipmanTalep talep = talepKaydet(calisanA);
        EkipmanBilgileri cihaz = cihazKaydet("PASSIVE-AFTER-APPROVE", EkipmanDurum.DEPODA);
        CountDownLatch onayOwnerKilidiAlindi = new CountDownLatch(1);
        CountDownLatch pasiflestirmeKilitIstegiGeldi = new CountDownLatch(1);
        CountDownLatch onayiBirak = new CountDownLatch(1);
        AtomicInteger kilitCagrisi = new AtomicInteger();

        doAnswer(invocation -> siraliKilitCevabi(
                invocation,
                gercekPersonelCevabi,
                kilitCagrisi,
                onayOwnerKilidiAlindi,
                pasiflestirmeKilitIstegiGeldi,
                onayiBirak
        )).when(personelRepository).findByIdForUpdate(calisanA.getPersonelId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Sonuc> onay = executor.submit(onayIstegi(talep, cihaz));
            assertTrue(onayOwnerKilidiAlindi.await(10, TimeUnit.SECONDS));
            Future<Sonuc> pasiflestirme = executor.submit(pasiflestirmeIstegi());
            assertTrue(pasiflestirmeKilitIstegiGeldi.await(10, TimeUnit.SECONDS));
            assertTrue(dbKilitBeklemesiniBekle(
                    "personel", calisanA.getPersonelId(), LOCK_TIMEOUT
            ));

            onayiBirak.countDown();
            assertEquals(Sonuc.BASARILI, onay.get(10, TimeUnit.SECONDS));
            assertEquals(Sonuc.BASARILI, pasiflestirme.get(10, TimeUnit.SECONDS));
            assertFalse(aktifMi(calisanA));
            assertEquals("ONAYLANDI", talepDurumu(talep));
            assertEquals(1L, acikZimmetSayisi(calisanA));
            assertEquals(EkipmanDurum.PERSONELDE, cihazDurumu(cihaz));
            assertEquals(2L, auditRepository.count());
        } finally {
            onayiBirak.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void pasiflestirmeOnceCommitOlursaOnayKontrolluReddedilir()
            throws Exception {
        EkipmanTalep talep = talepKaydet(calisanA);
        EkipmanBilgileri cihaz = cihazKaydet("PASSIVE-BEFORE-APPROVE", EkipmanDurum.DEPODA);
        CountDownLatch pasifOwnerKilidiAlindi = new CountDownLatch(1);
        CountDownLatch onayKilitIstegiGeldi = new CountDownLatch(1);
        CountDownLatch pasiflestirmeyiBirak = new CountDownLatch(1);
        AtomicInteger kilitCagrisi = new AtomicInteger();

        doAnswer(invocation -> siraliKilitCevabi(
                invocation,
                gercekPersonelCevabi,
                kilitCagrisi,
                pasifOwnerKilidiAlindi,
                onayKilitIstegiGeldi,
                pasiflestirmeyiBirak
        )).when(personelRepository).findByIdForUpdate(calisanA.getPersonelId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Sonuc> pasiflestirme = executor.submit(pasiflestirmeIstegi());
            assertTrue(pasifOwnerKilidiAlindi.await(10, TimeUnit.SECONDS));
            Future<Sonuc> onay = executor.submit(onayIstegi(talep, cihaz));
            assertTrue(onayKilitIstegiGeldi.await(10, TimeUnit.SECONDS));
            assertTrue(dbKilitBeklemesiniBekle(
                    "personel", calisanA.getPersonelId(), LOCK_TIMEOUT
            ));

            pasiflestirmeyiBirak.countDown();
            assertEquals(Sonuc.BASARILI, pasiflestirme.get(10, TimeUnit.SECONDS));
            assertEquals(Sonuc.CAKISMA, onay.get(10, TimeUnit.SECONDS));
            assertFalse(aktifMi(calisanA));
            assertEquals("IPTAL_EDILDI", talepDurumu(talep));
            assertEquals(0L, acikZimmetSayisi(calisanA));
            assertEquals(EkipmanDurum.DEPODA, cihazDurumu(cihaz));
            assertEquals(2L, auditRepository.count());
        } finally {
            pasiflestirmeyiBirak.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private Callable<Sonuc> createIstegi(Personel owner) {
        return islem(owner, RolAdi.PERSONEL, () -> talepService.talepOlustur(
                owner.getPersonelId(),
                new EkipmanTalepCreateRequest(laptop.getEkipmanTipiId(), "Paralel create")
        ), Set.of("Aynı ekipman tipi için bekleyen talep zaten mevcut."));
    }

    private Callable<Sonuc> iptalIstegi(Personel owner, EkipmanTalep talep) {
        return islem(owner, RolAdi.PERSONEL, () -> talepService.talepIptalEt(
                owner.getPersonelId(), talep.getTalepId()
        ), Set.of("Talep artık işlem beklemiyor."));
    }

    private Callable<Sonuc> onayIstegi(EkipmanTalep talep, EkipmanBilgileri cihaz) {
        return islem(yonetici, RolAdi.YONETICI, () -> kararService.talebiOnayla(
                yonetici.getPersonelId(),
                talep.getTalepId(),
                new EkipmanTalepOnayRequest(cihaz.getEkipmanId())
        ), Set.of(
                "Talep artık işlem beklemiyor.",
                "Talep sahibi artık aktif değil.",
                "Ekipman artık zimmete uygun değil."
        ));
    }

    private Callable<Sonuc> redIstegi(EkipmanTalep talep) {
        return islem(yonetici, RolAdi.YONETICI, () -> kararService.talebiReddet(
                yonetici.getPersonelId(),
                talep.getTalepId(),
                new EkipmanTalepRedRequest("Paralel red")
        ), Set.of("Talep artık işlem beklemiyor.", "Talep sahibi artık aktif değil."));
    }

    private Callable<Sonuc> iadeIstegi(ZimmetlemeBilgileri zimmet) {
        return islem(admin, RolAdi.ADMIN, () -> zimmetService.zimmetiIadeEt(
                admin.getPersonelId(), zimmet.getZimmetId()
        ), Set.of("Zimmet artık açık değil."));
    }

    private Callable<Sonuc> pasiflestirmeIstegi() {
        return islem(admin, RolAdi.ADMIN, () -> personelService.personelPasiflestir(
                admin.getPersonelId(), calisanA.getPersonelId()
        ), Set.of());
    }

    private Callable<Sonuc> islem(
            Personel aktor,
            RolAdi rol,
            Runnable islem,
            Set<String> beklenenCakismaMesajlari
    ) {
        return () -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    aktor.getPersonelId(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()))
            ));
            SecurityContextHolder.setContext(context);
            try {
                islem.run();
                return Sonuc.BASARILI;
            } catch (KaynakCakismasiException exception) {
                assertTrue(
                        beklenenCakismaMesajlari.contains(exception.getMessage()),
                        () -> "Beklenmeyen conflict mesajı: " + exception.getMessage()
                );
                return Sonuc.CAKISMA;
            } catch (KaynakBulunamadiException exception) {
                assertEquals("Ekipman talebi bulunamadı.", exception.getMessage());
                return Sonuc.CAKISMA;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private Object siraliKilitCevabi(
            InvocationOnMock invocation,
            Answer<?> gercekCevap,
            AtomicInteger cagriSayisi,
            CountDownLatch ilkKilitAlindi,
            CountDownLatch ikinciKilitIstegiGeldi,
            CountDownLatch ilkTransactioniBirak
    ) throws Throwable {
        int sira = cagriSayisi.incrementAndGet();
        if (sira == 1) {
            Object sonuc = gercekCevap.answer(invocation);
            ilkKilitAlindi.countDown();
            assertTrue(
                    ilkTransactioniBirak.await(10, TimeUnit.SECONDS),
                    "İlk transaction kontrollü biçimde serbest bırakılamadı."
            );
            return sonuc;
        }
        if (sira == 2) {
            ikinciKilitIstegiGeldi.countDown();
        }
        return gercekCevap.answer(invocation);
    }

    private void assertSonuclar(Future<Sonuc> ilk, Future<Sonuc> ikinci)
            throws Exception {
        List<Sonuc> sonuclar = List.of(
                ilk.get(10, TimeUnit.SECONDS),
                ikinci.get(10, TimeUnit.SECONDS)
        );
        assertEquals(1, sonuclar.stream().filter(s -> s == Sonuc.BASARILI).count());
        assertEquals(1, sonuclar.stream().filter(s -> s == Sonuc.CAKISMA).count());
    }

    private boolean dbKilitBeklemesiniBekle(
            String tablo,
            String satirId,
            Duration timeout
    ) throws SQLException {
        String sql = """
                SELECT 1
                FROM performance_schema.data_lock_waits lock_wait
                JOIN performance_schema.data_locks requesting
                  ON requesting.ENGINE = lock_wait.ENGINE
                 AND requesting.ENGINE_LOCK_ID = lock_wait.REQUESTING_ENGINE_LOCK_ID
                JOIN performance_schema.data_locks blocking
                  ON blocking.ENGINE = lock_wait.ENGINE
                 AND blocking.ENGINE_LOCK_ID = lock_wait.BLOCKING_ENGINE_LOCK_ID
                WHERE requesting.OBJECT_SCHEMA = ?
                  AND requesting.OBJECT_NAME = ?
                  AND blocking.OBJECT_SCHEMA = requesting.OBJECT_SCHEMA
                  AND blocking.OBJECT_NAME = requesting.OBJECT_NAME
                  AND requesting.INDEX_NAME = 'PRIMARY'
                  AND blocking.INDEX_NAME = 'PRIMARY'
                  AND requesting.LOCK_TYPE = 'RECORD'
                  AND blocking.LOCK_TYPE = 'RECORD'
                  AND requesting.LOCK_STATUS = 'WAITING'
                  AND blocking.LOCK_STATUS = 'GRANTED'
                  AND requesting.LOCK_DATA = blocking.LOCK_DATA
                  AND requesting.LOCK_DATA LIKE CONCAT('%', ?, '%')
                  AND lock_wait.REQUESTING_ENGINE_TRANSACTION_ID <>
                      lock_wait.BLOCKING_ENGINE_TRANSACTION_ID
                LIMIT 1
                """;
        long sonAn = System.nanoTime() + timeout.toNanos();
        try (Connection connection = DriverManager.getConnection(
                mysql.getJdbcUrl(), "root", mysql.getPassword()
        ); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, mysql.getDatabaseName());
            statement.setString(2, tablo);
            statement.setString(3, satirId);
            do {
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return true;
                    }
                }
                Thread.yield();
            } while (System.nanoTime() < sonAn);
        }
        return false;
    }

    private Answer<?> gercekCevap(Object spy) {
        return mockingDetails(spy).getMockCreationSettings().getDefaultAnswer();
    }

    private EkipmanTalep talepKaydet(Personel owner) {
        EkipmanTalep talep = new EkipmanTalep();
        talep.setPersonel(owner);
        talep.setEkipmanTipi(laptop);
        talep.setAciklama("Concurrency talebi");
        talep.setTalepDurum(bekliyor);
        talep.setTalepTarih(LocalDateTime.of(2026, 8, 31, 10, 0));
        return talepRepository.saveAndFlush(talep);
    }

    private EkipmanBilgileri cihazKaydet(String seriNo, EkipmanDurum durum) {
        EkipmanBilgileri cihaz = new EkipmanBilgileri();
        cihaz.setEkipmanTipiId(laptop);
        cihaz.setMarka("Test");
        cihaz.setModel("Concurrency");
        cihaz.setSeriNo(seriNo);
        cihaz.setEkipmanDurum(durum);
        return ekipmanRepository.saveAndFlush(cihaz);
    }

    private ZimmetlemeBilgileri zimmetKaydet(
            Personel owner,
            EkipmanBilgileri cihaz,
            EkipmanTalep talep
    ) {
        ZimmetlemeBilgileri zimmet = new ZimmetlemeBilgileri();
        zimmet.setPersonelId(owner);
        zimmet.setEkipmanId(cihaz);
        zimmet.setTalep(talep);
        zimmet.setTeslimTarihi(LocalDateTime.of(2026, 8, 30, 10, 0));
        return zimmetRepository.saveAndFlush(zimmet);
    }

    private String talepDurumu(EkipmanTalep talep) {
        return jdbcTemplate.queryForObject(
                """
                SELECT d.durum_adi FROM ekipman_talep t
                JOIN durum_turu d ON d.durum_id = t.talep_durum_id
                WHERE t.talep_id = ?
                """,
                String.class,
                talep.getTalepId()
        );
    }

    private EkipmanDurum cihazDurumu(EkipmanBilgileri cihaz) {
        return EkipmanDurum.valueOf(jdbcTemplate.queryForObject(
                "SELECT ekipman_durum FROM ekipman_bilgileri WHERE ekipman_id = ?",
                String.class,
                cihaz.getEkipmanId()
        ));
    }

    private boolean aktifMi(Personel personel) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT calisiyor_mu FROM personel WHERE personel_id = ?",
                Boolean.class,
                personel.getPersonelId()
        ));
    }

    private long acikZimmetSayisi(Personel owner) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM zimmetleme_bilgileri
                WHERE personel_id = ? AND iade_tarihi IS NULL
                """,
                Long.class,
                owner.getPersonelId()
        );
    }

    private Rol rolKaydet(int id, RolAdi ad) {
        Rol rol = new Rol();
        rol.setRolId(id);
        rol.setRolAdi(ad);
        return rolRepository.saveAndFlush(rol);
    }

    private Departman departmanKaydet(String ad) {
        Departman kayit = new Departman();
        kayit.setDepartmanAdi(ad);
        return departmanRepository.saveAndFlush(kayit);
    }

    private Personel personelKaydet(String kullaniciAdi, Rol rol, Personel manager) {
        Personel personel = new Personel();
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setParolaHash("test-hash");
        personel.setAdi("Test");
        personel.setSoyadi(kullaniciAdi);
        personel.setDogumTarihi(LocalDate.of(1995, 1, 1));
        personel.setePosta(kullaniciAdi + "@test.local");
        personel.setDepartman(departman);
        personel.setRol(rol);
        personel.setCalisiyorMu(true);
        personel.setIsGirisTarihi(LocalDate.of(2022, 1, 1));
        personel.setYillikIzinHakki(20);
        personel.setProfilFoto("def_pfp.png");
        personel.setParolaDegistirilmeliMi(false);
        personel.setYonetici(manager);
        return personelRepository.saveAndFlush(personel);
    }

    private EkipmanTipi tipKaydet(String ad) {
        EkipmanTipi tip = new EkipmanTipi();
        tip.setTipAdi(ad);
        tip.setAktifMi(true);
        return tipRepository.saveAndFlush(tip);
    }

    private enum Sonuc {
        BASARILI,
        CAKISMA
    }
}
