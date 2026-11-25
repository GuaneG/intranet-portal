package com.jforce.backend;

import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.model.dto.request.IzinTalepCreateRequest;
import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.IzinBilgileriRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.PersonelYetenekRepository;
import com.jforce.backend.repository.RefreshTokenRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.service.IzinTalepService;
import com.jforce.backend.service.IzinTalepKararService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;

@Import(IzinTalepConcurrencyIntegrationTest.SabitSaatTestConfig.class)
class IzinTalepConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final String CAKISMA_MESAJI =
            "Seçilen tarih aralığında mevcut bir izin talebiniz bulunuyor.";
    private static final LocalDate TALEP_TARIHI = LocalDate.of(2026, 8, 31);

    @Autowired
    private IzinTalepService izinTalepService;
    @Autowired
    private IzinTalepKararService izinTalepKararService;
    @MockitoSpyBean
    private IzinBilgileriRepository izinBilgileriRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @MockitoSpyBean
    private PersonelRepository personelRepository;
    @Autowired
    private PersonelYetenekRepository personelYetenekRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private DepartmanRepository departmanRepository;
    @Autowired
    private RolRepository rolRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String ilkPersonelId;
    private String ikinciPersonelId;
    private String yoneticiId;
    private Answer<?> gercekPersonelRepositoryCevabi;
    private Answer<?> gercekIzinRepositoryCevabi;

    @BeforeEach
    void setUp() {
        gercekPersonelRepositoryCevabi = mockingDetails(
                personelRepository
        ).getMockCreationSettings().getDefaultAnswer();
        gercekIzinRepositoryCevabi = mockingDetails(
                izinBilgileriRepository
        ).getMockCreationSettings().getDefaultAnswer();

        izinBilgileriRepository.deleteAll();
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        personelYetenekRepository.deleteAll();
        jdbcTemplate.update("UPDATE personel SET yonetici_id = NULL");
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM tatil");
        jdbcTemplate.update("DELETE FROM izin_turu");
        jdbcTemplate.update("DELETE FROM durum_turu");

        Rol personelRolu = new Rol();
        personelRolu.setRolId(1);
        personelRolu.setRolAdi(RolAdi.PERSONEL);
        personelRolu = rolRepository.saveAndFlush(personelRolu);

        Rol yoneticiRolu = new Rol();
        yoneticiRolu.setRolId(3);
        yoneticiRolu.setRolAdi(RolAdi.YONETICI);
        yoneticiRolu = rolRepository.saveAndFlush(yoneticiRolu);

        Departman departman = new Departman();
        departman.setDepartmanAdi("Yazılım");
        departman = departmanRepository.saveAndFlush(departman);

        Personel yonetici = personelKaydet(
                "concurrency.yonetici",
                "Yönetici",
                yoneticiRolu,
                departman,
                null
        );
        yoneticiId = yonetici.getPersonelId();

        ilkPersonelId = personelKaydet(
                "concurrency.bir",
                "Birinci",
                personelRolu,
                departman,
                yonetici
        ).getPersonelId();
        ikinciPersonelId = personelKaydet(
                "concurrency.iki",
                "İkinci",
                personelRolu,
                departman,
                null
        ).getPersonelId();

        jdbcTemplate.update(
                "INSERT INTO izin_turu "
                        + "(izin_turu_id, izin_turu_adi, "
                        + "yillik_haktan_duser_mi) VALUES (1, ?, ?)",
                "Mazeret İzni",
                false
        );
        jdbcTemplate.update(
                "INSERT INTO durum_turu (durum_adi) VALUES (?)",
                "ONAY_BEKLIYOR"
        );
    }

    @AfterEach
    void tearDown() {
        reset(personelRepository);
        reset(izinBilgileriRepository);
        SecurityContextHolder.clearContext();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void iptalVeOnayYarisindaYalnizIlkTransactionKazanir()
            throws Exception {
        jdbcTemplate.update(
                "INSERT INTO durum_turu (durum_adi) VALUES (?)",
                TalepDurum.ONAYLANDI.name()
        );
        jdbcTemplate.update(
                "INSERT INTO durum_turu (durum_adi) VALUES (?)",
                TalepDurum.IPTAL_EDILDI.name()
        );
        String talepId = "00000000-0000-0000-0000-000000008001";
        Integer bekleyenDurumId = jdbcTemplate.queryForObject(
                "SELECT durum_id FROM durum_turu WHERE durum_adi = ?",
                Integer.class,
                TalepDurum.ONAY_BEKLIYOR.name()
        );
        jdbcTemplate.update(
                """
                        INSERT INTO izin_bilgileri (
                            izin_id, personel_id, izin_turu_id,
                            baslangic_tarihi, bitis_tarihi,
                            is_gunu_sayisi, izin_durum_id, talep_tarihi
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                talepId,
                ilkPersonelId,
                1,
                TALEP_TARIHI,
                TALEP_TARIHI,
                1.0,
                bekleyenDurumId,
                "2026-08-24 10:00:00"
        );

        CountDownLatch iptalKilidiAlindi = new CountDownLatch(1);
        CountDownLatch kararKilitIstegiGeldi = new CountDownLatch(1);
        CountDownLatch iptalTransactioniniBirak = new CountDownLatch(1);
        AtomicInteger kilitCagrisi = new AtomicInteger();
        doAnswer(invocation -> {
            int sira = kilitCagrisi.incrementAndGet();
            if (sira == 1) {
                Object sonuc = gercekIzinKilitCagrisiniYap(invocation);
                iptalKilidiAlindi.countDown();
                assertTrue(
                        iptalTransactioniniBirak.await(10, TimeUnit.SECONDS),
                        "İptal transaction'ı kontrollü bırakılamadı."
                );
                return sonuc;
            }
            if (sira == 2) {
                kararKilitIstegiGeldi.countDown();
            }
            return gercekIzinKilitCagrisiniYap(invocation);
        }).when(izinBilgileriRepository).findByIdForUpdate(talepId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Sonuc> iptal = executor.submit(
                    iptalIstegi(ilkPersonelId, talepId)
            );
            assertTrue(
                    iptalKilidiAlindi.await(10, TimeUnit.SECONDS),
                    "İptal transaction'ı talep kilidini alamadı."
            );

            Future<Sonuc> onay = executor.submit(
                    onayIstegi(yoneticiId, talepId)
            );
            assertTrue(
                    kararKilitIstegiGeldi.await(10, TimeUnit.SECONDS),
                    "Karar transaction'ı talep kilit sorgusuna ulaşamadı."
            );
            assertTrue(
                    dbKilitBeklemesiniBekle(
                            "izin_bilgileri",
                            Duration.ofSeconds(5)
                    ),
                    "Karar transaction'ının gerçek talep row-lock wait "
                            + "kaydı gözlenemedi."
            );

            iptalTransactioniniBirak.countDown();
            assertEquals(Sonuc.BASARILI, iptal.get(10, TimeUnit.SECONDS));
            assertEquals(Sonuc.CAKISMA, onay.get(10, TimeUnit.SECONDS));
            assertEquals(2, kilitCagrisi.get());
            assertEquals(
                    TalepDurum.IPTAL_EDILDI.name(),
                    jdbcTemplate.queryForObject(
                            """
                                    SELECT d.durum_adi
                                    FROM izin_bilgileri i
                                    JOIN durum_turu d
                                      ON d.durum_id = i.izin_durum_id
                                    WHERE i.izin_id = ?
                                    """,
                            String.class,
                            talepId
                    )
            );
            assertEquals(1L, auditLogRepository.count());
        } finally {
            iptalTransactioniniBirak.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void ayniPersonelinParalelCakisanTaleplerindenYalnizBiriKaydedilir()
            throws Exception {
        CountDownLatch ilkKilitAlindi = new CountDownLatch(1);
        CountDownLatch ikinciKilitIstegiGeldi = new CountDownLatch(1);
        CountDownLatch ilkTransactionSerbest = new CountDownLatch(1);
        AtomicInteger ayniSatirKilitCagrisi = new AtomicInteger();

        doAnswer(invocation -> {
            int cagriSirasi = ayniSatirKilitCagrisi.incrementAndGet();
            if (cagriSirasi == 1) {
                Object sonuc = gercekKilitCagrisiniYap(invocation);
                ilkKilitAlindi.countDown();
                assertTrue(
                        ilkTransactionSerbest.await(10, TimeUnit.SECONDS),
                        "İlk transaction kontrollü biçimde serbest bırakılamadı."
                );
                return sonuc;
            }

            if (cagriSirasi == 2) {
                ikinciKilitIstegiGeldi.countDown();
                return gercekKilitCagrisiniYap(invocation);
            }
            return gercekKilitCagrisiniYap(invocation);
        }).when(personelRepository).findByIdForUpdate(ilkPersonelId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            IzinTalepCreateRequest request = talepRequesti();
            Future<Sonuc> ilk = executor.submit(istek(ilkPersonelId, request));

            boolean ilkKilitGozlendi = ilkKilitAlindi.await(
                    10,
                    TimeUnit.SECONDS
            );
            if (!ilkKilitGozlendi && ilk.isDone()) {
                ilk.get();
            }
            assertTrue(
                    ilkKilitGozlendi,
                    "İlk istek personel satır kilidini alamadı."
            );

            Future<Sonuc> ikinci = executor.submit(istek(
                    ilkPersonelId,
                    request
            ));
            assertTrue(
                    ikinciKilitIstegiGeldi.await(10, TimeUnit.SECONDS),
                    "İkinci istek aynı satırın kilit sorgusuna ulaşamadı."
            );
            assertFalse(
                    ilk.isDone(),
                    "İlk transaction test tarafından bırakılmadan tamamlandı."
            );
            boolean dbKilitBeklemesiGozlendi = dbKilitBeklemesiniBekle(
                    Duration.ofSeconds(5)
            );
            if (!dbKilitBeklemesiGozlendi && ikinci.isDone()) {
                ikinci.get();
            }
            assertTrue(
                    dbKilitBeklemesiGozlendi,
                    "İkinci transaction için gerçek personel row-lock wait "
                            + "kaydı gözlenemedi."
            );

            ilkTransactionSerbest.countDown();
            List<Sonuc> sonuclar = List.of(
                    ilk.get(10, TimeUnit.SECONDS),
                    ikinci.get(10, TimeUnit.SECONDS)
            );

            assertEquals(1, sonuclar.stream()
                    .filter(sonuc -> sonuc == Sonuc.BASARILI)
                    .count());
            assertEquals(1, sonuclar.stream()
                    .filter(sonuc -> sonuc == Sonuc.CAKISMA)
                    .count());
            assertEquals(2, ayniSatirKilitCagrisi.get());
            assertEquals(1L, izinBilgileriRepository.count());
            assertEquals(1L, auditLogRepository.count());
        } finally {
            ilkTransactionSerbest.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void farkliPersonellerAyriSatirKilitleriniBirlikteAlabilir()
            throws Exception {
        CountDownLatch ikiIstekHazir = new CountDownLatch(2);
        CountDownLatch birlikteBasla = new CountDownLatch(1);
        CountDownLatch ikiSatirKilidiAlindi = new CountDownLatch(2);
        CountDownLatch transactionlariSerbestBirak = new CountDownLatch(1);

        doAnswer(invocation -> {
            Object sonuc = gercekKilitCagrisiniYap(invocation);
            ikiSatirKilidiAlindi.countDown();
            assertTrue(
                    transactionlariSerbestBirak.await(10, TimeUnit.SECONDS),
                    "Farklı personel transaction'ları serbest bırakılamadı."
            );
            return sonuc;
        }).when(personelRepository).findByIdForUpdate(anyString());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            IzinTalepCreateRequest request = talepRequesti();
            Future<Sonuc> ilk = executor.submit(istek(
                    ilkPersonelId,
                    request,
                    ikiIstekHazir,
                    birlikteBasla
            ));
            Future<Sonuc> ikinci = executor.submit(istek(
                    ikinciPersonelId,
                    request,
                    ikiIstekHazir,
                    birlikteBasla
            ));

            assertTrue(
                    ikiIstekHazir.await(10, TimeUnit.SECONDS),
                    "İki istek paralel başlangıç noktasına ulaşamadı."
            );
            birlikteBasla.countDown();
            boolean ikiKilitGozlendi = ikiSatirKilidiAlindi.await(
                    10,
                    TimeUnit.SECONDS
            );
            if (!ikiKilitGozlendi) {
                if (ilk.isDone()) {
                    ilk.get();
                }
                if (ikinci.isDone()) {
                    ikinci.get();
                }
            }
            assertTrue(
                    ikiKilitGozlendi,
                    "İki ayrı personel satırı aynı anda kilitlenemedi."
            );

            transactionlariSerbestBirak.countDown();
            assertEquals(Sonuc.BASARILI, ilk.get(10, TimeUnit.SECONDS));
            assertEquals(Sonuc.BASARILI, ikinci.get(10, TimeUnit.SECONDS));
            assertEquals(2L, izinBilgileriRepository.count());
            assertEquals(2L, auditLogRepository.count());
        } finally {
            birlikteBasla.countDown();
            transactionlariSerbestBirak.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private Callable<Sonuc> istek(
            String personelId,
            IzinTalepCreateRequest request
    ) {
        return istek(personelId, request, null, null);
    }

    private Callable<Sonuc> iptalIstegi(
            String personelId,
            String talepId
    ) {
        return () -> izinIsleminiCalistir(
                personelId,
                RolAdi.PERSONEL,
                () -> izinTalepService.talebiIptalEt(personelId, talepId)
        );
    }

    private Callable<Sonuc> onayIstegi(
            String personelId,
            String talepId
    ) {
        return () -> izinIsleminiCalistir(
                personelId,
                RolAdi.YONETICI,
                () -> izinTalepKararService.talebiOnayla(personelId, talepId)
        );
    }

    private Sonuc izinIsleminiCalistir(
            String personelId,
            RolAdi rol,
            Runnable islem
    ) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                personelId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()))
        ));
        SecurityContextHolder.setContext(context);
        try {
            islem.run();
            return Sonuc.BASARILI;
        } catch (KaynakCakismasiException exception) {
            assertEquals("Talep artık işlem beklemiyor.", exception.getMessage());
            return Sonuc.CAKISMA;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Callable<Sonuc> istek(
            String personelId,
            IzinTalepCreateRequest request,
            CountDownLatch hazir,
            CountDownLatch basla
    ) {
        return () -> {
            SecurityContext context = SecurityContextHolder
                    .createEmptyContext();
            context.setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            personelId,
                            null,
                            List.of(new SimpleGrantedAuthority(
                                    "ROLE_PERSONEL"
                            ))
                    )
            );
            SecurityContextHolder.setContext(context);

            try {
                if (hazir != null) {
                    hazir.countDown();
                    assertTrue(
                            basla.await(10, TimeUnit.SECONDS),
                            "Paralel istekler birlikte başlatılamadı."
                    );
                }

                izinTalepService.talepOlustur(personelId, request);
                return Sonuc.BASARILI;
            } catch (KaynakCakismasiException exception) {
                assertEquals(CAKISMA_MESAJI, exception.getMessage());
                return Sonuc.CAKISMA;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private IzinTalepCreateRequest talepRequesti() {
        return new IzinTalepCreateRequest(
                1,
                TALEP_TARIHI,
                TALEP_TARIHI,
                null
        );
    }

    private Object gercekKilitCagrisiniYap(InvocationOnMock invocation)
            throws Throwable {
        return gercekPersonelRepositoryCevabi.answer(invocation);
    }

    private Object gercekIzinKilitCagrisiniYap(InvocationOnMock invocation)
            throws Throwable {
        return gercekIzinRepositoryCevabi.answer(invocation);
    }

    private boolean dbKilitBeklemesiniBekle(Duration timeout)
            throws SQLException {
        return dbKilitBeklemesiniBekle("personel", timeout);
    }

    private boolean dbKilitBeklemesiniBekle(
            String tablo,
            Duration timeout
    ) throws SQLException {
        String sql = """
                SELECT 1
                FROM performance_schema.data_lock_waits lock_wait
                JOIN performance_schema.data_locks requesting
                  ON requesting.ENGINE = lock_wait.ENGINE
                 AND requesting.ENGINE_LOCK_ID =
                     lock_wait.REQUESTING_ENGINE_LOCK_ID
                JOIN performance_schema.data_locks blocking
                  ON blocking.ENGINE = lock_wait.ENGINE
                 AND blocking.ENGINE_LOCK_ID =
                     lock_wait.BLOCKING_ENGINE_LOCK_ID
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
                  AND lock_wait.REQUESTING_ENGINE_TRANSACTION_ID <>
                      lock_wait.BLOCKING_ENGINE_TRANSACTION_ID
                LIMIT 1
                """;
        long sonAn = System.nanoTime() + timeout.toNanos();

        try (Connection connection = DriverManager.getConnection(
                mysql.getJdbcUrl(),
                "root",
                mysql.getPassword()
        ); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, mysql.getDatabaseName());
            statement.setString(2, tablo);

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

    private Personel personelKaydet(
            String kullaniciAdi,
            String ad,
            Rol rol,
            Departman departman,
            Personel yonetici
    ) {
        Personel personel = new Personel();
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setParolaHash("test-hash");
        personel.setAdi(ad);
        personel.setSoyadi("Personel");
        personel.setDogumTarihi(LocalDate.of(1995, 1, 1));
        personel.setePosta(kullaniciAdi + "@test.local");
        personel.setDepartman(departman);
        personel.setRol(rol);
        personel.setCalisiyorMu(true);
        personel.setIsGirisTarihi(LocalDate.of(2022, 1, 1));
        personel.setYillikIzinHakki(20);
        personel.setProfilFoto("def_pfp.png");
        personel.setParolaDegistirilmeliMi(false);
        personel.setYonetici(yonetici);
        return personelRepository.saveAndFlush(personel);
    }

    private enum Sonuc {
        BASARILI,
        CAKISMA
    }

    @TestConfiguration
    static class SabitSaatTestConfig {

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(
                    Instant.parse("2026-08-24T09:00:00Z"),
                    ZoneId.of("Europe/Istanbul")
            );
        }
    }
}
