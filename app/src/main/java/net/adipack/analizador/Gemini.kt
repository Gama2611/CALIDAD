package net.adipack.analizador

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ErrorIA(msg: String) : Exception(msg)

/**
 * Cliente mínimo de la API REST de Google Gemini (plan gratuito con API key).
 * Clave gratuita: https://aistudio.google.com/apikey
 * El plan gratuito NO incluye búsqueda en Google: para fichas técnicas se usa la lectura del PDF.
 */
object Gemini {
    const val MODELO_DEFECTO = "gemini-3.5-flash"

    private const val BASE = """Eres un ingeniero de procesos senior especialista en extrusión de película soplada de
polietileno (monocapa, bicapa y tricapa) para empaques flexibles en una planta latinoamericana. Respondes en español
técnico, directo y práctico, pensado para supervisores de producción y calidad. No copies párrafos de las fichas
técnicas; resume con tus palabras."""

    private const val SIN_WEB = BASE + """
- NO tienes acceso a internet en esta consulta. Trabaja con los datos de la formulación y del motor local.
- Para los grados marcados "NO verificado", NO afirmes valores numéricos de su ficha técnica como si fueran ciertos:
  indica qué datos de la TDS hay que confirmar y cómo cambiaría la conclusión si difieren.
- Tus conocimientos generales del grado puedes darlos sólo como referencia, marcados "(por confirmar con TDS)"."""

