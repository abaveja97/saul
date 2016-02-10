package edu.illinois.cs.cogcomp.saul.classifier

import java.io.File
import java.net.URL

import edu.illinois.cs.cogcomp.core.io.IOUtils
import edu.illinois.cs.cogcomp.lbjava.classify.{ FeatureVector, TestDiscrete }
import edu.illinois.cs.cogcomp.lbjava.learn.Learner.Parameters
import edu.illinois.cs.cogcomp.lbjava.learn._
import edu.illinois.cs.cogcomp.lbjava.parse.Parser
import edu.illinois.cs.cogcomp.lbjava.util.ExceptionlessOutputStream
import edu.illinois.cs.cogcomp.saul.TestContinuous
import edu.illinois.cs.cogcomp.saul.datamodel.DataModel
import edu.illinois.cs.cogcomp.saul.datamodel.property.{ CombinedDiscreteProperty, Property, PropertyWithWindow, RelationalFeature }
import edu.illinois.cs.cogcomp.saul.lbjrelated.LBJLearnerEquivalent
import edu.illinois.cs.cogcomp.saul.parser.LBJIteratorParserScala
import org.slf4j.{ Logger, LoggerFactory }

import scala.reflect.ClassTag

abstract class Learnable[T <: AnyRef](val datamodel: DataModel, val parameters: Parameters = new Learner.Parameters)(implicit tag: ClassTag[T]) extends LBJLearnerEquivalent {
  /** Whether to use caching */
  val useCache = false

  val logging = true
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  var isTraining = false

  val targetNode = datamodel.getNodeWithType[T]

  def fromData = targetNode.getTrainingInstances

  def getClassNameForClassifier = this.getClass.getCanonicalName

  def feature: List[Property[T]] = datamodel.getPropertiesForType[T]

  /** filter out the label from the features */
  def combinedProperties = if (label != null) new CombinedDiscreteProperty[T](this.feature.filterNot(_.name == label.name))
  else new CombinedDiscreteProperty[T](this.feature)

  def lbpFeatures = combinedProperties.classifier

  /** classifier need to be defined by the user */
  val classifier: Learner

  /** syntactic suger to create simple calls to the function */
  def apply(example: AnyRef): String = classifier.discreteValue(example: AnyRef)

  /** specifications of the classifier and its model files  */
  classifier.setReadLexiconOnDemand()
  var modelDir = "models" + File.separator
  var lcFilePath = new URL(new URL("file:"), modelDir + getClassNameForClassifier + ".lc")
  var lexFilePath = new URL(new URL("file:"), modelDir + getClassNameForClassifier + ".lex")
  classifier.setModelLocation(lcFilePath)
  classifier.setLexiconLocation(lexFilePath)

  def setExtractor(): Unit = {
    if (feature != null) {
      if (logging)
        logger.info("Setting the feature extractors to be {}", lbpFeatures.getCompositeChildren)
      classifier.setExtractor(lbpFeatures)
    } else {
      logger.warn("Warning: no features found!")
    }
  }

  def setLabeler(): Unit = {
    if (label != null) {
      val oracle = Property.entitiesToLBJFeature(label)
      if (logging) {
        logger.info("Setting the labeler to be '{}", oracle)
      }
      classifier.setLabeler(oracle)
    }
  }

  // set parameters for classifier
  setExtractor()
  setLabeler()

  def setModelDir(directory: String) = {
    classifier.setReadLexiconOnDemand()
    modelDir = directory + File.separator
    lcFilePath = new URL(new URL("file:"), modelDir + getClassNameForClassifier + ".lc")
    lexFilePath = new URL(new URL("file:"), modelDir + getClassNameForClassifier + ".lex")
    classifier.setModelLocation(lcFilePath)
    classifier.setLexiconLocation(lexFilePath)
  }

  def removeModelFiles(): Unit = {
    IOUtils.rm(lcFilePath.getPath)
    IOUtils.rm(lexFilePath.getPath)
    createFiles()
  }

  def save(): Unit = {
    removeModelFiles()
    val dummyClassifier = new SparseNetworkLearner
    classifier.setExtractor(dummyClassifier)
    classifier.setLabeler(dummyClassifier)
    classifier.save()

    // after saving, get rid of the dummyClassifier in the classifier.
    setExtractor()
    setLabeler()
  }

  def createFiles(): Unit = {
    // create the model directory if it does not exist
    IOUtils.mkdir(modelDir)
    // create .lex file if it does not exist
    if (!IOUtils.exists(lexFilePath.getPath)) {
      val lexFile = ExceptionlessOutputStream.openCompressedStream(lexFilePath)
      if (classifier.getCurrentLexicon == null) lexFile.writeInt(0)
      else classifier.getCurrentLexicon.write(lexFile)
      lexFile.close()
    }

    // create .lc file if it does not exist
    if (!IOUtils.exists(lcFilePath.getPath)) {
      val lcFile = ExceptionlessOutputStream.openCompressedStream(lcFilePath)
      classifier.write(lcFile)
      lcFile.close()
    }
  }

