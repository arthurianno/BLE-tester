package com.example.bletester.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import java.util.UUID;
import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.callback.DataReceivedCallback;

public class BleControlManager extends BleManager {

    private static final UUID UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID UART_RX_CHARACTERISTIC_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID UART_TX_CHARACTERISTIC_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

    private static BluetoothGattCharacteristic controlRequest;
    private BluetoothGattCharacteristic controlResponse;
    public BleControlManager(@NonNull Context context) {
        super(context);
    }

    @NonNull
    @Override
    protected BleManagerGattCallback getGattCallback() {
        return new BleControlManagerGattCallBacks();
    }

    class BleControlManagerGattCallBacks extends BleManagerGattCallback {

        @Override
        protected boolean isRequiredServiceSupported(@NonNull BluetoothGatt gatt) {
            BluetoothGattService controlService = gatt.getService(UART_SERVICE_UUID);
            if (controlService != null) {
                controlRequest = controlService.getCharacteristic(UART_RX_CHARACTERISTIC_UUID);
                controlResponse = controlService.getCharacteristic(UART_TX_CHARACTERISTIC_UUID);
            }
            return controlRequest != null && controlResponse != null;
        }

        @Override
        protected void onDeviceReady() {
            super.onDeviceReady();
            setNotificationCallback(controlResponse).with(notificationCallback);
            enableNotifications(controlResponse).enqueue();
        }

        @Override
        protected void onServicesInvalidated() {
            controlRequest = null;
            controlResponse = null;
            disconnect().enqueue();
            BleControlManager.this.close();

        }

        private final DataReceivedCallback notificationCallback = (device, data) -> {
            // Обработка данных, полученных от controlResponse
            // data - это массив байтов, которые представляют ответ от устройства
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                handleResponseData(data.getValue());
            }
        };

        @RequiresApi(api = Build.VERSION_CODES.O)
        private void handleResponseData(byte[] data) {

        }
    }
}
