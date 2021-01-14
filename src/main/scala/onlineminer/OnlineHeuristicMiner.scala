package onlineminer

import scala.collection.immutable.{HashMap, Queue, Seq}
import scala.annotation.tailrec

/***
  * Represents a coordinate in a dependency matrix including its value.
  * @param fromActivity The from/begin activity.
  * @param toActivity The to/end activity.
  * @param value The calculated dependency between the fromActivity and toActivity.
  */
case class DependencyMatrixCoordinate(fromActivity: Activity, toActivity: Activity, value: Float)

/***
  * Represents a dependency matrix produced by a heuristic miner.
  * @param matrix The matrix itself.
  * @param fromToActivityIdsToRelationMapping A mapping set from (fromActivityId,toActivityId) to corresponding valid relation.
  * @param activities All existing activities.
  */
case class DependencyMatrix(matrix: Seq[DependencyMatrixCoordinate], fromToActivityIdsToRelationMapping: HashMap[(Int, Int), Relation], activities: Set[Activity])

/***
  * Contains base information to start building a new ProcessGraph.
  * @param validRelations A map with valid relations in the graph to be built.
  * @param validActivities A set with all valid activities in the graph to be built.
  * @param startActivities A set with all start activities in the graph to be built.
  * @param finalActivities A set with all final end activities in the graph to be built.
  * @param activitiesWithSplitRelations All activities, which have more than one follow/to-activity relations in the graph to be built.
  */
case class ProcessGraphBase(validRelations: HashMap[(Int, Int), Relation], validActivities: Set[Activity], startActivities: Set[Activity], finalActivities: Set[Activity], activitiesWithSplitRelations: Set[Activity])

/***
  * Implementation of an online heuristic miner, which operates on queues, representing the current process-stream, instead of a "past trace log" .
  * See "Heuristics Miners for Streaming Event Data" (https://arxiv.org/pdf/1212.6383.pdf)
  * and "Genetic Mining und Heuristic Mining" https://publications.jannikarndt.de/Genetic%20Mining%20und%20Heuristic%20Mining.pdf
  * @param detectAsDependencyThreshold A threshold (should be between 0.1 and 0.8) for classifying a dependency grade
  *                                    as "dependent". Means a dependency grade > the threshold will be classified as
  *                                    "dependent", otherwise not.
  */
class OnlineHeuristicMiner(detectAsDependencyThreshold: Float = 0.499f) extends Serializable {

