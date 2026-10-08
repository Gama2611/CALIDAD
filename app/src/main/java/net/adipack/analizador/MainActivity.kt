@file:OptIn(ExperimentalMaterial3Api::class)

package net.adipack.analizador

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

// ============================================================================
// Paleta ADIPACK (blanco y rosado)
// ============================================================================
val Rosa = Color(0xFFE4007C)
val RosaClaro = Color(0xFFFCE4F1)
val RosaOscuro = Color(0xFFA80060)
val RosaBorde = Color(0xFFF3B6D6)
val Fondo = Color(0xFFFFF7FB)
val Gris = Color(0xFF5F6368)
val Verde = Color(0xFF2E7D32)
val Ambar = Color(0xFFF9A825)
val Rojo = Color(0xFFC62828)
val Naranja = Color(0xFFEF6C00)
val Azul = Color(0xFF1565C0)

fun colorNivel(n: String) = when (n) {
    "Estable" -> Verde
    "Vigilar" -> Ambar
    "Riesgo alto" -> Rojo
    else -> Gris
}

fun colorSev(s: String) = when (s) {
    "alta" -> Rojo
    "media" -> Naranja
    "baja" -> Ambar
    else -> Azul
}

// ============================================================================
// Estado de la aplicación
// ============================================================================
class AppState(val alm: Almacen) {
    var form by mutableStateOf(Formulacion())
    var cat by mutableStateOf(alm.catalogo())
    var resultado by mutableStateOf<Resultado?>(null)
    var reporte by mutableStateOf("")
    var textoIa by mutableStateOf("")
    var tab by mutableIntStateOf(0)
    var mensaje by mutableStateOf<String?>(null)
    var nombreFormula by mutableStateOf("")

    fun upd(f: (Formulacion) -> Formulacion) {
        form = f(form)
    }

    fun updCapa(i: Int, f: (Capa) -> Capa) {
        form = form.copy(capas = form.capas.mapIndexed { j, c -> if (j == i) f(c) else c })
    }

    fun cambiarEstructura(nueva: String) {
        val viejas = form.capas
        val nombres = ESTRUCTURAS.getValue(nueva)
        val pcts = PCT_DEFECTO.getValue(nueva)
        form = form.copy(estructura = nueva, capas = nombres.indices.map { i ->
            if (i < viejas.size) viejas[i].copy(nombre = nombres[i], pct = pcts[i]) else capaVacia(nombres[i], pcts[i])
        })
    }

    fun analizar(): Boolean {
        val r = try {
            Motor.analizar(form, cat)
        } catch (e: Exception) {
            mensaje = "Error en el análisis: ${e.message}"
            return false
        }
        resultado = r
        reporte = Motor.reporteMarkdown(r)
        if (r.error != null) {
            mensaje = r.error
            return false
        }
        return true
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val estado = AppState(Almacen(applicationContext))
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Rosa, onPrimary = Color.White, primaryContainer = RosaClaro,
                    onPrimaryContainer = RosaOscuro, secondary = RosaOscuro, secondaryContainer = RosaClaro,
                    background = Color.White, surface = Color.White,
                ),
            ) { App(estado) }
        }
    }
}

// ============================================================================
// Estructura principal
// ============================================================================
@Composable
fun App(st: AppState) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(st.mensaje) {
        st.mensaje?.let {
            snack.showSnackbar(it)
            st.mensaje = null
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ADIPACK", color = Rosa, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Text("Analizador de Formulaciones", color = RosaOscuro, fontSize = 15.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val items = listOf(
                    Triple("Formulación", Icons.Filled.Science, 0),
                    Triple("Resultados", Icons.Filled.Insights, 1),
                    Triple("Análisis IA", Icons.Filled.AutoAwesome, 2),
                    Triple("Catálogo", Icons.Filled.Inventory2, 3),
                )
                items.forEach { (t, ic, i) ->
                    NavigationBarItem(
                        selected = st.tab == i, onClick = { st.tab = i },
                        icon = { Icon(ic, contentDescription = t) }, label = { Text(t, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = RosaClaro, selectedIconColor = RosaOscuro),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Fondo)) {
            when (st.tab) {
                0 -> PantallaFormulacion(st)
                1 -> PantallaResultados(st)
                2 -> PantallaIA(st)
                else -> PantallaCatalogo(st)
            }
        }
    }
}

// ============================================================================
// Componentes reutilizables
// ============================================================================
@Composable
fun Seccion(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, RosaBorde, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(titulo, fontWeight = FontWeight.Bold, color = RosaOscuro, fontSize = 16.sp)
        contenido()
    }
}

@Composable
fun Campo(
    label: String, valor: String, modifier: Modifier = Modifier, sufijo: String? = null,
    numerico: Boolean = true, onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = valor, onValueChange = onChange, label = { Text(label, fontSize = 12.sp) }, singleLine = true,
        suffix = if (sufijo != null) ({ Text(sufijo) }) else null,
        keyboardOptions = if (numerico) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = modifier,
    )
}

