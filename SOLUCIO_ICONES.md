# Solució ràpida per l'error de les icones

## Error que has rebut:
```
ERROR: resource mipmap/ic_launcher not found
```

## Solució: Reemplaça aquests fitxers

### Opció 1: Descarrega el projecte corregit (MÉS FÀCIL)
1. Descarrega **TrobaCar_Fixed.zip**
2. Descomprimeix-lo en una nova carpeta
3. Obre aquest nou projecte a Android Studio
4. Ja està! Compila sense errors

### Opció 2: Corregeix el projecte actual manualment

Necessites crear/reemplaçar aquests 4 fitxers:

#### 1. Crea: `app/src/main/res/drawable/ic_launcher_background.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#1976D2"
        android:pathData="M0,0h108v108h-108z"/>
</vector>
```

#### 2. Crea: `app/src/main/res/drawable/ic_launcher_foreground.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    
    <!-- Cercle blanc de fons -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M54,54m-38,0a38,38 0,1 1,76 0a38,38 0,1 1,-76 0"/>
    
    <!-- Cotxe blau -->
    <path
        android:fillColor="#1976D2"
        android:pathData="M 68,50 L 66,44 C 65.5,42.5 64,41 62,41 L 46,41 C 44,41 42.5,42.5 42,44 L 40,50 L 38,50 L 38,62 C 38,63 38.5,63.5 39,63.5 L 41,63.5 C 41.5,63.5 42,63 42,62 L 42,60 L 66,60 L 66,62 C 66,63 66.5,63.5 67,63.5 L 69,63.5 C 69.5,63.5 70,63 70,62 L 70,50 L 68,50 Z M 46,57 C 44.5,57 43,55.5 43,54 C 43,52.5 44.5,51 46,51 C 47.5,51 49,52.5 49,54 C 49,55.5 47.5,57 46,57 Z M 62,57 C 60.5,57 59,55.5 59,54 C 59,52.5 60.5,51 62,51 C 63.5,51 65,52.5 65,54 C 65,55.5 63.5,57 62,57 Z M 42,48 L 44,43.5 L 64,43.5 L 66,48 L 42,48 Z"/>
    
    <!-- Pin de localització vermell -->
    <path
        android:fillColor="#F44336"
        android:pathData="M 54,28 C 50,28 47,31 47,35 C 47,39 54,48 54,48 C 54,48 61,39 61,35 C 61,31 58,28 54,28 Z M 54,37.5 C 52.5,37.5 51.5,36.5 51.5,35 C 51.5,33.5 52.5,32.5 54,32.5 C 55.5,32.5 56.5,33.5 56.5,35 C 56.5,36.5 55.5,37.5 54,37.5 Z"/>
</vector>
```

#### 3. Reemplaça: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

#### 4. Reemplaça: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

## Després d'aplicar la solució:

1. **A Android Studio**: `Build > Clean Project`
2. Després: `Build > Rebuild Project`
3. Ara ja pots compilar l'APK sense errors!

## Per què va passar això?

Els fitxers XML originals de les icones no estaven configurats correctament com a icones adaptatives d'Android. Ara usen el format correcte amb background + foreground.

El logo segueix sent el mateix (cotxe blau amb pin vermell), només està millor estructurat! 🚗📍
