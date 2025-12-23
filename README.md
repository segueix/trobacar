# TrobaCar

Aplicació Android per trobar el teu cotxe després de desconnectar-te d'Android Auto.

## Característiques

- **Detecció GPS**: Indicador verd/vermell que mostra si el GPS està actiu
- **Detecció Android Auto**: Indicador verd/vermell que mostra si estàs connectat a Android Auto
- **Guardar ubicació automàticament**: Quan et desconnectes d'Android Auto, l'app guarda automàticament la ubicació del cotxe
- **Historial d'ubicacions**: Guarda automàticament fins a 50 ubicacions anteriors
- **Inici automàtic**: L'app s'inicia automàticament quan encens el mòbil
- **Integració amb Google Maps**: Clica qualsevol ubicació per obrir-la directament a Google Maps
- **Neteja automàtica**: Cada vegada que et connectes a Android Auto, l'ubicació anterior s'esborra

## Com funciona

1. L'aplicació s'executa en segon pla amb un servei de localització
2. L'app s'inicia automàticament quan encens el mòbil
3. Quan connectes el mòbil a Android Auto (via cable o Bluetooth):
   - L'indicador d'Android Auto es posa en verd
   - S'esborra qualsevol ubicació guardada anteriorment
4. Quan desconnectes el mòbil d'Android Auto:
   - L'indicador d'Android Auto es posa en vermell
   - L'app guarda automàticament la ubicació actual del GPS
   - La ubicació s'afegeix a l'historial
5. Pots clicar la targeta d'ubicació per obrir Google Maps i veure on has deixat el cotxe
6. Pots accedir a l'historial per veure totes les ubicacions anteriors (fins a 50)

## Requisits

- Android 7.0 (API 24) o superior
- Permisos de localització (es demanen automàticament)
- GPS activat per obtenir ubicacions precises
- Android Auto instal·lat i configurat

## Instal·lació

1. Obre el projecte amb Android Studio
2. Connecta el teu dispositiu Android o inicia un emulador
3. Fes clic a "Run" (o prem Shift+F10)
4. Accepta els permisos de localització quan l'app els demani

## Permisos necessaris

L'aplicació demana els següents permisos:
- `ACCESS_FINE_LOCATION`: Per obtenir la ubicació precisa del GPS
- `ACCESS_COARSE_LOCATION`: Per ubicació aproximada (fallback)
- `FOREGROUND_SERVICE`: Per executar el servei de localització en segon pla
- `POST_NOTIFICATIONS`: Per mostrar notificacions (Android 13+)

## Estructura del projecte

```
TrobaCar/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/cat/edealae/trobacar/
│   │       │   ├── MainActivity.kt
│   │       │   ├── HistoryActivity.kt
│   │       │   ├── LocationService.kt
│   │       │   ├── LocationHistory.kt
│   │       │   ├── AndroidAutoReceiver.kt
│   │       │   └── BootReceiver.kt
│   │       ├── res/
│   │       │   ├── drawable/
│   │       │   │   ├── ic_circle_green.xml
│   │       │   │   ├── ic_circle_red.xml
│   │       │   │   ├── ic_car.xml
│   │       │   │   ├── ic_arrow_right.xml
│   │       │   │   └── ic_notification.xml
│   │       │   ├── layout/
│   │       │   │   ├── activity_main.xml
│   │       │   │   ├── activity_history.xml
│   │       │   │   └── item_history.xml
│   │       │   └── values/
│   │       │       ├── strings.xml
│   │       │       ├── colors.xml
│   │       │       └── themes.xml
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── design_preview.html
└── README.md
```

## Solució de problemes

### L'app no detecta quan em desconnecto d'Android Auto
- Assegura't que Android Auto està correctament instal·lat i configurat
- Verifica que els permisos de l'app estan activats a Configuració > Aplicacions > TrobaCar

### No es guarda la ubicació
- Verifica que el GPS està activat
- Assegura't que l'app té permisos de localització
- Comprova que l'indicador GPS està en verd abans de desconnectar-te

### L'app no s'obre quan clico la ubicació
- Assegura't que Google Maps està instal·lat
- Si no tens Google Maps, la ubicació s'obrirà al navegador web

## Nota tècnica

Android Auto utilitza broadcasts específics per notificar les connexions/desconnexions. L'app escolta aquests broadcasts mitjançant el `AndroidAutoReceiver` i reacciona en conseqüència.

## Versió

1.0 - Versió inicial

## Autor

Edicions De Aleae Ratione
