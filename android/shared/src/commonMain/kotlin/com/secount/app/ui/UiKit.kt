package com.secount.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Shared smart/modern building blocks (extracted from App.kt, no behavior change). */

internal fun scaled(base: TextUnit, scale: Float): TextUnit = (base.value * scale).sp

/** QR code rendered with zxing + Canvas (no camera permission needed). */
@Composable
internal fun QrCode(content: String) {
    val matrix = remember(content) {
        try {
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 33, 33)
        } catch (e: Exception) {
            null
        }
    }
    if (matrix == null) {
        Text(content, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        return
    }
    val n = matrix.width
    Canvas(Modifier.size(220.dp).background(Color.White).padding(8.dp)) {
        val cell = size.minDimension / n
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (matrix.get(x, y)) {
                    drawRect(
                        Color.Black,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell + 0.5f, cell + 0.5f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DropDown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }, modifier = modifier) {
        OutlinedTextField(
            selected, {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(open, { open = false }) {
            for (o in options) {
                DropdownMenuItem(
                    { Text(o) },
                    onClick = { onSelect(o); open = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MappedDropDown(
    label: String,
    ids: List<String>,
    selectedId: String,
    onSelectId: (String) -> Unit,
    display: (String) -> String,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }, modifier = modifier) {
        OutlinedTextField(
            display(selectedId), {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(open, { open = false }) {
            for (id in ids) {
                DropdownMenuItem(
                    { Text(display(id)) },
                    onClick = { onSelectId(id); open = false }
                )
            }
        }
    }
}

@Composable
internal fun StatusPill(text: String, accent: Color, fontScale: Float) {
    Surface(shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = 0.12f)) {
        Text(
            text.uppercase(),
            fontSize = scaled(10.sp, fontScale), fontWeight = FontWeight.Bold, color = accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
internal fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 44.dp)) {
        Checkbox(checked, onChange)
        Text(label, fontSize = 14.sp)
    }
}

@Composable
internal fun DrawerSection(title: String) {
    Text(
        title.uppercase(),
        fontSize = 11.sp, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
internal fun ThemeDot(themeName: String) {
    val c = when (themeName) {
        "Ocean" -> Color(0xFF0284C7)
        "Sunset" -> Color(0xFFEA580C)
        "Forest" -> Color(0xFF15803D)
        "Lavender" -> Color(0xFF6D64FF)
        "Porcelain" -> Color(0xFF475569)
        "Midnight Android" -> Color(0xFF0E9F6E)
        else -> Brand
    }
    Box(Modifier.size(18.dp).clip(CircleShape).background(c))
}

@Composable
internal fun HeroStat(value: String, label: String, modifier: Modifier = Modifier, fontScale: Float = 1f) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.18f), modifier = modifier) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = scaled(19.sp, fontScale), color = Color.White)
            Text(label, fontSize = scaled(11.sp, fontScale), color = Color.White.copy(alpha = 0.9f))
        }
    }
}

@Composable
internal fun SmartTab(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    compact: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(if (compact) 16.dp else 20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.heightIn(min = if (compact) 52.dp else 60.dp).clickable(onClick = onClick)
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = if (compact) 7.dp else 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = scaled(if (compact) 14.sp else 15.sp, fontScale),
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                fontSize = scaled(11.sp, fontScale),
                color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun EditSection(title: String) {
    Text(
        title,
        fontSize = 11.sp, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}
