package com.ritmo.treinos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun date(ms: Long, pattern: String = "dd/MM/yyyy • HH:mm") = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag("pt-BR")))
fun number(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.forLanguageTag("pt-BR"), "%.1f", value)
fun duration(start: Long, end: Long) = "${((end - start).coerceAtLeast(0) / 60000)} min"
@Composable fun PageTitle(title: String, subtitle: String? = null, back: (() -> Unit)? = null) {
    if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
    Text(title, style = MaterialTheme.typography.headlineLarge)
    if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(8.dp))
}
@Composable fun EmptyState(title: String, body: String) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}
@Composable fun Section(text: String) { Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
/** Exact user data, chronological points, no extrapolated goals or estimates. */
@Composable fun ProgressChart(points: List<Pair<Long, Double>>, label: String) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = .25f)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (points.isEmpty()) Text("Conclua uma série para visualizar sua evolução.")
            else {
                Text("Último registro: ${number(points.last().second)} kg", color = color)
                val low = (points.minOf { it.second } * .9).coerceAtLeast(0.0)
                val high = (points.maxOf { it.second } * 1.1).coerceAtLeast(low + 1)
                Text("Escala: ${number(low)}–${number(high)} kg", style = MaterialTheme.typography.labelMedium)
                Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                    val inset = 8.dp.toPx(); val w = size.width - inset * 2; val h = size.height - inset * 2
                    for (i in 0..3) drawLine(grid, Offset(inset, inset + h * i / 3), Offset(inset + w, inset + h * i / 3))
                    val first = points.first().first; val span = (points.last().first - first).coerceAtLeast(1)
                    val positions = points.map { (time, value) -> Offset(if (points.size == 1) size.width / 2 else inset + w * ((time - first).toDouble() / span).toFloat(), inset + h * (1 - (value - low) / (high - low)).toFloat()) }
                    positions.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, 3.dp.toPx(), StrokeCap.Round) }
                    positions.forEach { drawCircle(color, 5.dp.toPx(), it) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(date(points.first().first, "dd/MM"), style = MaterialTheme.typography.labelMedium); Text(date(points.last().first, "dd/MM"), style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}
