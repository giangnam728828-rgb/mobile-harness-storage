package com.jarves.mh

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.content.ComponentCallbacks2
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.LayoutDirection.Ltr
import androidx.compose.ui.unit.LayoutDirection.Rtl
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarves.mh.ui.AppStrings
import com.jarves.mh.ui.LocaleHelper
import com.jarves.mh.ui.MainViewModel
import com.jarves.mh.ui.PocketDevApp
import com.jarves.mh.ui.theme.PocketTheme

class MainActivity : ComponentActivity() {
    private var appliedLanguageCode: String? = null

    // Apply the in-app language to the Activity itself, not only to the Compose tree:
    // dialogs, dropdown menus and popups get their own window whose context is the Activity.
    override fun attachBaseContext(newBase: Context) {
        appliedLanguageCode = AppStrings.languageCode(newBase)
        super.attachBaseContext(AppStrings.context(newBase))
    }
    private val mainViewModel: MainViewModel by viewModels()

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm = mainViewModel
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.languageCode) {
                if (state.languageCode != appliedLanguageCode) recreate()
            }
            val context = LocalContext.current
            val localizedContext = remember(state.languageCode, context) {
                LocaleHelper.applyLanguage(context, state.languageCode)
            }
            val localizedConfiguration = localizedContext.resources.configuration
            val layoutDirection: LayoutDirection =
                if (localizedConfiguration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) Rtl else Ltr
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration,
                LocalLayoutDirection provides layoutDirection,
                LocalActivityResultRegistryOwner provides this,
            ) {
                PocketTheme(themeMode = state.themeMode) {
                    PocketDevApp(vm)
                }
            }
        }
        // Ask once the first frame is on screen, so the permission dialog never
        // competes with window setup for the main thread.
        window.decorView.post { ensureStorageAccess() }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            mainViewModel.releaseMemoryCaches()
        }
    }

    /**
     * The direct APK targets SDK 28, so the platform runs it in legacy storage mode:
     * broad /sdcard access comes from READ/WRITE_EXTERNAL_STORAGE, which is a normal
     * runtime permission. MANAGE_EXTERNAL_STORAGE is ignored below target SDK 30, so
     * the old "jump to the all-files-access settings page" flow never granted
     * anything — it only threw the user out of the app on every cold start.
     */
    private fun ensureStorageAccess() {
        val wanted = buildList {
            val read = Manifest.permission.READ_EXTERNAL_STORAGE
            val write = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (checkSelfPermission(read) != PackageManager.PERMISSION_GRANTED) add(read)
            if (checkSelfPermission(write) != PackageManager.PERMISSION_GRANTED) add(write)
        }
        if (wanted.isNotEmpty()) {
            storagePermissionLauncher.launch(wanted.toTypedArray())
        }
    }
}
