// TacticalMainScreen.kt — "Aussehen #2 / Tactical-MilSpec" Hauptscreen.
// Startpunkt/Vorlage für Jules Task 4. Paketname unten an das echte MeshSDR-Projekt
// anpassen (aktuell Platzhalter, analog RFCuts Struktur).
//
// Fonts: als .ttf in res/font/ bundeln (Big Shoulders Stencil, Barlow Condensed —
// beides freie Google Fonts). Bundled statt Downloadable Fonts gewählt, damit keine
// Play-Services-Cert-Hashes gepflegt werden müssen.

package com.hans.meshsdr.ui.tactical

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// import com.hans.meshsdr.R  // für R.font.* — einkommentieren im echten Projekt

// ---- Design-Tokens: "Aussehen #2 — Tactical/MilSpec" ----
object TacticalColors {
    val Background = Color(0xFF1A1A16)
    val Panel = Color(0xFF24241D)
    val Tan = Color(0xFF8B7355)
    val OliveDrab = Color(0xFF4B5320)
    val Amber = Color(0xFFE8A33D)
    val TextPrimary = Color(0xFFD8D4C8)
    val Divider = Color(0xFF2C2C24)
}

// val StencilFontFamily = FontFamily(Font(R.font.big_shoulders_stencil, FontWeight.Bold))
// val CondensedFontFamily = FontFamily(Font(R.font.barlow_condensed, FontWeight.Normal))
// Platzhalter bis res/font/ bestückt ist, damit die Datei für sich betrachtbar bleibt:
val StencilFontFamily = FontFamily.SansSerif
val CondensedFontFamily = FontFamily.SansSerif

data class MeshNode(val name: String, val hopsLabel: String)

@Composable
fun TacticalMainScreen(
    hackRfLinked: Boolean,
    frequencyMhz: String,
    channelName: String,
    signalDbm: String,
    spreadingFactor: String,
    nodes: List<MeshNode>,
    onSendClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalColors.Background)
    ) {
        StatusHeader(hackRfLinked)
        BriefingBox(frequencyMhz, channelName, signalDbm, spreadingFactor)
        Text(
            text = "ROSTER — ${nodes.size} STATIONEN",
            color = TacticalColors.Tan,
            fontFamily = CondensedFontFamily,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 14.dp, top = 8.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            nodes.forEach { node -> RosterRow(node) }
        }
        SendBar(onClick = onSendClick)
    }
}

@Composable
private fun StatusHeader(hackRfLinked: Boolean) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "MESHCOMM",
                color = TacticalColors.Amber,
                fontFamily = StencilFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = 0.5.sp
            )
            Text(
                if (hackRfLinked) "SITREP · HACKRF LINK AKTIV" else "SITREP · KEIN LINK",
                color = TacticalColors.Tan,
                fontFamily = CondensedFontFamily,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
        }
        CornerBrackets(modifier = Modifier.matchParentSize())
    }
}

@Composable
private fun CornerBrackets(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val len = 14.dp.toPx()
        val strokeWidth = 2.dp.toPx()
        val inset = 6.dp.toPx()
        // oben links
        drawLine(TacticalColors.Amber, Offset(inset, inset), Offset(inset + len, inset), strokeWidth)
        drawLine(TacticalColors.Amber, Offset(inset, inset), Offset(inset, inset + len), strokeWidth)
        // oben rechts
        drawLine(TacticalColors.Amber, Offset(size.width - inset, inset), Offset(size.width - inset - len, inset), strokeWidth)
        drawLine(TacticalColors.Amber, Offset(size.width - inset, inset), Offset(size.width - inset, inset + len), strokeWidth)
    }
}

@Composable
private fun BriefingBox(freq: String, channel: String, signal: String, sf: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .background(TacticalColors.Panel)
            .border(width = 1.dp, color = TacticalColors.OliveDrab)
            .padding(12.dp)
    ) {
        BriefRow("FREQ", "$freq MHz")
        BriefRow("KANAL", channel)
        BriefRow("SIGNAL", "$signal / $sf")
    }
}

@Composable
private fun BriefRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TacticalColors.Tan, fontFamily = CondensedFontFamily, fontSize = 13.sp)
        Text(value, color = TacticalColors.TextPrimary, fontFamily = CondensedFontFamily, fontSize = 13.sp)
    }
}

@Composable
private fun RosterRow(node: MeshNode) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                node.name.uppercase(),
                color = TacticalColors.TextPrimary,
                fontFamily = CondensedFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                node.hopsLabel.uppercase(),
                color = TacticalColors.Amber,
                fontFamily = CondensedFontFamily,
                fontSize = 11.sp
            )
        }
        HorizontalDivider(color = TacticalColors.Divider)
    }
}

@Composable
private fun HorizontalDivider(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

@Composable
private fun SendBar(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
            .background(TacticalColors.OliveDrab)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "NACHRICHT SENDEN",
            color = TacticalColors.TextPrimary,
            fontFamily = StencilFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 1.sp
        )
    }
}