/** Lista desplegable: opciones = (clave, texto visible) */
@Composable
fun Selector(
    label: String, valor: String?, opciones: List<Pair<String, String>>, modifier: Modifier = Modifier,
    onSel: (String) -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }
    val texto = opciones.firstOrNull { it.first == valor }?.second ?: ""
    Box(modifier) {
        OutlinedTextField(
            value = texto, onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text(label, fontSize = 12.sp) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { abierto = true })
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { (k, t) ->
                DropdownMenuItem(text = { Text(t) }, onClick = {
                    abierto = false
                    onSel(k)
                })
            }
        }
    }
}

@Composable
fun Opciones(titulo: String, valor: String?, opciones: List<Pair<String, String>>, onSel: (String) -> Unit) {
    Column {
        Text(titulo, fontWeight = FontWeight.SemiBold)
        opciones.forEach { (k, t) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onSel(k) }) {
                RadioButton(selected = valor == k, onClick = { onSel(k) })
                Text(t)
            }
        }
    }
}

@Composable
fun Casilla(texto: String, valor: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChange(!valor) }) {
        Checkbox(checked = valor, onCheckedChange = onChange)
        Text(texto)
    }
}

@Composable
fun BotonRosa(texto: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = Rosa, contentColor = Color.White)) {
        Text(texto)
    }
}

/** Renderizador simple de Markdown (títulos, viñetas, negritas, tablas). */
@Composable
fun TextoMd(md: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        md.lines().forEach { linea ->
            val t = linea.trimEnd()
            val esSeparador = t.startsWith("|") && t.replace("|", "").replace("-", "").replace(":", "").isBlank()
            when {
                t.startsWith("#") -> Text(negritas(t.trimStart('#').trim()), fontWeight = FontWeight.Bold,
                    color = RosaOscuro, fontSize = if (t.startsWith("###")) 15.sp else 17.sp,
                    modifier = Modifier.padding(top = 6.dp))
                esSeparador -> Spacer(Modifier.height(0.dp))
                t.startsWith("|") -> Text(negritas(t.trim('|').split("|").joinToString("  ·  ") { it.trim() }),
                    fontSize = 12.sp, modifier = Modifier.fillMaxWidth().background(RosaClaro.copy(alpha = 0.35f)).padding(4.dp))
                t.startsWith("- ") || t.startsWith("* ") -> Row {
                    Text("•  ", color = Rosa)
                    Text(negritas(t.drop(2)))
                }
                t.isBlank() -> Spacer(Modifier.height(4.dp))
                else -> Text(negritas(t.trim()))
            }
        }
    }
}

fun negritas(s: String): AnnotatedString = buildAnnotatedString {
    s.split("**").forEachIndexed { i, p ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(p) } else append(p)
    }
}

fun compartir(ctx: android.content.Context, asunto: String, texto: String) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, asunto)
        putExtra(Intent.EXTRA_TEXT, texto)
    }
    ctx.startActivity(Intent.createChooser(i, "Compartir reporte"))
}

