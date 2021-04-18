package com.baolar.autopie
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


private const val REQUEST_CODE_PERMISSIONS = 10
private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
)
private val tag = MainActivity::class.java.simpleName

@SuppressLint("RestrictedApi, ClickableViewAccessibility")
class MainActivity : AppCompatActivity(), LifecycleOwner {

    private lateinit var viewFinder: TextureView
    private lateinit var captureButton: ImageButton
    private lateinit var videoCapture: VideoCapture
    private var isRecording = false

    private var sensorManager: SensorManager? = null
    private var locationManager: LocationManager? = null
    private var timeStampText: TextView? = null
    private var accxText: TextView? = null
    private var accyText: TextView? = null
    private var acczText: TextView? = null
    private var gyroxText: TextView? = null
    private var gyroyText: TextView? = null
    private var gyrozText: TextView? = null
    private var magxText: TextView? = null
    private var magyText: TextView? = null
    private var magzText: TextView? = null
    private var workDir: String? = null
    private var accFile: File? = null
    private var gyroFile: File? = null
    private var magFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.view_finder)
        captureButton = findViewById(R.id.capture_button)

        // Request camera permissions
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )

            startCamera()
        }
        viewFinder.post { startCamera() }


        initCamera()
        initSensor()
        initAppDir()
    }

    private fun initAppDir() {
        val appPath = "" + Environment.getExternalStorageDirectory() + "/AutoPie"
        File(appPath).mkdirs()
    }

    private fun initCamera() {
        captureButton.setOnClickListener {
            if (!isRecording) {
                isRecording = true
                Toast.makeText(this, "开始录制", Toast.LENGTH_SHORT).show()
                captureButton.setBackgroundColor(Color.GREEN)

                var now = fileFormatNowData(System.currentTimeMillis())
//                workDir = "" + externalMediaDirs.first() + "/" + now    // 创建当前工作文件夹
                workDir = "" + Environment.getExternalStorageDirectory() + "/AutoPie/" + now    // 创建当前工作文件夹
                File(workDir).mkdirs()

                videoCapture.startRecording(File("$workDir/$now.mp4"), object : VideoCapture.OnVideoSavedListener {
                    override fun onVideoSaved(file: File?) {
                    }

                    override fun onError(
                            useCaseError: VideoCapture.UseCaseError?,
                            message: String?,
                            cause: Throwable?
                    ) {
                        Log.i(tag, "Video Error: $message")
                    }
                })
                // 优先调用摄像头后，开始创建传感器记录文件
                accFile = File("$workDir/acc.csv")
                accFile!!.writeText("time,x,y,z\n")
                gyroFile = File("$workDir/gyro.csv")
                gyroFile!!.writeText("time,x,y,z\n")
                magFile = File("$workDir/mag.csv")
                magFile!!.writeText("time,x,y,z\n")
            }
            else {
                isRecording = false
                Toast.makeText(this, "录制完成", Toast.LENGTH_SHORT).show()
                captureButton.setBackgroundColor(Color.RED)
                videoCapture.stopRecording()
            }
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String?>,
            grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                viewFinder.post { startCamera() } // Permission Granted
            } else if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()// Permission Denied
            } else {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
    }

    private fun allPermissionsGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                            this, permission
                    ) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun startCamera() {
        // Create configuration object for the viewfinder use case
        val previewConfig = PreviewConfig.Builder()
                .setTargetResolution(Size(viewFinder.width, viewFinder.height))
                .build()
        // Build the viewfinder use case
        val preview = Preview(previewConfig)

        // Create a configuration object for the video use case
        val videoCaptureConfig = VideoCaptureConfig.Builder().apply {
            setTargetRotation(viewFinder.display.rotation)
        }.build()
        videoCapture = VideoCapture(videoCaptureConfig)

        preview.setOnPreviewOutputUpdateListener {
            // 获取viewFinder父容器
            val parent = viewFinder.parent as ViewGroup
            parent.removeView(viewFinder)//将当前用来预览的TextureView从父容器移除
            parent.addView(viewFinder, 0)//并从新添加用来预览的TextureView

            viewFinder.surfaceTexture = it.surfaceTexture//将预览画面设置为我们的TextureView
            //  updateTransform()
        }

        // Bind use cases to lifecycle
        CameraX.bindToLifecycle(this, preview, videoCapture)
    }

    private fun initSensor() {
        timeStampText = findViewById(R.id.timeStamp)
        accxText = findViewById<View>(R.id.accx) as TextView
        accyText = findViewById<View>(R.id.accy) as TextView
        acczText = findViewById<View>(R.id.accz) as TextView
        gyroxText = findViewById<View>(R.id.gyrox) as TextView
        gyroyText = findViewById<View>(R.id.gyroy) as TextView
        gyrozText = findViewById<View>(R.id.gyroz) as TextView
        magxText = findViewById<View>(R.id.magx) as TextView
        magyText = findViewById<View>(R.id.magy) as TextView
        magzText = findViewById<View>(R.id.magz) as TextView

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val sensora = sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager!!.registerListener(listenera, sensora, SensorManager.SENSOR_DELAY_GAME)
        val sensorg = sensorManager!!.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        sensorManager!!.registerListener(listenerg, sensorg, SensorManager.SENSOR_DELAY_GAME)
        val sensorm = sensorManager!!.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        sensorManager!!.registerListener(listenerm, sensorm, SensorManager.SENSOR_DELAY_GAME)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val ok = locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (ok) {
            if (Build.VERSION.SDK_INT >= 23) { //判断是否为android6.0系统版本，如果是，需要动态添加权限
                if (ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        )
                        != PermissionChecker.PERMISSION_GRANTED) { // 没有权限，申请权限。
                    ActivityCompat.requestPermissions(
                            this, LOCATIONGPS,
                            100
                    )
                }
            }
        }
    }

    private val listenera: SensorEventListener = object : SensorEventListener {
        @SuppressLint("SetTextI18n")
        override fun onSensorChanged(event: SensorEvent) {
            val accx = event.values[0]
            val accy = event.values[1]
            val accz = event.values[2]
            accxText!!.text = "accx:" + accx
            accyText!!.text = "accy:" + accy
            acczText!!.text = "accz:" + accz
            val now = System.currentTimeMillis()
            timeStampText!!.text = "" + refFormatNowDate(now)
            if (isRecording) {
                accFile!!.appendText("" + fileFormatNowData(now) + "," + accx + "," + accy + "," + accz + "\n")
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    private val listenerg: SensorEventListener = object : SensorEventListener {
        @SuppressLint("SetTextI18n")
        override fun onSensorChanged(event: SensorEvent) {
            val gyrox = event.values[0]
            val gyroy = event.values[1]
            val gyroz = event.values[2]
            gyroxText!!.text = "gyrox:" + gyrox
            gyroyText!!.text = "gyroy:" + gyroy
            gyrozText!!.text = "gyroz:" + gyroz
            val now = System.currentTimeMillis()
            timeStampText!!.text = "" + refFormatNowDate(now)
            if (isRecording) {
                gyroFile!!.appendText("" + fileFormatNowData(now) + "," + gyrox + "," + gyroy + "," + gyroz + "\n")
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    private val listenerm: SensorEventListener = object : SensorEventListener {
        @SuppressLint("SetTextI18n")
        override fun onSensorChanged(event: SensorEvent) {
            val magx =event.values[0]
            val magy = event.values[1]
            val magz = event.values[2]
//            magxText!!.text = "magx:$magx"
//            magyText!!.text = "magy:$magy"
//            magzText!!.text = "magz:$magz"
            magxText!!.text = "magx:" + magx  // 用$连接可能出现科学计数法
            magyText!!.text = "magy:" + magy
            magzText!!.text = "magz:" + magz
            val now = System.currentTimeMillis()
            timeStampText!!.text = "" + refFormatNowDate(now)
            if (isRecording) {
                magFile!!.appendText("" + fileFormatNowData(now) + "," + magx + "," + magy + "," + magz + "\n")
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    @SuppressLint("SimpleDateFormat")
    private fun refFormatNowDate(currentTime: Long): String? { // 格式化时间 显示用
        val nowTime = Date(currentTime)
        val sdFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS")
        return sdFormatter.format(nowTime)
    }

    @SuppressLint("SimpleDateFormat")
    private fun fileFormatNowData(currentTime: Long): String? { // 格式化时间 文件用
        val nowTime = Date(currentTime)
        val sdFormatter = SimpleDateFormat("yyyyMMddHHmmssSSS")
        return sdFormatter.format(nowTime)
    }

    companion object {
        val LOCATIONGPS = arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
}