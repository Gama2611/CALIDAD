package net.adipack.analizador

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Motor de análisis de formulaciones de película soplada (mono/bi/tricapa).
 * Sistema de reglas de práctica de proceso: NO reemplaza la ficha técnica ni las pruebas en máquina.
 * Los rangos son orientativos: ajústelos a la planta en PRODUCTOS, PERFILES_TEMP y MAQUINAS.
 */

// ============================================================================
// Utilidades de formato
// ============================================================================
fun num(s: String?, def: Double = 0.0): Double = s?.trim()?.replace(",", ".")?.toDoubleOrNull() ?: def
fun fm(v: Double, d: Int): String = String.format(Locale.US, "%." + d + "f", v)
fun pc(v: Double): String = fm(v * 100, 0)
fun gn(v: Double): String =
    if (v == Math.rint(v) && abs(v) < 1e12) v.toLong().toString() else fm(v, 4).trimEnd('0').trimEnd('.')

// ============================================================================
// Datos de referencia
// ============================================================================
val ESTRUCTURAS = linkedMapOf(
    "Monocapa" to listOf("Única"),
    "Bicapa" to listOf("A - Externa", "B - Interna"),
    "Tricapa" to listOf("A - Externa", "B - Central", "C - Interna"),
)
val PCT_DEFECTO = mapOf(
    "Monocapa" to listOf("100"),
    "Bicapa" to listOf("50", "50"),
    "Tricapa" to listOf("25", "50", "25"),
)
val CAPAS_ESTRUCTURA = mapOf("Monocapa" to 1, "Bicapa" to 2, "Tricapa" to 3)

data class InfoColor(val pigmento: Double, val densidad: Double, val absorbeSlip: Boolean, val higroscopico: Boolean)

val COLORANTES: Map<String, InfoColor?> = linkedMapOf<String, InfoColor?>(
    "Natural (sin color)" to null,
    "Blanco (TiO2)" to InfoColor(0.65, 2.0, true, true),
    "Negro (negro de humo)" to InfoColor(0.40, 1.15, true, false),
    "Color orgánico" to InfoColor(0.25, 1.05, false, false),
    "Color inorgánico" to InfoColor(0.40, 1.40, false, false),
)

val TIPOS_RECUPERADO = listOf("Propio limpio (misma línea)", "Propio impreso", "Externo / comprado")

data class Producto(
    val nombre: String,
    val alimentario: Boolean,
    val espesor: Pair<Double, Double>,
    val bur: Pair<Double, Double>,
    val slip: Pair<Double, Double>,
    val ab: Pair<Double, Double>,
    val recContacto: Double,
    val recOtra: Double,
    val recMono: Double,
    val requisitos: String,
    val antifog: Boolean = false,
    val laminado: Boolean = false,
)

val PRODUCTOS = linkedMapOf(
    "pan" to Producto("Bolsa para pan", true, 20.0 to 40.0, 2.0 to 2.8, 600.0 to 1500.0, 1000.0 to 3000.0,
        0.0, 10.0, 5.0, "Claridad, brillo, buen deslizamiento en embolsado, sin olor, sellado limpio."),
    "tortillas" to Producto("Bolsa para tortillas", true, 25.0 to 45.0, 2.0 to 2.8, 400.0 to 1200.0, 1000.0 to 3000.0,
        0.0, 10.0, 5.0, "Contacto alimentario; producto tibio genera condensación; sellado e impresión.", antifog = true),
    "pollo" to Producto("Bolsa para pollo (refrigerado/congelado)", true, 30.0 to 70.0, 2.2 to 3.0, 300.0 to 1000.0,
        800.0 to 2500.0, 0.0, 10.0, 5.0,
        "Tenacidad a baja temperatura (-18 °C), resistencia a perforación por huesos, sello hermético."),
    "sacos" to Producto("Sacos industriales", false, 80.0 to 200.0, 2.5 to 3.8, 0.0 to 300.0, 0.0 to 1500.0,
        30.0, 40.0, 30.0, "Alto impacto, resistencia a la caída, al rasgado y a la fluencia (creep); COF alto para estiba."),
    "laminados" to Producto("Película para laminación", true, 25.0 to 120.0, 2.0 to 2.8, 0.0 to 600.0, 500.0 to 2500.0,
        0.0, 10.0, 5.0, "Planitud y perfil de espesor muy uniformes, cara a laminar sin slip y tratada, libre de geles.",
        laminado = true),
    "generales" to Producto("Bolsas comerciales generales", false, 15.0 to 80.0, 2.0 to 3.5, 300.0 to 1200.0,
        500.0 to 3000.0, 30.0, 50.0, 40.0, "Costo, resistencia razonable, apariencia y apertura fácil."),
)

/** (alimentación, barril min, barril max, dado min, dado max) en °C */
val PERFILES_TEMP = mapOf(
    "LDPE" to intArrayOf(150, 160, 185, 180, 195),
    "LLDPE_C4" to intArrayOf(165, 180, 205, 200, 215),
    "LLDPE_C6" to intArrayOf(170, 185, 210, 205, 220),
    "LLDPE_C8" to intArrayOf(170, 185, 210, 205, 220),
    "mLLDPE" to intArrayOf(165, 180, 205, 200, 215),
    "MDPE" to intArrayOf(170, 185, 210, 200, 215),
    "HDPE" to intArrayOf(175, 190, 215, 200, 215),
    "EVA" to intArrayOf(140, 150, 175, 170, 185),
    "MB_ADITIVO" to intArrayOf(150, 160, 185, 180, 195),
    "RECUPERADO" to intArrayOf(155, 165, 190, 185, 200),
)

/** Máquinas de la planta: diámetro de cabezal en mm. capasMax: 1 = monocapa, 3 = coextrusora. */
data class Maquina(val cabezalMm: Int, val capasMax: Int)

val MAQUINAS = linkedMapOf(
    "Extrusora #1" to Maquina(300, 1),
    "Coextrusora #2" to Maquina(250, 3),
    "Extrusora #3" to Maquina(300, 1),
    "Coextrusora #4" to Maquina(200, 3),
    "Coextrusora #7" to Maquina(350, 3),
    "Coextrusora #8" to Maquina(350, 3),
    "Extrusora #9" to Maquina(300, 1),
    "Extrusora #10" to Maquina(110, 1),
)

val TIPOS_MEZCLA = linkedMapOf(
    "baja" to "Baja densidad (LDPE / LLDPE)",
    "alta" to "Alta densidad (HDPE)",
)
private val BUR_ALTA_IDEAL = 3.0 to 4.5
private val BUR_ALTA_PRACTICO = 2.5 to 5.5
private val BUR_BAJA_PRACTICO = 1.5 to 4.0

val PRESENTACIONES = linkedMapOf(
    "Tubo (ancho OT = ancho plano)" to "tubo",
    "Lámina abierta (1 corte lateral)" to "lamina1",
    "Dos láminas (2 cortes laterales)" to "lamina2",
)

/** Temperatura orientativa de inicio de sellado (°C) por familia. */
val SIT_FAMILIA = mapOf(
    "LDPE" to 105.0, "LLDPE_C4" to 110.0, "LLDPE_C6" to 108.0, "LLDPE_C8" to 105.0, "mLLDPE" to 95.0,
    "MDPE" to 118.0, "HDPE" to 128.0, "EVA" to 88.0, "RECUPERADO" to 108.0,
)

val PESO = mapOf("alta" to 30, "media" to 15, "baja" to 7, "info" to 0)
val CATEGORIAS = listOf("burbuja", "espesor", "planitud", "sombras")
val NOMBRE_CAT = mapOf(
    "burbuja" to "Estabilidad de burbuja", "espesor" to "Uniformidad de espesor",
    "planitud" to "Planitud", "sombras" to "Sombras / geles / vetas",
    "producto" to "Requisitos del producto", "formulacion" to "Formulación",
    "maquina" to "Máquina y relación de soplado",
    "flexografia" to "Flexografía (impresión)", "laminacion" to "Laminación", "sellado" to "Sellado",
    "automatico" to "Empaque automático", "zipper" to "Zipper",
)
val POSTPROCESOS = listOf("automatico", "flexografia", "laminacion", "sellado", "zipper")

// ============================================================================
// Geometría: ancho plano y BUR
// ============================================================================
/**
 * Ancho plano (lay-flat) del tubo que debe salir de la burbuja para cumplir la OT (mm).
 *  - tubo:    LF = ancho·pistas + 2·fuelle + refile
 *  - lamina1: la lámina abierta mide 2·LF -> LF = (ancho·pistas + refile) / 2
 *  - lamina2: cada lámina mide LF -> LF = ancho·pistas + refile
 */
fun layFlatOt(anchoMm: Double, presentacion: String = "tubo", pistas: Int = 1, fuelleMm: Double = 0.0, refileMm: Double = 0.0): Double {
    if (anchoMm <= 0) return 0.0
    val base = anchoMm * max(1, pistas) + refileMm
    return when (presentacion) {
        "lamina1" -> base / 2
        "lamina2" -> base
        else -> base + 2 * fuelleMm
    }
}

data class BurCalc(val bur: Double, val layFlatMm: Double, val dBurbujaMm: Double, val perimetroMm: Double, val cabezalMm: Int)

/** BUR = Ø burbuja / Ø cabezal = 2·LF / (π·D) */
fun calcularBur(layFlatMm: Double, cabezalMm: Int): BurCalc? {
    if (layFlatMm <= 0 || cabezalMm <= 0) return null
    val d = 2 * layFlatMm / PI
    return BurCalc(d / cabezalMm, layFlatMm, d, 2 * layFlatMm, cabezalMm)
}

fun mezclaValida(m: String?) = m == "baja" || m == "alta"

/** (rango ideal, rango práctico) según tipo de mezcla y producto */
fun rangoBur(prodKey: String?, mezcla: String?): Pair<Pair<Double, Double>, Pair<Double, Double>> =
    if (mezcla == "alta") BUR_ALTA_IDEAL to BUR_ALTA_PRACTICO
    else (PRODUCTOS[prodKey]?.bur ?: (2.0 to 3.5)) to BUR_BAJA_PRACTICO

/** (nivel, texto). Sin tipo de mezcla no se juzga si el BUR es alto o bajo. */
fun evaluarBur(bur: Double, prodKey: String?, mezcla: String?): Pair<String, String> {
    if (!mezclaValida(mezcla)) return "Sin evaluar" to "Indique si la mezcla es de baja o alta densidad para evaluar el BUR"
    val (ideal, practico) = rangoBur(prodKey, mezcla)
    val tipo = if (mezcla == "alta") "alta densidad" else "baja densidad"
    val ri = "${gn(ideal.first)}-${gn(ideal.second)}"
    return when {
        bur < practico.first -> "Riesgo alto" to "BUR muy bajo para $tipo (mínimo práctico ${gn(practico.first)}): usar un cabezal más pequeño"
        bur > practico.second -> "Riesgo alto" to "BUR muy alto para $tipo (máximo práctico ${gn(practico.second)}): usar un cabezal más grande"
        bur < ideal.first -> "Vigilar" to "BUR bajo para $tipo (ideal $ri)"
        bur > ideal.second -> "Vigilar" to "BUR alto para $tipo (ideal $ri)"
        else -> "Estable" to "BUR adecuado para $tipo (ideal $ri)"
    }
}

