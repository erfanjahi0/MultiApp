package org.waxmoon

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.hack.opensdk.BuildConfig
import com.hack.opensdk.CmdConstants
import com.hack.opensdk.HackApi
import com.hack.utils.FileUtils
import org.waxmoon.ui.theme.GithubMultiAppTheme
import java.io.File

class AppListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GithubMultiAppTheme {
                BeautifulMultiApp()
            }
        }
    }
}

const val INSTALL_SUCCEEDED = 1
const val INSTALL_FAILED_ALREADY_EXISTS = -1
const val INSTALL_FAILED_INVALID_APK = -2
const val INSTALL_FAILED_INVALID_URI = -3
const val START_SUCCESS = 0
const val MENU_ENABLE_GP = 0
const val MENU_OPEN_GP = 1

val TAG = AppListActivity::class.simpleName
var userSpace: Int = 0
var dialogState = mutableStateOf(Any())

private val Ink = Color(0xFF121827)
private val CardGlass = Color(0xF21B2334)
private val CardStroke = Color(0x334D6BFF)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFFB6C2D9)
private val Muted = Color(0xFF7F8CA7)
private val Accent = Color(0xFF7C5CFF)
private val Accent2 = Color(0xFF18D6B8)
private val Warning = Color(0xFFFFB86B)
private val Danger = Color(0xFFFF6B8B)

var assistInstallRequestContract = object : ActivityResultContract<ApkInfo, ApkInfo>() {
    var info: ApkInfo? = null

    override fun parseResult(resultCode: Int, intent: Intent?): ApkInfo {
        return info!!
    }

    override fun createIntent(context: Context, input: ApkInfo): Intent {
        info = input
        return Intent().apply {
            setDataAndType(requestInstallAssist(context), "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION.or(Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            action = Intent.ACTION_VIEW
        }
    }

    fun requestInstallAssist(context: Context): Uri? {
        val file = File(context.externalCacheDir, "assist.apk")
        FileUtils.extractAsset(context, "assist.apk", file)
        val authority = context.packageName + ":" + MoonProvider::class.qualifiedName
        return FileProvider.getUriForFile(context, authority, file)
    }
}

fun isInstall(info: ApkInfo): Boolean {
    return try {
        MoonApplication.INSTANCE().packageManager.getPackageInfo(info.getShellPackage(), 0)
        true
    } catch (e: Exception) {
        false
    }
}

@Composable
fun BeautifulMultiApp() {
    var query by rememberSaveable { mutableStateOf("") }
    var onlyReady by rememberSaveable { mutableStateOf(false) }
    var userId by rememberSaveable { mutableStateOf(userSpace) }

    val apps = rememberInstalledApps()

    val filteredApps = remember(apps, query, onlyReady, userId) {
        apps
            .filter { app ->
                query.isBlank() ||
                    app.label.contains(query, ignoreCase = true) ||
                    app.pkgName.contains(query, ignoreCase = true)
            }
            .filter { app -> !onlyReady || safeIsInstall(app) }
            .sortedBy { app -> app.label.toLowerCase() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF080A12), Color(0xFF11182A), Color(0xFF090D17))
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 28.dp)
        ) {
            item {
                HeroHeader(
                    userId = userId,
                    appCount = apps.size,
                    readyCount = apps.count { app -> safeIsInstall(app) },
                    onSpaceBack = {
                        if (userId > 0) {
                            userSpace--
                            userId = userSpace
                        }
                    },
                    onSpaceForward = {
                        userSpace++
                        userId = userSpace
                    },
                    onEnableGoogle = { clickMenu(MENU_ENABLE_GP) },
                    onOpenGooglePlay = { clickMenu(MENU_OPEN_GP) }
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                SearchAndFilterBar(
                    query = query,
                    onlyReady = onlyReady,
                    onQueryChange = { newQuery -> query = newQuery },
                    onOnlyReadyChange = { newValue -> onlyReady = newValue }
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                SmartFeatureRow()
            }

            item {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Your apps",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${filteredApps.size} shown",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            if (filteredApps.isEmpty()) {
                item { EmptyState(query = query) }
            } else {
                items(filteredApps.size) { index ->
                    BeautifulAppCard(apkInfo = filteredApps[index])
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        AssistInstallDialog()
    }
}

@Composable
fun rememberInstalledApps(): SnapshotStateList<ApkInfo> {
    val context = LocalContext.current
    val list = remember { mutableStateListOf<ApkInfo>() }

    LaunchedEffect(Unit) {
        list.clear()

        val intent = Intent().apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val pm = context.packageManager
        val activityInfos = pm.queryIntentActivities(intent, 0)
        val result = mutableListOf<ApkInfo>()

        for (resolveInfo in activityInfos) {
            if (context.packageName != resolveInfo.activityInfo.packageName) {
                val apkInfo = ApkInfo(resolveInfo.activityInfo.applicationInfo.sourceDir, true)
                apkInfo.init(context = context, pkg = resolveInfo.activityInfo.applicationInfo.packageName)
                if (apkInfo.valid()) {
                    result.add(apkInfo)
                }
            }
        }

        list.addAll(result.sortedBy { app -> app.label.toLowerCase() })
    }

    return list
}

@Composable
fun HeroHeader(
    userId: Int,
    appCount: Int,
    readyCount: Int,
    onSpaceBack: () -> Unit,
    onSpaceForward: () -> Unit,
    onEnableGoogle: () -> Unit,
    onOpenGooglePlay: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(30.dp),
        backgroundColor = Color.Transparent,
        elevation = 10.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF202DFF), Color(0xFF8C4DFF), Color(0xFF10D7C4))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Apps, contentDescription = null, tint = Color.White)
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Orbit MultiApp",
                            color = Color.White,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "A cleaner space for cloned apps",
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 13.sp
                        )
                    }

                    IconButton(onClick = onEnableGoogle) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White)
                    }
                }

                Spacer(Modifier.height(22.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeroMetric(title = "Apps", value = appCount.toString(), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    HeroMetric(title = "Ready", value = readyCount.toString(), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    HeroMetric(title = "Space", value = userId.toString(), modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpaceButton(icon = Icons.Filled.ArrowBack, onClick = onSpaceBack)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "User Space $userId",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    SpaceButton(icon = Icons.Filled.ArrowForward, onClick = onSpaceForward)
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = onOpenGooglePlay,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.White, contentColor = Accent),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Google Play in this space", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun HeroMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(text = value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(text = title, color = Color.White.copy(alpha = 0.76f), fontSize = 12.sp)
    }
}

@Composable
fun SpaceButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
    }
}

