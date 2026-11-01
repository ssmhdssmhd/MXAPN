package com.mx.apn.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mx.apn.data.Apn
import com.mx.apn.data.ApnRepository
import com.mx.apn.data.Carrier
import com.mx.apn.util.CarrierDetector
import kotlinx.coroutines.launch

/**
 * 主界面 ViewModel
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ApnRepository(app.applicationContext)

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _carriers = MutableLiveData<List<Carrier>>()
    val carriers: LiveData<List<Carrier>> = _carriers

    private val _selectedCarrier = MutableLiveData<Carrier?>()
    val selectedCarrier: LiveData<Carrier?> = _selectedCarrier

    private val _detectedCarrier = MutableLiveData<Carrier?>()
    val detectedCarrier: LiveData<Carrier?> = _detectedCarrier

    private val _recommendedApn = MutableLiveData<Apn?>()
    val recommendedApn: LiveData<Apn?> = _recommendedApn

    private val _dataVersion = MutableLiveData<String>()
    val dataVersion: LiveData<String> = _dataVersion

    private val _isRemoteData = MutableLiveData<Boolean>(false)
    val isRemoteData: LiveData<Boolean> = _isRemoteData

    private val _simInfo = MutableLiveData<CarrierDetector.SimInfo>()
    val simInfo: LiveData<CarrierDetector.SimInfo> = _simInfo

    /**
     * 加载 APN 数据
     */
    fun loadData() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            // 先检测 SIM 信息
            _simInfo.value = CarrierDetector.getSimInfo(getApplication())

            // 尝试获取远程数据
            val remoteResult = repository.fetchRemote()
            val isRemote = remoteResult.isSuccess
            val result = if (isRemote) remoteResult else repository.loadLocal()

            result.onSuccess { response ->
                _carriers.value = response.carriers
                _dataVersion.value = response.version
                _isRemoteData.value = isRemote

                // 自动检测当前运营商
                val detected = repository.detectCarrier(response.carriers)
                _detectedCarrier.value = detected

                // 默认选中检测到的运营商
                _selectedCarrier.value = detected ?: response.carriers.firstOrNull()

                // 获取推荐 APN
                _recommendedApn.value = repository.getRecommendedApn(detected)
            }.onFailure { e ->
                _error.value = "加载 APN 数据失败: ${e.message}"
            }

            _loading.value = false
        }
    }

    /**
     * 选择运营商
     */
    fun selectCarrier(carrier: Carrier) {
        _selectedCarrier.value = carrier
        _recommendedApn.value = repository.getRecommendedApn(carrier)
    }

    /**
     * 刷新数据（强制从网络获取）
     */
    fun refresh() {
        loadData()
    }
}
