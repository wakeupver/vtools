package com.omarea.vtools.fragments

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.omarea.common.ui.ThemeMode
import com.omarea.kr.KrScriptConfig
import com.omarea.permissions.CheckRootStatus
import com.omarea.shell_utils.BackupRestoreUtils
import com.omarea.vtools.R
import com.omarea.vtools.activities.*
import com.projectkr.shell.OpenPageHelper
import com.omarea.vtools.databinding.FragmentNavBinding
import com.omarea.vtools.ui.overview.OverviewMenu

class FragmentNav : Fragment() {
    private lateinit var themeMode: ThemeMode
    private var _binding: FragmentNavBinding? = null
    private val binding get() = _binding!!
    private val rootRequiredIds = setOf(
        R.id.nav_core_control,
        R.id.nav_swap,
        R.id.nav_processes,
        R.id.nav_fps_chart,
        R.id.nav_applictions,
        R.id.nav_img,
        R.id.nav_additional,
        R.id.nav_additional_all,
        R.id.nav_app_magisk,
        R.id.nav_modules
    )

    companion object {
        fun createPage(themeMode: ThemeMode): Fragment {
            val fragment = FragmentNav()
            fragment.themeMode = themeMode
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentNavBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!::themeMode.isInitialized) {
            themeMode = (activity as? ActivityBase)?.themeMode ?: ThemeMode()
        }
        val isDark = themeMode.isDarkMode
        binding.composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.composeView.setContent {
            val ctx = LocalContext.current
            val colorScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDark -> dynamicDarkColorScheme(ctx)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S           -> dynamicLightColorScheme(ctx)
                isDark                                                     -> darkColorScheme()
                else                                                       -> lightColorScheme()
            }
            MaterialTheme(colorScheme = colorScheme) {
                OverviewMenu(
                    isRootAvailable = CheckRootStatus.lastCheckResult,
                    onItemClick = { handleNavClick(it) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isDetached) return
        activity?.title = getString(R.string.app_name)
    }

    private fun handleNavClick(id: Int) {
        if (!CheckRootStatus.lastCheckResult && rootRequiredIds.contains(id)) {
            Toast.makeText(context, "Root permission not granted; this feature is unavailable.", Toast.LENGTH_SHORT).show()
            return
        }
        when (id) {
            R.id.nav_applictions   -> startActivity(Intent(context, ActivityApplistions::class.java))
            R.id.nav_swap          -> startActivity(Intent(context, ActivitySwap::class.java))
            R.id.nav_charge        -> startActivity(Intent(context, ActivityCharge::class.java))
            R.id.nav_power_utilization -> startActivity(Intent(context, ActivityPowerUtilization::class.java))
            R.id.nav_img           -> {
                if (BackupRestoreUtils.isSupport()) startActivity(Intent(context, ActivityImg::class.java))
                else Toast.makeText(context, "Not supported on this device.", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_battery_stats -> startActivity(Intent(context, ActivityPowerUtilization::class.java))
            R.id.nav_core_control  -> startActivity(Intent(context, ActivityCpuControl::class.java))
            R.id.nav_miui_thermal  -> startActivity(Intent(context, ActivityMiuiThermal::class.java))
            R.id.nav_app_scene     -> startActivity(Intent(context, ActivityAppConfig2::class.java))
            R.id.nav_app_magisk    -> startActivity(Intent(context, ActivityMagisk::class.java))
            R.id.nav_modules       -> startActivity(Intent(context, ActivityModules::class.java))
            R.id.nav_processes     -> {
                startActivity(Intent(context, ActivityProcess::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            R.id.nav_fps_chart     -> startActivity(Intent(context, ActivityFpsChart::class.java))
            R.id.nav_additional    -> startActivity(Intent(context, ActivityAddin::class.java))
            R.id.nav_additional_all -> {
                val krScriptConfig = KrScriptConfig().init(context!!)
                val activity = activity!!
                krScriptConfig.pageListConfig?.run {
                    OpenPageHelper(activity).openPage(this.apply { title = getString(R.string.menu_additional) })
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