@Composable
fun SearchAndFilterBar(
    query: String,
    onlyReady: Boolean,
    onQueryChange: (String) -> Unit,
    onOnlyReadyChange: (Boolean) -> Unit
) {
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            label = { Text("Search apps", color = TextSecondary) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = TextSecondary)
                    }
                }
            },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = TextPrimary,
                cursorColor = Accent2,
                focusedBorderColor = Accent2,
                unfocusedBorderColor = CardStroke,
                backgroundColor = Ink
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            FeatureChip(
                label = if (onlyReady) "Showing ready apps" else "All apps",
                icon = if (onlyReady) Icons.Filled.CheckCircle else Icons.Filled.ViewModule,
                selected = onlyReady,
                onClick = { onOnlyReadyChange(!onlyReady) }
            )

            Spacer(Modifier.width(10.dp))

            FeatureChip(
                label = "Clean UI",
                icon = Icons.Filled.Star,
                selected = true,
                onClick = { }
            )
        }
    }
}

@Composable
fun SmartFeatureRow() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardGlass)
            .border(1.dp, CardStroke, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Text("Useful features", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))

        Row {
            MiniFeature("Multi-space", Icons.Filled.ViewModule, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            MiniFeature("Google Play", Icons.Filled.PlayArrow, Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        Row {
            MiniFeature("Fast launch", Icons.Filled.PlayArrow, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            MiniFeature("App manager", Icons.Filled.Settings, Modifier.weight(1f))
        }
    }
}

@Composable
fun MiniFeature(label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Accent2, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun FeatureChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (selected) Accent.copy(alpha = 0.24f) else CardGlass)
            .border(1.dp, if (selected) Accent else CardStroke, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Color.White else TextSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (selected) Color.White else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun BeautifulAppCard(apkInfo: ApkInfo) {
    var expanded by remember { mutableStateOf(false) }
    val installed = remember(apkInfo.pkgName, userSpace, expanded) { safeIsInstall(apkInfo) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        backgroundColor = CardGlass,
        elevation = 7.dp
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, CardStroke, RoundedCornerShape(24.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Image(
                        bitmap = apkInfo.bitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (installed) Accent2 else Warning)
                            .border(2.dp, CardGlass, CircleShape)
                    )
                }

                Spacer(Modifier.width(13.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = apkInfo.label,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = apkInfo.pkgName,
                        color = Muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(8.dp))

                    Row {
                        StatusPill(if (installed) "Ready" else "Not cloned", if (installed) Accent2 else Warning)
                        Spacer(Modifier.width(6.dp))
                        StatusPill("v${apkInfo.version}", Color(0xFF6EA8FF))
                    }
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(14.dp))
                Divider(color = Color.White.copy(alpha = 0.08f))
                Spacer(Modifier.height(14.dp))

                Text("Actions", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                Spacer(Modifier.height(10.dp))

                Row {
                    ActionButton(
                        text = if (installed) "Reinstall" else "Clone",
                        icon = Icons.Filled.ContentCopy,
                        color = Accent,
                        modifier = Modifier.weight(1f),
                        onClick = { install(apkInfo) }
                    )

                    Spacer(Modifier.width(8.dp))

                    ActionButton(
                        text = "Launch",
                        icon = Icons.Filled.PlayArrow,
                        color = Accent2,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (TextUtils.equals(BuildConfig.ASSIST_PACKAGE, apkInfo.getShellPackage())) {
                                if (!isInstall(apkInfo)) {
                                    dialogState.value = apkInfo
                                    return@ActionButton
                                }
                            }

                            startApp(apkInfo)
                        }
                    )

                    Spacer(Modifier.width(8.dp))

                    ActionButton(
                        text = "Remove",
                        icon = Icons.Filled.Delete,
                        color = Danger,
                        modifier = Modifier.weight(1f),
                        onClick = { uninstall(apkInfo) }
                    )
                }
            }
        }
    }
}

