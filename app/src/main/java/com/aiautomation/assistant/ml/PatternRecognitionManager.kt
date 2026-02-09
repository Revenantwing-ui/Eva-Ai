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

    suspend fun loadMidasModel() = withContext(Dispatchers.IO) {
        if (isModelLoaded) return@withContext

        try {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val useCustom = prefs.getBoolean("use_custom_model", false)
            val customName = prefs.getString("model_path", null)

            var modelFile = File(context.filesDir, "midas_model.bin")

            if (useCustom && !customName.isNullOrEmpty()) {
                val customFile = File(context.filesDir, customName)
                if (customFile.exists()) {
                    modelFile = customFile
                }
            }

            if (!modelFile.exists()) {
                try {
                    context.assets.open("midas_model.bin").use { inputStream ->
                        FileOutputStream(modelFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MIDAS", "No model found.")
                    return@withContext
                }
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(128)
                .setTemperature(0.5f)
                .setTopK(40)
                .setRandomSeed(42)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isModelLoaded = true
            Log.d("MIDAS", "Brain Online")

        } catch (e: Exception) {
            e.printStackTrace()
            isModelLoaded = false
        }
    }

    suspend fun analyzeScreen(bitmap: Bitmap, uiNodes: List<UIContextNode>): ActionSequence? {
        return withContext(Dispatchers.Default) {
            if (!isModelLoaded) return@withContext findHeuristicAction(uiNodes)

            val meaningfulNodes = uiNodes.filter { 
                it.text.isNotBlank() && it.isClickable 
            }.take(25)

            if (meaningfulNodes.isEmpty()) return@withContext null

            val screenDescription = meaningfulNodes.joinToString("\n") { 
                "- [${it.text}]" 
            }
            
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
                val response = llmInference?.generateResponse(prompt)?.trim() ?: "WAIT"
                
                if (response.contains("WAIT", ignoreCase = true)) return@withContext null

                val targetNode = meaningfulNodes.find { 
                    it.text.contains(response, ignoreCase = true) 
                }

                if (targetNode != null) {
                    if (recentActions.size > 4) recentActions.removeAt(0)
                    recentActions.add(response)

                    // FIX: Using Named Arguments to prevent Type Mismatch errors
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
        return if (target != null) {
            // FIX: Using Named Arguments here as well
            ActionSequence(
                actionType = "CLICK", 
                x = target.bounds.centerX().toFloat(), 
                y = target.bounds.centerY().toFloat(),
                sequenceId = "heuristic", 
                orderInSequence = 0, 
                timestamp = System.currentTimeMillis(), 
                text = "Fallback: ${target.text}"
            ) 
        } else null
    }

    suspend fun recognizePatterns(bitmap: Bitmap): List<RecognizedPattern> = emptyList()
    suspend fun updateModel(actions: List<ActionSequence>) {}
    suspend fun processFrame(bitmap: Bitmap) {}
}
