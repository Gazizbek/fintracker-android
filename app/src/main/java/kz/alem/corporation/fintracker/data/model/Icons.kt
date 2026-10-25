@file:Suppress("EnumEntryName")

package kz.alem.corporation.fintracker.data.model

// Icon enums. The constant names are exactly the iOS enum case names: `name` is what
// gets stored locally and sent to the backend as `iconName`, so iOS and Android must
// agree on every value. Only append new icons, never rename or reorder existing ones.
// The Material icon for each value lives in ui/common/IconMapping.kt.

enum class AccountIcons {
    creditcard,
    dollar,
    bitcoin,
    persons,
    banknote,
    giftcard,
    bag,
    pages,
    person,
    storefront,
    archivebox,
    mail,
    briefcase,
    house,
    building;

    companion object {
        fun fromRaw(raw: String?): AccountIcons = entries.firstOrNull { it.name == raw } ?: creditcard
    }
}

enum class CategoryGroupIcons {
    cart,
    car,
    house,
    basket,
    building,

    medicine,
    bus,
    travel,
    education,
    device,

    map,
    entertainment,
    globe,
    store,
    light;

    companion object {
        fun fromRaw(raw: String?): CategoryGroupIcons = entries.firstOrNull { it.name == raw } ?: basket
    }
}

enum class CategoryIcons {
    cart,
    car,
    house,
    work,
    medicine,

    docs,
    list,
    pages,
    book,
    magazine,

    newspaper,
    education,
    sport,
    swim,
    cycle,

    ball,
    trophy,
    umbrella,
    music,
    microphone,

    basket,
    wrenchscrewdriver,
    storefront,
    party,
    popcorn,

    building,
    phone,
    watch,
    tv,
    boltcar,

    bus,
    truck,
    stroller,
    fuelpump,
    engine,

    oilcan,
    syringe,
    dog,
    toys,
    tshirt,

    flower,
    movie,
    gamecontroller,
    cup,
    carrot,

    cake,
    forkknife,
    gift,
    paintbrush,
    chair;

    companion object {
        fun fromRaw(raw: String?): CategoryIcons = entries.firstOrNull { it.name == raw } ?: basket
    }
}
