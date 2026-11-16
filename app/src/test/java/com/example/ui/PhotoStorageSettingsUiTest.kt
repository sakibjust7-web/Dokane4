package com.example.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Product
import com.example.data.repository.StoreRepository
import com.example.ui.components.ProductThumbnail
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.StoreOSTheme
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.StoreViewModel
import com.example.util.PhotoStorageMode
import com.example.util.ProductImageUtil
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoStorageSettingsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var repository: StoreRepository
    private lateinit var authViewModel: AuthViewModel
    private lateinit var storeViewModel: StoreViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "datastore").deleteRecursively()
        File(context.filesDir, "product_photos").deleteRecursively()
        repository = StoreRepository(db = null, appContext = context)
        authViewModel = AuthViewModel(repository).apply { launchDemoMode() }
        storeViewModel = StoreViewModel(
            storeId = "demo_store",
            repository = repository,
            networkMonitor = null,
            currentUserId = "demo_user"
        )
    }

    private fun waitForCondition(timeoutMs: Long = 5000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError(
                    "Timed out: isBusy=${storeViewModel.isBusy.value}, " +
                        "error=${storeViewModel.errorMessage.value}, " +
                        "status=${storeViewModel.statusMessage.value}, " +
                        "products=${storeViewModel.products.value.map { it.name to it.photoUri }}"
                )
            }
            Thread.sleep(25L)
            composeRule.waitForIdle()
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp-xxhdpi")
    fun settingsItemsAndStock_showsPhotoStorageOptions_explanations_andConvertsWithoutDeleting() {
        val tempPhoto = File(context.cacheDir, "ui_test_sample.jpg")
        val bmp = Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.BLUE)
        FileOutputStream(tempPhoto).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()

        composeRule.setContent {
            StoreOSTheme {
                SettingsScreen(
                    authViewModel = authViewModel,
                    storeViewModel = storeViewModel,
                    onBack = {},
                    onLogout = {}
                )
            }
        }
        composeRule.waitForIdle()
        waitForCondition {
            storeViewModel.products.value.isNotEmpty()
        }

        // Add a product with a large photo in default SYNC mode
        var added = false
        composeRule.runOnUiThread {
            storeViewModel.addNewProductWizard(
                name = "Test Soap Bar",
                nameBn = "",
                category = "Grocery",
                unit = "pcs",
                buyPrice = 30.0,
                sellPrice = 45.0,
                stock = 12.0,
                lowStockLimit = 3.0,
                photoUri = Uri.fromFile(tempPhoto).toString(),
                onSuccess = { added = true }
            )
        }
        waitForCondition {
            added && storeViewModel.products.value.any { it.name == "Test Soap Bar" }
        }
        val addedProduct = storeViewModel.products.value.first { it.name == "Test Soap Bar" }
        assertTrue(ProductImageUtil.isSyncDataUri(addedProduct.photoUri))

        // Default mode is Sync photos
        assertEquals(PhotoStorageMode.SYNC, storeViewModel.photoStorageMode.value)

        // Scroll to Items & Stock card and check single-line labels & two short explanation lines
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_items_stock_card"))
        composeRule.onNodeWithText("ITEMS & STOCK").assertIsDisplayed()
        composeRule.onNodeWithText("Photo storage").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_photo_sync_tab").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_photo_phone_only_tab").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_photo_desc_sync").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_photo_desc_phone_only").assertIsDisplayed()
        composeRule.onNodeWithText("reinstalled", substring = true).assertIsDisplayed()

        // Switch to "This phone only" -> does not delete photo, offers conversion dialog
        composeRule.onNodeWithTag("settings_photo_phone_only_tab").performClick()
        composeRule.waitForIdle()

        assertEquals(PhotoStorageMode.PHONE_ONLY, storeViewModel.photoStorageMode.value)
        val beforeConvert = storeViewModel.products.value.first { it.id == addedProduct.id }
        assertTrue(
            "Switching mode alone must never delete existing photo",
            beforeConvert.photoUri.isNotBlank()
        )
        composeRule.onNodeWithTag("settings_photo_convert_dialog").assertIsDisplayed()

        // Confirm conversion -> moves JPEG to local file and updates product photoUri to flag
        composeRule.onNodeWithTag("settings_photo_convert_confirm_btn").performClick()
        waitForCondition {
            storeViewModel.products.value.firstOrNull { it.id == addedProduct.id }?.photoUri == ProductImageUtil.LOCAL_ONLY_FLAG
        }
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag("settings_photo_convert_dialog").fetchSemanticsNodes().size)
        assertNotNull(ProductImageUtil.findLocalOnlyPhotoFile(context, "demo_store", addedProduct.id))
        tempPhoto.delete()
    }

    @Test
    fun productThumbnail_inPhoneOnlyMode_showsPhotoOnOwnerPhone_andCategoryIconOnOtherPhone() {
        val sampleBytes = ByteArray(100) { 1 }
        // Create valid JPEG bytes for Phone 1
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.GREEN)
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        bmp.recycle()
        val validJpeg = out.toByteArray()

        ProductImageUtil.saveLocalOnlyPhotoBytes(context, "store_x", "prod_x", validJpeg)

        // Phone 1 sees local file
        assertNotNull(ProductImageUtil.toCoilImageModel(context, ProductImageUtil.LOCAL_ONLY_FLAG, "store_x", "prod_x"))

        // Second phone with isolated empty filesDir sees null and falls back to category icon in ProductThumbnail
        val phone2Dir = File(context.cacheDir, "ui_phone2").apply {
            deleteRecursively()
            mkdirs()
        }
        val phone2Context = object : ContextWrapper(context) {
            override fun getFilesDir(): File = phone2Dir
            override fun getApplicationContext(): Context = this
        }
        assertNull(ProductImageUtil.toCoilImageModel(phone2Context, ProductImageUtil.LOCAL_ONLY_FLAG, "store_x", "prod_x"))

        composeRule.setContent {
            StoreOSTheme {
                CompositionLocalProvider(LocalContext provides phone2Context) {
                    ProductThumbnail(
                        photoUri = ProductImageUtil.LOCAL_ONLY_FLAG,
                        category = "Grocery",
                        contentDescription = "Category Icon Fallback Item",
                        storeId = "store_x",
                        productId = "prod_x",
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // When no photo exists on Phone 2, the fallback Icon owns contentDescription = "Category Icon Fallback Item"
        composeRule.onNodeWithContentDescription("Category Icon Fallback Item").assertIsDisplayed()
        assertTrue(sampleBytes.isNotEmpty())
        ProductImageUtil.deleteLocalOnlyPhoto(context, "store_x", "prod_x")
    }
}
