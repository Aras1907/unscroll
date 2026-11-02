package com.unscroll.app

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { UnscrollTheme { Home(Prefs(this)) } }
    }
}

private val Coral = Color(0xFFFF6B5B)

@Composable
private fun UnscrollTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(ctx)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Coral)
        else -> lightColorScheme(primary = Coral)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun onResume(block: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) block() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

@Composable
private fun Home(prefs: Prefs) {
    val ctx = LocalContext.current
    var guardOn by remember { mutableStateOf(false) }
    var today by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    onResume {
        guardOn = isGuardEnabled(ctx)
        today = prefs.blockedToday
        total = prefs.blockedTotal
    }

    Scaffold { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Unscroll", fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(
                "Watch the reel in front of you. Just not the next hundred.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            StatusCard(guardOn) {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Swipes undone today", today, Modifier.weight(1f))
                Stat("All time", total, Modifier.weight(1f))
            }

            SectionTitle("Short videos", "Watch what's on screen. Swiping to the next one snaps back.")
            Card(shape = RoundedCornerShape(24.dp)) {
                AppToggle(prefs, Guard.INSTAGRAM_REELS, "Instagram Reels", "Reels tab, and reels opened from anywhere else")
                AppToggle(prefs, Guard.LITE_REELS, "Instagram Lite Reels", "Best effort. Lite shows other apps very little")
                AppToggle(prefs, Guard.YOUTUBE_SHORTS, "YouTube Shorts", "Normal videos are left alone")
            }
            Text(
                "Reels friends send you in DMs still open and play. You just can't swipe on to the next one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            SectionTitle("Feeds", "You see what's at the top. Scrolling down is undone.")
            Card(shape = RoundedCornerShape(24.dp)) {
                AppToggle(prefs, Guard.INSTAGRAM_FEED, "Instagram feed", "Home feed in Instagram and Instagram Lite")
                AppToggle(prefs, Guard.REDDIT_FEED, "Reddit feed", "Home and Popular")
            }

            Text(
                "Everything happens on your phone. Unscroll reads screen layout only to recognise Reels, Shorts and feeds, and it never stores or sends what it sees.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun StatusCard(on: Boolean, openSettings: () -> Unit) {
    val bg = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val fg = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (on) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, null)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (on) "Guard is on" else "Guard is off",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                if (on) "Go ahead and open Instagram. Try swiping to the next reel."
                else "Unscroll needs accessibility access to notice when you swipe in Reels or Shorts and scroll it back. Tap below, open \"Unscroll guard\" and switch it on.",
            )
            if (!on) {
                Text(
                    "Toggle greyed out? Android blocks this for apps installed from an APK. Go to Settings > Apps > Unscroll, tap the ⋮ menu, choose \"Allow restricted settings\", then try again.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FilledTonalButton(onClick = openSettings) {
                Text(if (on) "Accessibility settings" else "Turn on the guard")
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("$value", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(text: String, detail: String) {
    Column(Modifier.padding(top = 8.dp, start = 4.dp)) {
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppToggle(prefs: Prefs, g: Guard, name: String, detail: String) {
    var on by remember { mutableStateOf(prefs.isOn(g)) }
    ToggleRow(name, detail, on) { on = it; prefs.setOn(g, it) }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(if (checked) Coral else Color.Gray, CircleShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

private fun isGuardEnabled(ctx: android.content.Context): Boolean {
    val me = ComponentName(ctx, GuardService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabled.split(':').any { it.equals(me, true) }
}
