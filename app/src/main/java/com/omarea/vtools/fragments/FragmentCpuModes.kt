package com.omarea.vtools.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.omarea.Scene
import com.omarea.common.shared.FilePathResolver
import com.omarea.common.shared.FileWrite
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.ui.DialogHelper
import com.omarea.common.ui.ThemeMode
import com.omarea.data.EventBus
import com.omarea.data.EventType
import com.omarea.krscript.model.PageNode
import com.omarea.library.shell.ThermalDisguise
import com.omarea.permissions.CheckRootStatus
import com.omarea.scene_mode.CpuConfigInstaller
import com.omarea.scene_mode.ModeSwitcher
import com.omarea.store.SpfConfig
import com.omarea.utils.AccessibleServiceHelper
import com.omarea.vtools.R
import com.omarea.vtools.activities.*
import com.projectkr.shell.OpenPageHelper
import com.omarea.vtools.databinding.FragmentCpuModesBinding
import com.omarea.vtools.databinding.FragmentCpuModesContentBinding
import java.io.File
import java.nio.charset.Charset
import java.util.*

class FragmentCpuModes : Fragment() {
    private var _binding: FragmentCpuModesBinding? = null
    private val binding get() = _binding!!
    private var contentBinding: FragmentCpuModesContentBinding? = null

    private var author: String = ""
    private var configFileInstalled: Boolean = false
    private lateinit var modeSwitcher: ModeSwitcher
    private lateinit var globalSPF: SharedPreferences
    private lateinit var themeMode: ThemeMode
    private val showServiceNotice = mutableStateOf(false)
    private var cardModesView: View? = null
    private var cardServiceNoticeView: View? = null
    private var cardDynamicView: View? = null
    private var cardShortcutsView: View? = null
    private var cardMoreView: View? = null

