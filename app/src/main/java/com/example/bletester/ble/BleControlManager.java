package com.example.bletester.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.example.bletester.EntireCheck;

import com.example.bletester.viewModels.ScanViewModel;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.callback.DataReceivedCallback;

public class BleControlManager extends BleManager {

    private static final UUID UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID UART_RX_CHARACTERISTIC_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID UART_TX_CHARACTERISTIC_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    public String serialNumber = "";

    private static BluetoothGattCharacteristic controlRequest;
    private BluetoothGattCharacteristic controlResponse;
    public BleControlManager(@NonNull Context context) {
        super(context);
    }
    BleCallbackEvent bleCallbackEvent;

    public void setBleCallbackEvent(BleCallbackEvent bleCallbackEvent) {
        this.bleCallbackEvent = bleCallbackEvent;
    }
    @NonNull
    @Override
    protected BleManagerGattCallback getGattCallback() {
        return new BleControlManagerGattCallBacks();
    }


    public void sendCommand(String command, EntireCheck entireCheck) {
        if (isConnected() && controlRequest != null) {
            BluetoothGattCharacteristic characteristic = controlRequest;
            ScanViewModel.Companion.getEntireCheckQueue().add(entireCheck);
            if (characteristic != null) {
                byte[] data = command.getBytes();
                writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                        .done(device -> {
                            Log.e("BleControlManager", "Command sent: " + command);
                        })
                        .fail((device, status) -> Log.e("BleControlManager", "Failed to send command: " + status))
                        .enqueue();
            } else {
                Log.e("BleControlManager", "Control Request characteristic is null");
            }
        } else {
            Log.e("BleControlManager", "Device is not connected");
        }
    }
    public void sendPinCommand(String pinCode, EntireCheck entireCheck) {
        if (isConnected() && controlRequest != null) {
            BluetoothGattCharacteristic characteristic = controlRequest;
            ScanViewModel.Companion.getEntireCheckQueue().add(entireCheck);
            if (characteristic != null) {
                // Добавляем префикс "pin." к пин-коду
                String formattedPinCode = "pin." + pinCode;
                byte[] data = formattedPinCode.getBytes();
                writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                        .done(device -> {
                           Log.e("BleControlManager", "PIN command sent");
                        })
                        .fail((device, status) -> {
                            Log.e("BleControlManager", "PIN command incorrect");
                        })
                        .enqueue();
            }
        }
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
            bleCallbackEvent.onHandleCheck();
        }

        @SuppressLint("MissingPermission")
        private final DataReceivedCallback notificationCallback = (device, data) -> {
            // Обработка данных, полученных от controlResponse
            // data - это массив байтов, которые представляют ответ от устройства
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                handleResponseData(data.getValue());
            }
        };

        @RequiresApi(api = Build.VERSION_CODES.O)
        private void handleResponseData(byte[] data) {
            EntireCheck entireCheck1 = ScanViewModel.Companion.getEntireCheckQueue().poll();
            if (entireCheck1 == null) {
                Log.d("BleControlManager", "Entire is null");
                return;
            }
            switch (entireCheck1){
                case HW_VER:
                    Log.d("BleControlManager","data " + Arrays.toString(data));
                    handleHwVer(data);
                    break;
                case default_command:
                    Log.d("BleControlManager","data " + Arrays.toString(data));
                    handleDefaultCommand(data);
                    break;
                case PIN_C0DE:
                    Log.d("BleControlManager","data " + Arrays.toString(data));
                    handlePinCodeResult(data);

            }
        }
        private void handleHwVer(byte[] data) {
            String hwVer = new String(Arrays.copyOfRange(data, 4, 20), StandardCharsets.US_ASCII).trim().replaceAll("[\\x00-\\x1F]", "");
            //DataItem dataItemHwVer = new DataItem(hwVer, bytesToHex(data), "HW VERSION", false,0x4C,0x10,DataType.CHAR_ARRAY);
            //listOfDataItem.add(dataItemHwVer);
            serialNumber = hwVer;
            bleCallbackEvent.onVersionCheck(serialNumber);
            Log.d("BleControlManager", "VERSION: " + hwVer);
        }

        @SuppressLint("WrongConstant")
        private void handleDefaultCommand(byte[] data) {
            String defaultResponse = new String(data, StandardCharsets.UTF_8);
            Log.d("BleControlManager", "updating hwVer " + defaultResponse);
            if(defaultResponse.contains("ble.ok")){
                Log.i("BleControlManager","DEVICES STARTING TO OFF");
            }

        }
        private void handlePinCodeResult(byte[] data){
            String pinResponse = new String(data, StandardCharsets.UTF_8);
            if(pinResponse.contains("pin.ok")){
                Log.d("BleControlManager", "Pin code is correct");
                sendCommand("serial", EntireCheck.HW_VER);
            }
        }
    }

}




