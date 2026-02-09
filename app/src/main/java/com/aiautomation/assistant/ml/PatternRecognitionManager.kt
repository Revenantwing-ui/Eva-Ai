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

    // No TFLite needed for this approach - we use direct bitmap analysis
    
    /**
     * MAIN BRAIN: Analyzes the screen for matches
     */
    suspend fun analyzeScreen(bitmap: Bitmap, uiNodes: List<UIContextNode>): ActionSequence? {
        return withContext(Dispatchers.Default) {
            
            // 1. Check for Text Buttons (Play, Continue, etc.)
            val smartAction = findSmartAction(uiNodes)
            if (smartAction != null) {
                Log.d("AI_BRAIN", "Found Menu Action: ${smartAction.text}")
                return@withContext smartAction
            }

            // 2. Check for Game Tiles (Grid Match)
            val matchAction = findVisualMatches(bitmap)
            if (matchAction != null) {
                Log.d("AI_BRAIN", "Found Tile Match at ${matchAction.x}, ${matchAction.y}")
                return@withContext matchAction
            }
            
            return@withContext null
        }
    }

    private fun findSmartAction(nodes: List<UIContextNode>): ActionSequence? {
        val targetKeywords = listOf(
            "Play", "Level", "Claim", "Collect", "No Thanks", "Tap to Start", "Retry", "Free",
            "Install", "Update", "Confirm", "Allow", "Continue", "Next", "Skip", "Close", "X"
        )
        
        // Find clickable nodes with matching text
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
                text = "Clicked: ${targetNode.text}", // For debug
                confidence = 1.0f
            )
        }
        return null
    }

    /**
     * ADVANCED COLOR MATCHER:
     * Instead of complex features, we check the "Color Signature" of the center of each tile.
     * This is much more robust for games like Domino Dreams.
     */
    private fun findVisualMatches(bitmap: Bitmap): ActionSequence? {
        val width = bitmap.width
        val height = bitmap.height
        
        // Expand search area slightly (15% to 85% of screen height)
        val gameAreaTop = (height * 0.15).toInt()
        val gameAreaBottom = (height * 0.85).toInt()
        
        // Standard Grid for Match Games (Adjustable)
        val rows = 6
        val cols = 4
        val cellW = width / cols
        val cellH = (gameAreaBottom - gameAreaTop) / rows
        
        val cells = mutableListOf<GridCell>()

        try {
            // 1. EXTRACT SIGNATURES
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val cx = c * cellW
                    val cy = gameAreaTop + (r * cellH)
                    
                    // Safety check bounds
                    if (cx + cellW <= width && cy + cellH <= height) {
                        // Get center point of this grid cell
                        val centerX = cx + (cellW / 2)
                        val centerY = cy + (cellH / 2)
                        
                        // Extract avg color of a 20x20 box in the center of the tile
                        // This avoids border issues and background noise
                        val signature = getAverageColor(bitmap, centerX, centerY, 20)
                        
                        // Filter out dark/transparent "empty" spaces (assuming black/dark grey background)
                        if (signature.brightness > 0.2f) {
                            cells.add(GridCell(r, c, centerX.toFloat(), centerY.toFloat(), signature))
                        }
                    }
                }
            }

            // 2. FIND MATCHING PAIRS
            for (i in 0 until cells.size) {
                for (j in i + 1 until cells.size) {
                    val cellA = cells[i]
                    val cellB = cells[j]
                    
                    // Compare Color Signatures
                    if (areColorsSimilar(cellA.signature, cellB.signature)) {
                        return ActionSequence(
                            actionType = "CLICK",
                            x = cellA.centerX,
                            y = cellA.centerY, // Click the first one found
                            sequenceId = "tile_match",
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
        
        val startX = (x - size/2).coerceAtLeast(0)
        val startY = (y - size/2).coerceAtLeast(0)
        val endX = (x + size/2).coerceAtMost(bitmap.width - 1)
        val endY = (y + size/2).coerceAtMost(bitmap.height - 1)

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
        
        // Calculate brightness (Luminance)
        val brightness = (0.299*r + 0.587*g + 0.114*b) / 255.0
        
        return ColorSignature(r, g, b, brightness.toFloat())
    }

    private fun areColorsSimilar(c1: ColorSignature, c2: ColorSignature): Boolean {
        // Simple Euclidean distance in RGB space
        // Threshold: 15 (Very strict match) to 30 (Loose match)
        val diff = abs(c1.r - c2.r) + abs(c1.g - c2.g) + abs(c1.b - c2.b)
        return diff < 25 // Adjust this if matches are missed (increase) or wrong (decrease)
    }

    // --- Helper Classes & Legacy Stubs ---
    
    data class ColorSignature(val r: Int, val g: Int, val b: Int, val brightness: Float)
    private data class GridCell(val row: Int, val col: Int, val centerX: Float, val centerY: Float, val signature: ColorSignature)

    suspend fun recognizePatterns(bitmap: Bitmap): List<RecognizedPattern> = emptyList()
    suspend fun updateModel(actions: List<ActionSequence>) {}
    suspend fun processFrame(bitmap: Bitmap) {}
}
