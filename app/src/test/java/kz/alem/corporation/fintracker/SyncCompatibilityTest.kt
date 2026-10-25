package kz.alem.corporation.fintracker

import kotlinx.serialization.json.jsonObject
import kz.alem.corporation.fintracker.data.model.AccountIcons
import kz.alem.corporation.fintracker.data.model.AccountType
import kz.alem.corporation.fintracker.data.model.CategoryGroupIcons
import kz.alem.corporation.fintracker.data.model.CategoryIcons
import kz.alem.corporation.fintracker.data.model.CategoryType
import kz.alem.corporation.fintracker.data.model.OperationType
import kz.alem.corporation.fintracker.sync.PullResponse
import kz.alem.corporation.fintracker.sync.UploadPayload
import kz.alem.corporation.fintracker.sync.syncJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Values that travel between iOS, Android and the backend must never drift. */
class SyncCompatibilityTest {

    @Test
    fun accountIconNamesMatchIos() {
        assertEquals(
            listOf("creditcard", "dollar", "bitcoin", "persons", "banknote", "giftcard", "bag", "pages", "person",
                "storefront", "archivebox", "mail", "briefcase", "house", "building"),
            AccountIcons.entries.map { it.name },
        )
    }

    @Test
    fun categoryGroupIconNamesMatchIos() {
        assertEquals(
            listOf("cart", "car", "house", "basket", "building", "medicine", "bus", "travel", "education", "device",
                "map", "entertainment", "globe", "store", "light"),
            CategoryGroupIcons.entries.map { it.name },
        )
    }

    @Test
    fun categoryIconNamesMatchIos() {
        assertEquals(
            listOf(
                "cart", "car", "house", "work", "medicine", "docs", "list", "pages", "book", "magazine",
                "newspaper", "education", "sport", "swim", "cycle", "ball", "trophy", "umbrella", "music", "microphone",
                "basket", "wrenchscrewdriver", "storefront", "party", "popcorn", "building", "phone", "watch", "tv", "boltcar",
                "bus", "truck", "stroller", "fuelpump", "engine", "oilcan", "syringe", "dog", "toys", "tshirt",
                "flower", "movie", "gamecontroller", "cup", "carrot", "cake", "forkknife", "gift", "paintbrush", "chair",
            ),
            CategoryIcons.entries.map { it.name },
        )
    }

    @Test
    fun unknownIconFallsBackLikeIos() {
        assertEquals(AccountIcons.creditcard, AccountIcons.fromRaw("wallet"))
        assertEquals(CategoryIcons.basket, CategoryIcons.fromRaw(null))
        assertEquals(CategoryGroupIcons.basket, CategoryGroupIcons.fromRaw(""))
    }

    @Test
    fun rawValuesMatchIos() {
        assertEquals(listOf("expense", "income", "transfer"), OperationType.entries.map { it.raw })
        assertEquals(listOf("expenseCategory", "incomeCategory", "mixedCategory"), CategoryType.entries.map { it.raw })
        assertEquals(
            listOf("debitCard", "creditCard", "deposit", "credit", "cash", "savings", "debt"),
            AccountType.entries.map { it.raw },
        )
    }

    @Test
    fun serverEnumMapping() {
        assertEquals(listOf(0, 1, 2), OperationType.entries.map { it.serverValue })
        assertEquals(listOf(0, 1, 2), CategoryType.entries.map { it.serverValue })
        assertEquals(listOf(0, 0, 1, 0, 0, 1, 2), AccountType.entries.map { it.serverValue })
        assertEquals(AccountType.CREDIT_CARD, AccountType.fromServer(0))
        assertEquals(AccountType.DEPOSIT, AccountType.fromServer(1))
        assertEquals(AccountType.DEBT, AccountType.fromServer(2))
    }

    @Test
    fun uploadOmitsNullDeviceTokenAndSendsIsDeleted() {
        val payload = UploadPayload(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 5L, null)
        val json = syncJson.encodeToString(UploadPayload.serializer(), payload)
        assertFalse(json.contains("deviceToken"))
        val withCurrency = syncJson.encodeToString(
            kz.alem.corporation.fintracker.sync.CurrencyDto.serializer(),
            kz.alem.corporation.fintracker.sync.CurrencyDto("id", 1, name = "Dollar", code = "USD", symbol = "$", useAsDefault = false, colorArgb = -16776961),
        )
        val obj = syncJson.parseToJsonElement(withCurrency).jsonObject
        assertEquals("false", obj["isDeleted"].toString())
        assertEquals("-16776961", obj["colorArgb"].toString())
    }

    @Test
    fun pullDecodesWithoutPlansOrDeletedIds() {
        val response = syncJson.decodeFromString(
            PullResponse.serializer(),
            """{"syncedAt":10,"currencies":[],"accounts":[],"categoryGroups":[],"categories":[],"operations":[],"extra":1}""",
        )
        assertEquals(10L, response.syncedAt)
        assertEquals(0, response.plans.size)
    }
}
