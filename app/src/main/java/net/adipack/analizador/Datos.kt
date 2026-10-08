package net.adipack.analizador

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/*
 * Catálogo de resinas, modelo de la formulación y persistencia local.
 *
 * IMPORTANTE: los valores de grados comerciales son REFERENCIALES.
 *  - verificado = false -> valores típicos de la familia, NO confirmados con la ficha técnica (TDS).
 *  - verificado = true  -> confirmados por el usuario o leídos de la TDS en PDF.
 * Unidades: MFI g/10 min (190 °C / 2.16 kg), densidad g/cm³, slip/antibloqueo en ppm.
 */

val FAMILIAS = linkedMapOf(
    "LDPE" to "LDPE (baja densidad, ramificado)",
    "LLDPE_C4" to "LLDPE Buteno (C4)",
    "LLDPE_C6" to "LLDPE Hexeno (C6)",
    "LLDPE_C8" to "LLDPE Octeno (C8)",
    "mLLDPE" to "mLLDPE Metaloceno",
    "MDPE" to "MDPE (media densidad)",
    "HDPE" to "HDPE película (HMW)",
    "EVA" to "EVA",
    "MB_ADITIVO" to "Masterbatch de aditivo",
)

val LINEALES = setOf("LLDPE_C4", "LLDPE_C6", "LLDPE_C8", "mLLDPE")

/** Valores típicos por familia: (MFI, densidad) */
val TIPICOS = mapOf(
    "LDPE" to (2.0 to 0.922),
    "LLDPE_C4" to (1.0 to 0.918),
    "LLDPE_C6" to (1.0 to 0.918),
    "LLDPE_C8" to (1.0 to 0.920),
    "mLLDPE" to (1.0 to 0.918),
    "MDPE" to (0.2 to 0.935),
    "HDPE" to (0.06 to 0.950),
    "EVA" to (2.0 to 0.930),
    "MB_ADITIVO" to (10.0 to 0.950),
)

@Serializable
data class Grado(
    val id: String,
    val marca: String,
    val grado: String,
    val familia: String,
    val proceso: String = "Soplado",
    val mfi: Double,
    val densidad: Double,
    val slipPpm: Double = 0.0,
    val abPpm: Double = 0.0,
    val ppa: Boolean = false,
    val antifog: Boolean = false,
    val uv: Boolean = false,
    val nota: String = "",
    val verificado: Boolean = false,
    val fuente: String = "",
)

private fun g(
    id: String, marca: String, grado: String, familia: String, proceso: String = "Soplado",
    mfi: Double? = null, densidad: Double? = null, slip: Double = 0.0, ab: Double = 0.0,
    ppa: Boolean = false, antifog: Boolean = false, uv: Boolean = false, nota: String = "",
): Grado {
    val t = TIPICOS.getValue(familia)
    return Grado(id, marca, grado, familia, proceso, mfi ?: t.first, densidad ?: t.second, slip, ab, ppa, antifog, uv, nota)
}

private const val NOTA_FAM = "Valores típicos de la familia; confirmar con la TDS del grado."

