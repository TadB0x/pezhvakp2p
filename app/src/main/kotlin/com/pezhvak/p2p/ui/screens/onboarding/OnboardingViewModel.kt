package com.pezhvak.p2p.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import com.pezhvak.p2p.core.identity.KeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val keyManager: KeyManager,
) : ViewModel() {
    fun createIdentity(displayName: String) {
        keyManager.getOrCreateIdentity(displayName)
    }
}
