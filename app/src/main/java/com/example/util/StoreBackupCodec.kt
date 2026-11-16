package com.example.util

import com.example.data.model.Account
import com.example.data.model.AccountAdjustment
import com.example.data.model.BackupImportPreview
import com.example.data.model.CsvExportTarget
import com.example.data.model.Customer
import com.example.data.model.Expense
import com.example.data.model.OtherIncome
import com.example.data.model.Payment
import com.example.data.model.Product
import com.example.data.model.Purchase
import com.example.data.model.PurchaseItem
import com.example.data.model.PurchaseReturn
import com.example.data.model.Sale
import com.example.data.model.SaleItem
import com.example.data.model.Store
import com.example.data.model.StoreBackupCounters
import com.example.data.model.StoreBackupPayload
import com.example.data.model.StoreBackupSettings
import com.example.data.model.StoreBackupUnits
import com.example.data.model.StoreCategory
import com.example.data.model.StoreNote
import com.example.data.model.Supplier
import com.example.data.model.SupplierPayment
import com.example.domain.CalculationEngine
import com.example.domain.ShopUnitPreset
import com.google.firebase.Timestamp
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class BackupCodecException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

object StoreBackupCodec {

    private fun dateSlug(timeMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date(timeMillis))
    }

    fun sanitizeShopSlug(storeName: String): String {
        val cleaned = storeName.trim()
            .replace(Regex("[/\\\\:*?\"<>|\\s]+"), "-")
            .replace(Regex("-{2,}"), "-")
            .trim('-', '.')
            .take(48)
        return cleaned.ifBlank { "storeos" }
    }

    fun buildBackupJsonFileName(storeName: String, timeMillis: Long = System.currentTimeMillis()): String {
        return "${sanitizeShopSlug(storeName)}-${dateSlug(timeMillis)}.json"
    }

    fun buildAutoBackupFileName(storeName: String, timeMillis: Long = System.currentTimeMillis()): String {
        val ts = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date(timeMillis))
        return "${sanitizeShopSlug(storeName)}-auto-$ts.json"
    }

    fun buildCsvFileName(
        storeName: String,
        target: CsvExportTarget,
        timeMillis: Long = System.currentTimeMillis()
    ): String {
        return "${sanitizeShopSlug(storeName)}-${target.slug}-${dateSlug(timeMillis)}.csv"
    }

    fun buildPreview(payload: StoreBackupPayload, targetStoreIsEmpty: Boolean): BackupImportPreview {
        return BackupImportPreview(
            payload = payload,
            version = payload.version,
            sourceStoreName = payload.store.name.ifBlank { payload.storeSettings.name.ifBlank { "StoreOS" } },
            shopType = payload.shopType.ifBlank { payload.store.shopType },
            exportedAtMillis = payload.exportedAtMillis,
            targetStoreIsEmpty = targetStoreIsEmpty,
            productCategoriesCount = payload.productCategories.size,
            categoryRecordsCount = payload.categoryRecords.size,
            productsCount = payload.products.size,
            customersCount = payload.customers.size,
            suppliersCount = payload.suppliers.size,
            salesCount = payload.sales.size,
            purchasesCount = payload.purchases.size,
            purchaseReturnsCount = payload.purchaseReturns.size,
            paymentsCount = payload.payments.size,
            supplierPaymentsCount = payload.supplierPayments.size,
            accountsCount = payload.accounts.size,
            accountAdjustmentsCount = payload.accountAdjustments.size,
            expensesCount = payload.expenses.size,
            incomeCount = payload.otherIncomes.size,
            notesCount = payload.notes.size
        )
    }

    fun encodeToJson(payload: StoreBackupPayload): String {
        val root = JSONObject()
        root.put("format", payload.format)
        root.put("version", payload.version)
        root.put("exportedAt", formatIsoUtc(payload.exportedAtMillis))
        root.put("exportedAtMillis", payload.exportedAtMillis)

        root.put("store", JSONObject().apply {
            put("id", payload.store.id)
            put("name", payload.store.name)
            put("phone", payload.store.phone)
            put("address", payload.store.address)
            put("shopType", payload.shopType.ifBlank { payload.store.shopType })
            put("productCategories", JSONArray(payload.productCategories))
            put("ownerId", payload.store.ownerId)
        })

        root.put("storeSettings", JSONObject().apply {
            put("name", payload.storeSettings.name.ifBlank { payload.store.name })
            put("phone", payload.storeSettings.phone.ifBlank { payload.store.phone })
            put("address", payload.storeSettings.address.ifBlank { payload.store.address })
            put("shopType", payload.shopType.ifBlank { payload.storeSettings.shopType })
            put("language", payload.storeSettings.language)
            put("isSimpleMode", payload.storeSettings.isSimpleMode)
            put("photoStorageMode", payload.storeSettings.photoStorageMode)
        })

        root.put("shopType", payload.shopType.ifBlank { payload.store.shopType })

        val categoriesObj = JSONObject().apply {
            put("product", JSONArray(payload.productCategories))
            put(
                "expense",
                JSONArray(payload.categoryRecords.filter { it.type == "expense" }.map { it.name })
            )
            put(
                "income",
                JSONArray(payload.categoryRecords.filter { it.type == "income" }.map { it.name })
            )
            put("records", JSONArray().apply {
                payload.categoryRecords.forEach { cat ->
                    put(JSONObject().apply {
                        put("id", cat.id)
                        put("storeId", cat.storeId)
                        put("type", cat.type)
                        put("name", cat.name)
                        put("isArchived", cat.isArchived)
                        put("createdBy", cat.createdBy)
                        put("createdAtMillis", cat.createdAt?.toDate()?.time ?: 0L)
                    })
                }
            })
        }
        root.put("categories", categoriesObj)

        val unitsObj = JSONObject().apply {
            put("presets", JSONArray().apply {
                payload.units.presets.forEach { preset ->
                    put(JSONObject().apply {
                        put("id", preset.id)
                        put("labelEn", preset.labelEn)
                        put("labelBn", preset.labelBn)
                        put("category", preset.category)
                        put("stockUnit", preset.stockUnit)
                        put("buyUnit", preset.buyUnit)
                        put("buyConversion", preset.buyConversion)
                        put("sellUnits", JSONObject().apply {
                            preset.sellUnits.forEach { (k, v) -> put(k, v) }
                        })
                    })
                }
            })
            put("allUnits", JSONArray(payload.units.allUnits))
        }
        root.put("units", unitsObj)

        root.put("counters", JSONObject().apply {
            put("invoiceSeq", payload.counters.invoiceSeq)
            put("paymentSeq", payload.counters.paymentSeq)
            put("purchaseSeq", payload.counters.purchaseSeq)
            put("returnSeq", payload.counters.returnSeq)
        })

        root.put("products", JSONArray().apply {
            payload.products.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("storeId", p.storeId)
                    put("name", p.name)
                    put("nameBn", p.nameBn)
                    put("barcode", p.barcode)
                    put("category", p.category)
                    put("unit", p.unit)
                    put("buyUnit", p.buyUnit)
                    put("buyConversion", p.buyConversion)
                    put("sellUnits", JSONObject().apply {
                        p.sellUnits.forEach { (k, v) -> put(k, v) }
                    })
                    put("buyPrice", p.buyPrice)
                    put("sellPrice", p.sellPrice)
                    put("wholesalePrice", p.wholesalePrice)
                    put("mrp", p.mrp)
                    put("itemLocation", p.itemLocation)
                    put("partyRate", p.partyRate)
                    put("stock", p.stock)
                    put("lowStockLimit", p.lowStockLimit)
                    put("photoUri", p.photoUri)
                    put("createdBy", p.createdBy)
                    put("createdAtMillis", p.createdAt?.toDate()?.time ?: 0L)
                    put("updatedAtMillis", p.updatedAt?.toDate()?.time ?: 0L)
                })
            }
        })

        root.put("customers", JSONArray().apply {
            payload.customers.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("storeId", c.storeId)
                    put("name", c.name)
                    put("mobile", c.mobile)
                    put("address", c.address)
                    put("notes", c.notes)
                    put("currentBalance", c.currentBalance)
                    put("createdBy", c.createdBy)
                    put("createdAtMillis", c.createdAt?.toDate()?.time ?: 0L)
                    put("updatedAtMillis", c.updatedAt?.toDate()?.time ?: 0L)
                })
            }
        })

        root.put("suppliers", JSONArray().apply {
            payload.suppliers.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("storeId", s.storeId)
                    put("name", s.name)
                    put("mobile", s.mobile)
                    put("company", s.company)
                    put("address", s.address)
                    put("balance", s.balance)
                    put("createdBy", s.createdBy)
                    put("createdAtMillis", s.createdAt?.toDate()?.time ?: 0L)
                    put("updatedAtMillis", s.updatedAt?.toDate()?.time ?: 0L)
                })
            }
        })

        root.put("sales", JSONArray().apply {
            payload.sales.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("storeId", s.storeId)
                    put("invoiceNo", s.invoiceNo)
                    put("items", JSONArray().apply {
                        s.items.forEach { itRow ->
                            put(JSONObject().apply {
                                put("productId", itRow.productId)
                                put("name", itRow.name)
                                put("qty", itRow.qty)
                                put("unit", itRow.unit)
                                put("stockQty", itRow.stockQty)
                                put("buyPrice", itRow.buyPrice)
                                put("sellPrice", itRow.sellPrice)
                                put("discount", itRow.discount)
                                put("lineTotal", itRow.lineTotal)
                                put("profit", itRow.profit)
                            })
                        }
                    })
                    put("totalItems", s.totalItems)
                    put("subtotal", s.subtotal)
                    put("discount", s.discount)
                    put("grandTotal", s.grandTotal)
                    put("profit", s.profit)
                    put("paymentType", s.paymentType)
                    put("customerId", s.customerId)
                    put("customerName", s.customerName)
                    put("customerPhone", s.customerPhone)
                    put("accountId", s.accountId)
                    put("accountName", s.accountName)
                    put("notes", s.notes)
                    put("soldBy", s.soldBy)
                    put("createdAtMillis", s.createdAt?.toDate()?.time ?: 0L)
                })
            }
        })

        root.put("purchases", JSONArray().apply {
            payload.purchases.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("storeId", p.storeId)
                    put("purchaseNo", p.purchaseNo)
                    put("supplierId", p.supplierId)
                    put("supplierName", p.supplierName)
                    put("supplierMobile", p.supplierMobile)
                    put("items", JSONArray().apply {
                        p.items.forEach { item ->
                            put(JSONObject().apply {
                                put("productId", item.productId)
                                put("name", item.name)
                                put("qty", item.qty)
                                put("unit", item.unit)
                                put("stockQty", item.stockQty)
                                put("conversionFactor", item.conversionFactor)
                                put("costPerStockUnit", item.costPerStockUnit)
                                put("buyPrice", item.buyPrice)
                                put("lineTotal", item.lineTotal)
                            })
                        }
                    })
                    put("totalItems", p.totalItems)
                    put("totalAmount", p.totalAmount)
                    put("paidAmount", p.paidAmount)
                    put("dueAmount", p.dueAmount)
                    put("paymentType", p.paymentType)
                    put("accountId", p.accountId)
                    put("accountName", p.accountName)
                    put("note", p.note)
                    put("createdBy", p.createdBy)
                    put("createdAtMillis", p.createdAt?.toDate()?.time ?: 0L)
                })
            }
        })

        root.put("purchaseReturns", JSONArray().apply {
            payload.purchaseReturns.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("storeId", r.storeId)
                    put("returnNo", r.returnNo)
                    put("purchaseId", r.purchaseId)
                    put("supplierId", r.supplierId)
                    put("supplierName", r.supplierName)
                    put("amount", r.amount)
                    put("reason", r.reason)
                    put("createdBy", r.createdBy)
                    put("createdAtMillis", r.createdAt?.toDate()?.time ?: 0L)
                })
            }
        })

        root.put("payments", JSONArray().apply {
            payload.payments.forEach { pay ->
                put(JSONObject().apply {
                    put("id", pay.id)
                    put("storeId", pay.storeId)
                    put("paymentNo", pay.paymentNo)
                    put("customerId", pay.customerId)
                    put("customerName", pay.customerName)
                    put("customerMobile", pay.customerMobile)
                    put("amount", pay.amount)
                    put("paymentMethod", pay.paymentMethod)
                    put("accountId", pay.accountId)
                    put("accountName", pay.accountName)
                    put("paymentDate", pay.paymentDate)
                    put("note", pay.note)
                    put("receivedBy", pay.receivedBy)
                    put("createdAtMillis", pay.createdAt?.toDate()?.time ?: 0L)
                })
            }
        })

        root.put("supplierPayments", JSONArray().apply {
            payload.supplierPayments.forEach { sp ->
                put(JSONObject().apply {
                    put("id", sp.id)
                    put("storeId", sp.storeId)
                    put("paymentNo", sp.paymentNo)
                    put("supplierId", sp.supplierId)
                    put("supplierName", sp.supplierName)
                    put("amount", sp.amount)
                    put("accountId", sp.accountId)
                    put("accountName", sp.accountName)
                    put("paymentDate", sp.paymentDate)
                    put("note", sp.note)
                    put("paidBy", sp.paidBy)
                    put("createdAtMillis", sp.createdAt?.toDate()?.time ?: 0L)
                })
            }
        })

        root.put("accounts", JSONArray().apply {
            payload.accounts.forEach { acc ->
                put(JSONObject().apply {
                    put("id", acc.id)
                    put("storeId", acc.storeId)
                    put("name", acc.name)
                    put("type", acc.type)
                    put("accountNumber", acc.accountNumber)
                    put("openingBalance", acc.openingBalance)
                    put("isDefault", acc.isDefault)
                    put("isArchived", acc.isArchived)
                    put("createdBy", acc.createdBy)
                    put("createdAtMillis", acc.createdAt?.toDate()?.time ?: 0L)
                    put("updatedAtMillis", acc.updatedAt?.toDate()?.time ?: 0L)
                })
            }
        })

        root.put("accountAdjustments", JSONArray().apply {
            payload.accountAdjustments.forEach { adj ->
                put(JSONObject().apply {
                    put("id", adj.id)
                    put("storeId", adj.storeId)
                    put("accountId", adj.accountId)
                    put("accountName", adj.accountName)
                    put("oldBalance", adj.oldBalance)
                    put("newBalance", adj.newBalance)
                    put("difference", adj.difference)
                    put("note", adj.note)
                    put("adjustedBy", adj.adjustedBy)
                    put("createdAtMillis", adj.createdAt?.toDate()?.time ?: 0L)
                })
            }
        })

        root.put("expenses", JSONArray().apply {
            payload.expenses.forEach { exp ->
                put(JSONObject().apply {
                    put("id", exp.id)
                    put("storeId", exp.storeId)
                    put("amount", exp.amount)
                    put("category", exp.category)
                    put("accountId", exp.accountId)
                    put("accountName", exp.accountName)
                    put("date", exp.date)
                    put("note", exp.note)
                    put("receiptUri", exp.receiptUri)
                    put("createdBy", exp.createdBy)
                    put("isDeleted", exp.isDeleted)
                    put("deletedNote", exp.deletedNote)
                    put("deletedBy", exp.deletedBy)
                    put("deletedAtMillis", exp.deletedAt?.toDate()?.time ?: 0L)
                    put("createdAtMillis", exp.createdAt?.toDate()?.time ?: 0L)
                })
            }
        })

        root.put("income", JSONArray().apply {
            payload.otherIncomes.forEach { inc ->
                put(JSONObject().apply {
                    put("id", inc.id)
                    put("storeId", inc.storeId)
                    put("amount", inc.amount)
                    put("category", inc.category)
                    put("accountId", inc.accountId)
                    put("accountName", inc.accountName)
                    put("date", inc.date)
                    put("note", inc.note)
                    put("createdBy", inc.createdBy)
                    put("isDeleted", inc.isDeleted)
                    put("deletedNote", inc.deletedNote)
                    put("deletedBy", inc.deletedBy)
                    put("deletedAtMillis", inc.deletedAt?.toDate()?.time ?: 0L)
                    put("createdAtMillis", inc.createdAt?.toDate()?.time ?: 0L)
                })
            }
        })

        root.put("notes", JSONArray().apply {
            payload.notes.forEach { n ->
                put(JSONObject().apply {
                    put("id", n.id)
                    put("storeId", n.storeId)
                    put("title", n.title)
                    put("text", n.text)
                    put("date", n.date)
                    put("createdBy", n.createdBy)
                    put("createdAtMillis", n.createdAt?.toDate()?.time ?: 0L)
                    put("updatedAtMillis", n.updatedAt?.toDate()?.time ?: 0L)
                })
            }
        })

        return root.toString(2)
    }

    fun decodeAndValidateJson(
        rawJson: String,
        lang: AppLanguage = ShopLocale.activeLanguage
    ): StoreBackupPayload {
        val cleaned = rawJson.removePrefix("\uFEFF").trim()
        if (cleaned.isBlank()) {
            throw BackupCodecException(ShopLocale.get("backup_err_empty_file", lang))
        }
        val root = try {
            JSONObject(cleaned)
        } catch (e: JSONException) {
            throw BackupCodecException(ShopLocale.get("backup_err_damaged_json", lang), e)
        }

        val format = root.optString("format", "").trim()
        if (!format.equals(StoreBackupPayload.FORMAT_ID, ignoreCase = true)) {
            throw BackupCodecException(ShopLocale.get("backup_err_wrong_file", lang))
        }

        if (!root.has("version") || root.isNull("version")) {
            throw BackupCodecException(ShopLocale.get("backup_err_missing_version", lang))
        }
        val versionAny = root.opt("version")
        val version = when (versionAny) {
            is Number -> versionAny.toInt()
            else -> throw BackupCodecException(ShopLocale.get("backup_err_missing_version", lang))
        }
        if (version <= 0 || version > StoreBackupPayload.CURRENT_VERSION) {
            throw BackupCodecException(ShopLocale.format("backup_err_unsupported_version", lang, version))
        }

        val storeObj = optStrictObject(root, "store", lang)
            ?: throw BackupCodecException(ShopLocale.get("backup_err_damaged_structure", lang))
        val settingsObj = optStrictObject(root, "storeSettings", lang)

        val storeName = storeObj.optString("name", "").ifBlank {
            settingsObj?.optString("name", "") ?: ""
        }.trim()
        val shopType = root.optString("shopType", "").ifBlank {
            storeObj.optString("shopType", "").ifBlank {
                settingsObj?.optString("shopType", "") ?: ""
            }
        }.trim()

        val categoriesObj = optStrictObject(root, "categories", lang)
        val productCategories = parseStringList(optStrictArray(categoriesObj, "product", lang))
            .ifEmpty { parseStringList(optStrictArray(storeObj, "productCategories", lang)) }

        val categoryRecords = parseArray(optStrictArray(categoriesObj, "records", lang), "categories", lang) { obj ->
            val type = obj.optString("type", "").trim()
            val name = obj.optString("name", "").trim()
            if (name.isBlank() || (type != "expense" && type != "income")) {
                throw BackupCodecException(ShopLocale.format("backup_err_corrupt_section", lang, "categories"))
            }
            StoreCategory(
                id = obj.optString("id", "").trim(),
                storeId = obj.optString("storeId", "").trim(),
                type = type,
                name = name,
                isArchived = obj.optBoolean("isArchived", false),
                createdBy = obj.optString("createdBy", ""),
                createdAt = millisToTimestamp(obj.optLong("createdAtMillis", 0L))
            )
        }

        val unitsObj = optStrictObject(root, "units", lang)
        val unitPresets = parseArray(optStrictArray(unitsObj, "presets", lang), "units", lang) { obj ->
            val sellUnitsMap = mutableMapOf<String, Double>()
            val sellUnitsObj = optStrictObject(obj, "sellUnits", lang)
            if (sellUnitsObj != null) {
                val keys = sellUnitsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = sellUnitsObj.optDouble(k, 0.0)
                    if (k.isNotBlank() && v.isFinite() && v > 0.0) {
                        sellUnitsMap[k] = v
                    }
                }
            }
            val stockUnit = obj.optString("stockUnit", "pcs").ifBlank { "pcs" }
            ShopUnitPreset(
                id = obj.optString("id", stockUnit),
                labelEn = obj.optString("labelEn", stockUnit),
                labelBn = obj.optString("labelBn", stockUnit),
                category = obj.optString("category", "General"),
                stockUnit = stockUnit,
                buyUnit = obj.optString("buyUnit", stockUnit).ifBlank { stockUnit },
                buyConversion = obj.optDouble("buyConversion", 1.0).takeIf { it.isFinite() && it > 0.0 } ?: 1.0,
                sellUnits = sellUnitsMap
            )
        }
        val allUnits = parseStringList(optStrictArray(unitsObj, "allUnits", lang))

        val countersObj = optStrictObject(root, "counters", lang)
        val counters = StoreBackupCounters(
            invoiceSeq = countersObj?.optLong("invoiceSeq", 100L)?.coerceAtLeast(0L) ?: 100L,
            paymentSeq = countersObj?.optLong("paymentSeq", 0L)?.coerceAtLeast(0L) ?: 0L,
            purchaseSeq = countersObj?.optLong("purchaseSeq", 0L)?.coerceAtLeast(0L) ?: 0L,
            returnSeq = countersObj?.optLong("returnSeq", 0L)?.coerceAtLeast(0L) ?: 0L
        )

        val products = parseArray(optStrictArray(root, "products", lang), "products", lang) { obj ->
            val name = obj.optString("name", "").trim()
            if (name.isBlank()) {
                throw BackupCodecException(ShopLocale.format("backup_err_corrupt_section", lang, "products"))
            }
            val sellUnitsObj = optStrictObject(obj, "sellUnits", lang)
            val sellUnitsMap = mutableMapOf<String, Double>()
            if (sellUnitsObj != null) {
                val keys = sellUnitsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = sellUnitsObj.optDouble(k, 0.0)
                    if (k.isNotBlank() && v.isFinite() && v > 0.0) {
                        sellUnitsMap[k] = v
                    }
                }
            }
            val buyConv = obj.optDouble("buyConversion", 1.0)
            Product(
                id = obj.optString("id", "").trim(),
                storeId = obj.optString("storeId", "").trim(),
                name = name,
                nameBn = obj.optString("nameBn", ""),
                barcode = obj.optString("barcode", ""),
                category = obj.optString("category", "General").ifBlank { "General" },
                unit = obj.optString("unit", "pcs").ifBlank { "pcs" },
                buyUnit = obj.optString("buyUnit", ""),
                buyConversion = if (buyConv.isFinite() && buyConv > 0.0) buyConv else 1.0,
                sellUnits = sellUnitsMap,
                buyPrice = safeDouble(obj, "buyPrice").coerceAtLeast(0.0),
                sellPrice = safeDouble(obj, "sellPrice").coerceAtLeast(0.0),
                wholesalePrice = safeDouble(obj, "wholesalePrice").coerceAtLeast(0.0),
                mrp = safeDouble(obj, "mrp").coerceAtLeast(0.0),
                itemLocation = obj.optString("itemLocation", ""),
                partyRate = safeDouble(obj, "partyRate").coerceAtLeast(0.0),
                stock = CalculationEngine.roundStock(safeDouble(obj, "stock").coerceAtLeast(0.0)),
                lowStockLimit = CalculationEngine.roundStock(safeDouble(obj, "lowStockLimit").coerceAtLeast(0.0)),
                photoUri = obj.optString("photoUri", ""),
                createdBy = obj.optString("createdBy", ""),
                createdAt = millisToTimestamp(obj.optLong("createdAtMillis", 0L)),
                updatedAt = millisToTimestamp(obj.optLong("updatedAtMillis", 0L))
            )
        }

        val customers = parseArray(optStrictArray(root, "customers", lang), "customers", lang) { obj ->
            val name = obj.optString("name", "").trim()
            if (name.isBlank()) {
                throw BackupCodecException(ShopLocale.format("backup_err_corrupt_section", lang, "customers"))
            }
            Customer(
                id = obj.optString("id", "").trim(),
                storeId = obj.optString("storeId", "").trim(),
                name = name,
                mobile = obj.optString("mobile", ""),
                address = obj.optString("address", ""),
                notes = obj.optString("notes", ""),
                currentBalance = CalculationEngine.roundMoney(safeDouble(obj, "currentBalance").coerceAtLeast(0.0)),
                createdBy = obj.optString("createdBy", ""),
                createdAt = millisToTimestamp(obj.optLong("createdAtMillis", 0L)),
                updatedAt = millisToTimestamp(obj.optLong("updatedAtMillis", 0L))
            )
        }

        val suppliers = parseArray(optStrictArray(root, "suppliers", lang), "suppliers", lang) { obj ->
            val name = obj.optString("name", "").trim()
            if (name.isBlank()) {
                throw BackupCodecException(ShopLocale.format("backup_err_corrupt_section", lang, "suppliers"))
            }
            Supplier(
                id = obj.optString("id", "").trim(),
                storeId = obj.optString("storeId", "").trim(),
                name = name,
                mobile = obj.optString("mobile", ""),
                company = obj.optString("company", ""),
                address = obj.optString("address", ""),
                balance = CalculationEngine.roundMoney(safeDouble(obj, "balance").coerceAtLeast(0.0)),
                createdBy = obj.optString("createdBy", ""),
                createdAt = millisToTimestamp(obj.optLong("createdAtMillis", 0L)),
                updatedAt = millisToTimestamp(obj.optLong("updatedAtMillis", 0L))
            )
        }

        val sales = parseArray(optStrictArray(root, "sales", lang), "sales", lang) { obj ->
            val invoiceNo = obj.optString("invoiceNo", "").trim()
            val itemsArr = optStrictArray(obj, "items", lang)
            if (invoiceNo.isBlank() || itemsArr == null || itemsArr.length() == 0) {
                throw BackupCodecException(ShopLocale.format("backup_err_corrupt_section", lang, "sales"))
            }
            val items = parseArray(itemsArr, "sales", lang) { itObj ->
                SaleItem(
                    productId = itObj.optString("productId", ""),
                    name = itObj.optString("name", ""),
                    qty = safeDouble(itObj, "qty").coerceAtLeast(0.0),
                    unit = itObj.optString("unit", "pcs").ifBlank { "pcs" },
                    stockQty = safeDouble(itObj, "stockQty").coerceAtLeast(0.0),
                    buyPrice = safeDouble(itObj, "buyPrice").coerceAtLeast(0.0),
                    sellPrice = safeDouble(itObj, "sellPrice").coerceAtLeast(0.0),
                    discount = safeDouble(itObj, "discount").coerceAtLeast(0.0),
                    lineTotal = safeDouble(itObj, "lineTotal").coerceAtLeast(0.0),
                    profit = safeDouble(itObj, "profit")
                )
            }
            val paymentType = obj.optString("paymentType", "cash").trim().lowercase()
                .let { if (it == "baki") "baki" else "cash" }
            Sale(
                id = obj.optString("id", "").trim(),
                storeId = obj.optString("storeId", "").trim(),
                invoiceNo = invoiceNo,
                items = items,
                totalItems = safeDouble(obj, "totalItems").coerceAtLeast(0.0),
                subtotal = safeDouble(obj, "subtotal").coerceAtLeast(0.0),
                discount = safeDouble(obj, "discount").coerceAtLeast(0.0),
                grandTotal = safeDouble(obj, "grandTotal").coerceAtLeast(0.0),
                profit = safeDouble(obj, "profit"),
                paymentType = paymentType,
                customerId = obj.optString("customerId", ""),
                customerName = obj.optString("customerName", "Cash Sale").ifBlank { "Cash Sale" },
                customerPhone = obj.optString("customerPhone", ""),
                accountId = obj.optString("accountId", ""),
                accountName = obj.optString("accountName", ""),
                notes = obj.optString("notes", ""),
                soldBy = obj.optString("soldBy", ""),
                createdAt = millisToTimestamp(obj.optLong("createdAtMillis", 0L))
            )
        }

        val purchases = parseArray(optStrictArray(root, "purchases", lang), "purchases", lang) { obj ->
            val purchaseNo = obj.optString("purchaseNo", "").trim()
            val itemsArr = optStrictArray(obj, "items", lang)
            if (purchaseNo.isBlank() || itemsArr == null || itemsArr.length() == 0) {
                throw BackupCodecException(ShopLocale.format("backup_err_corrupt_section", lang, "purchases"))
            }
            val items = parseArray(itemsArr, "purchases", lang) { itObj ->
                PurchaseItem(
                    productId = itObj.optString("productId", ""),
                    name = itObj.optString("name", ""),
                    qty = safeDouble(itObj, "qty").coerceAtLeast(0.0),
                    unit = itObj.optString("unit", "pcs").ifBlank { "pcs" },
                    stockQty = safeDouble(itObj, "stockQty").coerceAtLeast(0.0),
                    conversionFactor = safeDouble(itObj, "conversionFactor", 1.0).takeIf { it > 0.0 } ?: 1.0,
                    costPerStockUnit = safeDouble(itObj, "costPerStockUnit").coerceAtLeast(0.0),
                    buyPrice = safeDouble(itObj, "buyPrice").coerceAtLeast(0.0),
                    lineTotal = safeDouble(itObj, "lineTotal").coerceAtLeast(0.0)
                )
            }
            Purchase(
                id = obj.optString("id", "").trim(),
                storeId = obj.optString("storeId", "").trim(),
                purchaseNo = purchaseNo,
                supplierId = obj.optString("supplierId", ""),
                supplierName = obj.optString("supplierName", ""),
                supplierMobile = obj.optString("supplierMobile", ""),
                items = items,
                totalItems = safeDouble(obj, "totalItems").coerceAtLeast(0.0),
                totalAmount = safeDouble(obj, "totalAmount").coerceAtLeast(0.0),
                paidAmount = safeDouble(obj, "paidAmount").coerceAtLeast(0.0),
                dueAmount = safeDouble(obj, "dueAmount").coerceAtLeast(0.0),
                paymentType = obj.optString("paymentType", "paid"),
                accountId = obj.optString("accountId", ""),
                accountName = obj.optString("accountName", ""),
                note = obj.optString("note", ""),
                createdBy = obj.optString("createdBy", ""),
                createdAt = millisToTimestamp(obj.optLong("createdAtMillis", 0L))
            )
        }

        val purchaseReturns = parseArray(optStrictArray(root, "purchaseReturns", lang), "purchaseReturns", lang) { obj ->
            val returnNo = obj.optString("returnNo", "").trim()
            val amount = safeDouble(obj, "amount")
            if (returnNo.isBlank() || amount <= 0.0) {
                throw BackupCodecException(ShopLocale.format("backup_err_corrupt_section", lang, "purchaseReturns"))
            }
            PurchaseReturn(
                id = obj.optString("id", "").trim(),
                storeId = obj.optString("storeId", "").trim(),
                returnNo = returnNo,
                purchaseId = obj.optString("purchaseId", ""),
                supplierId = obj.optString("supplierId", ""),
                supplierName = obj.optString("supplierName", ""),
                amount = amount,
                reason = obj.optString("reason", ""),
                createdBy = obj.optString("createdBy", ""),
                createdAt = millisToTimestamp(obj.optLong("createdAtMillis", 0L))
            )
        }

        val payments = parseArray(optStrictArray(root, "payments", lang), "payments", lang) { obj ->
            val paymentNo = obj.optString("paymentNo", "").trim()
            val amount = safeDouble(obj, "amount")
            if (paymentNo.isBlank() || amount <= 0.0) {
                throw BackupCodecException(ShopLocale.format("backup_err_corrupt_section", lang, "payments"))
            }
            Payment(
                id = obj.optString("id", "").trim(),
                storeId = obj.optString("storeId", "").trim(),
                paymentNo = paymentNo,
                customerId = obj.optString("customerId", ""),
                customerName = obj.optString("customerName", ""),
                customerMobile = obj.optString("customerMobile", ""),
                amount = amount,
                paymentMethod = obj.optString("paymentMethod", "cash").ifBlank { "cash" },
                accountId = obj.optString("accountId", "cash_in_hand").ifBlank { "cash_in_hand" },
                accountName = obj.optString("accountName", "Cash in Hand").ifBlank { "Cash in Hand" },
                paymentDate = obj.optLong("paymentDate", 0L),
                note = obj.optString("note", ""),
                receivedBy = obj.optString("receivedBy", ""),
                createdAt = millisToTimestamp(obj.optLong("createdAtMillis", 0L))
            )
        }

        val supplierPayments = parseArray(optStrictArray(root, "supplierPayments", lang), "supplierPayments", lang) { obj ->
            val paymentNo = obj.optString("paymentNo", "").trim()
            val supplierId = obj.optString("supplierId", "").trim()
            val amount = safeDouble(obj, "amount")
            if (paymentNo.isBlank() || supplierId.isBlank() || amount <= 0.0) {
                throw BackupCodecException(ShopLocale.format("backup_err_corrupt_section", lang, "supplierPayments"))
            }
            SupplierPayment(
                id = obj.optString("id", "").trim(),
                storeId = obj.optString("storeId", "").trim(),
                paymentNo = paymentNo,
                supplierId = supplierId,
                supplierName = obj.optString("supplierName", ""),
                amount = amount,
                accountId = obj.optString("accountId", "cash_in_hand").ifBlank { "cash_in_hand" },
                accountName = obj.optString("accountName", "Cash in Hand").ifBlank { "Cash in Hand" },
                paymentDate = obj.optLong("paymentDate", 0L),
                note = obj.optString("note", ""),
                paidBy = obj.optString("paidBy", ""),
                createdAt = millisToTimestamp(obj.optLong("createdAtMillis", 0L))
            )
        }

        val accounts = parseArray(optStrictArray(root, "accounts", lang), "accounts", lang) { obj ->
            val name = obj.optString("name", "").trim()
            val type = obj.optString("type", "Cash").trim().let {
                if (it in listOf("Cash", "Bank", "Wallet")) it else "Cash"
            }
            if (name.isBlank()) {
                throw BackupCodecException(ShopLocale.format("backup_err_corrupt_section", lang, "accounts"))
            }
            Account(
                id = obj.optString("id", "").trim(),
                storeId = obj.optString("storeId", "").trim(),
                name = name,
                type = type,
                accountNumber = obj.optString("accountNumber", ""),
                openingBalance = safeDouble(obj, "openingBalance"),
                isDefault = obj.optBoolean("isDefault", false),
                isArchived = obj.optBoolean("isArchived", false),
                createdBy = obj.optString("createdBy", ""),
                createdAt = millisToTimestamp(obj.optLong("createdAtMillis", 0L)),
                updatedAt = millisToTimestamp(obj.optLong("updatedAtMillis", 0L))
            )
        }

        val adjustments = parseArray(optStrictArray(root, "accountAdjustments", lang), "accountAdjustments", lang) { obj ->
            val accountId = obj.optString("accountId", "").trim()
            val note = obj.optString("note", "").trim()
            if (accountId.isBlank() || note.isBlank()) {
                throw BackupCodecException(ShopLocale.format("backup_err_corrupt_section", lang, "accountAdjustments"))
            }
            AccountAdjustment(
                id = obj.optString("id", "").trim(),
                storeId = obj.optString("storeId", "").trim(),
                accountId = accountId,
                accountName = obj.optString("accountName", ""),
                oldBalance = safeDouble(obj, "oldBalance"),
                newBalance = safeDouble(obj, "newBalance"),
                difference = safeDouble(obj, "difference"),
                note = note,
                adjustedBy = obj.optString("adjustedBy", ""),
                createdAt = millisToTimestamp(obj.optLong("createdAtMillis", 0L))
            )
        }

        val expenses = parseArray(optStrictArray(root, "expenses", lang), "expenses", lang) { obj ->
            val amount = safeDouble(obj, "amount")
            val category = obj.optString("category", "").trim()
            if (amount <= 0.0 || category.isBlank()) {
                throw BackupCodecException(ShopLocale.format("backup_err_corrupt_section", lang, "expenses"))
            }
            Expense(
                id = obj.optString("id", "").trim(),
                storeId = obj.optString("storeId", "").trim(),
                amount = amount,
                category = category,
                accountId = obj.optString("accountId", "cash_in_hand").ifBlank { "cash_in_hand" },
                accountName = obj.optString("accountName", "Cash in Hand").ifBlank { "Cash in Hand" },
                date = obj.optLong("date", 0L),
                note = obj.optString("note", ""),
                receiptUri = obj.optString("receiptUri", ""),
                createdBy = obj.optString("createdBy", ""),
                isDeleted = obj.optBoolean("isDeleted", false),
                deletedNote = obj.optString("deletedNote", ""),
                deletedBy = obj.optString("deletedBy", ""),
                deletedAt = millisToTimestamp(obj.optLong("deletedAtMillis", 0L)),
                createdAt = millisToTimestamp(obj.optLong("createdAtMillis", 0L))
            )
        }

        val incomeArr = optStrictArray(root, "income", lang) ?: optStrictArray(root, "otherIncomes", lang)
        val otherIncomes = parseArray(incomeArr, "income", lang) { obj ->
            val amount = safeDouble(obj, "amount")
            val category = obj.optString("category", "").trim()
            if (amount <= 0.0 || category.isBlank()) {
                throw BackupCodecException(ShopLocale.format("backup_err_corrupt_section", lang, "income"))
            }
            OtherIncome(
                id = obj.optString("id", "").trim(),
                storeId = obj.optString("storeId", "").trim(),
                amount = amount,
                category = category,
                accountId = obj.optString("accountId", "cash_in_hand").ifBlank { "cash_in_hand" },
                accountName = obj.optString("accountName", "Cash in Hand").ifBlank { "Cash in Hand" },
                date = obj.optLong("date", 0L),
                note = obj.optString("note", ""),
                createdBy = obj.optString("createdBy", ""),
                isDeleted = obj.optBoolean("isDeleted", false),
                deletedNote = obj.optString("deletedNote", ""),
                deletedBy = obj.optString("deletedBy", ""),
                deletedAt = millisToTimestamp(obj.optLong("deletedAtMillis", 0L)),
                createdAt = millisToTimestamp(obj.optLong("createdAtMillis", 0L))
            )
        }

        val notes = parseArray(optStrictArray(root, "notes", lang), "notes", lang) { obj ->
            val title = obj.optString("title", "").trim()
            if (title.isBlank()) {
                throw BackupCodecException(ShopLocale.format("backup_err_corrupt_section", lang, "notes"))
            }
            StoreNote(
                id = obj.optString("id", "").trim(),
                storeId = obj.optString("storeId", "").trim(),
                title = title,
                text = obj.optString("text", ""),
                date = obj.optLong("date", 0L),
                createdBy = obj.optString("createdBy", ""),
                createdAt = millisToTimestamp(obj.optLong("createdAtMillis", 0L)),
                updatedAt = millisToTimestamp(obj.optLong("updatedAtMillis", 0L))
            )
        }

        val store = Store(
            id = storeObj.optString("id", ""),
            name = storeName,
            phone = storeObj.optString("phone", settingsObj?.optString("phone", "") ?: ""),
            address = storeObj.optString("address", settingsObj?.optString("address", "") ?: ""),
            shopType = shopType,
            productCategories = productCategories,
            ownerId = storeObj.optString("ownerId", "")
        )

        val settings = StoreBackupSettings(
            name = store.name,
            phone = store.phone,
            address = store.address,
            shopType = shopType,
            language = settingsObj?.optString("language", "en") ?: "en",
            isSimpleMode = settingsObj?.optBoolean("isSimpleMode", false) ?: false,
            photoStorageMode = settingsObj?.optString("photoStorageMode", "sync") ?: "sync"
        )

        return StoreBackupPayload(
            format = format,
            version = version,
            exportedAtMillis = root.optLong("exportedAtMillis", System.currentTimeMillis()),
            store = store,
            storeSettings = settings,
            shopType = shopType,
            productCategories = productCategories,
            categoryRecords = categoryRecords,
            units = StoreBackupUnits(presets = unitPresets, allUnits = allUnits),
            counters = counters,
            products = products,
            customers = customers,
            suppliers = suppliers,
            sales = sales,
            purchases = purchases,
            purchaseReturns = purchaseReturns,
            payments = payments,
            supplierPayments = supplierPayments,
            accounts = accounts,
            accountAdjustments = adjustments,
            expenses = expenses,
            otherIncomes = otherIncomes,
            notes = notes
        )
    }

    fun encodeCsv(target: CsvExportTarget, payload: StoreBackupPayload): ByteArray {
        val headers: List<String>
        val rows: List<List<String>>
        val numericCols: Set<Int>

        when (target) {
            CsvExportTarget.PRODUCTS -> {
                headers = listOf(
                    "ID", "Name", "Name (Bangla)", "Barcode", "Category",
                    "Unit", "Buy Unit", "Buy Conversion", "Buy Price", "Sell Price",
                    "Wholesale Price", "MRP", "Location", "Stock", "Low Stock Limit"
                )
                numericCols = setOf(7, 8, 9, 10, 11, 13, 14)
                rows = payload.products.map { p ->
                    listOf(
                        p.id, p.name, p.nameBn, p.barcode, p.category,
                        p.unit, p.buyUnit.ifBlank { p.unit }, formatNumber(p.buyConversion),
                        formatNumber(p.buyPrice), formatNumber(p.sellPrice),
                        formatNumber(p.wholesalePrice), formatNumber(p.mrp),
                        p.itemLocation, formatNumber(p.stock), formatNumber(p.lowStockLimit)
                    )
                }
            }
            CsvExportTarget.CUSTOMERS -> {
                headers = listOf("ID", "Name", "Mobile", "Address", "Current Balance", "Notes")
                numericCols = setOf(4)
                rows = payload.customers.map { c ->
                    listOf(c.id, c.name, c.mobile, c.address, formatNumber(c.currentBalance), c.notes)
                }
            }
            CsvExportTarget.SUPPLIERS -> {
                headers = listOf("ID", "Name", "Mobile", "Company", "Address", "Balance Due")
                numericCols = setOf(5)
                rows = payload.suppliers.map { s ->
                    listOf(s.id, s.name, s.mobile, s.company, s.address, formatNumber(s.balance))
                }
            }
            CsvExportTarget.SALES -> {
                headers = listOf(
                    "ID", "Invoice No", "Date", "Customer", "Customer Phone",
                    "Payment Type", "Account", "Items Qty", "Subtotal", "Discount",
                    "Grand Total", "Profit", "Notes"
                )
                numericCols = setOf(7, 8, 9, 10, 11)
                val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                rows = payload.sales.map { s ->
                    val ts = s.createdAt?.toDate()?.time ?: 0L
                    val dateStr = if (ts > 0L) dateFmt.format(Date(ts)) else ""
                    listOf(
                        s.id, s.invoiceNo, dateStr, s.customerName, s.customerPhone,
                        s.paymentType, s.accountName, formatNumber(s.totalItems),
                        formatNumber(s.subtotal), formatNumber(s.discount),
                        formatNumber(s.grandTotal), formatNumber(s.profit), s.notes
                    )
                }
            }
        }

        val baos = ByteArrayOutputStream()
        // UTF-8 BOM for direct Microsoft Excel compatibility
        baos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        val writer = baos.bufferedWriter(StandardCharsets.UTF_8)
        writer.write(headers.joinToString(",") { csvEscape(it, isNumeric = false) })
        writer.newLine()
        for (row in rows) {
            val line = row.mapIndexed { idx, value ->
                csvEscape(value, isNumeric = idx in numericCols)
            }.joinToString(",")
            writer.write(line)
            writer.newLine()
        }
        writer.flush()
        return baos.toByteArray()
    }

    private fun csvEscape(raw: String, isNumeric: Boolean): String {
        val trimmed = raw.trim()
        if (isNumeric) {
            return trimmed.ifBlank { "0" }
        }
        // Protect Excel/Sheets from CSV formula injection on text cells
        val safe = if (trimmed.isNotEmpty() && trimmed[0] in charArrayOf('=', '+', '-', '@')) {
            "'$trimmed"
        } else {
            trimmed
        }
        val mustQuote = safe.contains(',') || safe.contains('"') || safe.contains('\n') || safe.contains('\r')
        return if (mustQuote) {
            "\"${safe.replace("\"", "\"\"")}\""
        } else {
            safe
        }
    }

    private fun formatNumber(value: Double): String {
        if (!value.isFinite()) return "0"
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        }
    }

    private fun formatIsoUtc(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }

    private fun millisToTimestamp(millis: Long): Timestamp? {
        return if (millis > 0L) Timestamp(Date(millis)) else null
    }

    private fun safeDouble(obj: JSONObject, key: String, default: Double = 0.0): Double {
        if (!obj.has(key) || obj.isNull(key)) return default
        val raw = obj.optDouble(key, Double.NaN)
        return if (raw.isFinite()) raw else default
    }

    private fun optStrictObject(obj: JSONObject?, key: String, lang: AppLanguage): JSONObject? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        val value = obj.opt(key)
        return value as? JSONObject
            ?: throw BackupCodecException(ShopLocale.format("backup_err_corrupt_section", lang, key))
    }

    private fun optStrictArray(obj: JSONObject?, key: String, lang: AppLanguage): JSONArray? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        val value = obj.opt(key)
        return value as? JSONArray
            ?: throw BackupCodecException(ShopLocale.format("backup_err_corrupt_section", lang, key))
    }

    private fun parseStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "").trim()
            if (s.isNotBlank() && out.none { it.equals(s, ignoreCase = true) }) {
                out.add(s)
            }
        }
        return out
    }

    private fun <T> parseArray(
        arr: JSONArray?,
        sectionName: String,
        lang: AppLanguage,
        mapper: (JSONObject) -> T
    ): List<T> {
        if (arr == null) return emptyList()
        val out = ArrayList<T>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
                ?: throw BackupCodecException(ShopLocale.format("backup_err_corrupt_section", lang, sectionName))
            out.add(mapper(obj))
        }
        return out
    }
}
