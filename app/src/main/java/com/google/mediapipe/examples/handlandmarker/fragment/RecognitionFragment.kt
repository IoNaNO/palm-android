package com.google.mediapipe.examples.handlandmarker.fragment

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import com.google.mediapipe.examples.handlandmarker.HandLandmarkerHelper
import com.google.mediapipe.examples.handlandmarker.MainViewModel
import com.google.mediapipe.examples.handlandmarker.NetworkManager
import com.google.mediapipe.examples.handlandmarker.R
import com.google.mediapipe.examples.handlandmarker.databinding.FragmentRecognitionBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import okhttp3.Call
import okhttp3.Callback
import okio.IOException
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class RecognitionFragment:Fragment(),HandLandmarkerHelper.LandmarkerListener {
    private var _fragmentRecognitionBinding:FragmentRecognitionBinding?=null
    private val fragmentRecognitionBinding get()=_fragmentRecognitionBinding!!
    private var photoFile: File?=null

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ScheduledExecutorService
    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()
    companion object{
        const val TAG="RecognitionFragment"
    }
    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoFile?.let {
                processAndUploadImage(Uri.fromFile(it))
            }
        } else {
            Toast.makeText(requireContext(), "Failed to take picture", Toast.LENGTH_SHORT).show()
        }
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

    private fun processAndUploadImage(uri: Uri) {
        Log.d(TAG, "processAndUploadImage called with uri: $uri")
        setUiEnabled(false)
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()

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
            ?.let {bitmap->
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

                handLandmarkerHelper.detectImage(bitmap)?.let{res->
                    Log.d(TAG, "HandLandmarkerHelper.detectImage executed")
                    activity?.runOnUiThread {
                        if (res.results.isEmpty() || res.results[0].handednesses().isEmpty()) {
                            Toast.makeText(requireContext(), "No hands detected in the image.", Toast.LENGTH_SHORT).show()
                        } else {
                            val palmROI = handLandmarkerHelper.getROIImage(bitmap, res.results[0])
                            fragmentRecognitionBinding.tvPlaceholder.visibility = View.INVISIBLE
                            fragmentRecognitionBinding.imageView.visibility = View.VISIBLE
                            fragmentRecognitionBinding.imageView.setImageBitmap(palmROI)
                            fragmentRecognitionBinding.tvResponse.visibility = View.VISIBLE
                            // send ROI image to server and get the result of matching
                            val networkManager = NetworkManager.getInstance(requireContext())
                            if (palmROI != null) {
                                networkManager.postImage("/recognize_native", palmROI,object :Callback{
                                    override fun onFailure(call:Call, e:IOException){
                                        Log.d(TAG, "NetworkManager.postImage failed")
                                        activity?.runOnUiThread {
                                            Toast.makeText(
                                                requireContext(),
                                                "NetworkManager.postImage failed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    override fun onResponse(call:Call, response:okhttp3.Response){
                                        val responseString = response.body?.string()
                                        Log.d(TAG, "NetworkManager.postImage onResponse: $responseString")

                                        val result: String = try {
                                            val jsonResponse = JSONObject(responseString)
                                            jsonResponse.getString("result")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to parse JSON response", e)
                                            "Error parsing response"
                                        }

                                        activity?.runOnUiThread {
                                            fragmentRecognitionBinding.tvResponse.text =
                                                "User Info: $result"
                                        }
                                    }

                                })
                            }

                        }
                        setUiEnabled(true)
                        fragmentRecognitionBinding.bottomSheetLayout.inferenceTimeVal.text=
                            String.format("%d ms",res.inferenceTime)
                    }
                }?:run {
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


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentRecognitionBinding = FragmentRecognitionBinding.inflate(inflater, container, false)
        return fragmentRecognitionBinding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        // init view here
        fragmentRecognitionBinding.fabGetContent.setOnClickListener{
            photoFile = createImageFile()
            photoFile?.let {
                val photoURI = FileProvider.getUriForFile(
                    requireContext(),
                    "com.google.mediapipe.examples.handlandmarker.fileprovider",
                    it
                )
                takePicture.launch(photoURI)
            }
        }
        initBottomSheetControls()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _fragmentRecognitionBinding = null
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        // no-op
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

    private fun initBottomSheetControls() {
        // init bottom sheet settings
        fragmentRecognitionBinding.bottomSheetLayout.maxHandsValue.text =
            viewModel.currentMaxHands.toString()
        fragmentRecognitionBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinHandDetectionConfidence
            )
        fragmentRecognitionBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinHandTrackingConfidence
            )
        fragmentRecognitionBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinHandPresenceConfidence
            )

        // When clicked, lower detection score threshold floor
        fragmentRecognitionBinding.bottomSheetLayout.detectionThresholdMinus.setOnClickListener {
            if (viewModel.currentMinHandDetectionConfidence >= 0.2) {
                viewModel.setMinHandDetectionConfidence(viewModel.currentMinHandDetectionConfidence - 0.1f)
                updateControlsUi()
            }
        }

        // When clicked, raise detection score threshold floor
        fragmentRecognitionBinding.bottomSheetLayout.detectionThresholdPlus.setOnClickListener {
            if (viewModel.currentMinHandDetectionConfidence <= 0.8) {
                viewModel.setMinHandDetectionConfidence(viewModel.currentMinHandDetectionConfidence + 0.1f)
                updateControlsUi()
            }
        }

        // When clicked, lower hand tracking score threshold floor
        fragmentRecognitionBinding.bottomSheetLayout.trackingThresholdMinus.setOnClickListener {
            if (viewModel.currentMinHandTrackingConfidence >= 0.2) {
                viewModel.setMinHandTrackingConfidence(
                    viewModel.currentMinHandTrackingConfidence - 0.1f
                )
                updateControlsUi()
            }
        }

        // When clicked, raise hand tracking score threshold floor
        fragmentRecognitionBinding.bottomSheetLayout.trackingThresholdPlus.setOnClickListener {
            if (viewModel.currentMinHandTrackingConfidence <= 0.8) {
                viewModel.setMinHandTrackingConfidence(
                    viewModel.currentMinHandTrackingConfidence + 0.1f
                )
                updateControlsUi()
            }
        }

        // When clicked, lower hand presence score threshold floor
        fragmentRecognitionBinding.bottomSheetLayout.presenceThresholdMinus.setOnClickListener {
            if (viewModel.currentMinHandPresenceConfidence >= 0.2) {
                viewModel.setMinHandPresenceConfidence(
                    viewModel.currentMinHandPresenceConfidence - 0.1f
                )
                updateControlsUi()
            }
        }

        // When clicked, raise hand presence score threshold floor
        fragmentRecognitionBinding.bottomSheetLayout.presenceThresholdPlus.setOnClickListener {
            if (viewModel.currentMinHandPresenceConfidence <= 0.8) {
                viewModel.setMinHandPresenceConfidence(
                    viewModel.currentMinHandPresenceConfidence + 0.1f
                )
                updateControlsUi()
            }
        }

        // When clicked, reduce the number of objects that can be detected at a time
        fragmentRecognitionBinding.bottomSheetLayout.maxHandsMinus.setOnClickListener {
            if (viewModel.currentMaxHands > 1) {
                viewModel.setMaxHands(viewModel.currentMaxHands - 1)
                updateControlsUi()
            }
        }

        // When clicked, increase the number of objects that can be detected at a time
        fragmentRecognitionBinding.bottomSheetLayout.maxHandsPlus.setOnClickListener {
            if (viewModel.currentMaxHands < 2) {
                viewModel.setMaxHands(viewModel.currentMaxHands + 1)
                updateControlsUi()
            }
        }

        // When clicked, change the underlying hardware used for inference. Current options are CPU
        // GPU, and NNAPI
        fragmentRecognitionBinding.bottomSheetLayout.spinnerDelegate.setSelection(
            viewModel.currentDelegate,
            false
        )
        fragmentRecognitionBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?,
                    p1: View?,
                    p2: Int,
                    p3: Long
                ) {

                    viewModel.setDelegate(p2)
                    updateControlsUi()
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    // Update the values displayed in the bottom sheet. Reset detector.
    private fun updateControlsUi() {

        fragmentRecognitionBinding.bottomSheetLayout.maxHandsValue.text =
            viewModel.currentMaxHands.toString()
        fragmentRecognitionBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinHandDetectionConfidence
            )
        fragmentRecognitionBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinHandTrackingConfidence
            )
        fragmentRecognitionBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinHandPresenceConfidence
            )

    }

    private fun setUiEnabled(enabled: Boolean) {
        fragmentRecognitionBinding.fabGetContent.isEnabled = enabled
        fragmentRecognitionBinding.bottomSheetLayout.detectionThresholdMinus.isEnabled =
            enabled
        fragmentRecognitionBinding.bottomSheetLayout.detectionThresholdPlus.isEnabled =
            enabled
        fragmentRecognitionBinding.bottomSheetLayout.trackingThresholdMinus.isEnabled =
            enabled
        fragmentRecognitionBinding.bottomSheetLayout.trackingThresholdPlus.isEnabled =
            enabled
        fragmentRecognitionBinding.bottomSheetLayout.presenceThresholdMinus.isEnabled =
            enabled
        fragmentRecognitionBinding.bottomSheetLayout.presenceThresholdPlus.isEnabled =
            enabled
        fragmentRecognitionBinding.bottomSheetLayout.maxHandsPlus.isEnabled =
            enabled
        fragmentRecognitionBinding.bottomSheetLayout.maxHandsMinus.isEnabled =
            enabled
        fragmentRecognitionBinding.bottomSheetLayout.spinnerDelegate.isEnabled =
            enabled
    }
}