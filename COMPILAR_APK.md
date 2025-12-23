# Guia per Compilar l'APK de TrobaCar

## Logo Creat ✅

S'ha creat un logo personalitzat per a l'aplicació TrobaCar amb:
- 🚗 Un cotxe estilitzat en blau
- 📍 Un pin de localització vermell
- ⚪ Fons blanc amb cercle blau

Les icones s'han generat automàticament per a totes les densitats d'Android:
- mipmap-mdpi (48x48 px)
- mipmap-hdpi (72x72 px)
- mipmap-xhdpi (96x96 px)
- mipmap-xxhdpi (144x144 px)
- mipmap-xxxhdpi (192x192 px)

## Opcions per Compilar l'APK

### OPCIÓ 1: Android Studio (RECOMANAT) 🎯

Aquesta és la forma més senzilla i recomanada:

1. **Instal·la Android Studio**
   - Descarrega des de: https://developer.android.com/studio
   - Instal·la i configura l'Android SDK

2. **Obre el projecte**
   - File > Open
   - Selecciona la carpeta `TrobaCar`
   - Espera que Gradle sincronitzi (pot trigar uns minuts la primera vegada)

3. **Compila l'APK**
   - Build > Generate Signed Bundle/APK
   - Selecciona "APK"
   - Pots crear una keystore nova o utilitzar una existent
   - Selecciona "release" com a build variant
   - Clica "Finish"

4. **Troba l'APK**
   - L'APK es generarà a: `app/build/outputs/apk/release/app-release.apk`
   - Aquest APK està signat i es pot instal·lar en qualsevol dispositiu

**APK de Debug (sense signar)**
Si només vols provar l'app ràpidament sense signar:
- Build > Build Bundle(s) / APK(s) > Build APK(s)
- L'APK es generarà a: `app/build/outputs/apk/debug/app-debug.apk`

### OPCIÓ 2: Línia de Comandes 💻

Si prefereixes usar la terminal:

1. **Requisits**
   - Java JDK 8 o superior instal·lat
   - Android SDK instal·lat
   - Variable d'entorn ANDROID_HOME configurada

2. **Configura Android SDK**
   ```bash
   # Linux/Mac
   export ANDROID_HOME=$HOME/Android/Sdk
   export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
   
   # Windows
   set ANDROID_HOME=C:\Users\<username>\AppData\Local\Android\Sdk
   set PATH=%PATH%;%ANDROID_HOME%\tools;%ANDROID_HOME%\platform-tools
   ```

3. **Executa el setup**
   ```bash
   cd TrobaCar
   chmod +x setup_gradle.sh
   ./setup_gradle.sh
   ```

4. **Compila l'APK**
   
   **APK de Release (signat)**
   ```bash
   ./gradlew assembleRelease
   ```
   
   **APK de Debug**
   ```bash
   ./gradlew assembleDebug
   ```

5. **Troba l'APK**
   - Release: `app/build/outputs/apk/release/app-release.apk`
   - Debug: `app/build/outputs/apk/debug/app-debug.apk`

### OPCIÓ 3: Online Build Services 🌐

Si no vols instal·lar res localment, pots utilitzar serveis online:

1. **App Center (Microsoft)**
   - https://appcenter.ms/
   - Puja el codi i compila al núvol

2. **Bitrise**
   - https://www.bitrise.io/
   - CI/CD per a apps mòbils

3. **GitHub Actions**
   - Configura un workflow per compilar automàticament

## Signar l'APK (per a Release)

Per distribuir l'app fora de Google Play, necessites signar l'APK:

### Crear una Keystore

```bash
keytool -genkey -v -keystore trobacar.keystore -alias trobacar -keyalg RSA -keysize 2048 -validity 10000
```

Segueix les instruccions i recorda:
- **Password de la keystore**: Guarda'l de forma segura!
- **Alias**: `trobacar`
- **Password de l'alias**: Pot ser el mateix que la keystore

### Signar l'APK Manualment

Si tens un APK sense signar:

```bash
# Alinear l'APK
zipalign -v -p 4 app-release-unsigned.apk app-release-unsigned-aligned.apk

# Signar l'APK
apksigner sign --ks trobacar.keystore --out app-release.apk app-release-unsigned-aligned.apk

# Verificar la signatura
apksigner verify app-release.apk
```

### Configurar Signatura a Gradle

Pots configurar Gradle per signar automàticament:

1. Crea un fitxer `keystore.properties` a l'arrel del projecte:
   ```properties
   storeFile=/path/to/trobacar.keystore
   storePassword=<password>
   keyAlias=trobacar
   keyPassword=<password>
   ```

2. Afegeix a `app/build.gradle.kts`:
   ```kotlin
   android {
       signingConfigs {
           create("release") {
               val keystoreProperties = Properties()
               keystoreProperties.load(FileInputStream(file("../keystore.properties")))
               
               storeFile = file(keystoreProperties["storeFile"] as String)
               storePassword = keystoreProperties["storePassword"] as String
               keyAlias = keystoreProperties["keyAlias"] as String
               keyPassword = keystoreProperties["keyPassword"] as String
           }
       }
       
       buildTypes {
           release {
               signingConfig = signingConfigs.getByName("release")
               // ...
           }
       }
   }
   ```

## Instal·lar l'APK

### Al teu dispositiu Android:

1. **Activa "Fonts desconegudes"**
   - Configuració > Seguretat
   - Activa "Instal·lar apps de fonts desconegudes"

2. **Transfereix l'APK**
   - Per cable USB: Copia l'APK al dispositiu
   - Per email: Envia't l'APK i obre'l
   - Per Google Drive: Puja l'APK i descarrega'l al mòbil

3. **Instal·la**
   - Obre l'APK des del gestor d'arxius
   - Accepta els permisos
   - Ja està!

### Amb ADB (Android Debug Bridge):

```bash
adb install app-release.apk
```

## Solució de Problemes

### Error: "SDK location not found"
```bash
# Crea local.properties amb:
sdk.dir=/path/to/android-sdk
```

### Error: "Unsupported class file major version"
- Assegura't d'utilitzar Java 8 o superior
- Comprova: `java -version`

### Error: "Failed to find Build Tools"
- Obre Android Studio
- SDK Manager > SDK Tools
- Instal·la "Android SDK Build-Tools"

### L'app no s'instal·la
- Comprova que tens espai suficient
- Desinstal·la versions anteriors
- Activa "Fonts desconegudes"

## Distribució

### Google Play Store
1. Crea un compte de desenvolupador (25$ única vegada)
2. Puja l'APK signat (o millor, un AAB)
3. Completa la fitxa de l'app
4. Publica!

### Distribució Directa
- Pots distribuir l'APK directament
- Recorda signar-lo amb la teva keystore
- Guarda la keystore de forma segura (sense ella no podràs actualitzar l'app!)

### F-Droid
- Plataforma de codi obert
- Pots publicar-hi l'app gratuïtament

## Eines Útils

- **Gradle**: Build tool per Android
- **Android SDK**: Eines de desenvolupament d'Android
- **apksigner**: Per signar APKs
- **zipalign**: Per optimitzar APKs
- **adb**: Android Debug Bridge per comunicar amb dispositius

## Suport

Per a més informació:
- Documentació oficial d'Android: https://developer.android.com/
- Build your app: https://developer.android.com/studio/build

---

**Nota**: El logo i totes les icones ja estan configurades al projecte. Quan compillis l'APK, automàticament es farà servir el logo de TrobaCar! 🚗📍
