package com.tvremote.free

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tvremote.free.adb.AdbConnection
import com.tvremote.free.adb.AdbEvent

/** Standard Android key event codes (android.view.KeyEvent), sent via `input keyevent`. */
private object Keys {
    const val HOME = 3
    const val BACK = 4
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val POWER = 26
    const val MEDIA_PLAY_PAUSE = 85
    const val MUTE = 164
}

class MainActivity : AppCompatActivity() {

    private var connection: AdbConnection? = null
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        val ipField = findViewById<EditText>(R.id.ipAddressField)
        val connectButton = findViewById<Button>(R.id.connectButton)

        connectButton.setOnClickListener {
            val ip = ipField.text.toString().trim()
            if (ip.isEmpty()) {
                Toast.makeText(this, "TV-র IP address লিখো (Settings > Network এ পাবে)", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            connect(ip)
        }

        mapOf(
            R.id.btnHome to Keys.HOME,
            R.id.btnBack to Keys.BACK,
            R.id.btnUp to Keys.DPAD_UP,
            R.id.btnDown to Keys.DPAD_DOWN,
            R.id.btnLeft to Keys.DPAD_LEFT,
            R.id.btnRight to Keys.DPAD_RIGHT,
            R.id.btnSelect to Keys.DPAD_CENTER,
            R.id.btnVolUp to Keys.VOLUME_UP,
            R.id.btnVolDown to Keys.VOLUME_DOWN,
            R.id.btnMute to Keys.MUTE,
            R.id.btnPower to Keys.POWER,
            R.id.btnPlayPause to Keys.MEDIA_PLAY_PAUSE
        ).forEach { (viewId, keyCode) ->
            findViewById<Button>(viewId).setOnClickListener { sendKeyEvent(keyCode) }
        }
    }

    private fun connect(ip: String) {
        statusText.text = "সংযোগ করা হচ্ছে..."
        connection = AdbConnection(ip, 5555, applicationContext) { event ->
            runOnUiThread {
                when (event) {
                    is AdbEvent.Connected -> statusText.text = "সংযুক্ত ✓ ($ip)"
                    is AdbEvent.AuthRequired -> statusText.text = "TV-র স্ক্রিনে অনুমতি দাও, তারপর আবার চেষ্টা করো"
                    is AdbEvent.Error -> statusText.text = "সমস্যা: ${event.message}"
                    is AdbEvent.Disconnected -> statusText.text = "সংযোগ বিচ্ছিন্ন"
                }
            }
        }
        connection?.connect()
    }

    private fun sendKeyEvent(code: Int) {
        connection?.runShellCommand("input keyevent $code")
            ?: Toast.makeText(this, "প্রথমে TV-র সাথে সংযুক্ত হও", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        connection?.disconnect()
    }
}