val CATALOGO_BASE: List<Grado> = listOf(
    // --- Genéricos ---
    g("gen-ldpe-2", "Genérico", "LDPE película MFI 2.0", "LDPE", nota = "Referencia genérica"),
    g("gen-ldpe-03", "Genérico", "LDPE alta resistencia en fundido MFI 0.3", "LDPE", mfi = 0.3, densidad = 0.921,
        nota = "Ideal para estabilizar burbuja en mezclas con LLDPE"),
    g("gen-c4", "Genérico", "LLDPE C4 MFI 1.0", "LLDPE_C4", nota = "Referencia genérica"),
    g("gen-c4-2", "Genérico", "LLDPE C4 MFI 2.0", "LLDPE_C4", mfi = 2.0, nota = "Referencia genérica"),
    g("gen-c6", "Genérico", "LLDPE C6 MFI 1.0", "LLDPE_C6", nota = "Referencia genérica"),
    g("gen-c8", "Genérico", "LLDPE C8 MFI 1.0", "LLDPE_C8", nota = "Referencia genérica"),
    g("gen-mlldpe", "Genérico", "mLLDPE C6 MFI 1.0", "mLLDPE", nota = "Referencia genérica"),
    g("gen-mdpe", "Genérico", "MDPE película MFI 0.2", "MDPE", nota = "Referencia genérica"),
    g("gen-hdpe", "Genérico", "HDPE HMW película", "HDPE", nota = "MFI muy bajo; en TDS suele darse HLMI (21.6 kg)"),
    g("gen-eva", "Genérico", "EVA 4-9% VA MFI 2.0", "EVA", nota = "Mejora sellado a baja temperatura"),
    // --- CERTENE (valores por confirmar) ---
    g("cer-ldf222c", "CERTENE", "LDF-222C", "LDPE", nota = "Alta claridad, con slip y AB. $NOTA_FAM"),
    g("cer-ldf221c", "CERTENE", "LDF-221C", "LDPE", nota = "Uso general, sin slip. $NOTA_FAM"),
    g("cer-llbf218a", "CERTENE", "LLBF-218A", "LLDPE_C4", mfi = 2.0, nota = "Con slip/AB alto. $NOTA_FAM"),
    g("cer-llbf122", "CERTENE", "LLBF-122D / 122F", "LLDPE_C6", ppa = true, nota = "Slip/AB/PPA. $NOTA_FAM"),
    g("cer-llgf220a", "CERTENE", "LLGF-220A", "LLDPE_C8", proceso = "Cast", mfi = 2.0, nota = "Indicada como Cast. $NOTA_FAM"),
    g("cer-ld02hc", "CERTENE", "LD02HC", "LDPE", mfi = 2.0, slip = 750.0, ab = 1000.0,
        nota = "Slip 750 ppm / AB 1000 ppm según la planta. Confirmar densidad con TDS."),
    // --- ExxonMobil ---
    g("xom-ll1001", "ExxonMobil", "LL 1001 (serie)", "LLDPE_C4", mfi = 1.0, densidad = 0.918,
        nota = "El sufijo define slip/AB. $NOTA_FAM"),
    g("xom-ll1002", "ExxonMobil", "LL 1002 (serie)", "LLDPE_C4", mfi = 2.0, densidad = 0.918,
        nota = "El sufijo define slip/AB. $NOTA_FAM"),
    g("xom-ll3001", "ExxonMobil", "LL 3001 (serie)", "LLDPE_C6", mfi = 1.0, densidad = 0.917,
        nota = "El sufijo define slip/AB. $NOTA_FAM"),
    g("xom-exceed1018", "ExxonMobil", "Exceed 1018 (serie)", "mLLDPE", mfi = 1.0, densidad = 0.918,
        nota = "Metaloceno; requiere PPA y buena refrigeración. $NOTA_FAM"),
    g("xom-enable2010", "ExxonMobil", "Enable 2010 (serie)", "mLLDPE", mfi = 1.0, densidad = 0.920,
        nota = "mPE con ramificación larga: mejor estabilidad de burbuja. $NOTA_FAM"),
    g("xom-hta108", "ExxonMobil", "HTA 108", "HDPE", mfi = 0.7, densidad = 0.961, nota = NOTA_FAM),
    // --- Westlake / Pacific / Senator: agregar desde el Catálogo ---
    // --- Masterbatches de aditivo ---
    g("mb-slip", "Masterbatch", "MB Slip (erucamida 5%)", "MB_ADITIVO", slip = 50000.0, nota = "Ajustar % activo al proveedor"),
    g("mb-ab", "Masterbatch", "MB Antibloqueo (sílice 20%)", "MB_ADITIVO", ab = 200000.0, nota = "Ajustar % activo al proveedor"),
    g("mb-slipab", "Masterbatch", "MB Slip+AB (5% / 10%)", "MB_ADITIVO", slip = 50000.0, ab = 100000.0,
        nota = "Ajustar % activo al proveedor"),
    g("mb-ppa", "Masterbatch", "MB Ayuda de proceso (PPA)", "MB_ADITIVO", ppa = true,
        nota = "Elimina fractura de fundido en LLDPE/mLLDPE"),
    g("mb-antifog", "Masterbatch", "MB Antifog", "MB_ADITIVO", antifog = true,
        nota = "Para productos empacados tibios (tortillas, pan caliente)"),
    g("mb-uv", "Masterbatch", "MB UV (HALS)", "MB_ADITIVO", uv = true, nota = "Sacos a la intemperie"),
)

