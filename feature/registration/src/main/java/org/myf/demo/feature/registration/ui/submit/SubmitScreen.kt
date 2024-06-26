package org.myf.demo.feature.registration.ui.submit


import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.Navigation
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.myf.demo.feature.registration.R
import org.myf.demo.feature.registration.databinding.ScreenSubmitBinding


@AndroidEntryPoint
class SubmitScreen : Fragment() {

    private var _binding: ScreenSubmitBinding? = null
    private val binding get() = _binding!!
    private val viewModel by viewModels<SubmitViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ScreenSubmitBinding.inflate(layoutInflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner
        attachMenu()
        binding.editNameIv.setOnClickListener {
            Navigation.findNavController(view).navigate(R.id.action_navigate_from_submit_to_create)
        }
        binding.editReportIv.setOnClickListener {
            Navigation.findNavController(view).navigate(R.id.action_navigate_from_submit_to_reports)
        }
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED){
                launch {
                    viewModel.patient
                        .map { it.img }
                        .distinctUntilChanged()
                        .collect{
                        if (it != Uri.EMPTY) {
                            /*val inputStream =
                                requireActivity().contentResolver.openInputStream(Uri.parse(it))
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            binding.submitPatientIdIv.scaleType = ImageView.ScaleType.CENTER_CROP
                            binding.submitPatientIdIv.setImageBitmap(bitmap)*/
                        }
                    }
                }
            }
        }
    }

    private fun attachMenu(){
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_submit,menu)
                val item = menu.findItem(R.id.action_submit_application)
                val menuView = item.setActionView(R.layout.submit_menu_action_view)
                val review: MaterialButton = menuView.actionView as MaterialButton
                review.setOnClickListener {
                    lifecycleScope.launch {
                        if (viewModel.createPatent()){
                            val nav = Navigation.findNavController(requireView())
                            nav.navigate(R.id.action_navigate_from_submit_to_profile)
                        }
                    }
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean = false
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}