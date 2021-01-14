package onlineminer

import scala.collection.immutable.Queue
import collection.immutable.Seq

/***
  * Class for managing / updating the online mining queues.
  * Algorithm for queue management, see "Algorithm 2: Online HM" in "Heuristics Miners for Streaming Event Data" (https://arxiv.org/pdf/1212.6383.pdf)
  */
class OnlineMiningQueueManager(
                                maxSizeQueueRecentObservedActivityContainers: Int = 10000,
                                maxSizeQueueRecentObservedEventPerCase: Int = 10000,
                                maxSizeQueueRecentObservedSuccessionRelations: Int = 10000,
                                degreeOfImportanceInitWeight: Float = 1.0f) {

  /***
    * Updates the given mining Queues based on the given sequence of events.
    * @param eventsToProcess The events with which the queues will be updated.
    * @param queueRecentObservedActivityContainers The queue with the recent observed activity containers.
    * @param queueRecentObservedEventPerCase The queue with the recent observed events per case.
    * @param queueRecentObservedSuccessionRelations The queue with the recent observed succession relations.
    * @return A triple with the updated three queues (Queue[ActivityContainer], Queue[Event], Queue[Relation]).
    */
  def updateMiningQueuesWithEvents(eventsToProcess: Seq[Event]
                                   , queueRecentObservedActivityContainers: Queue[ActivityContainer]
                                   , queueRecentObservedEventPerCase: Queue[Event]
                                   , queueRecentObservedSuccessionRelations: Queue[Relation]): (Queue[ActivityContainer], Queue[Event], Queue[Relation]) = {
    eventsToProcess match {
      case first +: tail =>
        val (activities, events, relations) = updateMiningSourceQueues(first, queueRecentObservedActivityContainers, queueRecentObservedEventPerCase, queueRecentObservedSuccessionRelations)
        updateMiningQueuesWithEvents(tail, activities, events, relations)
      case Seq() => (queueRecentObservedActivityContainers, queueRecentObservedEventPerCase, queueRecentObservedSuccessionRelations)
    }
  }

  /***
    * Finds the most recent element checked by findPredicate in the queue.
    * @param queue The queue, in which the function will search.
    * @param findPredicate The check/search predicate.
    * @tparam QueueElement The type of the elements in the queue.
    * @return The first most recent found element in the queue.
    */
  private def findMostRecentElementInQueue[QueueElement](queue: Queue[QueueElement], findPredicate: QueueElement => Boolean): Option[QueueElement]=
    queue.foldRight(Seq[QueueElement]())( (e, acc) => if (findPredicate(e)) acc :+ e else acc ) match {
      case Seq() => None
      case first +: _ => Some(first)
    }

  /***
    * Dequeues an element from the queue, of the given maximum size was reached.
    * @param observedQueue The observed queue.
    * @param maxSize The maximum size (maximum number of elements in the queue).
    * @tparam QueueElement The type of the elements in the queue.
    * @return The new queue (without the oldest element, if the maximum queue size was reached.
    */
  private def dequeueIfMaxSizeIsReached[QueueElement](observedQueue: Queue[QueueElement], maxSize: Int): Queue[QueueElement] =
    if (observedQueue.length > maxSize)
      observedQueue.dequeue._2
    else
      observedQueue

  /***
    * Updates all relevant mining source queues.
    * @param newEventFromStream The new event from the stream.
    * @param queueRecentObservedActivityContainers The queue with the recent observed activity containers.
    * @param queueRecentObservedEventPerCase The queue with the recent observed events per case.
    * @param queueRecentObservedSuccessionRelations The queue with the recent observed succession relations.
    * @return A triple with the three updated queues (queueRecentObservedActivityContainers, queueRecentObservedEventPerCase, queueRecentObservedSuccessionRelations)
    */
  private def updateMiningSourceQueues(newEventFromStream: Event
                               , queueRecentObservedActivityContainers: Queue[ActivityContainer]
                               , queueRecentObservedEventPerCase: Queue[Event]
                               , queueRecentObservedSuccessionRelations: Queue[Relation]): (Queue[ActivityContainer], Queue[Event], Queue[Relation]) = {

    /***
      * Updates the queue with recent observed activities.
      * @return The updated queue with recent observed activities
      */
    def updateRecentObservedActivityContainersQueue(): Queue[ActivityContainer] = {
      // Look for existence of activity of new event
      val existingActivityContainerOption = queueRecentObservedActivityContainers.find(
        activityContainer => activityContainer.activity.activityId == newEventFromStream.activity.activityId
      )

      // Update queue with recent observed activities
      // check if ai is already in Qa
      existingActivityContainerOption match {
        case Some(existingActivityContainer) =>
          //  If ai is already present in the queue, remove it from its current position and move it at the beginning of the queue
          queueRecentObservedActivityContainers.filter(activityContainer => activityContainer.activity.activityId != existingActivityContainer.activity.activityId) :+ existingActivityContainer.copy(frequencyOfImportanceWeight = existingActivityContainer.frequencyOfImportanceWeight + 1)
        case None =>
          // If this is not the case, inserted activity in Qa with weight 0.
          dequeueIfMaxSizeIsReached(queueRecentObservedActivityContainers, maxSizeQueueRecentObservedActivityContainers) :+ new ActivityContainer(activity = newEventFromStream.activity, frequencyOfImportanceWeight = degreeOfImportanceInitWeight)
      }
    }

    /***
      * Updates the queues with recent observed events per case and with the recent observed relations.
      * @return A tuple with the two updated queues (queueRecentObservedEventPerCase, queueRecentObservedSuccessionRelations)
      */
    def updateRecentObservedEventPerCaseQueueIncludingSuccessionRelationsQueue(): (Queue[Event], Queue[Relation]) = {
      // look for the most recent event observed for case ci
      findMostRecentElementInQueue[Event](queueRecentObservedEventPerCase, event => event.caseId == newEventFromStream.caseId) match {
        case Some(recentEvent) =>

          /***
            * Updates the queue with recent succession relations.
            * @return The updated queue with recent succession relations.
            */
          def updateRecentObservedSuccessionRelationsQueue(): Queue[Relation] = {
            // Create new relation instance, with degreeOfImportanceWeight 0
            val newRelationWithDegreeOfImportance0 = new Relation(recentEvent.activity, newEventFromStream.activity, frequencyOfImportance = degreeOfImportanceInitWeight)

            /***
              * Checks whether the two relations are equal
              *
              * @param relation1 First relation for check.
              * @param relation2 Second relation for check.
              * @return True, when relation1 and relation2 are equal, otherwise false.
              */
            def areTwoRelationsEqual(relation1: Relation, relation2: Relation): Boolean =
              (relation1.fromActivity, relation1.toActivity) == (relation2.fromActivity, relation2.toActivity)

            val isNewRelationWithDegreeOfImportance0EqualWithRelation = areTwoRelationsEqual(newRelationWithDegreeOfImportance0, _: Relation)

            // Search for existence of relation in queue (exclude emissionEventTimestamp property for existence check through setting to 0 for check)
            queueRecentObservedSuccessionRelations.find(isNewRelationWithDegreeOfImportance0EqualWithRelation) match {
              case Some(foundExistingRelation) =>
                //  If ri is already present in the queue, remove it from its current position and move it at the beginning of the queue
                queueRecentObservedSuccessionRelations.filter(relInQueue => !areTwoRelationsEqual(foundExistingRelation, relInQueue)) :+ newRelationWithDegreeOfImportance0.copy(frequencyOfImportance = foundExistingRelation.frequencyOfImportance + 1)
              case None =>
                // Check that we have no new relation where fromActivity == toActivity => Not possible => Prohibit
                if (newRelationWithDegreeOfImportance0.fromActivity != newRelationWithDegreeOfImportance0.toActivity)
                  // If this is not the case, insert relation in front of queue
                  dequeueIfMaxSizeIsReached(queueRecentObservedSuccessionRelations, maxSizeQueueRecentObservedSuccessionRelations) :+ newRelationWithDegreeOfImportance0
                else
                  queueRecentObservedSuccessionRelations
            }
          }

          // If ci is already present in the queue, remove it from its current position and move it at the beginning of the queue
          (queueRecentObservedEventPerCase.filter(event => event.caseId != recentEvent.caseId) :+ newEventFromStream, updateRecentObservedSuccessionRelationsQueue())
        case None =>
          // If this is not the case, insert event in front of queue
          (dequeueIfMaxSizeIsReached(queueRecentObservedEventPerCase, maxSizeQueueRecentObservedEventPerCase) :+ newEventFromStream, queueRecentObservedSuccessionRelations)
      }
    }

    val updatedRecentObservedEventPerCaseQueueIncludingSuccessionRelationsQueue = updateRecentObservedEventPerCaseQueueIncludingSuccessionRelationsQueue()

    // Return all updated queues
    (updateRecentObservedActivityContainersQueue(), updatedRecentObservedEventPerCaseQueueIncludingSuccessionRelationsQueue._1, updatedRecentObservedEventPerCaseQueueIncludingSuccessionRelationsQueue._2)
  }
}
