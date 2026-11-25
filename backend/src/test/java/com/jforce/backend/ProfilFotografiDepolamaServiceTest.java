package com.jforce.backend;

import com.jforce.backend.exception.DesteklenmeyenDosyaTuruException;
import com.jforce.backend.exception.DosyaCokBuyukException;
import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.service.ProfilFotografiDosyasi;
import com.jforce.backend.service.ProfilFotografiDepolamaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfilFotografiDepolamaServiceTest {

    @TempDir
    Path depolamaDizini;

    @Test
    void gecerliJpegUuidAnahtariylaDepolamaKokuneYazilir() throws Exception {
        ProfilFotografiDepolamaService service = service();
        MockMultipartFile dosya = jpegDosyasi("portre.jpg", 320, 240);

        String anahtar = service.kaydet(dosya);

        assertTrue(anahtar.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.jpg"
        ));
        assertTrue(Files.isRegularFile(depolamaDizini.resolve(anahtar)));
    }

    @Test
    void uuidCakismasindaMevcutHedefDegistirilmez() throws Exception {
        UUID cakisanUuid = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID yeniUuid = UUID.fromString("22222222-2222-4222-8222-222222222222");
        Deque<UUID> uuidler = new ArrayDeque<>(List.of(cakisanUuid, yeniUuid));
        ProfilFotografiDepolamaService service = new ProfilFotografiDepolamaService(
                depolamaDizini.toString(),
                uuidler::removeFirst
        );
        Path mevcutHedef = depolamaDizini.resolve(cakisanUuid + ".jpg");
        byte[] mevcutIcerik = "mevcut-fotograf".getBytes(StandardCharsets.UTF_8);
        Files.write(mevcutHedef, mevcutIcerik);

        String anahtar = service.kaydet(jpegDosyasi("portre.jpg", 20, 20));

        assertEquals(yeniUuid + ".jpg", anahtar);
        assertArrayEquals(mevcutIcerik, Files.readAllBytes(mevcutHedef));
        assertTrue(Files.isRegularFile(depolamaDizini.resolve(anahtar)));
    }

    @Test
    void gecerliPngAlfaKanaliniKoruyarakYenidenYazilir() throws Exception {
        ProfilFotografiDepolamaService service = service();
        MockMultipartFile dosya = pngDosyasi("portre.png", 40, 30, 64);

        String anahtar = service.kaydet(dosya);
        BufferedImage saklanan = ImageIO.read(depolamaDizini.resolve(anahtar).toFile());

        assertTrue(anahtar.endsWith(".png"));
        assertNotNull(saklanan);
        assertTrue(saklanan.getColorModel().hasAlpha());
        assertEquals(64, (saklanan.getRGB(10, 10) >>> 24) & 0xff);
    }

    @Test
    void uzunKenar1024OlacakSekildeOranKorunur() throws Exception {
        ProfilFotografiDepolamaService service = service();

        String anahtar = service.kaydet(jpegDosyasi("yatay.jpg", 1600, 800));
        BufferedImage saklanan = ImageIO.read(depolamaDizini.resolve(anahtar).toFile());

        assertEquals(1024, saklanan.getWidth());
        assertEquals(512, saklanan.getHeight());
    }

    @Test
    void kucukGorselBuyutulmez() throws Exception {
        ProfilFotografiDepolamaService service = service();

        String anahtar = service.kaydet(pngDosyasi("kucuk.png", 320, 200, 255));
        BufferedImage saklanan = ImageIO.read(depolamaDizini.resolve(anahtar).toFile());

        assertEquals(320, saklanan.getWidth());
        assertEquals(200, saklanan.getHeight());
    }

    @Test
    void nullVeyaBosDosyaReddedilir() {
        ProfilFotografiDepolamaService service = service();
        MockMultipartFile bos = new MockMultipartFile(
                "dosya",
                "bos.jpg",
                "image/jpeg",
                new byte[0]
        );

        assertThrows(GecersizIstekException.class, () -> service.kaydet(null));
        assertThrows(GecersizIstekException.class, () -> service.kaydet(bos));
    }

    @Test
    void ikiMegabayttanBuyukDosyaDecodeEdilmedenReddedilir() {
        ProfilFotografiDepolamaService service = service();
        MockMultipartFile buyuk = new MockMultipartFile(
                "dosya",
                "buyuk.jpg",
                "image/jpeg",
                new byte[(2 * 1024 * 1024) + 1]
        );

        assertThrows(DosyaCokBuyukException.class, () -> service.kaydet(buyuk));
    }

    @Test
    void ikiBinKirkDokuzPikselGenislikReddedilir() throws Exception {
        ProfilFotografiDepolamaService service = service();

        MockMultipartFile dosya = pngDosyasi("genis.png", 2049, 1, 255);

        assertThrows(GecersizIstekException.class, () -> service.kaydet(dosya));
    }

    @ParameterizedTest
    @ValueSource(strings = {"portre.gif", "portre.svg", "portre.webp", "portre"})
    void desteklenmeyenUzantiReddedilir(String dosyaAdi) throws Exception {
        ProfilFotografiDepolamaService service = service();
        MockMultipartFile png = pngDosyasi(dosyaAdi, 20, 20, 255);

        assertThrows(
                DesteklenmeyenDosyaTuruException.class,
                () -> service.kaydet(png)
        );
    }

    @Test
    void uzantiIleBildirilenMimeUyusmazsaReddedilir() throws Exception {
        ProfilFotografiDepolamaService service = service();
        MockMultipartFile png = pngDosyasi("portre.png", 20, 20, 255);
        MockMultipartFile sahteMime = new MockMultipartFile(
                "dosya",
                "portre.jpg",
                "image/png",
                png.getBytes()
        );

        assertThrows(
                DesteklenmeyenDosyaTuruException.class,
                () -> service.kaydet(sahteMime)
        );
    }

    @Test
    void uzantiVeMimeIleDosyaImzasiUyusmazsaReddedilir() throws Exception {
        ProfilFotografiDepolamaService service = service();
        MockMultipartFile jpeg = jpegDosyasi("portre.jpg", 20, 20);
        MockMultipartFile sahteImza = new MockMultipartFile(
                "dosya",
                "portre.png",
                "image/png",
                jpeg.getBytes()
        );

        assertThrows(
                DesteklenmeyenDosyaTuruException.class,
                () -> service.kaydet(sahteImza)
        );
    }

    @Test
    void dogruImzaliFakatBozukGorselKontrolluReddedilir() {
        ProfilFotografiDepolamaService service = service();
        byte[] bozukPng = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47,
                0x0d, 0x0a, 0x1a, 0x0a,
                0x01, 0x02, 0x03
        };
        MockMultipartFile dosya = new MockMultipartFile(
                "dosya",
                "bozuk.png",
                "image/png",
                bozukPng
        );

        assertThrows(GecersizIstekException.class, () -> service.kaydet(dosya));
    }

    @Test
    void pngSonunaEklenenScriptKaliciDosyayaTasinmaz() throws Exception {
        ProfilFotografiDepolamaService service = service();
        MockMultipartFile temizPng = pngDosyasi("portre.png", 30, 30, 255);
        byte[] payload = "<script>alert('x')</script>".getBytes();
        byte[] yuklenen = Arrays.copyOf(
                temizPng.getBytes(),
                (int) temizPng.getSize() + payload.length
        );
        System.arraycopy(payload, 0, yuklenen, (int) temizPng.getSize(), payload.length);

        String anahtar = service.kaydet(new MockMultipartFile(
                "dosya",
                "portre.png",
                "image/png",
                yuklenen
        ));
        byte[] saklanan = Files.readAllBytes(depolamaDizini.resolve(anahtar));

        assertFalse(iceriyorMu(saklanan, payload));
        assertNotNull(ImageIO.read(depolamaDizini.resolve(anahtar).toFile()));
    }

    @Test
    void orijinalTraversalDosyaAdiCiktiYolunuBelirleyemez() throws Exception {
        Path storage = depolamaDizini.resolve("bir").resolve("iki");
        ProfilFotografiDepolamaService service =
                new ProfilFotografiDepolamaService(storage.toString());

        String anahtar = service.kaydet(jpegDosyasi("../../hedef.jpg", 20, 20));

        assertTrue(anahtar.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.jpg"
        ));
        assertTrue(Files.exists(storage.resolve(anahtar)));
        assertFalse(Files.exists(depolamaDizini.resolve("hedef.jpg")));
    }

    @Test
    void gecerliAnahtarGuvenilirMediaTypeIleGetirilir() throws Exception {
        ProfilFotografiDepolamaService service = service();
        String anahtar = service.kaydet(pngDosyasi("portre.png", 20, 20, 255));

        ProfilFotografiDosyasi sonuc = service.getir(anahtar);

        assertEquals(MediaType.IMAGE_PNG, sonuc.mediaType());
        assertTrue(sonuc.resource().exists());
        try (InputStream input = sonuc.resource().getInputStream()) {
            assertArrayEquals(
                    Files.readAllBytes(depolamaDizini.resolve(anahtar)),
                    input.readAllBytes()
            );
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "def_pfp.png",
            "../../hedef.jpg",
            "00000000-0000-4000-8000-000000000000.gif",
            "gecersiz.jpg"
    })
    void gecersizAnahtarOzelFotografDegildirVeGetirilemez(String anahtar) {
        ProfilFotografiDepolamaService service = service();

        assertFalse(service.ozelFotografMi(anahtar));
        KaynakBulunamadiException exception = assertThrows(
                KaynakBulunamadiException.class,
                () -> service.getir(anahtar)
        );
        assertEquals("Profil fotoğrafı bulunamadı.", exception.getMessage());
    }

    @Test
    void gecerliFormdakiEksikAnahtarKontrollu404Olur() {
        ProfilFotografiDepolamaService service = service();
        String anahtar = UUID.randomUUID() + ".jpg";

        KaynakBulunamadiException exception = assertThrows(
                KaynakBulunamadiException.class,
                () -> service.getir(anahtar)
        );

        assertEquals("Profil fotoğrafı bulunamadı.", exception.getMessage());
    }

    @Test
    void silmeYalnizDepolamaKokundekiGecerliAnahtaraDokunur() throws Exception {
        Path storage = depolamaDizini.resolve("storage");
        ProfilFotografiDepolamaService service =
                new ProfilFotografiDepolamaService(storage.toString());
        Path disaridakiDosya = depolamaDizini.resolve("hedef.jpg");
        Files.writeString(disaridakiDosya, "korunacak");

        service.sil("../hedef.jpg");

        assertTrue(Files.exists(disaridakiDosya));

        String anahtar = service.kaydet(jpegDosyasi("portre.jpg", 20, 20));
        service.sil(anahtar);

        assertFalse(Files.exists(storage.resolve(anahtar)));
    }

    private ProfilFotografiDepolamaService service() {
        return new ProfilFotografiDepolamaService(depolamaDizini.toString());
    }

    private MockMultipartFile jpegDosyasi(
            String dosyaAdi,
            int genislik,
            int yukseklik
    ) throws Exception {
        BufferedImage image = new BufferedImage(
                genislik,
                yukseklik,
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, genislik, yukseklik);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);
        return new MockMultipartFile(
                "dosya",
                dosyaAdi,
                "image/jpeg",
                output.toByteArray()
        );
    }

    private MockMultipartFile pngDosyasi(
            String dosyaAdi,
            int genislik,
            int yukseklik,
            int alfa
    ) throws Exception {
        BufferedImage image = new BufferedImage(
                genislik,
                yukseklik,
                BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(25, 50, 75, alfa));
            graphics.fillRect(0, 0, genislik, yukseklik);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile(
                "dosya",
                dosyaAdi,
                "image/png",
                output.toByteArray()
        );
    }

    private boolean iceriyorMu(byte[] veri, byte[] aranan) {
        outer:
        for (int i = 0; i <= veri.length - aranan.length; i++) {
            for (int j = 0; j < aranan.length; j++) {
                if (veri[i + j] != aranan[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

}