  /***
    * Mines the output process graph based on the two input queues.
    * @param queueRecentObservedActivityContainers The queue with the recent observed activity containers.
    * @param queueRecentObservedSuccessionRelations The queue with the recent observed succession relations.
    * @return The complete mined output graph.
    */
  def mineProcessGraph(queueRecentObservedActivityContainers: Queue[ActivityContainer], queueRecentObservedSuccessionRelations: Queue[Relation]): ProcessGraph = {

    // 1.) Calculate dependency matrix
    val calculatedResultDependencyMatrix = calcDependencyMatrix(queueRecentObservedActivityContainers, queueRecentObservedSuccessionRelations)

    /***
      * Calculates base information to build result ProcessGraph.
      *
      * @return Base information to build result ProcessGraph.
      */
    def calcProcessGraphBase(): ProcessGraphBase = {

      val initializedBaseInfoToBuildProcessGraph =
        calculatedResultDependencyMatrix.matrix.foldRight(
          new ProcessGraphBase(HashMap[(Int, Int), Relation](), Set[Activity](), calculatedResultDependencyMatrix.activities, calculatedResultDependencyMatrix.activities, Set[Activity]()))((matrixElement, acc) => {

          // Set new valid relation, or not
          val newValidRelation =
          // Check, whether we have a new "valid" relation based on detectAsDependencyThreshold, and it must exist in fromToActivityIdsToRelationMapping
            if (matrixElement.value > detectAsDependencyThreshold
              && calculatedResultDependencyMatrix.fromToActivityIdsToRelationMapping.contains(matrixElement.fromActivity.activityId, matrixElement.toActivity.activityId))
              Some(new Relation(
                fromActivity = matrixElement.fromActivity,
                toActivity = matrixElement.toActivity,
                calculatedResultDependencyMatrix.fromToActivityIdsToRelationMapping(matrixElement.fromActivity.activityId, matrixElement.toActivity.activityId).frequencyOfImportance))
            else
              None

          // Set update values for validRelations, startActivities, finalActivities and activitiesWithSplitRelations
          val (validRelations, validActivities, startActivities, finalActivities, activitiesWithSplitRelations) =
          // Check, whether we have a new "valid" relation or not
            newValidRelation match {
              case Some(newRelation) =>
                (
                  // Add as new valid relation
                  acc.validRelations + ((newRelation.fromActivity.activityId, newRelation.toActivity.activityId) -> newRelation),

                  // Add fromActivity and toActivity as new valid activities
                  acc.validActivities + newRelation.fromActivity + newRelation.toActivity,

                  // Because a new valid relation is created => filter out the to-Activity from startActivities
                  acc.startActivities.filter(startActivity => startActivity != newRelation.toActivity),

                  // Because a new valid relation is created => filter out the from-Activity from finalActivities
                  acc.finalActivities.filter(finalActivity => finalActivity != newRelation.fromActivity),

                  // Check, whether a relation with the from-Activity of the new relation already exist in acc.validRelations
                  acc.validRelations.find(validRelation => validRelation._1._1 == newRelation.fromActivity.activityId) match {
                    // if yes, add it to activitiesWithSplitRelations
                    case Some(_) => acc.activitiesWithSplitRelations + newRelation.fromActivity
                    // if not, add just let activitiesWithSplitRelations untouched
                    case None => acc.activitiesWithSplitRelations
                  }

                )
              case None =>
                // No new valid relation => let accumulators as it current state
                (acc.validRelations, acc.validActivities, acc.startActivities, acc.finalActivities, acc.activitiesWithSplitRelations)
            }

          new ProcessGraphBase(
            validRelations = validRelations,
            validActivities = validActivities,
            // startActivities are all activities, which exist not as a to-activity in a existing relation
            startActivities = startActivities,
            // finalActivities are all activities, which exist not as a from-activity in a existing relation
            finalActivities = finalActivities,
            activitiesWithSplitRelations = activitiesWithSplitRelations
          )
        })

      new ProcessGraphBase(
        validRelations = initializedBaseInfoToBuildProcessGraph.validRelations,
        validActivities = initializedBaseInfoToBuildProcessGraph.validActivities,
        // Each start activity must also exist in validActivities (final filtering/join)
        startActivities = initializedBaseInfoToBuildProcessGraph.startActivities.intersect(initializedBaseInfoToBuildProcessGraph.validActivities),
        // Each final activity must also exist in validActivities (final filtering/join)
        finalActivities = initializedBaseInfoToBuildProcessGraph.finalActivities.intersect(initializedBaseInfoToBuildProcessGraph.validActivities),
        activitiesWithSplitRelations = initializedBaseInfoToBuildProcessGraph.activitiesWithSplitRelations
      )
    }

    // 2.) Calculate base information to build result ProcessGraph
    val baseInfoToBuildProcessGraph = calcProcessGraphBase()

    /***
      * Build all descendant transitions of the given rootActivityElementOfGraph, including recursive the complete
      * sub-graph (means also all descendants of descendants-activity-elements, till we arrive to FinishTransitionElement.)
      * @param rootActivityElementOfGraph The root activity element, where the descendant transitions (recursively) will
      *                                   be filled. This given descendantTransitions property of rootActivityElementOfGraph
      *                                   could be an empty Seq() in the initial call, the new found descendants will be
      *                                   appended.
      * @return The complete filled rootActivityElementOfGraph with all descendant transitions, (means also all
      *         descendants of descendants-activity-elements, till we arrive to FinishTransitionElement.)
      */
    def buildDescendantTransitionsOfRootActivityElement(rootActivityElementOfGraph: ActivityElement): ActivityElement = {

      // Find all relations, which have the currentPlaceActivityElement activity as from-activity
      val relationsWithCurrentPlaceActivityAsFromActivity =
        baseInfoToBuildProcessGraph.validRelations.toList.filter { case ((fromActivity, _), _) => fromActivity == rootActivityElementOfGraph.activityEntity.activityId }

      /***
        * Searches for an existing ActivityElement with the given activityToSearch in the current state process graph.
        * @param activityToSearch The activity of the ActivityElement, which should be searched.
        * @return The found ActivityElement, or None, of it was not found.
        */
      def findActivityElementInCurrentStateProcessGraphHierarchy(activityToSearch: Activity): Option[ActivityElement] = {

        /***
          * Searches and returns found start activities in current graph.
          * @param existingActivityElementInProcessGraph An existing ActivityElement somewhere inside the process graph.
          * @return The found start ActivityElements
          */
        def getStartActivityElements(existingActivityElementInProcessGraph: ActivityElement): scala.collection.immutable.Seq[ActivityElement] = {
          existingActivityElementInProcessGraph.ancestorActivityElements match {
            // No further ancestors found => means that the existingActivityElementInProcessGraph is a StartActivityElement
            case Nil => Seq(existingActivityElementInProcessGraph)
            // Further ancestors found => Keep on searching recursive in ancestors
            case ancestorActivityElements => ancestorActivityElements.flatMap(nextAncestorActivityElement => getStartActivityElements(nextAncestorActivityElement))
          }
        }

        // Now search for the given activityToSearch from the startActivityElement point(s) of the process graph and return ActivityElement if an existing element was found, otherwise return None
        (for (startActivityElement <- getStartActivityElements(rootActivityElementOfGraph))
          yield {
            // Now search, whether a ActivityElement with the given activityToSearch already exist in the process graph, if yes return it
            findActivityElementInDescendantTransitionsOfProcessGraph(activityToSearch: Activity, startActivityElement) match {
              // No existing ActivityElement found => Return None
              case None => None
              // Found an existing ActivityElement => Return found element
              case foundActivityElement => foundActivityElement
            }
            // Aggregate results from each search beginning at a found startActivityElement => Means store the first found ActivityElement in accumulator and return that (should be found only one element), if no one was found, return None
          }).foldLeft[Option[ActivityElement]](None)(
          (accumulatedResult: Option[ActivityElement], nextResult: Option[ActivityElement]) =>
            accumulatedResult match {
              case Some(_) => accumulatedResult
              case None => nextResult match {
                case None => accumulatedResult
                case Some(_) => nextResult
              }
            })
      }

      /***
        * Creates and builds a descendant SingleTransitionElement based on the given relation (a transition to tansitionRelation.toActivity).
        * @param tansitionRelation The transition relation, on which the SingleTransitionElement should be created.
        * @return The built SingleTransitionElement including filled descendantTransitions of the containing descendantActivityElement.
        */
      def buildDescendantSingleTransitionElement(tansitionRelation: Relation): SingleTransitionElement = {
        new SingleTransitionElement(
          // Check/search, whether ActivityElement with tansitionRelation.toActivity already exists in process graph
          findActivityElementInCurrentStateProcessGraphHierarchy(tansitionRelation.toActivity) match {
            // ActivityElement not found => Create new and build descendants
            case None =>
              val rootActivityElementOfGraphWithFilledDescendantTransitions =
                buildDescendantTransitionsOfRootActivityElement(
                  new ActivityElement(
                    activityEntity = tansitionRelation.toActivity,
                    ancestorActivityElements = Seq(), //rootActivityElementOfGraph.ancestorActivityElements,
                    descendantTransitions = Seq()
                  )
                )

              rootActivityElementOfGraphWithFilledDescendantTransitions.copy(
                ancestorActivityElements = Seq(rootActivityElementOfGraph.copy(
                  descendantTransitions =
                    rootActivityElementOfGraph.descendantTransitions
                    :+ //rootActivityElementOfGraphWithFilledDescendantTransitions.descendantTransitions
                    new SingleTransitionElement(
                      new ActivityElement(
                        activityEntity = tansitionRelation.toActivity,
                        ancestorActivityElements = Seq(), //rootActivityElementOfGraph.ancestorActivityElements,
                        descendantTransitions = Seq()
                      )
                      , tansitionRelation.frequencyOfImportance
                    )
                ))
              )

            // ActivityElement found => Re-use found ActivityElement and append current rootActivityElementOfGraph to ancestorActivityElements
            case Some(foundActivityElement) => foundActivityElement.copy(ancestorActivityElements = foundActivityElement.ancestorActivityElements :+ rootActivityElementOfGraph)
          }
          , tansitionRelation.frequencyOfImportance
        )
      }

      relationsWithCurrentPlaceActivityAsFromActivity match {
        // No relation found
        case Nil => rootActivityElementOfGraph.copy(descendantTransitions = rootActivityElementOfGraph.descendantTransitions :+ FinishTransitionElement)
        // Exact one relations found
        case currentRelation :: Nil =>
          rootActivityElementOfGraph.copy(
            descendantTransitions =
              rootActivityElementOfGraph.descendantTransitions :+ buildDescendantSingleTransitionElement(currentRelation._2)
          )
        // More than one relations found => Determine correct split transition
        case foundRelations =>

          /***
            *
            */
          def detectCorrectSplitTransitions(sourceRelations: List[Relation]): scala.collection.immutable.Seq[TwoWaySplitTransition] = {

            // Get all needed split relations, which must be checked and built as Xor or And Transition
            val splitRelationsAtoBandAtoC =
              (for {
                nextRelationAToB <- sourceRelations
                nextRelationAToC <- sourceRelations
              }
                // Create set, because a set has no order (means Set(1, 2) is the same as Set(2, 1))
                yield Set(nextRelationAToB, nextRelationAToC))
                // Convert to a set, because a set automatically has no duplicates and no order (means Set(1, 2) is the same as Set(2, 1, 3))
                .toSet
                // Filter, that only relation pairs will provided (this remove the Sets with the single relation elements
                .filter(_.size == 2)
                // Finally map the sets with the 2 elements to a tuple, that we have simpler handling later: (RelationAToB, nextRelationAToC)
                .map(setWithTwoRelations => (setWithTwoRelations.head, setWithTwoRelations.tail.head))

            // Start check and building
            (for {
              // Iterate through each relation pair, where we have to check on the split type
              (nextRelationAToB, nextRelationAToC) <- splitRelationsAtoBandAtoC

              // Store activity information regarding nextRelationAToB
              eventActivityIdA = nextRelationAToB.fromActivity.activityId
              eventActivityB = nextRelationAToB.toActivity
              eventActivityIdB = eventActivityB.activityId

              // Store activity information regarding nextRelationAToC
              eventActivityC = nextRelationAToC.toActivity
              eventActivityIdC = eventActivityC.activityId

              // Get frequencies of event followings
              frequencyOfEventBFollowsEventA = getFrequencyOfRelation((eventActivityIdA, eventActivityIdB), calculatedResultDependencyMatrix.fromToActivityIdsToRelationMapping)
              frequencyOfEventCFollowsEventB = getFrequencyOfRelation((eventActivityIdB, eventActivityIdC), calculatedResultDependencyMatrix.fromToActivityIdsToRelationMapping)
              frequencyOfEventBFollowsEventC = getFrequencyOfRelation((eventActivityIdC, eventActivityIdB), calculatedResultDependencyMatrix.fromToActivityIdsToRelationMapping)
              frequencyOfEventCFollowsEventA = getFrequencyOfRelation((eventActivityIdA, eventActivityIdC), calculatedResultDependencyMatrix.fromToActivityIdsToRelationMapping)
            }
              yield
                // Calculate split grade (see https://publications.jannikarndt.de/Genetic%20Mining%20und%20Heuristic%20Mining.pdf, page 7) to detect correct split transition
              calcSplitGradeBetweenThreeEvents(frequencyOfEventCFollowsEventB, frequencyOfEventBFollowsEventC, frequencyOfEventBFollowsEventA, frequencyOfEventCFollowsEventA) match {
                case splitGrade
                  // values <=0.1 point to a XOR relation between B and C
                  if splitGrade <= 0.1f =>
                  new XorTransitionElement(
                    leftDescendantTransition =
                      buildDescendantSingleTransitionElement(nextRelationAToB),
                    rightDescendantTransition =
                      buildDescendantSingleTransitionElement(nextRelationAToC)
                  )
                case splitGrade
                  // values >0.1 point to a AND relation between B and C
                  if splitGrade > 0.1f =>
                  new AndTransitionElement(
                    leftDescendantTransition =
                      buildDescendantSingleTransitionElement(nextRelationAToB),
                    rightDescendantTransition =
                      buildDescendantSingleTransitionElement(nextRelationAToC)
                  )
              }) (collection.breakOut)
          }

          rootActivityElementOfGraph.copy(
            descendantTransitions =
              rootActivityElementOfGraph.descendantTransitions
                ++
                detectCorrectSplitTransitions(foundRelations.map(_._2))
          )
      }
    }

    // 3. Build graph
    baseInfoToBuildProcessGraph.startActivities.toList match {
      case Nil =>
        // Take first event activity as start activity
        new ProcessGraph (
          // ToDo eigenes erstes Element einfügen, dass auf alle startActivities zeigt, dass wir einen Startpunkt im Graph haben und keine Parallelgraphen bauen und die syncen muessen
          rootElementWithDescendantGraphElements = buildDescendantTransitionsOfRootActivityElement (new ActivityElement (activityEntity = queueRecentObservedActivityContainers.last.activity, ancestorActivityElements = Seq(), descendantTransitions = Seq () ) )
        )
      case startActivity :: _ =>
        // Take first found start activity
        new ProcessGraph (
          // ToDo eigenes erstes Element einfügen, dass auf alle startActivities zeigt, dass wir einen Startpunkt im Graph haben und keine Parallelgraphen bauen und die syncen muessen
          rootElementWithDescendantGraphElements = buildDescendantTransitionsOfRootActivityElement (new ActivityElement (activityEntity = startActivity, ancestorActivityElements = Seq(), descendantTransitions = Seq () ) )
        )
      case _ =>
        new ProcessGraph(new ActivityElement(new Activity(activityId = 1, activityName = "empty"), ancestorActivityElements = Seq(), Seq(FinishTransitionElement)))
    }
  }

