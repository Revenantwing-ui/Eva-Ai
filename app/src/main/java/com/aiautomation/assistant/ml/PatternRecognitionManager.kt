package com.aiautomation.assistant.ml

import android.content.Context
import android.graphics.Bitmap
import com.aiautomation.assistant.data.ActionSequence
import com.aiautomation.assistant.data.RecognizedPattern
import com.aiautomation.assistant.service.UIContextNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class PatternRecognitionManager(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val imageProcessor = ImagePreprocessor()
    private val patternTemplates = mutableListOf<PatternTemplate>()

    init {
        initializeModel()
    }

    private fun initializeModel() {
        try {
            // Placeholder for TFLite model initialization
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * MAIN ENTRY POINT: Analyze screen for both Game Logic (Visuals) and Menu Logic (Context)
     */
    suspend fun analyzeScreen(bitmap: Bitmap, uiNodes: List<UIContextNode>): ActionSequence? {
        return withContext(Dispatchers.Default) {
            
            // 1. MENU LOGIC: Check for text buttons first (Fastest & Most Reliable)
            val smartAction = findSmartAction(uiNodes)
            if (smartAction != null) return@withContext smartAction

            // 2. GAME LOGIC: If no menus are found, try to play the game
            val matchAction = findVisualMatches(bitmap)
            if (matchAction != null) return@withContext matchAction
            
            return@withContext null
        }
    }

    /**
     * Heuristic Engine: Finds common action buttons based on text labels
     */
    private fun findSmartAction(nodes: List<UIContextNode>): ActionSequence? {
        val targetKeywords = listOf(
            "Install", "Update", "Confirm", "Allow", "Continue", "Next", "Skip", "Close", "X",
            "Play", "Level", "Claim", "Collect", "No Thanks", "Tap to Start", "Retry", "Free", "Hint"
        )
        
        val targetNode = nodes.find { node -> 
            (node.isClickable || node.text.length < 20) && 
            targetKeywords.any { keyword -> 
                node.text.contains(keyword, ignoreCase = true) 
            }
        }

        if (targetNode != null) {
            return ActionSequence(
                actionType = "CLICK",
                x = targetNode.bounds.centerX().toFloat(),
                y = targetNode.bounds.centerY().toFloat(),
                sequenceId = "menu_auto_${System.currentTimeMillis()}",
                orderInSequence = 0,
                timestamp = System.currentTimeMillis(),
                confidence = 0.95f
            )
        }
        return null
    }

    /**
     * Visual Engine: Scans the screen for identical visual regions (Selection Matching)
     */
    private fun findVisualMatches(bitmap: Bitmap): ActionSequence? {
        val width = bitmap.width
        val height = bitmap.height
        val gameAreaTop = (height * 0.2).toInt()
        val gameAreaBottom = (height * 0.8).toInt()
        
        val rows = 6
        val cols = 4
        val cellW = width / cols
        val cellH = (gameAreaBottom - gameAreaTop) / rows
        
        val cells = mutableListOf<GridCell>()

        try {
            // 1. Extract features
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val cx = c * cellW
                    val cy = gameAreaTop + (r * cellH)
                    
                    if (cx + cellW <= width && cy + cellH <= height) {
                        val sample = Bitmap.createBitmap(bitmap, cx + 10, cy + 10, cellW - 20, cellH - 20)
                        val features = extractFeatures(sample)
                        cells.add(GridCell(r, c, cx + cellW/2f, cy + cellH/2f, features))
                    }
                }
            }

            // 2. Compare cells
            for (i in 0 until cells.size) {
                for (j in i + 1 until cells.size) {
                    val cellA = cells[i]
                    val cellB = cells[j]
                    
                    val similarity = calculateSimilarity(cellA.features, cellB.features)
                    
                    if (similarity > 0.92f) { 
                        return ActionSequence(
                            actionType = "CLICK",
                            x = cellA.centerX,
                            y = cellA.centerY,
                            sequenceId = "game_match_${System.currentTimeMillis()}",
                            orderInSequence = 0,
                            timestamp = System.currentTimeMillis(),
                            confidence = similarity
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return null
    }

    suspend fun processFrame(bitmap: Bitmap) = withContext(Dispatchers.Default) {
        // Legacy learning mode support
    }

    private fun extractFeatures(bitmap: Bitmap): FloatArray {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val features = FloatArray(224 * 224 * 3)
        var index = 0
        
        for (y in 0 until 224) {
            for (x in 0 until 224) {
                val pixel = scaledBitmap.getPixel(x, y)
                features[index++] = ((pixel shr 16) and 0xFF) / 255f
                features[index++] = ((pixel shr 8) and 0xFF) / 255f
                features[index++] = (pixel and 0xFF) / 255f
            }
        }
        scaledBitmap.recycle()
        return features
    }

    private fun calculateSimilarity(features1: FloatArray, features2: FloatArray): Float {
        if (features1.size != features2.size) return 0f
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        for (i in features1.indices) {
            dotProduct += features1[i] * features2[i]
            norm1 += features1[i] * features1[i]
            norm2 += features2[i] * features2[i]
        }
        val denominator = sqrt(norm1 * norm2)
        return if (denominator > 0) dotProduct / denominator else 0f
    }

    suspend fun updateModel(actions: List<ActionSequence>) = withContext(Dispatchers.Default) {
        // Model update logic
    }

    data class PatternTemplate(
        val type: String,
        val features: FloatArray,
        val x: Float,
        val y: Float,
        val width: Int,
        val height: Int,
        var observationCount: Int = 0,
        var lastSeen: Long = 0L
    )

    private data class GridCell(
        val row: Int, 
        val col: Int, 
        val centerX: Float, 
        val centerY: Float, 
        val features: FloatArray
    )
}

class ImagePreprocessor {
    fun preprocessImage(bitmap: Bitmap): TensorImage {
        return TensorImage.fromBitmap(bitmap)
    }
}