    companion object {
        fun createPage(themeMode: ThemeMode): Fragment {
            val fragment = FragmentCpuModes()
            fragment.themeMode = themeMode
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentCpuModesBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun startService() {
        AccessibleServiceHelper().stopSceneModeService(activity!!.applicationContext)
        Scene.toast(getString(R.string.accessibility_please_activate), Toast.LENGTH_SHORT)
        try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (e: Exception) {}
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!::themeMode.isInitialized) {
            themeMode = (activity as? ActivityBase)?.themeMode ?: ThemeMode()
        }
        globalSPF = context!!.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)
        modeSwitcher = ModeSwitcher()
        contentBinding = FragmentCpuModesContentBinding.inflate(layoutInflater)
        val content = contentBinding!!
        cardModesView      = detachFromParent(content.cpuModesCardModes)
        cardServiceNoticeView = detachFromParent(content.cpuModesCardServiceNotice)
        cardDynamicView    = detachFromParent(content.cpuModesCardDynamic)
        cardShortcutsView  = detachFromParent(content.cpuModesCardShortcuts)
        cardMoreView       = detachFromParent(content.navMore)

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
                TunerScreen(
                    cardModes         = cardModesView,
                    cardServiceNotice = cardServiceNoticeView,
                    showServiceNotice = showServiceNotice.value,
                    cardDynamic       = cardDynamicView,
                    cardShortcuts     = cardShortcutsView,
                    cardMore          = cardMoreView
                )
            }
        }

        bindMode(content.cpuConfigP0, ModeSwitcher.POWERSAVE)
        bindMode(content.cpuConfigP1, ModeSwitcher.BALANCE)
        bindMode(content.cpuConfigP2, ModeSwitcher.PERFORMANCE)
        bindMode(content.cpuConfigP3, ModeSwitcher.FAST)

        content.dynamicControl.setOnClickListener {
            val value = (it as Switch).isChecked
            if (value && !modeSwitcher.modeConfigCompleted()) {
                it.isChecked = false
                DialogHelper.alert(context!!, getString(R.string.sorry), getString(R.string.schedule_unfinished))
            } else if (value && !AccessibleServiceHelper().serviceRunning(context!!)) {
                it.isChecked = false
                startService()
            } else {
                globalSPF.edit().putBoolean(SpfConfig.GLOBAL_SPF_DYNAMIC_CONTROL, value).apply()
                reStartService()
            }
        }
        content.dynamicControlOpts2.initExpand(false)
        content.dynamicControl.setOnCheckedChangeListener { _, isChecked ->
            content.dynamicControlOpts.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        content.dynamicControlToggle.setOnClickListener {
            content.dynamicControlOpts2.toggleExpand()
            (it as ImageView).setImageDrawable(
                ContextCompat.getDrawable(context!!,
                    if (content.dynamicControlOpts2.isExpand) R.drawable.arrow_up else R.drawable.arrow_down))
        }

        content.strictMode.isChecked = globalSPF.getBoolean(SpfConfig.GLOBAL_SPF_DYNAMIC_CONTROL_STRICT, false)
        content.strictMode.setOnClickListener {
            globalSPF.edit().putBoolean(SpfConfig.GLOBAL_SPF_DYNAMIC_CONTROL_STRICT, (it as CompoundButton).isChecked).apply()
        }
        content.delaySwitch.isChecked = globalSPF.getBoolean(SpfConfig.GLOBAL_SPF_DYNAMIC_CONTROL_DELAY, false)
        content.delaySwitch.setOnClickListener {
            globalSPF.edit().putBoolean(SpfConfig.GLOBAL_SPF_DYNAMIC_CONTROL_DELAY, (it as CompoundButton).isChecked).apply()
        }

        content.firstMode.run {
            when (globalSPF.getString(SpfConfig.GLOBAL_SPF_POWERCFG_FIRST_MODE, ModeSwitcher.BALANCE)) {
                ModeSwitcher.POWERSAVE   -> setSelection(0)
                ModeSwitcher.BALANCE     -> setSelection(1)
                ModeSwitcher.PERFORMANCE -> setSelection(2)
                ModeSwitcher.FAST        -> setSelection(3)
                ModeSwitcher.IGONED      -> setSelection(4)
            }
            onItemSelectedListener = ModeOnItemSelectedListener(globalSPF) { reStartService() }
        }

        content.sleepMode.run {
            when (globalSPF.getString(SpfConfig.GLOBAL_SPF_POWERCFG_SLEEP_MODE, ModeSwitcher.POWERSAVE)) {
                ModeSwitcher.POWERSAVE   -> setSelection(0)
                ModeSwitcher.BALANCE     -> setSelection(1)
                ModeSwitcher.PERFORMANCE -> setSelection(2)
                ModeSwitcher.IGONED      -> setSelection(3)
            }
            onItemSelectedListener = ModeOnItemSelectedListener2(globalSPF) {}
        }

        val sourceClick = View.OnClickListener {
            if (configInstaller.outsideConfigInstalled()) {
                if (configInstaller.dynamicSupport(context!!)) {
                    DialogHelper.warning(activity!!, getString(R.string.make_choice), getString(R.string.schedule_remove_outside), {
                        configInstaller.removeOutsideConfig(); reStartService(); updateState(); chooseConfigSource()
                    })
                } else {
                    Scene.toast(getString(R.string.schedule_unofficial), Toast.LENGTH_LONG)
                }
            } else if (configInstaller.dynamicSupport(context!!)) {
                chooseConfigSource()
            } else {
                Scene.toast(getString(R.string.schedule_unsupported), Toast.LENGTH_LONG)
            }
        }
        content.configAuthorIcon.setOnClickListener(sourceClick)
        content.configAuthor.setOnClickListener(sourceClick)

        content.navBatteryStats.setOnClickListener { startActivity(Intent(context, ActivityPowerUtilization::class.java)) }
        content.navAppScene.setOnClickListener {
            if (!AccessibleServiceHelper().serviceRunning(context!!)) {
                startService()
            } else if (content.dynamicControl.isChecked) {
                startActivity(Intent(context, ActivityAppConfig2::class.java))
            } else {
                DialogHelper.warning(activity!!, getString(R.string.please_notice), getString(R.string.schedule_dynamic_off), {
                    startActivity(Intent(context, ActivityAppConfig2::class.java))
                })
            }
        }
        content.navSceneServiceNotActive.setOnClickListener { startService() }
        content.navSkipAd.setOnClickListener {
            if (AccessibleServiceHelper().serviceRunning(context!!))
                startActivity(Intent(context, ActivityAutoClick::class.java))
            else startService()
        }

        if (CheckRootStatus.lastCheckResult) {
            content.navMore.visibility = View.VISIBLE
            if (Build.MANUFACTURER.lowercase(Locale.getDefault()) == "xiaomi") {
                content.navThermal.setOnClickListener {
                    OpenPageHelper(activity!!).openPage(PageNode("").apply {
                        title = "MIUI only"
                        pageConfigPath = "file:///android_asset/kr-script/miui/miui.xml"
                    })
                }
            } else {
                content.navThermal.visibility = View.GONE
            }
            content.navProcesses.setOnClickListener {
                startActivity(Intent(context, ActivityProcess::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }
            content.navFreeze.setOnClickListener {
                if (AccessibleServiceHelper().serviceRunning(context!!))
                    startActivity(Intent(Intent.ACTION_VIEW).setClassName("com.omarea.vtools", "com.omarea.vtools.activities.ActivityFreezeApps2"))
                else startService()
            }
        }

        if (!modeSwitcher.modeConfigCompleted() && configInstaller.dynamicSupport(context!!)) {
            installConfig(false)
        }
        content.extremePerformance.visibility = if (ThermalDisguise().supported()) View.VISIBLE else View.GONE
        content.extremePerformanceOn.setOnClickListener {
            if ((it as CompoundButton).isChecked) ThermalDisguise().disableMessage()
            else ThermalDisguise().resumeMessage()
        }
    }

    private fun chooseConfigSource() {
        val view = layoutInflater.inflate(R.layout.dialog_powercfg_source, null)
        val dialog = DialogHelper.customDialog(activity!!, view)
        val conservative = view.findViewById<View>(R.id.source_official_conservative)
        val active       = view.findViewById<View>(R.id.source_official_active)
        val cpuConfigInstaller = CpuConfigInstaller()
        if (cpuConfigInstaller.dynamicSupport(context!!)) {
            conservative.setOnClickListener {
                if (configInstaller.outsideConfigInstalled()) configInstaller.removeOutsideConfig()
                installConfig(false); dialog.dismiss()
            }
            active.setOnClickListener {
                if (configInstaller.outsideConfigInstalled()) configInstaller.removeOutsideConfig()
                installConfig(true); dialog.dismiss()
            }
        } else {
            conservative.visibility = View.GONE; active.visibility = View.GONE
        }
        view.findViewById<View>(R.id.source_import).setOnClickListener { chooseLocalConfig(); dialog.dismiss() }
        view.findViewById<View>(R.id.source_download).setOnClickListener {
            if (outsideOverrode()) configInstaller.removeOutsideConfig()
            getOnlineConfig(); dialog.dismiss()
        }
        view.findViewById<View>(R.id.source_custom).setOnClickListener {
            if (outsideOverrode()) configInstaller.removeOutsideConfig()
            globalSPF.edit().putString(SpfConfig.GLOBAL_SPF_PROFILE_SOURCE, ModeSwitcher.SOURCE_SCENE_CUSTOM).apply()
            updateState(); dialog.dismiss()
        }
    }

    private class ModeOnItemSelectedListener(private val globalSPF: SharedPreferences, private val runnable: Runnable) : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {}
        @SuppressLint("ApplySharedPref")
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val mode = when (position) { 0 -> ModeSwitcher.POWERSAVE; 1 -> ModeSwitcher.BALANCE; 2 -> ModeSwitcher.PERFORMANCE; 3 -> ModeSwitcher.FAST; else -> ModeSwitcher.IGONED }
            if (globalSPF.getString(SpfConfig.GLOBAL_SPF_POWERCFG_FIRST_MODE, ModeSwitcher.DEFAULT) != mode) {
                globalSPF.edit().putString(SpfConfig.GLOBAL_SPF_POWERCFG_FIRST_MODE, mode).commit()
                runnable.run()
            }
        }
    }

    private class ModeOnItemSelectedListener2(private val globalSPF: SharedPreferences, private val runnable: Runnable) : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {}
        @SuppressLint("ApplySharedPref")
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val mode = when (position) { 0 -> ModeSwitcher.POWERSAVE; 1 -> ModeSwitcher.BALANCE; 2 -> ModeSwitcher.PERFORMANCE; else -> ModeSwitcher.IGONED }
            if (globalSPF.getString(SpfConfig.GLOBAL_SPF_POWERCFG_SLEEP_MODE, ModeSwitcher.POWERSAVE) != mode) {
                globalSPF.edit().putString(SpfConfig.GLOBAL_SPF_POWERCFG_SLEEP_MODE, mode).commit()
                runnable.run()
            }
        }
    }

    private fun bindMode(button: View, mode: String) {
        button.setOnClickListener {
            val b = contentBinding ?: return@setOnClickListener
            if (mode == ModeSwitcher.FAST && ModeSwitcher.getCurrentSource() == ModeSwitcher.SOURCE_OUTSIDE_UPERF) {
                DialogHelper.warning(activity!!, getString(R.string.please_notice), getString(R.string.schedule_uperf_fast), {
                    modeSwitcher.executePowercfgMode(mode, context!!.packageName)
                    updateState(b.cpuConfigP3, ModeSwitcher.FAST)
                })
            } else {
                modeSwitcher.executePowercfgMode(mode, context!!.packageName)
                updateState(b.cpuConfigP0, ModeSwitcher.POWERSAVE)
                updateState(b.cpuConfigP1, ModeSwitcher.BALANCE)
                updateState(b.cpuConfigP2, ModeSwitcher.PERFORMANCE)
                updateState(b.cpuConfigP3, ModeSwitcher.FAST)
            }
        }
    }

    private fun updateState() {
        val vb = contentBinding ?: return
        val outsideInstalled = configInstaller.outsideConfigInstalled()
        configFileInstalled = outsideInstalled || configInstaller.insideConfigInstalled()
        author = ModeSwitcher.getCurrentSource()
        vb.configAuthor.text = ModeSwitcher.getCurrentSourceName()
        updateState(vb.cpuConfigP0, ModeSwitcher.POWERSAVE)
        updateState(vb.cpuConfigP1, ModeSwitcher.BALANCE)
        updateState(vb.cpuConfigP2, ModeSwitcher.PERFORMANCE)
        updateState(vb.cpuConfigP3, ModeSwitcher.FAST)
        val serviceState   = AccessibleServiceHelper().serviceRunning(context!!)
        val dynamicControl = globalSPF.getBoolean(SpfConfig.GLOBAL_SPF_DYNAMIC_CONTROL, SpfConfig.GLOBAL_SPF_DYNAMIC_CONTROL_DEFAULT)
        vb.dynamicControl.isChecked = dynamicControl && serviceState
        val serviceNoticeVisible = if (serviceState) View.GONE else View.VISIBLE
        showServiceNotice.value = serviceNoticeVisible == View.VISIBLE
        vb.navSceneServiceNotActive.visibility = serviceNoticeVisible
        cardServiceNoticeView?.visibility = serviceNoticeVisible
        if (dynamicControl && !modeSwitcher.modeConfigCompleted()) {
            globalSPF.edit().putBoolean(SpfConfig.GLOBAL_SPF_DYNAMIC_CONTROL, false).apply()
            vb.dynamicControl.isChecked = false; reStartService()
        }
        vb.dynamicControlOpts.postDelayed({
            contentBinding?.dynamicControlOpts?.visibility =
                if (contentBinding?.dynamicControl?.isChecked == true) View.VISIBLE else View.GONE
        }, 15)
        vb.extremePerformanceOn.isChecked = ThermalDisguise().isDisabled()
    }

    private fun updateState(button: View, mode: String) {
        button.alpha = if (configFileInstalled && ModeSwitcher.getCurrentPowerMode() == mode) 1f else 0.4f
    }

    override fun onResume() {
        super.onResume()
        val currentAuthor = author
        updateState()
        val b = contentBinding
        if (b != null && b.dynamicControl.isChecked && currentAuthor.isNotEmpty() && currentAuthor != author) reStartService()
    }

    private val configInstaller = CpuConfigInstaller()
    private var useInnerFileChooser = false
    private val configFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val ctx  = context ?: return@registerForActivityResult
        if (Build.VERSION.SDK_INT >= 30 && !useInnerFileChooser) {
            val absPath = FilePathResolver().getPath(activity, data.data)
            if (absPath != null) {
                if (absPath.endsWith(".sh")) installLocalConfig(absPath)
                else Toast.makeText(ctx, "Invalid file (should be a .sh file)!", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(ctx, "Selected file not found!", Toast.LENGTH_SHORT).show()
        } else {
            val path = data.extras?.getString("file") ?: return@registerForActivityResult
            installLocalConfig(path)
        }
    }

    private fun chooseLocalConfig() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            useInnerFileChooser = false
            configFileLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) })
        } else {
            useInnerFileChooser = true
            try {
                configFileLauncher.launch(Intent(context, ActivityFileSelector::class.java).apply { putExtra("extension", "sh") })
            } catch (ex: Exception) {
                Toast.makeText(context, "Failed to launch built-in file picker!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openUrl(link: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (ex: Exception) {}
    }

    private fun readFileLines(file: File): String? {
        if (file.canRead()) return file.readText(Charset.defaultCharset()).trimStart().replace("\r", "")
        val innerPath = FileWrite.getPrivateFilePath(context!!, "powercfg.tmp")
        KeepShellPublic.doCmdSync("cp \"${file.absolutePath}\" \"$innerPath\"\nchmod 777 \"$innerPath\"")
        val tmpFile = File(innerPath)
        if (tmpFile.exists() && tmpFile.canRead()) {
            val lines = tmpFile.readText(Charset.defaultCharset()).trimStart().replace("\r", "")
            KeepShellPublic.doCmdSync("rm \"$innerPath\"")
            return lines
        }
        return null
    }

    private fun getOnlineConfig() {
        DialogHelper.alert(activity!!, "Notice",
            "Scene no longer provides online config scripts. If needed, use the optimization module by \"yc9559\" and flash it with Magisk.") {
            openUrl("https://github.com/yc9559/uperf")
        }
    }

    private fun installLocalConfig(path: String) {
        if (!path.endsWith(".sh")) { Toast.makeText(context, "Invalid script file!", Toast.LENGTH_LONG).show(); return }
        val file = File(path)
        if (!file.exists()) { Toast.makeText(context, "Selected file not found!", Toast.LENGTH_LONG).show(); return }
        if (file.length() > 200 * 1024) { Toast.makeText(context, "File too large (max 200KB)!", Toast.LENGTH_LONG).show(); return }
        val lines = readFileLines(file) ?: run { Toast.makeText(context, "Scene cannot read this file!", Toast.LENGTH_LONG).show(); return }
        val firstLine = lines.split("\n").firstOrNull()
        if (firstLine != null && (firstLine.startsWith("#!/") || lines.contains("echo "))) {
            if (configInstaller.installCustomConfig(context!!, lines, ModeSwitcher.SOURCE_SCENE_IMPORT)) configInstalled()
            else Toast.makeText(context, "Failed to install config script.", Toast.LENGTH_LONG).show()
        } else Toast.makeText(context, "Invalid script file!", Toast.LENGTH_LONG).show()
    }

    private fun installConfig(active: Boolean) {
        if (!configInstaller.dynamicSupport(context!!)) { Scene.toast(R.string.not_support_config, Toast.LENGTH_LONG); return }
        configInstaller.installOfficialConfig(context!!, "", active)
        configInstalled()
    }

    private fun configInstalled() { updateState(); reStartService() }

    private fun outsideOverrode(): Boolean {
        if (configInstaller.outsideConfigInstalled()) {
            DialogHelper.helpInfo(activity!!, "You need to delete the external config first.")
            return true
        }
        return false
    }

    private fun reStartService() { EventBus.publish(EventType.SERVICE_UPDATE) }
    private fun detachFromParent(view: View): View { (view.parent as? ViewGroup)?.removeView(view); return view }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null; contentBinding = null
        cardModesView = null; cardServiceNoticeView = null
        cardDynamicView = null; cardShortcutsView = null; cardMoreView = null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TunerScreen(
    cardModes: View?,
    cardServiceNotice: View?,
    showServiceNotice: Boolean,
    cardDynamic: View?,
    cardShortcuts: View?,
    cardMore: View?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Md3CardSection(cardModes, PaddingValues(start = 4.dp, top = 0.dp, end = 4.dp, bottom = 8.dp))
        if (showServiceNotice) Md3CardSection(cardServiceNotice)
        Md3CardSection(cardDynamic, PaddingValues(8.dp))
        Md3CardSection(cardShortcuts, PaddingValues(start = 8.dp, top = 0.dp, end = 8.dp, bottom = 8.dp))
        if (cardMore?.visibility == View.VISIBLE) Md3CardSection(cardMore)
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun Md3CardSection(
    view: View?,
    insidePadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
) {
    if (view == null) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(insidePadding)) {
            AndroidView(factory = {
                view.apply { layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT) }
            })
        }
    }
}
