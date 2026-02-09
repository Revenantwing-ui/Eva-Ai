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

    /**
     * MAIN BRAIN: Analyzes the screen for Domino Matches
     */
    suspend fun analyzeScreen(bitmap: Bitmap, uiNodes: List<UIContextNode>): ActionSequence? {
        return withContext(Dispatchers.Default) {
            
            // 1. First, check for System/Menu popups (Play, Continue, etc.)
            // We prioritize this so the AI doesn't get stuck on "Level Complete" screens.
            val smartAction = findSmartAction(uiNodes)
            if (smartAction != null) {
                Log.d("AI_BRAIN", "Menu Action Found: ${smartAction.text}")
                return@withContext smartAction
            }

            // 2. DOMINO LOGIC: Hand-to-Board Matching
            val matchAction = findDominoMatch(bitmap)
            if (matchAction != null) {
                Log.d("AI_BRAIN", "Domino Match Found at ${matchAction.x}, ${matchAction.y}")
                return@withContext matchAction
            }
            
            return@withContext null
        }
    }

    /**
     * Looks for clickable Menu Buttons
     */
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

    /**
     * DOMINO MATCHING ALGORITHM
     * 1. Identify the color of the "Hand" tile (Bottom Center).
     * 2. Scan the "Board" (Top) for that same color.
     */
    private fun findDominoMatch(bitmap: Bitmap): ActionSequence? {
        val width = bitmap.width
        val height = bitmap.height
        
        // --- ZONE DEFINITIONS ---
        // Hand Area: The single tile you play from (Bottom Center)
        val handX = width / 2
        val handY = (height * 0.88).toInt() // Approx 88% down the screen
        
        // Board Area: The puzzle tiles (Top 75% of screen)
        val boardBottom = (height * 0.75).toInt()
        val boardTop = (height * 0.15).toInt()

        try {
            // 1. GET HAND COLOR
            // We sample a small box in the player's hand to find the "Active Color"
            val handColor = getAverageColor(bitmap, handX, handY, 30)
            
            // If hand is too dark/black, maybe no tile is there? Skip.
            if (handColor.brightness < 0.2f) {
                Log.d("AI_BRAIN", "Hand is empty/dark. Waiting...")
                return null
            }

            // 2. SCAN BOARD FOR MATCH
            // We scan the board in a grid looking for the Hand Color
            val rows = 12 // Higher density scan for better accuracy
            val cols = 8
            val cellW = width / cols
            val cellH = (boardBottom - boardTop) / rows

            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val cx = c * cellW + (cellW / 2)
                    val cy = boardTop + r * cellH + (cellH / 2)
                    
                    // Get color of this board section
                    val boardColor = getAverageColor(bitmap, cx, cy, 20)
                    
                    // CHECK MATCH
                    if (areColorsSimilar(handColor, boardColor)) {
                        // Found a tile on board with same color as hand!
                        return ActionSequence(
                            actionType = "CLICK",
                            x = cx.toFloat(),
                            y = cy.toFloat(),
                            sequenceId = "domino_match",
                            orderInSequence = 0,
                            timestamp = System.currentTimeMillis(),
                            text = "Found Match!",
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
        
        // Bounds checking
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
        
        val r = (rSum / count).toInt()
        val g = (gSum / count).toInt()
        val b = (bSum / count).toInt()
        val brightness = (0.299*r + 0.587*g + 0.114*b) / 255.0
        
        return ColorSignature(r, g, b, brightness.toFloat())
    }

    private fun areColorsSimilar(c1: ColorSignature, c2: ColorSignature): Boolean {
        // Threshold: 30 allows for slight lighting variations (glare, shadows)
        val diff = abs(c1.r - c2.r) + abs(c1.g - c2.g) + abs(c1.b - c2.b)
        return diff < 30 
    }

    // Data Classes
    data class ColorSignature(val r: Int, val g: Int, val b: Int, val brightness: Float)
    
    // Legacy stubs to satisfy interface
    suspend fun recognizePatterns(bitmap: Bitmap): List<RecognizedPattern> = emptyList()
    suspend fun updateModel(actions: List<ActionSequence>) {}
    suspend fun processFrame(bitmap: Bitmap) {}
}
