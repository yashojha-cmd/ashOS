package app.ash.security

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Security tests (§39) against the merged manifest the app actually ships with. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ManifestSecurityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun requested(): List<String> =
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions?.toList().orEmpty()

    @Test fun noNetworkPermissions() {
        assertThat(requested()).containsNoneOf(Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE)
    }

    @Test fun noSmsOrContactsOrLocation() {
        assertThat(requested()).containsNoneOf(
            Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_EXTERNAL_STORAGE,
        )
    }

    @Test fun cloudBackupDisabled() {
        assertThat(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP).isEqualTo(0)
    }

    @Test fun onlyExpectedComponentsAreExported() {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS,
        )
        val exported = buildList {
            info.activities?.filter { it.exported }?.forEach { add(it.name) }
            info.services?.filter { it.exported }?.forEach { add(it.name) }
            info.receivers?.filter { it.exported && it.name.startsWith("app.ash") }?.forEach { add(it.name) }
            info.providers?.filter { it.exported }?.forEach { add(it.name) }
        }
        assertThat(exported.filter { it.startsWith("app.ash") }).containsExactly(
            "app.ash.MainActivity",
            "app.ash.notification.AshNotificationListener",
            "app.ash.reminder.BootReceiver",
        )
        val listener = info.services!!.first { it.name.endsWith("AshNotificationListener") }
        assertThat(listener.permission).isEqualTo("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE")
    }
}
