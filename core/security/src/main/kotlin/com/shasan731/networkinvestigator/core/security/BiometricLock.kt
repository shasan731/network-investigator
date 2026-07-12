package com.shasan731.networkinvestigator.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricLock(private val activity: FragmentActivity) {
    fun availability(): Int = runCatching { BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) }.getOrDefault(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)
    fun authenticate(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (availability() != BiometricManager.BIOMETRIC_SUCCESS) { onError("Biometric or device credential authentication is not available."); return }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errString.toString())
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Network Investigator").setSubtitle("Protect locally saved evidence").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build())
    }
}
