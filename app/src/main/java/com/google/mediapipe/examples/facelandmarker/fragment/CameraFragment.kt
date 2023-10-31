/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.facelandmarker.fragment

import android.annotation.SuppressLint
import android.content.res.AssetFileDescriptor
import android.content.res.Configuration
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Camera
import androidx.camera.core.AspectRatio
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import com.google.mediapipe.examples.facelandmarker.*
import com.google.mediapipe.examples.facelandmarker.databinding.FragmentCameraBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment(), FaceLandmarkerHelper.LandmarkerListener ,
    TextToSpeech.OnInitListener {



    companion object {
        private const val TAG = "Face Landmarker"
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private lateinit var interpreter: Interpreter
    private lateinit var textToSpeech: TextToSpeech

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT


    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_camera_to_permissions)
        }

        // Start the FaceLandmarkerHelper again when users come back
        // to the foreground.
        backgroundExecutor.execute {
            if (faceLandmarkerHelper.isClose()) {
                faceLandmarkerHelper.setupFaceLandmarker()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if(this::faceLandmarkerHelper.isInitialized) {
            viewModel.setMaxFaces(faceLandmarkerHelper.maxNumFaces)
            viewModel.setMinFaceDetectionConfidence(faceLandmarkerHelper.minFaceDetectionConfidence)
            viewModel.setMinFaceTrackingConfidence(faceLandmarkerHelper.minFaceTrackingConfidence)
            viewModel.setMinFacePresenceConfidence(faceLandmarkerHelper.minFacePresenceConfidence)
            viewModel.setDelegate(faceLandmarkerHelper.currentDelegate)

            // Close the FaceLandmarkerHelper and release resources
            backgroundExecutor.execute { faceLandmarkerHelper.clearFaceLandmarker() }
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
        textToSpeech.shutdown()
        // Shut down our background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()

        }


        // Create the FaceLandmarkerHelper that will handle the inference
        backgroundExecutor.execute {
            faceLandmarkerHelper = FaceLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                minFaceDetectionConfidence = viewModel.currentMinFaceDetectionConfidence,
                minFaceTrackingConfidence = viewModel.currentMinFaceTrackingConfidence,
                minFacePresenceConfidence = viewModel.currentMinFacePresenceConfidence,
                maxNumFaces = viewModel.currentMaxFaces,
                currentDelegate = viewModel.currentDelegate,
                faceLandmarkerHelperListener = this
            )
        }


        // Attach listeners to UI control widgets
        initBottomSheetControls()

        textToSpeech = TextToSpeech(requireContext(), this)
    }


    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // TTS 초기화 성공
            val result = textToSpeech.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // 언어가 지원되지 않을 경우 처리
            } else {
                // TTS 사용 가능
            }
        } else {
            // TTS 초기화 실패
        }
    }


    // TTS로 텍스트 음성 출력하기
    private fun speakText(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // TTS 멈추기
    private fun stopSpeaking() {
        textToSpeech.stop()
    }


    private fun initBottomSheetControls() {
        // init bottom sheet settings
        fragmentCameraBinding.bottomSheetLayout.maxFacesValue.text =
            viewModel.currentMaxFaces.toString()
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinFaceDetectionConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinFaceTrackingConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinFacePresenceConfidence
            )

        // When clicked, lower face detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdMinus.setOnClickListener {
            if (faceLandmarkerHelper.minFaceDetectionConfidence >= 0.2) {
                faceLandmarkerHelper.minFaceDetectionConfidence -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise face detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdPlus.setOnClickListener {
            if (faceLandmarkerHelper.minFaceDetectionConfidence <= 0.8) {
                faceLandmarkerHelper.minFaceDetectionConfidence += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, lower face tracking score threshold floor
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdMinus.setOnClickListener {
            if (faceLandmarkerHelper.minFaceTrackingConfidence >= 0.2) {
                faceLandmarkerHelper.minFaceTrackingConfidence -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise face tracking score threshold floor
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdPlus.setOnClickListener {
            if (faceLandmarkerHelper.minFaceTrackingConfidence <= 0.8) {
                faceLandmarkerHelper.minFaceTrackingConfidence += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, lower face presence score threshold floor
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdMinus.setOnClickListener {
            if (faceLandmarkerHelper.minFacePresenceConfidence >= 0.2) {
                faceLandmarkerHelper.minFacePresenceConfidence -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise face presence score threshold floor
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdPlus.setOnClickListener {
            if (faceLandmarkerHelper.minFacePresenceConfidence <= 0.8) {
                faceLandmarkerHelper.minFacePresenceConfidence += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, reduce the number of faces that can be detected at a
        // time
        fragmentCameraBinding.bottomSheetLayout.maxFacesMinus.setOnClickListener {
            if (faceLandmarkerHelper.maxNumFaces > 1) {
                faceLandmarkerHelper.maxNumFaces--
                updateControlsUi()
            }
        }

        // When clicked, increase the number of faces that can be detected
        // at a time
        fragmentCameraBinding.bottomSheetLayout.maxFacesPlus.setOnClickListener {
            if (faceLandmarkerHelper.maxNumFaces < 2) {
                faceLandmarkerHelper.maxNumFaces++
                updateControlsUi()
            }
        }

        // When clicked, change the underlying hardware used for inference.
        // Current options are CPU and GPU
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
            viewModel.currentDelegate, false
        )
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long
                ) {
                    try {
                        faceLandmarkerHelper.currentDelegate = p2
                        updateControlsUi()
                    } catch(e: UninitializedPropertyAccessException) {
                        Log.e(TAG, "FaceLandmarkerHelper has not been initialized yet.")
                    }
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    // Update the values displayed in the bottom sheet. Reset Facelandmarker
    // helper.
    private fun updateControlsUi() {
        fragmentCameraBinding.bottomSheetLayout.maxFacesValue.text =
            faceLandmarkerHelper.maxNumFaces.toString()
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                faceLandmarkerHelper.minFaceDetectionConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                faceLandmarkerHelper.minFaceTrackingConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                faceLandmarkerHelper.minFacePresenceConfidence
            )

        // Needs to be cleared instead of reinitialized because the GPU
        // delegate needs to be initialized on the thread using it when applicable
        backgroundExecutor.execute {
            faceLandmarkerHelper.clearFaceLandmarker()
            faceLandmarkerHelper.setupFaceLandmarker()
        }
        fragmentCameraBinding.overlay.clear()
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(backgroundExecutor) { image ->
                        detectFace(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectFace(imageProxy: ImageProxy) {
        faceLandmarkerHelper.detectLiveStream(
            imageProxy = imageProxy,
            isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    // Update UI after face have been detected. Extracts original
    // image height/width to scale and place the landmarks properly through
    // OverlayView
    override fun onResults(
        resultBundle: FaceLandmarkerHelper.ResultBundle
    ) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                    String.format("%d ms", resultBundle.inferenceTime)

                // Pass necessary information to OverlayView for drawing on the canvas
                fragmentCameraBinding.overlay.setResults(
                    resultBundle.result,
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )
                // Force a redraw
                fragmentCameraBinding.overlay.invalidate()

                val faceLandmarks = resultBundle.result.faceLandmarks()


                val cameraContainer: View = fragmentCameraBinding.cameraContainer
                cameraContainer.setOnClickListener {
                    // camera_container 클릭 이벤트 처리 로직을 여기에 구현합니다.
                    // 원하는 동작을 수행하세요.
                    if (faceLandmarks.isNotEmpty()) {
                        // 랜드마크 좌표 !!
                        var bobo: Int = 1
                        val nullableArrayx1 = arrayOfNulls<Float>(478)
                        val nullableArrayy1 = arrayOfNulls<Float>(478)
                        resultBundle.result.let { faceLandmarkerResult ->
                            for (landmark in faceLandmarkerResult.faceLandmarks()) {
                                for (normalizedLandmark in landmark) {
                                    //Log.i("tt",bobo.toString() +"x : " + normalizedLandmark.x().toString() + " , " + bobo.toString()+"y : " + normalizedLandmark.y().toString())
                                    nullableArrayx1[bobo-1] = normalizedLandmark.x()
                                    nullableArrayy1[bobo-1] = normalizedLandmark.y()
                                    bobo +=1
                                }
                            }
                        }

                        val normalizedArrayx1 = FloatArray(nullableArrayx1.size) { i -> nullableArrayx1[i]?.toFloat() ?: 0.0f }
                        val normalizedArrayy1 = FloatArray(nullableArrayy1.size) { i -> nullableArrayy1[i]?.toFloat() ?: 0.0f }

                        val multipliedArrayX = normalizedArrayx1.map { it * resultBundle.inputImageWidth }.toFloatArray()
                        val multipliedArrayY = normalizedArrayy1.map { it * resultBundle.inputImageHeight }.toFloatArray()

                        val arrayx = normalizeArray(multipliedArrayX)
                        val arrayy = normalizeArray(multipliedArrayY)

                        /*
                        val normalizedArrayx1 = FloatArray(nullableArrayx1.size) { i -> nullableArrayx1[i]?.toFloat() ?: 0.0f }
                        val normalizedArrayy1 = FloatArray(nullableArrayy1.size) { i -> nullableArrayy1[i]?.toFloat() ?: 0.0f }

                        val multipliedArrayX = normalizedArrayx1.map { it * resultBundle.inputImageWidth }.toTypedArray()
                        val multipliedArrayY = normalizedArrayy1.map { it * resultBundle.inputImageHeight }.toTypedArray()

                        val normalizedArrayx2 = FloatArray(multipliedArrayX.size) { i -> multipliedArrayX[i]?.toFloat() ?: 0.0f }
                        val normalizedArrayy2 = FloatArray(multipliedArrayY.size) { i -> multipliedArrayY[i]?.toFloat() ?: 0.0f }

                        val arrayx = normalizeArray(normalizedArrayx2)
                        val arrayy = normalizeArray(normalizedArrayy2)
                        */

                        // 배열 X, y로 나누기

                        val nullableArrayx = arrayOfNulls<Float>(479)
                        val nullableArrayy = arrayOfNulls<Float>(479)
                        for (i in arrayx.indices) {
                            nullableArrayx[i+1] = arrayx[i]
                        }

                        for (i in arrayy.indices) {
                            nullableArrayy[i+1] = arrayy[i]
                        }

                        // 모델 가져오기
                        loadModel()

                        //입력 데이터
                        val inputArray: FloatArray = FloatArray(300)
                        inputArray[0] = nullableArrayy[379]!!
                        inputArray[1] = nullableArrayx[297]!!
                        inputArray[2] = nullableArrayx[377]!!
                        inputArray[3] = nullableArrayy[324]!!
                        inputArray[4] = nullableArrayy[365]!!
                        inputArray[5] = nullableArrayy[307]!!
                        inputArray[6] = nullableArrayy[53]!!
                        inputArray[7] = nullableArrayy[366]!!
                        inputArray[8] = nullableArrayx[293]!!
                        inputArray[9] = nullableArrayx[309]!!
                        inputArray[10] = nullableArrayy[455]!!
                        inputArray[11] = nullableArrayy[357]!!
                        inputArray[12] = nullableArrayx[412]!!
                        inputArray[13] = nullableArrayy[395]!!
                        inputArray[14] = nullableArrayy[10]!!
                        inputArray[15] = nullableArrayy[66]!!
                        inputArray[16] = nullableArrayy[284]!!
                        inputArray[17] = nullableArrayx[410]!!
                        inputArray[18] = nullableArrayy[296]!!
                        inputArray[19] = nullableArrayy[362]!!
                        inputArray[20] = nullableArrayy[337]!!
                        inputArray[21] = nullableArrayy[380]!!
                        inputArray[22] = nullableArrayy[294]!!
                        inputArray[23] = nullableArrayy[431]!!
                        inputArray[24] = nullableArrayy[301]!!
                        inputArray[25] = nullableArrayy[289]!!
                        inputArray[26] = nullableArrayy[396]!!
                        inputArray[27] = nullableArrayy[154]!!
                        inputArray[28] = nullableArrayy[401]!!
                        inputArray[29] = nullableArrayy[367]!!
                        inputArray[30] = nullableArrayx[292]!!
                        inputArray[31] = nullableArrayy[233]!!
                        inputArray[32] = nullableArrayx[416]!!
                        inputArray[33] = nullableArrayy[77]!!
                        inputArray[34] = nullableArrayy[402]!!
                        inputArray[35] = nullableArrayy[224]!!
                        inputArray[36] = nullableArrayy[292]!!
                        inputArray[37] = nullableArrayy[23]!!
                        inputArray[38] = nullableArrayy[108]!!
                        inputArray[39] = nullableArrayy[335]!!
                        inputArray[40] = nullableArrayx[62]!!
                        inputArray[41] = nullableArrayy[47]!!
                        inputArray[42] = nullableArrayy[25]!!
                        inputArray[43] = nullableArrayy[244]!!
                        inputArray[44] = nullableArrayy[293]!!
                        inputArray[45] = nullableArrayy[423]!!
                        inputArray[46] = nullableArrayy[286]!!
                        inputArray[47] = nullableArrayy[398]!!
                        inputArray[48] = nullableArrayy[390]!!
                        inputArray[49] = nullableArrayy[64]!!
                        inputArray[50] = nullableArrayy[15]!!
                        inputArray[51] = nullableArrayy[288]!!
                        inputArray[52] = nullableArrayy[444]!!
                        inputArray[53] = nullableArrayx[408]!!
                        inputArray[54] = nullableArrayy[417]!!
                        inputArray[55] = nullableArrayy[79]!!
                        inputArray[56] = nullableArrayy[121]!!
                        inputArray[57] = nullableArrayx[411]!!
                        inputArray[58] = nullableArrayy[277]!!
                        inputArray[59] = nullableArrayx[409]!!
                        inputArray[60] = nullableArrayy[434]!!
                        inputArray[61] = nullableArrayy[368]!!
                        inputArray[62] = nullableArrayy[54]!!
                        inputArray[63] = nullableArrayy[56]!!
                        inputArray[64] = nullableArrayy[27]!!
                        inputArray[65] = nullableArrayx[326]!!
                        inputArray[66] = nullableArrayy[87]!!
                        inputArray[67] = nullableArrayy[260]!!
                        inputArray[68] = nullableArrayy[245]!!
                        inputArray[69] = nullableArrayy[155]!!
                        inputArray[70] = nullableArrayx[186]!!
                        inputArray[71] = nullableArrayy[134]!!
                        inputArray[72] = nullableArrayy[283]!!
                        inputArray[73] = nullableArrayy[86]!!
                        inputArray[74] = nullableArrayy[318]!!
                        inputArray[75] = nullableArrayx[417]!!
                        inputArray[76] = nullableArrayx[79]!!
                        inputArray[77] = nullableArrayy[63]!!
                        inputArray[78] = nullableArrayx[184]!!
                        inputArray[79] = nullableArrayx[307]!!
                        inputArray[80] = nullableArrayy[436]!!
                        inputArray[81] = nullableArrayy[145]!!
                        inputArray[82] = nullableArrayx[58]!!
                        inputArray[83] = nullableArrayy[106]!!
                        inputArray[84] = nullableArrayy[169]!!
                        inputArray[85] = nullableArrayy[410]!!
                        inputArray[86] = nullableArrayy[448]!!
                        inputArray[87] = nullableArrayy[443]!!
                        inputArray[88] = nullableArrayy[151]!!
                        inputArray[89] = nullableArrayy[231]!!
                        inputArray[90] = nullableArrayy[192]!!
                        inputArray[91] = nullableArrayy[62]!!
                        inputArray[92] = nullableArrayy[30]!!
                        inputArray[93] = nullableArrayy[232]!!
                        inputArray[94] = nullableArrayy[57]!!
                        inputArray[95] = nullableArrayy[216]!!
                        inputArray[96] = nullableArrayy[120]!!
                        inputArray[97] = nullableArrayy[226]!!
                        inputArray[98] = nullableArrayy[189]!!
                        inputArray[99] = nullableArrayy[191]!!
                        inputArray[100] = nullableArrayy[129]!!
                        inputArray[101] = nullableArrayy[9]!!
                        inputArray[102] = nullableArrayy[19]!!
                        inputArray[103] = nullableArrayy[309]!!
                        inputArray[104] = nullableArrayx[187]!!
                        inputArray[105] = nullableArrayy[156]!!
                        inputArray[106] = nullableArrayx[308]!!
                        inputArray[107] = nullableArrayy[179]!!
                        inputArray[108] = nullableArrayy[246]!!
                        inputArray[109] = nullableArrayy[81]!!
                        inputArray[110] = nullableArrayy[186]!!
                        inputArray[111] = nullableArrayy[315]!!
                        inputArray[112] = nullableArrayy[225]!!
                        inputArray[113] = nullableArrayy[342]!!
                        inputArray[114] = nullableArrayy[84]!!
                        inputArray[115] = nullableArrayy[194]!!
                        inputArray[116] = nullableArrayx[434]!!
                        inputArray[117] = nullableArrayy[377]!!
                        inputArray[118] = nullableArrayy[326]!!
                        inputArray[119] = nullableArrayx[192]!!
                        inputArray[120] = nullableArrayx[147]!!
                        inputArray[121] = nullableArrayx[63]!!
                        inputArray[122] = nullableArrayy[442]!!
                        inputArray[123] = nullableArrayy[234]!!
                        inputArray[124] = nullableArrayy[174]!!
                        inputArray[125] = nullableArrayy[409]!!
                        inputArray[126] = nullableArrayy[405]!!
                        inputArray[127] = nullableArrayx[77]!!
                        inputArray[128] = nullableArrayy[369]!!
                        inputArray[129] = nullableArrayy[152]!!
                        inputArray[130] = nullableArrayy[215]!!
                        inputArray[131] = nullableArrayy[206]!!
                        inputArray[132] = nullableArrayx[166]!!
                        inputArray[133] = nullableArrayy[43]!!
                        inputArray[134] = nullableArrayy[173]!!
                        inputArray[135] = nullableArrayy[24]!!
                        inputArray[136] = nullableArrayy[28]!!
                        inputArray[137] = nullableArrayy[435]!!
                        inputArray[138] = nullableArrayy[317]!!
                        inputArray[139] = nullableArrayy[217]!!
                        inputArray[140] = nullableArrayy[18]!!
                        inputArray[141] = nullableArrayy[122]!!
                        inputArray[142] = nullableArrayy[67]!!
                        inputArray[143] = nullableArrayy[229]!!
                        inputArray[144] = nullableArrayx[97]!!
                        inputArray[145] = nullableArrayy[101]!!
                        inputArray[146] = nullableArrayy[51]!!
                        inputArray[147] = nullableArrayy[314]!!
                        inputArray[148] = nullableArrayy[137]!!
                        inputArray[149] = nullableArrayy[258]!!
                        inputArray[150] = nullableArrayy[171]!!
                        inputArray[151] = nullableArrayy[203]!!
                        inputArray[152] = nullableArrayy[211]!!
                        inputArray[153] = nullableArrayy[223]!!
                        inputArray[154] = nullableArrayx[214]!!
                        inputArray[155] = nullableArrayx[82]!!
                        inputArray[156] = nullableArrayy[445]!!
                        inputArray[157] = nullableArrayy[213]!!
                        inputArray[158] = nullableArrayy[214]!!
                        inputArray[159] = nullableArrayy[113]!!
                        inputArray[160] = nullableArrayy[268]!!
                        inputArray[161] = nullableArrayy[406]!!
                        inputArray[162] = nullableArrayy[29]!!
                        inputArray[163] = nullableArrayy[433]!!
                        inputArray[164] = nullableArrayx[193]!!
                        inputArray[165] = nullableArrayy[259]!!
                        inputArray[166] = nullableArrayy[446]!!
                        inputArray[167] = nullableArrayy[16]!!
                        inputArray[168] = nullableArrayx[91]!!
                        inputArray[169] = nullableArrayy[316]!!
                        inputArray[170] = nullableArrayy[383]!!
                        inputArray[171] = nullableArrayx[78]!!
                        inputArray[172] = nullableArrayy[287]!!
                        inputArray[173] = nullableArrayx[44]!!
                        inputArray[174] = nullableArrayx[43]!!
                        inputArray[175] = nullableArrayy[133]!!
                        inputArray[176] = nullableArrayx[323]!!
                        inputArray[177] = nullableArrayy[136]!!
                        inputArray[178] = nullableArrayy[343]!!
                        inputArray[179] = nullableArrayy[48]!!
                        inputArray[180] = nullableArrayx[273]!!
                        inputArray[181] = nullableArrayy[180]!!
                        inputArray[182] = nullableArrayy[14]!!
                        inputArray[183] = nullableArrayy[141]!!
                        inputArray[184] = nullableArrayy[88]!!
                        inputArray[185] = nullableArrayy[150]!!
                        inputArray[186] = nullableArrayy[175]!!
                        inputArray[187] = nullableArrayy[201]!!
                        inputArray[188] = nullableArrayy[44]!!
                        inputArray[189] = nullableArrayx[437]!!
                        inputArray[190] = nullableArrayy[181]!!
                        inputArray[191] = nullableArrayy[58]!!
                        inputArray[192] = nullableArrayx[216]!!
                        inputArray[193] = nullableArrayx[325]!!
                        inputArray[194] = nullableArrayy[384]!!
                        inputArray[195] = nullableArrayx[96]!!
                        inputArray[196] = nullableArrayy[102]!!
                        inputArray[197] = nullableArrayx[75]!!
                        inputArray[198] = nullableArrayx[185]!!
                        inputArray[199] = nullableArrayy[165]!!
                        inputArray[200] = nullableArrayy[75]!!
                        inputArray[201] = nullableArrayx[180]!!
                        inputArray[202] = nullableArrayy[146]!!
                        inputArray[203] = nullableArrayy[109]!!
                        inputArray[204] = nullableArrayy[403]!!
                        inputArray[205] = nullableArrayy[418]!!
                        inputArray[206] = nullableArrayy[407]!!
                        inputArray[207] = nullableArrayy[422]!!
                        inputArray[208] = nullableArrayx[217]!!
                        inputArray[209] = nullableArrayy[78]!!
                        inputArray[210] = nullableArrayy[408]!!
                        inputArray[211] = nullableArrayy[272]!!
                        inputArray[212] = nullableArrayx[136]!!
                        inputArray[213] = nullableArrayy[85]!!
                        inputArray[214] = nullableArrayy[432]!!
                        inputArray[215] = nullableArrayy[300]!!
                        inputArray[216] = nullableArrayx[204]!!
                        inputArray[217] = nullableArrayx[208]!!
                        inputArray[218] = nullableArrayy[274]!!
                        inputArray[219] = nullableArrayy[452]!!
                        inputArray[220] = nullableArrayy[190]!!
                        inputArray[221] = nullableArrayy[419]!!
                        inputArray[222] = nullableArrayy[59]!!
                        inputArray[223] = nullableArrayy[208]!!
                        inputArray[224] = nullableArrayx[211]!!
                        inputArray[225] = nullableArrayx[213]!!
                        inputArray[226] = nullableArrayy[416]!!
                        inputArray[227] = nullableArrayx[321]!!
                        inputArray[228] = nullableArrayy[177]!!
                        inputArray[229] = nullableArrayy[182]!!
                        inputArray[230] = nullableArrayy[375]!!
                        inputArray[231] = nullableArrayx[41]!!
                        inputArray[232] = nullableArrayy[138]!!
                        inputArray[233] = nullableArrayy[253]!!
                        inputArray[234] = nullableArrayx[428]!!
                        inputArray[235] = nullableArrayy[90]!!
                        inputArray[236] = nullableArrayy[271]!!
                        inputArray[237] = nullableArrayx[50]!!
                        inputArray[238] = nullableArrayy[22]!!
                        inputArray[239] = nullableArrayy[458]!!
                        inputArray[240] = nullableArrayx[435]!!
                        inputArray[241] = nullableArrayy[37]!!
                        inputArray[242] = nullableArrayy[83]!!
                        inputArray[243] = nullableArrayy[184]!!
                        inputArray[244] = nullableArrayx[103]!!
                        inputArray[245] = nullableArrayy[119]!!
                        inputArray[246] = nullableArrayy[164]!!
                        inputArray[247] = nullableArrayx[427]!!
                        inputArray[248] = nullableArrayy[320]!!
                        inputArray[249] = nullableArrayy[183]!!
                        inputArray[250] = nullableArrayy[439]!!
                        inputArray[251] = nullableArrayy[39]!!
                        inputArray[252] = nullableArrayx[203]!!
                        inputArray[253] = nullableArrayx[288]!!
                        inputArray[254] = nullableArrayy[105]!!
                        inputArray[255] = nullableArrayx[207]!!
                        inputArray[256] = nullableArrayx[137]!!
                        inputArray[257] = nullableArrayy[158]!!
                        inputArray[258] = nullableArrayy[252]!!
                        inputArray[259] = nullableArrayx[311]!!
                        inputArray[260] = nullableArrayy[282]!!
                        inputArray[261] = nullableArrayy[17]!!
                        inputArray[262] = nullableArrayy[230]!!
                        inputArray[263] = nullableArrayy[273]!!
                        inputArray[264] = nullableArrayy[412]!!
                        inputArray[265] = nullableArrayy[111]!!
                        inputArray[266] = nullableArrayy[212]!!
                        inputArray[267] = nullableArrayx[359]!!
                        inputArray[268] = nullableArrayy[82]!!
                        inputArray[269] = nullableArrayy[334]!!
                        inputArray[270] = nullableArrayy[321]!!
                        inputArray[271] = nullableArrayy[143]!!
                        inputArray[272] = nullableArrayy[202]!!
                        inputArray[273] = nullableArrayy[404]!!
                        inputArray[274] = nullableArrayx[151]!!
                        inputArray[275] = nullableArrayx[205]!!
                        inputArray[276] = nullableArrayy[123]!!
                        inputArray[277] = nullableArrayy[176]!!
                        inputArray[278] = nullableArrayy[304]!!
                        inputArray[279] = nullableArrayy[308]!!
                        inputArray[280] = nullableArrayy[193]!!
                        inputArray[281] = nullableArrayy[313]!!
                        inputArray[282] = nullableArrayy[92]!!
                        inputArray[283] = nullableArrayx[81]!!
                        inputArray[284] = nullableArrayx[99]!!
                        inputArray[285] = nullableArrayy[348]!!
                        inputArray[286] = nullableArrayy[228]!!
                        inputArray[287] = nullableArrayy[329]!!
                        inputArray[288] = nullableArrayy[381]!!
                        inputArray[289] = nullableArrayy[312]!!
                        inputArray[290] = nullableArrayy[311]!!
                        inputArray[291] = nullableArrayy[128]!!
                        inputArray[292] = nullableArrayy[188]!!
                        inputArray[293] = nullableArrayy[305]!!
                        inputArray[294] = nullableArrayy[33]!!
                        inputArray[295] = nullableArrayy[285]!!
                        inputArray[296] = nullableArrayx[402]!!
                        inputArray[297] = nullableArrayy[257]!!
                        inputArray[298] = nullableArrayx[423]!!
                        inputArray[299] = nullableArrayx[40]!!


// 나머지 값들을 추가로 작성해주세요







                        Log.i("tt",inputArray[0].toString())

                        val inputTensor = Array(1) { FloatArray(300) }
                        inputArray.copyInto(inputTensor[0])

                        // 입력 데이터 초기화
                        val outputArray = FloatArray(6) // 4개의 값을 가지는 1차원 배열 생성
                        val outputTensor = Array(1) { outputArray } // [1 6] 형태의 아웃풋 텐서 생성
                        interpreter.run(inputTensor, outputTensor)
                        //Log.i("tt",outputTensor[0][0].toString())
                        //Log.i("tt",outputTensor[0][1].toString())
                        //Log.i("tt",outputTensor[0][2].toString())
                        //Log.i("tt",outputTensor[0][3].toString())
                        val maxIndex = outputTensor[0].indices.maxByOrNull { outputTensor[0][it] } ?: -1
                        //Log.i("tt",maxIndex.toString())
                        textToSpeech = TextToSpeech(activity, this)

                        val textToSpeech = TextToSpeech(requireContext()) { status ->
                            if (status == TextToSpeech.SUCCESS) {
                                val result = textToSpeech.setLanguage(Locale.KOREAN)

                                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                } else {
                                    // tts 성공
                                    if (maxIndex.toString() == "0") {
                                        Log.i("tt","무표정.")
                                        val textToSpeak = "상대방이 아무표정도 짓지 않고 있습니다 상대방과 좀 더 활발한 의사소통을 해보세요."
                                        textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
                                        // 처리할 로직 추가
                                    } else if (maxIndex.toString() == "1") {

                                        Log.i("tt","기쁨.")
                                        val textToSpeak = "상대방이 미소짓고 있습니다 의사소통이 잘 되고있는 것 같아요."
                                        textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
                                        //speakText("기쁜 표정을 짓고있습니다.")
                                        // 처리할 로직 추가
                                    } else if (maxIndex.toString() == "2") {
                                        Log.i("tt", "화남")
                                        val textToSpeak = "상대방이 기분이 안좋아보여요 상대방의 기분을 풀어보는건 어떨까요 ?"
                                        textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
                                        // 처리할 로직 추가
                                    } else if (maxIndex.toString() == "3") {
                                        Log.i("tt", "놀람")
                                        val textToSpeak = "상대방이 놀란 반응을 보이고 있어요 뭐 때문에 놀란걸까요 ?"
                                        textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
                                        // 처리할 로직 추가
                                    } else if (maxIndex.toString() == "4") {
                                        Log.i("tt", "경멸")
                                        val textToSpeak = "상대방이 싫어하는 것 같아요 실수한건 없는지 생각해보는건 어떨까요 ?"
                                        textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
                                        // 처리할 로직 추가
                                    } else if (maxIndex.toString() == "5") {
                                        Log.i("tt", "슬픔")
                                        val textToSpeak = "상대방이 슬퍼하고 있어요 위로의 한마디를 해보는건 어떨까요 ?"
                                        textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
                                        // 처리할 로직 추가
                                    } else {
                                        Log.i("tt", "Invalid Max Index: $maxIndex")
                                        // 처리할 로직 추가
                                    }

                                }
                            } else {
                            }
                        }



                    }
                    /*


                    //아웃 풋 출력 처리
                    when (outputArray[0]) {
                        0 -> {
                            // 처리할 코드
                            Toast.makeText(requireContext(), "값은 0입니다.", Toast.LENGTH_SHORT).show()
                            speakText("값은 0입니다.")
                        }
                        1 -> {
                            // 처리할 코드
                            Toast.makeText(requireContext(), "값은 1입니다.", Toast.LENGTH_SHORT).show()
                            speakText("값은 1입니다.")
                        }
                        2 -> {
                            // 처리할 코드
                            Toast.makeText(requireContext(), "값은 2입니다.", Toast.LENGTH_SHORT).show()
                            speakText("값은 2입니다.")
                        }
                        3 -> {
                            // 처리할 코드
                            Toast.makeText(requireContext(), "값은 3입니다.", Toast.LENGTH_SHORT).show()
                            speakText("값은 3입니다.")
                        }
                        else -> {
                            // 처리할 코드
                            Toast.makeText(requireContext(), "해당 값은 구분할 수 없습니다.", Toast.LENGTH_SHORT).show()
                            speakText("해당 값은 구분할 수 없습니다.")
                        }
                    }
                    */

                }


            }
        }


    }
    fun normalizeArray(array: FloatArray): FloatArray {
        val minValue = array.minOrNull() ?: return array // 배열이 비어있는 경우 그대로 반환
        val maxValue = array.maxOrNull() ?: return array // 배열이 비어있는 경우 그대로 반환

        val normalizedArray = FloatArray(array.size)

        for (i in array.indices) {
            normalizedArray[i] = (array[i] - minValue) / (maxValue - minValue)
        }

        return normalizedArray
    }

    private fun loadModel() {
        try {
            val fileDescriptor: AssetFileDescriptor = requireContext().assets.openFd("modelFINAL.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            interpreter = Interpreter(mappedByteBuffer)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onEmpty() {
        fragmentCameraBinding.overlay.clear()
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == FaceLandmarkerHelper.GPU_ERROR) {
                fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    FaceLandmarkerHelper.DELEGATE_CPU, false
                )
            }
        }
    }



}
