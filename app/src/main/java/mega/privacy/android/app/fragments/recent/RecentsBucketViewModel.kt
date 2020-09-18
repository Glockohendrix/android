package mega.privacy.android.app.fragments.recent

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.*
import androidx.lifecycle.Observer

import kotlinx.coroutines.launch
import mega.privacy.android.app.fragments.homepage.nodesChange
import mega.privacy.android.app.utils.Constants.INVALID_POSITION
import nz.mega.sdk.MegaApiAndroid
import nz.mega.sdk.MegaNode
import nz.mega.sdk.MegaRecentActionBucket

class RecentsBucketViewModel @ViewModelInject constructor(
    private val megaApi: MegaApiAndroid,
    private val recentsBucketRepository: RecentsBucketRepository
) : ViewModel() {

    var bucket: MutableLiveData<MegaRecentActionBucket> = MutableLiveData()

    var shouldCloseFragment: MutableLiveData<Boolean> = MutableLiveData(false)

    var items: LiveData<List<MegaNode>> = bucket.switchMap {
        viewModelScope.launch {
            recentsBucketRepository.getNodes(it)
        }

        recentsBucketRepository.nodes
    }

    fun getItemPositionByHandle(handle: Long): Int {
        var index = INVALID_POSITION
        items.value?.forEachIndexed { i, megaNode ->
            if (megaNode.handle == handle) {
                index = i
                return@forEachIndexed
            }
        }
        return index
    }

    private fun isSameBucket(other: MegaRecentActionBucket, ignoreTimestamp: Boolean): Boolean {
        val selected = bucket.value
        return selected?.isMedia == other.isMedia &&
                selected.isUpdate == other.isUpdate &&
                (selected.timestamp == other.timestamp || ignoreTimestamp) &&
                selected.parentHandle == other.parentHandle &&
                selected.userEmail == other.userEmail
    }

    private val nodesChangeObserver = Observer<Boolean> {
        if (it) {
            if (bucket.value == null) {
                return@Observer
            }

            val recentActions = megaApi.recentActions
            recentActions.forEach { b ->
                if (isSameBucket(b, false)) {
                    bucket.value = b
                    return@Observer
                }
            }

            // If enter this loop, that means current open bucket is in today.
            recentActions.forEach { b ->
                if (isSameBucket(b, true)) {
                    bucket.value = b
                    return@Observer
                }
            }
            shouldCloseFragment.value = true
        }
    }

    init {
        nodesChange.observeForever(nodesChangeObserver)
    }

    override fun onCleared() {
        nodesChange.removeObserver(nodesChangeObserver)
    }
}