  /***
    * Gets/reads the frequency of a relation of two event activities based on the fromToActivityIdsToRelationMapping HashMap.
    * @param fromToActivityIdsToRelationMappingKey The key tuple (fromActivityId,toActivityId)
    * @param fromToActivityIdsToRelationMapping The mapping from (fromActivityId,toActivityId) to relation.
    * @return The read frequency of the relation.
    */
  private def getFrequencyOfRelation(fromToActivityIdsToRelationMappingKey: (Int, Int), fromToActivityIdsToRelationMapping: HashMap[(Int, Int), Relation]): Float =
    if (fromToActivityIdsToRelationMapping.contains(fromToActivityIdsToRelationMappingKey))
      fromToActivityIdsToRelationMapping(fromToActivityIdsToRelationMappingKey).frequencyOfImportance
    else
      0f

  /***
    * Calculates the dependency matrix based on the two input queues.
    * @param queueRecentObservedActivityContainers The queue with the recent observed activity containers.
    * @param queueRecentObservedSuccessionRelations The queue with the recent observed succession relations.
    * @return The created dependency matrix with information about the grade of dependency for each event relation.
    */
  private def calcDependencyMatrix(queueRecentObservedActivityContainers: Queue[ActivityContainer], queueRecentObservedSuccessionRelations: Queue[Relation]): DependencyMatrix = {
    // Create HashMap with mappings from activity (activityId) to corresponding coordinate in result matrix
    val activityIdToActivityMapping =
      queueRecentObservedActivityContainers
        .foldRight(HashMap[Int, Activity]())( (a, acc) => acc + (a.activity.activityId -> a.activity) )

    // Create HashMap based on relations with mappings from (fromActivityId,toActivityId) to relation, to optimize creation of result matrix in next step
    val fromToActivityIdsToRelationMapping =
      queueRecentObservedSuccessionRelations
        .foldRight(HashMap[(Int, Int), Relation]())( (r, acc) => acc + ((r.fromActivity.activityId, r.toActivity.activityId) -> r) )



    // Create matrix
    //val resultDependencyMatrix = Array.ofDim[Float](activityIdToActivityMapping.size, activityIdToActivityMapping.size)

    // Fill matrix and fill detected dependencies
    val resultDependencyMatrix =
      (for {
        activityIdRow <- activityIdToActivityMapping
        activityIdCol <- activityIdToActivityMapping

        // Matrix Row is "from-Event-Activity"
        fromEventActivityId = activityIdRow._1

        // Matrix Column is "to-Event-Activity"
        toEventActivityId = activityIdCol._1

        // Get frequency of event B follows event A
        frequencyOfEventBFollowsEventA = getFrequencyOfRelation((fromEventActivityId, toEventActivityId), fromToActivityIdsToRelationMapping)

        // Get frequency of event A follows event B
        frequencyOfEventAFollowsEventB = getFrequencyOfRelation((toEventActivityId, fromEventActivityId), fromToActivityIdsToRelationMapping)
      }
        // Set matrix field
        // Return matrix field
        yield
        {
          if (frequencyOfEventBFollowsEventA != 0f || frequencyOfEventAFollowsEventB != 0f)
          // If yes calculate the grade of dependency between the from/to-event and set as element in result matrix
            //(fromEventActivityId, toEventActivityId) -> calcDependencyGradeBetweenTwoEvents(frequencyOfEventBFollowsEventA, frequencyOfEventAFollowsEventB)
            new DependencyMatrixCoordinate(fromActivity = activityIdRow._2, toActivity = activityIdCol._2, value = calcDependencyGradeBetweenTwoEvents(frequencyOfEventBFollowsEventA, frequencyOfEventAFollowsEventB))
          else
          // If not, set 0 as element in result matrix (which means no dependency)
            //(fromEventActivityId, toEventActivityId) -> 0f
            new DependencyMatrixCoordinate(fromActivity = activityIdRow._2, toActivity = activityIdCol._2, value = 0f)
        }).to[collection.immutable.Seq]
        /*
        resultDependencyMatrix(activityIdToMatrixCoordinateRow._2)(activityIdToMatrixCoordinateCol._2) =
          // Check, whether relation exist, based on lookup in fromToActivityIdsToRelationMapping with key
          if (frequencyOfEventBFollowsEventA != 0f || frequencyOfEventAFollowsEventB != 0f)
            // If yes calculate the grade of dependency between the from/to-event and set as element in result matrix
            calcDependencyGradeBetweenTwoEvents(frequencyOfEventBFollowsEventA, frequencyOfEventAFollowsEventB)
          else
            // If not, set 0 as element in result matrix (which means no dependency)
            0f
            */


    // Return matrix and mapping
    new DependencyMatrix(resultDependencyMatrix, fromToActivityIdsToRelationMapping, activityIdToActivityMapping.values.to[collection.immutable.Set])
  }


