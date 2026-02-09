package com.aiautomation.assistant.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.aiautomation.assistant.data.ActionSequence
import com.aiautomation.assistant.data.RecognizedPattern
import com.aiautomation.assistant.service.UIContextNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class PatternRecognitionManager(private val context: Context) {

    suspend fun analyzeScreen(bitmap: Bitmap, uiNodes: List<UIContextNode>): ActionSequence? {
        return withContext(Dispatchers.Default) {
            val smartAction = findSmartAction(uiNodes)
            if (smartAction != null) return@withContext smartAction

            val matchAction = findDominoMatch(bitmap)
            if (matchAction != null) return@withContext matchAction
            
            return@withContext null
        }
    }

    private fun findSmartAction(nodes: List<UIContextNode>): ActionSequence? {
        val targetKeywords = listOf(
            "Play", "Level", "Claim", "Collect", "No Thanks", "Tap to Start", "Retry", "Free",
            "Install", "Update", "Confirm", "Allow", "Continue", "Next", "Skip", "Close", "X"
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
                sequenceId = "menu_auto",
                orderInSequence = 0,
                timestamp = System.currentTimeMillis(),
                text = "Menu: ${targetNode.text}",
                confidence = 1.0f
            )
        }
        return null
    }

    private fun findDominoMatch(bitmap: Bitmap): ActionSequence? {
        val width = bitmap.width
        val height = bitmap.height
        
        val handX = width / 2
        val handY = (height * 0.88).toInt()
        
        val boardBottom = (height * 0.75).toInt()
        val boardTop = (height * 0.15).toInt()

        try {
            val handColor = getAverageColor(bitmap, handX, handY, 40)
            if (handColor.brightness < 0.2f) return null

            val rows = 12
            val cols = 8
            val cellW = width / cols
            val cellH = (boardBottom - boardTop) / rows

            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val cx = c * cellW + (cellW / 2)
                    val cy = boardTop + r * cellH + (cellH / 2)
                    
                    val boardColor = getAverageColor(bitmap, cx, cy, 25)
                    
                    if (areColorsSimilar(handColor, boardColor)) {
                        return ActionSequence(
                            actionType = "CLICK",
                            x = cx.toFloat(),
                            y = cy.toFloat(),
                            sequenceId = "domino_match",
                            orderInSequence = 0,
                            timestamp = System.currentTimeMillis(),
                            text = "Match Found!",
                            confidence = 0.95f
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun getAverageColor(bitmap: Bitmap, x: Int, y: Int, size: Int): ColorSignature {
        var rSum = 0L; var gSum = 0L; var bSum = 0L
        var count = 0
        
        val startX = (x - size/2).coerceIn(0, bitmap.width - 1)
        val endX = (x + size/2).coerceIn(0, bitmap.width - 1)
        val startY = (y - size/2).coerceIn(0, bitmap.height - 1)
        val endY = (y + size/2).coerceIn(0, bitmap.height - 1)

        for (px in startX..endX) {
            for (py in startY..endY) {
                val pixel = bitmap.getPixel(px, py)
                rSum += Color.red(pixel)
                gSum += Color.green(pixel)
                bSum += Color.blue(pixel)
                count++
            }
        }
        
        if (count == 0) return ColorSignature(0,0,0,0f)
        return ColorSignature(
            (rSum/count).toInt(), 
            (gSum/count).toInt(), 
            (bSum/count).toInt(), 
            ((rSum+gSum+bSum)/count)/765f
        )
    }

    private fun areColorsSimilar(c1: ColorSignature, c2: ColorSignature): Boolean {
        val diff = abs(c1.r - c2.r) + abs(c1.g - c2.g) + abs(c1.b - c2.b)
        return diff < 35 
    }

    data class ColorSignature(val r: Int, val g: Int, val b: Int, val brightness: Float)
    
    // Legacy stubs
    suspend fun recognizePatterns(bitmap: Bitmap): List<RecognizedPattern> = emptyList()
    suspend fun updateModel(actions: List<ActionSequence>) {}
    suspend fun processFrame(bitmap: Bitmap) {}
}
