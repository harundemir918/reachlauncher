package com.baklavapp.reachlauncher.core.model

/*
 * Launch history and prediction types.
 *
 * Flutter analogy: the row/model classes you'd map from sqflite/drift results; the Room
 * entities in `:core:data` are mapped to these so nothing above the data layer sees Room.
 */

/**
 * One launch performed by Reach. Recording our own launches needs no permission and is
 * the primary input for the "Now" predictions.
 *
 * Hour and weekday are captured in the user's local time *at launch time*, so a trip to
 * another time zone doesn't rewrite what "usually around now" meant back then.
 *
 * @property epochMillis wall-clock time of the launch.
 * @property hourOfDay 0–23, local time.
 * @property dayOfWeek ISO day of week, 1 = Monday … 7 = Sunday.
 */
data class LaunchEvent(
    val key: AppKey,
    val epochMillis: Long,
    val hourOfDay: Int,
    val dayOfWeek: Int,
) {
    /** Weekend per ISO (Saturday, Sunday); predictions keep weekdays and weekends apart. */
    val isWeekend: Boolean get() = dayOfWeek >= 6
}

/** Why an app is in the "Now" row. Rendered as a short mono label above the app name. */
sealed interface PredictionReason {
    /** Launched in this hour window on similar days ("USUALLY AROUND NOW"). */
    data object UsuallyAroundNow : PredictionReason

    /** Has unread notifications ("2 UNREAD"). */
    data class Unread(
        val count: Int,
    ) : PredictionReason

    /** Used recently, no strong time pattern yet ("RECENT"). */
    data object Recent : PredictionReason
}

/** A predicted app for the "Now" row (Home) and the empty-query search suggestions. */
data class Prediction(
    val app: CatalogApp,
    val reason: PredictionReason,
    val score: Double,
)
