package org.myf.ahc.ui.registration.reports

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.myf.ahc.util.FileTypesUtil
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repo: SubmitReportsRepo
): ViewModel(){

    val uiState: StateFlow<ReportsUiState>  = repo.uiState


    fun getReportsList() = repo.getReportsList()

    fun uploadFile(
        data: ByteArray,
        name: String
    ) = repo.uploadFile(
        data = data,
        name = name
    )

    fun onAttemptToDeleteFile(
        path: String
    ) = repo.attemptToDeleteFile(
        path = path
    )

    suspend fun isFileExist(
        name: String
    ): Boolean = withContext(Dispatchers.Default){
        uiState.value.list.forEach {
            val file = it.name+FileTypesUtil.getFileTypeExtension(it.type)
            if (name == file){
                return@withContext true
            }
        }
        false
    }

    fun clearDeleteFile() = repo.clearDeleteFile()

    fun deleteFile() = repo.deleteFile()

    fun openFiles() = repo.openFiles()

    fun openImage() = repo.openImage()

    fun clearOpenFiles() = repo.clearOpenFiles()

    override fun onCleared() {
        super.onCleared()
        repo.cancelJob()
    }
}