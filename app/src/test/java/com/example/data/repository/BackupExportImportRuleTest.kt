package com.example.data.repository

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Account
import com.example.data.model.BackupImportMode
import com.example.data.model.CsvExportTarget
import com.example.data.model.Customer
import com.example.data.model.Expense
import com.example.data.model.OtherIncome
import com.example.data.model.Product
import com.example.data.model.Purchase
import com.example.data.model.PurchaseItem
import com.example.data.model.PurchaseReturn
import com.example.data.model.SaleItem
import com.example.data.model.StoreCategory
import com.example.data.model.StoreInvite
import com.example.data.model.StoreNote
import com.example.data.model.StorePermissions
import com.example.data.model.Supplier
import com.example.domain.CalculationEngine
import com.example.domain.ShopType
import com.example.util.BackupCodecException
import com.example.util.PhotoStorageMode
import com.example.util.ProductImageUtil
import com.example.util.StoreBackupCodec
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupExportImportRuleTest : FirestoreEmulatorTestBase() {

    private fun createSamplePhotoFile(fileName: String): File {
        val dir = File(appContext.cacheDir, "backup_test_photos").apply { mkdirs() }
        val file = File(dir, fileName)
        val bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(34, 139, 34))
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        bmp.recycle()
        return file
    }

    @Test
    fun exportFromCurrentStore_importIntoNewEmptyStore_preservesProductsStockBalancesInvoicesAndRecomputesTotals() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = StoreRepository(firestore, appContext)
        File(appContext.filesDir, "auto_backups").deleteRecursively()

        val sourceStoreId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createStore(
                name = "Rahim Traders",
                phone = "01711112222",
                address = "Dhaka",
                shopType = ShopType.GROCERY.id,
                productCategories = listOf("Rice & Grains", "Oil & Ghee")
            )
        }

        // Add products (one synced photo, one local-only photo)
        val photo1 = createSamplePhotoFile("prod_sync.jpg")
        val photo2 = createSamplePhotoFile("prod_local.jpg")

        val p1Outcome = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addProduct(
                storeId = sourceStoreId,
                product = Product(
                    name = "Miniket Rice 5kg",
                    category = "Rice & Grains",
                    unit = "bag",
                    buyPrice = 300.0,
                    sellPrice = 350.0,
                    stock = 20.0,
                    lowStockLimit = 5.0,
                    photoUri = Uri.fromFile(photo1).toString()
                ),
                photoStorageMode = PhotoStorageMode.SYNC
            )
        }
        val p2Outcome = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addProduct(
                storeId = sourceStoreId,
                product = Product(
                    name = "Mustard Oil 1L",
                    category = "Oil & Ghee",
                    unit = "bottle",
                    buyPrice = 180.0,
                    sellPrice = 220.0,
                    stock = 12.0,
                    lowStockLimit = 3.0,
                    photoUri = Uri.fromFile(photo2).toString()
                ),
                photoStorageMode = PhotoStorageMode.PHONE_ONLY
            )
        }

        // Add customer & supplier & custom account
        val custId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addCustomer(sourceStoreId, Customer(name = "Karim Mia", mobile = "01700001111"))
        }
        val supId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addSupplier(sourceStoreId, Supplier(name = "Bengal Mills", mobile = "01800002222"))
        }
        val bkashAccId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createAccount(
                Account(
                    storeId = sourceStoreId,
                    name = "bKash Merchant",
                    type = "Wallet",
                    accountNumber = "01711112222",
                    openingBalance = 2500.0
                )
            )
        }

        // Record a baki sale + partial payment
        val inv1 = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.executeSaleCheckout(
                storeId = sourceStoreId,
                items = listOf(
                    SaleItem(
                        productId = p1Outcome.productId,
                        name = "Miniket Rice 5kg",
                        qty = 3.0,
                        unit = "bag",
                        stockQty = 3.0,
                        buyPrice = 300.0,
                        sellPrice = 350.0,
                        discount = 10.0,
                        lineTotal = 1040.0,
                        profit = 140.0
                    )
                ),
                paymentType = "baki",
                customerId = custId,
                customerName = "Karim Mia",
                customerPhone = "01700001111"
            )
        }
        assertEquals("INV-0101", inv1)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.recordCustomerPaymentAtomic(
                storeId = sourceStoreId,
                customerId = custId,
                customerName = "Karim Mia",
                customerMobile = "01700001111",
                amount = 400.0,
                note = "Partial baki collection",
                paymentDate = System.currentTimeMillis() - 3000L,
                accountId = bkashAccId,
                accountName = "bKash Merchant"
            )
        }

        // Record purchase + return + expense + income + category + note
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.recordPurchase(
                storeId = sourceStoreId,
                purchase = Purchase(
                    storeId = sourceStoreId,
                    purchaseNo = "PUR-0101",
                    supplierId = supId,
                    supplierName = "Bengal Mills",
                    items = listOf(
                        PurchaseItem(
                            productId = p2Outcome.productId,
                            name = "Mustard Oil 1L",
                            qty = 5.0,
                            unit = "bottle",
                            stockQty = 5.0,
                            conversionFactor = 1.0,
                            costPerStockUnit = 180.0,
                            buyPrice = 180.0,
                            lineTotal = 900.0
                        )
                    ),
                    totalItems = 5.0,
                    totalAmount = 900.0,
                    paidAmount = 300.0,
                    dueAmount = 600.0,
                    paymentType = "credit"
                )
            )
            repo.recordPurchaseReturn(
                storeId = sourceStoreId,
                returnItem = PurchaseReturn(
                    storeId = sourceStoreId,
                    returnNo = "RET-0001",
                    supplierId = supId,
                    supplierName = "Bengal Mills",
                    amount = 100.0,
                    reason = "Damaged bottle"
                )
            )
            repo.createCategory(StoreCategory(storeId = sourceStoreId, type = "expense", name = "Shop Rent"))
            repo.recordExpense(
                Expense(
                    storeId = sourceStoreId,
                    amount = 250.0,
                    category = "Shop Rent",
                    accountId = "cash_in_hand",
                    accountName = "Cash in Hand",
                    date = System.currentTimeMillis() - 5000L,
                    note = "Daily utility"
                )
            )
            repo.recordOtherIncome(
                OtherIncome(
                    storeId = sourceStoreId,
                    amount = 120.0,
                    category = "Scrap Sale",
                    accountId = "cash_in_hand",
                    accountName = "Cash in Hand",
                    date = System.currentTimeMillis() - 4000L,
                    note = "Empty cartons"
                )
            )
            repo.saveNote(StoreNote(storeId = sourceStoreId, title = "Morning Checklist", text = "Count cash drawer"))
        }

        // Export entire store backup
        val exportedPayload = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.exportStoreBackup(
                storeId = sourceStoreId,
                photoStorageMode = PhotoStorageMode.SYNC,
                isSimpleMode = true
            )
        }

        val jsonFileName = StoreBackupCodec.buildBackupJsonFileName(
            exportedPayload.store.name,
            exportedPayload.exportedAtMillis
        )
        assertTrue("Backup filename must start with slugified shop name", jsonFileName.startsWith("Rahim-Traders-"))
        assertTrue("Backup filename must end with .json", jsonFileName.endsWith(".json"))

        // Verify local-only photo thumbnail was exported as compressed JPEG data URI too
        val exportedP2 = exportedPayload.products.first { it.id == p2Outcome.productId }
        assertTrue(
            "Local-only product photo on exporting phone must be packed as JPEG thumbnail in backup",
            ProductImageUtil.isSyncDataUri(exportedP2.photoUri)
        )
        assertTrue(exportedPayload.counters.invoiceSeq >= 101L)
        assertTrue(exportedPayload.counters.purchaseSeq >= 101L)
        assertEquals(2, exportedPayload.products.size)
        assertEquals(1, exportedPayload.customers.size)
        assertEquals(1, exportedPayload.suppliers.size)
        assertEquals(1, exportedPayload.sales.size)
        assertEquals(1, exportedPayload.purchases.size)
        assertEquals(1, exportedPayload.purchaseReturns.size)
        assertEquals(1, exportedPayload.payments.size)
        assertEquals(1, exportedPayload.expenses.size)
        assertEquals(1, exportedPayload.otherIncomes.size)
        assertEquals(1, exportedPayload.notes.size)

        // Check Excel/CSV exports contain UTF-8 BOM + columns
        val productsCsv = String(StoreBackupCodec.encodeCsv(CsvExportTarget.PRODUCTS, exportedPayload), StandardCharsets.UTF_8)
        assertTrue(productsCsv.startsWith("\uFEFF"))
        assertTrue(productsCsv.contains("Miniket Rice 5kg"))
        val salesCsv = String(StoreBackupCodec.encodeCsv(CsvExportTarget.SALES, exportedPayload), StandardCharsets.UTF_8)
        assertTrue(salesCsv.contains("INV-0101"))

        val rawJson = StoreBackupCodec.encodeToJson(exportedPayload)

        // Tamper grandTotal in JSON to prove import recomputes totals with CalculationEngine
        val tamperedRoot = JSONObject(rawJson)
        val salesArr = tamperedRoot.getJSONArray("sales")
        salesArr.getJSONObject(0).put("grandTotal", 99999.0)
        salesArr.getJSONObject(0).put("profit", 88888.0)
        val tamperedJson = tamperedRoot.toString()

        // Create a NEW EMPTY STORE and import into it
        val targetStoreId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createStore("Temp Empty Store", "", "")
        }
        assertTrue(repo.isStoreEmptyForImport(targetStoreId))

        val summary = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.importStoreBackup(
                storeId = targetStoreId,
                rawJson = tamperedJson,
                mode = BackupImportMode.ADD_TO_EMPTY_STORE,
                photoStorageMode = PhotoStorageMode.SYNC,
                isSimpleMode = false
            )
        }

        // Verify automatic backup file created before applying writes
        val autoBackupFile = File(summary.autoBackupPath)
        assertTrue("Automatic backup must be written before import", autoBackupFile.exists())
        val autoParsed = StoreBackupCodec.decodeAndValidateJson(autoBackupFile.readText(StandardCharsets.UTF_8))
        assertEquals("Temp Empty Store", autoParsed.store.name)

        // Verify imported store settings, shopType, products, stock, balances, invoices, and recomputed totals!
        val restoredStore = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeStore(targetStoreId).first { it != null && it.name == "Rahim Traders" }
        }!!
        assertEquals(ShopType.GROCERY.id, restoredStore.shopType)
        assertTrue(restoredStore.productCategories.contains("Rice & Grains"))

        val restoredProducts = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeProducts(targetStoreId).first { it.size == 2 }
        }
        val restoredRice = restoredProducts.first { it.id == p1Outcome.productId }
        assertEquals(17.0, restoredRice.stock, 0.001) // 20 - 3 sold
        val restoredOil = restoredProducts.first { it.id == p2Outcome.productId }
        assertEquals(17.0, restoredOil.stock, 0.001) // 12 + 5 purchased
        assertTrue(ProductImageUtil.isSyncDataUri(restoredOil.photoUri))

        val restoredCustomers = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeCustomers(targetStoreId).first { it.size == 1 }
        }
        // Customer baki = 1040 - 400 payment = 640
        assertEquals(640.0, restoredCustomers.first().currentBalance, 0.001)

        val restoredSuppliers = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeSuppliers(targetStoreId).first { it.size == 1 }
        }
        // Supplier balance = 600 due - 100 return = 500
        assertEquals(500.0, restoredSuppliers.first().balance, 0.001)

        val restoredSales = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeSales(targetStoreId).first { it.size == 1 }
        }
        val restoredSale = restoredSales.first()
        assertEquals("INV-0101", restoredSale.invoiceNo)
        // Recomputed by CalculationEngine (NOT the tampered 99999 / 88888!)
        assertEquals(1040.0, restoredSale.grandTotal, 0.001)
        assertEquals(140.0, restoredSale.profit, 0.001)

        // Recording another sale on restored store increments cleanly from INV-0101 -> INV-0102
        val nextInv = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.executeSaleCheckout(
                storeId = targetStoreId,
                items = listOf(
                    SaleItem(
                        productId = p1Outcome.productId,
                        name = "Miniket Rice 5kg",
                        qty = 1.0,
                        unit = "bag",
                        stockQty = 1.0,
                        buyPrice = 300.0,
                        sellPrice = 350.0,
                        discount = 0.0,
                        lineTotal = 350.0,
                        profit = 50.0
                    )
                ),
                paymentType = "cash",
                customerId = "",
                customerName = "Cash Sale",
                customerPhone = ""
            )
        }
        assertEquals("INV-0102", nextInv)

        photo1.delete()
        photo2.delete()
        Unit
    }

    @Test
    fun importModeChecks_addToEmptyStoreFailsOnNonEmpty_andMergeSkipsDuplicates() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = StoreRepository(firestore, appContext)

        val storeA = withTimeout(DEFAULT_TIMEOUT_MS) { repo.createStore("Backup Source", "0171", "Dhaka") }
        val p1 = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addProduct(storeA, Product(name = "Item One", buyPrice = 10.0, sellPrice = 15.0, stock = 5.0))
        }
        val p2 = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addProduct(storeA, Product(name = "Item Two", buyPrice = 20.0, sellPrice = 30.0, stock = 9.0))
        }
        val exported = withTimeout(DEFAULT_TIMEOUT_MS) { repo.exportStoreBackup(storeA) }
        val json = StoreBackupCodec.encodeToJson(exported)

        // Store B already has Item One (same doc ID via manually importing a subset or directly adding a product)
        val storeB = withTimeout(DEFAULT_TIMEOUT_MS) { repo.createStore("Target Store", "0172", "Dhaka") }
        val partialJson = StoreBackupCodec.encodeToJson(
            exported.copy(products = exported.products.filter { it.id == p1.productId })
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.importStoreBackup(storeB, partialJson, BackupImportMode.ADD_TO_EMPTY_STORE)
        }
        assertFalse(repo.isStoreEmptyForImport(storeB))

        // Add to empty store must reject non-empty storeB
        val emptyRejected = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                repo.importStoreBackup(storeB, json, BackupImportMode.ADD_TO_EMPTY_STORE)
            }
        }
        assertTrue("Add to empty store must fail on non-empty store", emptyRejected.isFailure)

        // Merge skips duplicate p1 and adds new p2
        val mergeSummary = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.importStoreBackup(storeB, json, BackupImportMode.MERGE)
        }
        assertEquals(1, mergeSummary.productsAdded)
        assertEquals(1, mergeSummary.productsSkipped)

        val finalProds = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeProducts(storeB).first { it.size == 2 }
        }
        assertTrue(finalProds.any { it.id == p1.productId })
        assertTrue(finalProds.any { it.id == p2.productId })
        Unit
    }

    @Test
    fun staffUser_isBlockedFromExportAndImport() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val ownerRepo = StoreRepository(firestore, appContext)
        val storeId = withTimeout(DEFAULT_TIMEOUT_MS) { ownerRepo.createStore("Owner Only Store", "0170", "Dhaka") }
        val inviteCode = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.createStaffInvite(
                StoreInvite(
                    storeId = storeId,
                    storeName = "Owner Only Store",
                    staffName = "Bob Staff",
                    roleLabel = "Cashier",
                    permissionLevel = "Full",
                    permissions = StorePermissions.forLevel("Full"),
                    createdByUid = aliceUid,
                    createdByName = "Alice",
                    status = "active"
                )
            )
        }

        val bobUid = signInTestUser(BOB_EMAIL)
        val staffRepo = StoreRepository(firestore, appContext)
        val joinResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            staffRepo.joinStoreWithInvite(
                code = inviteCode,
                uid = bobUid,
                email = BOB_EMAIL,
                displayName = "Bob Staff"
            )
        }
        assertTrue(joinResult.isSuccess)

        val exportAttempt = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) { staffRepo.exportStoreBackup(storeId) }
        }
        assertTrue("Staff export must throw SecurityException", exportAttempt.exceptionOrNull() is SecurityException)

        val validDummyJson = StoreBackupCodec.encodeToJson(
            com.example.data.model.StoreBackupPayload(
                store = com.example.data.model.Store(name = "Fake"),
                counters = com.example.data.model.StoreBackupCounters(invoiceSeq = 100L)
            )
        )
        val importAttempt = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                staffRepo.importStoreBackup(storeId, validDummyJson, BackupImportMode.MERGE)
            }
        }
        assertTrue("Staff import must throw SecurityException", importAttempt.exceptionOrNull() is SecurityException)
        Unit
    }

    @Test
    fun codecValidation_rejectsWrongOrDamagedFilesWithClearErrors() {
        val emptyErr = runCatching { StoreBackupCodec.decodeAndValidateJson("") }.exceptionOrNull()
        assertTrue(emptyErr is BackupCodecException && emptyErr.message!!.isNotBlank())

        val badJsonErr = runCatching { StoreBackupCodec.decodeAndValidateJson("{not valid json") }.exceptionOrNull()
        assertTrue(badJsonErr is BackupCodecException && badJsonErr.message!!.contains("JSON", ignoreCase = true))

        val wrongFmtErr = runCatching {
            StoreBackupCodec.decodeAndValidateJson("""{"format":"other-app","version":1,"store":{"name":"A"}}""")
        }.exceptionOrNull()
        assertTrue(wrongFmtErr is BackupCodecException && wrongFmtErr.message!!.contains("StoreOS", ignoreCase = true))

        val unsupportedVerErr = runCatching {
            StoreBackupCodec.decodeAndValidateJson("""{"format":"storeos-backup","version":99,"store":{"name":"A"}}""")
        }.exceptionOrNull()
        assertTrue(unsupportedVerErr is BackupCodecException && unsupportedVerErr.message!!.contains("99"))

        val corruptArrayErr = runCatching {
            StoreBackupCodec.decodeAndValidateJson("""{"format":"storeos-backup","version":1,"store":{"name":"A"},"products":"broken"}""")
        }.exceptionOrNull()
        assertTrue(corruptArrayErr is BackupCodecException && corruptArrayErr.message!!.contains("products"))
    }

    private companion object {
        const val ALICE_EMAIL = "alice_backup_test@test.com"
        const val BOB_EMAIL = "bob_backup_staff@test.com"
        const val DEFAULT_TIMEOUT_MS = 10000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