// ----------------------------------------------------------------------------
// Formulación
// ----------------------------------------------------------------------------
@Serializable
data class ResinaPct(val id: String? = null, val pct: String = "")

@Serializable
data class Recuperado(val pct: String = "", val tipo: String = "Propio limpio (misma línea)", val mfi: String = "2.0")

@Serializable
data class Colorante(val tipo: String = "Natural (sin color)", val pct: String = "", val mfi: String = "10")

@Serializable
data class Capa(
    val nombre: String,
    val pct: String,
    val resinas: List<ResinaPct> = listOf(ResinaPct()),
    val recuperado: Recuperado = Recuperado(),
    val colorante: Colorante = Colorante(),
)

@Serializable
data class Formulacion(
    // 1. Máquina y orden de trabajo
    val maquina: String? = null,
    val anchoOtCm: String = "",
    val presentacion: String = "Tubo (ancho OT = ancho plano)",
    val pistas: String = "1",
    val fuelleCm: String = "",
    val refileCm: String = "",
    val espesorUm: String = "30",
    val tipoMezcla: String? = null,
    // 2. Tratamiento, procesos posteriores y empaque
    val corona: Boolean = false,
    val ladoCorona: String = "Externa",
    val flexografia: Boolean = false,
    val laminacion: Boolean = false,
    val sellado: Boolean = false,
    val empaqueAutomatico: Boolean = false,
    val zipper: Boolean = false,
    // 3. Producto y estructura
    val producto: String = "pan",
    val estructura: String = "Monocapa",
    val gapMm: String = "",
    val exterior: Boolean = false,
    // 4. Capas
    val capas: List<Capa> = capasPara("Monocapa"),
)

fun capaVacia(nombre: String, pct: String) = Capa(nombre, pct)

fun capasPara(estructura: String): List<Capa> {
    val nombres = ESTRUCTURAS[estructura] ?: listOf("Única")
    val pcts = PCT_DEFECTO[estructura] ?: listOf("100")
    return nombres.indices.map { capaVacia(nombres[it], pcts[it]) }
}

// ----------------------------------------------------------------------------
// Persistencia local (almacenamiento privado de la app)
// ----------------------------------------------------------------------------
class Almacen(ctx: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val fCatalogo = File(ctx.filesDir, "catalogo_usuario.json")
    private val fFormulas = File(ctx.filesDir, "formulaciones.json")
    private val prefs = ctx.getSharedPreferences("config", Context.MODE_PRIVATE)

    private fun entradasUsuario(): List<Grado> =
        runCatching { json.decodeFromString<List<Grado>>(fCatalogo.readText()) }.getOrDefault(emptyList())

    fun catalogo(): Map<String, Grado> {
        val m = LinkedHashMap<String, Grado>()
        CATALOGO_BASE.forEach { m[it.id] = it }
        entradasUsuario().forEach { m[it.id] = it }
        return m
    }

    fun guardarGrado(gr: Grado) {
        val lista = entradasUsuario().filter { it.id != gr.id } + gr
        fCatalogo.writeText(json.encodeToString(lista))
    }

    /** Elimina una entrada del usuario. Si era un grado base, vuelve al valor original. */
    fun eliminarGrado(id: String): Boolean {
        val lista = entradasUsuario()
        if (lista.none { it.id == id }) return false
        fCatalogo.writeText(json.encodeToString(lista.filter { it.id != id }))
        return true
    }

    fun formulaciones(): Map<String, Formulacion> =
        runCatching { json.decodeFromString<Map<String, Formulacion>>(fFormulas.readText()) }.getOrDefault(emptyMap())

    fun guardarFormulacion(nombre: String, f: Formulacion) {
        val m = formulaciones().toMutableMap()
        m[nombre] = f
        fFormulas.writeText(json.encodeToString(m))
    }

    var apiKey: String
        get() = prefs.getString("gemini_key", "") ?: ""
        set(v) = prefs.edit().putString("gemini_key", v.trim()).apply()

    var modelo: String
        get() = prefs.getString("gemini_modelo", Gemini.MODELO_DEFECTO) ?: Gemini.MODELO_DEFECTO
        set(v) = prefs.edit().putString("gemini_modelo", v.trim().ifBlank { Gemini.MODELO_DEFECTO }).apply()
}
