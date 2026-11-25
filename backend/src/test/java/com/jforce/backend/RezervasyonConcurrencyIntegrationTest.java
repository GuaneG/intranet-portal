package com.jforce.backend;

import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.model.dto.request.RezervasyonRequest;
import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Oda;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.OdaRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.RezervasyonRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.service.RezervasyonService;
import com.jforce.backend.service.PersonelService;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;

@Import(RezervasyonConcurrencyIntegrationTest.SabitClockConfig.class)
class RezervasyonConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate REZERVASYON_TARIHI =
            LocalDate.of(2026, 9, 11);
    private static final String ODA_CAKISMA_MESAJI =
            "Seçilen oda bu saat aralığında dolu.";
    private static final String OWNER_CAKISMA_MESAJI =
            "Bu saat aralığında başka bir rezervasyonunuz bulunuyor.";

    @Autowired
    private RezervasyonService rezervasyonService;

    @Autowired
    private PersonelService personelService;

    @MockitoSpyBean
    private PersonelRepository personelRepository;

    @MockitoSpyBean
    private OdaRepository odaRepository;

    @Autowired
    private RezervasyonRepository rezervasyonRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private DepartmanRepository departmanRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Answer<?> gercekPersonelRepositoryCevabi;
    private Answer<?> gercekOdaRepositoryCevabi;
    private Departman departman;
    private Rol personelRolu;
    private Rol adminRolu;

    @BeforeEach
    void setUp() {
        gercekPersonelRepositoryCevabi = mockingDetails(personelRepository)
                .getMockCreationSettings()
                .getDefaultAnswer();
        gercekOdaRepositoryCevabi = mockingDetails(odaRepository)
                .getMockCreationSettings()
                .getDefaultAnswer();

        jdbcTemplate.update("DELETE FROM rezervasyon");
        jdbcTemplate.update("DELETE FROM audit_log");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM izin_bilgileri");
        jdbcTemplate.update("DELETE FROM personel_yetenek");
        jdbcTemplate.update("DELETE FROM tatil");
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();
        odaRepository.deleteAll();

        personelRolu = new Rol();
        personelRolu.setRolId(2);
        personelRolu.setRolAdi(RolAdi.PERSONEL);
        personelRolu = rolRepository.saveAndFlush(personelRolu);

        adminRolu = new Rol();
        adminRolu.setRolId(1);
        adminRolu.setRolAdi(RolAdi.ADMIN);
        adminRolu = rolRepository.saveAndFlush(adminRolu);

        departman = new Departman();
        departman.setDepartmanAdi("Concurrency Departmanı");
        departman = departmanRepository.saveAndFlush(departman);
    }

    @AfterEach
    void tearDown() {
        reset(personelRepository);
        reset(odaRepository);
    }

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void ayniOdaFarkliOwnerYarisindaYalnizBirRezervasyonOlusur()
            throws Exception {
        Personel ilkOwner = personelKaydet("oda.owner.bir");
        Personel ikinciOwner = personelKaydet("oda.owner.iki");
        Oda oda = odaKaydet("Büyük Salon", 12);

        CountDownLatch ilkOdaKilidiAlindi = new CountDownLatch(1);
        CountDownLatch ikinciOdaKilitIstegiGeldi = new CountDownLatch(1);
        CountDownLatch ilkTransactioniBirak = new CountDownLatch(1);
        AtomicInteger odaKilitCagrisi = new AtomicInteger();

        doAnswer(invocation -> {
            int sira = odaKilitCagrisi.incrementAndGet();
            if (sira == 1) {
                Object sonuc = gercekOdaKilitCagrisiniYap(invocation);
                ilkOdaKilidiAlindi.countDown();
                assertTrue(
                        ilkTransactioniBirak.await(10, TimeUnit.SECONDS),
                        "İlk oda transaction'ı kontrollü bırakılamadı."
                );
                return sonuc;
            }
            if (sira == 2) {
                ikinciOdaKilitIstegiGeldi.countDown();
            }
            return gercekOdaKilitCagrisiniYap(invocation);
        }).when(odaRepository).findByIdForUpdate(oda.getOdaId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Sonuc> ilk = executor.submit(istek(
                    ilkOwner.getPersonelId(),
                    request(oda.getOdaId(), "İlk oda isteği"),
                    ODA_CAKISMA_MESAJI
            ));
            assertTrue(
                    ilkOdaKilidiAlindi.await(10, TimeUnit.SECONDS),
                    "İlk transaction oda kilidini alamadı."
            );

            Future<Sonuc> ikinci = executor.submit(istek(
                    ikinciOwner.getPersonelId(),
                    request(oda.getOdaId(), "İkinci oda isteği"),
                    ODA_CAKISMA_MESAJI
            ));
            assertTrue(
                    ikinciOdaKilitIstegiGeldi.await(10, TimeUnit.SECONDS),
                    "İkinci transaction oda kilit sorgusuna ulaşamadı."
            );
            assertTrue(
                    dbKilitBeklemesiniBekle("oda", Duration.ofSeconds(5)),
                    "Aynı oda PRIMARY kaydı için gerçek lock wait gözlenemedi."
            );

            ilkTransactioniBirak.countDown();
            assertEquals(Sonuc.BASARILI, ilk.get(10, TimeUnit.SECONDS));
            assertEquals(Sonuc.CAKISMA, ikinci.get(10, TimeUnit.SECONDS));
            assertEquals(2, odaKilitCagrisi.get());
            assertEquals(1L, rezervasyonRepository.count());
            assertEquals(1L, auditLogRepository.count());
        } finally {
            ilkTransactioniBirak.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void ayniOwnerFarkliOdaYarisindaYalnizBirRezervasyonOlusur()
            throws Exception {
        Personel owner = personelKaydet("ortak.owner");
        Oda ilkOda = odaKaydet("Büyük Salon", 12);
        Oda ikinciOda = odaKaydet("Küçük Oda", 6);

        CountDownLatch ilkOwnerKilidiAlindi = new CountDownLatch(1);
        CountDownLatch ikinciOwnerKilitIstegiGeldi = new CountDownLatch(1);
        CountDownLatch ilkTransactioniBirak = new CountDownLatch(1);
        AtomicInteger ownerKilitCagrisi = new AtomicInteger();

        doAnswer(invocation -> {
            int sira = ownerKilitCagrisi.incrementAndGet();
            if (sira == 1) {
                Object sonuc = gercekPersonelKilitCagrisiniYap(invocation);
                ilkOwnerKilidiAlindi.countDown();
                assertTrue(
                        ilkTransactioniBirak.await(10, TimeUnit.SECONDS),
                        "İlk owner transaction'ı kontrollü bırakılamadı."
                );
                return sonuc;
            }
            if (sira == 2) {
                ikinciOwnerKilitIstegiGeldi.countDown();
            }
            return gercekPersonelKilitCagrisiniYap(invocation);
        }).when(personelRepository).findByIdForUpdate(owner.getPersonelId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Sonuc> ilk = executor.submit(istek(
                    owner.getPersonelId(),
                    request(ilkOda.getOdaId(), "İlk owner isteği"),
                    OWNER_CAKISMA_MESAJI
            ));
            assertTrue(
                    ilkOwnerKilidiAlindi.await(10, TimeUnit.SECONDS),
                    "İlk transaction owner kilidini alamadı."
            );

            Future<Sonuc> ikinci = executor.submit(istek(
                    owner.getPersonelId(),
                    request(ikinciOda.getOdaId(), "İkinci owner isteği"),
                    OWNER_CAKISMA_MESAJI
            ));
            assertTrue(
                    ikinciOwnerKilitIstegiGeldi.await(10, TimeUnit.SECONDS),
                    "İkinci transaction owner kilit sorgusuna ulaşamadı."
            );
            assertTrue(
                    dbKilitBeklemesiniBekle("personel", Duration.ofSeconds(5)),
                    "Aynı personel PRIMARY kaydı için gerçek lock wait gözlenemedi."
            );

            ilkTransactioniBirak.countDown();
            assertEquals(Sonuc.BASARILI, ilk.get(10, TimeUnit.SECONDS));
            assertEquals(Sonuc.CAKISMA, ikinci.get(10, TimeUnit.SECONDS));
            assertEquals(2, ownerKilitCagrisi.get());
            assertEquals(1L, rezervasyonRepository.count());
            assertEquals(1L, auditLogRepository.count());
        } finally {
            ilkTransactioniBirak.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void farkliOwnerVeOdalarGlobalOlarakSerilestirilmez()
            throws Exception {
        Personel ilkOwner = personelKaydet("bagimsiz.owner.bir");
        Personel ikinciOwner = personelKaydet("bagimsiz.owner.iki");
        Oda ilkOda = odaKaydet("Büyük Salon", 12);
        Oda ikinciOda = odaKaydet("Küçük Oda", 6);

        CountDownLatch ikiIstekHazir = new CountDownLatch(2);
        CountDownLatch birlikteBasla = new CountDownLatch(1);
        CountDownLatch ikiOdaKilidiAlindi = new CountDownLatch(2);
        CountDownLatch transactionlariBirak = new CountDownLatch(1);

        doAnswer(invocation -> {
            Object sonuc = gercekOdaKilitCagrisiniYap(invocation);
            ikiOdaKilidiAlindi.countDown();
            assertTrue(
                    transactionlariBirak.await(10, TimeUnit.SECONDS),
                    "Bağımsız transaction'lar kontrollü bırakılamadı."
            );
            return sonuc;
        }).when(odaRepository).findByIdForUpdate(anyInt());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Sonuc> ilk = executor.submit(istek(
                    ilkOwner.getPersonelId(),
                    request(ilkOda.getOdaId(), "Bağımsız ilk"),
                    ODA_CAKISMA_MESAJI,
                    ikiIstekHazir,
                    birlikteBasla
            ));
            Future<Sonuc> ikinci = executor.submit(istek(
                    ikinciOwner.getPersonelId(),
                    request(ikinciOda.getOdaId(), "Bağımsız ikinci"),
                    ODA_CAKISMA_MESAJI,
                    ikiIstekHazir,
                    birlikteBasla
            ));

            assertTrue(
                    ikiIstekHazir.await(10, TimeUnit.SECONDS),
                    "İki istek paralel başlangıç noktasına ulaşamadı."
            );
            birlikteBasla.countDown();
            assertTrue(
                    ikiOdaKilidiAlindi.await(10, TimeUnit.SECONDS),
                    "İki farklı oda kilidi aynı anda alınamadı."
            );

            transactionlariBirak.countDown();
            assertEquals(Sonuc.BASARILI, ilk.get(10, TimeUnit.SECONDS));
            assertEquals(Sonuc.BASARILI, ikinci.get(10, TimeUnit.SECONDS));
            assertEquals(2L, rezervasyonRepository.count());
            assertEquals(2L, auditLogRepository.count());
        } finally {
            birlikteBasla.countDown();
            transactionlariBirak.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void paralelIkiIptaldeYalnizBiriBasariliOlur() throws Exception {
        Personel owner = personelKaydet("iptal.owner");
        Oda oda = odaKaydet("Büyük Salon", 12);
        String rezervasyonId = rezervasyonService.rezervasyonOlustur(
                owner.getPersonelId(),
                request(oda.getOdaId(), "İptal yarışı")
        ).id();
        auditLogRepository.deleteAll();

        CountDownLatch ilkOwnerKilidiAlindi = new CountDownLatch(1);
        CountDownLatch ikinciOwnerKilitIstegiGeldi = new CountDownLatch(1);
        CountDownLatch ilkTransactioniBirak = new CountDownLatch(1);
        AtomicInteger ownerKilitCagrisi = new AtomicInteger();

        doAnswer(invocation -> {
            int sira = ownerKilitCagrisi.incrementAndGet();
            if (sira == 1) {
                Object sonuc = gercekPersonelKilitCagrisiniYap(invocation);
                ilkOwnerKilidiAlindi.countDown();
                assertTrue(
                        ilkTransactioniBirak.await(10, TimeUnit.SECONDS),
                        "İlk iptal transaction'ı kontrollü bırakılamadı."
                );
                return sonuc;
            }
            if (sira == 2) {
                ikinciOwnerKilitIstegiGeldi.countDown();
            }
            return gercekPersonelKilitCagrisiniYap(invocation);
        }).when(personelRepository).findByIdForUpdate(owner.getPersonelId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Sonuc> ilk = executor.submit(iptalIstegi(
                    owner.getPersonelId(), rezervasyonId
            ));
            assertTrue(
                    ilkOwnerKilidiAlindi.await(10, TimeUnit.SECONDS),
                    "İlk iptal owner kilidini alamadı."
            );
            Future<Sonuc> ikinci = executor.submit(iptalIstegi(
                    owner.getPersonelId(), rezervasyonId
            ));
            assertTrue(
                    ikinciOwnerKilitIstegiGeldi.await(10, TimeUnit.SECONDS),
                    "İkinci iptal owner kilit sorgusuna ulaşamadı."
            );
            assertTrue(
                    dbKilitBeklemesiniBekle("personel", Duration.ofSeconds(5)),
                    "Paralel iptalde gerçek owner lock wait gözlenemedi."
            );

            ilkTransactioniBirak.countDown();
            assertEquals(Sonuc.BASARILI, ilk.get(10, TimeUnit.SECONDS));
            assertEquals(Sonuc.CAKISMA, ikinci.get(10, TimeUnit.SECONDS));
            assertEquals(2, ownerKilitCagrisi.get());
            assertEquals(
                    "IPTAL_EDILDI",
                    rezervasyonRepository.findById(rezervasyonId)
                            .orElseThrow().getDurum().name()
            );
            assertEquals(1L, auditLogRepository.count());
        } finally {
            ilkTransactioniBirak.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void createOnceCommitOlursaPasiflestirmeYeniRezervasyonuIptalEder()
            throws Exception {
        Personel owner = personelKaydet("pasiflestirme.owner");
        Personel admin = personelKaydet("pasiflestirme.admin", adminRolu);
        Oda oda = odaKaydet("Büyük Salon", 12);

        CountDownLatch createOwnerKilidiAlindi = new CountDownLatch(1);
        CountDownLatch pasiflestirmeKilitIstegiGeldi = new CountDownLatch(1);
        CountDownLatch createTransactioniBirak = new CountDownLatch(1);
        AtomicInteger ownerKilitCagrisi = new AtomicInteger();

        doAnswer(invocation -> {
            int sira = ownerKilitCagrisi.incrementAndGet();
            if (sira == 1) {
                Object sonuc = gercekPersonelKilitCagrisiniYap(invocation);
                createOwnerKilidiAlindi.countDown();
                assertTrue(
                        createTransactioniBirak.await(10, TimeUnit.SECONDS),
                        "Create transaction'ı kontrollü bırakılamadı."
                );
                return sonuc;
            }
            if (sira == 2) {
                pasiflestirmeKilitIstegiGeldi.countDown();
            }
            return gercekPersonelKilitCagrisiniYap(invocation);
        }).when(personelRepository).findByIdForUpdate(owner.getPersonelId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Sonuc> create = executor.submit(istek(
                    owner.getPersonelId(),
                    request(oda.getOdaId(), "Pasifleştirme yarışı"),
                    ODA_CAKISMA_MESAJI
            ));
            assertTrue(
                    createOwnerKilidiAlindi.await(10, TimeUnit.SECONDS),
                    "Create owner kilidini alamadı."
            );
            Future<Void> pasiflestirme = executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                admin.getPersonelId(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        )
                );
                try {
                    personelService.personelPasiflestir(
                            admin.getPersonelId(), owner.getPersonelId()
                    );
                    return null;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            assertTrue(
                    pasiflestirmeKilitIstegiGeldi.await(10, TimeUnit.SECONDS),
                    "Pasifleştirme owner kilit sorgusuna ulaşamadı."
            );
            assertTrue(
                    dbKilitBeklemesiniBekle("personel", Duration.ofSeconds(5)),
                    "Create/pasifleştirme gerçek owner lock wait gözlenemedi."
            );

            createTransactioniBirak.countDown();
            assertEquals(Sonuc.BASARILI, create.get(10, TimeUnit.SECONDS));
            pasiflestirme.get(10, TimeUnit.SECONDS);

            assertEquals(false, personelRepository.findById(owner.getPersonelId())
                    .orElseThrow().getCalisiyorMu());
            assertEquals(1L, rezervasyonRepository.count());
            assertEquals(
                    "IPTAL_EDILDI",
                    rezervasyonRepository.findAll().getFirst()
                            .getDurum().name()
            );
            assertEquals(3L, auditLogRepository.count());
        } finally {
            createTransactioniBirak.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void pasiflestirmeOnceCommitOlursaBekleyenCreate403Alir()
            throws Exception {
        Personel owner = personelKaydet("once.pasiflestirme.owner");
        Personel admin = personelKaydet("once.pasiflestirme.admin", adminRolu);
        Oda oda = odaKaydet("Büyük Salon", 12);

        CountDownLatch pasiflestirmeKilidiAlindi = new CountDownLatch(1);
        CountDownLatch createKilitIstegiGeldi = new CountDownLatch(1);
        CountDownLatch pasiflestirmeyiBirak = new CountDownLatch(1);
        AtomicInteger ownerKilitCagrisi = new AtomicInteger();

        doAnswer(invocation -> {
            int sira = ownerKilitCagrisi.incrementAndGet();
            if (sira == 1) {
                Object sonuc = gercekPersonelKilitCagrisiniYap(invocation);
                pasiflestirmeKilidiAlindi.countDown();
                assertTrue(
                        pasiflestirmeyiBirak.await(10, TimeUnit.SECONDS),
                        "Pasifleştirme transaction'ı kontrollü bırakılamadı."
                );
                return sonuc;
            }
            if (sira == 2) {
                createKilitIstegiGeldi.countDown();
            }
            return gercekPersonelKilitCagrisiniYap(invocation);
        }).when(personelRepository).findByIdForUpdate(owner.getPersonelId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> pasiflestirme = executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                admin.getPersonelId(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        )
                );
                try {
                    personelService.personelPasiflestir(
                            admin.getPersonelId(), owner.getPersonelId()
                    );
                    return null;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            assertTrue(
                    pasiflestirmeKilidiAlindi.await(10, TimeUnit.SECONDS),
                    "Pasifleştirme owner kilidini alamadı."
            );

            Future<Integer> create = executor.submit(() -> {
                try {
                    rezervasyonService.rezervasyonOlustur(
                            owner.getPersonelId(),
                            request(oda.getOdaId(), "Pasif owner isteği")
                    );
                    return 201;
                } catch (org.springframework.security.access.AccessDeniedException exception) {
                    assertEquals(
                            "Bu işlem için yetkiniz bulunmuyor.",
                            exception.getMessage()
                    );
                    return 403;
                }
            });
            assertTrue(
                    createKilitIstegiGeldi.await(10, TimeUnit.SECONDS),
                    "Create owner kilit sorgusuna ulaşamadı."
            );
            assertTrue(
                    dbKilitBeklemesiniBekle("personel", Duration.ofSeconds(5)),
                    "Pasifleştirme/create gerçek owner lock wait gözlenemedi."
            );

            pasiflestirmeyiBirak.countDown();
            pasiflestirme.get(10, TimeUnit.SECONDS);
            assertEquals(403, create.get(10, TimeUnit.SECONDS));

            assertEquals(false, personelRepository.findById(owner.getPersonelId())
                    .orElseThrow().getCalisiyorMu());
            assertEquals(0L, rezervasyonRepository.count());
            assertEquals(1L, auditLogRepository.count());
        } finally {
            pasiflestirmeyiBirak.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private Callable<Sonuc> istek(
            String ownerId,
            RezervasyonRequest request,
            String beklenenCakismaMesaji
    ) {
        return istek(ownerId, request, beklenenCakismaMesaji, null, null);
    }

    private Callable<Sonuc> istek(
            String ownerId,
            RezervasyonRequest request,
            String beklenenCakismaMesaji,
            CountDownLatch hazir,
            CountDownLatch basla
    ) {
        return () -> {
            if (hazir != null) {
                hazir.countDown();
                assertTrue(
                        basla.await(10, TimeUnit.SECONDS),
                        "Paralel istekler birlikte başlatılamadı."
                );
            }

            try {
                rezervasyonService.rezervasyonOlustur(ownerId, request);
                return Sonuc.BASARILI;
            } catch (KaynakCakismasiException exception) {
                assertEquals(beklenenCakismaMesaji, exception.getMessage());
                return Sonuc.CAKISMA;
            }
        };
    }

    private RezervasyonRequest request(Integer odaId, String baslik) {
        return new RezervasyonRequest(
                odaId,
                REZERVASYON_TARIHI,
                9,
                11,
                baslik,
                3
        );
    }

    private Callable<Sonuc> iptalIstegi(String ownerId, String rezervasyonId) {
        return () -> {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            ownerId,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_PERSONEL"))
                    )
            );
            try {
                rezervasyonService.rezervasyonIptalEt(
                        ownerId, rezervasyonId, null
                );
                return Sonuc.BASARILI;
            } catch (KaynakCakismasiException exception) {
                assertEquals(
                        "Rezervasyon artık iptal edilemez.",
                        exception.getMessage()
                );
                return Sonuc.CAKISMA;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private Object gercekPersonelKilitCagrisiniYap(InvocationOnMock invocation)
            throws Throwable {
        return gercekPersonelRepositoryCevabi.answer(invocation);
    }

    private Object gercekOdaKilitCagrisiniYap(InvocationOnMock invocation)
            throws Throwable {
        return gercekOdaRepositoryCevabi.answer(invocation);
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

    private Personel personelKaydet(String kullaniciAdi) {
        return personelKaydet(kullaniciAdi, personelRolu);
    }

    private Personel personelKaydet(String kullaniciAdi, Rol rol) {
        Personel personel = new Personel();
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setParolaHash("test-hash");
        personel.setAdi("Concurrency");
        personel.setSoyadi(kullaniciAdi);
        personel.setDogumTarihi(LocalDate.of(1990, 1, 1));
        personel.setePosta(kullaniciAdi + "@test.local");
        personel.setDepartman(departman);
        personel.setRol(rol);
        personel.setCalisiyorMu(true);
        personel.setIsGirisTarihi(LocalDate.of(2022, 1, 1));
        personel.setYillikIzinHakki(20);
        personel.setProfilFoto("def_pfp.png");
        return personelRepository.saveAndFlush(personel);
    }

    private Oda odaKaydet(String ad, int kapasite) {
        Oda oda = new Oda();
        oda.setOdaAd(ad);
        oda.setOdaKapasite(kapasite);
        return odaRepository.saveAndFlush(oda);
    }

    private enum Sonuc {
        BASARILI,
        CAKISMA
    }

    @TestConfiguration
    static class SabitClockConfig {

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(
                    Instant.parse("2026-09-10T07:20:00Z"),
                    ZoneId.of("Europe/Istanbul")
            );
        }
    }
}
