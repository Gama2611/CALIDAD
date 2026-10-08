# Analizador de Formulaciones de Extrusión — ADIPACK (Android nativo, Kotlin)

App Android nativa (Kotlin + Jetpack Compose) del analizador de formulaciones de película soplada
mono/bi/tricapa. Es la versión Android del analizador en Python: mismo motor de reglas, mismas máquinas,
mismo plan de Calidad. El APK lo compila **GitHub Actions**; no hace falta instalar Android Studio.

## Compilar el APK con GitHub Actions

1. Crear una cuenta en https://github.com (si no la tiene) y un repositorio nuevo, por ejemplo
   `analizador-extrusion` (puede ser **privado**).
2. Subir **todo el contenido** de esta carpeta (incluida la carpeta oculta `.github`):
   - Desde la web: *Add file → Upload files* y arrastrar los archivos y carpetas.
     Si la carpeta `.github` no se sube al arrastrar, créela a mano con *Add file → Create new file* y el nombre
     `.github/workflows/android.yml`, pegando el contenido del archivo.
   - O con Git: `git init`, `git add .`, `git commit -m "App"`, `git branch -M main`,
     `git remote add origin <url>`, `git push -u origin main`.
3. Abrir la pestaña **Actions** del repositorio. El flujo **Compilar APK Android** arranca solo con cada
   subida (o con *Run workflow*). Tarda unos 5–8 minutos.
4. Al terminar (✔ verde), entrar a la ejecución y descargar el artefacto **AnalizadorExtrusion-APK** (un .zip con
   el APK).
5. Pasar el APK al teléfono e instalarlo. Android pedirá permitir "instalar apps de origen desconocido" para el
   explorador de archivos o el navegador: es normal en apps internas.

Si una compilación falla (✖ rojo), abra el paso que falló, copie el error y envíelo para corregirlo.

### Versiones y publicación
- Cada ejecución sube el número de versión automáticamente (`1.0.<número de ejecución>`).
- Para publicar una versión descargable en *Releases*: crear una etiqueta que empiece por `v`
  (*Releases → Draft a new release → Choose a tag → v1.0.0 → Publish*).

### Firma propia (recomendado si la app se distribuirá a varios equipos)
El APK *debug* se firma con una llave temporal distinta en cada compilación de GitHub: para actualizar la app
habría que desinstalar la anterior (se pierden catálogo y formulaciones guardadas). Para evitarlo, cree una
llave propia una sola vez:

```
keytool -genkeypair -v -keystore adipack.jks -alias adipack -keyalg RSA -keysize 2048 -validity 10000
```

y en *Settings → Secrets and variables → Actions → New repository secret* cree:

| Secret | Valor |
|---|---|
| `KEYSTORE_BASE64` | contenido de `adipack.jks` en Base64 (`base64 -w0 adipack.jks` en Linux, o `certutil -encode` en Windows y quitar las líneas BEGIN/END) |
| `KEYSTORE_PASSWORD` | contraseña del keystore |
| `KEY_ALIAS` | `adipack` |
| `KEY_PASSWORD` | contraseña de la llave |

Con esos secretos el flujo también genera `AnalizadorExtrusion-<n>.apk` firmado con la llave de la empresa.
Guarde `adipack.jks` en un lugar seguro: sin ella no se pueden publicar actualizaciones.

## Qué hace la app

**1. Formulación** (en este orden)
1. *Máquina y orden de trabajo*: máquina (el Ø de cabezal se toma solo), ancho de la OT en **cm**, espesor,
   presentación (tubo / lámina abierta / dos láminas), pistas, fuelle y refile, **tipo de mezcla** (baja o alta
   densidad). El **BUR** se calcula en vivo: `BUR = 2 × ancho plano / (π × Ø cabezal)`, con semáforo y
   sugerencia de otra máquina si no conviene.
2. *Tratamiento y procesos*: ¿lleva corona? (y cara tratada), flexografía, laminación, sellado, empaque
   automático, ¿lleva zipper?
3. *Producto y estructura*: pan, tortillas, pollo, sacos, laminados, bolsas generales; mono/bi/tricapa; gap.
4. *Formulación por capa*: N resinas con %, recuperado (%, tipo, MFI) y colorante (tipo de MB, %, MFI).
   Guardar y abrir formulaciones.

**2. Resultados** (sin internet): indicadores de burbuja, espesor, planitud y sombras (más uno por cada
proceso marcado), BUR de la máquina seleccionada, propiedades por capa, puntos críticos por proceso
(flexografía, laminación, sellado, zipper, empaque automático) y de extrusión, ventana de temperaturas,
y plan de Calidad con tolerancias 🟢 óptimo / 🟡 crítico / 🔴 fuera de rango, cerrando con los controles
**en máquinas de sellado** (apariencia y fuerza de sello, temperatura ideal, velocidad en golpes/min, zipper).
Botón para **compartir el reporte** por WhatsApp, correo, etc.

**3. Análisis IA**: Google Gemini (gratis con API key de https://aistudio.google.com/apikey). Diagnóstico,
causas raíz, límites de proceso, mejoras y plan de Calidad.

**4. Catálogo**: agregar/editar grados (Westlake, Pacific, Senator, CERTENE, ExxonMobil…). **Leer ficha PDF (IA)**:
se elige la TDS del fabricante en el teléfono y Gemini llena MFI, densidad, slip, AB, PPA y proceso.

## Ajustes a la planta (en el código)

| Qué | Dónde |
|---|---|
| Máquinas, cabezales y nº de capas | `MAQUINAS` en `app/src/main/java/net/adipack/analizador/Motor.kt` |
| Rangos por producto (espesor, BUR, slip, AB, recuperado) | `PRODUCTOS` en `Motor.kt` |
| Perfiles de temperatura por familia | `PERFILES_TEMP` en `Motor.kt` |
| Rangos de BUR alta/baja densidad | `BUR_ALTA_IDEAL`, `BUR_ALTA_PRACTICO`, `BUR_BAJA_PRACTICO` en `Motor.kt` |
| Tolerancias de Calidad | función `planCalidad` en `Motor.kt` |
| Catálogo base de resinas | `CATALOGO_BASE` en `Datos.kt` |
| Colores corporativos | `Rosa`, `RosaClaro`, `RosaOscuro` en `MainActivity.kt` |
| Ícono de la app | `app/src/main/res/drawable/ic_launcher.xml` |

Se asumió que las extrusoras #1, #3, #9 y #10 son monocapa y las coextrusoras hacen hasta 3 capas.

## Notas
- Los grados con `*` usan valores típicos de su familia, no los de su ficha técnica: confírmelos en el Catálogo.
- Todos los rangos y tolerancias son orientativos de práctica de proceso.
- El plan gratuito de Gemini tiene límite de consultas por minuto/día y Google puede usar lo enviado:
  no incluya datos confidenciales de clientes.
- Los datos (catálogo editado, formulaciones, API key) se guardan sólo en el teléfono.

## Estructura del proyecto

```
.github/workflows/android.yml   ← compila el APK en GitHub
settings.gradle.kts, build.gradle.kts, gradle.properties
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/net/adipack/analizador/
    Motor.kt         motor de reglas, BUR, postprocesos, plan de Calidad, reporte
    Datos.kt         catálogo de resinas, modelo de formulación, almacenamiento
    Gemini.kt        API de Google Gemini (análisis y lectura de fichas PDF)
    MainActivity.kt  interfaz (4 pestañas)
```
