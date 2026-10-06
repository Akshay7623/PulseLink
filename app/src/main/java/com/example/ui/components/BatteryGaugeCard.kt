package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DeviceTelemetry
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.EmeraldCharging
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.RoseAlert

@Composable
fun BatteryGaugeCard(
    telemetry: DeviceTelemetry,
    titlePrefix: String = "Device Battery",
    modifier: Modifier = Modifier
) {
    val level = telemetry.batteryLevel
    val isCharging = telemetry.isCharging
    val isLow = level <= 20 && !isCharging

    val batteryColor = when {
        isCharging -> EmeraldCharging
        isLow -> RoseAlert
        level > 50 -> Color(0xFF10B981)
        else -> Color(0xFFF59E0B)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isCharging) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "boltScale"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        // Flat: a shadow would show through the translucent container as an inner box
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isCharging) EmeraldGlow else batteryColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = titlePrefix,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (isCharging) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(EmeraldCharging.copy(alpha = 0.2f))
                            .border(1.dp, EmeraldCharging.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Charging",
                                tint = EmeraldGlow,
                                modifier = Modifier
                                    .size(14.dp)
                                    .scale(pulseScale)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "CHARGING",
                                color = EmeraldGlow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Percentage & Big Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$level",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = batteryColor
                        )
                        Text(
                            text = "%",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = batteryColor,
                            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                        )
                    }
                    Text(
                        text = if (isCharging) "Plugged into ${telemetry.chargePlugType}" else "On Battery Power",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Battery Icon Graphic
                Box(
                    modifier = Modifier
                        .width(68.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(2.dp, batteryColor.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                        .padding(3.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (level / 100f).coerceIn(0.05f, 1f))
                            .height(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(batteryColor.copy(alpha = 0.7f), batteryColor)
                                )
                            )
                    )
                    if (isCharging) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Charging Bolt",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(20.dp)
                                .scale(pulseScale)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { (level / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = batteryColor,
                trackColor = MaterialTheme.colorScheme.surface,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Sub-metrics (Health, Temperature, Voltage, Plug)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricChip(
                    icon = Icons.Default.DeviceThermostat,
                    label = "Temp",
                    value = if (telemetry.batteryTempCelsius > 0) "${telemetry.batteryTempCelsius}°C" else "N/A"
                )
                MetricChip(
                    icon = Icons.Default.ElectricMeter,
                    label = "Voltage",
                    value = if (telemetry.batteryVoltageMv > 0) "${telemetry.batteryVoltageMv} mV" else "N/A"
                )
                MetricChip(
                    icon = Icons.Default.Power,
                    label = "Health",
                    value = telemetry.batteryHealth
                )
            }
        }
    }
}

@Composable
private fun MetricChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