data class BurMaquina(val maquina: String, val cabezalMm: Int, val bur: Double, val nivel: String, val texto: String, val admiteCapas: Boolean)

fun burPorMaquina(layFlatMm: Double, prodKey: String?, capas: Int, mezcla: String?): List<BurMaquina> =
    MAQUINAS.mapNotNull { (nombre, m) ->
        calcularBur(layFlatMm, m.cabezalMm)?.let { r ->
            val (nivel, texto) = evaluarBur(r.bur, prodKey, mezcla)
            BurMaquina(nombre, m.cabezalMm, r.bur, nivel, texto, m.capasMax >= capas)
        }
    }

// ============================================================================
// Resultados
// ============================================================================
data class Hallazgo(
    val severidad: String, val afecta: List<String>, val titulo: String,
    val detalle: String, val sugerencia: String, val capa: String? = null,
)

/** afecta: categorías separadas por coma, p. ej. "maquina,burbuja" */
fun hz(sev: String, afecta: String, titulo: String, detalle: String, sugerencia: String, capa: String? = null) =
    Hallazgo(sev, afecta.split(","), titulo, detalle, sugerencia, capa)

class CapaCalc(
    val nombre: String,
    val pctEstructura: Double,
    val mfi: Double,
    val densidad: Double,
    val frac: Map<String, Double>,
    val fLdpe: Double,
    val fLin: Double,
    val fMlin: Double,
    val fTenaz: Double,
    val fHdpe: Double,
    val slip: Double,
    val ab: Double,
    val ppa: Boolean,
    val antifog: Boolean,
    val uv: Boolean,
    val recPct: Double,
    val recTipo: String,
    val recMfi: Double,
    val colTipo: String,
    val colPct: Double,
    val colMfi: Double,
    val pigmentoPct: Double,
    val perfilTemp: IntArray,
    val noVerificados: List<String>,
) {
    var wEst = 0.0
    var espesorUm = 0.0
    var slipSuperficie = slip
}

data class Global(
    val mfi: Double, val densidad: Double, val fLdpe: Double, val fLin: Double, val fMlin: Double,
    val fTenaz: Double, val fHdpe: Double, val recPct: Double, val pigmentoPct: Double,
)

data class Geometria(
    val bur: Double? = null, val dadoMm: Double? = null, val layFlatMm: Double? = null, val gapMm: Double? = null,
    val ddr: Double? = null, val maquina: String? = null, val anchoOtMm: Double? = null, val fuelleMm: Double? = null,
    val pistas: Int = 1, val presentacion: String = "tubo", val dBurbujaMm: Double? = null,
)

data class Indicador(val puntos: Int, val nivel: String)
data class Sit(val capa: String, val sit: Int, val barraMin: Int, val barraMax: Int)
data class Extrusor(val capa: String, val alimentacion: String, val barril: String, val dado: String, val masa: String)
data class Ventana(
    val extrusores: List<Extrusor>, val lineaEnfriamiento: String, val burSugerido: String,
    val gapSugerido: String, val variables: List<String>,
)
data class FilaCalidad(
    val prueba: String, val frecuencia: String, val optimo: String, val critico: String, val fuera: String,
    val grupo: String,
)

data class Resultado(
    val error: String? = null,
    val hallazgos: List<Hallazgo> = emptyList(),
    val producto: String = "",
    val prodKey: String = "generales",
    val estructura: String = "",
    val espesor: Double = 0.0,
    val requisitos: String = "",
    val corona: Boolean = false,
    val postprocesos: List<String> = emptyList(),
    val sit: Sit? = null,
    val tipoMezcla: String? = null,
    val zipper: Boolean = false,
    val burEval: Pair<String, String>? = null,
    val global: Global? = null,
    val geo: Geometria = Geometria(),
    val capas: List<CapaCalc> = emptyList(),
    val indicadores: Map<String, Indicador> = emptyMap(),
    val ventana: Ventana? = null,
    val calidad: List<FilaCalidad> = emptyList(),
)

// ============================================================================
// Motor
// ============================================================================
object Motor {

    /** Regla logarítmica: ln(MFI) = Σ w·ln(MFIi) */
    fun mezclaMfi(p: List<Pair<Double, Double>>): Double? {
        val v = p.filter { it.second > 0 }
        val tot = v.sumOf { it.first }
        if (tot <= 0) return null
        return exp(v.sumOf { it.first * ln(it.second) } / tot)
    }

    /** Aditividad de volúmenes específicos: 1/ρ = Σ w/ρi */
    fun mezclaDensidad(p: List<Pair<Double, Double>>): Double? {
        val v = p.filter { it.second > 0 }
        val tot = v.sumOf { it.first }
        if (tot <= 0) return null
        return tot / v.sumOf { it.first / it.second }
    }

    private class Item(
        val pct: Double, val familia: String, val mfi: Double, val dens: Double, val slip: Double, val ab: Double,
        val ppa: Boolean, val antifog: Boolean, val uv: Boolean, val nombre: String, val verificado: Boolean,
    ) { var w = 0.0 }

    fun sumaCapa(c: Capa): Double =
        c.resinas.sumOf { num(it.pct) } + num(c.recuperado.pct) +
            (if (COLORANTES[c.colorante.tipo] != null) num(c.colorante.pct) else 0.0)

    fun analizarCapa(capa: Capa, cat: Map<String, Grado>, avisos: MutableList<Hallazgo>): CapaCalc? {
        val items = mutableListOf<Item>()
        for (r in capa.resinas) {
            val pct = num(r.pct)
            val gr = r.id?.let { cat[it] }
            if (pct <= 0 || gr == null) continue
            items += Item(pct, gr.familia, gr.mfi, gr.densidad, gr.slipPpm, gr.abPpm, gr.ppa, gr.antifog, gr.uv,
                "${gr.marca} ${gr.grado}", gr.verificado)
        }
        val recPctRaw = num(capa.recuperado.pct)
        if (recPctRaw > 0) {
            items += Item(recPctRaw, "RECUPERADO", num(capa.recuperado.mfi, 2.0), 0.925, 0.0, 0.0, false, false, false,
                "Material recuperado", true)
        }
        val colTipo = capa.colorante.tipo
        val info = COLORANTES[colTipo]
        val colPctRaw = if (info != null) num(capa.colorante.pct) else 0.0
        if (info != null && colPctRaw > 0) {
            items += Item(colPctRaw, "MB_ADITIVO", num(capa.colorante.mfi, 10.0), info.densidad, 0.0, 0.0, false, false,
                false, "MB $colTipo", true)
        }

        val nombre = capa.nombre
        if (items.isEmpty()) {
            avisos += hz("alta", "formulacion", "$nombre: capa sin materiales",
                "No hay resinas con porcentaje mayor a cero.", "Agregue al menos una resina.", nombre)
            return null
        }
        val suma = items.sumOf { it.pct }
        if (abs(suma - 100) > 0.5) {
            avisos += hz(if (abs(suma - 100) > 2) "alta" else "baja", "formulacion",
                "$nombre: la formulación suma ${fm(suma, 1)}%", "Se normalizó a 100% para el cálculo.",
                "Corrija los porcentajes para que la capa sume 100%.", nombre)
        }
        items.forEach { it.w = it.pct / suma }

        val polim = items.filter { it.familia != "MB_ADITIVO" }
        val wp = polim.sumOf { it.w }.let { if (it > 0) it else 1.0 }
        val frac = HashMap<String, Double>()
        polim.forEach { frac[it.familia] = (frac[it.familia] ?: 0.0) + it.w / wp }

        val perfil = DoubleArray(5)
        items.forEach { i ->
            val p = PERFILES_TEMP[i.familia] ?: PERFILES_TEMP.getValue("LDPE")
            for (k in 0 until 5) perfil[k] += i.w * p[k]
        }
        val mfi = mezclaMfi(items.map { it.w to it.mfi }) ?: 1.0
        val ajuste = when {
            mfi < 0.3 -> 10
            mfi < 0.8 -> 5
            mfi > 3 -> -5
            else -> 0
        }
        val perfilInt = IntArray(5) { (perfil[it] + ajuste).roundToInt() }

        fun fr(k: String) = frac[k] ?: 0.0
        val colPct = colPctRaw / suma * 100
        return CapaCalc(
            nombre = nombre,
            pctEstructura = num(capa.pct),
            mfi = mfi,
            densidad = mezclaDensidad(polim.map { it.w to it.dens }) ?: 0.92,
            frac = frac,
            fLdpe = fr("LDPE") + fr("EVA") * 0.5,
            fLin = LINEALES.sumOf { fr(it) },
            fMlin = fr("mLLDPE"),
            fTenaz = fr("LLDPE_C6") + fr("LLDPE_C8") + fr("mLLDPE"),
            fHdpe = fr("HDPE") + fr("MDPE") * 0.5,
            slip = items.sumOf { it.w * it.slip },
            ab = items.sumOf { it.w * it.ab },
            ppa = items.any { it.ppa },
            antifog = items.any { it.antifog },
            uv = items.any { it.uv },
            recPct = recPctRaw / suma * 100,
            recTipo = capa.recuperado.tipo,
            recMfi = num(capa.recuperado.mfi, 2.0),
            colTipo = colTipo,
            colPct = colPct,
            colMfi = num(capa.colorante.mfi, 10.0),
            pigmentoPct = if (info != null) colPct * info.pigmento else 0.0,
            perfilTemp = perfilInt,
            noVerificados = items.filter { !it.verificado }.map { it.nombre },
        )
    }

