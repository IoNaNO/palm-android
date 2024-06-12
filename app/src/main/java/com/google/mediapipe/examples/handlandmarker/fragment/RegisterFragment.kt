package com.google.mediapipe.examples.handlandmarker.fragment

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import com.google.mediapipe.examples.handlandmarker.HandLandmarkerHelper
import com.google.mediapipe.examples.handlandmarker.MainViewModel
import com.google.mediapipe.examples.handlandmarker.NetworkManager
import com.google.mediapipe.examples.handlandmarker.R
import com.google.mediapipe.examples.handlandmarker.databinding.FragmentRegisterBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import okhttp3.Call
import okhttp3.Callback
import okio.IOException
import java.util.concurrent.ScheduledExecutorService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class RegisterFragment:Fragment(),HandLandmarkerHelper.LandmarkerListener {
    private var _fragmentRegisterBinding: FragmentRegisterBinding? = null
    private val fragmentRegisterBinding get() = _fragmentRegisterBinding!!
    private var photoFile:File?=null


    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ScheduledExecutorService
    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()
    companion object{
        const val TAG="RegisterFragment"
        const val NEED_IMAGE_NUM=2
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoFile?.let {
                handleRegistrationPicture(Uri.fromFile(it))
            }
        } else {
            Toast.makeText(requireContext(), "Failed to take picture", Toast.LENGTH_SHORT).show()
        }
    }

    // variable for registering the user
    data class PalmPrintRegistrationData(
        val leftHandPrints: MutableList<Bitmap> = mutableListOf(),
        val rightHandPrints: MutableList<Bitmap> = mutableListOf(),
        var userName:String="",
    ){
        fun addHandPrint(handedness:String,bitmap:Bitmap){
            if(handedness.equals("Right",ignoreCase=true)){
                if (leftHandPrints.size>= NEED_IMAGE_NUM){
                    leftHandPrints.removeAt(0)
                }
                leftHandPrints.add(bitmap)
            }else{
                if (rightHandPrints.size>= NEED_IMAGE_NUM){
                    rightHandPrints.removeAt(0)
                }
                rightHandPrints.add(bitmap)
            }
        }

        fun readyAll():Boolean{
            return leftHandPrints.size== NEED_IMAGE_NUM && rightHandPrints.size== NEED_IMAGE_NUM&&userName.isNotEmpty()
        }

        fun readyPrints():Boolean{
            return leftHandPrints.size== NEED_IMAGE_NUM && rightHandPrints.size== NEED_IMAGE_NUM
        }
    }

    private val palmPrintRegistrationData=PalmPrintRegistrationData()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentRegisterBinding= FragmentRegisterBinding.inflate(inflater, container, false)
        return fragmentRegisterBinding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _fragmentRegisterBinding=null
    }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_camera_to_permissions)
        }
        // Start the HandLandmarkerHelper again when users come back
        // to the foreground.
        backgroundExecutor.execute {
            if (handLandmarkerHelper.isClose()) {
                handLandmarkerHelper.setupHandLandmarker()
            }
        }
    }

    override fun onError(error:String,errorCode:Int){
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        // no-op
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?){
        super.onViewCreated(view, savedInstanceState)
        backgroundExecutor= Executors.newSingleThreadScheduledExecutor()
        // init view action
        fragmentRegisterBinding.fabGetContent.setOnClickListener{
            photoFile = createImageFile()
            photoFile?.let{
                val photoURI=FileProvider.getUriForFile(
                    requireContext(),
                    "com.google.mediapipe.examples.handlandmarker.fileprovider",
                    it
                )
                takePicture.launch(photoURI)
            }
        }

        fragmentRegisterBinding.btnRegister.setOnClickListener{
            if(palmPrintRegistrationData.readyAll()){
                // do nothing for test
                registerUser()
            }else{
                if(palmPrintRegistrationData.readyPrints()){
                    Toast.makeText(requireContext(), "Please input your username", Toast.LENGTH_SHORT).show()
                }else{
                    Toast.makeText(requireContext(), "Please take more pictures", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fragmentRegisterBinding.etUsername.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                palmPrintRegistrationData.userName=s.toString()
            }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // no-op
            }
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                // no-op
            }
        })
    }

    private fun updateValueUI(){
        fragmentRegisterBinding.tvLeftHand.text=String.format("LeftHand: %d",palmPrintRegistrationData.leftHandPrints.size)
        fragmentRegisterBinding.tvRightHand.text=String.format("RightHand: %d",palmPrintRegistrationData.rightHandPrints.size)
    }

    private fun createImageFile():File{
        // Create an image file name
        val timeStamp:String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        // Get the dictionary of current app
        val storageDir:File?=requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        // Create a file
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    private fun handleRegistrationPicture(uri:Uri){
        backgroundExecutor=Executors.newSingleThreadScheduledExecutor()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(
                requireActivity().contentResolver,
                uri
            )
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(
                requireActivity().contentResolver,
                uri
            )
        }
            .copy(Bitmap.Config.ARGB_8888, true)
            ?.let { bitmap ->
                Log.d(TAG, "Bitmap loaded successfully")
                backgroundExecutor.execute {
                    Log.d(TAG, "Background task started")
                    handLandmarkerHelper = HandLandmarkerHelper(
                        context = requireContext(),
                        runningMode = RunningMode.IMAGE,
                        minHandDetectionConfidence = viewModel.currentMinHandDetectionConfidence,
                        minHandTrackingConfidence = viewModel.currentMinHandTrackingConfidence,
                        minHandPresenceConfidence = viewModel.currentMinHandPresenceConfidence,
                        maxNumHands = viewModel.currentMaxHands,
                        currentDelegate = viewModel.currentDelegate
                    )

                    handLandmarkerHelper.detectImage(bitmap)?.let { res ->
                        Log.d(TAG, "HandLandmarkerHelper.detectImage executed")
                        activity?.runOnUiThread {
                            if (res.results.isEmpty() || res.results[0].handednesses().isEmpty()) {
                                Toast.makeText(
                                    requireContext(),
                                    "No hands detected in the image.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val palmROI =
                                    handLandmarkerHelper.getROIImage(bitmap, res.results[0])
                                val handedness = res.results[0].handednesses()[0][0].categoryName()
                                Log.d(TAG, "Handedness: $handedness")
                                if (palmROI != null) {
                                    palmPrintRegistrationData.addHandPrint(handedness, palmROI)
                                    updateValueUI()
                                }
                            }
                        }
                    } ?: run {
                        Log.d(TAG, "HandLandmarkerHelper.detectImage failed")
                        activity?.runOnUiThread {
                            Toast.makeText(
                                requireContext(),
                                "HandLandmarkerHelper.detectImage failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    handLandmarkerHelper.clearHandLandmarker()
                }
            }
    }

    private fun registerUser(){
        val networkManager= NetworkManager.getInstance(requireContext())
        networkManager.postImages(
            "/register_native",
            palmPrintRegistrationData,
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Failed to register user", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    activity?.runOnUiThread {
                        if(response.isSuccessful){
                            Toast.makeText(requireContext(),"Register success",Toast.LENGTH_SHORT).show()
                        }else{
                            Toast.makeText(requireContext(),"Register failed: ${response.code}",Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }
}