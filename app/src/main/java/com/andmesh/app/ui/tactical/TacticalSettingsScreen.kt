package com.andmesh.app.ui.tactical

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TacticalSettingsScreen(
    currentFreqHz: Long,
    onFrequencySelected: (Long) -> Unit,
    currentChannelName: String,
    onChannelNameChanged: (String) -> Unit,
    currentPsk: String,
    onPskChanged: (String) -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalColors.Background)
            .padding(16.dp)
    ) {
        Text(
            text = "EINSTELLUNGEN",
            color = TacticalColors.Amber,
            fontFamily = StencilFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(
            text = "REGION / FREQUENZ",
            color = TacticalColors.Tan,
            fontFamily = CondensedFontFamily,
            fontSize = 14.sp,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val frequencies = listOf(
            "EU 868 (869.525 MHz)" to 869525000L,
            "US 915 (906.875 MHz)" to 906875000L,
            "AU 915 (916.625 MHz)" to 916625000L,
            "EU 433 (433.175 MHz)" to 433175000L
        )

        frequencies.forEach { (label, freqValue) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFrequencySelected(freqValue) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (currentFreqHz == freqValue) "[X]" else "[ ]",
                    color = TacticalColors.Amber,
                    fontFamily = CondensedFontFamily,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(
                    text = label,
                    color = TacticalColors.TextPrimary,
                    fontFamily = CondensedFontFamily,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "KANALNAME",
            color = TacticalColors.Tan,
            fontFamily = CondensedFontFamily,
            fontSize = 14.sp,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        BasicTextField(
            value = currentChannelName,
            onValueChange = onChannelNameChanged,
            textStyle = TextStyle(
                color = TacticalColors.TextPrimary,
                fontFamily = CondensedFontFamily,
                fontSize = 16.sp
            ),
            cursorBrush = SolidColor(TacticalColors.Amber),
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalColors.Panel)
                .border(1.dp, TacticalColors.OliveDrab)
                .padding(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PSK (BASE64)",
            color = TacticalColors.Tan,
            fontFamily = CondensedFontFamily,
            fontSize = 14.sp,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        BasicTextField(
            value = currentPsk,
            onValueChange = onPskChanged,
            textStyle = TextStyle(
                color = TacticalColors.TextPrimary,
                fontFamily = CondensedFontFamily,
                fontSize = 16.sp
            ),
            cursorBrush = SolidColor(TacticalColors.Amber),
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalColors.Panel)
                .border(1.dp, TacticalColors.OliveDrab)
                .padding(12.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalColors.OliveDrab)
                .clickable(onClick = onBackClick)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ZURÜCK",
                color = TacticalColors.TextPrimary,
                fontFamily = StencilFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 1.sp
            )
        }
    }
}