  /***
    * Calculates the grade of dependency between two events.
    * @param frequencyOfEventBFollowsEventA Frequency that event B follows event A.
    * @param frequencyOfEventAFollowsEventB Frequency that event A follows event B.
    * @return The grade of dependency between two events (a value between 1 and -1: Values near 1 have a strong
    *         dependency, values near 0 have a strong independence, values near -1 show dependency in the reverse
    *         direction.
    */
  private def calcDependencyGradeBetweenTwoEvents(frequencyOfEventBFollowsEventA: Float,
                                                  frequencyOfEventAFollowsEventB: Float): Float =
    (frequencyOfEventBFollowsEventA - frequencyOfEventAFollowsEventB) / (frequencyOfEventBFollowsEventA + frequencyOfEventAFollowsEventB + 1)


  /***
    * Calculates the grade of split between three events.
    * @param frequencyOfEventCFollowsEventB Frequency that event C follows event B.
    * @param frequencyOfEventBFollowsEventC Frequency that event B follows event C.
    * @param frequencyOfEventBFollowsEventA Frequency that event B follows event A.
    * @param frequencyOfEventCFollowsEventA Frequency that event C follows event A.
    * @return The split grade between three events, here example with, check on a~>w(B follows C):
    *         (A value between 0 and 1: values <0.1 lead to a XOR relation between B and C,
    *         values >0.1 lead to a AND relation between B and C.
    */
  private def calcSplitGradeBetweenThreeEvents(frequencyOfEventCFollowsEventB: Float,
                                               frequencyOfEventBFollowsEventC: Float,
                                               frequencyOfEventBFollowsEventA: Float,
                                               frequencyOfEventCFollowsEventA: Float): Float =
    (frequencyOfEventCFollowsEventB + frequencyOfEventBFollowsEventC) / (frequencyOfEventBFollowsEventA + frequencyOfEventCFollowsEventA + 1)

