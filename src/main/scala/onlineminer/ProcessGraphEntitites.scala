package onlineminer

/*
sealed trait TransitionType
case object Single extends TransitionType
sealed trait TwoWaySplit extends TransitionType
case object And extends TwoWaySplit
case object Xor extends TwoWaySplit
*/

/*******************************************************************************************************************
  * Domain model with algebraic data types for a complete output process graph, produced by an online process miner.
  ******************************************************************************************************************/

final case class ProcessGraph(rootElementWithDescendantGraphElements: ActivityElement)

sealed trait ProcessGraphElement
sealed trait TransitionElement extends ProcessGraphElement
final case class SingleTransitionElement(descendantActivityElement: ActivityElement, frequencyOfImportance: Float) extends TransitionElement
//sealed trait TwoWaySplitTransitionDescendant {
//  def frequencyOfImportance: Float
//}
//sealed trait TwoWaySplitTransitionElement extends TransitionElement with TwoWaySplitTransitionDescendant {
//  def leftDescendantElement: TwoWaySplitTransitionDescendant
//  def rightDescendantElement: TwoWaySplitTransitionDescendant
//}
sealed trait TwoWaySplitTransition extends TransitionElement {
  def leftDescendantTransition: SingleTransitionElement
  def rightDescendantTransition: SingleTransitionElement
}

final case class AndTransitionElement(leftDescendantTransition: SingleTransitionElement, rightDescendantTransition: SingleTransitionElement) extends TwoWaySplitTransition //, frequencyOfImportance: Float) extends TwoWaySplitTransitionElement
final case class XorTransitionElement(leftDescendantTransition: SingleTransitionElement, rightDescendantTransition: SingleTransitionElement) extends TwoWaySplitTransition //, frequencyOfImportance: Float) //extends TwoWaySplitTransitionElement
final case class ActivityElement(activityEntity: Activity, ancestorActivityElements: scala.collection.immutable.Seq[ActivityElement], descendantTransitions: scala.collection.immutable.Seq[TransitionElement]) extends ProcessGraphElement
//final case class ActivityTwoWaySplitDescendant(descendantActivity: ActivityElement, frequencyOfImportance: Float) extends TwoWaySplitTransitionDescendant
case object FinishTransitionElement extends TransitionElement