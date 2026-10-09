package app.ash.di

import app.ash.common.SystemTimeProvider
import app.ash.common.TimeProvider
import app.ash.data.repository.DocumentRepositoryImpl
import app.ash.data.repository.LifeRepositoryImpl
import app.ash.data.repository.ProfileRepositoryImpl
import app.ash.data.repository.RecordRepositoryImpl
import app.ash.data.repository.ReminderRepositoryImpl
import app.ash.data.repository.SourceRepositoryImpl
import app.ash.document.MlKitOcrEngine
import app.ash.document.OcrEngine
import app.ash.domain.repository.DocumentRepository
import app.ash.domain.repository.LifeRepository
import app.ash.domain.repository.ProfileRepository
import app.ash.domain.repository.RecordRepository
import app.ash.domain.repository.ReminderRepository
import app.ash.domain.repository.SourceRepository
import app.ash.intelligence.IntelligenceEngine
import app.ash.intelligence.RuleBasedIntelligenceEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds abstract fun timeProvider(impl: SystemTimeProvider): TimeProvider

    /** Swap here for an OnDeviceAIEngine in a future version; nothing else changes. */
    @Binds abstract fun intelligenceEngine(impl: RuleBasedIntelligenceEngine): IntelligenceEngine

    @Binds abstract fun ocrEngine(impl: MlKitOcrEngine): OcrEngine

    @Binds abstract fun recordRepository(impl: RecordRepositoryImpl): RecordRepository
    @Binds abstract fun lifeRepository(impl: LifeRepositoryImpl): LifeRepository
    @Binds abstract fun profileRepository(impl: ProfileRepositoryImpl): ProfileRepository
    @Binds abstract fun documentRepository(impl: DocumentRepositoryImpl): DocumentRepository
    @Binds abstract fun reminderRepository(impl: ReminderRepositoryImpl): ReminderRepository
    @Binds abstract fun sourceRepository(impl: SourceRepositoryImpl): SourceRepository
}
