package networkDeploy;

import java.io.IOException;

import org.deeplearning4j.nn.modelimport.keras.exceptions.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.exceptions.UnsupportedKerasConfigurationException;
import org.scijava.log.LogService;
import org.scijava.log.StderrLogService;

import cellTracking.ImageComponentsAnalysis;
import cellTracking.ImageFunctions;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import inra.ijpb.binary.BinaryImages;
import inra.ijpb.binary.ChamferWeights;
import inra.ijpb.watershed.MarkerControlledWatershedTransform2D;

/* class for segmentation models testing */
public class TestModels {
	private LogService log;

	public TestModels() {
		log = new StderrLogService();
	}
	
	public static void main(String[] args) {	
		String path2 = "G:\\Tokyo\\watershed_results\\c0010901_easy_ex\\c0010901_easy_ex.tif";
		ImagePlus fluo_1 = IJ.openImage(path2);
		
		ImageStack stack = fluo_1.getStack();
		ImageStack testStack = new ImageStack(stack.getWidth(), stack.getHeight(), 3);
		for (int i=1; i<=3; ++i) {
			testStack.setProcessor(stack.getProcessor(i), i);
		}
		
		String model_name = "3stack_with_mitosis_full.h5";
//		model_name = "unet_confocal_fluo_32_boundary_loss_model_full.h5";

		String stack_full = "unet_confocal_fluo_watershed_test_full.h5";
		String resunet = "model_inout_512.h5";
		String resunet_mitosis = "model_fluo_resunet_mitosis_512_weights.h5";
		try {
			UNetSegmentation unet = new UNetSegmentation(resunet_mitosis);
			ImageStack[] maskAndMarkers = unet.predictMaskMarkersMitosis3Stack(testStack);
			ImageProcessor mask = maskAndMarkers[0].getProcessor(1);
			ImageProcessor markers = maskAndMarkers[0].getProcessor(2);
			
			ImageProcessor mitosisStart = maskAndMarkers[1].getProcessor(1);
			ImageProcessor mitosisEnd = maskAndMarkers[1].getProcessor(2);
			ImageProcessor mitosisOther = maskAndMarkers[1].getProcessor(3);

			ImageProcessor binaryMask = ImageFunctions.maskThresholdMoreThan(mask, 0.5f, null);
			ImageProcessor binaryMarkers = ImageFunctions.maskThresholdMoreThan(markers, 0.5f, null);
			
			ImageComponentsAnalysis comps = new ImageComponentsAnalysis(binaryMarkers, markers, true);
			ImageProcessor labeledMarkers = comps.getSinglePixelComponents();
			
			ChamferWeights weights = ChamferWeights.BORGEFORS;
			final ImageProcessor dist = BinaryImages.distanceMap(binaryMask, weights.getFloatWeights(), true);
			dist.invert();
			
			
			MarkerControlledWatershedTransform2D watershed = new MarkerControlledWatershedTransform2D(dist, labeledMarkers,
					binaryMask, 4);
			ImageProcessor res = watershed.applyWithPriorityQueue();
			ImagePlus imp = new ImagePlus("fdfd",res);
			imp.show();
			ImagePlus imp2 = new ImagePlus("mitosis",maskAndMarkers[1]);
			IJ.save(imp2, "G:/Tokyo/mitosis_res_before_softmax.tif");
			imp2.show();
			
		} catch (IOException | UnsupportedKerasConfigurationException | InvalidKerasConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println("Finished test");
	}
}
