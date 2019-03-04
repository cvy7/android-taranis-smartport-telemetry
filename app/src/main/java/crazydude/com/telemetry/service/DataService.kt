package crazydude.com.telemetry.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import com.google.android.gms.maps.model.LatLng
import crazydude.com.telemetry.R
import crazydude.com.telemetry.protocol.DataPoller
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class DataService : Service(), DataPoller.Listener {

    private var dataPoller: DataPoller? = null
    private var dataListener: DataPoller.Listener? = null
    private val dataBinder = DataBinder()
    private var hasGPSFix = false
    private var satellites = 0
    val points: ArrayList<LatLng> = ArrayList()

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel =
                NotificationChannel("bt_channel", "Bluetooth", importance)
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, "bt_channel")
            .setContentText("Telemetry service is running")
            .setContentTitle("Telemetry service is running. To stop - disconnect and close the app")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        return Service.START_REDELIVER_INTENT
    }

    inner class DataBinder : Binder() {
        fun getService(): DataService = this@DataService
    }

    fun connect(device: BluetoothDevice) {
        var fileOutputStream : FileOutputStream? = null
        var csvFileOutputStream : FileOutputStream? = null
        if (checkCallingOrSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            val name = SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(Date())
            val dir = Environment.getExternalStoragePublicDirectory("TelemetryLogs")
            dir.mkdirs()
            val file = File(dir, "$name.log")
            val csvFile = File(dir, "$name.csv")
            fileOutputStream = FileOutputStream(file)
            csvFileOutputStream = FileOutputStream(csvFile)
        }
        val socket = device.createRfcommSocketToServiceRecord(device.uuids[0].uuid)
        dataPoller?.disconnect()
        dataPoller = DataPoller(socket, this, fileOutputStream, csvFileOutputStream)
    }

    fun setDataListener(dataListener: DataPoller.Listener?) {
        this.dataListener = dataListener
        if (dataListener != null) {
            dataListener.onGPSState(satellites, hasGPSFix)
        } else {
            if (!isConnected()) {
                stopSelf()
            }
        }
    }

    fun isConnected(): Boolean {
        return dataPoller != null
    }

    override fun onBind(intent: Intent): IBinder? {
        return dataBinder
    }

    override fun onDestroy() {
        super.onDestroy()
        dataPoller?.disconnect()
        dataPoller = null
    }

    override fun onConnectionFailed() {
        dataListener?.onConnectionFailed()
        dataPoller = null
    }

    override fun onFuelData(fuel: Int) {
        dataListener?.onFuelData(fuel)
    }

    override fun onConnected() {
        dataListener?.onConnected()
    }

    override fun onGPSData(latitude: Double, longitude: Double) {
        if (hasGPSFix) {
            points.add(LatLng(latitude, longitude))
        }
        dataListener?.onGPSData(latitude, longitude)
    }

    override fun onVBATData(voltage: Float) {
        dataListener?.onVBATData(voltage)
    }

    override fun onCellVoltageData(voltage: Float) {
        dataListener?.onCellVoltageData(voltage)
    }

    override fun onCurrentData(current: Float) {
        dataListener?.onCurrentData(current)
    }

    override fun onHeadingData(heading: Float) {
        dataListener?.onHeadingData(heading)
    }

    override fun onRSSIData(rssi: Int) {
        dataListener?.onRSSIData(rssi)
    }

    override fun onDisconnected() {
        points.clear()
        dataListener?.onDisconnected()
        dataPoller = null
        satellites = 0
        hasGPSFix = false
    }

    override fun onGPSState(satellites: Int, gpsFix: Boolean) {
        hasGPSFix = gpsFix
        dataListener?.onGPSState(satellites, gpsFix)
    }

    override fun onVSpeedData(vspeed: Float) {
        dataListener?.onVSpeedData(vspeed)
    }

    override fun onAltitudeData(altitude: Float) {
        dataListener?.onAltitudeData(altitude)
    }

    override fun onGPSAltitudeData(altitude: Float) {
        dataListener?.onGPSAltitudeData(altitude)
    }

    override fun onDistanceData(distance: Int) {
        dataListener?.onDistanceData(distance)
    }

    override fun onRollData(rollAngle: Float) {
        dataListener?.onRollData(rollAngle)
    }

    override fun onPitchData(pitchAngle: Float) {
        dataListener?.onPitchData(pitchAngle)
    }

    override fun onGSpeedData(speed: Float) {
        dataListener?.onGSpeedData(speed)
    }

    override fun onFlyModeData(
        armed: Boolean,
        heading: Boolean,
        firstFlightMode: DataPoller.Companion.FlyMode,
        secondFlightMode: DataPoller.Companion.FlyMode?
    ) {
        dataListener?.onFlyModeData(armed, heading, firstFlightMode, secondFlightMode)
    }

    fun disconnect() {
        points.clear()
        dataPoller?.disconnect()
        dataPoller = null
        satellites = 0
        hasGPSFix = false
    }
}