  def calcSplits(dependencyMatrix: DependencyMatrix) = {
    // Find Relations, where we have to check on, which split type
    dependencyMatrix
  }

  /***
    *
    * @param activityToSearch
    * @param startSearchActivityElementOfGraph
    * @return
    */
  def findActivityElementInDescendantTransitionsOfProcessGraph(activityToSearch: Activity, startSearchActivityElementOfGraph: ActivityElement): Option[ActivityElement] = {
    startSearchActivityElementOfGraph.descendantTransitions match {
      // No descendant found
      case Nil => None
      // One or more descendants found
      case descendantTransitions =>
        (for (descendantTransition <- descendantTransitions)
          yield {
            descendantTransition match {
              // Not found => Return None
              case FinishTransitionElement => None
              // If found in single transition return found element or search further in descendants
              case SingleTransitionElement(descendantActivityElement, _) =>
                if (descendantActivityElement.activityEntity.activityId == activityToSearch.activityId)
                  Some(descendantActivityElement)
                else
                  findActivityElementInDescendantTransitionsOfProcessGraph(activityToSearch, descendantActivityElement)
              // If found in TwoWy Split transition search in lef and right side
              case twoWaySplitTrans: TwoWaySplitTransition =>
                if (twoWaySplitTrans.leftDescendantTransition.descendantActivityElement.activityEntity.activityId == activityToSearch.activityId)
                  return Some(twoWaySplitTrans.leftDescendantTransition.descendantActivityElement)
                else if (twoWaySplitTrans.rightDescendantTransition.descendantActivityElement.activityEntity.activityId == activityToSearch.activityId)
                  return Some(twoWaySplitTrans.rightDescendantTransition.descendantActivityElement)
                else
                // Search in leftDescendantElement
                  findActivityElementInDescendantTransitionsOfProcessGraph(activityToSearch, twoWaySplitTrans.leftDescendantTransition.descendantActivityElement) match {
                    // If not found in leftDescendantElement search in rightDescendantElement
                    case None => return findActivityElementInDescendantTransitionsOfProcessGraph(activityToSearch, twoWaySplitTrans.rightDescendantTransition.descendantActivityElement)
                    // if found in leftDescendantElement return found element
                    case foundElement => return foundElement
                  }
            }
            // Aggregate results from each descendant search => Means store the first found ActivityElement in accumulator and return that (should be found only one element)
          }).foldLeft[Option[ActivityElement]](None)(
          (accumulatedResult: Option[ActivityElement], nextResult: Option[ActivityElement]) =>
            accumulatedResult match {
              case Some(_) => accumulatedResult
              case None => nextResult match {
                case None => accumulatedResult
                case Some(_) => nextResult
              }
            })
    }
  }

}