  /** Loads the model and lexicon for the classifier. Looks up in the local file system
    * and the files are not found, looks up in the classpath JARs.
    * @param lcFile The path of the model file
    * @param lexFile The path of the lexicon file
    */
  def load(lcFile: String, lexFile: String): Unit = {
    if (IOUtils.exists(lcFile))
      classifier.readModel(lcFile)
    else {
      val modelResourcesUrls = IOUtils.lsResources(getClass, lcFile)
      if (modelResourcesUrls.size() == 1)
        classifier.readModel(modelResourcesUrls.get(0))
      else logger.error("Cannot find model file: {}", lcFile)
    }
    if (IOUtils.exists(lcFile))
      classifier.readLexicon(lexFile)
    else {
      val lexiconResourcesUrls = IOUtils.lsResources(getClass, lexFile)
      if (lexiconResourcesUrls.size() == 1)
        classifier.readLexicon(lexiconResourcesUrls.get(0))
      else logger.error("Cannot find lexicon file {}", lexFile)
    }
  }

  def load(lcFile: URL, lexFile: URL): Unit = {
    load(lcFile.getPath, lexFile.getPath)
  }

  def load(): Unit = {
    load(lcFilePath.getPath, lexFilePath.getPath)
  }

  def learn(iteration: Int, filePath: String = datamodel.defaultDIFilePath): Unit = {
    createFiles()
    isTraining = true
    if (useCache) {
      if (!datamodel.hasDerivedInstances) {
        if (new File(filePath).exists()) {
          datamodel.load(filePath)
        } else {
          datamodel.deriveInstances()
          datamodel.write(filePath)
        }
      }
      learnWithDerivedInstances(iteration, targetNode.derivedInstances.values)
    } else {
      learn(iteration, this.fromData)
    }
    isTraining = false
  }

  def learn(iteration: Int, data: Iterable[T]): Unit = {
    createFiles()
    if (logging)
      logger.info("Learnable: Learn with data of size {}", data.size)
    isTraining = true
    val crTokenTest = new LBJIteratorParserScala[T](data)
    crTokenTest.reset()

    def learnAll(crTokenTest: Parser, remainingIteration: Int): Unit = {
      val v = crTokenTest.next
      if (v == null) {
        if (logging & remainingIteration % 10 == 0)
          logger.info("Training: {} iterations remain.", remainingIteration)

        if (remainingIteration > 1) {
          crTokenTest.reset()
          datamodel.clearPropertyCache()
          learnAll(crTokenTest, remainingIteration - 1)
        }
      } else {
        classifier.learn(v)
        learnAll(crTokenTest, remainingIteration)
      }
    }

    learnAll(crTokenTest, iteration)
    classifier.doneLearning()
    isTraining = false
  }

  def learnWithDerivedInstances(numIterations: Int, featureVectors: Iterable[FeatureVector]): Unit = {
    isTraining = true
    val propertyNameSet = feature.map(_.name).toSet
    (0 until numIterations).foreach { _ =>
      featureVectors.foreach {
        fullFeatureVector =>
          val featureVector = new FeatureVector()
          val numFeatures = fullFeatureVector.size()
          (0 until numFeatures).foreach {
            featureIndex =>
              val feature = fullFeatureVector.getFeature(featureIndex)
              val propertyName = feature.getGeneratingClassifier
              if (label != null && label.name.equals(propertyName)) {
                featureVector.addLabel(feature)
              } else if (propertyNameSet.contains(propertyName)) {
                featureVector.addFeature(feature)
              }
          }
          classifier.learn(featureVector)
      }
    }
    classifier.doneLearning()
    isTraining = false
  }

  def forget() = this.classifier.forget()

  /** Test with given data, use internally
    *
    * @param testData The data to test on
    * @return List of (label, (f1, precision, recall))
    */
  def test(testData: Iterable[T] = null, prediction: Property[T] = null, groundTruth: Property[T] = null, exclude: String = ""): List[(String, (Double, Double, Double))] = {
    isTraining = false
    val testReader = new LBJIteratorParserScala[T](if (testData == null) this.datamodel.getNodeWithType[T].getTestingInstances else testData)
    testReader.reset()
    val tester = if (prediction == null && groundTruth == null)
      TestDiscrete.testDiscrete(classifier, classifier.getLabeler, testReader)
    else
      TestDiscrete.testDiscrete(prediction.classifier, groundTruth.classifier, testReader)
    if (!exclude.isEmpty) {
      tester.addNull(exclude)
    }
    tester.printPerformance(System.out)
    val ret = tester.getLabels.map { label => (label, (tester.getF1(label), tester.getPrecision(label), tester.getRecall(label))) }
    ret.toList
  }
  def testContinuos(): Unit = {
    isTraining = false
    val data = this.datamodel.getNodeWithType[T].getTestingInstances
    testContinuos(data)
  }

