package com.mx.apn

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.mx.apn.data.Apn
import com.mx.apn.data.Carrier
import com.mx.apn.databinding.ActivityMainBinding
import com.mx.apn.ui.ApnAdapter
import com.mx.apn.ui.MainViewModel
import com.mx.apn.util.ApnImporter
import com.mx.apn.util.CarrierDetector

/**
 * 主界面
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: ApnAdapter
    private var lastUpdateEventId = 0

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupListeners()

        checkPermissions()
        viewModel.loadData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
        supportActionBar?.subtitle = "v${viewModel.getCurrentVersionName()}"
    }

    private fun setupRecyclerView() {
        adapter = ApnAdapter(
            onItemClick = { apn -> showApnDetail(apn) },
            onImportClick = { apn -> showImportOptions(apn) },
            onCopyClick = { apn ->
                val result = ApnImporter.copyToClipboard(this, apn)
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.loading.observe(this) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
            binding.progressBar.visibility = if (isLoading && !binding.swipeRefresh.isRefreshing) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            if (!error.isNullOrEmpty()) {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = error
            } else {
                binding.tvError.visibility = View.GONE
            }
        }

        viewModel.carriers.observe(this) { carriers ->
            if (carriers.isNotEmpty()) {
                setupCarrierSpinner(carriers)
            }
        }

        viewModel.selectedCarrier.observe(this) { carrier ->
            carrier?.let {
                adapter.submitList(it.apns)
                binding.tvCarrierInfo.text = "${it.name} (${it.nameEn})"
            }
        }

        viewModel.detectedCarrier.observe(this) { carrier ->
            if (carrier != null) {
                binding.tvDetectedCarrier.text = "检测到: ${carrier.name}"
                binding.tvDetectedCarrier.visibility = View.VISIBLE
            } else {
                binding.tvDetectedCarrier.text = "未检测到 SIM 卡或非中国运营商"
                binding.tvDetectedCarrier.visibility = View.VISIBLE
            }
        }

        viewModel.recommendedApn.observe(this) { apn ->
            if (apn != null) {
                binding.cardRecommended.visibility = View.VISIBLE
                binding.tvRecommendName.text = apn.name
                binding.tvRecommendApn.text = apn.apn
                binding.tvRecommendNote.text = apn.note
            } else {
                binding.cardRecommended.visibility = View.GONE
            }
        }

        viewModel.dataVersion.observe(this) { version ->
            val source = if (viewModel.isRemoteData.value == true) "远程" else "本地"
            binding.tvDataVersion.text = "数据版本: $version ($source)"
        }

        viewModel.simInfo.observe(this) { simInfo ->
            if (simInfo.isAvailable) {
                val name = CarrierDetector.getCarrierName(simInfo.mcc, simInfo.mnc)
                val networkType = CarrierDetector.getNetworkType(this)
                binding.tvSimInfo.text = "SIM: $name | 网络: $networkType | MCC:${simInfo.mcc} MNC:${simInfo.mnc}"
            } else {
                binding.tvSimInfo.text = "SIM: 未检测到或无权限"
            }
        }

        viewModel.updateEvent.observe(this) { event ->
            if (event == null || event.id == lastUpdateEventId) return@observe
            lastUpdateEventId = event.id

            when (event.type) {
                MainViewModel.UpdateEventType.CHECKING -> {
                    binding.btnCheckUpdate.isEnabled = false
                    binding.btnCheckUpdate.text = "检测中..."
                }
                MainViewModel.UpdateEventType.NEW_VERSION -> {
                    binding.btnCheckUpdate.isEnabled = true
                    binding.btnCheckUpdate.text = "检查更新"
                    event.info?.let { showUpdateDialog(it) }
                }
                MainViewModel.UpdateEventType.LATEST -> {
                    binding.btnCheckUpdate.isEnabled = true
                    binding.btnCheckUpdate.text = "检查更新"
                    Toast.makeText(
                        this,
                        "已是最新版本 v${event.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                MainViewModel.UpdateEventType.ERROR -> {
                    binding.btnCheckUpdate.isEnabled = true
                    binding.btnCheckUpdate.text = "检查更新"
                    Toast.makeText(
                        this,
                        "检查更新失败: ${event.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }

        binding.btnUseRecommend.setOnClickListener {
            viewModel.recommendedApn.value?.let { apn ->
                showImportOptions(apn)
            }
        }

        binding.btnCheckUpdate.setOnClickListener {
            viewModel.checkAppUpdate()
        }
    }

    /**
     * 显示新版本对话框
     */
    private fun showUpdateDialog(info: com.mx.apn.network.UpdateChecker.UpdateInfo) {
        val changelog = if (info.changelog.isBlank()) "无更新说明" else info.changelog
        AlertDialog.Builder(this)
            .setTitle("发现新版本 v${info.versionName}")
            .setMessage(changelog)
            .setPositiveButton("去下载") { _, _ ->
                openDownloadUrl(info.apkUrl)
            }
            .setNegativeButton("以后再说", null)
            .show()
    }

    private fun openDownloadUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开下载链接: $url", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupCarrierSpinner(carriers: List<Carrier>) {
        val carrierNames = carriers.map { it.name }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, carrierNames)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCarrier.adapter = spinnerAdapter

        // 设置默认选中
        val detected = viewModel.detectedCarrier.value
        val defaultIndex = detected?.let { carriers.indexOf(it) } ?: 0
        if (defaultIndex >= 0) {
            binding.spinnerCarrier.setSelection(defaultIndex)
        }

        binding.spinnerCarrier.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.selectCarrier(carriers[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /**
     * 显示 APN 详细信息
     */
    private fun showApnDetail(apn: Apn) {
        AlertDialog.Builder(this)
            .setTitle(apn.name)
            .setMessage(apn.toDetailText())
            .setPositiveButton("复制信息") { _, _ ->
                val result = ApnImporter.copyToClipboard(this, apn)
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("导入", { _, _ ->
                showImportOptions(apn)
            })
            .setNeutralButton("关闭", null)
            .show()
    }

    /**
     * 显示导入选项
     */
    private fun showImportOptions(apn: Apn) {
        val options = arrayOf(
            "直接导入（需系统权限）",
            "Root 导入",
            "复制信息后手动设置",
            "打开 APN 设置页面"
        )

        AlertDialog.Builder(this)
            .setTitle("导入 APN - ${apn.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val result = ApnImporter.importViaContentResolver(this, apn)
                        showImportResult(result)
                    }
                    1 -> {
                        val result = ApnImporter.importViaRoot(apn)
                        showImportResult(result)
                    }
                    2 -> {
                        val result = ApnImporter.copyToClipboard(this, apn)
                        showImportResult(result)
                    }
                    3 -> {
                        val result = ApnImporter.openApnSettings(this)
                        showImportResult(result)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showImportResult(result: ApnImporter.ImportResult) {
        val title = if (result.success) "导入成功" else "导入失败"
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(result.message)
            .setPositiveButton("确定", null)
            .show()
    }

    /**
     * 检查权限
     */
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_PHONE_STATE)
            }
        }
        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // 权限结果返回后重新加载
            viewModel.loadData()
        }
    }
}