@Composable
fun StatusPill(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = color.copy(alpha = 0.16f), contentColor = color),
        elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
        modifier = modifier.heightIn(min = 46.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun EmptyState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(CardGlass)
            .border(1.dp, CardStroke, RoundedCornerShape(28.dp))
            .padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Muted, modifier = Modifier.size(48.dp))

        Spacer(Modifier.height(12.dp))

        Text("No apps found", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)

        Text(
            text = if (query.isBlank()) "Try changing the filter." else "No result for \"$query\".",
            color = TextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
fun AssistInstallDialog() {
    val openDialog = remember { dialogState }

    val launcher = rememberLauncherForActivityResult(contract = assistInstallRequestContract) {
        if (!isInstall(it)) {
            Toast.makeText(MoonApplication.INSTANCE(), "Assist package install failed", Toast.LENGTH_SHORT).show()
        }
    }

    if (openDialog.value is ApkInfo) {
        AlertDialog(
            onDismissRequest = { openDialog.value = Any() },
            title = { Text(text = "Assist package needed") },
            text = { Text("This app requires the 32-bit assist package before it can start in this space.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        launcher.launch(openDialog.value as ApkInfo)
                        openDialog.value = Any()
                    }
                ) {
                    Text("Install")
                }
            },
            dismissButton = {
                TextButton(onClick = { openDialog.value = Any() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

fun safeIsInstall(info: ApkInfo): Boolean {
    return try {
        isInstall(info)
    } catch (_: Exception) {
        false
    }
}

var clickMenu: (Int) -> Unit = { index ->
    when (index) {
        MENU_ENABLE_GP -> {
            val googlePkgs = listOf("com.google.android.gms", "com.google.android.gsf", "com.android.vending")

            for (pkg in googlePkgs) {
                HackApi.installPackageFromHost(pkg, userSpace, false)
            }

            Toast.makeText(MoonApplication.INSTANCE(), "Google services enabled for space $userSpace", Toast.LENGTH_SHORT).show()
        }

        MENU_OPEN_GP -> {
            val intent = MoonApplication.INSTANCE().packageManager.getLaunchIntentForPackage("com.android.vending")
            intent?.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

            if (intent == null) {
                Toast.makeText(MoonApplication.INSTANCE(), "Google Play is not installed on this phone", Toast.LENGTH_SHORT).show()
            } else {
                HackApi.startActivity(intent, userSpace)
            }
        }
    }
}

var uninstall: (ApkInfo) -> Unit = { apkInfo ->
    HackApi.uninstallPackage(apkInfo.pkgName, userSpace)
    Toast.makeText(MoonApplication.INSTANCE(), "Removed from space $userSpace", Toast.LENGTH_SHORT).show()
}

var install: (ApkInfo) -> Unit = { apkInfo ->
    val ret = HackApi.installPackageFromHost(apkInfo.pkgName, userSpace, false)

    when (ret) {
        INSTALL_SUCCEEDED -> Toast.makeText(MoonApplication.INSTANCE(), "Cloned into space $userSpace", Toast.LENGTH_SHORT).show()
        INSTALL_FAILED_ALREADY_EXISTS -> Toast.makeText(MoonApplication.INSTANCE(), "Already cloned in this space", Toast.LENGTH_SHORT).show()
        INSTALL_FAILED_INVALID_APK -> Toast.makeText(MoonApplication.INSTANCE(), "Invalid APK", Toast.LENGTH_SHORT).show()
        INSTALL_FAILED_INVALID_URI -> Toast.makeText(MoonApplication.INSTANCE(), "Invalid URI", Toast.LENGTH_SHORT).show()
        else -> Toast.makeText(MoonApplication.INSTANCE(), "Action failed", Toast.LENGTH_SHORT).show()
    }
}

var startApp: (ApkInfo) -> Unit = { apkInfo ->
    var intent: Intent? = null

    if (apkInfo.sysInstalled) {
        intent = MoonApplication.INSTANCE().packageManager.getLaunchIntentForPackage(apkInfo.pkgName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    } else {
        Toast.makeText(MoonApplication.INSTANCE(), "Unsupported app", Toast.LENGTH_SHORT).show()
    }

    if (intent != null) {
        Log.d(TAG, "begin start " + apkInfo.pkgName)

        val startRet = HackApi.startActivity(intent, userSpace)

        if (startRet != START_SUCCESS) {
            Toast.makeText(MoonApplication.INSTANCE(), "Launch failed", Toast.LENGTH_SHORT).show()
        }
    }
}

@Stable
class ApkInfo constructor(val apkPath: String, val sysInstalled: Boolean) {
    lateinit var label: String
    lateinit var pkgName: String
    lateinit var version: String
    lateinit var bitmap: ImageBitmap

    var sysApp: Boolean = false
    var extras: Bundle? = null

    fun getShellPackage(): String {
        if (extras == null) {
            extras = HackApi.getPackageSetting(pkgName, userSpace, 0)
        }

        val assist: Boolean? = extras?.getBoolean(CmdConstants.PKG_SET_REQUEST_ASSISTANT, false)

        return if (assist != null && assist) {
            BuildConfig.ASSIST_PACKAGE
        } else {
            BuildConfig.MASTER_PACKAGE
        }
    }

    fun init(context: Context, pkg: String?) {
        val pm = context.packageManager
        val pkgInfo: PackageInfo?

        if (sysInstalled && pkg != null) {
            pkgInfo = pm.getPackageInfo(pkg, 0)

            val labelId: Int = pkgInfo.applicationInfo.labelRes

            label = when (labelId) {
                0 -> pkgInfo.packageName
                else -> pm.getResourcesForApplication(pkgInfo.packageName).getString(labelId)
            }
        } else {
            pkgInfo = pm.getPackageArchiveInfo(apkPath, 0)
            label = pkgInfo?.packageName ?: ""
        }

        if (pkgInfo != null) {
            pkgName = pkgInfo.packageName
            version = pkgInfo.versionName ?: "unknown"

            val icon = pkgInfo.applicationInfo.loadIcon(pm)
            bitmap = getAppIconCompat(icon)

            sysApp = (pkgInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        }
    }

    fun valid(): Boolean {
        return ::pkgName.isInitialized &&
            ::version.isInitialized &&
            pkgName.isNotEmpty() &&
            version.isNotEmpty() &&
            !sysApp
    }

    private fun getAppIconCompat(icon: Drawable): ImageBitmap {
        val width = if (icon.intrinsicWidth > 0) icon.intrinsicWidth else 96
        val height = if (icon.intrinsicHeight > 0) icon.intrinsicHeight else 96

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val tmpCanvas = Canvas(bitmap)

        icon.setBounds(0, 0, tmpCanvas.width, tmpCanvas.height)
        icon.draw(tmpCanvas)

        return bitmap.asImageBitmap()
    }

    override fun toString(): String {
        return "ApkInfo(apkPath='$apkPath', sysInstalled=$sysInstalled, label='$label', pkgName='$pkgName', version='$version', sysApp=$sysApp)"
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    GithubMultiAppTheme {
        Greeting("Android")
    }
}
