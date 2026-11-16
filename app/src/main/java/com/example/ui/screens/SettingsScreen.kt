package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BackupImportMode
import com.example.data.model.BackupImportPreview
import com.example.data.model.BackupImportSummary
import com.example.ui.theme.AlertRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VividGreen
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.BusinessNavDestination
import com.example.ui.viewmodel.StoreViewModel
import com.example.domain.ShopTypeCatalog
import com.example.ui.components.BackupImportConfirmDialog
import com.example.ui.components.BackupImportPreviewDialog
import com.example.ui.components.BackupImportSummaryDialog
import com.example.ui.components.BackupPinDialog
import com.example.ui.components.BackupPinDialogMode
import com.example.ui.components.CsvExportTargetDialog
import com.example.ui.components.SettingsShopTypeDialog
import com.example.ui.components.shopTypeIcon
import com.example.util.AppLanguage
import com.example.util.PhotoStorageMode
import com.example.util.ShopLocale
import com.example.util.SyncStatus
import java.nio.charset.StandardCharsets

@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    storeViewModel: StoreViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateDestination: ((BusinessNavDestination) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentLanguage by storeViewModel.currentLanguage.collectAsState()
    val store by storeViewModel.store.collectAsState()
    val isOwner by storeViewModel.isOwner.collectAsState()
    val currentMember by storeViewModel.currentMember.collectAsState()
    val userProfile by authViewModel.userProfile.collectAsState()
    val authUiState by authViewModel.uiState.collectAsState()
    val syncStatus by storeViewModel.syncStatus.collectAsState()
    val isStockGridView by storeViewModel.isStockGridView.collectAsState()
    val isSimpleMode by storeViewModel.isSimpleMode.collectAsState()
    val isPrivacyHidden by storeViewModel.isPrivacyHidden.collectAsState()
    val canExpenses by storeViewModel.canAccessExpensesAndIncomeFlow.collectAsState()
    val shopType by storeViewModel.shopType.collectAsState()
    val isBusy by storeViewModel.isBusy.collectAsState()
    val photoStorageMode by storeViewModel.photoStorageMode.collectAsState()
    val products by storeViewModel.products.collectAsState()
    val statusMessage by storeViewModel.statusMessage.collectAsState()
    val errorMessage by storeViewModel.errorMessage.collectAsState()
    val hasOwnerPin by storeViewModel.hasOwnerPin.collectAsState()
    val isBackupUnlocked by storeViewModel.isBackupUnlocked.collectAsState()
    val context = LocalContext.current

    var dangerExpanded by rememberSaveable { mutableStateOf(true) }
    var showShopTypeDialog by remember { mutableStateOf(false) }
    var pendingConvertTargetMode by remember { mutableStateOf<PhotoStorageMode?>(null) }
    var pendingConvertCount by remember { mutableIntStateOf(0) }
    var pinDialogMode by remember { mutableStateOf<BackupPinDialogMode?>(null) }
    var pendingPinUnlockedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingJsonPayload by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingCsvPayload by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }
    var showCsvTargetDialog by remember { mutableStateOf(false) }
    var pendingImportRawJson by remember { mutableStateOf<String?>(null) }
    var pendingImportPreview by remember { mutableStateOf<BackupImportPreview?>(null) }
    var pendingImportMode by remember { mutableStateOf<BackupImportMode?>(null) }
    var importSummary by remember { mutableStateOf<BackupImportSummary?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val jsonCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val payload = pendingJsonPayload
        pendingJsonPayload = null
        if (uri == null || payload == null) return@rememberLauncherForActivityResult
        try {
            val os = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Cannot open output stream")
            os.use { stream ->
                stream.write(payload.second.toByteArray(StandardCharsets.UTF_8))
                stream.flush()
            }
            storeViewModel.notifyBackupExportSaved(payload.first, isCsv = false)
        } catch (_: Exception) {
            storeViewModel.notifyBackupFileError("backup_err_write_file")
        }
    }

    val csvCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val payload = pendingCsvPayload
        pendingCsvPayload = null
        if (uri == null || payload == null) return@rememberLauncherForActivityResult
        try {
            val os = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Cannot open output stream")
            os.use { stream ->
                stream.write(payload.second)
                stream.flush()
            }
            storeViewModel.notifyBackupExportSaved(payload.first, isCsv = true)
        } catch (_: Exception) {
            storeViewModel.notifyBackupFileError("backup_err_write_file")
        }
    }

    val jsonOpenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open input stream")
            val rawText = input.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            storeViewModel.inspectImportJson(rawText) { preview ->
                pendingImportRawJson = rawText
                pendingImportPreview = preview
            }
        } catch (_: Exception) {
            storeViewModel.notifyBackupFileError("backup_err_read_file")
        }
    }

    fun requireBackupPin(action: () -> Unit) {
        if (!isOwner) return
        if (isBackupUnlocked) {
            action()
        } else {
            pendingPinUnlockedAction = action
            pinDialogMode = if (hasOwnerPin) BackupPinDialogMode.ENTER_PIN else BackupPinDialogMode.SET_PIN
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            storeViewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            storeViewModel.clearErrorMessage()
        }
    }

    val convertibleCount = remember(products, photoStorageMode) {
        storeViewModel.countConvertiblePhotosForMode(photoStorageMode)
    }

    fun handlePhotoStorageSelected(targetMode: PhotoStorageMode) {
        if (targetMode == photoStorageMode) return
        storeViewModel.setPhotoStorageMode(targetMode)
        val count = storeViewModel.countConvertiblePhotosForMode(targetMode)
        if (count > 0) {
            pendingConvertTargetMode = targetMode
            pendingConvertCount = count
        }
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070907))
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            VividGreen.copy(alpha = 0.20f),
                            VividGreen.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.55f, size.height * 0.16f),
                        radius = size.width * 0.90f
                    )
                )
            }
            .statusBarsPadding()
            .testTag("settings_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact Header matching Image 2
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("settings_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (currentLanguage == AppLanguage.BN) "ফিরে যান" else "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = if (currentLanguage == AppLanguage.BN) "সেটিংস" else "Settings",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("settings_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. APP MODE (Simple | Detail)
                item(key = "settings_app_mode") {
                    SettingsSectionCard(
                        title = if (currentLanguage == AppLanguage.BN) "অ্যাপ মোড" else "APP MODE",
                        accentGlow = true,
                        modifier = Modifier.testTag("settings_mode_card")
                    ) {
                        val trackShape = RoundedCornerShape(16.dp)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = trackShape,
                            color = Color(0xCC0D110D),
                            border = BorderStroke(1.dp, Color(0x28FFFFFF))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SettingsSegmentTab(
                                    label = if (currentLanguage == AppLanguage.BN) "সিম্পল" else "Simple",
                                    selected = isSimpleMode,
                                    onClick = { storeViewModel.setSimpleMode(true) },
                                    testTag = "settings_mode_simple",
                                    modifier = Modifier.weight(1f)
                                )
                                SettingsSegmentTab(
                                    label = if (currentLanguage == AppLanguage.BN) "বিস্তারিত" else "Detailed",
                                    selected = !isSimpleMode,
                                    onClick = { storeViewModel.setSimpleMode(false) },
                                    testTag = "settings_mode_detailed",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isSimpleMode) {
                                if (currentLanguage == AppLanguage.BN) {
                                    "সিম্পল: শুধু দ্রুত বিক্রি, স্টক ও বাকি হিসাব"
                                } else {
                                    "Simple: Core product fields, quick checkout & essentials only"
                                }
                            } else {
                                if (currentLanguage == AppLanguage.BN) {
                                    "বিস্তারিত: সব ফিল্ড, অ্যাকাউন্ট ও পূর্ণ রিপোর্ট"
                                } else {
                                    "Detailed: All product fields, accounts, analytics & full reports"
                                }
                            },
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                // 2 & 3. LANGUAGE & DISPLAY + CURRENCY & FORMAT (kept in one LazyColumn item so scrolling to display settings keeps regional rows composed)
                item(key = "settings_display_and_currency") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsSectionCard(
                            title = if (currentLanguage == AppLanguage.BN) "ভাষা ও প্রদর্শন" else "LANGUAGE & DISPLAY",
                            accentGlow = true
                        ) {
                            // Language: English | বাংলা
                            SettingsRowWithControl(
                                label = if (currentLanguage == AppLanguage.BN) "ভাষা" else "Language"
                            ) {
                                SettingsInlinePillGroup(
                                    options = listOf(
                                        Triple("English", currentLanguage == AppLanguage.EN, "settings_lang_en") to {
                                            storeViewModel.setLanguage(AppLanguage.EN)
                                        },
                                        Triple("বাংলা", currentLanguage == AppLanguage.BN, "settings_lang_bn") to {
                                            storeViewModel.setLanguage(AppLanguage.BN)
                                        }
                                    )
                                )
                            }

                            HorizontalDivider(color = Color(0x16FFFFFF), modifier = Modifier.padding(vertical = 8.dp))

                            // Stock Layout: List | Grid (persisted via DataStore)
                            SettingsRowWithControl(
                                label = if (currentLanguage == AppLanguage.BN) "স্টক ভিউ" else "Stock Layout",
                                modifier = Modifier
                                    .clickable { storeViewModel.toggleStockViewMode() }
                                    .testTag("settings_stock_view_row")
                            ) {
                                SettingsInlinePillGroup(
                                    options = listOf(
                                        Triple(
                                            if (currentLanguage == AppLanguage.BN) "তালিকা" else "List",
                                            !isStockGridView,
                                            "settings_stock_view_list"
                                        ) to { storeViewModel.setStockGridView(false) },
                                        Triple(
                                            if (currentLanguage == AppLanguage.BN) "গ্রিড" else "Grid",
                                            isStockGridView,
                                            "settings_stock_view_grid"
                                        ) to { storeViewModel.setStockGridView(true) }
                                    )
                                )
                            }

                            HorizontalDivider(color = Color(0x16FFFFFF), modifier = Modifier.padding(vertical = 8.dp))

                            // Font size (read-only system scale indicator, no fake toggles)
                            SettingsRowWithControl(
                                label = if (currentLanguage == AppLanguage.BN) "ফন্ট সাইজ" else "Font size",
                                modifier = Modifier.testTag("settings_font_size_row")
                            ) {
                                SettingsValueChip(
                                    text = if (currentLanguage == AppLanguage.BN) "সিস্টম ডিফল্ট" else "System Default"
                                )
                            }
                        }

                        // 3. CURRENCY & FORMAT (active working regional format only)
                        SettingsSectionCard(
                            title = if (currentLanguage == AppLanguage.BN) "মুদ্রা ও ফরম্যাট" else "CURRENCY & FORMAT"
                        ) {
                            SettingsRowWithControl(
                                label = if (currentLanguage == AppLanguage.BN) "মুদ্রা" else "Currency",
                                modifier = Modifier.testTag("settings_currency_row")
                            ) {
                                SettingsValueChip(
                                    text = "${ShopLocale.CURRENCY} Taka (BDT)",
                                    highlight = true
                                )
                            }

                            HorizontalDivider(color = Color(0x16FFFFFF), modifier = Modifier.padding(vertical = 8.dp))

                            SettingsRowWithControl(
                                label = if (currentLanguage == AppLanguage.BN) "তারিখের ধরন" else "Date format",
                                modifier = Modifier.testTag("settings_date_format_row")
                            ) {
                                SettingsValueChip(text = "dd MMM yyyy")
                            }
                        }
                    }
                }

                // 3b. ITEMS & STOCK (Photo storage setting)
                item(key = "settings_items_stock") {
                    SettingsSectionCard(
                        title = ShopLocale.get("items_and_stock_section", currentLanguage),
                        modifier = Modifier.testTag("settings_items_stock_card")
                    ) {
                        Text(
                            text = ShopLocale.get("photo_storage_label", currentLanguage),
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val trackShape = RoundedCornerShape(16.dp)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = trackShape,
                            color = Color(0xCC0D110D),
                            border = BorderStroke(1.dp, Color(0x28FFFFFF))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SettingsSegmentTab(
                                    label = ShopLocale.get("photo_storage_sync", currentLanguage),
                                    selected = photoStorageMode == PhotoStorageMode.SYNC,
                                    onClick = { handlePhotoStorageSelected(PhotoStorageMode.SYNC) },
                                    testTag = "settings_photo_sync_tab",
                                    modifier = Modifier.weight(1f)
                                )
                                SettingsSegmentTab(
                                    label = ShopLocale.get("photo_storage_phone_only", currentLanguage),
                                    selected = photoStorageMode == PhotoStorageMode.PHONE_ONLY,
                                    onClick = { handlePhotoStorageSelected(PhotoStorageMode.PHONE_ONLY) },
                                    testTag = "settings_photo_phone_only_tab",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = ShopLocale.get("photo_storage_desc_sync", currentLanguage),
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            lineHeight = 15.5.sp,
                            modifier = Modifier.testTag("settings_photo_desc_sync")
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = ShopLocale.get("photo_storage_desc_phone_only", currentLanguage),
                            color = if (photoStorageMode == PhotoStorageMode.PHONE_ONLY) Color(0xFFF3B23E) else TextSecondary,
                            fontSize = 11.5.sp,
                            lineHeight = 15.5.sp,
                            modifier = Modifier.testTag("settings_photo_desc_phone_only")
                        )

                        if (convertibleCount > 0) {
                            HorizontalDivider(color = Color(0x16FFFFFF), modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_photo_convert_row"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val bannerKey = if (photoStorageMode == PhotoStorageMode.PHONE_ONLY) {
                                    "photo_convert_banner_to_phone"
                                } else {
                                    "photo_convert_banner_to_sync"
                                }
                                Text(
                                    text = ShopLocale.format(bannerKey, currentLanguage, convertibleCount),
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )
                                Surface(
                                    onClick = {
                                        if (!isBusy) {
                                            storeViewModel.convertExistingPhotosToMode(photoStorageMode)
                                        }
                                    },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF1A2916),
                                    border = BorderStroke(1.dp, VividGreen.copy(alpha = 0.72f)),
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 36.dp)
                                        .testTag("settings_photo_convert_btn")
                                ) {
                                    Text(
                                        text = ShopLocale.get("photo_convert_action", currentLanguage),
                                        color = VividGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. PRIVACY (working Privacy Mode switch)
                item(key = "settings_privacy") {
                    SettingsSectionCard(
                        title = if (currentLanguage == AppLanguage.BN) "গোপনীয়তা" else "PRIVACY"
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { storeViewModel.togglePrivacyHidden() }
                                .padding(vertical = 2.dp)
                                .testTag("settings_privacy_mode_row"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (currentLanguage == AppLanguage.BN) "প্রাইভেসি মোড" else "Privacy Mode",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (currentLanguage == AppLanguage.BN) {
                                        "হোম, স্টক ও ইতিহাসে টাকার অঙ্ক লুকিয়ে রাখুন"
                                    } else {
                                        "Hide financial amounts across Home, Stock & History"
                                    },
                                    color = TextMuted,
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = isPrivacyHidden,
                                onCheckedChange = { storeViewModel.togglePrivacyHidden() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = VividGreen,
                                    uncheckedThumbColor = Color(0xFFE0E0E0),
                                    uncheckedTrackColor = Color(0xFF2B2F2B),
                                    uncheckedBorderColor = Color(0x38FFFFFF)
                                ),
                                modifier = Modifier.testTag("settings_privacy_mode_switch")
                            )
                        }
                    }
                }

                // 5. BUSINESS SETTINGS (Store identity + working screen shortcuts)
                item(key = "settings_business") {
                    SettingsSectionCard(
                        title = if (currentLanguage == AppLanguage.BN) "ব্যবসা সেটিংস" else "BUSINESS SETTINGS"
                    ) {
                        val roleLabel = if (isOwner) {
                            ShopLocale.get("owner_badge", currentLanguage)
                        } else {
                            currentMember?.roleLabel?.ifBlank { "Staff" } ?: "Staff"
                        }
                        val storeSubtitle = listOfNotNull(
                            roleLabel,
                            userProfile?.email?.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Storefront,
                                contentDescription = null,
                                tint = VividGreen,
                                modifier = Modifier.size(19.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = store?.name?.ifBlank { "StoreOS" } ?: "StoreOS",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = storeSubtitle,
                                    color = TextSecondary,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        val canEditShopType = isOwner || authUiState is AuthUiState.DemoMode
                        HorizontalDivider(color = Color(0x16FFFFFF), modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = canEditShopType) { showShopTypeDialog = true }
                                .padding(vertical = 4.dp)
                                .testTag("settings_shop_type_row"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = shopType?.let { shopTypeIcon(it) } ?: Icons.Outlined.Category,
                                    contentDescription = null,
                                    tint = VividGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = ShopLocale.get("shop_type_setting", currentLanguage),
                                    color = TextPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            SettingsValueChip(
                                text = shopType?.let { ShopTypeCatalog.displayName(it, currentLanguage) }
                                    ?: ShopLocale.get("shop_type_not_set", currentLanguage),
                                highlight = shopType != null
                            )
                        }

                        if (onNavigateDestination != null && canExpenses && !isSimpleMode) {
                            HorizontalDivider(color = Color(0x16FFFFFF), modifier = Modifier.padding(vertical = 8.dp))
                            SettingsNavigationRow(
                                icon = Icons.Outlined.Category,
                                label = if (currentLanguage == AppLanguage.BN) "ক্যাটেগরি পরিচালনা" else "Manage Categories",
                                testTag = "settings_nav_categories",
                                onClick = { onNavigateDestination(BusinessNavDestination.MANAGE_CATEGORIES_SCREEN) }
                            )
                        }

                        if (onNavigateDestination != null && isOwner) {
                            HorizontalDivider(color = Color(0x16FFFFFF), modifier = Modifier.padding(vertical = 8.dp))
                            SettingsNavigationRow(
                                icon = Icons.Outlined.Badge,
                                label = if (currentLanguage == AppLanguage.BN) "কর্মচারী ও অনুমতি" else "Staff & Permissions",
                                testTag = "settings_nav_staff",
                                onClick = { onNavigateDestination(BusinessNavDestination.STAFF_MANAGEMENT_SCREEN) }
                            )
                        }
                    }
                }

                // 6. BACKUP & CLOUD (real Firebase / Demo connection status only)
                item(key = "settings_backup_cloud") {
                    SettingsSectionCard(
                        title = if (currentLanguage == AppLanguage.BN) "ব্যাকআপ ও ক্লাউড" else "BACKUP & CLOUD"
                    ) {
                        val isDemo = authUiState is AuthUiState.DemoMode
                        val (dotColor, statusTitle, statusSub) = when {
                            isDemo -> Triple(
                                VividGreen,
                                if (currentLanguage == AppLanguage.BN) "ডেমো মোড · লোকাল স্টোর" else "Demo Mode · Local Store",
                                if (currentLanguage == AppLanguage.BN) {
                                    "লাইভ ক্লাউড সিঙ্ক পেতে গুগল দিয়ে সাইন ইন করুন"
                                } else {
                                    "Sign in with Google for live multi-device Firebase sync"
                                }
                            )
                            syncStatus == SyncStatus.ONLINE -> Triple(
                                VividGreen,
                                if (currentLanguage == AppLanguage.BN) "ফায়ারবেস সংযুক্ত" else "Firebase Connected",
                                if (currentLanguage == AppLanguage.BN) {
                                    "সকল তথ্য রিয়েল-টাইমে ক্লাউডে সংরক্ষিত হচ্ছে"
                                } else {
                                    "Real-time cloud sync active across your devices"
                                }
                            )
                            syncStatus == SyncStatus.SYNCING -> Triple(
                                Color(0xFFF3B23E),
                                if (currentLanguage == AppLanguage.BN) "ফায়ারবেস সিঙ্ক হচ্ছে..." else "Syncing with Firebase...",
                                if (currentLanguage == AppLanguage.BN) {
                                    "পরিবর্তনগুলো ক্লাউডে আপলোড হচ্ছে"
                                } else {
                                    "Uploading recent changes to cloud"
                                }
                            )
                            else -> Triple(
                                AlertRed,
                                if (currentLanguage == AppLanguage.BN) "অফলাইন · লোকাল ক্যাশ" else "Offline · Local Cache",
                                if (currentLanguage == AppLanguage.BN) {
                                    "ইন্টারনেট সংযোগ ফিরলে স্বয়ংক্রিয়ভাবে সিঙ্ক হবে"
                                } else {
                                    "Changes saved locally and will sync when back online"
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Text(
                                text = statusTitle,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = statusSub,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        HorizontalDivider(color = Color(0x16FFFFFF), modifier = Modifier.padding(vertical = 10.dp))

                        if (!isOwner) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 40.dp)
                                    .testTag("settings_backup_staff_locked"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = ShopLocale.get("backup_owner_only_hint", currentLanguage),
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 44.dp)
                                    .testTag("settings_backup_pin_row"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isBackupUnlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = if (isBackupUnlocked) VividGreen else Color(0xFFF3B23E),
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Text(
                                        text = ShopLocale.get(
                                            if (isBackupUnlocked) "backup_unlocked_hint" else "backup_locked_hint",
                                            currentLanguage
                                        ),
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!isBackupUnlocked) {
                                        SettingsActionChip(
                                            text = ShopLocale.get(
                                                if (hasOwnerPin) "backup_unlock_btn" else "backup_set_pin_btn",
                                                currentLanguage
                                            ),
                                            highlight = true,
                                            testTag = "settings_backup_pin_action_btn",
                                            onClick = {
                                                pendingPinUnlockedAction = null
                                                pinDialogMode = if (hasOwnerPin) {
                                                    BackupPinDialogMode.ENTER_PIN
                                                } else {
                                                    BackupPinDialogMode.SET_PIN
                                                }
                                            }
                                        )
                                    } else {
                                        SettingsActionChip(
                                            text = ShopLocale.get("backup_change_pin_btn", currentLanguage),
                                            highlight = false,
                                            testTag = "settings_backup_change_pin_btn",
                                            onClick = {
                                                pendingPinUnlockedAction = null
                                                pinDialogMode = BackupPinDialogMode.CHANGE_PIN
                                            }
                                        )
                                        SettingsActionChip(
                                            text = ShopLocale.get("backup_lock_btn", currentLanguage),
                                            highlight = false,
                                            testTag = "settings_backup_lock_btn",
                                            onClick = { storeViewModel.lockBackup() }
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0x16FFFFFF), modifier = Modifier.padding(vertical = 8.dp))

                            SettingsNavigationRow(
                                icon = Icons.Outlined.Download,
                                label = ShopLocale.get("backup_export_json_btn", currentLanguage),
                                testTag = "settings_backup_export_json_btn",
                                onClick = {
                                    requireBackupPin {
                                        storeViewModel.prepareBackupJsonForExport { fileName, json ->
                                            pendingJsonPayload = fileName to json
                                            jsonCreateLauncher.launch(fileName)
                                        }
                                    }
                                }
                            )

                            HorizontalDivider(color = Color(0x16FFFFFF), modifier = Modifier.padding(vertical = 6.dp))

                            SettingsNavigationRow(
                                icon = Icons.Outlined.TableChart,
                                label = ShopLocale.get("backup_export_csv_btn", currentLanguage),
                                testTag = "settings_backup_export_csv_btn",
                                onClick = {
                                    requireBackupPin {
                                        showCsvTargetDialog = true
                                    }
                                }
                            )

                            HorizontalDivider(color = Color(0x16FFFFFF), modifier = Modifier.padding(vertical = 6.dp))

                            SettingsNavigationRow(
                                icon = Icons.Outlined.UploadFile,
                                label = ShopLocale.get("backup_import_json_btn", currentLanguage),
                                testTag = "settings_backup_import_json_btn",
                                onClick = {
                                    requireBackupPin {
                                        jsonOpenLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                    }
                                }
                            )
                        }
                    }
                }

                // 7. DANGER ZONE (Log Out)
                item(key = "settings_danger_zone") {
                    val cardShape = RoundedCornerShape(18.dp)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShape,
                        color = Color.Transparent,
                        border = BorderStroke(
                            width = 1.dp,
                            color = AlertRed.copy(alpha = 0.55f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF281212),
                                            Color(0xFF170D0D)
                                        )
                                    )
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { dangerExpanded = !dangerExpanded }
                                    .padding(vertical = 4.dp)
                                    .testTag("settings_danger_zone_toggle"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (currentLanguage == AppLanguage.BN) "ডেঞ্জার জোন" else "Danger Zone",
                                    color = AlertRed,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Icon(
                                    imageVector = if (dangerExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (dangerExpanded) "Collapse" else "Expand",
                                    tint = AlertRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            AnimatedVisibility(visible = dangerExpanded) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable(onClick = onLogout)
                                            .testTag("settings_logout_btn"),
                                        shape = RoundedCornerShape(14.dp),
                                        color = AlertRed,
                                        border = BorderStroke(1.dp, Color(0x40FFFFFF))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = ShopLocale.get("log_out", currentLanguage),
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showShopTypeDialog) {
            SettingsShopTypeDialog(
                currentShopType = shopType,
                currentLanguage = currentLanguage,
                isBusy = isBusy,
                onConfirmChange = { newType, addSuggestions ->
                    storeViewModel.updateStoreShopType(
                        newType = newType,
                        addSuggestedCategories = addSuggestions,
                        onSuccess = { showShopTypeDialog = false }
                    )
                },
                onDismiss = { showShopTypeDialog = false }
            )
        }

        val convertTarget = pendingConvertTargetMode
        if (convertTarget != null) {
            val titleKey = if (convertTarget == PhotoStorageMode.PHONE_ONLY) {
                "photo_convert_to_phone_title"
            } else {
                "photo_convert_to_sync_title"
            }
            val bodyKey = if (convertTarget == PhotoStorageMode.PHONE_ONLY) {
                "photo_convert_to_phone_body"
            } else {
                "photo_convert_to_sync_body"
            }
            AlertDialog(
                onDismissRequest = { pendingConvertTargetMode = null },
                containerColor = Color(0xFF171B17),
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
                modifier = Modifier.testTag("settings_photo_convert_dialog"),
                title = {
                    Text(
                        text = ShopLocale.get(titleKey, currentLanguage),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                text = {
                    Text(
                        text = ShopLocale.format(bodyKey, currentLanguage, pendingConvertCount),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingConvertTargetMode = null
                            storeViewModel.convertExistingPhotosToMode(convertTarget)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = VividGreen),
                        modifier = Modifier.testTag("settings_photo_convert_confirm_btn")
                    ) {
                        Text(
                            text = ShopLocale.get("photo_convert_action", currentLanguage),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { pendingConvertTargetMode = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
                        modifier = Modifier.testTag("settings_photo_convert_dismiss_btn")
                    ) {
                        Text(
                            text = ShopLocale.get("photo_keep_as_is", currentLanguage),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            )
        }

        val activePinMode = pinDialogMode
        if (activePinMode != null && isOwner) {
            BackupPinDialog(
                mode = activePinMode,
                currentLanguage = currentLanguage,
                onSetPin = { pin, _ ->
                    storeViewModel.setOwnerPin(pin) {
                        val followUp = pendingPinUnlockedAction
                        pendingPinUnlockedAction = null
                        pinDialogMode = null
                        followUp?.invoke()
                    }
                },
                onVerifyPin = { pin, onResult ->
                    storeViewModel.verifyOwnerPin(pin) { ok ->
                        onResult(ok)
                        if (ok) {
                            val followUp = pendingPinUnlockedAction
                            pendingPinUnlockedAction = null
                            pinDialogMode = null
                            followUp?.invoke()
                        }
                    }
                },
                onChangePin = { oldPin, newPin, onResult ->
                    storeViewModel.changeOwnerPin(oldPin, newPin) { ok ->
                        onResult(ok)
                        if (ok) {
                            pendingPinUnlockedAction = null
                            pinDialogMode = null
                        }
                    }
                },
                onDismiss = {
                    pendingPinUnlockedAction = null
                    pinDialogMode = null
                }
            )
        }

        if (showCsvTargetDialog && isOwner && isBackupUnlocked) {
            CsvExportTargetDialog(
                currentLanguage = currentLanguage,
                isBusy = isBusy,
                onSelectTarget = { target ->
                    showCsvTargetDialog = false
                    storeViewModel.prepareCsvForExport(target) { fileName, bytes ->
                        pendingCsvPayload = fileName to bytes
                        csvCreateLauncher.launch(fileName)
                    }
                },
                onDismiss = { showCsvTargetDialog = false }
            )
        }

        val activePreview = pendingImportPreview
        if (activePreview != null && isOwner && isBackupUnlocked && pendingImportMode == null) {
            BackupImportPreviewDialog(
                preview = activePreview,
                currentLanguage = currentLanguage,
                onChooseMode = { chosenMode ->
                    pendingImportMode = chosenMode
                },
                onDismiss = {
                    pendingImportPreview = null
                    pendingImportRawJson = null
                }
            )
        }

        val activeConfirmMode = pendingImportMode
        if (activePreview != null && activeConfirmMode != null && isOwner && isBackupUnlocked) {
            BackupImportConfirmDialog(
                preview = activePreview,
                mode = activeConfirmMode,
                currentLanguage = currentLanguage,
                isBusy = isBusy,
                onConfirm = {
                    val rawJson = pendingImportRawJson ?: return@BackupImportConfirmDialog
                    storeViewModel.confirmAndImportBackup(rawJson, activeConfirmMode) { summary ->
                        pendingImportMode = null
                        pendingImportPreview = null
                        pendingImportRawJson = null
                        importSummary = summary
                    }
                },
                onDismiss = {
                    pendingImportMode = null
                }
            )
        }

        val activeSummary = importSummary
        if (activeSummary != null) {
            BackupImportSummaryDialog(
                summary = activeSummary,
                currentLanguage = currentLanguage,
                onDone = { importSummary = null }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Color(0xFF1D241D),
                contentColor = TextPrimary,
                actionColor = VividGreen
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    accentGlow: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val cardShape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = if (accentGlow) {
                    listOf(VividGreen.copy(alpha = 0.65f), Color(0x28FFFFFF))
                } else {
                    listOf(VividGreen.copy(alpha = 0.36f), Color(0x22FFFFFF))
                }
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (accentGlow) {
                            listOf(Color(0xEB1D2B19), Color(0xEC131613))
                        } else {
                            listOf(Color(0xEB1A1D1A), Color(0xEC121412))
                        }
                    )
                )
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.1.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSegmentTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(13.dp)
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 38.dp)
            .clip(shape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = shape,
        color = if (selected) Color(0xFF282E28) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, VividGreen.copy(alpha = 0.65f)) else null
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                color = if (selected) TextPrimary else TextSecondary,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsRowWithControl(
    label: String,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        )
        control()
    }
}

@Composable
private fun SettingsInlinePillGroup(
    options: List<Pair<Triple<String, Boolean, String>, () -> Unit>>
) {
    val trackShape = RoundedCornerShape(14.dp)
    Surface(
        shape = trackShape,
        color = Color(0xCC0D100D),
        border = BorderStroke(1.dp, Color(0x28FFFFFF))
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            options.forEach { (meta, onClick) ->
                val (label, selected, tag) = meta
                val pillShape = RoundedCornerShape(11.dp)
                Surface(
                    modifier = Modifier
                        .clip(pillShape)
                        .clickable(onClick = onClick)
                        .testTag(tag),
                    shape = pillShape,
                    color = if (selected) Color(0xFF252B25) else Color.Transparent,
                    border = if (selected) BorderStroke(1.dp, VividGreen.copy(alpha = 0.65f)) else null
                ) {
                    Text(
                        text = label,
                        color = if (selected) TextPrimary else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsValueChip(
    text: String,
    highlight: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (highlight) Color(0xFF1A2916) else Color(0xFF1F221F),
        border = BorderStroke(
            width = 1.dp,
            color = if (highlight) VividGreen.copy(alpha = 0.72f) else Color(0x2EFFFFFF)
        )
    ) {
        Text(
            text = text,
            color = if (highlight) TextPrimary else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SettingsActionChip(
    text: String,
    highlight: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(shape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = shape,
        color = if (highlight) Color(0xFF1A2916) else Color(0xFF1F221F),
        border = BorderStroke(
            width = 1.dp,
            color = if (highlight) VividGreen.copy(alpha = 0.72f) else Color(0x2EFFFFFF)
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                text = text,
                color = if (highlight) VividGreen else TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}
