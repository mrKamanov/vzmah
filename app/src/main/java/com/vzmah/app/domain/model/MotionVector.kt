package com.vzmah.app.domain.model

data class MotionVector(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
) {
    val magnitude: Double get() = kotlin.math.sqrt(x * x + y * y + z * z)

    operator fun minus(other: MotionVector): MotionVector =
        MotionVector(x - other.x, y - other.y, z - other.z)

    fun scale(factor: Double): MotionVector = MotionVector(x * factor, y * factor, z * factor)

    companion object {
        val ZERO = MotionVector()
        const val GRAVITY = 9.80665
    }
}

data class MotionSample(
    val timestampNanos: Long,
    val rawAccel: MotionVector,
    val linearAccel: MotionVector,
    val filteredAccel: MotionVector,
    val angularVelocity: MotionVector,
) {

    val magnitude: Double get() = filteredAccel.magnitude

    val nodRate: Double get() = angularVelocity.x

    val nodMagnitude: Double get() = kotlin.math.abs(nodRate)
}
