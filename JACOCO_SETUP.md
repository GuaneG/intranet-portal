# JaCoCo Test Coverage Kurulum ve Kullanım Kılavuzu

## ✅ Yapılan İşlemler

pom.xml dosyasına JaCoCo plugin'i eklenmiştir. Aşağıdaki konfigürasyonlar yapılmıştır:

## 📋 Konfigürasyon Detayları

### JaCoCo Plugin Ayarları

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.10</version>
    <executions>
        <!-- Prepare Agent -->
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        
        <!-- Generate Report -->
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        
        <!-- Coverage Check (minimum %50 line coverage) -->
        <execution>
            <id>jacoco-check</id>
            <phase>test</phase>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
    
    <!-- Exclude Ayarları -->
    <configuration>
        <excludes>
            <exclude>**/Application.class</exclude>           <!-- Spring Boot Main Class -->
            <exclude>**/config/**</exclude>                   <!-- Config Classes -->
            <exclude>**/entity/**</exclude>                   <!-- JPA Entities -->
            <exclude>**/dto/**</exclude>                      <!-- Data Transfer Objects -->
            <exclude>**/mapper/**</exclude>                   <!-- Mapper Classes -->
            <exclude>**/exception/**</exclude>                <!-- Exception Classes -->
        </excludes>
    </configuration>
</plugin>
```

## 🚀 Test Coverage Çalıştırma

### 1. Test'i Çalıştır ve Raporu Oluştur
```bash
cd backend
./mvnw clean test jacoco:report
```

### 2. Raporu Görüntüle
```bash
# MacOS/Linux
open target/site/jacoco/index.html

# Windows
start target/site/jacoco/index.html
```

## 📊 Rapor Nedir?

HTML rapor şu bilgileri içerir:
- **Line Coverage**: Kaç satır kod test edilmiş
- **Branch Coverage**: Koşul dalları test edilmiş mi
- **Complexity**: Kodun karmaşıklığı
- **Method Coverage**: Hangi metodlar test edilmiş

## ⚙️ Exclude Edilen Dosyalar

Aşağıdaki dosyalar test coverage'dan hariç tutulmuştur:

| Kategori | Sebep |
|----------|-------|
| `Application.class` | Spring Boot entry point - manual test |
| `config/**` | Configuration classes - integration test |
| `entity/**` | JPA entities - DB mapping, test edilmez |
| `dto/**` | Data objects - mapping classes |
| `mapper/**` | DTO mappers - getter/setter |
| `exception/**` | Exception classes - simple wrappers |

## 🔧 Exclude Kurallarını Değiştirme

Başka dosyalar exclude etmek istiyorsanız pom.xml'de şu bölümü düzenleyin:

```xml
<excludes>
    <exclude>**/config/**</exclude>
    <exclude>**/entity/**</exclude>
    <!-- Yeni exclude ekleyin -->
    <exclude>**/util/**</exclude>
    <exclude>**/constant/**</exclude>
</excludes>
```

## 📈 Minimum Coverage Ayarı

Şu anda minimum %50 line coverage zorunlu tutulmuştur:

```xml
<limits>
    <limit>
        <counter>LINE</counter>
        <value>COVEREDRATIO</value>
        <minimum>0.50</minimum>  <!-- 50% -->
    </limit>
</limits>
```

Bunu değiştirmek için değeri güncelleyin (örneğin 0.80 = %80)

## ⚠️ Coverage Check Başarısız Olursa

Eğer test coverage minimum değerin altındaysa build başarısız olur. İki seçenek:

### Seçenek 1: Daha fazla test yaz
Test dosyalarınıza yeni test case'ler ekleyin.

### Seçenek 2: Coverage kuralını gevşet
pom.xml'de minimum değeri düşür:
```xml
<minimum>0.30</minimum>  <!-- 30% coverage -->
```

### Seçenek 3: Coverage check'i devre dışı bırak
`jacoco-check` execution'ını kaldır pom.xml'den

## 📝 Komut Kısayolları

```bash
# Sadece test çalıştır
./mvnw test

# Test + Coverage raporu
./mvnw test jacoco:report

# Clean + Test + Coverage
./mvnw clean test jacoco:report

# Raporu görüntüle (Linux/Mac)
./mvnw test jacoco:report && open target/site/jacoco/index.html
```

## 🎯 Best Practices

1. **Entity ve DTO'lar exclude et** - Getter/setter'lar otomatik oluşturulur
2. **Configuration'ları exclude et** - Manual integration test'ler yapılır
3. **%70-80 coverage hedefle** - Aşırı coverage vakit kaybı
4. **Critical code öncelik** - Business logic'i önce test et
5. **CI/CD'ye entegre et** - Her commit'te otomatik çalıştır

## 🔗 Kaynaklar

- [JaCoCo Documentation](https://www.eclemma.org/jacoco/)
- [Maven JaCoCo Plugin](https://www.jacoco.org/jacoco/trunk/doc/maven.html)
