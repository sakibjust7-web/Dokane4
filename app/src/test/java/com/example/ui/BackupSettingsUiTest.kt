package com.example.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.BackupImportMode
import com.example.data.model.BackupImportPreview
import com.example.data.model.BackupImportSummary
import com.example.data.model.CsvExportTarget
import com.example.data.repository.StoreRepository
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.StoreOSTheme
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.StoreViewModel
import com.example.util.StoreBackupCodec
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupSettingsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var repository: StoreRepository
    private lateinit var authViewModel: AuthViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "datastore").deleteRecursively()
        File(context.filesDir, "auto_backups").deleteRecursively()
        repository = StoreRepository(db = null, appContext = context)
        authViewModel = AuthViewModel(repository).apply { launchDemoMode() }
    }

    private fun waitForCondition(
        vm: StoreViewModel,
        timeoutMs: Long = 5000L,
        condition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError(
                    "Timed out: isBusy=${vm.isBusy.value}, error=${vm.errorMessage.value}, status=${vm.statusMessage.value}"
                )
            }
            Thread.sleep(25L)
            composeRule.waitForIdle()
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp-xxhdpi")
    fun staffUser_cannotSeeOrInvokeBackupControls_seesOwnerOnlyNotice() {
        val staffVm = StoreViewModel(
            storeId = "demo_store",
            repository = repository,
            networkMonitor = null,
            currentUserId = "staff_cashier_1"
        )

        composeRule.setContent {
            StoreOSTheme {
                SettingsScreen(
                    authViewModel = authViewModel,
                    storeViewModel = staffVm,
                    onBack = {},
                    onLogout = {}
                )
            }
        }
        composeRule.waitForIdle()
        waitForCondition(staffVm) { staffVm.store.value != null }
        assertFalse(staffVm.isOwner.value)

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_backup_staff_locked"))
        composeRule.onNodeWithTag("settings_backup_staff_locked").assertIsDisplayed()
        composeRule.onNodeWithText("Owner only", substring = true).assertIsDisplayed()

        assertEquals(0, composeRule.onAllNodesWithTag("settings_backup_export_json_btn").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("settings_backup_export_csv_btn").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("settings_backup_import_json_btn").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("settings_backup_pin_row").fetchSemanticsNodes().size)

        // Direct ViewModel call also blocked for staff
        var exportTriggered = false
        composeRule.runOnUiThread {
            staffVm.prepareBackupJsonForExport { _, _ -> exportTriggered = true }
        }
        composeRule.waitForIdle()
        assertFalse(exportTriggered)
        assertTrue(staffVm.errorMessage.value?.contains("owner", ignoreCase = true) == true)
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp-xxhdpi")
    fun ownerPinGate_blocksExportUntilPinSetAndVerified_andExportsAndMergesBackup() {
        val ownerVm = StoreViewModel(
            storeId = "demo_store",
            repository = repository,
            networkMonitor = null,
            currentUserId = "demo_user"
        )

        composeRule.setContent {
            StoreOSTheme {
                SettingsScreen(
                    authViewModel = authViewModel,
                    storeViewModel = ownerVm,
                    onBack = {},
                    onLogout = {}
                )
            }
        }
        composeRule.waitForIdle()
        waitForCondition(ownerVm) { ownerVm.store.value != null && ownerVm.products.value.isNotEmpty() }
        assertTrue(ownerVm.isOwner.value)
        assertFalse(ownerVm.isBackupUnlocked.value)

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_backup_import_json_btn"))
        composeRule.onNodeWithTag("settings_backup_pin_row").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_backup_export_json_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_backup_export_csv_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_backup_import_json_btn").assertIsDisplayed()

        // Tapping CSV export while locked opens Set Owner PIN dialog
        composeRule.onNodeWithTag("settings_backup_export_csv_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("backup_pin_dialog").assertIsDisplayed()

        composeRule.onNodeWithTag("backup_pin_input").performTextReplacement("2580")
        composeRule.onNodeWithTag("backup_pin_confirm_input").performTextReplacement("2580")
        composeRule.onNodeWithTag("backup_pin_confirm_btn").performClick()

        waitForCondition(ownerVm) { ownerVm.isBackupUnlocked.value && ownerVm.hasOwnerPin.value }
        composeRule.waitForIdle()
        // Queued CSV target picker opens automatically after unlocking
        composeRule.onNodeWithTag("backup_csv_target_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("csv_export_cancel_btn").performClick()
        composeRule.waitForIdle()

        // Export JSON & CSV bytes directly from unlocked ViewModel
        var exportedName: String? = null
        var exportedJson: String? = null
        composeRule.runOnUiThread {
            ownerVm.prepareBackupJsonForExport { name, json ->
                exportedName = name
                exportedJson = json
            }
        }
        waitForCondition(ownerVm) { exportedJson != null }
        assertTrue(exportedName!!.endsWith(".json"))

        var csvBytes: ByteArray? = null
        composeRule.runOnUiThread {
            ownerVm.prepareCsvForExport(CsvExportTarget.CUSTOMERS) { _, bytes ->
                csvBytes = bytes
            }
        }
        waitForCondition(ownerVm) { csvBytes != null }
        assertTrue(String(csvBytes!!, Charsets.UTF_8).contains("Mohammad Rahim"))

        // Lock again -> wrong PIN rejected, valid PIN unlocks
        composeRule.onNodeWithTag("settings_backup_lock_btn").performClick()
        composeRule.waitForIdle()
        assertFalse(ownerVm.isBackupUnlocked.value)

        composeRule.onNodeWithTag("settings_backup_pin_action_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("backup_pin_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("backup_pin_input").performTextReplacement("9999")
        composeRule.onNodeWithTag("backup_pin_confirm_btn").performClick()
        waitForCondition(ownerVm) { ownerVm.errorMessage.value != null }
        composeRule.onNodeWithTag("backup_pin_error_text").assertIsDisplayed()
        assertFalse(ownerVm.isBackupUnlocked.value)

        composeRule.onNodeWithTag("backup_pin_input").performTextReplacement("2580")
        composeRule.onNodeWithTag("backup_pin_confirm_btn").performClick()
        waitForCondition(ownerVm) { ownerVm.isBackupUnlocked.value }

        // Preview & Merge check via StoreViewModel
        val parsed = StoreBackupCodec.decodeAndValidateJson(exportedJson!!)
        var preview: BackupImportPreview? = null
        composeRule.runOnUiThread {
            ownerVm.inspectImportJson(exportedJson!!) { p -> preview = p }
        }
        waitForCondition(ownerVm) { preview != null }
        assertNotNull(preview)
        assertFalse(preview!!.targetStoreIsEmpty)
        assertEquals(parsed.products.size, preview!!.productsCount)

        var mergeSummary: BackupImportSummary? = null
        composeRule.runOnUiThread {
            ownerVm.confirmAndImportBackup(exportedJson!!, BackupImportMode.MERGE) { s -> mergeSummary = s }
        }
        waitForCondition(ownerVm) { mergeSummary != null }
        assertEquals(0, mergeSummary!!.productsAdded)
        assertEquals(parsed.products.size, mergeSummary!!.productsSkipped)
        assertTrue(File(mergeSummary!!.autoBackupPath).exists())
    }
}
