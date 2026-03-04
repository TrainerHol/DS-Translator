package com.dstranslator.di

import android.content.Context
import com.dstranslator.data.segmentation.SudachiSegmenter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SegmentationModule {

    @Provides
    @Singleton
    fun provideSudachiSegmenter(
        @ApplicationContext context: Context
    ): SudachiSegmenter {
        return SudachiSegmenter(context)
    }
}