//    new ProcessGraph(
//      rootElementsWithDescendantGraphElements =
//        (
//          for (startActivity <- baseInfoToBuildProcessGraph.startActivities)
//            yield new ActivityElement(activityEntity = startActivity, descendantTransitions = findDescendantTransitions(new ActivityElement(startActivity, Seq())))
//          ).to[scala.collection.immutable.Seq]

//    @tailrec
//    def findPredecessorActivityElements(
//                                         currentPlaceActivityElement: ActivityElement,
//                                         furtherActivityElementsOnSameLayer: scala.collection.immutable.Seq[ActivityElement]) //,
//                                         //availableRelations: List[((Int, Int), Relation)])
//                                         //currentRootActivityElements: scala.collection.immutable.Seq[ActivityElement])
//    : scala.collection.immutable.Seq[ActivityElement] = {
//
//      // Find all relations, which have the currentPlaceActivityElement activity as to-activity
//      val relationsWithCurrentPlaceActivityAsToActivity =
//        baseInfoToBuildProcessGraph.validRelations.toList.filter { case ((_, toActivity), _) => toActivity == currentPlaceActivityElement.activityEntity.activityId }
//        //availableRelations.filter { case ((_, toActivity), _) => toActivity == currentPlaceActivityElement.activityEntity.activityId }
//
//      relationsWithCurrentPlaceActivityAsToActivity match {
//        // No relation found => At a start activity arrived
//        case Nil =>
//          furtherActivityElementsOnSameLayer match {
//            case Nil => Seq (currentPlaceActivityElement)
//            case nextActivityElement :: furtherActivityElements =>
//              findPredecessorActivityElements(nextActivityElement, furtherActivityElements) //, List()) //, currentPlaceActivityElement +: currentRootActivityElements)
//          }
//        // One remaining relation found
//        case nextRelation :: Nil =>
//          furtherActivityElementsOnSameLayer match {
//            case Nil =>
//              findPredecessorActivityElements(
//                new ActivityElement(
//                  activityEntity = nextRelation._2.fromActivity
//                  , descendantTransitions = Seq(
//                    new SingleTransitionElement(
//                        currentPlaceActivityElement
//                      , nextRelation._2.frequencyOfImportance)
//                  )
//                )
//                , Seq()
//              ) //, baseInfoToBuildProcessGraph.validRelations.toList
//            case furtherActivityElements => //nextActivityElement :: furtherActivityElements =>
//              findPredecessorActivityElements(
//                new ActivityElement(
//                  activityEntity = nextRelation._2.fromActivity
//                  , descendantTransitions = Seq(
//                    new SingleTransitionElement(
//                      currentPlaceActivityElement
//                      , nextRelation._2.frequencyOfImportance)
//                  )
//                )
//                , furtherActivityElementsOnSameLayer) //, List(nextRelation))
//          }
//        // relations found => ToDo Check on split relations
//        case firstRelation :: furtherRelevantRelations =>
//          furtherActivityElementsOnSameLayer match {
//            case Nil =>
//              findPredecessorActivityElements(
//                new ActivityElement(
//                  activityEntity = firstRelation._2.fromActivity
//                  , descendantTransitions = Seq(
//                    new SingleTransitionElement(
//                      currentPlaceActivityElement
//                      , firstRelation._2.frequencyOfImportance)
//                  )
//                )
//                , for (nextRelation <- furtherRelevantRelations)
//                    yield
//                      //findPredecessorActivityElements(
//                        new ActivityElement(
//                          activityEntity = nextRelation._2.fromActivity
//                          , descendantTransitions = Seq(
//                            new SingleTransitionElement(
//                              currentPlaceActivityElement
//                              , nextRelation._2.frequencyOfImportance)
//                          )
//                        )
//                //, baseInfoToBuildProcessGraph.validRelations.toList
//              )
//
//
//
////              findPredecessorActivityElements(
////                currentPlaceActivityElement
////                , Seq(
////                  new ActivityElement(
////                    activityEntity = firstRelation._2.fromActivity
////                    , descendantTransitions = Seq(
////                      new SingleTransitionElement(
////                        currentPlaceActivityElement
////                        , firstRelation._2.frequencyOfImportance)
////                    )
////                  )
////                )
////                , furtherRelevantRelations
////              )
////              findPredecessorActivityElements(
////                new ActivityElement(
////                  activityEntity = firstRelation._2.fromActivity
////                  , descendantTransitions = Seq(
////                    new SingleTransitionElement(
////                      currentPlaceActivityElement
////                      , firstRelation._2.frequencyOfImportance)
////                  )
////                )
////                // Build further activity elements on same layer
////                , for (nextRelation <- furtherRelevantRelations)
////                  yield
////                    //findPredecessorActivityElements(
////                      new ActivityElement(
////                        activityEntity = nextRelation._2.fromActivity
////                        , descendantTransitions = Seq(
////                          new SingleTransitionElement(
////                            currentPlaceActivityElement
////                            , nextRelation._2.frequencyOfImportance)
////                        )
////                      )
////                , baseInfoToBuildProcessGraph.validRelations.toList
////              )
//            case nextActivityElement :: furtherActivityElements =>
//              findPredecessorActivityElements(nextActivityElement, furtherActivityElements) //, relationsWithCurrentPlaceActivityAsToActivity)
//          }
//      }
//    }


