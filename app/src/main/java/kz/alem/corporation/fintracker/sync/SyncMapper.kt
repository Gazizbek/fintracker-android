package kz.alem.corporation.fintracker.sync

import kz.alem.corporation.fintracker.data.calculateSum
import kz.alem.corporation.fintracker.data.model.Account
import kz.alem.corporation.fintracker.data.model.AccountType
import kz.alem.corporation.fintracker.data.model.Category
import kz.alem.corporation.fintracker.data.model.CategoryGroup
import kz.alem.corporation.fintracker.data.model.CategoryType
import kz.alem.corporation.fintracker.data.model.Currency
import kz.alem.corporation.fintracker.data.model.Operation
import kz.alem.corporation.fintracker.data.model.OperationType
import kz.alem.corporation.fintracker.data.model.Plan
import kz.alem.corporation.fintracker.data.model.Tombstone

/**
 * Entity <-> DTO mapping. Colors are already ARGB ints locally, so unlike iOS there is
 * no hex conversion. `apply` overwrites an entity with server data (keeping its local id)
 * and marks it synced.
 */
object SyncMapper {

    fun toDto(e: Currency) = CurrencyDto(
        remoteId = e.remoteId,
        updatedAt = e.updatedAt,
        name = e.name,
        code = e.code,
        symbol = e.symbol,
        useAsDefault = e.useAsDefault,
        colorArgb = e.color,
    )

    fun toDto(e: Account) = AccountDto(
        remoteId = e.remoteId,
        updatedAt = e.updatedAt,
        type = e.typeEnum.serverValue,
        name = e.name,
        mainBalance = e.mainBalance,
        currencyRemoteId = e.currency.target?.remoteId.orEmpty(),
        includeInTotal = e.inSummary,
        useAsDefault = e.useAsDefault,
        visible = e.visible,
        colorArgb = e.color,
        iconName = e.icon,
    )

    fun toDto(e: CategoryGroup) = CategoryGroupDto(
        remoteId = e.remoteId,
        updatedAt = e.updatedAt,
        name = e.name,
        useAsDefault = e.useAsDefault,
        colorArgb = e.color,
        iconName = e.icon,
    )

    fun toDto(e: Category) = CategoryDto(
        remoteId = e.remoteId,
        updatedAt = e.updatedAt,
        groupRemoteId = e.categoryGroup.target?.remoteId.orEmpty(),
        type = e.typeEnum.serverValue,
        name = e.name,
        useAsDefault = e.useAsDefault,
        visible = e.visible,
        colorArgb = e.color,
        iconName = e.icon,
    )

    fun toDto(e: Operation) = OperationDto(
        remoteId = e.remoteId,
        updatedAt = e.updatedAt,
        dateTimeMillis = e.date,
        type = e.typeEnum.serverValue,
        accountRemoteId = e.account.target?.remoteId.orEmpty(),
        addAccountRemoteId = e.addAccount.target?.remoteId.orEmpty(),
        categoryRemoteId = e.category.target?.remoteId.orEmpty(),
        quantity = e.quantity,
        amount = e.amount,
        exchangeRate = e.rate,
        mainRate = e.mainRate,
        comment = e.note.orEmpty(),
        confirmed = e.confirmed,
    )

    fun toDto(e: Plan) = PlanDto(
        remoteId = e.remoteId,
        updatedAt = e.updatedAt,
        name = e.name,
        type = e.typeEnum.serverValue,
        beginDateMillis = e.beginDate,
        endDateMillis = e.endDate,
        amount = e.amount,
        currencyRemoteId = e.currency.target?.remoteId.orEmpty(),
        categoryRemoteIds = e.categories.map { it.remoteId }.filter { it.isNotEmpty() },
        categoryGroupRemoteIds = e.categoryGroups.map { it.remoteId }.filter { it.isNotEmpty() },
    )

    fun toDto(t: Tombstone) = TombstoneDto(t.remoteId, t.collectionName, t.deletedAt)

