package com.dstranslator.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for segmentation dependencies.
 *
 * SudachiSegmenter and FuriganaResolver use constructor injection
 * (@Inject constructor with @Singleton) so no explicit @Provides needed.
 * This module is retained as a placeholder for future segmentation bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
object SegmentationModule