// ============================================================================
// 1. FORMULACIÓN
// ============================================================================
@Composable
fun PantallaFormulacion(st: AppState) {
    val f = st.form
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {

        // ---------------- 1. Máquina y orden de trabajo ----------------
        Seccion("1. Máquina y orden de trabajo") {
            Selector("Máquina", f.maquina,
                MAQUINAS.map { (k, m) -> k to "$k  ·  cabezal ${m.cabezalMm} mm" }, Modifier.fillMaxWidth()) { v ->
                st.upd { it.copy(maquina = v) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo("Ancho de la OT", f.anchoOtCm, Modifier.weight(1f), "cm") { v -> st.upd { it.copy(anchoOtCm = v) } }
                Campo("Espesor", f.espesorUm, Modifier.weight(1f), "µm") { v -> st.upd { it.copy(espesorUm = v) } }
            }
            Selector("Presentación", f.presentacion, PRESENTACIONES.keys.map { it to it }, Modifier.fillMaxWidth()) { v ->
                st.upd { it.copy(presentacion = v) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo("Pistas", f.pistas, Modifier.weight(1f)) { v -> st.upd { it.copy(pistas = v) } }
                Campo("Fuelle c/lado", f.fuelleCm, Modifier.weight(1f), "cm") { v -> st.upd { it.copy(fuelleCm = v) } }
                Campo("Refile total", f.refileCm, Modifier.weight(1f), "cm") { v -> st.upd { it.copy(refileCm = v) } }
            }
            Opciones("Tipo de mezcla:", f.tipoMezcla, TIPOS_MEZCLA.map { it.key to it.value }) { v ->
                st.upd { it.copy(tipoMezcla = v) }
            }
            PanelBur(f)
        }

        // ---------------- 2. Tratamiento, procesos y empaque ----------------
        Seccion("2. Tratamiento, procesos posteriores y empaque") {
            Opciones("¿Lleva tratamiento corona?", if (f.corona) "si" else "no", listOf("si" to "Sí", "no" to "No")) { v ->
                st.upd { it.copy(corona = v == "si") }
            }
            if (f.corona && f.estructura != "Monocapa") {
                Selector("Cara tratada", f.ladoCorona, listOf("Externa" to "Externa", "Interna" to "Interna"),
                    Modifier.fillMaxWidth()) { v -> st.upd { it.copy(ladoCorona = v) } }
            }
            Text("La película pasará por:", color = Gris, fontSize = 13.sp)
            Casilla("Flexografía (impresión)", f.flexografia) { v -> st.upd { it.copy(flexografia = v) } }
            Casilla("Laminación", f.laminacion) { v -> st.upd { it.copy(laminacion = v) } }
            Casilla("Sellado", f.sellado) { v -> st.upd { it.copy(sellado = v) } }
            Text("Destino del producto:", color = Gris, fontSize = 13.sp)
            Casilla("Empaque automático", f.empaqueAutomatico) { v -> st.upd { it.copy(empaqueAutomatico = v) } }
            Opciones("¿Lleva zipper?", if (f.zipper) "si" else "no", listOf("si" to "Sí", "no" to "No")) { v ->
                st.upd { it.copy(zipper = v == "si") }
            }
        }

        // ---------------- 3. Producto y estructura ----------------
        Seccion("3. Producto y estructura") {
            Selector("Producto", f.producto, PRODUCTOS.map { it.key to it.value.nombre }, Modifier.fillMaxWidth()) { v ->
                st.upd { it.copy(producto = v, laminacion = it.laminacion || v == "laminados") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Selector("Estructura", f.estructura, ESTRUCTURAS.keys.map { it to it }, Modifier.weight(1f)) { v ->
                    st.cambiarEstructura(v)
                }
                Campo("Gap del dado", f.gapMm, Modifier.weight(1f), "mm") { v -> st.upd { it.copy(gapMm = v) } }
            }
            if (f.producto == "sacos") {
                Casilla("Uso a la intemperie", f.exterior) { v -> st.upd { it.copy(exterior = v) } }
            }
        }

        // ---------------- 4. Capas ----------------
        Seccion("4. Formulación por capa") {
            Text("* = grado sin ficha técnica confirmada (valores típicos). Corríjalo en Catálogo.",
                fontSize = 12.sp, color = Gris)
            f.capas.forEachIndexed { i, capa -> TarjetaCapa(st, i, capa, f.estructura != "Monocapa") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            BotonRosa("Analizar formulación", Modifier.weight(1f)) {
                if (st.analizar()) st.tab = 1
            }
            OutlinedButton(onClick = {
                st.form = Formulacion(producto = f.producto, estructura = f.estructura, capas = capasPara(f.estructura))
                st.nombreFormula = ""
            }) { Text("Nueva") }
        }

        // ---------------- Guardar / abrir ----------------
        Seccion("Guardar / abrir formulación") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Campo("Nombre de la formulación", st.nombreFormula, Modifier.weight(1f), numerico = false) {
                    st.nombreFormula = it
                }
                IconButton(onClick = {
                    val n = st.nombreFormula.trim()
                    if (n.isEmpty()) st.mensaje = "Escriba un nombre para la formulación."
                    else {
                        st.alm.guardarFormulacion(n, st.form)
                        st.mensaje = "Formulación '$n' guardada."
                    }
                }) { Icon(Icons.Filled.Save, contentDescription = "Guardar", tint = Rosa) }
            }
            val guardadas = remember(st.mensaje) { st.alm.formulaciones() }
            Selector("Abrir formulación guardada", null, guardadas.keys.sorted().map { it to it }, Modifier.fillMaxWidth()) { n ->
                guardadas[n]?.let {
                    st.form = it
                    st.nombreFormula = n
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun PanelBur(f: Formulacion) {
    val m = f.maquina?.let { MAQUINAS[it] }
    val lf = layFlatOt(num(f.anchoOtCm) * 10, PRESENTACIONES[f.presentacion] ?: "tubo",
        maxOf(1, num(f.pistas, 1.0).toInt()), num(f.fuelleCm) * 10, num(f.refileCm) * 10)
    val r = if (m != null) calcularBur(lf, m.cabezalMm) else null
    Column(
        Modifier.fillMaxWidth().background(RosaClaro, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Relación de soplado (BUR)", fontSize = 12.sp, color = Gris)
        if (m == null || r == null) {
            Text("—", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = RosaOscuro)
            Text("Seleccione máquina y ancho de la OT", color = Gris)
        } else {
            val (nivel, texto) = evaluarBur(r.bur, f.producto, f.tipoMezcla)
            val col = colorNivel(nivel)
            Text(fm(r.bur, 2), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = col)
            Text(if (nivel == "Sin evaluar") texto else "$nivel: $texto", color = col, fontWeight = FontWeight.SemiBold)
            Text("Cabezal Ø ${m.cabezalMm} mm · ancho plano ${gn(lf / 10)} cm · Ø burbuja ${fm(r.dBurbujaMm / 10, 1)} cm · " +
                "perímetro ${gn(r.perimetroMm / 10)} cm", fontSize = 12.sp, color = Gris)
            val nCapas = CAPAS_ESTRUCTURA[f.estructura] ?: 1
            val avisos = mutableListOf<String>()
            if (m.capasMax < nCapas) avisos += "⚠ Esta máquina no produce ${f.estructura.lowercase()}."
            if (nivel != "Estable" && nivel != "Sin evaluar") {
                val alt = burPorMaquina(lf, f.producto, nCapas, f.tipoMezcla).filter { it.nivel == "Estable" && it.admiteCapas }
                avisos += if (alt.isNotEmpty()) "Mejor en: " + alt.joinToString(", ") { "${it.maquina} (BUR ${fm(it.bur, 2)})" }
                else "Ninguna máquina deja este ancho en el rango ideal; revise pistas o presentación."
            }
            if (avisos.isNotEmpty()) Text(avisos.joinToString(" "), fontSize = 12.sp, color = RosaOscuro)
        }
    }
}

@Composable
fun TarjetaCapa(st: AppState, i: Int, capa: Capa, multicapa: Boolean) {
    val opciones = remember(st.cat) {
        st.cat.values.sortedWith(compareBy({ it.marca != "Genérico" }, { it.marca }, { it.grado }))
            .map { it.id to "${it.marca} · ${it.grado}${if (it.verificado) "" else " *"}" }
    }
    val suma = Motor.sumaCapa(capa)
    val ok = kotlin.math.abs(suma - 100) < 0.05
    Column(
        Modifier.fillMaxWidth().border(1.dp, RosaBorde, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(capa.nombre, color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Rosa, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 5.dp))
            Spacer(Modifier.weight(1f))
            if (multicapa) {
                Campo("% estructura", capa.pct, Modifier.width(130.dp)) { v -> st.updCapa(i) { it.copy(pct = v) } }
            }
        }
        capa.resinas.forEachIndexed { ri, r ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Selector("Resina ${ri + 1}", r.id, opciones, Modifier.weight(1f)) { id ->
                    st.updCapa(i) { c -> c.copy(resinas = c.resinas.mapIndexed { k, x -> if (k == ri) x.copy(id = id) else x }) }
                }
                Campo("%", r.pct, Modifier.width(78.dp)) { v ->
                    st.updCapa(i) { c -> c.copy(resinas = c.resinas.mapIndexed { k, x -> if (k == ri) x.copy(pct = v) else x }) }
                }
                IconButton(onClick = {
                    st.updCapa(i) { c ->
                        if (c.resinas.size > 1) c.copy(resinas = c.resinas.filterIndexed { k, _ -> k != ri })
                        else c.copy(resinas = listOf(ResinaPct()))
                    }
                }) { Icon(Icons.Filled.Delete, contentDescription = "Quitar", tint = Gris) }
            }
        }
        TextButton(onClick = { st.updCapa(i) { it.copy(resinas = it.resinas + ResinaPct()) } }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(" Agregar resina")
        }

        Text("Material recuperado", fontWeight = FontWeight.SemiBold, color = RosaOscuro, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Campo("% recup.", capa.recuperado.pct, Modifier.weight(0.8f)) { v ->
                st.updCapa(i) { it.copy(recuperado = it.recuperado.copy(pct = v)) }
            }
            Campo("MFI recup.", capa.recuperado.mfi, Modifier.weight(0.8f)) { v ->
                st.updCapa(i) { it.copy(recuperado = it.recuperado.copy(mfi = v)) }
            }
        }
        Selector("Tipo de recuperado", capa.recuperado.tipo, TIPOS_RECUPERADO.map { it to it }, Modifier.fillMaxWidth()) { v ->
            st.updCapa(i) { it.copy(recuperado = it.recuperado.copy(tipo = v)) }
        }

        Text("Colorante", fontWeight = FontWeight.SemiBold, color = RosaOscuro, fontSize = 13.sp)
        Selector("Colorante (masterbatch)", capa.colorante.tipo, COLORANTES.keys.map { it to it }, Modifier.fillMaxWidth()) { v ->
            st.updCapa(i) { it.copy(colorante = it.colorante.copy(tipo = v)) }
        }
        if (COLORANTES[capa.colorante.tipo] != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Campo("% MB color", capa.colorante.pct, Modifier.weight(1f)) { v ->
                    st.updCapa(i) { it.copy(colorante = it.colorante.copy(pct = v)) }
                }
                Campo("MFI vehículo", capa.colorante.mfi, Modifier.weight(1f)) { v ->
                    st.updCapa(i) { it.copy(colorante = it.colorante.copy(mfi = v)) }
                }
            }
        }
        Text("Total capa: ${fm(suma, 1)}%" + (if (ok) "" else "  (debe sumar 100%)"),
            fontWeight = FontWeight.Bold, color = if (ok) Verde else Rojo)
    }
}

// ============================================================================
// 2. RESULTADOS
// ============================================================================
@Composable
fun PantallaResultados(st: AppState) {
    val R = st.resultado
    val ctx = LocalContext.current
    if (R == null || R.error != null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(R?.error ?: "Ejecute \"Analizar formulación\" para ver resultados.", color = Gris)
            Spacer(Modifier.height(12.dp))
            BotonRosa("Ir a Formulación") { st.tab = 0 }
        }
        return
    }
    val post = R.hallazgos.filter { h -> h.afecta.any { it in POSTPROCESOS } }
    val extrusion = R.hallazgos.filter { it !in post }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(R.producto, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = RosaOscuro)
            Text("${R.geo.maquina ?: "Máquina no indicada"} · ${R.estructura} · ${fm(R.espesor, 0)} µm · " +
                "Corona: ${if (R.corona) "Sí" else "No"}", color = Gris, fontSize = 13.sp)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { compartir(ctx, "Análisis de formulación", st.reporte) }) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(" Compartir reporte")
                }
                BotonRosa("Profundizar con IA") { st.tab = 2 }
            }
        }
        // Indicadores en cuadrícula de 2
        items(R.indicadores.entries.toList().chunked(2)) { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fila.forEach { (k, v) -> TarjetaIndicador(NOMBRE_CAT[k] ?: k, v, Modifier.weight(1f)) }
                if (fila.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        // BUR de la máquina seleccionada
        val geo = R.geo
        if (geo.maquina != null && geo.bur != null) {
            item {
                val (nivel, texto) = R.burEval ?: ("" to "")
                Seccion("Relación de soplado — ${geo.maquina}") {
                    Text("BUR ${fm(geo.bur, 2)}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colorNivel(nivel))
                    Text(if (nivel == "Sin evaluar") texto else "$nivel — $texto", color = colorNivel(nivel))
                    Text("Cabezal Ø ${fm(geo.dadoMm ?: 0.0, 0)} mm · ancho plano ${gn((geo.layFlatMm ?: 0.0) / 10)} cm · " +
                        "Ø burbuja ${fm((geo.dBurbujaMm ?: 0.0) / 10, 1)} cm · Mezcla: ${TIPOS_MEZCLA[R.tipoMezcla] ?: "no indicada"}",
                        fontSize = 12.sp, color = Gris)
                }
            }
        }
        // Propiedades por capa
        item {
            Seccion("Propiedades calculadas por capa") {
                R.capas.forEach { c ->
                    Text(c.nombre, fontWeight = FontWeight.Bold)
                    Text("${pc(c.wEst)}% estructura · ${fm(c.espesorUm, 1)} µm · MFI ${fm(c.mfi, 2)} · ρ ${fm(c.densidad, 3)}\n" +
                        "LDPE ${pc(c.fLdpe)}% · lineal ${pc(c.fLin)}% · recuperado ${fm(c.recPct, 0)}% · pigmento ${fm(c.pigmentoPct, 1)}%\n" +
                        "Slip ${fm(c.slip, 0)} ppm · AB ${fm(c.ab, 0)} ppm · PPA ${if (c.ppa) "Sí" else "No"}",
                        fontSize = 12.sp, color = Gris)
                }
                R.global?.let { G ->
                    Text("Global: MFI ${fm(G.mfi, 2)} · ρ ${fm(G.densidad, 3)} · LDPE ${pc(G.fLdpe)}% · lineales ${pc(G.fLin)}% · " +
                        "recuperado ${fm(G.recPct, 1)}%", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                R.sit?.let {
                    Text("Sellado (orientativo): inicio ≈ ${it.sit} °C en ${it.capa}; barra ${it.barraMin}-${it.barraMax} °C",
                        fontSize = 13.sp)
                }
            }
        }
        // Puntos críticos de procesos posteriores
        for (p in R.postprocesos) {
            val hs = post.filter { p in it.afecta }
            if (hs.isNotEmpty()) {
                item { Titulo("Puntos críticos — ${NOMBRE_CAT[p]}") }
                items(hs) { TarjetaHallazgo(it) }
            }
        }
        item { Titulo("Puntos críticos de extrusión y formulación") }
        items(extrusion) { TarjetaHallazgo(it) }
        // Ventana de proceso
        R.ventana?.let { V ->
            item {
                Seccion("Ventana de proceso orientativa") {
                    V.extrusores.forEach { e ->
                        Text(e.capa, fontWeight = FontWeight.Bold)
                        Text("Alimentación ${e.alimentacion} · Barril ${e.barril}\nAdaptador y dado ${e.dado} · Masa ${e.masa}",
                            fontSize = 12.sp, color = Gris)
                    }
                    Text("Línea de enfriamiento: ${V.lineaEnfriamiento}", fontSize = 13.sp)
                    Text("BUR sugerido: ${V.burSugerido} · Gap de dado: ${V.gapSugerido}", fontSize = 13.sp)
                    Text("Variables que debe seguir el operador:", fontWeight = FontWeight.SemiBold)
                    V.variables.forEach { Text("•  $it", fontSize = 12.sp) }
                }
            }
        }
        // Plan de calidad
        item {
            Titulo("Plan de seguimiento de Calidad")
            Text("🟢 Óptimo: liberar · 🟡 Crítico: alertar, ajustar y medir más seguido · 🔴 Fuera de rango: retener / rechazar",
                fontSize = 12.sp, color = Gris)
        }
        items(R.calidad.filter { it.grupo != "sellado" }) { TarjetaCalidad(it) }
        val sel = R.calidad.filter { it.grupo == "sellado" }
        if (sel.isNotEmpty()) {
            item { Titulo("En máquinas de sellado" + (if (R.zipper) " (con zipper)" else "")) }
            items(sel) { TarjetaCalidad(it) }
            item {
                Text("Temperatura y velocidad son puntos de partida para selladora de barra caliente; ajustar con la curva " +
                    "de sellado de cada máquina.", fontSize = 12.sp, color = Gris)
            }
        }
        item {
            Text("Rangos y tolerancias orientativos de práctica de proceso. Ajustar a la especificación del cliente, " +
                "fichas técnicas y pruebas en línea.", fontSize = 11.sp, color = Gris,
                modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}

@Composable
fun Titulo(t: String) {
    Text(t, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = RosaOscuro, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun TarjetaIndicador(nombre: String, v: Indicador, modifier: Modifier) {
    val c = colorNivel(v.nivel)
    Column(
        modifier.background(Color.White, RoundedCornerShape(12.dp)).border(1.dp, RosaBorde, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(nombre, fontSize = 12.sp, color = Gris, maxLines = 2)
        Text(v.nivel, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c)
        LinearProgressIndicator(progress = { v.puntos / 100f }, color = c, trackColor = Color(0xFFEEEEEE),
            modifier = Modifier.fillMaxWidth())
        Text("Riesgo ${v.puntos}/100", fontSize = 11.sp, color = Gris)
    }
}

@Composable
fun TarjetaHallazgo(h: Hallazgo) {
    val c = colorSev(h.severidad)
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(10.dp))
        .border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(10.dp))) {
        Box(Modifier.width(6.dp).height(10.dp).background(c))
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(h.titulo, fontWeight = FontWeight.Bold, color = c)
            Text(h.afecta.joinToString(", ") { NOMBRE_CAT[it] ?: it }, fontSize = 11.sp, color = Gris)
            if (h.detalle.isNotBlank()) Text(h.detalle, fontSize = 13.sp)
            Text("➜ ${h.sugerencia}", fontSize = 13.sp, color = RosaOscuro)
        }
    }
}

@Composable
fun TarjetaCalidad(q: FilaCalidad) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(10.dp))
            .border(1.dp, RosaBorde, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(q.prueba, fontWeight = FontWeight.Bold)
        Text(q.frecuencia, fontSize = 12.sp, color = Gris)
        Text("🟢 ${q.optimo}", fontSize = 13.sp, color = Verde)
        Text("🟡 ${q.critico}", fontSize = 13.sp, color = Color(0xFF9A6B00))
        Text("🔴 ${q.fuera}", fontSize = 13.sp, color = Rojo)
    }
}

// ============================================================================
// 3. ANÁLISIS IA (Google Gemini)
// ============================================================================
@Composable
fun PantallaIA(st: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(st.alm.apiKey) }
    var modelo by remember { mutableStateOf(st.alm.modelo) }
    var cargando by remember { mutableStateOf(false) }
    var estado by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Seccion("Configuración de Google Gemini") {
            OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("API key de Gemini") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = modelo, onValueChange = { modelo = it }, label = { Text("Modelo") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotonRosa("Guardar") {
                    st.alm.apiKey = key
                    st.alm.modelo = modelo
                    st.mensaje = "Configuración guardada."
                }
            }
            Text("Clave gratuita en aistudio.google.com/apikey (cuenta de Google). El plan gratuito analiza la formulación " +
                "y lee fichas técnicas en PDF (Catálogo → Leer ficha PDF). No incluye búsqueda web. Google puede usar lo " +
                "enviado en el plan gratuito: no incluya datos confidenciales de clientes.", fontSize = 12.sp, color = Gris)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(vertical = 8.dp)) {
            BotonRosa("Analizar con IA", enabled = !cargando) {
                st.alm.apiKey = key
                st.alm.modelo = modelo
                if (!st.analizar()) return@BotonRosa
                cargando = true
                estado = "Analizando… (puede tardar 1-2 min)"
                scope.launch {
                    try {
                        st.textoIa = Gemini.analisisCompleto(st.form, st.cat, st.reporte, key, modelo)
                        estado = "Análisis completado con Google Gemini."
                    } catch (e: Exception) {
                        estado = "Error: ${e.message}"
                    } finally {
                        cargando = false
                    }
                }
            }
            if (cargando) CircularProgressIndicator(color = Rosa, modifier = Modifier.size(24.dp))
            if (st.textoIa.isNotBlank()) {
                OutlinedButton(onClick = { compartir(ctx, "Análisis IA de formulación", st.textoIa) }) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(" Compartir")
                }
            }
        }
        if (estado.isNotBlank()) Text(estado, color = Gris, fontSize = 12.sp)
        if (st.textoIa.isNotBlank()) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp).background(Color.White, RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(12.dp)).padding(12.dp)) {
                TextoMd(st.textoIa)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ============================================================================
// 4. CATÁLOGO
// ============================================================================
data class Edicion(
    val id: String? = null, val marca: String = "", val grado: String = "", val familia: String = "LDPE",
    val proceso: String = "Soplado", val mfi: String = "", val densidad: String = "", val slip: String = "0",
    val ab: String = "0", val ppa: Boolean = false, val antifog: Boolean = false, val uv: Boolean = false,
    val verificado: Boolean = false, val nota: String = "", val fuente: String = "",
)

fun Grado.aEdicion() = Edicion(id, marca, grado, familia, proceso, gn(mfi), gn(densidad), gn(slipPpm), gn(abPpm),
    ppa, antifog, uv, verificado, nota, fuente)

private fun nombreArchivo(ctx: android.content.Context, uri: Uri): String =
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && c.moveToFirst()) c.getString(i) else null
    } ?: "ficha.pdf"

