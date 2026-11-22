package com.calorie.diary.ui.exercise

import androidx.compose.ui.graphics.Color
import com.calorie.diary.R

/**
 * 运动热量计算核心（MET 法）。
 * 权威依据：
 *  - 中国体育科学学会 T/CSSS 002—2023；1 MET = 1 kcal/(kg·h)
 *  - Ainsworth Compendium of Physical Activities（抗阻训练 02050/02052/02054）
 *  - 中文实测：低负荷 25RM ≈ 5.72 MET，中负荷 12RM ≈ 6.60 MET
 */

enum class ExerciseCategory { CARDIO, STRENGTH }

data class ExerciseDef(
    val id: String,
    val nameRes: Int,
    val category: ExerciseCategory,
    val met: Double
)

data class LoadLevel(
    val id: String,
    val nameRes: Int,
    val descRes: Int,
    val met: Double
)

/** 有氧 / 心肺运动：固定 MET */
val cardioExercises = listOf(
    ExerciseDef("walk", R.string.ex_walk, ExerciseCategory.CARDIO, 2.5),
    ExerciseDef("brisk", R.string.ex_brisk_walk, ExerciseCategory.CARDIO, 4.3),
    ExerciseDef("jog", R.string.ex_jog, ExerciseCategory.CARDIO, 7.0),
    ExerciseDef("run", R.string.ex_run, ExerciseCategory.CARDIO, 9.8),
    ExerciseDef("cycle", R.string.ex_cycle, ExerciseCategory.CARDIO, 6.8),
    ExerciseDef("rope", R.string.ex_jump_rope, ExerciseCategory.CARDIO, 11.0),
    ExerciseDef("swim", R.string.ex_swim, ExerciseCategory.CARDIO, 7.0),
    ExerciseDef("elliptical", R.string.ex_elliptical, ExerciseCategory.CARDIO, 5.0),
    ExerciseDef("stairs", R.string.ex_stairs, ExerciseCategory.CARDIO, 7.5),
    ExerciseDef("aerobics", R.string.ex_aerobics, ExerciseCategory.CARDIO, 5.5),
    ExerciseDef("yoga", R.string.ex_yoga, ExerciseCategory.CARDIO, 2.5),
    ExerciseDef("kettlebell", R.string.ex_kettlebell, ExerciseCategory.CARDIO, 9.8)
)

/** 力量 / 抗阻训练：负荷档位决定 MET */
val strengthExercise =
    ExerciseDef("strength", R.string.ex_strength, ExerciseCategory.STRENGTH, 3.5)

val loadLevels = listOf(
    LoadLevel("body", R.string.load_body, R.string.load_body_desc, 3.5),
    LoadLevel("light", R.string.load_light, R.string.load_light_desc, 5.7),
    LoadLevel("medium", R.string.load_medium, R.string.load_medium_desc, 6.6),
    LoadLevel("heavy", R.string.load_heavy, R.string.load_heavy_desc, 6.0)
)

/** 总运动消耗（含静息，gross） */
fun grossCalories(met: Double, weightKg: Float, minutes: Int): Double =
    met * weightKg * (minutes / 60.0)

/**
 * 净运动消耗（net）：扣除这段时间即使不运动也会产生的 1 MET 静息消耗，
 * 这才是运动真正额外烧掉、可计入当日热量缺口的部分。
 */
fun netCalories(met: Double, weightKg: Float, minutes: Int): Double =
    (met - 1.0) * weightKg * (minutes / 60.0)

/**
 * 根据负重 / 体重比推荐负荷档位（经验阈值，仅作参考，可手动覆盖）。
 */
fun recommendLoad(loadKg: Float?, weightKg: Float): Int {
    if (loadKg == null || loadKg <= 0f) return 0
    val ratio = loadKg / weightKg
    return when {
        ratio < 0.3f -> 1
        ratio < 0.7f -> 2
        else -> 3
    }
}

/**
 * 当日热量缺口分析。
 * @param goal 每日目标热量
 * @param intake 今日已摄入
 * @param netExercise 本次净运动消耗
 */
data class GapResult(
    val goal: Int,
    val intake: Int,
    val netExercise: Int,
    val beforeRemaining: Int,   // 运动前还可摄入（负=已超标）
    val afterRemaining: Int     // 运动后还可摄入
)

fun computeGap(goal: Int, intake: Int, netExercise: Int): GapResult = GapResult(
    goal = goal,
    intake = intake,
    netExercise = netExercise,
    beforeRemaining = goal - intake,
    afterRemaining = goal - intake + netExercise
)

/** 身材分类（与 BMI 页一致的中国成人标准） */
data class BodyShape(val labelRes: Int, val color: Color)

fun bodyShape(bmi: Float): BodyShape = when {
    bmi < 18.5f -> BodyShape(R.string.bmi_thin, Color(0xFF5DADE2))
    bmi < 24f -> BodyShape(R.string.bmi_normal, Color(0xFF58B79B))
    bmi < 28f -> BodyShape(R.string.bmi_overweight, Color(0xFFE8A33D))
    else -> BodyShape(R.string.bmi_obese, Color(0xFFEF6C6C))
}

/** 不同身材（体重档位）做同一运动的净消耗对比 */
val weightCompareKg = listOf(50, 60, 70, 80)
