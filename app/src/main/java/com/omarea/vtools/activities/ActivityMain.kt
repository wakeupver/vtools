package com.omarea.vtools.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.omarea.Scene
import com.omarea.common.shared.MagiskExtend
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.shell.KernelProrp
import com.omarea.common.shell.RootFile
import com.omarea.common.ui.DialogHelper
import com.omarea.permissions.CheckRootStatus
import com.omarea.store.SpfConfig
import com.omarea.utils.ElectricityUnit
import com.omarea.utils.Update
import com.omarea.vtools.R
import com.omarea.vtools.databinding.ActivityMainBinding
import com.omarea.vtools.dialogs.DialogMonitor
import com.omarea.vtools.dialogs.DialogPower
import com.omarea.vtools.fragments.FragmentCpuModes
import com.omarea.vtools.fragments.FragmentHome
import com.omarea.vtools.fragments.FragmentNav
import com.omarea.vtools.fragments.FragmentNotRoot
import java.util.ArrayDeque

class ActivityMain : ActivityBase() {
    companion object {
        const val EXTRA_SELECT_TAB = "select_tab"
        const val TAB_HOME = 0
        const val TAB_NAV  = 1
        const val TAB_TUNER = 2
        var lastSelectedTab = TAB_HOME
    }

    private lateinit var globalSPF: SharedPreferences
    private lateinit var binding: ActivityMainBinding
    private val tabHistory = ArrayDeque<Int>()
    private var suppressTabHistory = false

    // Tab id → pager index
    private val navIdToIndex = mapOf(
        R.id.tab_home  to TAB_HOME,
        R.id.tab_nav   to TAB_NAV,
        R.id.tab_tuner to TAB_TUNER
    )
    private val indexToNavId = mapOf(
        TAB_HOME  to R.id.tab_home,
        TAB_NAV   to R.id.tab_nav,
        TAB_TUNER to R.id.tab_tuner
    )

    private class ThermalCheckThread(private var context: Activity) : Thread() {
        private fun deleteThermalCopyWarn(onYes: Runnable) {
            Scene.post {
                if (!context.isFinishing) {
                    val view = LayoutInflater.from(context).inflate(R.layout.dialog_delete_thermal, null)
                    val dialog = DialogHelper.customDialog(context, view)
                    view.findViewById<View>(R.id.btn_no).setOnClickListener { dialog.dismiss() }
                    view.findViewById<View>(R.id.btn_yes).setOnClickListener {
                        dialog.dismiss()
                        onYes.run()
                    }
                    dialog.setCancelable(false)
                }
            }
        }

        override fun run() {
            sleep(500)
            if (MagiskExtend.magiskSupported() &&
                KernelProrp.getProp("${MagiskExtend.MAGISK_PATH}system/vendor/etc/thermal.current.ini") != "") {
                when {
                    RootFile.list("/data/thermal/config").size > 0 -> {
                        deleteThermalCopyWarn {
                            KeepShellPublic.doCmdSync(
                                "chattr -R -i /data/thermal 2> /dev/null\n" +
                                "rm -rf /data/thermal 2> /dev/null\n" +
                                "sync;svc power reboot || reboot;")
                        }
                    }
                    RootFile.list("/data/vendor/thermal/config").size > 0 -> {
                        if (RootFile.fileEquals(
                                "/data/vendor/thermal/config/thermal-normal.conf",
                                MagiskExtend.getMagiskReplaceFilePath("/system/vendor/etc/thermal-normal.conf"))) {
                            return
                        } else {
                            deleteThermalCopyWarn {
                                KeepShellPublic.doCmdSync(
                                    "chattr -R -i /data/vendor/thermal 2> /dev/null\n" +
                                    "rm -rf /data/vendor/thermal 2> /dev/null\n" +
                                    "sync;svc power reboot || reboot;")
                            }
                        }
                    }
                    else -> return
                }
            }
        }
    }