// Exact one relation found
//        case oneExistingRelation :: Nil =>
//          findPredecessorActivityElements(
//            new ActivityElement(
//              activityEntity = oneExistingRelation._2.fromActivity
//              , descendantTransitions = Seq(
//                new SingleTransitionElement(
//                  currentPlaceActivityElement
//                  , oneExistingRelation._2.frequencyOfImportance)
//              )
//            )
//          )



// 3.) Start with final activities and create Process graph recursively till start activity elements with findPredecessorActivityElements
// Create one final activity element to which all final activities show, that we have one parsing starting point
//    val finiteActivity = new Activity(activityId = 99999, activityName = "END")
//    val finiteActivityElement = new ActivityElement(finiteActivity, Seq(FinishTransitionElement))
//    val finalBaseInfoToBuildProcessGraph = baseInfoToBuildProcessGraph.copy(validRelations = baseInfoToBuildProcessGraph.validRelations)
//      validRelations = initializedBaseInfoToBuildProcessGraph.validRelations,
//      validActivities = initializedBaseInfoToBuildProcessGraph.validActivities,
//      // Each start activity must also exist in validActivities (final filtering/join)
//      startActivities = initializedBaseInfoToBuildProcessGraph.startActivities.intersect(initializedBaseInfoToBuildProcessGraph.validActivities),
//      // Each final activity must also exist in validActivities (final filtering/join)
//      finalActivities = initializedBaseInfoToBuildProcessGraph.finalActivities.intersect(initializedBaseInfoToBuildProcessGraph.validActivities),
//      activitiesWithSplitRelations = initializedBaseInfoToBuildProcessGraph.activitiesWithSplitRelations
//    )

