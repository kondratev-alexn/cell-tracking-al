package cell_tracking;

import ij.ImagePlus;
import ij.plugin.filter.RankFilters;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import inra.ijpb.morphology.Strel;
import inra.ijpb.morphology.Morphology.Operation;


/* Class for different image processing functions, like maybe filtering for certain processors, or normalizing the image */
public class ImageFunctions {

	/* normalizes image from [ip.min,ip.max] to [minValue, maxValue]. Works in place.  */
	public static void normalize(ImageProcessor ip, float minValue, float maxValue) {
		//find max and min first
		float ipmin = Float.MAX_VALUE, ipmax = Float.MIN_VALUE;
		float v;
		for (int i=0; i<ip.getPixelCount(); i++) {
			v = ip.getf(i);
			if (v < ipmin) ipmin = v;
			if (v > ipmax) ipmax = v;
		}
		
		// normalize
		for (int i=0; i<ip.getPixelCount(); i++) {
			v = ip.getf(i);
			ip.setf(i, (v-ipmin)/(ipmax-ipmin) * (maxValue - minValue) + minValue);
		}		
	}
	
	/* leave areas with values between minValue and maxValue */
	public static void threshold(ImageProcessor ip, double minValue, double maxValue) {
		int w = ip.getWidth(), h = ip.getHeight();
		for (int y=0; y < h; y++) {
			for (int x=0; x < w; x++) {
				 /*if (ip.getf(x, y) < minValue)
					ip.setf(x, y, (float)minValue);
				else if (ip.getf(x, y) > maxValue)
					ip.setf(x, y, (float)maxValue); */
				if (ip.getf(x, y) < minValue || ip.getf(x,y) > maxValue)
					ip.setf(x, y, 0);
				else ip.setf(x, y, 255);				
			}
		}
	}
	
	/* computes AND of two float-binary images and saves result into ip1 */
	public static void AND(ImageProcessor ip1, ImageProcessor ip2) {
		//if (ip1.getPixelCount() != ip2.getPixelCount()) return;
		for (int i=0; i<ip1.getPixelCount(); i++) {			
			ip1.setf(i, ip1.get(i)&ip2.get(i));
		}
	}
	
	/* computes OR of two float-binary images and saves result into ip1 */
	public static void OR(ImageProcessor ip1, ImageProcessor ip2) {
		//if (ip1.getPixelCount() != ip2.getPixelCount()) return;
		for (int i=0; i<ip1.getPixelCount(); i++) {			
			if (ip1.getf(i) > 10 || ip2.getf(i) > 10) ip1.setf(i, 255);
		}
	}
	
	/* returns the result of morphological operation "op" from MorpholibJ plugin with element type "shape" and "radius" */
	public static ImageProcessor operationMorph(ImageProcessor ip, Operation op, Strel.Shape shape, int radius) {
		Strel strel = shape.fromRadius(radius);
		return op.apply(ip, strel);
	}
	
	/* copies src image to dest image */
	public static void Copy(ImageProcessor dest, ImageProcessor src) {
		for (int i=0; i<src.getPixelCount(); i++) {			
			dest.setf(i, src.getf(i));
		}
	}
	
