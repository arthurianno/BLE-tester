package com.example.bletester

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.work.WorkManager
import com.example.bletester.ble.BleControlManager
import com.example.bletester.viewModels.ReportViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@SuppressLint("StaticFieldLeak")
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private lateinit var context: Context

    @Provides
    @Singleton
    fun provideBluetoothAdapter(@ApplicationContext context: Context):BluetoothAdapter{
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return manager.adapter
    }

    @Provides
    @Singleton
    fun provideBleControlManager(bluetoothAdapter: BluetoothAdapter,@ApplicationContext context: Context): BleControlManager {
        return BleControlManager(context)
    }

    @Provides
    @Singleton
    fun provideReportViewModel(@ApplicationContext context: Context): ReportViewModel {
        return ReportViewModel(context)
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

}