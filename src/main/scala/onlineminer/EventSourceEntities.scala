package onlineminer

/***
  * Defines an raw activity.
  * @param activityId The unique numeric id of an activity.
  * @param activityName The describing activity name.
  */
final case class Activity(activityId: Int, activityName: String) extends Serializable //, payloadData: Payload)

/***
  * Container for an activity with additional describing attributes.
  * @param activity The activity itself.
  * @param frequencyOfImportanceWeight The frequency of importance (the higher, the more important the activity is).
  */
final case class ActivityContainer(activity: Activity, frequencyOfImportanceWeight: Float) extends Serializable

/***
  * Defines a raw event.
  * @param caseId The id of the case, to which the activity belongs to.
  * @param activity The activity, which was produced by the event.
  * @param emissionEventTimestamp The emission time stamp of the event.
  */
final case class Event(caseId: Int, activity: Activity, emissionEventTimestamp: Long) extends Serializable

/***
  * Defines a raw relation between two events.
  * @param fromActivity The from activity.
  * @param toActivity The to activity.
  * @param frequencyOfImportance The frequency of importance (the higher, the more important the relation is).
  */
final case class Relation(fromActivity: Activity, toActivity: Activity, frequencyOfImportance: Float) extends Serializable