	// Canny edge detection with thresholds t1,t2 and sigma-gaussian blur
    public static ImageProcessor Canny(ImageProcessor ip, double sigma, double t1, double t2, int offx, int offy, boolean useOtsuThreshold)
    {
    	final int h = ip.getHeight();
    	final int w = ip.getWidth();
        ImageProcessor Gx = ip.duplicate();
        ImageProcessor Gy = ip.duplicate();
        Gaussian gaus = new Gaussian();
        
        gaus.GaussianDerivativeX(Gx, (float) sigma);
        gaus.GaussianDerivativeY(Gy, (float) sigma);
        final double Pi8=Math.PI/8;
        final double Pi4 = Math.PI/4;
        
        ImageProcessor Grad = ip.duplicate();
        gaus.GradientMagnitudeGaussian(Grad, (float) sigma);
        //Grad = operationMorph(Grad, Operation.EROSION, Strel.Shape.DISK, 1);
        normalize(Grad, 0, 255);
        ImageProcessor t = Grad.duplicate();
        double theta;
        double v1 = 0, v2 = 0;
        
        // Non-maximum supression
        for (int y = 1; y < h - 1; y++)
            for (int x = 1; x < w - 1; x++)
            {
                theta = Math.atan2(Gy.getf(x, y), Gx.getf(x, y));
                // theta = theta > Math.PI / 2 ? theta - Math.PI/2 : theta + Math.PI / 2; // make orthogonal angle from -pi:pi
                // first type of gradient orientation - not so good
                if (theta <= Pi8 && theta > -Pi8 || theta <= -7 * Pi8 || theta > 7 * Pi8) //gradient is horizontal
                {
                    if (t.getf(x, y) < t.getf(x-1, y)) Grad.setf(x, y, 0);
                    if (t.getf(x, y) < t.getf(x+1, y)) Grad.setf(x, y, 0);
                }
                else if (theta > 3 * Pi8 && theta <= 5 * Pi8 || theta > -5 * Pi8 && theta <= -3 * Pi8) //grad is vertical
                {
                    if (t.getf(x, y) < t.getf(x, y-1)) Grad.setf(x, y, 0);
                    if (t.getf(x, y) < t.getf(x, y+1)) Grad.setf(x, y, 0);
                }
                else if (theta > -7 * Pi8 && theta <= -5 * Pi8 || theta <= 3 * Pi8 && theta > Pi8) //grad is 135
                {
                    if (t.getf(x, y) < t.getf(x-1, y-1)) Grad.setf(x, y, 0);
                    if (t.getf(x, y) < t.getf(x+1, y+1)) Grad.setf(x, y, 0);
                }
                else //grad is 45
                {
                    if (t.getf(x, y) < t.getf(x+1, y-1)) Grad.setf(x, y, 0);
                    if (t.getf(x, y) < t.getf(x-1, y+1)) Grad.setf(x, y, 0);
                }
                /*
                
               if (theta > -Pi4 && theta <= 0 || theta > 3*Pi4 && theta <= Math.PI) { //e-se
            	   if (theta > 0) theta = theta - Math.PI;
            	   theta = - theta / Pi4; // norm to [0,1], 1 for se/nw
            	   v1 = (1-theta) * t.getf(x+1,y) + theta * t.getf(x+1,y+1);
            	   v2 = (1-theta) * t.getf(x-1,y) + theta * t.getf(x-1,y-1);
               }
               else if (theta > 0 && theta <= Pi4 || theta >= - Math.PI && theta <= -3*Pi4) { //e-ne
            	   if (theta < 0) theta = theta + Math.PI;
            	   theta = theta / Pi4; // [0,1], 1 for ne/sw
            	   v1 = (1-theta) * t.getf(x+1,y) + theta * t.getf(x+1,y-1);
            	   v2 = (1-theta) * t.getf(x-1,y) + theta * t.getf(x-1,y+1); 
               }
               else if (theta > Pi4 && theta <= 2*Pi4 || theta > -3*Pi4 && theta <= -2*Pi4) { //n-ne
            	   if (theta < 0) theta = theta + Math.PI;
            	   theta = (theta - Pi4) / Pi4; // [0,1], 1 for n/s 
            	   v1 = (1-theta) * t.getf(x+1,y-1) + theta * t.getf(x,y-1);
            	   v2 = (1-theta) * t.getf(x-1,y+1) + theta * t.getf(x,y+1);
               }
               else { // n-nw
            	   if (theta < 0) theta = theta + Math.PI;
            	   theta = (theta - Math.PI/2) / Pi4; // [0,1], 1 for nw/se
            	   v1 = (1-theta) * t.getf(x,y-1) + theta * t.getf(x-1,y-1);
            	   v2 = (1-theta) * t.getf(x,y+1) + theta * t.getf(x+1,y+1);
               }
               System.out.println(theta); 
        	   if (t.getf(x, y) < v1 || t.getf(x,y) < v2) Grad.setf(x,y, 0); 
        	   */
            }
        
        if (useOtsuThreshold) {
        	t2 = otsu(t.convertToByteProcessor(true).getHistogram(), t.getPixelCount());
        	t1 = t2/2.0;
        }

        // Double threshholding and tracking by hysteresis
        for (int y = 1; y < h-1; y++)
            for (int x = 1; x < w-1; x++)
            {
                if (Grad.getf(x, y) < t1) Grad.setf(x, y, 0);
                else if (Grad.getf(x, y) >= t1 && Grad.getf(x, y) < t2 && !CheckNearStrongEdge(Grad, x, y, t1, t2))
                	Grad.setf(x, y, 0);
                else ;// Grad[x, y] = 255;  
            }

        for (int y = 0; y < h; y++)
            for (int x = 0; x < offx; x++)
            { 
            	Grad.setf(x, y, 0); 
            	Grad.setf(w-1-x, y, 0);
            }
        for (int x = 0; x < w; x++)
            for (int y = 0; y < offy; y++)
            {
            	Grad.setf(x, y, 0);
            	Grad.setf(x, h-1-y, 0);
            }
        
        for (int y = 1; y < h-1; y++)
            for (int x = 1; x < w-1; x++)
            {
                if (Grad.get(x, y) != 0) Grad.setf(x, y, 255);
            }
        return Grad;
    }
    
