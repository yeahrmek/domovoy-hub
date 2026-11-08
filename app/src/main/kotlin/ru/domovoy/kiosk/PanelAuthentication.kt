package ru.domovoy.kiosk

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import kotlinx.coroutines.launch

/** Android verifies the screen-lock credential and limits failed attempts. */
internal class PanelAuthentication(private val activity: ComponentActivity) {
    private var cancellation: CancellationSignal? = null
    private var generation = 0
    private var credentialRequest: Int? = null
    private var completion: ((String?) -> Unit)? = null
    private val credentials = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val request = credentialRequest ?: return@registerForActivityResult
        credentialRequest = null
        finish(request, if (result.resultCode == Activity.RESULT_OK) null else "Проверка отменена")
    }

    @Suppress("DEPRECATION")
    fun request(title: String, result: (String?) -> Unit) {
        if (credentialRequest != null) {
            result("Завершите предыдущую проверку доступа")
            return
        }
        cancel()
        val request = generation
        completion = result
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            cancellation = signal
            BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setAllowedAuthenticators(DEVICE_CREDENTIAL)
                .build()
                .authenticate(
                    signal,
                    activity.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            finish(request, null)
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            finish(request, "Проверка не завершена: $errString")
                        }
                    },
                )
        } else {
            // Older Android keeps the normal panel usable with system PIN verification.
            val intent = activity.getSystemService(KeyguardManager::class.java)
                .createConfirmDeviceCredentialIntent(title, "Подтвердите доступ к приложениям")
            if (intent == null) {
                finish(request, "Сначала задайте PIN-код планшета")
            } else {
                credentialRequest = request
                credentials.launch(intent)
            }
        }
    }

    fun cancel() {
        generation++
        completion = null
        cancellation?.cancel()
        cancellation = null
    }

    private fun finish(request: Int, failure: String?) {
        if (generation != request) return
        val result = completion ?: return
        completion = null
        cancellation = null
        // Device-credential UI can finish authentication before our Activity has resumed.
        // Deliver only when the panel is visible again, and discard cancellation/recreation.
        activity.lifecycleScope.launch {
            activity.lifecycle.withResumed {
                if (generation == request) result(failure)
            }
        }
    }
}
