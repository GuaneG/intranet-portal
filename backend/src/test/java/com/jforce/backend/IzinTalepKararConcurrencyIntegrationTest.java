package com.jforce.backend;

import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.model.dto.request.IzinTalepRedRequest;
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
import com.jforce.backend.service.IzinTalepKararService;
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
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;

class IzinTalepKararConcurrencyIntegrationTest
        extends AbstractIntegrationTest {

    private static final String TALEP_ID =
            "00000000-0000-0000-0000-000000003001";
    private static final String CAKISMA_MESAJI =
            "Talep artık işlem beklemiyor.";

    @Autowired
    private IzinTalepKararService izinTalepKararService;
    @MockitoSpyBean
    private IzinBilgileriRepository izinBilgileriRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
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
    @Autowired
    private Clock clock;

    private Personel yonetici;
    private Personel calisan;
    private Answer<?> gercekIzinRepositoryCevabi;

    @BeforeEach
    void setUp() {
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
        jdbcTemplate.update("DELETE FROM izin_turu");
        jdbcTemplate.update("DELETE FROM durum_turu");

        Rol personelRolu = rolKaydet(1, RolAdi.PERSONEL);
        Rol yoneticiRolu = rolKaydet(2, RolAdi.YONETICI);
        Departman departman = departmanKaydet("Yazılım");

        yonetici = personelKaydet(
                "karar.concurrent.yonetici",
                "Yönetici",
                yoneticiRolu,
                departman,
                null
        );
        calisan = personelKaydet(
                "karar.concurrent.calisan",
                "Çalışan",
                personelRolu,
                departman,
                yonetici
        );

        jdbcTemplate.update(
                "INSERT INTO izin_turu "
                        + "(izin_turu_id, izin_turu_adi, "
                        + "yillik_haktan_duser_mi) VALUES (1, ?, ?)",
                "Mazeret İzni",
                false
        );
        for (TalepDurum durum : TalepDurum.values()) {
            jdbcTemplate.update(
                    "INSERT INTO durum_turu (durum_adi) VALUES (?)",
                    durum.name()
            );
        }

        jdbcTemplate.update(
                """
                INSERT INTO izin_bilgileri (
                    izin_id, personel_id, izin_turu_id,
                    baslangic_tarihi, bitis_tarihi, is_gunu_sayisi,
                    yoneticiye_not, izin_durum_id, talep_tarihi
                )
                SELECT ?, ?, 1, ?, ?, ?, ?, durum_id, ?
                FROM durum_turu
                WHERE durum_adi = ?
                """,
                TALEP_ID,
                calisan.getPersonelId(),
                LocalDate.now(clock).plusDays(7),
                LocalDate.now(clock).plusDays(7),
                1.0,
                "Paralel karar testi",
                LocalDateTime.of(2026, 8, 25, 9, 0),
                TalepDurum.ONAY_BEKLIYOR.name()
        );
    }

    @AfterEach
    void tearDown() {
        reset(izinBilgileriRepository);
        SecurityContextHolder.clearContext();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void paralelOnayVeReddenYalnizBiriTamamlanir() throws Exception {
        CountDownLatch ilkTalepKilidiAlindi = new CountDownLatch(1);
        CountDownLatch ikinciKilitIstegiGeldi = new CountDownLatch(1);
        CountDownLatch ilkTransactionSerbest = new CountDownLatch(1);
        AtomicInteger kilitCagrisi = new AtomicInteger();

        doAnswer(invocation -> {
            int cagriSirasi = kilitCagrisi.incrementAndGet();
            if (cagriSirasi == 1) {
                Object sonuc = gercekKilitCagrisiniYap(invocation);
                ilkTalepKilidiAlindi.countDown();
                assertTrue(
                        ilkTransactionSerbest.await(10, TimeUnit.SECONDS),
                        "İlk karar transaction'ı serbest bırakılamadı."
                );
                return sonuc;
            }

            if (cagriSirasi == 2) {
                ikinciKilitIstegiGeldi.countDown();
            }
            return gercekKilitCagrisiniYap(invocation);
        }).when(izinBilgileriRepository).findByIdForUpdate(TALEP_ID);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Sonuc> onay = executor.submit(onayIstegi());
            boolean ilkKilitGozlendi = ilkTalepKilidiAlindi.await(
                    10,
                    TimeUnit.SECONDS
            );
            if (!ilkKilitGozlendi && onay.isDone()) {
                onay.get();
            }
            assertTrue(
                    ilkKilitGozlendi,
                    "İlk karar isteği izin talebi satırını kilitleyemedi."
            );

            Future<Sonuc> red = executor.submit(redIstegi());
            assertTrue(
                    ikinciKilitIstegiGeldi.await(10, TimeUnit.SECONDS),
                    "İkinci karar isteği kilit sorgusuna ulaşamadı."
            );
            assertFalse(
                    onay.isDone(),
                    "İlk transaction test serbest bırakmadan tamamlandı."
            );

            boolean dbKilitBeklemesiGozlendi = dbKilitBeklemesiniBekle(
                    Duration.ofSeconds(5)
            );
            if (!dbKilitBeklemesiGozlendi && red.isDone()) {
                red.get();
            }
            assertTrue(
                    dbKilitBeklemesiGozlendi,
                    "Aynı izin talebi için gerçek row-lock wait gözlenemedi."
            );

            ilkTransactionSerbest.countDown();
            List<Sonuc> sonuclar = List.of(
                    onay.get(10, TimeUnit.SECONDS),
                    red.get(10, TimeUnit.SECONDS)
            );

            assertEquals(1, sonuclar.stream()
                    .filter(sonuc -> sonuc == Sonuc.BASARILI)
                    .count());
            assertEquals(1, sonuclar.stream()
                    .filter(sonuc -> sonuc == Sonuc.CAKISMA)
                    .count());
            assertEquals(2, kilitCagrisi.get());
            assertEquals(1L, auditLogRepository.count());

            Map<String, Object> karar = jdbcTemplate.queryForMap(
                    """
                    SELECT d.durum_adi, i.islem_yapan_id, i.karar_notu
                    FROM izin_bilgileri i
                    JOIN durum_turu d
                      ON d.durum_id = i.izin_durum_id
                    WHERE i.izin_id = ?
                    """,
                    TALEP_ID
            );
            String sonDurum = karar.get("durum_adi").toString();
            assertTrue(
                    sonDurum.equals(TalepDurum.ONAYLANDI.name())
                            || sonDurum.equals(TalepDurum.REDDEDILDI.name())
            );
            assertEquals(
                    yonetici.getPersonelId(),
                    karar.get("islem_yapan_id")
            );
            if (sonDurum.equals(TalepDurum.ONAYLANDI.name())) {
                assertNull(karar.get("karar_notu"));
            } else {
                assertEquals("Paralel red.", karar.get("karar_notu"));
            }
        } finally {
            ilkTransactionSerbest.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private Callable<Sonuc> onayIstegi() {
        return kararIstegi(() -> izinTalepKararService.talebiOnayla(
                yonetici.getPersonelId(),
                TALEP_ID
        ));
    }

    private Callable<Sonuc> redIstegi() {
        return kararIstegi(() -> izinTalepKararService.talebiReddet(
                yonetici.getPersonelId(),
                TALEP_ID,
                new IzinTalepRedRequest("Paralel red.")
        ));
    }

    private Callable<Sonuc> kararIstegi(Runnable karar) {
        return () -> {
            SecurityContext context = SecurityContextHolder
                    .createEmptyContext();
            context.setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            yonetici.getPersonelId(),
                            null,
                            List.of(new SimpleGrantedAuthority(
                                    "ROLE_YONETICI"
                            ))
                    )
            );
            SecurityContextHolder.setContext(context);

            try {
                karar.run();
                return Sonuc.BASARILI;
            } catch (KaynakCakismasiException exception) {
                assertEquals(CAKISMA_MESAJI, exception.getMessage());
                return Sonuc.CAKISMA;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private Object gercekKilitCagrisiniYap(InvocationOnMock invocation)
            throws Throwable {
        return gercekIzinRepositoryCevabi.answer(invocation);
    }

    private boolean dbKilitBeklemesiniBekle(Duration timeout)
            throws SQLException {
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
                  AND requesting.OBJECT_NAME = 'izin_bilgileri'
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
                mysql.getJdbcUrl(),
                "root",
                mysql.getPassword()
        ); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, mysql.getDatabaseName());
            statement.setString(2, TALEP_ID);

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

    private Rol rolKaydet(int rolId, RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(rolId);
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
            String ad,
            Rol rol,
            Departman departman,
            Personel personelYoneticisi
    ) {
        Personel personel = new Personel();
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setParolaHash("test-hash");
        personel.setAdi(ad);
        personel.setSoyadi("Test");
        personel.setDogumTarihi(LocalDate.of(1995, 1, 1));
        personel.setePosta(kullaniciAdi + "@test.local");
        personel.setDepartman(departman);
        personel.setRol(rol);
        personel.setCalisiyorMu(true);
        personel.setIsGirisTarihi(LocalDate.of(2022, 1, 1));
        personel.setYillikIzinHakki(20);
        personel.setProfilFoto("def_pfp.png");
        personel.setParolaDegistirilmeliMi(false);
        personel.setYonetici(personelYoneticisi);
        return personelRepository.saveAndFlush(personel);
    }

    private enum Sonuc {
        BASARILI,
        CAKISMA
    }
}