    @SuppressLint("ResourceAsColor")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ActivityStartSplash.finished) {
            val intent = Intent(this.applicationContext, ActivityStartSplash::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            startActivity(intent)
            finish()
            return
        }

        globalSPF = getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)
        if (!globalSPF.contains(SpfConfig.GLOBAL_SPF_CURRENT_NOW_UNIT)) {
            globalSPF.edit()
                .putInt(SpfConfig.GLOBAL_SPF_CURRENT_NOW_UNIT, ElectricityUnit().getDefaultElectricityUnit(this))
                .apply()
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        // Toolbar action menu clicks
        binding.toolbar.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.action_graph    -> { actionGraph(); true }
                R.id.action_power    -> { DialogPower(this).showPowerMenu(); true }
                R.id.action_settings -> {
                    startActivity(Intent(applicationContext, ActivityOtherSettings::class.java))
                    true
                }
                else -> false
            }
        }

        // Build page fragments (order: Home=0, Nav=1, Tuner=2)
        val fragments: List<Fragment> = listOf(
            if (CheckRootStatus.lastCheckResult) FragmentHome() else FragmentNotRoot(),
            FragmentNav.createPage(themeMode),
            FragmentCpuModes()
        )

        binding.tabContent.adapter = object : FragmentStateAdapter(this as FragmentActivity) {
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int) = fragments[position]
        }
        binding.tabContent.isUserInputEnabled = false  // disable swipe (tabs handle navigation)
        binding.tabContent.offscreenPageLimit = 2      // keep all fragments alive

        // Bottom nav → ViewPager2
        binding.bottomNav.setOnItemSelectedListener { item ->
            val index = navIdToIndex[item.itemId] ?: return@setOnItemSelectedListener false
            if (!suppressTabHistory && tabHistory.peekLast() != index) {
                tabHistory.addLast(index)
            }
            binding.tabContent.setCurrentItem(index, false)
            lastSelectedTab = index
            true
        }

        // ViewPager2 → bottom nav sync (in case of programmatic navigation)
        binding.tabContent.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val menuId = indexToNavId[position] ?: return
                suppressTabHistory = true
                binding.bottomNav.selectedItemId = menuId
                suppressTabHistory = false
            }
        })

        setInitialTab(intent.getIntExtra(EXTRA_SELECT_TAB, TAB_HOME))

        if (CheckRootStatus.lastCheckResult) {
            try {
                if (MagiskExtend.magiskSupported() &&
                    !(MagiskExtend.moduleInstalled() || globalSPF.getBoolean("magisk_dot_show", false))) {
                    DialogHelper.confirm(this,
                        getString(R.string.magisk_install_title),
                        getString(R.string.magisk_install_desc),
                        { MagiskExtend.magiskModuleInstall(this) })
                }
            } catch (ex: Exception) {
                DialogHelper.alert(this, getString(R.string.sorry),
                    "Failed to start app\n" + ex.message) { recreate() }
            }
            ThermalCheckThread(this).start()
        }
    }

    private fun actionGraph() {
        if (!CheckRootStatus.lastCheckResult) {
            Toast.makeText(this, getString(R.string.not_root_disabled), Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 23) {
            if (Settings.canDrawOverlays(this)) {
                DialogMonitor(this).show()
            } else {
                val intent = Intent()
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                intent.data = Uri.fromParts("package", this.packageName, null)
                Toast.makeText(applicationContext, getString(R.string.permission_float), Toast.LENGTH_LONG).show()
            }
        } else {
            DialogMonitor(this).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (globalSPF.getLong(SpfConfig.GLOBAL_SPF_LAST_UPDATE, 0) + (3600 * 24 * 1000) < System.currentTimeMillis()) {
            Update().checkUpdate(this)
            globalSPF.edit().putLong(SpfConfig.GLOBAL_SPF_LAST_UPDATE, System.currentTimeMillis()).apply()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setInitialTab(intent.getIntExtra(EXTRA_SELECT_TAB, TAB_HOME))
    }

    private fun setInitialTab(index: Int) {
        if (!::binding.isInitialized) return
        val safeIndex = index.coerceIn(0, 2)
        val menuId = indexToNavId[safeIndex] ?: R.id.tab_home
        suppressTabHistory = true
        binding.bottomNav.selectedItemId = menuId
        suppressTabHistory = false
        binding.tabContent.setCurrentItem(safeIndex, false)
        tabHistory.clear()
        tabHistory.addLast(safeIndex)
        lastSelectedTab = safeIndex
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {}

    override fun onBackPressed() {
        try {
            when {
                supportFragmentManager.backStackEntryCount > 0 -> supportFragmentManager.popBackStack()
                tabHistory.size > 1 -> {
                    tabHistory.removeLast()
                    val previous = tabHistory.peekLast()
                    if (previous != null) {
                        suppressTabHistory = true
                        val menuId = indexToNavId[previous] ?: R.id.tab_home
                        binding.bottomNav.selectedItemId = menuId
                        binding.tabContent.setCurrentItem(previous, false)
                        suppressTabHistory = false
                        return
                    }
                    excludeFromRecent()
                    super.onBackPressed()
                }
                else -> {
                    excludeFromRecent()
                    super.onBackPressed()
                }
            }
        } catch (ex: Exception) {
            ex.stackTrace
        }
    }

    public override fun onPause() {
        super.onPause()
        if (!CheckRootStatus.lastCheckResult) finish()
    }

    override fun onDestroy() {
        supportFragmentManager.fragments.clear()
        super.onDestroy()
    }
}