    /* return interpolated value between two pixels, clockwise-oriented */
    private float interpolatePixelByAngle(float p1, float p2, double theta) {
    	theta = (theta + Math.PI) / Math.PI / 2.0; //norm to [0,1]
    	return 0;
    }

    /* Function for Canny edge detection algorithm. Return true if edge at the point (x,y) belongs to the strong edge. */
    private static boolean CheckNearStrongEdge(ImageProcessor grad, int x, int y, double t1, double t2)
    {
        if (grad.getf(x - 1, y - 1) >= t2) return true;
        if (grad.getf(x - 1, y + 1) >= t2) return true;
        if (grad.getf(x, y - 1) >= t2) return true;
        if (grad.getf(x, y + 1) >= t2) return true;
        if (grad.getf(x + 1, y - 1) >= t2) return true;
        if (grad.getf(x + 1, y + 1) >= t2) return true;
        if (grad.getf(x - 1, y) >= t2) return true;
        if (grad.getf(x + 1, y) >= t2) return true;
        return false;
    }
    
    /* calculates otsu threshold, taken from wikipedia. Histogram is 8-bit image histogtram, pixelsNumber is total number of pixels */
    public static float otsu(int[] histogram, int pixelsNumber) {
    	float sum = 0;
    	for (int i = 0; i < histogram.length; i++) //normally it will be 255 but sometimes we want to change step
    		sum += i * histogram[i];
    	float sumB = 0
    			, wB = 0
    			, wF = 0
    			, mB
    			, mF
    			, max = 0
    			, between
    			, threshold = 0;
    	for (int i = 0; i < 256; ++i) {
    		wB += histogram[i];
    		if (wB == 0)
    			continue;
    		wF = pixelsNumber - wB;
    		if (wF == 0)
    			break;
    		sumB += i * histogram[i];
    		mB = sumB / wB;
    		mF = (sum - sumB) / wF;
    		between = (float) (wB * wF * Math.pow(mB - mF, 2));
    		if (between > max) {
    			max = between;
    			threshold = i;
    		}
    	}
    	return threshold;
    }

	/* sets pixels below minValue to minValue, and higher than maxValue to maxValue */ 
	public static void clippingIntensity(ImageProcessor ip, float minValue, float maxValue) {
		for (int i=0; i<ip.getPixelCount(); i++) {
			if (ip.getf(i) < minValue) 
				ip.setf(i, minValue);
			else if (ip.getf(i) > maxValue) 
				ip.setf(i, maxValue);
		}
	}
	
	/* return binary image by 0-value thresholding */
	public static ImageProcessor getBinary(ImageProcessor fp) {
		int w = fp.getWidth(), h = fp.getHeight();
		ImageProcessor ip = new ByteProcessor(w, h);
		for (int y=0; y < h; y++) {
			for (int x=0; x < w; x++) {
				if (fp.getf(x,y) > 0)
					ip.setf(x, y, 255);
			}
		}
		return ip;
	}
	
	/* "convert" float binary image to byte binary image with values 0, 255 */
	public static void floatToByteBinary(ByteProcessor ip, FloatProcessor fp) {
		int w = ip.getWidth(), h = ip.getHeight();
		if (w != fp.getWidth() || h!= fp.getHeight())
			return;
		for (int y=0; y < h; y++) {
			for (int x=0; x < w; x++) {
				if (fp.getf(x,y) > 200 )
					ip.setf(x, y, 255);
			}
		}
	}
	
	/* "convert" byte binary image tofloat binary image with values 0, 255 */
	public static void byteToFloatBinary(FloatProcessor ip, ByteProcessor fp) {
		int w = ip.getWidth(), h = ip.getHeight();
		if (w != fp.getWidth() || h!= fp.getHeight())
			return;
		for (int y=0; y < h; y++) {
			for (int x=0; x < w; x++) {
				if (fp.getf(x,y) > 200 )
					ip.setf(x, y, 255);
			}
		}
	}
}
