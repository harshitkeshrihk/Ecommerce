package com.example.vishnu.di

import com.example.vishnu.repository.CartDataSource
import com.example.vishnu.repository.CurrentUserProvider
import com.example.vishnu.repository.GiftingOrderDataSource
import com.example.vishnu.repository.OrderDataSource
import com.example.vishnu.repository.SupabaseCartDataSource
import com.example.vishnu.repository.SupabaseCurrentUserProvider
import com.example.vishnu.repository.SupabaseGiftingOrderDataSource
import com.example.vishnu.repository.SupabaseOrderDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {

    @Binds
    abstract fun bindCurrentUserProvider(impl: SupabaseCurrentUserProvider): CurrentUserProvider

    @Binds
    abstract fun bindCartDataSource(impl: SupabaseCartDataSource): CartDataSource

    @Binds
    abstract fun bindOrderDataSource(impl: SupabaseOrderDataSource): OrderDataSource

    @Binds
    abstract fun bindGiftingOrderDataSource(impl: SupabaseGiftingOrderDataSource): GiftingOrderDataSource
}