    suspend fun generar(apiKey: String, modelo: String, prompt: String, sistema: String, pdf: ByteArray? = null): String =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) throw ErrorIA("Configure la API key de Gemini en la pestaña Análisis IA.")
            val mod = modelo.trim().ifBlank { MODELO_DEFECTO }
            val partes = JSONArray()
            if (pdf != null) {
                partes.put(JSONObject().put("inlineData", JSONObject()
                    .put("mimeType", "application/pdf")
                    .put("data", Base64.encodeToString(pdf, Base64.NO_WRAP))))
            }
            partes.put(JSONObject().put("text", prompt))
            val cuerpo = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sistema))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", partes)))

            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$mod:generateContent")
            val con = url.openConnection() as HttpURLConnection
            try {
                con.requestMethod = "POST"
                con.connectTimeout = 30_000
                con.readTimeout = 240_000
                con.doOutput = true
                con.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                con.setRequestProperty("x-goog-api-key", apiKey.trim())
                con.outputStream.use { it.write(cuerpo.toString().toByteArray(Charsets.UTF_8)) }
                val codigo = con.responseCode
                val flujo = if (codigo in 200..299) con.inputStream else con.errorStream
                val respuesta = flujo?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (codigo !in 200..299) throw ErrorIA(mensajeError(codigo, respuesta, mod))
                extraerTexto(respuesta)
            } catch (e: ErrorIA) {
                throw e
            } catch (e: java.net.UnknownHostException) {
                throw ErrorIA("Sin conexión a internet (o la red bloquea generativelanguage.googleapis.com).")
            } catch (e: java.net.SocketTimeoutException) {
                throw ErrorIA("Gemini tardó demasiado en responder. Intente de nuevo.")
            } finally {
                con.disconnect()
            }
        }

    private fun extraerTexto(respuesta: String): String {
        val j = JSONObject(respuesta)
        val cands = j.optJSONArray("candidates")
        val sb = StringBuilder()
        if (cands != null && cands.length() > 0) {
            val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            if (parts != null) {
                for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text", ""))
            }
        }
        if (sb.isBlank()) throw ErrorIA("Gemini no devolvió texto (posible bloqueo de seguridad o respuesta vacía).")
        return sb.toString()
    }

    private fun mensajeError(codigo: Int, cuerpo: String, modelo: String): String = when {
        codigo == 429 || "RESOURCE_EXHAUSTED" in cuerpo ->
            "Se alcanzó el límite gratuito de Gemini por minuto/día. Espere un momento e intente de nuevo."
        "API_KEY_INVALID" in cuerpo || "API key not valid" in cuerpo ->
            "API key de Gemini inválida. Genere una en https://aistudio.google.com/apikey"
        codigo == 403 ->
            "Acceso denegado (403): verifique la API key y que la red permita generativelanguage.googleapis.com."
        codigo == 404 ->
            "El modelo '$modelo' no existe o no está disponible para su clave. Pruebe otro nombre de modelo."
        else -> "Error de Gemini ($codigo): " + cuerpo.take(300)
    }

    // ------------------------------------------------------------------------
    // Tareas
    // ------------------------------------------------------------------------
    private fun resumenFormulacion(form: Formulacion, cat: Map<String, Grado>): String =
        form.capas.joinToString("\n") { c ->
            val mats = mutableListOf<String>()
            for (r in c.resinas) {
                val g = r.id?.let { cat[it] } ?: continue
                val pct = num(r.pct)
                if (pct > 0) {
                    mats += "${gn(pct)}% ${g.marca} ${g.grado} (${g.familia}; valores usados: MFI ${gn(g.mfi)}, " +
                        "densidad ${gn(g.densidad)}, slip ${gn(g.slipPpm)} ppm, AB ${gn(g.abPpm)} ppm, " +
                        (if (g.verificado) "verificado con TDS" else "NO verificado") + ")"
                }
            }
            if (num(c.recuperado.pct) > 0) mats += "${c.recuperado.pct}% recuperado (${c.recuperado.tipo}, MFI ${c.recuperado.mfi})"
            if (num(c.colorante.pct) > 0 && COLORANTES[c.colorante.tipo] != null) {
                mats += "${c.colorante.pct}% masterbatch ${c.colorante.tipo} (MFI vehículo ${c.colorante.mfi})"
            }
            "- ${c.nombre} (${c.pct}% de la estructura): " + mats.joinToString("; ")
        }

    suspend fun analisisCompleto(form: Formulacion, cat: Map<String, Grado>, reporteLocal: String, apiKey: String, modelo: String): String {
        val P = PRODUCTOS[form.producto] ?: PRODUCTOS.getValue("generales")
        val m = form.maquina?.let { MAQUINAS[it] }
        val lf = layFlatOt(num(form.anchoOtCm) * 10, PRESENTACIONES[form.presentacion] ?: "tubo",
            maxOf(1, num(form.pistas, 1.0).toInt()), num(form.fuelleCm) * 10, num(form.refileCm) * 10)
        val r = if (m != null) calcularBur(lf, m.cabezalMm) else null
        val geometria = if (r != null && m != null)
            "Máquina: ${form.maquina} (cabezal Ø ${m.cabezalMm} mm) · ancho OT ${form.anchoOtCm} cm × ${form.pistas} pista(s), " +
                "${form.presentacion} · ancho plano ${gn(lf / 10)} cm · BUR ${fm(r.bur, 2)}"
        else "Máquina / BUR: no indicados"
        val procesos = listOf(
            "empaque automático" to form.empaqueAutomatico, "flexografía" to form.flexografia,
            "laminación" to form.laminacion, "sellado" to form.sellado,
        ).filter { it.second }.joinToString(", ") { it.first }.ifBlank { "ninguno" }

        val prompt = """Analiza esta formulación de película soplada.

Producto: ${P.nombre} — ${P.requisitos}
$geometria
Estructura: ${form.estructura} · Espesor total ${form.espesorUm} µm · Gap ${form.gapMm.ifBlank { "n/d" }} mm
Tratamiento corona: ${if (form.corona) "sí, cara " + form.ladoCorona else "no"}
Tipo de mezcla: ${TIPOS_MEZCLA[form.tipoMezcla] ?: "no indicado"} · Zipper: ${if (form.zipper) "sí" else "no"}
Procesos posteriores: $procesos

Formulación por capa:
${resumenFormulacion(form, cat)}

Resultado del motor de reglas local:
${reporteLocal.take(6000)}

Entrega en Markdown, con estas secciones:
1. **Datos a confirmar**: tabla de los grados NO verificados con los datos de la TDS que hay que confirmar y por qué importan para esta formulación.
2. **Correcciones o matices al análisis local**.
3. **Estabilidad de burbuja, espesor, planitud y sombras**: diagnóstico y causas raíz probables.
4. **Puntos críticos y límites del proceso**: temperaturas por extrusor, BUR en la máquina indicada, línea de enfriamiento, presión, velocidad.
4b. **Puntos críticos para los procesos posteriores indicados** (flexografía, laminación, sellado, zipper, empaque automático): dinas, slip en cada cara, registro, adhesión, ventana de sellado.
5. **Sugerencias de mejora de la formulación** con porcentajes concretos.
6. **Seguimiento de Calidad**: pruebas, frecuencia y tolerancias en tres niveles (óptimo, crítico, fuera de rango), cerrando con los controles en máquinas de sellado (apariencia y fuerza de sello, zipper si aplica, temperatura ideal y velocidad de máquina).
Sé concreto y breve en cada punto."""
        return generar(apiKey, modelo, prompt, SIN_WEB)
    }

    data class FichaLeida(
        val encontrada: Boolean, val marca: String?, val grado: String?, val familia: String?,
        val mfi: Double?, val densidad: Double?, val slipPpm: Double?, val abPpm: Double?, val ppa: Boolean,
        val proceso: String?, val nota: String,
    )

    suspend fun leerFichaPdf(pdf: ByteArray, nombreArchivo: String, apiKey: String, modelo: String): FichaLeida {
        val prompt = """El PDF adjunto ("$nombreArchivo") es la ficha técnica de una resina de polietileno.
Extrae SÓLO lo que figura en el documento. Si un dato no aparece, usa null; no lo deduzcas.
Si el slip/antibloqueo aparece como "sí/no" sin ppm, pon null y explícalo en "nota".
Responde SOLO con un bloque ```json``` con estas claves:
{"encontrada": true/false, "marca": "texto", "grado": "texto",
 "familia": uno de ["LDPE","LLDPE_C4","LLDPE_C6","LLDPE_C8","mLLDPE","MDPE","HDPE","EVA"],
 "mfi": número (g/10 min a 190 °C/2.16 kg; null si no figura), "densidad": número g/cm³ (null si no figura),
 "slip_ppm": número o null, "ab_ppm": número o null, "ppa": true/false, "proceso": "Soplado"|"Cast"|"Ambos",
 "temp_recomendada": "texto breve", "nota": "texto breve"}
"encontrada" = true si el documento es una ficha técnica de resina."""
        val texto = generar(apiKey, modelo, prompt, BASE, pdf)
        val o = extraerJson(texto) ?: return FichaLeida(false, null, null, null, null, null, null, null, false, null,
            "Respuesta no interpretable: " + texto.take(200))
        fun d(k: String): Double? = if (o.has(k) && !o.isNull(k)) o.optDouble(k).takeIf { !it.isNaN() } else null
        fun s(k: String): String? = if (o.has(k) && !o.isNull(k)) o.optString(k).takeIf { it.isNotBlank() } else null
        val nota = listOfNotNull(s("nota"), s("temp_recomendada")).joinToString(" ")
        return FichaLeida(
            encontrada = o.optBoolean("encontrada", false), marca = s("marca"), grado = s("grado"),
            familia = s("familia"), mfi = d("mfi"), densidad = d("densidad"), slipPpm = d("slip_ppm"),
            abPpm = d("ab_ppm"), ppa = o.optBoolean("ppa", false), proceso = s("proceso"), nota = nota,
        )
    }

    private fun extraerJson(t: String): JSONObject? {
        val m = Regex("```(?:json)?\\s*(\\{.*?\\})\\s*```", RegexOption.DOT_MATCHES_ALL).find(t)
            ?: Regex("(\\{.*\\})", RegexOption.DOT_MATCHES_ALL).find(t)
            ?: return null
        return runCatching { JSONObject(m.groupValues[1]) }.getOrNull()
    }
}