    // ------------------------------------------------------------------------
    fun analizar(form: Formulacion, cat: Map<String, Grado>): Resultado {
        val H = mutableListOf<Hallazgo>()
        val prodKey = if (form.producto in PRODUCTOS) form.producto else "generales"
        val P = PRODUCTOS.getValue(prodKey)
        val estructura = form.estructura
        val espesor = num(form.espesorUm, 30.0)
        val flexo = form.flexografia
        val laminacion = form.laminacion || P.laminado
        val sellado = form.sellado
        val automatico = form.empaqueAutomatico
        val zipper = form.zipper
        val mezcla = form.tipoMezcla
        val corona = form.corona
        val maquina = form.maquina

        val capas = form.capas.mapNotNull { analizarCapa(it, cat, H) }
        if (capas.isEmpty()) return Resultado(error = "No hay capas válidas para analizar.", hallazgos = H)

        val sumaCapas = capas.sumOf { it.pctEstructura }
        if (estructura != "Monocapa" && abs(sumaCapas - 100) > 0.5) {
            H += hz("media", "formulacion", "Distribución de capas suma ${fm(sumaCapas, 0)}%",
                "Se normalizó la relación de capas.", "Ajuste la relación de capas a 100%.")
        }
        for (c in capas) {
            c.wEst = if (sumaCapas > 0) c.pctEstructura / sumaCapas else 1.0 / capas.size
            c.espesorUm = espesor * c.wEst
        }

        fun gp(sel: (CapaCalc) -> Double) = capas.sumOf { it.wEst * sel(it) }
        val G = Global(
            mfi = mezclaMfi(capas.map { it.wEst to it.mfi }) ?: 1.0,
            densidad = mezclaDensidad(capas.map { it.wEst to it.densidad }) ?: 0.92,
            fLdpe = gp { it.fLdpe }, fLin = gp { it.fLin }, fMlin = gp { it.fMlin },
            fTenaz = gp { it.fTenaz }, fHdpe = gp { it.fHdpe },
            recPct = gp { it.recPct }, pigmentoPct = gp { it.pigmentoPct },
        )

        // ---- geometría ----
        val anchoOt = num(form.anchoOtCm) * 10
        val fuelle = num(form.fuelleCm) * 10
        val refile = num(form.refileCm) * 10
        val pres = PRESENTACIONES[form.presentacion] ?: "tubo"
        val pistas = max(1, num(form.pistas, 1.0).toInt())
        val layFlat = if (anchoOt > 0) layFlatOt(anchoOt, pres, pistas, fuelle, refile) else 0.0
        val mq = maquina?.let { MAQUINAS[it] }
        val dado = mq?.cabezalMm?.toDouble() ?: 0.0
        val gap = num(form.gapMm)
        val bur: Double? = if (layFlat > 0 && dado > 0) 2 * layFlat / (PI * dado) else null
        val geo = Geometria(
            bur = bur, dadoMm = if (dado > 0) dado else null, layFlatMm = if (layFlat > 0) layFlat else null,
            gapMm = if (gap > 0) gap else null,
            ddr = if (gap > 0 && bur != null && espesor > 0) gap * 1000 / (espesor * bur) else null,
            maquina = if (mq != null) maquina else null, anchoOtMm = if (anchoOt > 0) anchoOt else null,
            fuelleMm = if (fuelle > 0) fuelle else null, pistas = pistas, presentacion = pres,
            dBurbujaMm = bur?.let { it * dado },
        )

        // ===================== MÁQUINA =====================
        val nCapas = CAPAS_ESTRUCTURA[estructura] ?: 1
        if (mq != null) {
            if (mq.capasMax < nCapas) {
                val aptas = MAQUINAS.filter { it.value.capasMax >= nCapas }.keys.joinToString(", ")
                H += hz("alta", "maquina", "$maquina no produce estructura ${estructura.lowercase()}",
                    "La máquina es de ${mq.capasMax} capa(s).", "Usar una coextrusora: $aptas.")
            }
            if (bur != null && !mezclaValida(mezcla)) {
                H += hz("info", "maquina", "BUR sin evaluar",
                    "BUR ${fm(bur, 2)}. No se indicó si la mezcla es de baja o alta densidad.",
                    "Seleccione el tipo de mezcla en la sección 1.")
            } else if (bur != null) {
                val (nivel, texto) = evaluarBur(bur, prodKey, mezcla)
                val alternativas = burPorMaquina(layFlat, prodKey, nCapas, mezcla)
                    .filter { it.nivel == "Estable" && it.admiteCapas && it.maquina != maquina }
                val altTxt = if (alternativas.isNotEmpty())
                    "Máquinas donde este ancho queda en rango: " +
                        alternativas.joinToString(", ") { "${it.maquina} (BUR ${fm(it.bur, 2)})" } + "."
                else "Ninguna máquina deja este ancho en el rango ideal; revisar pistas, presentación o ancho con el cliente."
                val alto = "alto" in texto
                if (nivel == "Riesgo alto") {
                    H += hz("alta", "maquina,burbuja,planitud", "${texto.substringBefore(':')} en $maquina (${fm(bur, 2)})",
                        if (alto) "La burbuja se estira demasiado: inestable, se adelgaza y se rompe."
                        else "Casi no se sopla: orientación sólo en máquina (rasgado fácil en MD) y riesgo de colapso de la burbuja.",
                        altTxt)
                } else if (nivel == "Vigilar") {
                    H += hz("media", "maquina,burbuja,planitud", "$texto en $maquina (${fm(bur, 2)})",
                        if (alto) "BUR alto: más exigencia a la burbuja y menos espesor de margen."
                        else "BUR bajo: orientación desbalanceada, película que rasga fácil en MD.",
                        altTxt)
                }
            }
            if (mezcla == "alta" && G.fHdpe < 0.3) {
                H += hz("media", "maquina", "Tipo de mezcla vs. formulación",
                    "Se indicó alta densidad pero la formulación tiene ${pc(G.fHdpe)}% de HDPE/MDPE.",
                    "Revisar el tipo de mezcla seleccionado o la formulación.")
            }
            if (mezcla == "baja" && G.fHdpe > 0.5) {
                H += hz("media", "maquina", "Tipo de mezcla vs. formulación",
                    "Se indicó baja densidad pero la formulación tiene ${pc(G.fHdpe)}% de HDPE.",
                    "Seleccionar alta densidad: el HDPE requiere BUR mayor y tallo alto.")
            }
        }

        // ===================== ESTABILIDAD DE BURBUJA =====================
        val restoLineal = G.fLin + G.fHdpe
        if (G.fLdpe < 0.10 && restoLineal > 0.6) {
            H += hz("alta", "burbuja", "Muy baja resistencia en fundido",
                "La estructura tiene ${pc(G.fLdpe)}% de LDPE y ${pc(restoLineal)}% de lineales/HDPE. Los lineales tienen " +
                    "poca resistencia en fundido: la burbuja tiende a respirar, oscilar o descentrarse.",
                "Incorporar 15-30% de LDPE fraccional (MFI ≤ 0.5) en las capas más gruesas, o un mPE con ramificación " +
                    "larga. Usar aro de enfriamiento de doble labio y canasta/jaula estabilizadora.")
        } else if (G.fLdpe < 0.20 && restoLineal > 0.6) {
            H += hz("media", "burbuja", "Resistencia en fundido limitada",
                "LDPE total ${pc(G.fLdpe)}%. Ventana de proceso estrecha a velocidades altas.",
                "Subir LDPE a 20-30% o usar LDPE de menor MFI; controlar temperatura del aire del aro (±1 °C).")
        }
        if (G.fMlin > 0.5) {
            H += hz("media", "burbuja", "Alto contenido de metaloceno",
                "mLLDPE ${pc(G.fMlin)}% de la estructura: burbuja sensible y mayor tendencia a fractura de fundido.",
                "Usar PPA, gap de dado amplio (≥ 2.0 mm), refrigeración eficiente y LDPE 10-20% como estabilizador.")
        }
        if (G.fHdpe > 0.4) {
            H += hz("info", "burbuja", "Estructura con HDPE significativo",
                "El HDPE de película se procesa con 'tallo alto' (cuello largo) para orientar balanceado.",
                "Línea de enfriamiento a 6-10 veces el diámetro del dado; BUR 3-4.")
        }
        if (G.mfi > 4) {
            H += hz("alta", "burbuja", "MFI global alto (${fm(G.mfi, 1)})",
                "Fundido muy fluido: burbuja débil y difícil de mantener estable.",
                "Bajar el MFI usando resinas fraccionales; reducir temperatura de masa.")
        } else if (G.mfi > 2.5) {
            H += hz("media", "burbuja", "MFI global elevado (${fm(G.mfi, 1)})",
                "Menor resistencia en fundido, sobre todo con espesores bajos.",
                "Mantener la temperatura de masa en la parte baja de la ventana.")
        }
        if (G.recPct > 25) {
            H += hz("media", "burbuja,espesor", "Recuperado global ${fm(G.recPct, 0)}%",
                "El MFI del recuperado varía por lote; provoca variaciones de presión y 'respiración' de la burbuja.",
                "Homogeneizar el recuperado (mezclar lotes), controlar su MFI por lote y dosificar gravimétricamente.")
        }
        if (espesor < 15 && restoLineal > 0.5) {
            H += hz("media", "burbuja", "Película muy delgada con predominio lineal",
                "Espesores bajos con lineales reducen la estabilidad y aumentan roturas.",
                "Aumentar LDPE o reducir velocidad; vigilar temperatura del aire.")
        }

        // ===================== POR CAPA =====================
        val contacto = if (estructura != "Monocapa") capas.last().nombre else null
        capas.forEachIndexed { idx, c ->
            val n = c.nombre
            if (c.fMlin >= 0.3 && !c.ppa) {
                H += hz("alta", "sombras,espesor", "$n: metaloceno sin PPA",
                    "Alto riesgo de fractura de fundido (piel de tiburón), líneas y depósitos en el labio del dado.",
                    "Agregar masterbatch PPA (típico 1-2%) y purgar con PPA al arranque.", n)
            } else if (c.fLin >= 0.5 && !c.ppa && c.mfi < 1.5) {
                H += hz("media", "sombras,espesor", "$n: lineal de bajo MFI sin PPA",
                    "Lineales ${pc(c.fLin)}% con MFI ${fm(c.mfi, 2)}: presión alta y posible piel de tiburón.",
                    "Usar grado con PPA o agregar MB PPA; verificar gap de dado ≥ 1.8 mm.", n)
            }
            if (estructura != "Monocapa") {
                if (c.espesorUm < 4) {
                    H += hz("alta", "espesor,sombras", "$n: capa de ${fm(c.espesorUm, 1)} µm",
                        "Capa demasiado delgada para distribuirse uniformemente; habrá zonas sin cubrir.",
                        "Aumentar la relación de esta capa o el espesor total (mínimo práctico 5-6 µm según el dado).", n)
                } else if (c.espesorUm < 6) {
                    H += hz("media", "espesor", "$n: capa delgada (${fm(c.espesorUm, 1)} µm)",
                        "Cerca del mínimo práctico de distribución del dado coextrusor.",
                        "Confirmar con el fabricante del dado el mínimo por capa.", n)
                }
            }
            if (c.recPct > 0) {
                if (c.recTipo.startsWith("Externo")) {
                    H += hz("alta", "sombras,producto", "$n: recuperado externo",
                        "Contaminación desconocida: geles, puntos negros, olor y variación de MFI/color.",
                        "Usar sólo con filtro fino (malla 80-120) y cambio de mallas programado; nunca en alimentos.", n)
                } else if (c.recTipo.startsWith("Propio impreso")) {
                    H += hz("media", "sombras", "$n: recuperado impreso",
                        "Las tintas degradan y generan puntos negros, vetas de color y olor.",
                        "Limitar a ≤ 10%, ubicar en capa central y usar desgasificación/filtrado.", n)
                }
                if (c.recPct > 35) {
                    H += hz("alta", "espesor,sombras", "$n: recuperado ${fm(c.recPct, 0)}%",
                        "Nivel alto: geles, variación de espesor y de propiedades mecánicas.",
                        "Reducir o compensar con resina virgen de mayor desempeño.", n)
                } else if (c.recPct > 20) {
                    H += hz("media", "espesor,sombras", "$n: recuperado ${fm(c.recPct, 0)}%",
                        "Aumenta geles y variación de espesor.",
                        "Controlar MFI del recuperado por lote y aumentar frecuencia de conteo de geles.", n)
                }
                if (c.mfi > 0 && c.recMfi / c.mfi > 3) {
                    H += hz("media", "espesor", "$n: recuperado mucho más fluido que la mezcla",
                        "MFI recuperado ${fm(c.recMfi, 1)} vs mezcla ${fm(c.mfi, 2)}: mezcla poco homogénea.",
                        "Peletizar con filtrado o reducir su porcentaje.", n)
                }
            }
            val info = COLORANTES[c.colTipo]
            if (info != null && c.colPct > 0) {
                if (c.mfi > 0 && c.colMfi / c.mfi > 8) {
                    H += hz("media", "sombras", "$n: vehículo del MB muy fluido",
                        "MFI del masterbatch ${fm(c.colMfi, 0)} vs capa ${fm(c.mfi, 2)}: mala dispersión → vetas y sombras.",
                        "Pedir MB con vehículo de MFI cercano a la mezcla o mejorar mezclado (husillo con zona de mezcla).", n)
                }
                if (c.pigmentoPct > 10) {
                    H += hz("media", "sombras", "$n: pigmento ${fm(c.pigmentoPct, 1)}% en la capa",
                        "Carga alta: aglomerados, rayas de dado y pérdida de propiedades.",
                        "Repartir el pigmento en más espesor (capa central) o usar MB más concentrado de mejor dispersión.", n)
                }
                if (info.higroscopico) {
                    H += hz("info", "sombras", "$n: MB blanco (TiO2) absorbe humedad",
                        "La humedad produce 'lacing' (agujeros/encaje) y burbujas en la película.",
                        "Mantener el MB cerrado y seco; secar si estuvo expuesto.", n)
                }
                if (info.absorbeSlip && c.slip > 0) {
                    H += hz("baja", "producto", "$n: pigmento reduce la eficacia del slip",
                        "TiO2 y negro de humo adsorben la erucamida: el COF real queda más alto que el esperado.",
                        "Aumentar 15-30% el slip o medir COF a las 24-48 h para ajustar.", n)
                }
                if (estructura == "Tricapa" && idx != 1 && c.wEst < 0.25 && c.pigmentoPct > 3) {
                    H += hz("media", "sombras", "$n: pigmento en capa externa delgada",
                        "En capas delgadas el pigmento se ve en vetas y desgasta el labio del dado.",
                        "Llevar el colorante a la capa central (B) para opacidad uniforme.", n)
                }
            }
        }

        // --- compatibilidad reológica entre capas adyacentes ---
        capas.zipWithNext().forEach { (a, b) ->
            if (a.mfi > 0 && b.mfi > 0) {
                val rel = max(a.mfi, b.mfi) / min(a.mfi, b.mfi)
                if (rel > 3) {
                    H += hz("alta", "espesor,sombras", "Desbalance de viscosidad ${a.nombre} / ${b.nombre}",
                        "Relación de MFI ${fm(rel, 1)}: inestabilidad interfacial (ondas, zig-zag), capas mal distribuidas.",
                        "Acercar los MFI (relación ≤ 2) o ajustar temperaturas de cada extrusor para igualar viscosidades.")
                } else if (rel > 2) {
                    H += hz("media", "espesor,sombras", "Viscosidades distintas ${a.nombre} / ${b.nombre}",
                        "Relación de MFI ${fm(rel, 1)}: posible encapsulamiento y variación de capa.",
                        "Compensar con temperatura: la capa más fluida un poco más fría.")
                }
            }
        }

        // --- gap de dado vs. material ---
        if (gap > 0) {
            if (restoLineal > 0.5 && gap < 1.5) {
                H += hz("media", "espesor,sombras", "Gap de dado ${fm(gap, 1)} mm estrecho para lineales",
                    "Los LLDPE/mLLDPE con gap estrecho elevan presión y esfuerzo de corte: fractura de fundido.",
                    "Usar gap 1.8-2.5 mm o PPA y temperatura de masa en la parte alta de la ventana.")
            } else if (restoLineal < 0.3 && gap > 2.0) {
                H += hz("baja", "burbuja", "Gap de dado ${fm(gap, 1)} mm amplio para LDPE",
                    "Mucho estiramiento (DDR alto) con LDPE: puede aumentar orientación en MD.",
                    "Gap típico para LDPE 0.8-1.5 mm.")
            }
        }

        // ===================== PLANITUD =====================
        if (estructura == "Bicapa" && capas.size == 2) {
            val dd = abs(capas[0].densidad - capas[1].densidad)
            if (dd > 0.012 || (capas[0].fHdpe - capas[1].fHdpe).pow(2) > 0.25) {
                H += hz("alta", "planitud", "Bicapa asimétrica: riesgo de enrollamiento (curl)",
                    "Diferencia de densidad entre capas ${fm(dd, 3)} g/cm³: contraen distinto al cristalizar.",
                    "Igualar densidades o pasar a tricapa simétrica (A/B/A).")
            } else if (dd > 0.006) {
                H += hz("media", "planitud", "Bicapa con densidades diferentes",
                    "Diferencia ${fm(dd, 3)} g/cm³: leve tendencia a curl.", "Vigilar curl en mesa tras 24 h.")
            }
        }
        if (estructura == "Tricapa" && capas.size == 3) {
            val a = capas[0]
            val c = capas[2]
            val dd = abs(a.densidad - c.densidad)
            if (dd > 0.010) {
                H += hz("media", "planitud", "Capas externas con densidades distintas",
                    "A vs C difieren ${fm(dd, 3)} g/cm³: tensión asimétrica, posible curl.",
                    "Aproximar densidades de A y C o compensar con espesores.")
            }
            if (abs(a.pctEstructura - c.pctEstructura) > 15) {
                H += hz("baja", "planitud", "Tricapa con capas externas de espesor distinto",
                    "La asimetría de espesor favorece el curl si los materiales difieren.",
                    "Usar relación simétrica (p. ej. 25/50/25) salvo requerimiento funcional.")
            }
        }
        if (restoLineal > 0.6) {
            H += hz("baja", "planitud", "Predominio lineal/HDPE",
                "Mayor contracción y tensiones residuales: sensible a enfriamiento no uniforme.",
                "Evitar corrientes de aire en la línea, centrar el aro, usar dado rotativo u oscilante en el colapsador.")
        }
        if (prodKey == "laminados") {
            H += hz("info", "planitud", "Laminación exige planitud",
                "Variaciones de espesor > ±5% se convierten en bolsas flojas y arrugas en la laminadora.",
                "Control automático de perfil (aro segmentado) y bobinado con tensión decreciente (taper).")
        }

        // ===================== REQUISITOS DEL PRODUCTO =====================
        val (eLo, eHi) = P.espesor
        if (espesor < eLo || espesor > eHi) {
            H += hz("media", "producto", "Espesor ${fm(espesor, 0)} µm fuera del rango típico ${gn(eLo)}-${gn(eHi)} µm",
                "Rango usual para ${P.nombre}.", "Confirmar con la especificación del cliente.")
        }
        // El slip de la capa central migra a través de pieles delgadas
        if (estructura == "Tricapa" && capas.size == 3) {
            for (piel in listOf(capas[0], capas[2])) {
                piel.slipSuperficie = piel.slip + if (piel.espesorUm < 10) 0.5 * capas[1].slip else 0.0
            }
        }
        val caras: List<Pair<String, CapaCalc>> =
            if (estructura == "Monocapa") listOf("Ambas caras" to capas[0])
            else listOf("Externa" to capas.first(), "Interna" to capas.last())
        val (sMin, sMax) = P.slip
        for ((cara, c) in caras) {
            val s = c.slipSuperficie
            if (prodKey == "sacos" && s > sMax) {
                H += hz("media", "producto", "Cara $cara: slip ${fm(s, 0)} ppm en sacos",
                    "COF bajo: los sacos se deslizan en la estiba.", "Usar grados sin slip en sacos.", c.nombre)
            } else if (prodKey != "sacos" && prodKey != "laminados" && s < sMin) {
                H += hz("media", "producto", "Cara $cara: slip ${fm(s, 0)} ppm (sugerido ${gn(sMin)}-${gn(sMax)})",
                    "COF alto: dificultad para abrir la bolsa y en embolsado automático.",
                    "Agregar MB slip o usar grado con slip.", c.nombre)
            } else if (s > sMax * 1.3 && prodKey != "sacos") {
                H += hz("baja", "producto", "Cara $cara: slip alto (${fm(s, 0)} ppm)",
                    "Exceso de slip: exudación, problemas de impresión/sello y bobinas que se deslizan (telescopio).",
                    "Reducir slip.", c.nombre)
            }
        }

        H += puntosPostproceso(form, prodKey, estructura, capas, espesor, corona, flexo, laminacion, sellado, H)
        if (automatico) H += puntosEmpaqueAutomatico(prodKey, estructura, capas, espesor, sellado, H)
        if (zipper) H += puntosZipper(form, estructura, capas, espesor, corona, H)

        if (P.antifog && capas.none { it.antifog }) {
            H += hz("baja", "producto", "Sin antiempañante",
                "El producto se empaca tibio: la condensación empaña la bolsa y afecta la vida útil.",
                "Evaluar MB antifog en la capa interna.")
        }
        if (P.alimentario) {
            for (c in capas) {
                if (c.recPct <= 0) continue
                if (c.recTipo.startsWith("Externo")) {
                    H += hz("alta", "producto", "${c.nombre}: recuperado externo en producto alimentario",
                        "No hay trazabilidad del origen: riesgo de contaminantes y olor.",
                        "Eliminar. Sólo recuperado propio limpio y trazable.", c.nombre)
                }
                val esContacto = estructura == "Monocapa" || c.nombre == contacto
                val lim = if (estructura == "Monocapa") P.recMono else if (c.nombre == contacto) P.recContacto else P.recOtra
                if (c.recPct > lim) {
                    H += hz("alta", "producto", "${c.nombre}: recuperado ${fm(c.recPct, 0)}% (máx. sugerido ${gn(lim)}%)",
                        if (esContacto) "Producto en contacto con alimentos: el recuperado debe ir en capas sin contacto y limitado."
                        else "Nivel de recuperado alto para un empaque alimentario, aun en capa sin contacto.",
                        if (esContacto) "Capa en contacto 100% virgen apta para alimentos; llevar el recuperado a la capa central."
                        else "Reducir a ≤ ${gn(lim)}% con recuperado propio, limpio y trazable.",
                        c.nombre)
                }
            }
        } else {
            for (c in capas) {
                val lim = if (estructura == "Monocapa") P.recMono else P.recOtra
                if (c.recPct > lim) {
                    H += hz("media", "producto", "${c.nombre}: recuperado ${fm(c.recPct, 0)}% sobre lo usual (${gn(lim)}%)",
                        "Riesgo de no cumplir propiedades mecánicas.", "Validar con pruebas de impacto/tracción.", c.nombre)
                }
            }
        }
        if (prodKey == "pollo") {
            if (G.fTenaz < 0.3) {
                H += hz("media", "producto", "Baja tenacidad para congelación",
                    "Sólo ${pc(G.fTenaz)}% de C6/C8/metaloceno: el C4 y el LDPE pierden impacto a -18 °C.",
                    "Usar ≥ 40-60% de LLDPE C6/C8 o mLLDPE en la estructura.")
            }
            if (G.fLdpe > 0.6) {
                H += hz("media", "producto", "Exceso de LDPE para pollo",
                    "Baja resistencia a perforación por huesos.", "Sustituir parte del LDPE por mLLDPE/C6.")
            }
        }
        if (prodKey == "sacos") {
            if (G.fLdpe > 0.5) {
                H += hz("media", "producto", "Mucho LDPE para saco industrial",
                    "Bajo impacto y mayor fluencia (creep) con el saco estibado.",
                    "Usar base de LLDPE C6/mLLDPE con 10-25% de HDPE/LDPE para rigidez y burbuja.")
            }
            if (espesor < 80) {
                H += hz("media", "producto", "Espesor bajo para saco industrial",
                    "Riesgo de rotura en llenado y caída.", "Revisar especificación (típico 100-200 µm).")
            }
            if (form.exterior && capas.none { it.uv }) {
                H += hz("media", "producto", "Saco a la intemperie sin UV",
                    "El PE se degrada al sol en semanas.", "Agregar MB UV (HALS).")
            }
        }
        if (prodKey == "pan" && capas[0].fLin > 0.6 && (capas[0].frac["LLDPE_C4"] ?: 0.0) > 0.4) {
            H += hz("baja", "producto", "Claridad reducida en la cara externa",
                "El LLDPE C4 aporta más turbidez (haze) que el LDPE de claridad.",
                "Cara externa con mayor proporción de LDPE de claridad o mLLDPE.")
        }
        val sinFicha = capas.flatMap { it.noVerificados }.toSortedSet()
        if (sinFicha.isNotEmpty()) {
            H += hz("info", "formulacion", "Grados sin ficha técnica confirmada",
                "Se usaron valores típicos para: ${sinFicha.joinToString(", ")}.",
                "Confirmar MFI, densidad y aditivos con la TDS (pestaña Catálogo → Leer ficha PDF).")
        }

        // ---- indicadores ----
        val activos = listOf(
            "automatico" to automatico, "flexografia" to flexo, "laminacion" to laminacion,
            "sellado" to sellado, "zipper" to zipper,
        ).filter { it.second }.map { it.first }
        val ind = LinkedHashMap<String, Indicador>()
        for (cat2 in CATEGORIAS + activos) {
            val pts = min(100, H.filter { cat2 in it.afecta }.sumOf { PESO[it.severidad] ?: 0 })
            ind[cat2] = Indicador(pts, if (pts < 25) "Estable" else if (pts < 50) "Vigilar" else "Riesgo alto")
        }

        val orden = mapOf("alta" to 0, "media" to 1, "baja" to 2, "info" to 3)
        return Resultado(
            hallazgos = H.sortedBy { orden[it.severidad] ?: 3 },
            producto = P.nombre, prodKey = prodKey, estructura = estructura, espesor = espesor,
            requisitos = P.requisitos, corona = corona, postprocesos = activos,
            sit = sitCaraSello(capas, estructura, espesor), tipoMezcla = mezcla, zipper = zipper,
            burEval = bur?.let { evaluarBur(it, prodKey, mezcla) },
            global = G, geo = geo, capas = capas, indicadores = ind,
            ventana = ventanaProceso(capas, G, geo, prodKey, mezcla),
            calidad = planCalidad(prodKey, capas, G, form, corona, flexo, laminacion),
        )
    }