    fun apply(dto: CurrencyDto, entity: Currency) {
        entity.remoteId = dto.remoteId
        entity.name = dto.name
        entity.code = dto.code
        entity.symbol = dto.symbol
        entity.useAsDefault = dto.useAsDefault
        entity.color = dto.colorArgb
        entity.updatedAt = dto.updatedAt
        entity.synced = true
    }

    fun apply(dto: AccountDto, entity: Account, currencyByRemoteId: Map<String, Currency>) {
        entity.remoteId = dto.remoteId
        entity.name = dto.name
        entity.typeEnum = AccountType.fromServer(dto.type)
        entity.mainBalance = dto.mainBalance
        entity.currency.target = currencyByRemoteId[dto.currencyRemoteId]
        entity.inSummary = dto.includeInTotal
        entity.useAsDefault = dto.useAsDefault
        entity.visible = dto.visible
        entity.color = dto.colorArgb
        entity.icon = dto.iconName
        entity.updatedAt = dto.updatedAt
        entity.synced = true
    }

    fun apply(dto: CategoryGroupDto, entity: CategoryGroup) {
        entity.remoteId = dto.remoteId
        entity.name = dto.name
        entity.useAsDefault = dto.useAsDefault
        entity.color = dto.colorArgb
        entity.icon = dto.iconName
        entity.updatedAt = dto.updatedAt
        entity.synced = true
    }

    fun apply(dto: CategoryDto, entity: Category, groupByRemoteId: Map<String, CategoryGroup>) {
        entity.remoteId = dto.remoteId
        entity.name = dto.name
        entity.typeEnum = CategoryType.fromServer(dto.type)
        entity.categoryGroup.target = groupByRemoteId[dto.groupRemoteId]
        entity.useAsDefault = dto.useAsDefault
        entity.visible = dto.visible
        entity.color = dto.colorArgb
        entity.icon = dto.iconName
        entity.updatedAt = dto.updatedAt
        entity.synced = true
    }

    fun apply(
        dto: OperationDto,
        entity: Operation,
        accountByRemoteId: Map<String, Account>,
        categoryByRemoteId: Map<String, Category>,
    ) {
        entity.remoteId = dto.remoteId
        entity.date = dto.dateTimeMillis
        entity.typeEnum = OperationType.fromServer(dto.type)
        entity.account.target = accountByRemoteId[dto.accountRemoteId]
        entity.addAccount.target = accountByRemoteId[dto.addAccountRemoteId]
        entity.category.target = categoryByRemoteId[dto.categoryRemoteId]
        entity.quantity = dto.quantity
        entity.amount = dto.amount
        entity.rate = dto.exchangeRate
        entity.mainRate = dto.mainRate
        // sum is local-only, so recompute it the same way the operation form does.
        entity.sum = calculateSum(dto.amount * dto.quantity, dto.exchangeRate, dto.mainRate)
        entity.note = dto.comment.ifEmpty { null }
        entity.confirmed = dto.confirmed
        entity.updatedAt = dto.updatedAt
        entity.synced = true
    }

    fun apply(
        dto: PlanDto,
        entity: Plan,
        currencyByRemoteId: Map<String, Currency>,
        categoryByRemoteId: Map<String, Category>,
        groupByRemoteId: Map<String, CategoryGroup>,
    ) {
        entity.remoteId = dto.remoteId
        entity.name = dto.name
        entity.typeEnum = OperationType.fromServer(dto.type)
        entity.beginDate = dto.beginDateMillis
        entity.endDate = dto.endDateMillis
        entity.amount = dto.amount
        entity.currency.target = currencyByRemoteId[dto.currencyRemoteId]
        entity.categories.clear()
        entity.categories.addAll(dto.categoryRemoteIds.mapNotNull { categoryByRemoteId[it] })
        entity.categoryGroups.clear()
        entity.categoryGroups.addAll(dto.categoryGroupRemoteIds.mapNotNull { groupByRemoteId[it] })
        entity.updatedAt = dto.updatedAt
        entity.synced = true
    }
}