  def testContinuos(testData: Iterable[T]): Unit = {
    isTraining = false
    println()
    val testReader = new LBJIteratorParserScala[T](testData)
    testReader.reset()
    val tester = new TestContinuous(classifier, classifier.getLabeler, testReader)
    // tester.printPerformance(System.out)
    //val ret = tester.getLabels.map({
    // label => (label , (tester.getF1(label),tester.getPrecision(label),tester.getRecall(label)))
    // })

    //ret toList
  }

  def chunkData(ts: List[Iterable[T]], i: Int, curr: Int, acc: (Iterable[T], Iterable[T])): (Iterable[T], Iterable[T]) = {
    ts match {
      case head :: more =>
        acc match {
          case (train, test) =>
            if (i == curr) {
              // we found the test part
              chunkData(more, i, curr + 1, (train, head))
            } else {
              chunkData(more, i, curr + 1, (head ++ train, test))
            }
        }
      case Nil => acc
    }
  }

  /** Run k fold cross validation. */
  def crossValidation(k: Int) = {
    val allData = this.fromData

    if (logging)
      logger.info("Running cross validation on {} data", allData.size)

    val groupSize = Math.ceil(allData.size / k).toInt
    val groups = allData.grouped(groupSize).toList

    val loops = if (k == 1) {
      (groups.head, Nil) :: Nil
    } else {
      (0 until k) map {
        i => chunkData(groups, i, 0, (Nil, Nil))
      }
    }

    def printTestResult(result: (String, (Double, Double, Double))): Unit = {
      result match {
        case (label, (f1, precision, recall)) =>
          println(s"  $label    $f1    $precision     $recall   ")
      }
    }

    val results = loops.zipWithIndex map {
      case ((trainingSet, testingSet), idx) =>
        logger.info("Running fold {}", idx)
        logger.info("Learn with {}", trainingSet.size)
        logger.info("Test with {}", testingSet.size)

        this.classifier.forget()
        this.learn(10, trainingSet)
        val testResult = this.test(testingSet)
        testResult
    }

    def sumTuple(a: (Double, Double, Double), b: (Double, Double, Double)): (Double, Double, Double) = {
      (a._1 + b._1, a._2 + b._2, a._3 + b._3)
    }

    def avgTuple(a: (Double, Double, Double), size: Int): (Double, Double, Double) = {
      (a._1 / size, a._2 / size, a._3 / size)
    }

    println("  label    f1    precision     recall   ")

    results.flatten.toList.groupBy({
      tu: (String, (Double, Double, Double)) => tu._1
    }).foreach({
      case (label, l) =>
        val t = l.length
        val avg = avgTuple(l.map(_._2).reduce(sumTuple), t)

        printTestResult((label, avg))
    })
  }

  /** Label property foor users classifier */
  def label: Property[T]

  /** A windows of properties
    *
    * @param before always negative (or 0)
    * @param after always positive (or 0)
    */
  def windowWithIn[U <: AnyRef](before: Int, after: Int, properties: List[Property[T]])(implicit uTag: ClassTag[U]): Property[T] = {
    val fls = datamodel.getRelatedFieldsBetween[T, U]
    getWindowWithFilters(before, after, fls.map(e => (t: T) => e.neighborsOf(t).head), properties)
  }

  def window(before: Int, after: Int)(properties: List[Property[T]]): Property[T] = {
    getWindowWithFilters(before, after, Nil, properties)
  }

  private def getWindowWithFilters(before: Int, after: Int, filters: Iterable[T => Any], properties: List[Property[T]]): Property[T] = {
    new PropertyWithWindow[T](this.datamodel, before, after, filters, properties)
  }

  def using(properties: List[Property[T]]*): List[Property[T]] = {
    properties.toList.flatten
  }

  def nextWithIn[U <: AnyRef](properties: List[Property[T]])(implicit uTag: ClassTag[U]): Property[T] = {
    this.windowWithIn[U](0, 1, properties.toList)
  }

  def prevWithIn[U <: AnyRef](property: Property[T]*)(implicit uTag: ClassTag[U]): Property[T] = {
    this.windowWithIn[U](-1, 0, property.toList)
  }

  def nextOf(properties: List[Property[T]]): Property[T] = {
    window(0, 1)(properties)
  }

  def prevOf(properties: List[Property[T]]): Property[T] = {
    window(0, 1)(properties)
  }

  def relationalProperty[U <: AnyRef](implicit uTag: ClassTag[U]): Property[T] = {
    val fts: List[Property[U]] = this.datamodel.getPropertiesForType[U]
    relationalProperty[U](fts)
  }

  def relationalProperty[U <: AnyRef](ls: List[Property[U]])(implicit uTag: ClassTag[U]): Property[T] = {
    val fts = this.datamodel.getPropertiesForType[U]
    new RelationalFeature[T, U](this.datamodel, fts)
  }
}
