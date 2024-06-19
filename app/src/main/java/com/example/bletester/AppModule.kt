package com.example.bletester

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.example.bletester.ble.BleControlManager
import com.example.bletester.viewModels.ReportViewModel
import com.example.bletester.viewModels.ScanViewModel
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
    @Singleton
    fun provideBleControlManager(@ApplicationContext context: Context,bluetoothAdapter: BluetoothAdapter): BleControlManager {
        return BleControlManager(context)
    }

    @Provides
    @Singleton
    fun provideReportViewModel(@ApplicationContext context: Context): ReportViewModel {
        return ReportViewModel(context)
    }

    @Provides
    @Singleton
    fun provideScanViewModel(
        bleControlManager: BleControlManager,
        reportViewModel: ReportViewModel
    ): ScanViewModel {
        return ScanViewModel(bleControlManager, reportViewModel)
    }

    @Provides
    @Singleton
    fun provideLogger(): Logger {
        return Logger
    }
}