//    new ProcessGraph(
//      rootElementsWithDescendantGraphElements = findPredecessorActivityElements(finiteActivityElement)
//    )

//    new ProcessGraph(
//      rootElementsWithDescendantGraphElements =
//        baseInfoToBuildProcessGraph.finalActivities.to[collection.immutable.Seq]
//          .flatMap(finalActivity => findPredecessorActivityElements(new ActivityElement(finalActivity, Seq(FinishTransitionElement)), Seq())) //, baseInfoToBuildProcessGraph.validRelations.toList))
//    )




////***************************************************************





//def parseResultProcessGraph(currentPlaceActivity: ActivityElement, currentStateProcessGraph: ProcessGraph, baseInfoToBuildProcessGraph: ProcessGraphBase): ProcessGraph = {
//    def findPredecessorTransitions(currentPlaceActivity: Activity): scala.collection.immutable.Seq[TransitionElement] = {
////      val relationsWithCurrentPlaceActivityAsStartActivity =
////        baseInfoToBuildProcessGraph.validRelations.filter{ case ((fromActivityId, _), _) => fromActivityId == currentPlaceActivity.activityId }
//
//      @tailrec
//      def findPredecessorTransitionsAcc(currentPlaceActivity: Activity, acc: scala.collection.immutable.Seq[TransitionElement]): scala.collection.immutable.Seq[TransitionElement] = {
//        baseInfoToBuildProcessGraph.validRelations.filter{ case ((fromActivityId, _), _) => fromActivityId == currentPlaceActivity.activityId }.toList match {
//          // No relation found
//          case Nil => Seq(FinishTransitionElement)
//          // Exact one relation found
//          case oneExistingRelation::Nil =>
//            findPredecessorTransitionsAcc(
//              oneExistingRelation._2.toActivity
//              , Seq(
//                new SingleTransitionElement(
//                  new ActivityElement(
//                    oneExistingRelation._2.toActivity,
//                    acc)
//                  , oneExistingRelation._2.frequencyOfImportance)
//              )
//            )
//          // More than one relations found
//          //case oneExistingRelation::furtherRelations => Seq(FinishTransitionElement)
//          case furtherRelations => Seq(FinishTransitionElement)
//        }
//      }
//
//      findPredecessorTransitionsAcc(currentPlaceActivity, Seq())
//
//
////      //currentPlaceActivity.descendantTransition =
////      relationsWithCurrentPlaceActivityAsStartActivity.toList match {
////          // No relation found
////        case Nil => Seq(FinishTransitionElement)
////          // Exact one relation found
////        case oneExistingRelation::Nil =>
////          Seq(
////            new SingleTransitionElement(
////              new ActivityElement(
////                oneExistingRelation._2.toActivity,
////                findDescendantTransitions(oneExistingRelation._2.toActivity))
////              , oneExistingRelation._2.frequencyOfImportance)
////          )
////        // More than one relations found
////        //case oneExistingRelation::furtherRelations => Seq(FinishTransitionElement)
////        case furtherRelations => Seq(FinishTransitionElement)
////      }
//    }

// 3.) Start to build the process graph
// Start with the start activities
//    new ProcessGraph(
//      (for (startActivity <- baseInfoToBuildProcessGraph.startActivities) yield new ActivityElement(startActivity, findDescendantTransitions(startActivity))).to[collection.immutable.Seq]
//    )