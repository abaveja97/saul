package edu.illinois.cs.cogcomp.saulexamples.nlp.SemanticRoleLabeling

import edu.illinois.cs.cogcomp.core.datastructures.textannotation.{ Constituent, Relation }
import edu.illinois.cs.cogcomp.lbjava.learn.SparseAveragedPerceptron
import edu.illinois.cs.cogcomp.saul.classifier.{ Learnable, SparseNetworkLBP }
import edu.illinois.cs.cogcomp.saul.datamodel.property.Property

/** Created by Parisa on 12/30/15.
  */
object SRLClassifiers {
  import SRLApps.srlDataModelObject._
  //TODO This needs to be overriden by the user; change it to be dynamic
  val parameters = new SparseAveragedPerceptron.Parameters()
  object predicateClassifier extends Learnable[Constituent](predicates, parameters) {

    //TODO These are not used during Learner's initialization
    def label: Property[Constituent] = isPredicateGold
    override def feature = using(posTag, subcategorization, phraseType, headword, voice, verbClass, predPOSWindow, predWordWindow)
    override lazy val classifier = new SparseNetworkLBP
  }
  //This classifier has not been used in our current models
  object predicateSenseClassifier extends Learnable[Constituent](predicates, parameters) {
    def label = predicateSenseGold
    override lazy val classifier = new SparseNetworkLBP
  }

  object argumentTypeLearner extends Learnable[Relation](relations, parameters) {
    def label = argumentLabelGold
    override def feature = using(containsMOD, containsNEG, clauseFeatures, chunkPathPattern, chunkEmbedding, chunkLength,
      constituentLength, argPOSWindow, argWordWindow, headwordRelation, syntacticFrameRelation, pathRelation,
      phraseTypeRelation, predPosTag, predLemmaR, linearPosition)
    override lazy val classifier = new SparseNetworkLBP
  }

  object argumentXuIdentifierGivenApredicate extends Learnable[Relation](relations, parameters) {

    def label = isArgumentXuGold
    override def feature = using(headwordRelation, syntacticFrameRelation, pathRelation,
      phraseTypeRelation, predPosTag, predLemmaR, linearPosition, argWordWindow, argPOSWindow,
      constituentLength, chunkLength, chunkEmbedding, chunkPathPattern, clauseFeatures, containsNEG, containsMOD)
    override lazy val classifier = new SparseNetworkLBP
  }

}

