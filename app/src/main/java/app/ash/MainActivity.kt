package app.ash

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import app.ash.navigation.AshRoot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

/** Single activity. FragmentActivity is required by BiometricPrompt. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val openReminders = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { AshRoot(openReminders) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_REMINDERS, false) == true) openReminders.value = true
    }

    companion object {
        const val EXTRA_OPEN_REMINDERS = "open_reminders"
    }
}