@Composable
fun PantallaCatalogo(st: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var buscar by remember { mutableStateOf("") }
    var ed by remember { mutableStateOf(Edicion()) }
    var estado by remember { mutableStateOf("Toque un grado para editarlo, o 'Nuevo'.") }
    var leyendo by remember { mutableStateOf(false) }

    val lanzadorPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        leyendo = true
        estado = "Leyendo la ficha con IA…"
        scope.launch {
            try {
                val nombre = nombreArchivo(ctx, uri)
                val bytes = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: throw ErrorIA("No se pudo leer el archivo.")
                if (bytes.size > 18 * 1024 * 1024) throw ErrorIA("El PDF supera 18 MB.")
                val d = Gemini.leerFichaPdf(bytes, nombre, st.alm.apiKey, st.alm.modelo)
                if (!d.encontrada) {
                    estado = "No se obtuvo la ficha: ${d.nota.take(200)}"
                } else {
                    val fam = d.familia?.takeIf { it in FAMILIAS } ?: ed.familia
                    ed = ed.copy(
                        marca = ed.marca.ifBlank { d.marca ?: "" },
                        grado = ed.grado.ifBlank { d.grado ?: "" },
                        familia = fam,
                        mfi = d.mfi?.let { gn(it) } ?: ed.mfi,
                        densidad = d.densidad?.let { gn(it) } ?: ed.densidad,
                        slip = d.slipPpm?.let { gn(it) } ?: ed.slip,
                        ab = d.abPpm?.let { gn(it) } ?: ed.ab,
                        ppa = d.ppa,
                        proceso = d.proceso?.takeIf { it in listOf("Soplado", "Cast", "Ambos") } ?: ed.proceso,
                        nota = d.nota, fuente = "PDF: $nombre", verificado = true,
                    )
                    val faltan = listOf("MFI" to d.mfi, "densidad" to d.densidad, "slip" to d.slipPpm, "AB" to d.abPpm)
                        .filter { it.second == null }.map { it.first }
                    estado = "Ficha leída. Revise los valores y presione Guardar." +
                        (if (faltan.isNotEmpty()) " No figuran en la ficha: ${faltan.joinToString(", ")}." else "")
                }
            } catch (e: Exception) {
                estado = "Error: ${e.message}"
            } finally {
                leyendo = false
            }
        }
    }

    val lista = st.cat.values.sortedWith(compareBy({ it.marca }, { it.grado }))
        .filter { buscar.isBlank() || "${it.marca} ${it.grado} ${it.familia}".contains(buscar, ignoreCase = true) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Seccion("Ficha del grado") {
                Text("Descargue la ficha técnica (PDF) del fabricante y use 'Leer ficha PDF (IA)' para llenar los datos.",
                    fontSize = 12.sp, color = Gris)
                Text(estado, fontSize = 13.sp, color = RosaOscuro)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Campo("Marca", ed.marca, Modifier.weight(1f), numerico = false) { ed = ed.copy(marca = it) }
                    Campo("Grado", ed.grado, Modifier.weight(1f), numerico = false) { ed = ed.copy(grado = it) }
                }
                Selector("Familia", ed.familia, FAMILIAS.map { it.key to it.value }, Modifier.fillMaxWidth()) {
                    ed = ed.copy(familia = it)
                }
                Selector("Proceso", ed.proceso, listOf("Soplado", "Cast", "Ambos").map { it to it }, Modifier.fillMaxWidth()) {
                    ed = ed.copy(proceso = it)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Campo("MFI (g/10 min)", ed.mfi, Modifier.weight(1f)) { ed = ed.copy(mfi = it) }
                    Campo("Densidad (g/cm³)", ed.densidad, Modifier.weight(1f)) { ed = ed.copy(densidad = it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Campo("Slip (ppm)", ed.slip, Modifier.weight(1f)) { ed = ed.copy(slip = it) }
                    Campo("Antibloqueo (ppm)", ed.ab, Modifier.weight(1f)) { ed = ed.copy(ab = it) }
                }
                Row {
                    Casilla("PPA", ed.ppa) { ed = ed.copy(ppa = it) }
                    Casilla("Antifog", ed.antifog) { ed = ed.copy(antifog = it) }
                    Casilla("UV", ed.uv) { ed = ed.copy(uv = it) }
                }
                Casilla("Verificado con TDS", ed.verificado) { ed = ed.copy(verificado = it) }
                Campo("Nota", ed.nota, Modifier.fillMaxWidth(), numerico = false) { ed = ed.copy(nota = it) }
                Campo("Fuente (TDS)", ed.fuente, Modifier.fillMaxWidth(), numerico = false) { ed = ed.copy(fuente = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonRosa("Guardar") {
                        if (ed.marca.isBlank() || ed.grado.isBlank()) {
                            st.mensaje = "Marca y grado son obligatorios."
                            return@BotonRosa
                        }
                        val t = TIPICOS[ed.familia] ?: (1.0 to 0.92)
                        val id = ed.id ?: "usr-" + UUID.randomUUID().toString().take(8)
                        st.alm.guardarGrado(Grado(
                            id = id, marca = ed.marca.trim(), grado = ed.grado.trim(), familia = ed.familia,
                            proceso = ed.proceso, mfi = num(ed.mfi).takeIf { it > 0 } ?: t.first,
                            densidad = num(ed.densidad).takeIf { it > 0 } ?: t.second,
                            slipPpm = num(ed.slip), abPpm = num(ed.ab), ppa = ed.ppa, antifog = ed.antifog, uv = ed.uv,
                            nota = ed.nota, verificado = ed.verificado, fuente = ed.fuente,
                        ))
                        st.cat = st.alm.catalogo()
                        ed = ed.copy(id = id)
                        st.mensaje = "Grado guardado."
                    }
                    OutlinedButton(onClick = {
                        ed = Edicion()
                        estado = "Nuevo grado"
                    }) { Text("Nuevo") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(enabled = !leyendo, onClick = {
                        if (st.alm.apiKey.isBlank()) st.mensaje = "Configure la API key en la pestaña Análisis IA."
                        else lanzadorPdf.launch(arrayOf("application/pdf"))
                    }) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = Rosa)
                        Text(" Leer ficha PDF (IA)")
                    }
                    if (leyendo) CircularProgressIndicator(color = Rosa, modifier = Modifier.size(22.dp))
                }
                TextButton(onClick = {
                    val id = ed.id
                    if (id != null && st.alm.eliminarGrado(id)) {
                        st.cat = st.alm.catalogo()
                        ed = st.cat[id]?.aEdicion() ?: Edicion()
                        st.mensaje = "Entrada eliminada (si era un grado base, se restauró su valor original)."
                    } else {
                        st.mensaje = "Sólo se pueden eliminar o restaurar entradas editadas/creadas por el usuario."
                    }
                }) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text(" Eliminar / restaurar")
                }
            }
        }
        item {
            Titulo("Grados disponibles")
            Campo("Buscar grado o marca", buscar, Modifier.fillMaxWidth(), numerico = false) { buscar = it }
        }
        items(lista, key = { it.id }) { g ->
            Row(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(10.dp))
                    .clickable {
                        ed = g.aEdicion()
                        estado = "Editando: ${g.marca} ${g.grado}"
                    }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (g.verificado) Icons.Filled.Verified else Icons.Filled.Warning, contentDescription = null,
                    tint = if (g.verificado) Verde else Naranja)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("${g.marca} · ${g.grado}", fontWeight = FontWeight.SemiBold)
                    Text("${FAMILIAS[g.familia] ?: g.familia} · MFI ${gn(g.mfi)} · ρ ${gn(g.densidad)} · " +
                        "slip ${gn(g.slipPpm)} · AB ${gn(g.abPpm)}", fontSize = 12.sp, color = Gris)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
