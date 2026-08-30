package com.v2ray.ang.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.dto.entities.PanelSubscriptionMetadata
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MainActivity : HelperBaseActivity() {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    val mainViewModel: MainViewModel by viewModels()
    private lateinit var groupPagerAdapter: GroupPagerAdapter
    private var tabMediator: TabLayoutMediator? = null
    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            showSubscriptionMetadata(groupPagerAdapter.groups.getOrNull(position)?.id)
        }
    }

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }
    private val requestActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (SettingsChangeManager.consumeRestartService() && mainViewModel.isRunning.value == true) {
            restartV2Ray()
        }
        if (SettingsChangeManager.consumeSetupGroupTab()) {
            setupGroupTab()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupTopBar()

        // setup viewpager and tablayout
        groupPagerAdapter = GroupPagerAdapter(this, emptyList())
        binding.viewPager.adapter = groupPagerAdapter
        binding.viewPager.isUserInputEnabled = true
        binding.viewPager.registerOnPageChangeCallback(pageChangeCallback)

        binding.fab.setOnClickListener { handleFabAction() }
        binding.buttonAdd.setOnClickListener { showAddSubscriptionMenu(it) }
        binding.buttonPing.setOnClickListener { mainViewModel.testAllRealPing() }
        binding.buttonRefresh.setOnClickListener { importConfigViaSub() }

        setupGroupTab()
        setupViewModel()
        SubscriptionUpdater.sync()
        mainViewModel.reloadServerList()

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {
        }
    }

    private fun setupTopBar() {
        binding.toolbar.navigationContentDescription = getString(R.string.title_sub_setting)
        binding.toolbar.setNavigationOnClickListener {
            requestActivityLauncher.launch(Intent(this, SubSettingActivity::class.java))
        }
        binding.toolbar.inflateMenu(R.menu.menu_simple_main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.simple_settings) {
                requestActivityLauncher.launch(Intent(this, SettingsActivity::class.java))
                true
            } else {
                false
            }
        }
    }

    private fun setupViewModel() {
        mainViewModel.updateTestResultAction.observe(this) { setTestState(it) }
        mainViewModel.isRunning.observe(this) { isRunning ->
            applyRunningState(false, isRunning)
        }
        mainViewModel.isTesting.observe(this) { isTesting ->
            binding.buttonPing.isEnabled = !isTesting
            binding.buttonPing.text = getString(
                if (isTesting) R.string.simple_testing else R.string.simple_ping
            )
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun setupGroupTab() {
        val groups = mainViewModel.getSubscriptions(this)
        groupPagerAdapter.update(groups)

        tabMediator?.detach()
        tabMediator = TabLayoutMediator(binding.tabGroup, binding.viewPager) { tab, position ->
            groupPagerAdapter.groups.getOrNull(position)?.let {
                tab.text = it.remarks
                tab.tag = it.id
            }
        }.also { it.attach() }

        val targetIndex = groups.indexOfFirst { it.id == mainViewModel.subscriptionId }.takeIf { it >= 0 } ?: (groups.size - 1)
        if (targetIndex >= 0) {
            binding.viewPager.setCurrentItem(targetIndex, false)
        }

        binding.tabGroup.isVisible = groups.isNotEmpty()
        binding.subscriptionLabel.isVisible = groups.isNotEmpty()
        showSubscriptionMetadata(groups.getOrNull(targetIndex)?.id)
        refreshGroupTabTitles(true)
    }

    private fun showSubscriptionMetadata(subscriptionId: String?) {
        val subscription = subscriptionId
            ?.takeIf { it.isNotEmpty() }
            ?.let(MmkvManager::decodeSubscription)
        val metadata = subscription?.panelMetadata
        if (subscription == null || metadata == null) {
            binding.subscriptionMetadataCard.isVisible = false
            return
        }

        binding.subscriptionMetadataCard.isVisible = true
        binding.tvPanelWorkspace.text = metadata.workspace
            ?.takeIf { it.isNotBlank() }
            ?: subscription.remarks

        val user = metadata.user?.takeIf { it.isNotBlank() }
        binding.tvPanelUser.isVisible = user != null
        binding.tvPanelUser.text = user?.let { getString(R.string.simple_panel_user, it) }.orEmpty()

        val status = metadata.status?.takeIf { it.isNotBlank() }
        binding.panelStatusCard.isVisible = status != null
        if (status != null) {
            binding.tvPanelStatus.text = status.replaceFirstChar { it.uppercaseChar() }
            binding.panelStatusCard.setCardBackgroundColor(
                ContextCompat.getColor(this, statusColor(status))
            )
        }

        val remaining = formatRemainingTime(metadata)
        binding.tvPanelRemaining.isVisible = remaining != null
        binding.tvPanelRemaining.text = remaining.orEmpty()

        val dates = buildList {
            metadata.startsOn?.takeIf { it.isNotBlank() }?.let {
                add(getString(R.string.simple_starts_on, displayDateValue(it)))
            }
            displayExpiryDate(metadata)?.let {
                add(getString(R.string.simple_expires_on, it))
            }
        }
        binding.tvPanelDates.isVisible = dates.isNotEmpty()
        binding.tvPanelDates.text = dates.joinToString(" • ")

        val details = buildList {
            metadata.refreshIntervalMinutes?.takeIf { it > 0L }?.let {
                add(getString(R.string.simple_refresh_every, formatRefreshInterval(it)))
            }
            metadata.metadataVersion?.let {
                add(getString(R.string.simple_metadata_version, it))
            }
        }
        val telegramUrl = metadata.telegramUrl?.takeIf { it.isNotBlank() }
        binding.panelMetadataFooter.isVisible = details.isNotEmpty() || telegramUrl != null
        binding.tvPanelMetadataDetails.text = details.joinToString(" • ")
        binding.buttonPanelTelegram.isVisible = telegramUrl != null
        binding.buttonPanelTelegram.setOnClickListener(
            telegramUrl?.let { url -> View.OnClickListener { Utils.openUri(this, url) } }
        )
    }

    private fun statusColor(status: String): Int = when (status) {
        "active" -> R.color.colorPing
        "scheduled" -> R.color.color_fab_active
        "expired" -> R.color.colorPingRed
        else -> R.color.color_fab_inactive
    }

    private fun formatRemainingTime(metadata: PanelSubscriptionMetadata): String? {
        metadata.expiresAt
            ?.take(10)
            ?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { expiryDate ->
                return when (val days = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate)) {
                    in Long.MIN_VALUE..-1L -> getString(R.string.simple_expired)
                    0L -> getString(R.string.simple_expires_today)
                    1L -> getString(R.string.simple_one_day_remaining)
                    else -> getString(R.string.simple_days_remaining, days)
                }
            }

        val expiry = metadata.expireEpochSeconds ?: return null
        val seconds = expiry - Instant.now().epochSecond
        if (seconds <= 0L) return getString(R.string.simple_expired)
        val days = seconds / SECONDS_PER_DAY
        if (days > 0L) {
            return if (days == 1L) {
                getString(R.string.simple_one_day_remaining)
            } else {
                getString(R.string.simple_days_remaining, days)
            }
        }
        val hours = seconds / SECONDS_PER_HOUR
        return if (hours > 0L) {
            if (hours == 1L) {
                getString(R.string.simple_one_hour_remaining)
            } else {
                getString(R.string.simple_hours_remaining, hours)
            }
        } else {
            getString(R.string.simple_less_than_hour_remaining)
        }
    }

    private fun displayExpiryDate(metadata: PanelSubscriptionMetadata): String? {
        metadata.expiresAt?.takeIf { it.isNotBlank() }?.let { raw ->
            if (raw.toLongOrNull() == null) return displayDateValue(raw)
        }
        val expiry = metadata.expireEpochSeconds ?: return null
        return Instant.ofEpochSecond(expiry)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    private fun displayDateValue(value: String): String {
        val datePrefix = value.take(10)
        return if (datePrefix.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) datePrefix else value
    }

    private fun formatRefreshInterval(minutes: Long): String = when {
        minutes % MINUTES_PER_DAY == 0L ->
            getString(R.string.simple_interval_days, minutes / MINUTES_PER_DAY)
        minutes % MINUTES_PER_HOUR == 0L ->
            getString(R.string.simple_interval_hours, minutes / MINUTES_PER_HOUR)
        else -> getString(R.string.simple_interval_minutes, minutes)
    }

    fun refreshGroupTabTitles(refreshAll: Boolean = false) {
        val groupsToRefresh = if (refreshAll || mainViewModel.subscriptionId.isEmpty()) {
            groupPagerAdapter.groups
        } else {
            groupPagerAdapter.groups.filter { it.id == mainViewModel.subscriptionId }
        }

        groupsToRefresh.forEach { group ->
            if (group.id.isEmpty()) {
                return@forEach
            }
            val tabIndex = groupPagerAdapter.groups.indexOfFirst { it.id == group.id }
            if (tabIndex >= 0) {
                val count = MmkvManager.decodeServerList(group.id).size
                binding.tabGroup.getTabAt(tabIndex)?.text = "${group.remarks} ($count)"
            }
        }
    }

    private fun handleFabAction() {
        applyRunningState(isLoading = true, isRunning = false)

        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        } else if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                startV2Ray()
            } else {
                requestVpnPermission.launch(intent)
            }
        } else {
            startV2Ray()
        }
    }

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        CoreServiceManager.startVService(this)
    }

    fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            delay(500)
            startV2Ray()
        }
    }

    private fun setTestState(content: String?) {
        binding.tvTestState.text = content
    }

    private fun showAddSubscriptionMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(Menu.NONE, ADD_FROM_CLIPBOARD, Menu.NONE, R.string.simple_clipboard)
                .setIcon(R.drawable.ic_copy)
            menu.add(Menu.NONE, ADD_FROM_QR_CODE, Menu.NONE, R.string.simple_qr_code)
                .setIcon(R.drawable.ic_scan_24dp)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    ADD_FROM_CLIPBOARD -> addSubscriptionFromClipboard()
                    ADD_FROM_QR_CODE -> addSubscriptionFromQrCode()
                    else -> false
                }
            }
            show()
        }
    }

    private fun addSubscriptionFromClipboard(): Boolean {
        return try {
            val url = Utils.getClipboard(this).trim()
            if (url.isEmpty()) {
                toast(R.string.toast_none_data_clipboard)
                false
            } else {
                addSubscription(url)
                true
            }
        } catch (error: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to read subscription from clipboard", error)
            toastError(R.string.toast_failure)
            false
        }
    }

    private fun addSubscriptionFromQrCode(): Boolean {
        launchQRCodeScanner { scanResult ->
            scanResult?.trim()?.takeIf { it.isNotEmpty() }?.let(::addSubscription)
        }
        return true
    }

    private fun addSubscription(url: String) {
        val cleanUrl = url.trim()
        if (!Utils.isValidUrl(cleanUrl)) {
            toast(R.string.toast_invalid_url)
            return
        }
        if (!Utils.isValidSubUrl(cleanUrl)) {
            toast(R.string.toast_insecure_url_protocol)
            return
        }
        if (MmkvManager.decodeSubscriptions().any {
                it.subscription.url.trim().equals(cleanUrl, ignoreCase = true)
            }
        ) {
            toast(R.string.simple_subscription_exists)
            return
        }

        val remarks = subscriptionNameFromLink(cleanUrl)
        val subscription = SubscriptionItem(remarks = remarks, url = cleanUrl)
        val subscriptionId = Utils.getUuid()
        MmkvManager.encodeSubscription(subscriptionId, subscription)
        mainViewModel.subscriptionIdChanged(subscriptionId)
        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                AngConfigManager.updateConfigViaSub(
                    SubscriptionCache(subscriptionId, subscription)
                )
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { updateResult ->
                    setupGroupTab()
                    mainViewModel.reloadServerList()
                    refreshGroupTabTitles()
                    SubscriptionUpdater.sync()
                    if (updateResult.successCount > 0) {
                        toast(
                            getString(
                                R.string.title_update_config_count,
                                updateResult.configCount
                            )
                        )
                    } else {
                        toastError(R.string.simple_subscription_update_failed)
                    }
                }.onFailure { error ->
                    LogUtil.e(AppConfig.TAG, "Failed to add subscription", error)
                    setupGroupTab()
                    toastError(R.string.simple_subscription_update_failed)
                }
                hideLoading()
            }
        }
    }

    /** Uses the human-readable name carried after # in the subscription link. */
    private fun subscriptionNameFromLink(url: String): String {
        return runCatching {
            val uri = URI(url)
            uri.fragment
                ?.trim()
                ?.lineSequence()
                ?.firstOrNull()
                ?.take(MAX_SUBSCRIPTION_NAME_LENGTH)
                ?.takeIf { it.isNotEmpty() }
                ?: uri.host
                    ?.removePrefix("www.")
                    ?.take(MAX_SUBSCRIPTION_NAME_LENGTH)
                    .orEmpty()
        }.getOrDefault("").ifEmpty {
            getString(R.string.simple_default_subscription_name)
        }
    }

    private fun applyRunningState(isLoading: Boolean, isRunning: Boolean) {
        if (isLoading) {
            binding.fab.setImageResource(R.drawable.ic_fab_check)
            return
        }

        if (isRunning) {
            binding.fab.setImageResource(R.drawable.ic_stop_24dp)
            binding.fab.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.color_fab_active)
            )
            binding.fab.contentDescription = getString(R.string.action_stop_service)
            setTestState(getString(R.string.simple_connected))
        } else {
            binding.fab.setImageResource(R.drawable.ic_play_24dp)
            binding.fab.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.color_fab_inactive)
            )
            binding.fab.contentDescription = getString(R.string.tasker_start_service)
            setTestState(getString(R.string.simple_tap_to_connect))
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.import_qrcode -> {
            importQRcode()
            true
        }

        R.id.import_clipboard -> {
            importClipboard()
            true
        }

        R.id.import_local -> {
            importConfigLocal()
            true
        }

        R.id.import_manually_policy_group -> {
            importManually(EConfigType.POLICYGROUP.value)
            true
        }

        R.id.import_manually_proxy_chain -> {
            importManually(EConfigType.PROXYCHAIN.value)
            true
        }

        R.id.import_manually_vmess -> {
            importManually(EConfigType.VMESS.value)
            true
        }

        R.id.import_manually_vless -> {
            importManually(EConfigType.VLESS.value)
            true
        }

        R.id.import_manually_ss -> {
            importManually(EConfigType.SHADOWSOCKS.value)
            true
        }

        R.id.import_manually_socks -> {
            importManually(EConfigType.SOCKS.value)
            true
        }

        R.id.import_manually_http -> {
            importManually(EConfigType.HTTP.value)
            true
        }

        R.id.import_manually_trojan -> {
            importManually(EConfigType.TROJAN.value)
            true
        }

        R.id.import_manually_wireguard -> {
            importManually(EConfigType.WIREGUARD.value)
            true
        }

        R.id.import_manually_hysteria2 -> {
            importManually(EConfigType.HYSTERIA2.value)
            true
        }

        R.id.export_all -> {
            exportAll()
            true
        }

        R.id.real_ping_all -> {
            toast(getString(R.string.connection_test_testing_count, mainViewModel.serversCache.count()))
            mainViewModel.testAllRealPing()
            true
        }

        R.id.service_restart -> {
            restartV2Ray()
            true
        }

        R.id.del_all_config -> {
            delAllConfig()
            true
        }

        R.id.del_duplicate_config -> {
            delDuplicateConfig()
            true
        }

        R.id.del_invalid_config -> {
            delInvalidConfig()
            true
        }

        R.id.sort_by_test_results -> {
            sortByTestResults()
            true
        }

        R.id.sub_update -> {
            importConfigViaSub()
            true
        }

        R.id.locate_selected_config -> {
            locateSelectedServer()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun importManually(createConfigType: Int) {
        if (createConfigType == EConfigType.POLICYGROUP.value) {
            startActivity(
                Intent()
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerGroupActivity::class.java)
            )
        } else if (createConfigType == EConfigType.PROXYCHAIN.value) {
            startActivity(
                Intent()
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerProxyChainActivity::class.java)
            )
        } else {
            startActivity(
                Intent()
                    .putExtra("createConfigType", createConfigType)
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerActivity::class.java)
            )
        }
    }

    /**
     * import config from qrcode
     */
    private fun importQRcode(): Boolean {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                importBatchConfig(scanResult)
            }
        }
        return true
    }

    /**
     * import config from clipboard
     */
    private fun importClipboard()
            : Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            importBatchConfig(clipboard)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
            return false
        }
        return true
    }

    private fun importBatchConfig(server: String?) {
        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (count, countSub) = AngConfigManager.importBatchConfig(server, mainViewModel.subscriptionId, true)
                delay(500L)
                withContext(Dispatchers.Main) {
                    when {
                        count > 0 -> {
                            toast(getString(R.string.title_import_config_count, count))
                            mainViewModel.reloadServerList()
                            refreshGroupTabTitles()
                        }

                        countSub > 0 -> setupGroupTab()
                        else -> toastError(R.string.toast_failure)
                    }
                    hideLoading()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_failure)
                    hideLoading()
                }
                LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
            }
        }
    }

    /**
     * import config from local config file
     */
    private fun importConfigLocal(): Boolean {
        try {
            showFileChooser()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from local file", e)
            return false
        }
        return true
    }


    /**
     * import config from sub
     */
    fun importConfigViaSub(): Boolean {
        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            val result = mainViewModel.updateConfigViaSubAll()
            delay(500L)
            launch(Dispatchers.Main) {
                if (result.successCount + result.failureCount + result.skipCount == 0) {
                    toast(R.string.title_update_subscription_no_subscription)
                } else if (result.successCount > 0 && result.failureCount + result.skipCount == 0) {
                    toast(getString(R.string.title_update_config_count, result.configCount))
                } else {
                    toast(
                        getString(
                            R.string.title_update_subscription_result,
                            result.configCount, result.successCount, result.failureCount, result.skipCount
                        )
                    )
                }
                if (result.configCount > 0) {
                    mainViewModel.reloadServerList()
                    refreshGroupTabTitles()
                }
                if (mainViewModel.subscriptionId.isNotEmpty()) {
                    SubscriptionUpdater.syncOne(subId = mainViewModel.subscriptionId)
                } else {
                    SubscriptionUpdater.sync(forceReschedule = true)
                }
                showSubscriptionMetadata(mainViewModel.subscriptionId)
                hideLoading()
            }
        }
        return true
    }

    private fun exportAll() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val ret = mainViewModel.exportAllServer()
            launch(Dispatchers.Main) {
                if (ret > 0)
                    toast(getString(R.string.title_export_config_count, ret))
                else
                    toastError(R.string.toast_failure)
                hideLoading()
            }
        }
    }

    private fun delAllConfig() {
        AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeAllServer()
                    launch(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        refreshGroupTabTitles()
                        toast(getString(R.string.title_del_config_count, ret))
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do noting
            }
            .show()
    }

    private fun delDuplicateConfig() {
        AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeDuplicateServer()
                    launch(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        refreshGroupTabTitles()
                        toast(getString(R.string.title_del_duplicate_config_count, ret))
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do noting
            }
            .show()
    }

    private fun delInvalidConfig() {
        AlertDialog.Builder(this).setMessage(R.string.del_invalid_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeInvalidServer()
                    launch(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        refreshGroupTabTitles()
                        toast(getString(R.string.title_del_config_count, ret))
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do noting
            }
            .show()
    }

    private fun sortByTestResults() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            mainViewModel.sortByTestResults()
            launch(Dispatchers.Main) {
                mainViewModel.reloadServerList()
                hideLoading()
            }
        }
    }

    /**
     * show file chooser
     */
    private fun showFileChooser() {
        launchFileChooser { uri ->
            if (uri == null) {
                return@launchFileChooser
            }

            readContentFromUri(uri)
        }
    }

    /**
     * read content from uri
     */
    private fun readContentFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri).use { input ->
                importBatchConfig(input?.bufferedReader()?.readText())
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to read content from URI", e)
        }
    }

    /**
     * Locates and scrolls to the currently selected server.
     * If the selected server is in a different group, automatically switches to that group first.
     */
    private fun locateSelectedServer() {
        val targetSubscriptionId = mainViewModel.findSubscriptionIdBySelect()
        if (targetSubscriptionId.isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }

        val targetGroupIndex = groupPagerAdapter.groups.indexOfFirst { it.id == targetSubscriptionId }
        if (targetGroupIndex < 0) {
            toast(R.string.toast_server_not_found_in_group)
            return
        }

        // Switch to target group if needed, then scroll to the server
        if (binding.viewPager.currentItem != targetGroupIndex) {
            binding.viewPager.setCurrentItem(targetGroupIndex, true)
            binding.viewPager.postDelayed({ scrollToSelectedServer(targetGroupIndex) }, 1000)
        } else {
            scrollToSelectedServer(targetGroupIndex)
        }
    }

    /**
     * Scrolls to the selected server in the specified fragment.
     * @param groupIndex The index of the group/fragment to scroll in
     */
    private fun scrollToSelectedServer(groupIndex: Int) {
        val itemId = groupPagerAdapter.getItemId(groupIndex)
        val fragment = supportFragmentManager.findFragmentByTag("f$itemId") as? GroupServerFragment

        if (fragment?.isAdded == true && fragment.view != null) {
            fragment.scrollToSelectedServer()
        } else {
            toast(R.string.toast_fragment_not_available)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }


    override fun onDestroy() {
        binding.viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        tabMediator?.detach()
        super.onDestroy()
    }

    companion object {
        private const val ADD_FROM_CLIPBOARD = 1
        private const val ADD_FROM_QR_CODE = 2
        private const val MAX_SUBSCRIPTION_NAME_LENGTH = 80
        private const val SECONDS_PER_HOUR = 60L * 60L
        private const val SECONDS_PER_DAY = 24L * SECONDS_PER_HOUR
        private const val MINUTES_PER_HOUR = 60L
        private const val MINUTES_PER_DAY = 24L * MINUTES_PER_HOUR
    }
}
