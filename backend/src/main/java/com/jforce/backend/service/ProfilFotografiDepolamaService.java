package com.jforce.backend.service;

import com.jforce.backend.exception.DesteklenmeyenDosyaTuruException;
import com.jforce.backend.exception.DosyaCokBuyukException;
import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.exception.KaynakBulunamadiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProfilFotografiDepolamaService {

    private static final long MAKSIMUM_DOSYA_BOYUTU = 2L * 1024 * 1024;
    private static final int MAKSIMUM_GIRDI_KENARI = 2048;
    private static final long MAKSIMUM_TOPLAM_PIKSEL = 4_194_304L;
    private static final int MAKSIMUM_CIKTI_KENARI = 1024;
    private static final String BULUNAMADI_MESAJI = "Profil fotoğrafı bulunamadı.";
    private static final Pattern ANAHTAR_DESENI = Pattern.compile(
            "^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.(jpg|png)$"
    );

    private final Path depolamaKoku;
    private final Supplier<UUID> uuidUretici;

    @Autowired
    public ProfilFotografiDepolamaService(
            @Value("${app.profil-fotografi.depolama-yolu}") String depolamaYolu
    ) {
        this(depolamaYolu, UUID::randomUUID);
    }

    public ProfilFotografiDepolamaService(
            String depolamaYolu,
            Supplier<UUID> uuidUretici
    ) {
        this.uuidUretici = Objects.requireNonNull(uuidUretici);
        try {
            Path aday = Path.of(depolamaYolu).toAbsolutePath().normalize();
            Files.createDirectories(aday);
            depolamaKoku = aday.toRealPath();
            if (!Files.isDirectory(depolamaKoku) || !Files.isWritable(depolamaKoku)) {
                throw new IllegalStateException("Profil fotoğrafı depolama alanı kullanılamıyor.");
            }
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("Profil fotoğrafı depolama alanı kullanılamıyor.", ex);
        }
    }

    public String kaydet(MultipartFile dosya) {
        if (dosya == null || dosya.isEmpty()) {
            throw new GecersizIstekException("Geçersiz profil fotoğrafı.");
        }
        if (dosya.getSize() > MAKSIMUM_DOSYA_BOYUTU) {
            throw new DosyaCokBuyukException("Profil fotoğrafı en fazla 2 MB olabilir.");
        }

        Format uzantiFormati = uzantiFormati(dosya.getOriginalFilename());
        Format mimeFormati = mimeFormati(dosya.getContentType());
        byte[] hamVeri = sinirliOku(dosya);
        Format imzaFormati = imzaFormati(hamVeri);
        if (uzantiFormati != mimeFormati || uzantiFormati != imzaFormati) {
            throw desteklenmeyenDosyaTuru();
        }

        BufferedImage kaynak = decodeEt(hamVeri, uzantiFormati);
        BufferedImage temiz = yenidenUret(kaynak, uzantiFormati);
        kaynak.flush();
        try {
            return kaliciYaz(temiz, uzantiFormati);
        } finally {
            temiz.flush();
        }
    }

    public ProfilFotografiDosyasi getir(String anahtar) {
        if (!ozelFotografMi(anahtar)) {
            throw bulunamadi();
        }

        Path hedef = guvenliHedef(anahtar);
        if (hedef == null
                || !Files.isRegularFile(hedef, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(hedef)) {
            throw bulunamadi();
        }

        MediaType mediaType = anahtar.endsWith(".jpg")
                ? MediaType.IMAGE_JPEG
                : MediaType.IMAGE_PNG;
        return new ProfilFotografiDosyasi(new FileSystemResource(hedef), mediaType);
    }

    public boolean ozelFotografMi(String anahtar) {
        if (anahtar == null || anahtar.isBlank() || "def_pfp.png".equals(anahtar)) {
            return false;
        }
        Matcher matcher = ANAHTAR_DESENI.matcher(anahtar);
        if (!matcher.matches()) {
            return false;
        }
        try {
            return UUID.fromString(matcher.group(1)).toString().equals(matcher.group(1))
                    && guvenliHedef(anahtar) != null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public void sil(String anahtar) {
        if (!ozelFotografMi(anahtar)) {
            return;
        }

        Path hedef = guvenliHedef(anahtar);
        if (hedef == null || !Files.isRegularFile(hedef, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Files.deleteIfExists(hedef);
        } catch (IOException ex) {
            throw new IllegalStateException("Profil fotoğrafı silinemedi.", ex);
        }
    }

    private byte[] sinirliOku(MultipartFile dosya) {
        try (InputStream input = dosya.getInputStream()) {
            byte[] veri = input.readNBytes((int) MAKSIMUM_DOSYA_BOYUTU + 1);
            if (veri.length > MAKSIMUM_DOSYA_BOYUTU) {
                throw new DosyaCokBuyukException("Profil fotoğrafı en fazla 2 MB olabilir.");
            }
            return veri;
        } catch (DosyaCokBuyukException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new GecersizIstekException("Geçersiz profil fotoğrafı.");
        }
    }

    private BufferedImage decodeEt(byte[] hamVeri, Format beklenenFormat) {
        ImageReader reader = null;
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(
                new ByteArrayInputStream(hamVeri)
        )) {
            if (imageInput == null) {
                throw gecersizGorsel();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw gecersizGorsel();
            }

            reader = readers.next();
            reader.setInput(imageInput, true, true);
            if (readerFormati(reader) != beklenenFormat) {
                throw desteklenmeyenDosyaTuru();
            }

            int genislik = reader.getWidth(0);
            int yukseklik = reader.getHeight(0);
            if (genislik < 1
                    || yukseklik < 1
                    || genislik > MAKSIMUM_GIRDI_KENARI
                    || yukseklik > MAKSIMUM_GIRDI_KENARI
                    || (long) genislik * yukseklik > MAKSIMUM_TOPLAM_PIKSEL) {
                throw gecersizGorsel();
            }

            BufferedImage image = reader.read(0);
            if (image == null) {
                throw gecersizGorsel();
            }
            return image;
        } catch (DesteklenmeyenDosyaTuruException | GecersizIstekException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw gecersizGorsel();
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }
    }

    private BufferedImage yenidenUret(BufferedImage kaynak, Format format) {
        int kaynakGenislik = kaynak.getWidth();
        int kaynakYukseklik = kaynak.getHeight();
        int hedefGenislik = kaynakGenislik;
        int hedefYukseklik = kaynakYukseklik;

        if (Math.max(kaynakGenislik, kaynakYukseklik) > MAKSIMUM_CIKTI_KENARI) {
            if (kaynakGenislik >= kaynakYukseklik) {
                hedefGenislik = MAKSIMUM_CIKTI_KENARI;
                hedefYukseklik = Math.max(
                        1,
                        (int) Math.round(
                                kaynakYukseklik * (double) MAKSIMUM_CIKTI_KENARI
                                        / kaynakGenislik
                        )
                );
            } else {
                hedefYukseklik = MAKSIMUM_CIKTI_KENARI;
                hedefGenislik = Math.max(
                        1,
                        (int) Math.round(
                                kaynakGenislik * (double) MAKSIMUM_CIKTI_KENARI
                                        / kaynakYukseklik
                        )
                );
            }
        }

        BufferedImage temiz = new BufferedImage(
                hedefGenislik,
                hedefYukseklik,
                format == Format.JPEG
                        ? BufferedImage.TYPE_INT_RGB
                        : BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D graphics = temiz.createGraphics();
        try {
            if (format == Format.PNG) {
                graphics.setComposite(AlphaComposite.Src);
            }
            graphics.drawImage(kaynak, 0, 0, hedefGenislik, hedefYukseklik, null);
        } finally {
            graphics.dispose();
        }
        return temiz;
    }

    private String kaliciYaz(BufferedImage image, Format format) {
        Path geciciDosya = null;
        try {
            geciciDosya = Files.createTempFile(depolamaKoku, ".profil-", ".tmp");
            if (!ImageIO.write(image, format.imageIoAdi, geciciDosya.toFile())) {
                throw new IOException("Görsel yazıcı bulunamadı.");
            }

            while (true) {
                String anahtar = uuidUretici.get() + format.kaliciUzanti;
                Path hedef = guvenliHedef(anahtar);
                if (hedef == null) {
                    throw new IOException("Geçersiz depolama hedefi.");
                }
                try {
                    tasi(geciciDosya, hedef);
                    return anahtar;
                } catch (FileAlreadyExistsException ex) {
                    // UUID çakışırsa aynı geçici dosya için yeni bir anahtar denenir.
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Profil fotoğrafı kaydedilemedi.", ex);
        } finally {
            geciciDosyayiSil(geciciDosya);
        }
    }

    private void tasi(Path kaynak, Path hedef) throws IOException {
        Files.move(kaynak, hedef);
    }

    private Format uzantiFormati(String dosyaAdi) {
        if (dosyaAdi == null || dosyaAdi.isBlank()) {
            throw desteklenmeyenDosyaTuru();
        }
        int nokta = dosyaAdi.lastIndexOf('.');
        if (nokta < 0 || nokta == dosyaAdi.length() - 1) {
            throw desteklenmeyenDosyaTuru();
        }
        return switch (dosyaAdi.substring(nokta + 1).toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> Format.JPEG;
            case "png" -> Format.PNG;
            default -> throw desteklenmeyenDosyaTuru();
        };
    }

    private Format mimeFormati(String contentType) {
        if (MediaType.IMAGE_JPEG_VALUE.equalsIgnoreCase(contentType)) {
            return Format.JPEG;
        }
        if (MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(contentType)) {
            return Format.PNG;
        }
        throw desteklenmeyenDosyaTuru();
    }

    private Format imzaFormati(byte[] veri) {
        if (veri.length >= 3
                && (veri[0] & 0xff) == 0xff
                && (veri[1] & 0xff) == 0xd8
                && (veri[2] & 0xff) == 0xff) {
            return Format.JPEG;
        }
        byte[] pngImzasi = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47,
                0x0d, 0x0a, 0x1a, 0x0a
        };
        if (veri.length >= pngImzasi.length) {
            boolean ayni = true;
            for (int i = 0; i < pngImzasi.length; i++) {
                if (veri[i] != pngImzasi[i]) {
                    ayni = false;
                    break;
                }
            }
            if (ayni) {
                return Format.PNG;
            }
        }
        throw desteklenmeyenDosyaTuru();
    }

    private Format readerFormati(ImageReader reader) throws IOException {
        return switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
            case "jpeg", "jpg" -> Format.JPEG;
            case "png" -> Format.PNG;
            default -> throw desteklenmeyenDosyaTuru();
        };
    }

    private Path guvenliHedef(String anahtar) {
        try {
            Path hedef = depolamaKoku.resolve(anahtar).normalize();
            if (!hedef.startsWith(depolamaKoku)
                    || !depolamaKoku.equals(hedef.getParent())) {
                return null;
            }
            return hedef;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private DesteklenmeyenDosyaTuruException desteklenmeyenDosyaTuru() {
        return new DesteklenmeyenDosyaTuruException(
                "Yalnız JPEG veya PNG profil fotoğrafı yüklenebilir."
        );
    }

    private GecersizIstekException gecersizGorsel() {
        return new GecersizIstekException("Geçersiz profil fotoğrafı.");
    }

    private KaynakBulunamadiException bulunamadi() {
        return new KaynakBulunamadiException(BULUNAMADI_MESAJI);
    }

    private void geciciDosyayiSil(Path geciciDosya) {
        if (geciciDosya == null) {
            return;
        }
        try {
            Files.deleteIfExists(geciciDosya);
        } catch (IOException ignored) {
            // Asıl kontrollü hata korunur.
        }
    }

    private enum Format {
        JPEG("jpeg", ".jpg"),
        PNG("png", ".png");

        private final String imageIoAdi;
        private final String kaliciUzanti;

        Format(String imageIoAdi, String kaliciUzanti) {
            this.imageIoAdi = imageIoAdi;
            this.kaliciUzanti = kaliciUzanti;
        }
    }
}
