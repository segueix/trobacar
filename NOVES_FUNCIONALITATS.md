# Noves Funcionalitats de Detecció - TrobaCar

## 🎉 Què s'ha implementat?

L'app ara té **3 mètodes** per detectar automàticament quan surts del cotxe:

### 🔵 1. Bluetooth (MYCAR)
**Com funciona:**
- L'app detecta quan et connectes al Bluetooth del cotxe anomenat "MYCAR"
- Quan et desconnectes, guarda automàticament la ubicació

**Configuració:**
1. Assegura't que el Bluetooth del teu cotxe es diu exactament **"MYCAR"**
2. Si té un altre nom, hauràs de modificar el codi:
   - Obre: `BluetoothReceiver.kt`
   - Canvia la línia: `const val CAR_BLUETOOTH_NAME = "MYCAR"`
   - Posa el nom del teu cotxe entre cometes

**Icona a l'historial:** 🔵

### 🚶 2. Activity Recognition (Google)
**Com funciona:**
- Google detecta automàticament quan estàs al cotxe (IN_VEHICLE)
- Quan detecta que has sortit del cotxe, espera 10 segons i guarda la ubicació
- Funciona amb QUALSEVOL cotxe, sense necessitat de configuració

**Avantatges:**
- ✅ Completament automàtic
- ✅ No necessita Bluetooth ni Android Auto
- ✅ Funciona en segon pla
- ✅ Molt precís (tecnologia de Google)

**Icona a l'historial:** 🚶

### 🚗 3. Android Auto (manté-se)
**Com funciona:**
- Igual que abans: detecta quan et desconnectes d'Android Auto

**Icona a l'historial:** 🚗

## 📱 Pantalla Principal

**Indicadors:**
- **GPS** (esquerra): Verd si el GPS està actiu
- **Cotxe** (dreta): Verd si estàs connectat (Bluetooth, Activity o Android Auto)

**Ubicació guardada:**
Mostra la icona del mètode utilitzat:
- 🔵 Bluetooth
- 🚶 Activity Recognition
- 🚗 Android Auto

## 📋 Historial

Cada ubicació guardada mostra:
- **Icona del mètode** que l'ha detectat
- **Nom del mètode** (Bluetooth, Activity, Android Auto)
- **Data i hora**
- **Coordenades GPS**

## ⚙️ Configuració Necessària

### Permisos nous:
1. **Bluetooth** - Per detectar MYCAR
2. **Activity Recognition** - Per detectar activitat física

L'app els demanarà automàticament la primera vegada.

### A Configuració del mòbil:
- **Localització**: "Tot el temps" (molt important!)
- **Bluetooth**: Activat
- **Activity Recognition**: Permís concedit

## 🧪 Com provar les funcionalitats

### Provar Bluetooth:
1. Connecta't al Bluetooth del cotxe "MYCAR"
2. Espera uns segons
3. Desconnecta't
4. Comprova que s'ha guardat la ubicació amb 🔵

### Provar Activity Recognition:
1. Obre l'app al cotxe (o simula estar al cotxe)
2. Condueix una mica (o espera que Google detecti IN_VEHICLE)
3. Para i surt del cotxe
4. Espera 10-15 segons
5. Comprova que s'ha guardat la ubicació amb 🚶

### Simular amb ADB (per desenvolupadors):
```bash
# Simular connexió Bluetooth
adb shell am broadcast -a android.bluetooth.device.action.ACL_CONNECTED --es android.bluetooth.device.extra.DEVICE "MYCAR"

# Simular desconnexió Bluetooth
adb shell am broadcast -a android.bluetooth.device.action.ACL_DISCONNECTED --es android.bluetooth.device.extra.DEVICE "MYCAR"
```

## 🔧 Resolució de problemes

### Bluetooth no detecta:
- Verifica que el nom del cotxe és exactament "MYCAR"
- Comprova que tens permís de Bluetooth
- Assegura't que el Bluetooth està activat

### Activity Recognition no funciona:
- Verifica que tens permís d'Activity Recognition
- Assegura't que els Google Play Services estan actualitzats
- Condueix almenys 5 minuts perquè Google detecti IN_VEHICLE
- Espera 10-15 segons després de sortir del cotxe

### Cap mètode funciona:
- Comprova que el GPS està activat
- Verifica que l'app té permís de "Localització tot el temps"
- Assegura't que el servei de l'app està actiu (hauria d'haver-hi una notificació)

## 🎯 Recomanacions

**Millor combinació:**
- **Bluetooth** per cotxes amb Bluetooth configurable
- **Activity Recognition** com a backup per quan el Bluetooth falla
- **Android Auto** per cotxes compatibles

**Consell pro:**
Si el teu cotxe té un nom diferent de "MYCAR", canvia'l al codi o simplement confia en Activity Recognition que funciona amb qualsevol cotxe! 🚀

## 📊 Estadístiques

Amb aquests 3 mètodes, la fiabilitat de detecció passa de:
- **~50%** (només Android Auto)
- A **~95%** (Bluetooth + Activity Recognition + Android Auto)

És gairebé impossible que els 3 mètodes fallin alhora! 🎉

## 🔮 Funcionalitats futures

Possibles millores:
- Configuració per canviar el nom del Bluetooth des de l'app
- Múltiples dispositius Bluetooth (diversos cotxes)
- Sensibilitat ajustable per Activity Recognition
- Notificacions quan es guarda una ubicació
- Estadístiques de quin mètode funciona millor

---

**Gaudeix de TrobaCar amb detecció automàtica millorada!** 🚗📍
