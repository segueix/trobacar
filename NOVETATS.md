# Noves Funcionalitats de TrobaCar

## Historial d'Ubicacions

L'aplicació ara guarda automàticament totes les ubicacions del cotxe en un historial persistent. 

### Característiques:
- **Capacitat**: Fins a 50 ubicacions guardades
- **Format**: Data/hora + coordenades GPS
- **Persistència**: Les dades es guarden localment amb SharedPreferences i Gson
- **Accés**: Pantalla dedicada accessible des del botó "Veure historial d'ubicacions"

### Funcionalitats de l'historial:
1. **Visualització**: Llista cronològica de totes les ubicacions (més recent primer)
2. **Navegació**: Clica qualsevol ubicació per obrir-la a Google Maps
3. **Eliminació**: Botó per esborrar tot l'historial amb confirmació
4. **Informació mostrada**: 
   - Data i hora de quan es va guardar
   - Coordenades GPS (latitud i longitud)
   - Icona de cotxe per cada entrada

### Implementació tècnica:
- **LocationHistory.kt**: Classe singleton per gestionar l'historial
- **LocationEntry**: Data class amb latitude, longitude i timestamp
- **Gson**: Serialització/deserialització JSON per persistència
- **RecyclerView**: Llista eficient i reciclable d'items d'historial

## Inici Automàtic al Boot

L'aplicació ara s'inicia automàticament quan encens el mòbil.

### Com funciona:
1. **BootReceiver.kt**: BroadcastReceiver que escolta l'event `BOOT_COMPLETED`
2. Quan el sistema Android es reinicia, l'app:
   - Rep la notificació de boot
   - Inicia automàticament el LocationService
   - Comença a monitoritzar Android Auto
3. El servei continua executant-se en segon pla fins que es tanqui manualment

### Permisos necessaris:
- `RECEIVE_BOOT_COMPLETED`: Permís per rebre notificacions de boot del sistema

### Avantatges:
- No cal obrir l'app manualment després de reiniciar el mòbil
- El servei està sempre disponible per detectar connexions d'Android Auto
- Experiència d'usuari sense fricció

## Vista Prèvia del Disseny (HTML)

S'ha creat un fitxer HTML interactiu per veure el disseny de l'app sense necessitat d'Android Studio.

### Característiques del prototip:
- **Disseny responsiu**: S'adapta a diferents pantalles
- **Dues pantalles**:
  1. Pantalla principal amb indicadors i ubicació actual
  2. Pantalla d'historial amb llista d'ubicacions
- **Elements interactius**:
  - Tabs per canviar entre pantalles
  - Targetes clicables
  - Botons amb hover effects
- **Visual realista**: Aspecte similar a l'app real d'Android

### Com utilitzar-lo:
1. Obre `design_preview.html` en qualsevol navegador web
2. Clica els tabs per veure les diferents pantalles
3. Interactua amb les targetes i botons

### Útil per a:
- **Demostració**: Mostrar el disseny sense instal·lar l'app
- **Testing UX**: Provar la interfície abans de compilar
- **Presentació**: Mostrar el projecte a clients o col·laboradors
- **Desenvolupament**: Referència visual durant el desenvolupament

## Millores Tècniques

### Dependencies afegides:
```kotlin
implementation("androidx.recyclerview:recyclerview:1.3.2")
implementation("com.google.code.gson:gson:2.10.1")
```

### Nous arxius creats:
1. **LocationHistory.kt**: Gestió de l'historial
2. **HistoryActivity.kt**: Pantalla d'historial
3. **BootReceiver.kt**: Receptor per inici al boot
4. **activity_history.xml**: Layout de l'historial
5. **item_history.xml**: Layout d'items individuals
6. **ic_arrow_right.xml**: Icona de fletxa
7. **design_preview.html**: Prototip HTML del disseny

### Modificacions:
1. **AndroidManifest.xml**: 
   - Afegit permís `RECEIVE_BOOT_COMPLETED`
   - Afegit BootReceiver
   - Afegit HistoryActivity
2. **MainActivity.kt**: 
   - Afegit botó per veure historial
3. **AndroidAutoReceiver.kt**: 
   - Integració amb LocationHistory
4. **activity_main.xml**: 
   - Afegit botó d'historial
5. **build.gradle.kts**: 
   - Afegides dependencies Gson i RecyclerView

## Flux de Dades

```
Boot del sistema
    ↓
BootReceiver inicia LocationService
    ↓
LocationService monitoritza GPS i Android Auto
    ↓
Connexió a Android Auto → AndroidAutoReceiver
    ↓
Desconnexió d'Android Auto → Guarda ubicació
    ↓
AndroidAutoReceiver → LocationHistory.addLocation()
    ↓
Ubicació guardada a:
    1. SharedPreferences (ubicació actual)
    2. LocationHistory (historial JSON)
    ↓
MainActivity/HistoryActivity mostren les dades
```

## Futur Desenvolupament

Possibles millores per a versions futures:
1. **Exportació**: Exportar historial a CSV o KML
2. **Cerca**: Filtrar historial per data o ubicació
3. **Notes**: Afegir notes a cada ubicació
4. **Fotos**: Guardar foto de l'entorn
5. **Notificacions**: Recordatori si el cotxe porta molt temps al mateix lloc
6. **Estadístiques**: Mapa de calor amb llocs més freqüents
7. **Compartir**: Compartir ubicacions amb altres usuaris
8. **Backup cloud**: Sincronització amb Google Drive