    // ------------------------------------------------------------------------
    // Caras y sellado
    // ------------------------------------------------------------------------
    /** (tratada, sello, opuesta) */
    private fun carasPelicula(capas: List<CapaCalc>, estructura: String, lado: String): Triple<CapaCalc, CapaCalc, CapaCalc> {
        if (estructura == "Monocapa") return Triple(capas[0], capas[0], capas[0])
        val ext = capas.first()
        val intn = capas.last()
        val tratada = if (lado == "Externa") ext else intn
        val sello = if (tratada === ext) intn else ext
        return Triple(tratada, sello, sello)
    }

    fun sitCaraSello(capas: List<CapaCalc>, estructura: String, espesor: Double): Sit? {
        val capa = if (estructura == "Monocapa") capas[0] else capas.last()
        val pol = capa.frac.filterKeys { it in SIT_FAMILIA }
        val tot = pol.values.sum()
        if (tot <= 0) return null
        val sit = pol.entries.sumOf { SIT_FAMILIA.getValue(it.key) * it.value } / tot
        val extra = if (espesor > 80) 10 else if (espesor > 50) 5 else 0
        return Sit(capa.nombre, sit.roundToInt(), (sit + 15 + extra).roundToInt(), (sit + 45 + extra).roundToInt())
    }

