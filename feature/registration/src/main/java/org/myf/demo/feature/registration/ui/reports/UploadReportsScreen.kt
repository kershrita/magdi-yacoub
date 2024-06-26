package org.myf.demo.feature.registration.ui.reports

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.findFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.myf.demo.core.common.uiState.ReportsUiState
import org.myf.demo.core.common.util.FileTypesUtil
import org.myf.demo.core.common.util.FilesSizeUtil.REPORTS_SIZE
import org.myf.demo.feature.registration.R
import org.myf.demo.feature.registration.adapters.DocumentsAdapter
import org.myf.demo.feature.registration.databinding.ScreenUploadReportsBinding
import org.myf.demo.feature.registration.util.ActivityLauncherObserver
import org.myf.demo.ui.theme.AppTheme
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import org.myf.demo.ui.R as uiResource


@AndroidEntryPoint
class UploadReportsScreen : Fragment(), ReportLauncherListener {

    private var _binding: ScreenUploadReportsBinding? = null
    private val binding get() = _binding!!
    private lateinit var observer: ActivityLauncherObserver
    private val viewModel by viewModels<ReportsViewModel>()
    private val coroutine = CoroutineScope(Dispatchers.Default) //TODO refactor!, remove it..
    private var size = 0L
    private val adapter = DocumentsAdapter()
    //private val deleteDialog = DeleteReportDialog()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observer = ActivityLauncherObserver(requireActivity().activityResultRegistry,this)
        lifecycle.addObserver(observer)
        viewModel.getReportsList()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ScreenUploadReportsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner
        attachMenu()
        BottomSheetBehavior.from(
            view.findViewById(R.id.pick_up_chooser_bottom_sheet)
        ).apply {
            state = BottomSheetBehavior.STATE_HIDDEN
        }
        binding.reportsRv.apply {
            adapter = this@UploadReportsScreen.adapter
            layoutManager = LinearLayoutManager(context)
        }
        binding.uploadButton.setOnClickListener {
            val chooserDialog = PickupChooserDialog()
            this@UploadReportsScreen.view?.findFragment<UploadReportsScreen>()?.let {
                chooserDialog.show(it.childFragmentManager, PickupChooserDialog.TAG)
            }
        }
        binding.reportsCompose?.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    ReportsDeleteDialog(
                        viewModel = viewModel
                    )
                }
            }
        }
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch {
                    viewModel.uiState
                        .map { it }
                        .distinctUntilChanged()
                        .collect { updateUi(it) }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.removeOpenFilesObserver()
    }

    private fun attachMenu(){
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_upload_repoerts,menu)
                val item = menu.findItem(R.id.action_review_application)
                val menuView = item.setActionView(R.layout.reports_menu_action_view)
                val review: MaterialButton = menuView.actionView as MaterialButton
                review.setOnClickListener {
                    viewModel.saveFilesCount()
                    Navigation.findNavController(requireView())
                        .navigate(R.id.action_navigate_from_upload_to_submit)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean = false
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private suspend fun updateUi(
        ui: ReportsUiState
    ) = withContext(Dispatchers.Main){
        if (ui.pickFile) {
            observer.pickFile()
        }
        if (ui.pickImage) {
            observer.pickImage()
        }
        size = ui.size
        adapter.setDocuments(ui.list.sortedBy { it.name })
       /* if (ui.deleteFile != null && !deleteDialog.isAdded) {
            this@UploadReportsScreen.view?.findFragment<UploadReportsScreen>()?.let {
                deleteDialog.show(it.childFragmentManager, DeleteReportDialog.TAG)
            }
        }
        if (ui.deleteFile == null && deleteDialog.isAdded) {
            deleteDialog.dismiss()
        }*/
    }

    @Throws(FileNotFoundException::class)
    fun openDocument(uri: Uri) = coroutine.launch {
        var name = ""
        val type: String? = requireActivity().contentResolver.getType(uri)
        requireActivity().contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex =
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        val inputStream = requireActivity().contentResolver.openInputStream(uri) ?: return@launch
        withContext(Dispatchers.IO) {
            val fileSize = inputStream.available()
            Log.d(
                "File_$type",
                "name: $name\n" +
                        "Size: $fileSize Bytes"
            )
            if (type == FileTypesUtil.PDF) {
                if (viewModel.isFileExist(name)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            getString(uiResource.string.file_exist_message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    if (fileSize < REPORTS_SIZE && (fileSize + size) < REPORTS_SIZE) {
                        inputStream.readBytes().let {
                            viewModel.uploadFile(
                                data = it,
                                name = name
                            )
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                getString(uiResource.string.file_size_message),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            if (type == FileTypesUtil.MICROSOFT_WORD) {
                if (viewModel.isFileExist(name)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            getString(uiResource.string.file_exist_message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    if (fileSize < REPORTS_SIZE && (fileSize + size) < REPORTS_SIZE) {
                        inputStream.readBytes().let {
                            viewModel.uploadFile(
                                data = it,
                                name = name
                            )
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                getString(uiResource.string.file_size_message),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            inputStream.close()
        }
    }

    @Throws(FileNotFoundException::class)
    fun openImage(uri: Uri) = coroutine.launch {
        var name = ""
        val type = requireActivity().contentResolver.getType(uri)
        requireActivity().contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(
                OpenableColumns.DISPLAY_NAME
            )
            if (cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }

        }
        val inputStream = requireActivity().contentResolver.openInputStream(uri) ?: return@launch
        withContext(Dispatchers.IO) {
            val fileSize = inputStream.available()
            Log.d(
                "IMAGE_$name",
                "$type , size: $fileSize Bytes"
            )
            if (type == FileTypesUtil.JPG || type == FileTypesUtil.PNG) {
                if (viewModel.isFileExist(name)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            getString(uiResource.string.file_exist_message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    if (fileSize < REPORTS_SIZE && (fileSize + size) < REPORTS_SIZE) {
                        if (fileSize > 3_000_000) {
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            val byteStream = ByteArrayOutputStream()
                            bitmap.compress(
                                if (type == FileTypesUtil.JPG) Bitmap.CompressFormat.JPEG
                                else Bitmap.CompressFormat.PNG, 60, byteStream
                            )
                            viewModel.uploadFile(
                                data = byteStream.toByteArray(),
                                name = name
                            )
                        } else {
                            inputStream.readBytes().let {
                                viewModel.uploadFile(
                                    data = it,
                                    name = name
                                )
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                getString(uiResource.string.file_size_message),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            inputStream.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutine.cancel()
        _binding = null
    }

    override fun onImagePicked(uri: Uri) {
        openImage(uri = uri)
    }

    override fun onFilePicked(uri: Uri) {
        openDocument(uri = uri)
    }
}