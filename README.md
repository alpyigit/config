# Spring Cloud Config Demo with YAML

Bu proje, Spring Cloud Config Server ve Client uygulamalarını içeren bir demo projesidir. Config Server, uygulamalar için merkezi konfigürasyon yönetimi sağlar ve **YAML formatinda** konfigürasyon dosyalarını kullanır.

## Proje Yapısı

```
config/
├── config-server/          # Spring Cloud Config Server (YAML)
├── spring-config-client/   # Spring Boot Client Uygulaması (YAML) 
├── config-repo/           # YAML konfigürasyon dosyaları
│   ├── spring-config-client.yml  # Ana konfigürasyon (210 değer)
│   └── application.yml           # Varsayılan konfigürasyon
├── start-all.bat          # Tüm uygulamaları başlatma scripti
├── test-endpoints.bat     # API test scripti
└── README.md              # Bu dosya
```

## Konfigürasyon

Config repository'de **210+ konfigürasyon değeri** bulunmaktadır:
- Uygulama ayarları (5 değer)
- Veritabanı konfigürasyonu (10 değer)
- Cache ayarları (11 değer)
- Güvenlik konfigürasyonu (14 değer)
- Feature flag'ler (12 değer)
- API ayarları (15 değer)
- İş mantığı parametreleri (15 değer)
- Messaging konfigürasyonu (16 değer)
- Email ayarları (11 değer)
- Storage ayarları (11 değer)
- Monitoring ayarları (15 değer)
- Performans ayarları (11 değer)
- Entegrasyon ayarları (9 değer)
- Notification ayarları (9 değer)
- Content ayarları (8 değer)
- Search ayarları (10 değer)
- Backup ayarları (8 değer)
- Social media ayarları (9 değer)
- Çeşitli ayarlar (11 değer)

**Toplam: 210 konfigürasyon değeri**

## Hızlı Başlangıç

### Otomatik Başlatma (Önerilen)

```bash
# Tüm uygulamaları başlat
start-all.bat
```

Bu script:
1. Config Server'ı başlatır (port 8888)
2. 10 saniye bekler
3. Client uygulamasını başlatır (port 8080)

### Manuel Başlatma

#### 1. Config Server'ı Başlatın

```bash
cd config-server
mvn spring-boot:run
```

Config Server http://localhost:8888 adresinde çalışacaktır.

#### 2. Spring Client Uygulamasını Başlatın

Yeni bir terminal açın:

```bash
cd spring-config-client
mvn spring-boot:run
```

Client uygulama http://localhost:8080 adresinde çalışacaktır.

## API Endpoints

### Config Server Endpoints
- `GET http://localhost:8888/spring-config-client/default` - Client için konfigürasyonları getirir
- `GET http://localhost:8888/actuator/health` - Config Server sağlık durumu

### Client Application Endpoints
- `GET http://localhost:8080/api/config/app-info` - Uygulama bilgileri
- `GET http://localhost:8080/api/config/database-config` - Veritabanı konfigürasyonu
- `GET http://localhost:8080/api/config/features` - Feature flag'ler
- `GET http://localhost:8080/api/config/security-settings` - Güvenlik ayarları
- `GET http://localhost:8080/api/config/all-config` - Tüm konfigürasyon
- `GET http://localhost:8080/api/properties/sample-properties` - Örnek özellikler
- `GET http://localhost:8080/api/properties/count` - Konfigürasyon sayısı
- `GET http://localhost:8080/api/properties/get?key=<property-key>` - Belirli bir özelliği getir

## Test Etme

### Otomatik Test

```bash
# Tüm endpoint'leri test et
test-endpoints.bat
```

### Manuel Test Örnekleri

#### 1. Uygulama Bilgilerini Kontrol Etme
```bash
curl http://localhost:8080/api/config/app-info
```

Beklenen çıktı:
```json
{
  "name": "Spring Config Client Application",
  "version": "1.0.0",
  "description": "Demo application using Spring Cloud Config Server",
  "author": "Spring Developer",
  "environment": "development"
}
```

#### 2. Konfigürasyon Sayısını Görme
```bash
curl http://localhost:8080/api/properties/count
```

Beklenen çıktı:
```json
{
  "total": 210,
  "app": 5,
  "database": 10,
  "cache": 11,
  ...
}
```

#### 3. Feature Flag'leri Kontrol Etme
```bash
curl http://localhost:8080/api/config/features
```

Beklenen çıktı:
```json
{
  "newDashboard": true,
  "betaCheckout": false
}
```

## Konfigürasyon Değişikliği

Konfigürasyon değişikliği yapmak için:

1. `config-repo/spring-config-client.yml` veya `config-repo/application.yml` dosyasını düzenleyin
2. Uygulamayı yeniden başlatın veya refresh endpoint'ini kullanın:
   ```bash
   curl -X POST http://localhost:8080/actuator/refresh
   ```

### YAML Format Örneği:
```yaml
app:
  name: Spring Config Client Application
  version: 1.0.0
  environment: development

feature:
  new-dashboard: true
  beta-checkout: false

database:
  url: jdbc:mysql://localhost:3306/mydb
  username: admin
  connection-timeout: 30000
```

## Doğrulama

Projenin başarıyla çalıştığını doğrulamak için:

1. ✅ Config Server çalışıyor: http://localhost:8888/actuator/health
2. ✅ Client uygulaması çalışıyor: http://localhost:8080/actuator/health
3. ✅ Konfigürasyon alınıyor: http://localhost:8080/api/properties/count
4. ✅ 200+ konfigürasyon değeri mevcut (gerçekte 210 adet)
5. ✅ Config Server'dan konfigürasyon okunuyor

## Özellikler

- ✅ **210+ konfigürasyon değeri** (hedef: 200)
- ✅ **YAML format konfigürasyon** (daha okunaklı ve yapısal)
- ✅ **Merkezi konfigürasyon yönetimi**
- ✅ **Native dosya sistemi tabanlı konfigürasyon**
- ✅ **Runtime'da konfigürasyon erişimi**
- ✅ **RESTful API'ler ile konfigürasyon erişimi**
- ✅ **Tip güvenli konfigürasyon sınıfları**
- ✅ **Feature flag desteği**
- ✅ **Actuator endpoint'leri**
- ✅ **Otomatik başlatma script'leri**
- ✅ **Kapsamlı test endpoint'leri**
- ✅ **Hiyerarşik YAML konfigürasyon yapısı**

## Teknolojiler

- Spring Boot 3.2.0
- Spring Cloud Config 2023.0.0
- Java 17
- Maven
- Native File System (Git yerine basitlik için)

## Sorun Giderme

### Port Kullanımda Hatası
```bash
# Portları kullanan işlemleri bul
netstat -ano | findstr :8888
netstat -ano | findstr :8080

# İşlemi sonlandır
taskkill /pid <PID> /f
```

### Konfigürasyon Alınamıyor
1. Config Server'ın çalıştığından emin olun
2. `config-repo` klasöründe `.properties` dosyalarının olduğunu kontrol edin
3. Client uygulamasının `bootstrap.properties` dosyasında doğru config server URL'ini kontrol edin

### Maven Bağımlılık Sorunları
```bash
# Maven cache'i temizle
mvn clean install -U
```