    private fun riesgoEspesor(H: List<Hallazgo>) =
        H.filter { "espesor" in it.afecta || "planitud" in it.afecta }.sumOf { PESO[it.severidad] ?: 0 }

    private fun puntosPostproceso(
        form: Formulacion, prodKey: String, estructura: String, capas: List<CapaCalc>, espesor: Double,
        corona: Boolean, flexo: Boolean, laminacion: Boolean, sellado: Boolean, previos: List<Hallazgo>,
    ): List<Hallazgo> {
        val H = mutableListOf<Hallazgo>()
        val (tratada, _, opuesta) = carasPelicula(capas, estructura, form.ladoCorona)
        val lado = form.ladoCorona
        val riesgo = riesgoEspesor(previos)
        val hayRec = capas.any { it.recPct > 0 }

        // ---------------- FLEXOGRAFÍA ----------------
        if (flexo) {
            if (!corona) {
                H += hz("alta", "flexografia", "Se imprime sin tratamiento corona",
                    "El PE tiene ~31 dyn/cm: la tinta no ancla y se desprende con la prueba de cinta.",
                    "Activar corona en la cara a imprimir: ≥ 38 dyn/cm (tintas solvente) y ≥ 40-42 dyn/cm (tintas base agua).")
            } else {
                if (tratada.slipSuperficie > 600) {
                    H += hz("alta", "flexografia", "Slip ${fm(tratada.slipSuperficie, 0)} ppm en la cara a imprimir",
                        "La erucamida aflora con los días, baja las dinas y la tinta pierde adherencia.",
                        "Cara de impresión ≤ 400-600 ppm de slip; imprimir lo antes posible tras extruir.", tratada.nombre)
                } else if (tratada.slipSuperficie > 300) {
                    H += hz("media", "flexografia", "Slip moderado (${fm(tratada.slipSuperficie, 0)} ppm) en la cara impresa",
                        "El nivel de dinas cae con el tiempo de almacenamiento.",
                        "Medir dinas antes de montar en la impresora; no almacenar más de 1-2 semanas.", tratada.nombre)
                }
                H += hz("info", "flexografia", "Tratar sólo la cara ${lado.lowercase()}",
                    "El sobretratamiento o tratar el reverso provoca bloqueo en bobina, repinte (set-off) y que la tinta " +
                        "pase a la otra cara.",
                    "Electrodo ajustado al ancho, ≤ 50-52 dyn/cm, sin descargas en el reverso (prueba de dinas en ambas caras).")
            }
            if (riesgo >= 25) {
                H += hz("media", "flexografia", "Variación de espesor/planitud afecta el registro",
                    "Bandas gruesas/delgadas se estiran distinto con la tensión de la impresora: pérdida de registro y arrugas.",
                    "Corregir el perfil de espesor antes de liberar a impresión.")
            }
            if (espesor < 20) {
                H += hz("media", "flexografia", "Película delgada (${fm(espesor, 0)} µm) para imprimir",
                    "Se elonga con la tensión de la máquina: fuera de registro y arrugas.",
                    "Tensiones bajas en la impresora y secado controlado (temperatura del túnel).")
            }
            if (hayRec) {
                H += hz("media", "flexografia", "Recuperado: geles generan fallas de impresión",
                    "Cada gel levanta el sustrato: puntos sin tinta (picado) en sólidos y tramas.",
                    "Conteo de geles por bobina antes de liberar; mallas finas en el cambiamallas.")
            }
            if (tratada.ab > 3000) {
                H += hz("baja", "flexografia", "Antibloqueo alto en cara impresa (${fm(tratada.ab, 0)} ppm)",
                    "Las partículas de sílice dan aspereza: tintas sólidas con poros y menos brillo.",
                    "AB ≤ 2000-2500 ppm en la cara impresa.", tratada.nombre)
            }
            if (tratada.pigmentoPct > 0 && tratada.colTipo.startsWith("Blanco")) {
                H += hz("info", "flexografia", "Fondo blanco en la cara impresa",
                    "El TiO2 en la piel impresa mejora el contraste pero desgasta clisés y anilox con el tiempo.",
                    "Preferir el blanco en la capa central (tricapa).")
            }
        }

        // ---------------- LAMINACIÓN ----------------
        if (laminacion) {
            if (!corona) {
                H += hz("alta", "laminacion", "Laminación sin tratamiento corona",
                    "El adhesivo no ancla sobre PE sin tratar: delaminación.",
                    "Corona en la cara a laminar ≥ 40-42 dyn/cm al momento de laminar.")
            } else {
                if (tratada.slipSuperficie > 200) {
                    H += hz("alta", "laminacion", "Slip ${fm(tratada.slipSuperficie, 0)} ppm en la cara a laminar",
                        "La erucamida forma una capa en la interfase: baja la fuerza de adhesión y puede migrar después del curado.",
                        "Cara a laminar sin slip (≤ 200 ppm).")
                }
                if (opuesta !== tratada && opuesta.slipSuperficie > 800) {
                    H += hz("media", "laminacion", "Slip de la cara opuesta se transfiere en la bobina",
                        "La cara de sello tiene ${fm(opuesta.slipSuperficie, 0)} ppm: al estar enrollada en contacto con la " +
                            "cara tratada, el slip pasa por contacto y baja las dinas.",
                        "Laminar pronto; usar slip de migración lenta (behenamida/estearamida) o bajar el nivel.")
                }
            }
            if (riesgo >= 15) {
                H += hz(if (riesgo >= 40) "alta" else "media", "laminacion", "Perfil de espesor/planitud crítico para laminar",
                    "Bandas y bolsas flojas generan arrugas, túneles y burbujas de aire en el laminado.",
                    "Tolerancia ±5% en perfil; bobinado con tensión decreciente; dado/colapsador oscilante.")
            }
            if (hayRec) {
                H += hz("media", "laminacion", "Geles visibles después de laminar",
                    "En laminados transparentes cada gel se ve como un punto o 'ojo de pescado'.",
                    "Recuperado ≤ 10% y sólo en capa central; inspección de geles por bobina.")
            }
            H += hz("info", "laminacion", "Medir COF y adhesión después del curado",
                "El curado (24-72 h) cambia la migración de slip y el COF final del laminado.",
                "Liberar el laminado con COF y adhesión (ASTM F904) medidos post-curado.")
        }

        // ---------------- SELLADO ----------------
        if (sellado) {
            val sit = sitCaraSello(capas, estructura, espesor)
            val sc = if (estructura == "Monocapa") capas[0] else capas.last()
            if (sit != null) {
                H += hz("info", "sellado", "Ventana de sellado orientativa (${sc.nombre})",
                    "Inicio de sellado estimado ≈ ${sit.sit} °C según la composición de la cara de sello.",
                    "Barra/mordaza ${sit.barraMin}-${sit.barraMax} °C como punto de partida; confirmar con la curva " +
                        "temperatura-vs-fuerza de sello.")
            }
            if (corona && estructura != "Monocapa" && tratada === sc) {
                H += hz("alta", "sellado", "La cara tratada es la cara de sello",
                    "El tratamiento corona oxida la superficie: sube la temperatura de sellado y debilita el sello.",
                    "Tratar la cara externa y sellar por la interna.")
            }
            if (estructura == "Monocapa" && corona) {
                H += hz("info", "sellado", "Monocapa tratada: sello interno sin tratar",
                    "Si la bolsa sella cara tratada contra cara sin tratar (traslapes), el sello es más débil.",
                    "Verificar el tipo de sello en la selladora (fondo, lateral o traslape).")
            }
            if (sc.fHdpe > 0.4) {
                H += hz("media", "sellado", "Cara de sello con HDPE",
                    "Mayor temperatura de sellado y ventana estrecha: riesgo de sellos fríos o quemados.",
                    "Agregar LLDPE/mLLDPE a la cara de sello o subir tiempo de contacto.")
            }
            if (sc.fLdpe > 0.7 && (prodKey == "pollo" || prodKey == "sacos")) {
                H += hz("media", "sellado", "Bajo 'hot tack' en la cara de sello",
                    "El LDPE tiene poca resistencia del sello en caliente: se abre al caer el producto pesado.",
                    "Incluir 30-50% de mLLDPE/C6/C8 en la cara de sello.")
            }
            if (sc.recPct > 0) {
                H += hz("media", "sellado", "Recuperado en la cara de sello (${fm(sc.recPct, 0)}%)",
                    "Geles y contaminación generan microfugas y sellos débiles.", "Cara de sello 100% virgen.", sc.nombre)
            }
            if (sc.slipSuperficie > 1500) {
                H += hz("baja", "sellado", "Slip alto en la cara de sello (${fm(sc.slipSuperficie, 0)} ppm)",
                    "El exceso de amida en la superficie reduce la fuerza de sello.", "Bajar slip en esa cara.", sc.nombre)
            }
            if (sc.pigmentoPct > 5) {
                H += hz("baja", "sellado", "Pigmento alto en la cara de sello",
                    "Las cargas reducen la fuerza del sello.", "Llevar el pigmento a otra capa.", sc.nombre)
            }
            if (espesor > 100) {
                H += hz("info", "sellado", "Película gruesa (${fm(espesor, 0)} µm)",
                    "El calor tarda en atravesar: requiere más tiempo de contacto o sellado por impulso.",
                    "Ajustar tiempo de contacto y enfriamiento de la barra; verificar sello con prueba de caída.")
            }
        }
        return H
    }

