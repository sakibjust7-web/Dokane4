package com.example.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Product
import com.example.util.PhotoStorageMode
import com.example.util.ProductImageUtil
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductPhotoStorageRuleTest : FirestoreEmulatorTestBase() {

    private fun createLargeBitmapFile(fileName: String, width: Int = 1200, height: Int = 900): File {
        val dir = File(appContext.cacheDir, "test_inputs").apply { mkdirs() }
        val file = File(dir, fileName)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(46, 139, 87))
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        bitmap.recycle()
        return file
    }

    private fun createIsolatedSecondPhoneContext(name: String): Context {
        val isolatedRoot = File(appContext.cacheDir, "second_phone_$name").apply {
            deleteRecursively()
            mkdirs()
        }
        val isolatedFilesDir = File(isolatedRoot, "files").apply { mkdirs() }
        val isolatedCacheDir = File(isolatedRoot, "cache").apply { mkdirs() }
        return object : ContextWrapper(appContext) {
            override fun getFilesDir(): File = isolatedFilesDir
            override fun getCacheDir(): File = isolatedCacheDir
            override fun getApplicationContext(): Context = this
        }
    }

    @Test
    fun syncMode_addsCompressedJpegVisibleOnSecondPhone_thenSwitchToPhoneOnly_showsIconOnSecondPhone() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val phone1Repo = StoreRepository(firestore, appContext)
        val storeId = withTimeout(DEFAULT_TIMEOUT_MS) {
            phone1Repo.createStore("Photo Sync Store", "01777777777", "Dhaka")
        }

        // Clean any leftover local files on Phone 1
        File(appContext.filesDir, "product_photos").deleteRecursively()

        // 1. Add product with large photo in default SYNC mode
        val largePhotoFile = createLargeBitmapFile("large_input_sync.jpg", width = 1200, height = 900)
        val outcome = withTimeout(DEFAULT_TIMEOUT_MS) {
            phone1Repo.addProduct(
                storeId = storeId,
                product = Product(
                    name = "Sylhet Green Tea",
                    category = "Tea & Coffee",
                    unit = "pack",
                    buyPrice = 120.0,
                    sellPrice = 160.0,
                    stock = 15.0,
                    photoUri = Uri.fromFile(largePhotoFile).toString()
                ),
                photoStorageMode = PhotoStorageMode.SYNC
            )
        }

        assertFalse("Expected sync photo save to succeed", outcome.photoFailed)
        assertNull(outcome.photoWarning)
        assertTrue(
            "Sync mode must store JPEG data URI inside Firestore product record",
            ProductImageUtil.isSyncDataUri(outcome.photoUri)
        )
        val decodedBytes = ProductImageUtil.decodeDataUriBytes(outcome.photoUri)
        assertNotNull(decodedBytes)
        assertTrue(
            "Compressed JPEG must be <= 30 KB, was ${decodedBytes!!.size}",
            decodedBytes.size <= ProductImageUtil.SYNC_MAX_BYTES
        )
        val decodedBmp = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        assertNotNull(decodedBmp)
        assertTrue(
            "Compressed dimensions must be <= 400px, got ${decodedBmp.width}x${decodedBmp.height}",
            max(decodedBmp.width, decodedBmp.height) <= ProductImageUtil.SYNC_MAX_DIMENSION_PX
        )
        decodedBmp.recycle()

        // Confirm second phone (clean device with empty filesDir) reads synced photo from Firestore
        val phone2Context = createIsolatedSecondPhoneContext("p2_sync_check")
        val phone2Repo = StoreRepository(firestore, phone2Context)
        val productsOnPhone2 = withTimeout(FLOW_TIMEOUT_MS) {
            phone2Repo.observeProducts(storeId).first { list -> list.any { it.id == outcome.productId } }
        }
        val syncedOnPhone2 = productsOnPhone2.first { it.id == outcome.productId }
        val phone2SyncModel = ProductImageUtil.toCoilImageModel(
            context = phone2Context,
            photoUri = syncedOnPhone2.photoUri,
            storeId = syncedOnPhone2.storeId,
            productId = syncedOnPhone2.id
        )
        assertTrue(
            "Second phone must receive ByteArray image model from synced Firestore JPEG",
            phone2SyncModel is ByteArray && phone2SyncModel.isNotEmpty()
        )

        // 2. Switch Phone 1 to THIS_PHONE_ONLY and convert existing synced photo
        val convertResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            phone1Repo.convertExistingProductPhotos(
                storeId = storeId,
                targetMode = PhotoStorageMode.PHONE_ONLY,
                currentProducts = productsOnPhone2
            )
        }
        assertEquals(1, convertResult.convertedCount)
        assertEquals(0, convertResult.failedCount)

        // Verify Firestore now holds only the local_only flag
        val updatedOnPhone2 = withTimeout(FLOW_TIMEOUT_MS) {
            phone2Repo.observeProducts(storeId).first { list ->
                list.any { it.id == outcome.productId && ProductImageUtil.isLocalOnlyFlag(it.photoUri) }
            }
        }.first { it.id == outcome.productId }
        assertEquals(ProductImageUtil.LOCAL_ONLY_FLAG, updatedOnPhone2.photoUri)

        // Phone 1 STILL resolves the photo locally
        val phone1LocalModel = ProductImageUtil.toCoilImageModel(
            context = appContext,
            photoUri = updatedOnPhone2.photoUri,
            storeId = updatedOnPhone2.storeId,
            productId = updatedOnPhone2.id
        )
        assertTrue(
            "Phone 1 must still show the local photo after converting to This phone only",
            phone1LocalModel is File && phone1LocalModel.exists() && phone1LocalModel.length() > 0L
        )

        // Phone 2 has NO local copy -> resolves null so UI displays category icon
        val phone2AfterSwitchModel = ProductImageUtil.toCoilImageModel(
            context = phone2Context,
            photoUri = updatedOnPhone2.photoUri,
            storeId = updatedOnPhone2.storeId,
            productId = updatedOnPhone2.id
        )
        assertNull(
            "Second phone must resolve null in phone-only mode so it shows the category icon",
            phone2AfterSwitchModel
        )

        // 3. Convert Phone 1 back to SYNC -> second phone gets compressed <=400px/30KB JPEG again
        val resyncResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            phone1Repo.convertExistingProductPhotos(
                storeId = storeId,
                targetMode = PhotoStorageMode.SYNC,
                currentProducts = listOf(updatedOnPhone2)
            )
        }
        assertEquals(1, resyncResult.convertedCount)
        val resyncedOnPhone2 = withTimeout(FLOW_TIMEOUT_MS) {
            phone2Repo.observeProducts(storeId).first { list ->
                list.any { it.id == outcome.productId && ProductImageUtil.isSyncDataUri(it.photoUri) }
            }
        }.first { it.id == outcome.productId }
        val resyncedBytes = ProductImageUtil.decodeDataUriBytes(resyncedOnPhone2.photoUri)
        assertNotNull(resyncedBytes)
        assertTrue(resyncedBytes!!.size <= ProductImageUtil.SYNC_MAX_BYTES)
        assertNull(
            "Local-only file on Phone 1 should be removed once moved back into Firestore sync",
            ProductImageUtil.findLocalOnlyPhotoFile(appContext, storeId, outcome.productId)
        )
        largePhotoFile.delete()
        Unit
    }

    @Test
    fun unreadablePhotoUri_stillSavesProductWithClearWarning() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = StoreRepository(firestore, appContext)
        val storeId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createStore("Resilient Store", "01788888888", "Dhaka")
        }

        val brokenUri = "file:///nonexistent_dir_12345/missing_photo.jpg"
        val outcome = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addProduct(
                storeId = storeId,
                product = Product(
                    name = "Mustard Oil 1L",
                    category = "Oil & Ghee",
                    unit = "bottle",
                    buyPrice = 200.0,
                    sellPrice = 240.0,
                    stock = 8.0,
                    photoUri = brokenUri
                ),
                photoStorageMode = PhotoStorageMode.SYNC
            )
        }

        assertTrue("Product ID must be created even when photo read fails", outcome.productId.isNotBlank())
        assertTrue("photoFailed should be true", outcome.photoFailed)
        assertNotNull("Clear warning message must be returned", outcome.photoWarning)
        assertTrue(outcome.photoWarning!!.contains("Product saved", ignoreCase = true))
        assertEquals("", outcome.photoUri)

        val saved = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeProducts(storeId).first { list -> list.any { it.id == outcome.productId } }
        }
        assertTrue(saved.any { it.id == outcome.productId && it.name == "Mustard Oil 1L" })
        Unit
    }

    private companion object {
        const val ALICE_EMAIL = "alice_photo_test@test.com"
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
