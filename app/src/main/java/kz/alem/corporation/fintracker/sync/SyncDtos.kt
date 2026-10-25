package kz.alem.corporation.fintracker.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Wire format shared with iOS and the backend (fintracker-backend/src/types/dtos.ts).
// Field names must match exactly.

@Serializable
data class CurrencyDto(
    val remoteId: String,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val name: String,
    val code: String,
    val symbol: String,
    val useAsDefault: Boolean,
    val colorArgb: Int,
)

@Serializable
data class AccountDto(
    val remoteId: String,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val type: Int,
    val name: String,
    val mainBalance: Double,
    val currencyRemoteId: String,
    val includeInTotal: Boolean,
    val useAsDefault: Boolean,
    val visible: Boolean,
    val colorArgb: Int,
    val iconName: String,
)

@Serializable
data class CategoryGroupDto(
    val remoteId: String,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val name: String,
    val useAsDefault: Boolean,
    val colorArgb: Int,
    val iconName: String,
)

@Serializable
data class CategoryDto(
    val remoteId: String,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val groupRemoteId: String,
    val type: Int,
    val name: String,
    val useAsDefault: Boolean,
    val visible: Boolean,
    val colorArgb: Int,
    val iconName: String,
)

@Serializable
data class OperationDto(
    val remoteId: String,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val dateTimeMillis: Long,
    val type: Int,
    val accountRemoteId: String,
    /** Transfer destination, empty for non-transfers. */
    val addAccountRemoteId: String,
    /** Empty when there is no category. */
    val categoryRemoteId: String,
    val quantity: Double,
    val amount: Double,
    val exchangeRate: Double,
    val mainRate: Int,
    val comment: String,
    val confirmed: Boolean,
)

@Serializable
data class PlanDto(
    val remoteId: String,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val name: String,
    val type: Int,
    val beginDateMillis: Long,
    val endDateMillis: Long,
    val amount: Double,
    val currencyRemoteId: String,
    val categoryRemoteIds: List<String> = emptyList(),
    val categoryGroupRemoteIds: List<String> = emptyList(),
)

@Serializable
data class TombstoneDto(
    val remoteId: String,
    val collectionName: String,
    val deletedAt: Long,
)

@Serializable
data class UploadPayload(
    val currencies: List<CurrencyDto>,
    val accounts: List<AccountDto>,
    val categoryGroups: List<CategoryGroupDto>,
    val categories: List<CategoryDto>,
    val operations: List<OperationDto>,
    val plans: List<PlanDto>,
    val tombstones: List<TombstoneDto>,
    val clientSyncAt: Long,
    /** Omitted from the JSON when null (the backend rejects an explicit null). */
    val deviceToken: String? = null,
)

@Serializable
data class UploadResponse(
    val syncedAt: Long,
    val uploadedCount: Int = 0,
    val conflicts: List<ConflictDto> = emptyList(),
)

/** The server's winning version of an entity; decoded by [collectionName] in the engine. */
@Serializable
data class ConflictDto(
    val remoteId: String,
    val collectionName: String,
    val serverData: JsonElement? = null,
)

@Serializable
data class PullResponse(
    val syncedAt: Long,
    val currencies: List<CurrencyDto> = emptyList(),
    val accounts: List<AccountDto> = emptyList(),
    val categoryGroups: List<CategoryGroupDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    val operations: List<OperationDto> = emptyList(),
    val plans: List<PlanDto> = emptyList(),
    val deletedIds: DeletedIds? = null,
) {
    @Serializable
    data class DeletedIds(
        val currencies: List<String> = emptyList(),
        val accounts: List<String> = emptyList(),
        val categoryGroups: List<String> = emptyList(),
        val categories: List<String> = emptyList(),
        val operations: List<String> = emptyList(),
        val plans: List<String> = emptyList(),
    )
}

@Serializable
data class SubscriptionDto(
    val active: Boolean = false,
    val expiresAt: Long? = null,
    val platform: String? = null,
    val productId: String? = null,
    /** null = unknown, false = cancelled but active until expiresAt. */
    val autoRenewStatus: Boolean? = null,
)

@Serializable
data class AndroidVerifyRequest(val productId: String, val purchaseToken: String)

@Serializable
data class RegisterDeviceRequest(val token: String, val platform: String)

@Serializable
data class UnregisterDeviceRequest(val token: String)