    private fun puntosEmpaqueAutomatico(
        prodKey: String, estructura: String, capas: List<CapaCalc>, espesor: Double, sellado: Boolean,
        previos: List<Hallazgo>,
    ): List<Hallazgo> {
        val H = mutableListOf<Hallazgo>()
        val riesgo = riesgoEspesor(previos)
        val caras = if (estructura == "Monocapa") listOf("Ambas caras" to capas[0])
        else listOf("Cara externa" to capas.first(), "Cara interna" to capas.last())
        if (prodKey != "sacos") {
            for ((nom, c) in caras) {
                if (c.slipSuperficie < 400) {
                    H += hz("alta", "automatico", "$nom: COF alto para máquina automática",
                        "Slip efectivo ${fm(c.slipSuperficie, 0)} ppm: la película frena en formadores y guías, la bolsa " +
                            "no abre ni avanza.",
                        "Slip 600-1200 ppm en las caras que rozan la máquina; objetivo COF 0.15-0.25.", c.nombre)
                }
                if (c.pigmentoPct > 0 && COLORANTES[c.colTipo]?.absorbeSlip == true) {
                    H += hz("media", "automatico", "$nom: pigmento consume slip",
                        "El COF real queda más alto que el calculado y cambia entre lotes.",
                        "Medir COF a 24 h y 7 días y compensar el slip.", c.nombre)
                }
            }
        }
        if (prodKey == "pan" || prodKey == "tortillas" || prodKey == "generales") {
            for ((nom, c) in caras) {
                if (c.ab < 1000) {
                    H += hz("media", "automatico", "$nom: antibloqueo bajo (${fm(c.ab, 0)} ppm)",
                        "Las caras se pegan: la abridora por aire no separa la bolsa.",
                        "Antibloqueo 1500-3000 ppm en caras internas.", c.nombre)
                }
            }
        }
        if (riesgo >= 25) {
            H += hz("alta", "automatico", "Variación de espesor / planitud",
                "La película se desvía (mal 'tracking'), hace arrugas en el formador y descalibra el corte.",
                "Perfil ≤ ±5%, bobinas sin bandas flojas; ajustar perfil antes de liberar.")
        }
        if (espesor < 20 && prodKey != "sacos") {
            H += hz("media", "automatico", "Película delgada (${fm(espesor, 0)} µm) para línea automática",
                "Poca rigidez: se arruga en formadores y se estira con la tensión de la máquina.",
                "Subir espesor o aumentar rigidez (más densidad: LLDPE/MDPE en la central).")
        }
        H += hz("info", "automatico", "Electricidad estática",
            "El PE se carga al desenrollar: la bolsa se pega a guías y atrae polvo.",
            "Barras ionizadoras en la línea o MB antiestático; medir carga en la bobina.")
        H += hz("info", "automatico", "Calidad de la bobina",
            "Empalmes, telescopiado y diámetros fuera de OT provocan paros en la máquina del cliente.",
            "Bobinado con tensión controlada, empalmes marcados y diámetro/núcleo según OT.")
        if (sellado || prodKey == "pollo" || prodKey == "sacos") {
            val sc = if (estructura != "Monocapa") capas.last() else capas[0]
            if (sc.fTenaz < 0.3) {
                H += hz("media", "automatico", "Sello en caliente débil para alta velocidad",
                    "En líneas automáticas el producto cae sobre el sello aún caliente.",
                    "≥ 30-50% de mLLDPE/C6/C8 en la cara de sello (hot tack).", sc.nombre)
            }
        }
        return H
    }

    private fun puntosZipper(
        form: Formulacion, estructura: String, capas: List<CapaCalc>, espesor: Double, corona: Boolean,
        previos: List<Hallazgo>,
    ): List<Hallazgo> {
        val H = mutableListOf<Hallazgo>()
        val (tratada, _, _) = carasPelicula(capas, estructura, form.ladoCorona)
        val zc = if (estructura == "Monocapa") capas[0] else capas.last()
        val riesgo = riesgoEspesor(previos)
        if (corona && estructura != "Monocapa" && tratada === zc) {
            H += hz("alta", "zipper", "Zipper sobre cara tratada",
                "La cara tratada sella mal con el perfil del zipper: se desprende al abrir la bolsa.",
                "Tratar la cara externa; sellar el zipper sobre la cara interna sin tratar.", zc.nombre)
        }
        if (zc.fHdpe > 0.4) {
            H += hz("alta", "zipper", "Cara de sello con HDPE frente a zipper de LDPE/LLDPE",
                "La temperatura que necesita el HDPE deforma el perfil del zipper antes de lograr el sello.",
                "Cara de sello de LDPE/LLDPE/mLLDPE compatible con el zipper (confirmar material con el proveedor).")
        }
        if (zc.slipSuperficie > 800) {
            H += hz("media", "zipper", "Slip alto en la cara del zipper (${fm(zc.slipSuperficie, 0)} ppm)",
                "La amida en superficie debilita la unión zipper-película.",
                "≤ 600-800 ppm en la cara interna o slip sólo en la cara externa.", zc.nombre)
        }
        if (zc.recPct > 0) {
            H += hz("media", "zipper", "Recuperado en la cara del zipper",
                "Geles y contaminación generan puntos sin sellar en la unión del zipper.",
                "Cara interna 100% virgen.", zc.nombre)
        }
        if (espesor < 40) {
            H += hz("media", "zipper", "Película delgada (${fm(espesor, 0)} µm) para zipper",
                "La película se adelgaza, arruga o quema en la unión con el perfil, que es mucho más grueso.",
                "Típicamente ≥ 40-50 µm; barra de zipper con temperatura menor y teflón en buen estado.")
        }
        if (riesgo >= 25) {
            H += hz("media", "zipper", "Variación de espesor / planitud",
                "El zipper se monta desalineado y la bolsa cierra torcida.", "Perfil de espesor ≤ ±5% y bobinas planas.")
        }
        H += hz("info", "zipper", "Sellado del zipper",
            "El zipper necesita su propia temperatura y tiempo (menor que el sello lateral) y enfriamiento para no " +
                "aplastar el perfil; los cruces con el sello lateral son el punto débil típico.",
            "Barras dedicadas de zipper, enfriamiento después del sello y prueba de hermeticidad en los cruces.")
        return H
    }

