package kz.alem.corporation.fintracker.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildFriendly
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightbulbCircle
import androidx.compose.material.icons.filled.LocalActivity
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.OilBarrel
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHorizontalCircle
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Watch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kz.alem.corporation.fintracker.data.model.AccountIcons
import kz.alem.corporation.fintracker.data.model.CategoryGroupIcons
import kz.alem.corporation.fintracker.data.model.CategoryIcons
import kz.alem.corporation.fintracker.data.model.CategoryType
import kz.alem.corporation.fintracker.data.model.OperationType
import kz.alem.corporation.fintracker.ui.theme.TypeColors

// Material equivalents of the iOS SF Symbols. Only the look differs per platform; the
// enum values themselves are shared through sync.

val AccountIcons.imageVector: ImageVector
    get() = when (this) {
        AccountIcons.creditcard -> Icons.Filled.CreditCard
        AccountIcons.dollar -> Icons.Filled.AttachMoney
        AccountIcons.bitcoin -> Icons.Filled.CurrencyBitcoin
        AccountIcons.persons -> Icons.Filled.People
        AccountIcons.banknote -> Icons.Filled.Payments
        AccountIcons.giftcard -> Icons.Filled.CardGiftcard
        AccountIcons.bag -> Icons.Filled.ShoppingBag
        AccountIcons.pages -> Icons.AutoMirrored.Filled.MenuBook
        AccountIcons.person -> Icons.Filled.Person
        AccountIcons.storefront -> Icons.Filled.Storefront
        AccountIcons.archivebox -> Icons.Filled.Archive
        AccountIcons.mail -> Icons.Filled.Mail
        AccountIcons.briefcase -> Icons.Filled.BusinessCenter
        AccountIcons.house -> Icons.Filled.Home
        AccountIcons.building -> Icons.Filled.AccountBalance
    }

val CategoryGroupIcons.imageVector: ImageVector
    get() = when (this) {
        CategoryGroupIcons.cart -> Icons.Filled.ShoppingCart
        CategoryGroupIcons.car -> Icons.Filled.DirectionsCar
        CategoryGroupIcons.house -> Icons.Filled.Home
        CategoryGroupIcons.basket -> Icons.Filled.ShoppingBasket
        CategoryGroupIcons.building -> Icons.Filled.Business
        CategoryGroupIcons.medicine -> Icons.Filled.MedicalServices
        CategoryGroupIcons.bus -> Icons.Filled.DirectionsBus
        CategoryGroupIcons.travel -> Icons.Filled.BeachAccess
        CategoryGroupIcons.education -> Icons.Filled.School
        CategoryGroupIcons.device -> Icons.Filled.Devices
        CategoryGroupIcons.map -> Icons.Filled.Map
        CategoryGroupIcons.entertainment -> Icons.Filled.Theaters
        CategoryGroupIcons.globe -> Icons.Filled.Public
        CategoryGroupIcons.store -> Icons.Filled.Storefront
        CategoryGroupIcons.light -> Icons.Filled.LightbulbCircle
    }

val CategoryIcons.imageVector: ImageVector
    get() = when (this) {
        CategoryIcons.cart -> Icons.Filled.ShoppingCart
        CategoryIcons.car -> Icons.Filled.DirectionsCar
        CategoryIcons.house -> Icons.Filled.Home
        CategoryIcons.work -> Icons.Filled.Business
        CategoryIcons.medicine -> Icons.Filled.MedicalServices
        CategoryIcons.docs -> Icons.Filled.FileCopy
        CategoryIcons.list -> Icons.AutoMirrored.Filled.Assignment
        CategoryIcons.pages -> Icons.AutoMirrored.Filled.MenuBook
        CategoryIcons.book -> Icons.Filled.Book
        CategoryIcons.magazine -> Icons.Filled.AutoStories
        CategoryIcons.newspaper -> Icons.Filled.Newspaper
        CategoryIcons.education -> Icons.Filled.School
        CategoryIcons.sport -> Icons.Filled.SportsTennis
        CategoryIcons.swim -> Icons.Filled.Pool
        CategoryIcons.cycle -> Icons.AutoMirrored.Filled.DirectionsBike
        CategoryIcons.ball -> Icons.Filled.SportsSoccer
        CategoryIcons.trophy -> Icons.Filled.EmojiEvents
        CategoryIcons.umbrella -> Icons.Filled.BeachAccess
        CategoryIcons.music -> Icons.Filled.MusicNote
        CategoryIcons.microphone -> Icons.Filled.Mic
        CategoryIcons.basket -> Icons.Filled.ShoppingBasket
        CategoryIcons.wrenchscrewdriver -> Icons.Filled.Build
        CategoryIcons.storefront -> Icons.Filled.Storefront
        CategoryIcons.party -> Icons.Filled.Celebration
        CategoryIcons.popcorn -> Icons.Filled.LocalActivity
        CategoryIcons.building -> Icons.Filled.Apartment
        CategoryIcons.phone -> Icons.Filled.PhoneAndroid
        CategoryIcons.watch -> Icons.Filled.Watch
        CategoryIcons.tv -> Icons.Filled.Tv
        CategoryIcons.boltcar -> Icons.Filled.ElectricCar
        CategoryIcons.bus -> Icons.Filled.DirectionsBus
        CategoryIcons.truck -> Icons.Filled.LocalShipping
        CategoryIcons.stroller -> Icons.Filled.ChildFriendly
        CategoryIcons.fuelpump -> Icons.Filled.LocalGasStation
        CategoryIcons.engine -> Icons.Filled.CarRepair
        CategoryIcons.oilcan -> Icons.Filled.OilBarrel
        CategoryIcons.syringe -> Icons.Filled.Vaccines
        CategoryIcons.dog -> Icons.Filled.Pets
        CategoryIcons.toys -> Icons.Filled.Toys
        CategoryIcons.tshirt -> Icons.Filled.Checkroom
        CategoryIcons.flower -> Icons.Filled.LocalFlorist
        CategoryIcons.movie -> Icons.Filled.Movie
        CategoryIcons.gamecontroller -> Icons.Filled.SportsEsports
        CategoryIcons.cup -> Icons.Filled.LocalCafe
        CategoryIcons.carrot -> Icons.Filled.Eco
        CategoryIcons.cake -> Icons.Filled.Cake
        CategoryIcons.forkknife -> Icons.Filled.Restaurant
        CategoryIcons.gift -> Icons.Filled.Redeem
        CategoryIcons.paintbrush -> Icons.Filled.Brush
        CategoryIcons.chair -> Icons.Filled.Chair
    }

val OperationType.color: Color
    get() = when (this) {
        OperationType.EXPENSE -> TypeColors.expense
        OperationType.INCOME -> TypeColors.income
        OperationType.TRANSFER -> TypeColors.transfer
    }

val OperationType.icon: ImageVector
    get() = when (this) {
        OperationType.EXPENSE -> Icons.Filled.RemoveCircleOutline
        OperationType.INCOME -> Icons.Filled.AddCircleOutline
        OperationType.TRANSFER -> Icons.Filled.SwapHorizontalCircle
    }

val CategoryType.color: Color
    get() = when (this) {
        CategoryType.EXPENSE -> TypeColors.expense
        CategoryType.INCOME -> TypeColors.income
        CategoryType.MIXED -> TypeColors.transfer
    }
