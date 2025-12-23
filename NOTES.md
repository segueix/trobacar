# Notes importants sobre TrobaCar

## Com provar l'aplicació sense Android Auto

Mentre desenvolupes o proves l'app, pots simular les connexions/desconnexions d'Android Auto utilitzant ADB:

### Simular connexió a Android Auto:
```bash
adb shell am broadcast -a android.car.action.CAR_CONNECTION_STATUS --es CarConnectionState connected --ei connection_type 1
```

### Simular desconnexió d'Android Auto:
```bash
adb shell am broadcast -a android.car.action.CAR_CONNECTION_STATUS --es CarConnectionState disconnected
```

## Limitacions conegudes

1. **Precisió del GPS**: La precisió depèn de la qualitat del senyal GPS. En zones urbanes denses o aparcaments coberts, la precisió pot ser limitada.

2. **Emulador**: Si proves amb l'emulador d'Android Studio, necessitaràs simular ubicacions manuals ja que l'emulador no té GPS real.

3. **Android Auto**: L'aplicació detecta només Android Auto. No funciona amb CarPlay (iOS) ni altres sistemes de vehicles.

4. **Consum de bateria**: El servei de localització en segon pla pot consumir bateria. El servei està optimitzat per actualitzar cada 5 segons o 10 metres.

## Configuració d'ubicació al emulador

Si estàs provant amb un emulador:

1. Obre l'emulador
2. Clica els tres punts (...) al lateral
3. Ves a "Location"
4. Introdueix coordenades manuals o utilitza el mapa
5. Clica "SET LOCATION"

## Permisos en versions modernes d'Android

Android 10+ requereix que l'usuari accepti permisos de localització:
- **Només mentre s'usa l'app**: L'app funcionarà només quan estigui oberta
- **Tot el temps**: Recomanat per TrobaCar perquè funcioni en segon pla

Per canviar els permisos després de la instal·lació:
1. Configuració > Aplicacions > TrobaCar
2. Permisos > Ubicació
3. Selecciona "Tot el temps"

## Comprovació del servei

Per verificar que el servei s'està executant:

```bash
adb shell dumpsys activity services | grep TrobaCar
```

## Depuració de broadcasts

Per veure els broadcasts que rep l'app:

```bash
adb logcat | grep AndroidAutoReceiver
```

## Estructura de SharedPreferences

L'aplicació guarda les següents dades a SharedPreferences ("TrobaCar"):
- `android_auto_connected`: Boolean - Indica si està connectat a Android Auto
- `saved_latitude`: Float - Latitud de l'última ubicació guardada
- `saved_longitude`: Float - Longitud de l'última ubicació guardada
- `saved_timestamp`: Long - Timestamp de quan es va guardar l'ubicació

## Millores futures possibles

1. Afegir historial d'ubicacions
2. Notificació quan es guarda la ubicació
3. Opció per guardar ubicació manualment
4. Widget per accés ràpid
5. Suport per Wear OS
6. Foto de l'entorn del cotxe
7. Recordatori per temps d'aparcament limitat
8. Integració amb altres apps de navegació (Waze, etc.)

## Compilació de l'APK

Per generar un APK de release:

1. Build > Generate Signed Bundle/APK
2. Selecciona APK
3. Crea o selecciona una keystore
4. Selecciona "release" com a build variant
5. Clica Finish

O des de la línia de comandes:
```bash
./gradlew assembleRelease
```

L'APK es generarà a: `app/build/outputs/apk/release/`

## Suport i contribucions

Per reportar errors o suggerir millores, obre un issue al repositori del projecte.
