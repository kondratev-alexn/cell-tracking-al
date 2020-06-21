package networkDeploy;

import java.io.IOException;
import java.lang.Math;

import org.apache.commons.math3.analysis.function.Exp;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.modelimport.keras.exceptions.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.exceptions.UnsupportedKerasConfigurationException;
import org.deeplearning4j.nn.transferlearning.FineTuneConfiguration;
import org.deeplearning4j.nn.transferlearning.TransferLearning;
import org.deeplearning4j.nn.transferlearning.TransferLearning.GraphBuilder;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.io.ClassPathResource;

import cellTracking.ImageComponentsAnalysis;
import cellTracking.ImageFunctions;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import inra.ijpb.binary.BinaryImages;
import inra.ijpb.binary.ChamferWeights;
import inra.ijpb.watershed.MarkerControlledWatershedTransform2D;

public class UNetSegmentation {
	ComputationGraph model;
	
	// for segmentation
	private FloatProcessor cellMask;
	private FloatProcessor cellMarkers;
	
	// for mitosis usage later...
	private FloatProcessor other;
	
	// processing modes
	public static final int SINGLE_BINARY=0, MASK_WITH_MARKERS=1;
	
	
	public UNetSegmentation(String modelFile) throws IOException, UnsupportedKerasConfigurationException, InvalidKerasConfigurationException {
		setModel(modelFile);
	}
	
	public void setModel(String modelFile) throws IOException, UnsupportedKerasConfigurationException, InvalidKerasConfigurationException {
		String simpleMlp = new ClassPathResource(modelFile).getFile().getPath();		
		model = KerasModelImport.importKerasModelAndWeights(simpleMlp, false);		
		System.out.println(model.summary());
	}
	
	public ImageProcessor binarySegmentation(ImageProcessor ip, float threshold) throws IOException, InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
		ImageProcessor prediction = predictMaskSingleImage(ip);
		ImageProcessor binary = ImageFunctions.maskThresholdMoreThan(prediction, threshold, null);
		return binary;
	}
	
	/* apply softmax */
