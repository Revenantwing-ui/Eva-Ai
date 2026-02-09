package com.aiautomation.assistant.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.aiautomation.assistant.data.ActionSequence
import com.aiautomation.assistant.data.RecognizedPattern
import com.aiautomation.assistant.service.UIContextNode
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PatternRecognitionManager(private val context: Context) {

    private var llmInference: LlmInference? = null
    private var isModelLoaded = false
    private val recentActions = mutableListOf<String>()

    /**
     * LOADS THE MODEL (USER SELECTED OR DEFAULT)
     */
    suspend fun loadMidasModel() = withContext(Dispatchers.IO) {
        if (isModelLoaded) return@withContext

        try {
            // 1. Determine which file to load
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val useCustom = prefs.getBoolean("use_custom_model", false)
            val customName = prefs.getString("model_path", null)

            var modelFile = File(context.filesDir, "midas_model.bin") // Default

            if (useCustom && !customName.isNullOrEmpty()) {
                val customFile = File(context.filesDir, customName)
                if (customFile.exists()) {
                    modelFile = customFile
                }
            }

            // 2. Extract default from Assets if needed
            if (!modelFile.exists()) {
                Log.d("MIDAS", "Model not found in storage, checking assets...")
                try {
                    // Try to extract midas_model.bin from assets if it exists there
                    context.assets.open("midas_model.bin").use { inputStream ->
                        FileOutputStream(modelFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Log.d("MIDAS", "Extracted default model from Assets.")
                } catch (e: Exception) {
                    Log.e("MIDAS", "No model found! Please import a .bin file in Settings.")
                    return@withContext
                }
            }

            Log.d("MIDAS", "Initializing LLM: ${modelFile.name}")

            // 3. 4GB RAM Optimized Config
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(128)      // Strict token limit
                .setTemperature(0.5f)   // Focused
                .setTopK(40)            // Low vocabulary size for speed
                .setRandomSeed(42)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isModelLoaded = true
            Log.d("MIDAS", "Brain Online")

        } catch (e: Exception) {
            Log.e("MIDAS", "Brain Init Failed: ${e.message}")
            e.printStackTrace()
            isModelLoaded = false
        }
    }

    /**
     * CORE AGENT FUNCTION
     */
    suspend fun analyzeScreen(bitmap: Bitmap, uiNodes: List<UIContextNode>): ActionSequence? {
        return withContext(Dispatchers.Default) {
            // Fallback if LLM is not ready
            if (!isModelLoaded) return@withContext findHeuristicAction(uiNodes)

            // 1. Perception (Text Extraction)
            // Limit to 25 relevant nodes to save context
            val meaningfulNodes = uiNodes.filter { 
                it.text.isNotBlank() && it.isClickable 
            }.take(25)

            if (meaningfulNodes.isEmpty()) return@withContext null

            val screenDescription = meaningfulNodes.joinToString("\n") { 
                "- [${it.text}]" 
            }
            
            // 2. Reasoning (Prompt Engineering)
            val prompt = """
                System: Android Automation Agent.
                Goal: Click the best button to progress.
                
                Screen Context:
                $screenDescription
                
                History: $recentActions
                
                Instructions:
                Output ONLY the exact text of the button to click.
                If no button is suitable, output "WAIT".
            """.trimIndent()

            try {
                // 3. Inference
                val response = llmInference?.generateResponse(prompt)?.trim() ?: "WAIT"
                
                if (response.contains("WAIT", ignoreCase = true)) return@withContext null

                // 4. Action Mapping
                val targetNode = meaningfulNodes.find { 
                    it.text.contains(response, ignoreCase = true) 
                }

                if (targetNode != null) {
                    // Update Memory
                    if (recentActions.size > 4) recentActions.removeAt(0)
                    recentActions.add(response)

                    return@withContext ActionSequence(
                        actionType = "CLICK",
                        x = targetNode.bounds.centerX().toFloat(),
                        y = targetNode.bounds.centerY().toFloat(),
                        sequenceId = "midas_llm_act",
                        orderInSequence = 0,
                        timestamp = System.currentTimeMillis(),
                        text = "Midas: ${targetNode.text}",
                        confidence = 0.95f
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                // If LLM fails (OOM), fall back to heuristics
                return@withContext findHeuristicAction(uiNodes)
            }

            return@withContext null
        }
    }

    private fun findHeuristicAction(nodes: List<UIContextNode>): ActionSequence? {
        val keywords = listOf("Play", "Next", "Continue", "Claim", "X", "Close", "Agree")
        val target = nodes.find { node -> 
            keywords.any { k -> node.text.contains(k, ignoreCase = true) }
        }
        return if (target != null) ActionSequence(
            "CLICK", target.bounds.centerX().toFloat(), target.bounds.centerY().toFloat(),
            "heuristic", 0, System.currentTimeMillis(), "Fallback: ${target.text}"
        ) else null
    }

    // Stub implementations for interface compliance
    suspend fun recognizePatterns(bitmap: Bitmap): List<RecognizedPattern> = emptyList()
    suspend fun updateModel(actions: List<ActionSequence>) {}
    suspend fun processFrame(bitmap: Bitmap) {}
}
