package com.example.bletester.core

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.example.bletester.ble.BleControlManager
import com.example.bletester.services.ScanningService
import com.example.bletester.ui.theme.devicesList.ScanViewModel
import com.example.bletester.ui.theme.log.Logger
import com.example.bletester.ui.theme.report.ReportViewModel
import com.example.bletester.utils.FileObserver
import com.example.bletester.utils.IniUtil
import com.example.bletester.utils.SharedData
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBluetoothAdapter(@ApplicationContext context: Context): BluetoothAdapter {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return manager.adapter
    }

    @Provides
    fun provideBleControlManager(@ApplicationContext context: Context): BleControlManager {
        return BleControlManager(context)
    }

    @Provides
    @Singleton
    fun provideReportViewModel(
        iniUtil: IniUtil,
        sharedData: SharedData,
        fileObserver: FileObserver
    ): ReportViewModel {
        return ReportViewModel(sharedData, iniUtil, fileObserver)
    }

    @Provides
    @Singleton
    fun provideScanViewModel(
        scanningService: ScanningService,
        sharedData: SharedData
    ): ScanViewModel {
        return ScanViewModel(scanningService,sharedData)
    }

    @Provides
    @Singleton
    fun provideLogger(): Logger {
        return Logger
    }

    @Provides
    @Singleton
    fun provideIniUtil(sharedData: SharedData): IniUtil {
        return IniUtil(sharedData)
    }

    @Provides
    @Singleton
    fun provideSharedData(): SharedData {
        return SharedData()
    }

    @Provides
    @Singleton
    fun provideFileObserver(sharedData: SharedData): FileObserver {
        return FileObserver(sharedData)
    }

    @Provides
    @Singleton
    fun provideScanningService(
        @ApplicationContext context: Context,
        reportViewModel: ReportViewModel,
        deviceProcessor: DeviceProcessor,
        sharedData: SharedData,
        iniUtil: IniUtil
    ): ScanningService {
        return ScanningService(context,reportViewModel,deviceProcessor,sharedData,iniUtil)
    }

    @Provides
    @Singleton
    fun provideDeviceProcessor(
        reportViewModel: ReportViewModel,
        sharedData: SharedData
    ): DeviceProcessor {
        return DeviceProcessor(reportViewModel,sharedData)
    }
}