//	public ImageStack softmax(ImageStack stack) {
//		int w = stack.getWidth();
//		int h = stack.getHeight();
//		int s = stack.getSize();
//		ImageStack res = new ImageStack(w, h, s);
//		for (int y=0; y<h; ++y)
//			for (int x=0; x<w; ++x) {
//				float temp[] = new float[s];				
//				for (int c=1; c<=s; ++c) {
//				// softmax is 
//					temp[c] = Exp(stack.getpi)
//				}
//			}
//		
//		return res;
//	}
	
	/** apply softmax for 3-channel images */
	public ImageStack softmax_3c(ImageStack stack) {
	int w = stack.getWidth();
	int h = stack.getHeight();
	int s = stack.getSize(); //s must be 3 there!!
	
	ImageStack res = new ImageStack(w, h, s);
	ImageProcessor ip1 = stack.getProcessor(1).duplicate();
	ImageProcessor ip2 = stack.getProcessor(2).duplicate();
	ImageProcessor ip3 = stack.getProcessor(3).duplicate();;
	Exp exp = new Exp();
	for (int y=0; y<h; ++y)
		for (int x=0; x<w; ++x) {
			float x1,x2,x3,sum,v1,v2,v3;
			x1 = (float) exp.value(ip1.getf(x,y));
			x2 = (float) exp.value(ip2.getf(x,y));
			x3 = (float) exp.value(ip3.getf(x,y));
			sum = x1+x2+x3;
			v1 = x1/sum;
			v2 = x2/sum;
			v3 = x3/sum;
			ip1.setf(x, y, v1);
			ip2.setf(x, y, v2);
			ip3.setf(x, y, v3);
		}
	res.setProcessor(ip1, 1);
	res.setProcessor(ip2, 2);
	res.setProcessor(ip3, 3);	
	return res;
}
	
	/* return watershed components, mitosis start and mitosis end ips */
	public ImageProcessor[] binarySegmentationFromMaskWithMarkers(ImageStack stack, float threshold, float thresholdMitosis) {
		ImageStack[] maskAndMarkers = predictMaskMarkersMitosis3Stack(stack);
		ImageProcessor mask = maskAndMarkers[0].getProcessor(1);
		ImageProcessor markers = maskAndMarkers[1].getProcessor(2);

		ImageProcessor binaryMask = ImageFunctions.maskThresholdMoreThan(mask, threshold, null);
		ImageProcessor binaryMarkers = ImageFunctions.maskThresholdMoreThan(markers, threshold, null);
		
		ImageProcessor binaryMitosisStart = ImageFunctions.maskThresholdMoreThan(maskAndMarkers[1].getProcessor(1), thresholdMitosis, null);
		ImageProcessor binaryMitosisEnd = ImageFunctions.maskThresholdMoreThan(maskAndMarkers[1].getProcessor(2), thresholdMitosis, null);
		
		ImageComponentsAnalysis comps = new ImageComponentsAnalysis(binaryMarkers, markers, true);
		ImageProcessor labeledMarkers = comps.getSinglePixelComponents();
		
		ChamferWeights weights = ChamferWeights.BORGEFORS;
		final ImageProcessor dist = BinaryImages.distanceMap(binaryMask, weights.getFloatWeights(), true);
		dist.invert();		
		
		MarkerControlledWatershedTransform2D watershed = new MarkerControlledWatershedTransform2D(dist, labeledMarkers,
				binaryMask, 4);
		ImageProcessor res_ips[] = new ImageProcessor[3];
		res_ips[0] = watershed.applyWithPriorityQueue();
		res_ips[1] = binaryMitosisStart;
		res_ips[2] = binaryMitosisEnd;
		return res_ips;
	}

	public FloatProcessor predictMaskSingleImage(ImageProcessor ip) throws IOException, InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
		normalize01((FloatProcessor) ip);		
		
		INDArray input = imageProcessor2INDArray(ip);		
		INDArray[] prediction = model.output(input);
		
		INDArray singlePrediction = prediction[0];		
		ImageProcessor res = INDArray2ImageProcessor(singlePrediction);
		
		return res.convertToFloatProcessor();
	}
	
	public ImageStack[] predictMaskMarkersMitosis3Stack(ImageStack stack) {
		stack = normalizeStack01(stack);
		INDArray input = imageStack2INDArray(stack);
		
		ComputationGraph graph = new TransferLearning.GraphBuilder(model)
				.removeVertexAndConnections("output_softmax")
//				.removeVertexAndConnections("output_sigmoid")
//				.setOutputs("conv2d_80", "conv2d_79")
//				.setOutputs("activation_55")
				.setOutputs("output_sigmoid", "last_conv_softmax")
				.build();
		INDArray[] prediction = graph.output(input);
		INDArray predSigmoid = prediction[0];
		INDArray predBeforeSoftmax = prediction[1];
//		System.out.println(predSigmoid.shapeInfoToString());
//		System.out.println(predBeforeSoftmax.shapeInfoToString());
		
		ImageStack binaryMaskWithMarkers = INDArray2ImageStack(predSigmoid);
		ImageStack categoryMitosisMasks = INDArray2ImageStack(predBeforeSoftmax);
		
		ImageStack[] res = new ImageStack[2];
		res[0] = binaryMaskWithMarkers;
		res[1] = softmax_3c(categoryMitosisMasks); //also apply softmax
		return res;
	}

	private INDArray imageProcessor2INDArray(ImageProcessor ip) {
		int w = ip.getWidth();
		int h = ip.getHeight();
		/* Input shape is [minibatchSize, layerInputDepth, inputHeight, inputWidth] */
		INDArray res = Nd4j.zeros(1, 1, h, w);
		for (int y=0; y<h; ++y)
			for (int x=0; x<w; ++x) {
					res.putScalar(0,0,x,y, ip.getf(x, y));
			}
		return res;
	}
	
	private INDArray imageStack2INDArray(ImageStack stack) {
		int w = stack.getWidth();
		int h = stack.getHeight();
		int c = stack.getSize();
		/* Input shape is [minibatchSize, layerInputDepth, inputHeight, inputWidth] */
		INDArray res = Nd4j.zeros(1, c, h, w);
		for (int z=0; z<c; ++z) {
			ImageProcessor ip = stack.getProcessor(z+1);
			for (int y=0; y<h; ++y)
				for (int x=0; x<w; ++x) {					
						res.putScalar(0,z,y,x, ip.getf(x, y));
//						System.out.println(ip.getf(x, y));
				}
		}
		return res;
	}
	
	private ImageProcessor INDArray2ImageProcessor(INDArray arr) {
		long[] shape = arr.shape();
		int w = (int) shape[3];
		int h = (int) shape[2];
		ImageProcessor ip = new FloatProcessor(w,h);
		for (int y=0; y<h; ++y)
			for (int x=0; x<w; ++x) {
				ip.putPixelValue(x, y, arr.getDouble(0, 0, x, y));
			}
		return ip;
	}
	
	private ImageStack INDArray2ImageStack(INDArray arr) {
		long[] shape = arr.shape();
		int w = (int) shape[3];
		int h = (int) shape[2];
		int c = (int) shape[1];
		double v;
		ImageStack stack = new ImageStack(w,h,c);
		for (int z=0; z<c; ++z) {
			ImageProcessor slice = new FloatProcessor(w,h);
			for (int y=0; y<h; ++y)
				for (int x=0; x<w; ++x) {
					v = arr.getDouble(0, z, y, x);
					slice.putPixelValue(x, y, v);
//					if (v>0.1) System.out.println(v);
				}
			stack.setProcessor(slice, z+1);
		}
		return stack;
	}
	
	@Deprecated
	private ImageProcessor patch(ImageProcessor ip, int x0, int y0, int width, int height) {
		ImageProcessor res = new FloatProcessor(width, height);
		for (int y=y0; y<y0+height; ++y)
			for (int x=x0; x<x0+width; ++x) {
				res.putPixelValue(x-x0, y-y0, ip.getf(x,y));
			}		
		return res;
	}
	
	private ImageStack normalizeStack01(ImageStack stack) {
		ImageStack res = new ImageStack(stack.getWidth(), stack.getHeight(), stack.getSize());
		double min=Float.MAX_VALUE, max = Float.MIN_VALUE, v;
		for (int z=1; z<=stack.getSize(); ++z) {
			ImageProcessor ip = stack.getProcessor(z);
			for (int y=0; y<stack.getHeight(); ++y)
				for (int x=0; x<stack.getWidth(); ++x) { 
					v = ip.getf(x, y);
					if (v>max) 
						max=v;
					if (v<min)
						min=v;
				}
		}
		for (int z=1; z<=stack.getSize(); ++z) {
			ImageProcessor ip = stack.getProcessor(z).convertToFloat();
			for (int y=0; y<stack.getHeight(); ++y)
				for (int x=0; x<stack.getWidth(); ++x) { 
					v = ip.getf(x, y);
					ip.setf(x, y, (float) ((v-min)/(max-min)));
//					System.out.println((v-min)/(max-min));
//					System.out.println(ip.getf(x, y));
				}
			res.setProcessor(ip, z);
		}
		return res;
	}
		
	private void normalize01(FloatProcessor fp) {
		ImageFunctions.normalize(fp, 0, 1);
		for(int i=0; i<fp.getPixelCount(); ++i) {
			fp.setf(i, fp.getf(i));
		}
	}
	
	public static void main(String[] args) {	
		String path = "G:\\Tokyo\\Confocal\\181221-q8156901-tiff\\c2\\181221-q8156901hfC2c2.tif";
		String path2 = "G:\\Tokyo\\watershed_results\\c0010901_easy_ex\\c0010901_easy_ex.tif";
		ImagePlus fluo_1 = IJ.openImage(path2);
			
		
		ImageStack stack = fluo_1.getStack();
		ImageProcessor ip = stack.getProcessor(5);
		FloatProcessor fp = ip.convertToFloatProcessor();
		
		String model_name = "3stack_with_mitosis_model.h5";

		
		ImagePlus slice = new ImagePlus("Slice for Testing", fp);
		new ImageJ();
		slice.show();		
//			try {
//				ImageProcessor segmented = predict(fp);
//				ImagePlus res = new ImagePlus("Segmentation", segmented);
//				res.show();
//			} catch (IOException | InvalidKerasConfigurationException | UnsupportedKerasConfigurationException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
		System.out.println("Finished");
	}
}

