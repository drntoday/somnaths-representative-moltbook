package com.somnath.representative.safety

import com.somnath.representative.factpack.FactPack
import com.somnath.representative.factpack.FreshnessTriggerHelper

class ConfidenceScorer {
    fun score(
        threadText: String,
        factPack: FactPack?,
        sensitiveLevel: SensitiveLevel,
        injectionDetected: Boolean
    ): Int {
        var score = 50

        if (factPack != null && factPack.bullets.size >= 2) {
            score += 20
        }
        if (factPack?.asOf?.isNotBlank() == true) {
            score += 10
        }
        if (sensitiveLevel == SensitiveLevel.LOW) {
            score += 10
        }

        if (sensitiveLevel != SensitiveLevel.LOW) {
            score -= 20
        }

        if (FreshnessTriggerHelper.requiresFreshness(threadText) && factPack == null) {
            score -= 25
        }

        if (injectionDetected) {
            score -= 30
        }

        return score.coerceIn(0, 100)
    }
}
