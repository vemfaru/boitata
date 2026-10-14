package br.com.faru.boitata.core.draw

import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform

/**
 * Draws [block] leaning about its foot, as a tree, a plant or a flame leans in the wind: the foot at ([feetX], [feetY]) stays where it is and the higher it goes the farther it moves
 * sideways. [bend] is -1..1 (a share of [maxLean]); [maxLean] is how far the very top moves per unit of height at full bend (0.14 is about 8 degrees).
 *
 * A plain shear, so it is cheap and works on any drawing. Take [bend] from `WindResponse.bend`.
 */
public fun DrawScope.sway(feetX: Float, feetY: Float, bend: Float, maxLean: Float = 0.14f, block: DrawScope.() -> Unit) {
    if (bend == 0f) { block(); return }
    val shear = Matrix().apply { this[1, 0] = -bend * maxLean }          // x moves by this much for each unit of height above the foot
    withTransform({ translate(feetX, feetY); transform(shear); translate(-feetX, -feetY) }) { block() }
}
