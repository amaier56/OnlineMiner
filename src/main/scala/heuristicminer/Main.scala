package heuristicminer

import scala.collection.immutable.Queue
import collection.immutable.Seq
import onlineminer._

object Main extends App {


  val eventsRoot: Seq[Event] = Seq(
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 1,
        activityName = "Start process"
       // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1
    ),
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 2,
        activityName = "Step 1"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 2
    ),
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 3,
        activityName = "Step 2"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 3
    ),
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 2,
        activityName = "Step 1"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 4
    ),
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 3,
        activityName = "Step 2"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 5
    ),
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 2,
        activityName = "Step 1"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 6
    ),
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 3,
        activityName = "Step 2"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 7
    ),
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 3,
        activityName = "Step 2"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 8
    ),
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 2,
        activityName = "Step 1"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 9
    ),
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 2,
        activityName = "Step 1"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 10
    )
  )



  val events: Seq[Event] = Seq(
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 1,
        activityName = "Start process"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1
    ),
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 2,
        activityName = "Step 1"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 2
    ),
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 3,
        activityName = "Step 2"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 3
    ),
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 5,
        activityName = "Step 4"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 4
    ),
    new Event(
      caseId = 2,
      activity = new Activity(
        activityId = 1,
        activityName = "Start process"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1
    ),
    new Event(
      caseId = 2,
      activity = new Activity(
        activityId = 2,
        activityName = "Step 1"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 2
    ),
    new Event(
      caseId = 2,
      activity = new Activity(
        activityId = 3,
        activityName = "Step 2"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 3
    ),
    new Event(
      caseId = 2,
      activity = new Activity(
        activityId = 5,
        activityName = "Step 4"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 4
    )
    ,
    new Event(
      caseId = 3,
      activity = new Activity(
        activityId = 1,
        activityName = "Start process"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1
    ),
    new Event(
      caseId = 3,
      activity = new Activity(
        activityId = 2,
        activityName = "Step 1"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 2
    ),
    new Event(
      caseId = 3,
      activity = new Activity(
        activityId = 4,
        activityName = "Step 3"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 3
    ),
    new Event(
      caseId = 3,
      activity = new Activity(
        activityId = 5,
        activityName = "Step 4"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 4
    )
  )


  val eventsWithOneEvent: Seq[Event] = Seq(
    new Event(
      caseId = 3,
      activity = new Activity(
        activityId = 4,
        activityName = "Step 3"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 3
    ),
    new Event(
      caseId = 3,
      activity = new Activity(
        activityId = 5,
        activityName = "Step 4"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 4
    )
  )

  val eventsWithOneSimu: Seq[Event] = Seq(
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 1,
        activityName = "register request"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1293703320000L
    ),
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 2,
        activityName = "examine thoroughly"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1293786360000L
    ),
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 3,
        activityName = "check ticket"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1294236720000L
    )
  )

  val eventsCycle: Seq[Event] = Seq(
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 1,
        activityName = "register request"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1293703320000L
    ),
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 2,
        activityName = "examine thoroughly"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1293786360000L
    ),
    new Event(
      caseId = 2,
      activity = new Activity(
        activityId = 1,
        activityName = "register request"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1293703370000L
    ),
    new Event(
      caseId = 2,
      activity = new Activity(
        activityId = 2,
        activityName = "examine thoroughly"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1293786380000L
    ),
    new Event(
      caseId = 2,
      activity = new Activity(
        activityId = 1,
        activityName = "register request"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1293786390000L
    ),
    new Event(
      caseId = 3,
      activity = new Activity(
        activityId = 2,
        activityName = "examine thoroughly"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1293786400000L
    ),
    new Event(
      caseId = 3,
      activity = new Activity(
        activityId = 1,
        activityName = "register request"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1293786410000L
    ),
    new Event(
      caseId = 4,
      activity = new Activity(
        activityId = 2,
        activityName = "examine thoroughly"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1293786420000L
    ),
    new Event(
      caseId = 4,
      activity = new Activity(
        activityId = 1,
        activityName = "register request"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1293786430000L
    ),
    new Event(
      caseId = 5,
      activity = new Activity(
        activityId = 2,
        activityName = "examine thoroughly"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1293786440000L
    ),
    new Event(
      caseId = 5,
      activity = new Activity(
        activityId = 1,
        activityName = "register request"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1293786450000L
    )
  )


  val eventsStackoverflowTest: Seq[Event] = Seq(
    new Event(
      caseId = 1,
      activity = new Activity(
        activityId = 1,
        activityName = "register request"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1293703320000L
    ),
    new Event(
      caseId = 4,
      activity = new Activity(
        activityId = 5,
        activityName = "reject request"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1294843440000L
    ),
    new Event(
      caseId = 3,
      activity = new Activity(
        activityId = 7,
        activityName = "pay compensation"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1295084700000L
    ),
    new Event(
      caseId = 2,
      activity = new Activity(
        activityId = 7,
        activityName = "pay compensation"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1294484700000L
    ),
    new Event(
      caseId = 5,
      activity = new Activity(
        activityId = 1,
        activityName = "register request"
        // degreeOfImportanceWeight =  12
        //payloadData = "Testpayload"
      )
      , emissionEventTimestamp = 1294300920000L
    )
  )

  //val f = findMostRecentElementInQueue[Event](queueRecentObservedEventPerCase, e => e.activity.activityId == 3)

  // Stream
  //while (true) {
    // Get new event from stream

  val b = Set(1,2)
  val a= Set(2, 1, 1)
  val c = a == b

  val onlineQueueMgr = new OnlineMiningQueueManager()

  val (aa, ee, rr) = onlineQueueMgr.updateMiningQueuesWithEvents(events, Queue(), Queue(), Queue())
  val onlineHeurMinerFirst = new OnlineHeuristicMiner(detectAsDependencyThreshold = 0.2f)
  val firstGraph = onlineHeurMinerFirst.mineProcessGraph(aa,rr)

  val (aa1, ee1, rr1) = onlineQueueMgr.updateMiningQueuesWithEvents(events, aa, ee, rr)
  //val (aa2, ee2, rr2) = onlineQueueMgr.updateMiningQueuesWithEvents(events, aa1, ee1, rr1)

  val onlineHeurMiner = new OnlineHeuristicMiner(detectAsDependencyThreshold = 0.2f)
  val graph = onlineHeurMiner.mineProcessGraph(aa1,rr1)

  println("fertig")
  //println(contains(singletonSet(1), 1))
}