    // ------------------------------------------------------------------------
    // Ventana de proceso
    // ------------------------------------------------------------------------
    private fun ventanaProceso(capas: List<CapaCalc>, G: Global, geo: Geometria, prodKey: String, mezcla: String?): Ventana {
        val ext = capas.map { c ->
            val (a, b1, b2, d1, d2) = c.perfilTemp
            Extrusor(c.nombre, "${a - 5}-${a + 5} °C", "$b1-$b2 °C (rampa ascendente)", "$d1-$d2 °C", "$d1-${d2 + 5} °C")
        }
        val (f1, f2, tipo) = when {
            G.fHdpe > 0.4 || mezcla == "alta" -> Triple(6, 10, "tallo alto (HDPE)")
            G.fLin > 0.5 -> Triple(3, 5, "media (predominio lineal)")
            else -> Triple(2, 4, "baja-media (predominio LDPE)")
        }
        var linea = "$f1-$f2 × diámetro de dado"
        geo.dadoMm?.let { linea += " (${fm(f1 * it, 0)}-${fm(f2 * it, 0)} mm)" }
        linea += " — $tipo"
        val (ideal, _) = rangoBur(prodKey, mezcla)
        return Ventana(
            extrusores = ext,
            lineaEnfriamiento = linea,
            burSugerido = "${gn(ideal.first)}-${gn(ideal.second)}",
            gapSugerido = if (G.fLin + G.fHdpe > 0.5) "1.8-2.5 mm (lineales)" else "0.8-1.5 mm (LDPE)",
            variables = listOf(
                "Presión de masa en cada extrusor (alarma si varía > ±5% del valor estable)",
                "Temperatura de masa real (termopar de inmersión) vs. ventana",
                "Amperaje/carga del motor (sube con lineales de bajo MFI)",
                "Altura de la línea de enfriamiento y estabilidad visual de la burbuja",
                "Temperatura y caudal del aire del aro de enfriamiento",
                "Perfil transversal de espesor y ancho (lay-flat)",
                "Velocidad de halado y relación con las RPM (consistencia de producción kg/h)",
                "Diferencial de presión en cambiamallas (indica colmatación por geles/recuperado)",
            ),
        )
    }

    // ------------------------------------------------------------------------
    // Plan de Calidad: Óptimo (liberar) / Crítico (alertar y ajustar) / Fuera de rango (retener)
    // ------------------------------------------------------------------------
    private fun rango(nominal: Double, p: Int) = "${fm(nominal * (1 - p / 100.0), 1)} – ${fm(nominal * (1 + p / 100.0), 1)}"

    private fun planCalidad(
        prodKey: String, capas: List<CapaCalc>, G: Global, form: Formulacion, corona: Boolean, impreso: Boolean,
        laminacion: Boolean,
    ): List<FilaCalidad> {
        val auto = form.empaqueAutomatico
        val sellado = form.sellado
        val zipper = form.zipper
        val esp = num(form.espesorUm, 30.0)
        val anchoCm = num(form.anchoOtCm)
        val F = mutableListOf<FilaCalidad>()
        var grupo = "extrusion"
        fun fila(prueba: String, frecuencia: String, optimo: String, critico: String, fuera: String) {
            F += FilaCalidad(prueba, frecuencia, optimo, critico, fuera, grupo)
        }

        // --- Espesor ---
        val (o, c) = if (prodKey == "laminados" || laminacion) 3 to 5 else if (auto) 5 to 8 else 5 to 10
        fila("Espesor promedio (nominal ${fm(esp, 0)} µm)", "Inicio, cada bobina o cada 2 h",
            "±$o% (${rango(esp, o)} µm)", "±$o–$c%",
            "> ±$c% (< ${fm(esp * (1 - c / 100.0), 1)} o > ${fm(esp * (1 + c / 100.0), 1)} µm)")
        fila("Variación del perfil transversal (máx – mín)", "Cada bobina", "≤ ${o * 2}% del nominal",
            "${o * 2}–${c * 2}%", "> ${c * 2}% (bandas)")
        // --- Ancho ---
        val (to, tc) = if (auto) 0.2 to 0.3 else 0.3 to 0.5
        if (anchoCm > 0) {
            fila("Ancho de la OT (nominal ${gn(anchoCm)} cm)", "Cada bobina",
                "±${gn(to)} cm (${gn(anchoCm - to)} – ${gn(anchoCm + to)} cm)", "±${gn(to)}–${gn(tc)} cm", "> ±${gn(tc)} cm")
        } else {
            fila("Ancho de la OT", "Cada bobina", "±${gn(to)} cm", "±${gn(to)}–${gn(tc)} cm", "> ±${gn(tc)} cm")
        }
        // --- Apariencia ---
        fila("Geles / ojos de pescado (inspección visual)", "Cada bobina", "Sin geles > 1 mm",
            "Geles aislados ≤ 1 mm (≤ 5 por m²)", "Geles > 1 mm o > 5 por m²; puntos negros")
        fila("Rayas, vetas de color, piel de tiburón", "Cada bobina", "Ausentes", "Leves, sólo a contraluz",
            "Visibles a simple vista")
        // --- Mecánicas ---
        fila("Tracción y elongación MD/TD (ASTM D882)", "Por lote", "≥ 110% de la especificación",
            "100–110% de la especificación", "< especificación")
        fila("Rasgado Elmendorf MD/TD (ASTM D1922)", "Por lote", "MD/TD entre 0.5 y 2", "MD/TD 0.3–0.5 o 2–3",
            "MD/TD < 0.3 o > 3 (se raja en una dirección)")
        fila("Impacto dardo (ASTM D1709)", "Por lote", "≥ 110% de la especificación", "100–110%", "< especificación")
        // --- COF ---
        if (prodKey == "sacos") {
            fila("COF estático película/película (ASTM D1894)", "Por lote", "≥ 0.40 (no desliza en estiba)",
                "0.30–0.40", "< 0.30")
        } else if (auto) {
            fila("COF dinámico película/película (ASTM D1894)", "Por lote, medido a 24 h y 7 días", "0.15–0.25",
                "0.10–0.15 o 0.25–0.30", "< 0.10 o > 0.30")
        } else {
            fila("COF dinámico película/película (ASTM D1894)", "Por lote, medido a 24 h", "0.15–0.30",
                "0.10–0.15 o 0.30–0.40", "< 0.10 o > 0.40")
        }
        // --- Planitud ---
        fila("Planitud / curl (muestra sobre mesa, 24 h)", if (laminacion || auto) "Cada bobina" else "Por lote",
            "Plana, sin bandas flojas", "Curl en bordes < 10 mm o bandas flojas leves",
            "Se enrolla o bandas flojas visibles en el rodillo")
        // --- Tratamiento ---
        if (corona) {
            if (laminacion) {
                fila("Tratamiento corona, cara a laminar (tintas ASTM D2578)", "Cada bobina y antes de laminar",
                    "42–48 dyn/cm", "40–41 dyn/cm", "< 40 dyn/cm")
            }
            if (impreso || !laminacion) {
                fila("Tratamiento corona, cara a imprimir (tintas ASTM D2578)", "Cada bobina y antes de imprimir",
                    "40–48 dyn/cm", "38–39 dyn/cm o 49–52 (sobretratado)", "< 38 o > 52 dyn/cm")
            }
            fila("Tratamiento en el reverso (no debe estar tratado)", "Inicio de turno", "≤ 34 dyn/cm",
                "35–36 dyn/cm", "≥ 37 dyn/cm (bloqueo / repinte)")
        }
        if (impreso) {
            fila("Adherencia de tinta (prueba de cinta, ASTM F2252)", "Inicio de cada tiraje", "0% desprendimiento",
                "< 5% (puntos aislados)", "≥ 5% desprendimiento")
        }
        if (laminacion) {
            fila("Fuerza de adhesión del laminado (ASTM F904), tras curado", "Por lote",
                "≥ especificación con rotura de película", "≥ especificación con separación limpia", "< especificación")
        }
        if (!sellado) {
            fila("Resistencia del sello", "Por lote / cambio de condición", "≥ 120% de la especificación",
                "100–120%", "< especificación")
        }
        // --- Color ---
        if (capas.any { COLORANTES[it.colTipo] != null && it.colPct > 0 }) {
            fila("Color ΔE vs. patrón (espectrofotómetro)", "Inicio y cada bobina", "ΔE ≤ 1.0", "ΔE 1.0–2.0", "ΔE > 2.0")
            fila("Opacidad vs. patrón", "Por lote", "± 1 punto", "± 1–2 puntos", "> ± 2 puntos")
        }
        // --- Recuperado ---
        if (G.recPct > 0) {
            fila("MFI del recuperado vs. referencia (ASTM D1238)", "Cada lote de recuperado", "± 10%", "± 10–20%",
                "> ± 20% (no usar)")
        }
        // --- Empaque automático ---
        if (auto) {
            fila("Empalmes por bobina", "Cada bobina", "0", "1 (marcado)", "> 1 o empalme sin marcar")
            fila("Bobinado: telescopiado / borde", "Cada bobina", "≤ 2 mm", "2–5 mm", "> 5 mm")
            fila("Diámetro exterior y núcleo de la bobina", "Cada bobina", "Según OT ± 2%", "± 2–5%", "> ± 5% o núcleo dañado")
            fila("Carga estática en la bobina (medidor de campo)", "Por lote", "< 5 kV", "5–10 kV", "> 10 kV")
            fila("Prueba en máquina del cliente / simulación (apertura y avance)", "Primer lote de cada OT",
                "Sin paros", "Paros ocasionales", "Atascos o desvío continuo")
        }
        // --- Específicas del producto ---
        if (prodKey == "pan" || prodKey == "tortillas") {
            fila("Haze / brillo vs. patrón (ASTM D1003 / D2457)", "Por lote", "≤ patrón", "Patrón +1 a +2 puntos de haze",
                "> patrón + 2 puntos")
        }
        if (PRODUCTOS.getValue(prodKey).alimentario) {
            fila("Olor y sabor (panel interno)", "Por lote", "Sin olor", "Olor leve (1 de 5 panelistas)",
                "Olor perceptible (≥ 2 de 5)")
        }
        if (prodKey == "tortillas" && capas.any { it.antifog }) {
            fila("Antiempañante (frasco con agua tibia, 30 min)", "Por lote", "Película transparente",
                "Gotas finas aisladas", "Película empañada")
        }
        if (prodKey == "pollo") {
            fila("Impacto dardo a -18 °C (muestra acondicionada)", "Por lote", "≥ 110% de la especificación",
                "100–110%", "< especificación")
            fila("Perforación (ASTM D5748 o interna)", "Por lote", "≥ 110% de la especificación", "100–110%",
                "< especificación")
        }
        if (prodKey == "sacos") {
            fila("Prueba de caída del saco lleno (ISO 7965 o interna)", "Por lote",
                "Sin rotura en todas las caídas especificadas", "Deformación del sello sin rotura", "Rotura o fuga")
        }

        // ================= EN MÁQUINAS DE SELLADO =================
        grupo = "sellado"
        fila("Apariencia del sello (visual)", "Inicio de turno y cada 30 min",
            "Liso, uniforme, transparente, ancho constante, sin burbujas ni arrugas",
            "Arrugas leves, blanqueado o variación de ancho ≤ 1 mm",
            "Quemado, perforado, sello frío (se abre a mano) o arrugas que fugan")
        fila("Fuerza de sello (ASTM F88, tira 15 o 25 mm)", "Inicio de turno y cada cambio de temperatura o velocidad",
            "≥ 120% de la especificación; rompe la película fuera del sello",
            "100–120%; se despega en el sello (peel)", "< especificación, fugas o sello quemado")
        sitCaraSello(capas, form.estructura, esp)?.let { s ->
            val ideal = ((s.barraMin + s.barraMax) / 2.0).roundToInt()
            fila("Temperatura ideal de sellado (barra) — cara ${s.capa}", "Inicio de turno y cada cambio de bobina",
                "${ideal - 5} – ${ideal + 5} °C (ideal ≈ $ideal °C)",
                "${ideal - 10} – ${ideal - 6} °C o ${ideal + 6} – ${ideal + 10} °C",
                "< ${ideal - 10} °C (sello frío) o > ${ideal + 10} °C (quemado, adelgazamiento)")
        }
        val dw = when {
            esp <= 30 -> 0.25 to 0.40
            esp <= 60 -> 0.40 to 0.70
            esp <= 100 -> 0.70 to 1.20
            else -> 1.20 to 2.00
        }
        val fz = if (zipper) 0.8 else 1.0
        val gLo = (60 * 0.4 / dw.second * fz).roundToInt()
        val gHi = (60 * 0.4 / dw.first * fz).roundToInt()
        fila("Velocidad de máquina (tiempo de contacto ${fm(dw.first, 2)}–${fm(dw.second, 2)} s${if (zipper) ", con zipper" else ""})",
            "Inicio de turno y cada ajuste", "$gLo – $gHi golpes/min", "${gHi + 1} – ${(gHi * 1.2).roundToInt()} golpes/min",
            "> ${(gHi * 1.2).roundToInt()} golpes/min (tiempo de contacto insuficiente: sello frío)")
        fila("Curva de sellado temperatura vs. fuerza", "Al aprobar la formulación / nuevo lote de resina",
            "Ventana ≥ 20 °C con fuerza ≥ especificación", "Ventana 10–20 °C", "Ventana < 10 °C")
        if (prodKey == "pollo" || prodKey == "sacos" || auto) {
            fila("Hot tack (ASTM F1921)", "Al aprobar la formulación", "≥ 120% de la especificación", "100–120%",
                "< especificación")
        }
        if (zipper) {
            fila("Zipper: unión zipper–película (pelado manual / ASTM F88)", "Inicio de turno y cada 30 min",
                "Rompe la película o el perfil antes de separarse", "Se separa con esfuerzo, sin dañar el perfil",
                "Se desprende fácil o deja canales")
            fila("Zipper: alineación respecto al borde / impresión", "Cada 30 min", "± 1 mm", "± 1–2 mm", "> ± 2 mm")
            fila("Zipper: fuerza de apertura y cierre", "Por lote", "Dentro de la especificación del proveedor",
                "± 10–20% fuera de especificación", "> 20% fuera o no cierra")
            fila("Zipper: hermeticidad (agua o aire) y cruces con el sello lateral", "Inicio de turno y por lote",
                "Sin fuga", "Goteo leve en el cruce", "Fuga o perfil aplastado")
        }
        return F
    }

