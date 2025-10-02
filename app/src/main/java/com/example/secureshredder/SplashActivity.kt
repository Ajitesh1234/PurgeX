// app/src/main/java/com/example/secureshredder/SplashActivity.kt
package com.example.secureshredder

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.secureshredder.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val SPLASH_DELAY: Long = 2500 // 2.5 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Optional: If you want to use "Purgex" from strings.xml for the app name TextView
        // binding.tvAppName.text = getString(R.string.app_display_name) 
        // Ensure R.string.app_display_name is defined as "Purgex" in strings.xml if you use this.
        // Otherwise, the text "Purgex" is already hardcoded in activity_splash.xml.

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out) // Added this line
            finish() // Finish SplashActivity so the user can't navigate back to it
        }, SPLASH_DELAY)
    }
}
