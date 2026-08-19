// TacticalMainScreen.kt — "Aussehen #2 / Tactical-MilSpec" Hauptscreen.

package com.andmesh.app.ui.tactical

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.andmesh.app.R

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

val StencilFontFamily = FontFamily(Font(R.font.big_shoulders_stencil, FontWeight.Bold))
val CondensedFontFamily = FontFamily(Font(R.font.barlow_condensed, FontWeight.Normal))

data class MeshNode(
    val nodeId: Long,
    val name: String,
    val hexId: String = "",
    val hopsLabel: String,
    val statusLabel: String = "",
    val isFavorite: Boolean = false
)

@Composable
fun TacticalMainScreen(
    hackRfLinked: Boolean,
    frequencyMhz: String,
    channelName: String,
    signalDbm: String,
    spreadingFactor: String,
    nodes: List<MeshNode>,
    messages: List<MeshMessage>,
    onSendClick: (String) -> Unit,
    onNodeClick: (Long) -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalColors.Background)
    ) {
        StatusHeader(hackRfLinked, onSettingsClick)
        BriefingBox(frequencyMhz, channelName, signalDbm, spreadingFactor)

        // Split space between nodes and messages
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "ROSTER — ${nodes.size} STATIONEN",
                color = TacticalColors.Tan,
                fontFamily = CondensedFontFamily,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(start = 14.dp, top = 8.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .weight(0.4f)
                    .padding(horizontal = 14.dp)
            ) {
                items(nodes) { node ->
                    RosterRow(node = node, onClick = { onNodeClick(node.nodeId) })
                }
            }

            Text(
                text = "COMMS LOG",
                color = TacticalColors.Tan,
                fontFamily = CondensedFontFamily,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(start = 14.dp, top = 16.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .weight(0.6f)
                    .padding(horizontal = 14.dp)
            ) {
                items(messages) { msg -> MessageRow(msg) }
            }
        }

        SendBar(onClick = onSendClick)
    }
}

@Composable
private fun StatusHeader(hackRfLinked: Boolean, onSettingsClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
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
            Text(
                "EINSTELLUNGEN",
                color = TacticalColors.Amber,
                fontFamily = CondensedFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onSettingsClick)
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
private fun RosterRow(node: MeshNode, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (node.isFavorite) {
                    Text(
                        "★",
                        color = TacticalColors.Amber,
                        fontSize = 14.sp
                    )
                }
                Column {
                    Text(
                        node.name.uppercase(),
                        color = TacticalColors.TextPrimary,
                        fontFamily = CondensedFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${node.hexId} · ${node.statusLabel}".uppercase(),
                        color = TacticalColors.Tan,
                        fontFamily = CondensedFontFamily,
                        fontSize = 11.sp
                    )
                }
            }
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
private fun MessageRow(msg: MeshMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = "FROM: ${msg.fromNode.uppercase()}",
            color = TacticalColors.Amber,
            fontFamily = CondensedFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = msg.text,
            color = TacticalColors.TextPrimary,
            fontFamily = CondensedFontFamily,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
        )
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
private fun SendBar(onClick: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
            .background(TacticalColors.Panel)
            .border(1.dp, TacticalColors.OliveDrab),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            textStyle = TextStyle(
                color = TacticalColors.TextPrimary,
                fontFamily = CondensedFontFamily,
                fontSize = 15.sp
            ),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(
                        text = "ENTER MESSAGE...",
                        color = TacticalColors.Amber,
                        fontFamily = CondensedFontFamily,
                        fontSize = 15.sp
                    )
                }
                innerTextField()
            }
        )

        Box(
            modifier = Modifier
                .background(TacticalColors.OliveDrab)
                .clickable {
                    if (text.isNotBlank()) {
                        onClick(text)
                        text = ""
                    }
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "SENDEN",
                color = TacticalColors.TextPrimary,
                fontFamily = StencilFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
        }
    }
}