    // ------------------------------------------------------------------------
    // Reporte en Markdown (para compartir y para la IA)
    // ------------------------------------------------------------------------
    private val ICONO = mapOf("alta" to "🔴", "media" to "🟠", "baja" to "🟡", "info" to "🔵")

    fun reporteMarkdown(R: Resultado): String {
        if (R.error != null) return "**Error:** ${R.error}"
        val geo = R.geo
        val L = mutableListOf<String>()
        val procesos = R.postprocesos.joinToString(", ") { NOMBRE_CAT[it] ?: it }.ifBlank { "ninguno" }
        L += "# Análisis de formulación — ${R.producto}"
        L += "**Máquina:** ${geo.maquina ?: "no indicada"} · **Estructura:** ${R.estructura} · " +
            "**Espesor:** ${fm(R.espesor, 0)} µm · **Tratamiento corona:** ${if (R.corona) "Sí" else "No"} · " +
            "**Procesos posteriores:** $procesos"
        L += "*Requisitos del producto:* ${R.requisitos}"
        L += ""
        L += "## Indicadores"
        L += "| Aspecto | Nivel | Puntaje de riesgo |"
        L += "|---|---|---|"
        R.indicadores.forEach { (k, v) -> L += "| ${NOMBRE_CAT[k]} | ${v.nivel} | ${v.puntos}/100 |" }

        if (geo.maquina != null && geo.bur != null) {
            val (nivel, texto) = R.burEval ?: ("" to "")
            val sem = mapOf("Estable" to "🟢", "Vigilar" to "🟡", "Riesgo alto" to "🔴")[nivel] ?: "⚪"
            L += ""
            L += "## Relación de soplado — ${geo.maquina}"
            L += "Cabezal Ø ${fm(geo.dadoMm ?: 0.0, 0)} mm · ancho plano ${gn((geo.layFlatMm ?: 0.0) / 10)} cm · " +
                "Ø burbuja ${fm((geo.dBurbujaMm ?: 0.0) / 10, 1)} cm · **BUR ${fm(geo.bur, 2)}** · " +
                "Mezcla: ${TIPOS_MEZCLA[R.tipoMezcla] ?: "no indicada"}"
            L += "$sem **$nivel** — $texto"
        }

        L += ""
        L += "## Propiedades calculadas por capa"
        L += "| Capa | % estructura | µm | MFI mezcla | Densidad | LDPE | Lineal | Recup. | Pigmento | Slip ppm | AB ppm | PPA |"
        L += "|---|---|---|---|---|---|---|---|---|---|---|---|"
        for (c in R.capas) {
            L += "| ${c.nombre} | ${pc(c.wEst)}% | ${fm(c.espesorUm, 1)} | ${fm(c.mfi, 2)} | ${fm(c.densidad, 3)} | " +
                "${pc(c.fLdpe)}% | ${pc(c.fLin)}% | ${fm(c.recPct, 0)}% | ${fm(c.pigmentoPct, 1)}% | " +
                "${fm(c.slip, 0)} | ${fm(c.ab, 0)} | ${if (c.ppa) "Sí" else "No"} |"
        }
        R.global?.let { G ->
            L += ""
            L += "**Global:** MFI ${fm(G.mfi, 2)} · densidad ${fm(G.densidad, 3)} g/cm³ · LDPE ${pc(G.fLdpe)}% · " +
                "lineales ${pc(G.fLin)}% · recuperado ${fm(G.recPct, 1)}%"
        }
        val partes = mutableListOf<String>()
        geo.anchoOtMm?.let {
            val pres = mapOf("tubo" to "tubo", "lamina1" to "lámina abierta 1 lado", "lamina2" to "2 láminas")[geo.presentacion] ?: "tubo"
            partes += "ancho OT ${gn(it / 10)} cm × ${geo.pistas} pista(s), $pres" +
                (geo.fuelleMm?.let { f -> " + fuelle ${gn(f / 10)} cm" } ?: "")
        }
        geo.ddr?.let { partes += "DDR ${fm(it, 0)}" }
        if (partes.isNotEmpty()) L += "**Geometría:** " + partes.joinToString(" · ")
        R.sit?.let { L += "**Sellado (orientativo):** inicio ≈ ${it.sit} °C en ${it.capa}; barra ${it.barraMin}-${it.barraMax} °C" }

        fun linea(h: Hallazgo): String {
            val cats = h.afecta.joinToString(", ") { NOMBRE_CAT[it] ?: it }
            return "- ${ICONO[h.severidad]} **${h.titulo}** _(${cats})_  \n  ${h.detalle}  \n  ➜ ${h.sugerencia}"
        }
        val post = R.hallazgos.filter { h -> h.afecta.any { it in POSTPROCESOS } }
        for (p in R.postprocesos) {
            val hs = post.filter { p in it.afecta }
            if (hs.isNotEmpty()) {
                L += ""
                L += "## Puntos críticos — ${NOMBRE_CAT[p]}"
                hs.forEach { L += linea(it) }
            }
        }
        L += ""
        L += "## Puntos críticos de extrusión y formulación"
        R.hallazgos.filter { it !in post }.forEach { L += linea(it) }

        R.ventana?.let { V ->
            L += ""
            L += "## Ventana de proceso orientativa"
            L += "| Extrusor / capa | Alimentación | Barril | Adaptador y dado | Masa |"
            L += "|---|---|---|---|---|"
            V.extrusores.forEach { L += "| ${it.capa} | ${it.alimentacion} | ${it.barril} | ${it.dado} | ${it.masa} |" }
            L += ""
            L += "- **Línea de enfriamiento:** ${V.lineaEnfriamiento}"
            L += "- **BUR sugerido:** ${V.burSugerido} · **Gap de dado:** ${V.gapSugerido}"
            L += ""
            L += "**Variables que debe seguir el operador:**"
            V.variables.forEach { L += "- $it" }
        }

        L += ""
        L += "## Plan de seguimiento de Calidad"
        L += "🟢 **Óptimo**: liberar · 🟡 **Crítico**: alertar, ajustar proceso y aumentar la frecuencia de medición · " +
            "🔴 **Fuera de rango**: retener la bobina / rechazar"
        L += ""
        L += "| Prueba | Frecuencia | 🟢 Óptimo | 🟡 Crítico | 🔴 Fuera de rango |"
        L += "|---|---|---|---|---|"
        R.calidad.filter { it.grupo != "sellado" }
            .forEach { L += "| ${it.prueba} | ${it.frecuencia} | ${it.optimo} | ${it.critico} | ${it.fuera} |" }
        val sel = R.calidad.filter { it.grupo == "sellado" }
        if (sel.isNotEmpty()) {
            L += ""
            L += "### En máquinas de sellado" + (if (R.zipper) " (con zipper)" else "")
            L += "| Control | Frecuencia | 🟢 Óptimo | 🟡 Crítico | 🔴 Fuera de rango |"
            L += "|---|---|---|---|---|"
            sel.forEach { L += "| ${it.prueba} | ${it.frecuencia} | ${it.optimo} | ${it.critico} | ${it.fuera} |" }
            L += ""
            L += "_Temperatura y velocidad son puntos de partida para selladora de barra caliente; ajustar con la curva de " +
                "sellado de cada máquina._"
        }
        L += ""
        L += "_Rangos y tolerancias orientativos de práctica de proceso. Ajustar a la especificación del cliente, fichas " +
            "técnicas y pruebas en línea._"
        return L.joinToString("\n")
    }
}
