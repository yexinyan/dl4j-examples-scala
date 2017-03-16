package org.deeplearning4j.examples.misc.presave

import org.deeplearning4j.datasets.iterator.AsyncDataSetIterator
import org.deeplearning4j.eval.Evaluation
import org.deeplearning4j.nn.api.OptimizationAlgorithm
import org.deeplearning4j.nn.conf.{NeuralNetConfiguration, Updater}
import org.deeplearning4j.nn.conf.inputs.InputType
import org.deeplearning4j.nn.conf.layers.{ConvolutionLayer, DenseLayer, OutputLayer, SubsamplingLayer}
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.deeplearning4j.nn.weights.WeightInit
import org.deeplearning4j.optimize.listeners.ScoreIterationListener
import org.nd4j.linalg.activations.Activation
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.dataset.{DataSet, ExistingMiniBatchDataSetIterator}
import org.nd4j.linalg.lossfunctions.LossFunctions
import org.slf4j.LoggerFactory

import java.io.File

/**
  *
  * YOU NEED TO RUN PreSave first
  * before using this class.
  *
  * This class demonstrates how to  use a pre saved
  * dataset to minimize time spent loading data.
  * This is critical if you want to have ANY speed
  * with deeplearning4j.
  *
  * Deeplearning4j does not force you to use a particular data format.
  * Unfortunately this flexibility means that many people get training wrong.
  *
  * With more flexibility comes more complexity. This class demonstrates how
  * to minimize time spent training while using an existing iterator and an existing dataset.
  *
  * We use an {@link AsyncDataSetIterator}  to load data in the background
  * and {@link PreSave} to pre save the data to 2 specified directories,
  * trainData and testData
  *
  *
  *
  *
  * Created by agibsonccc on 9/16/15.
  * Modified by dmichelin on 12/10/2016 to add documentation
  */
object LoadPreSavedLenetMnistExample {
  private val log = LoggerFactory.getLogger(LoadPreSavedLenetMnistExample.getClass)

  @throws[Exception]
  def main(args: Array[String]) {
    val nChannels = 1 // Number of input channels
    val outputNum = 10 // The number of possible outcomes
    val nEpochs = 1 // Number of training epochs
    val iterations = 1 // Number of training iterations
    val seed = 123 //

    /*
      Load the pre saved data. NOTE: YOU NEED TO RUN PreSave first.

     */ log.info("Load data....")
    /**
      * Note the {@link ExistingMiniBatchDataSetIterator}
      * takes in a pattern of "mnist-train-%d.bin"
      * and "mnist-test-%d.bin"
      *
      * The %d is an integer. You need a %d
      * as part of the template in order to have
      * the iterator work.
      * It uses this %d integer to
      * index what number it is in the current dataset.
      * This is how pre save will save the data.
      *
      * If you still don't understand what this is, please see an example with printf in c:
      * http://www.sitesbay.com/program/c-program-print-number-pattern
      * and in java:
      * https://docs.oracle.com/javase/tutorial/java/data/numberformat.html
      */
    val existingTrainingData = new ExistingMiniBatchDataSetIterator(new File("trainFolder"), "mnist-train-%d.bin")
    //note here that we use as ASYNC iterator which loads data in the background, this is crucial to avoid disk as a bottleneck
    //when loading data
    val mnistTrain = new AsyncDataSetIterator(existingTrainingData)
    val existingTestData = new ExistingMiniBatchDataSetIterator(new File("testFolder"), "mnist-test-%d.bin")
    val mnistTest = new AsyncDataSetIterator(existingTestData)
    /*
         Construct the neural network
     */
    log.info("Build model....")
    val conf = new NeuralNetConfiguration.Builder()
      .seed(seed)
      .iterations(iterations) // Training iterations as above
      .regularization(true)
      .l2(0.0005)
      /*
         Uncomment the following for learning decay and bias
       */
      .learningRate(.01) //.biasLearningRate(0.02)
      //.learningRateDecayPolicy(LearningRatePolicy.Inverse).lrPolicyDecayRate(0.001).lrPolicyPower(0.75)
      .weightInit(WeightInit.XAVIER)
      .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
      .updater(Updater.NESTEROVS).momentum(0.9)
      .list
      .layer(0, new ConvolutionLayer.Builder(5, 5)
         //nIn and nOut specify depth. nIn here is the nChannels and nOut is the number of filters to be applied
        .nIn(nChannels)
        .stride(1, 1)
        .nOut(20)
        .activation(Activation.IDENTITY)
        .build)
      .layer(1, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX)
        .kernelSize(2, 2)
        .stride(2, 2)
        .build)
      .layer(2, new ConvolutionLayer.Builder(5, 5) //Note that nIn need not be specified in later layers
        .stride(1, 1)
        .nOut(50)
        .activation(Activation.IDENTITY)
        .build)
      .layer(3, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX)
        .kernelSize(2, 2)
        .stride(2, 2)
        .build)
      .layer(4, new DenseLayer.Builder()
        .activation(Activation.RELU)
        .nOut(500)
        .build)
      .layer(5, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
        .nOut(outputNum)
        .activation(Activation.SOFTMAX)
        .build)
      .setInputType(InputType.convolutionalFlat(28, 28, 1)) //See note below
      .backprop(true).pretrain(false).build
    /*
    Regarding the .setInputType(InputType.convolutionalFlat(28,28,1)) line: This does a few things.
    (a) It adds preprocessors, which handle things like the transition between the convolutional/subsampling layers
        and the dense layer
    (b) Does some additional configuration validation
    (c) Where necessary, sets the nIn (number of input neurons, or input depth in the case of CNNs) values for each
        layer based on the size of the previous layer (but it won't override values manually set by the user)

    InputTypes can be used with other layer types too (RNNs, MLPs etc) not just CNNs.
    For normal images (when using ImageRecordReader) use InputType.convolutional(height,width,depth).
    MNIST record reader is a special case, that outputs 28x28 pixel grayscale (nChannels=1) images, in a "flattened"
    row vector format (i.e., 1x784 vectors), hence the "convolutionalFlat" input type used here.
    */

    val model = new MultiLayerNetwork(conf)
    model.init()

    log.info("Train model....")
    model.setListeners(new ScoreIterationListener(1))
    for (i <- 0 until nEpochs) {
      model.fit(mnistTrain)
      log.info("*** Completed epoch {} ***", i)

      log.info("Evaluate model....")
      val eval: Evaluation = new Evaluation(outputNum)
      while (mnistTest.hasNext) {
        val ds: DataSet = mnistTest.next
        val output: INDArray = model.output(ds.getFeatureMatrix, false)
        eval.eval(ds.getLabels, output)
      }
      log.info(eval.stats)
      mnistTest.reset()
    }
    log.info("****************Example finished********************")
  }

}