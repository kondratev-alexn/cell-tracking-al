package histogram;

import ij.process.ImageProcessor;
import cellTracking.ImageFunctions;

// class for float histogram
public class FloatHistogram {
	private int bins = 65536;
	private float minVal;
	private float maxVal;
	private float dv;

	private int[] histogram;
	private int pixelsCount;

	// ImageProcessor img;

	public FloatHistogram(ImageProcessor ip) {
		histogram = new int[bins];
		minVal = Float.MAX_VALUE;
		maxVal = Float.MIN_VALUE;
		float v;
		for (int i = 0; i < ip.getPixelCount(); i++) {
			v = ip.getf(i);
			if (minVal > v)
				minVal = v;
			if (maxVal < v)
				maxVal = v;
		}
		dv = (maxVal - minVal) / bins;

		fillBins(ip);
	}

	// public FloatHistogram(ImageProcessor ip, float minVal, float maxVal, int x0,
	// int y0, int x1, int y1) {
	// histogram = new int[bins];
	// this.minVal = minVal;
	// this.maxVal = maxVal;
	// dv = (maxVal - minVal) / bins;
	// float v;
	//
	// for (int i = 0; i < ip.getPixelCount(); i++) {
	// v = ip.getf(i);
	// histogram[getBinByValue(v, minVal, maxVal)] += 1;
	// }
	// pixelsCount = ip.getPixelCount();
	// }

	// get histogram for image ip in square region with center (xc, yc) and radius
	// 'radius'
	public FloatHistogram(ImageProcessor ip, float minVal, float maxVal, int xc, int yc, int radius) {
		histogram = new int[bins];
		this.minVal = minVal;
		this.maxVal = maxVal;
		dv = (maxVal - minVal) / bins;

		float v;
		pixelsCount = 0;
		for (int y = yc - radius; y <= yc + radius; y++)
			for (int x = xc - radius; x <= xc + radius; x++) {
				if (x < 0 || x >= ip.getWidth() || y < 0 || y >= ip.getHeight())
					continue;
				v = ip.getf(x, y);
				histogram[getBinByValue(v, minVal, maxVal)] += 1;
				++pixelsCount;
			}
	}

	/* creates histogram for mask region of image */
	public FloatHistogram(ImageProcessor ip, ImageProcessor mask) {
		histogram = new int[bins];
		minVal = Float.MAX_VALUE;
		maxVal = Float.MIN_VALUE;
		float v;
		for (int i = 0; i < ip.getPixelCount(); i++) {
			v = ip.getf(i);
			if (minVal > v)
				minVal = v;
			if (maxVal < v)
				maxVal = v;
		}
		dv = (maxVal - minVal) / bins;

		pixelsCount = 0;
		for (int y = 0; y < ip.getHeight(); y++)
			for (int x = 0; x < ip.getWidth(); x++) {
				if (mask.get(x, y) < 1)
					continue;
				v = ip.getf(x, y);
				histogram[getBinByValue(v, minVal, maxVal)] += 1;
				++pixelsCount;
			}
	}

	private void fillBins(ImageProcessor ip) {
		float v;
		for (int i = 0; i < ip.getPixelCount(); i++) {
			v = ip.getf(i);
			histogram[getBinByValue(v, minVal, maxVal)] += 1;
		}
		pixelsCount = ip.getPixelCount();
	}

	private int getBinByValue(float v, float minVal, float maxVal) {
		// System.out.format("v = %f, min = %f, max = %f %n", v, minVal, maxVal);
		if (v < minVal)
			return 0;
		if (v >= maxVal)
			return bins - 1;
		float dv = (maxVal - minVal) / bins;
		return (int) (v / dv);
	}

	public float getAverageValue() {
		float avrg = 0;
		int count = 0;
		for (int i = 0; i < bins; i++) {
			avrg += (i + 0.5) * dv * histogram[i];
			++count;
		}
		return avrg / count;
	}

	public float otsuThreshold() {
		float sum = 0;
		for (int i = 0; i < histogram.length; i++) // normally it will be 255 but sometimes we want to change step
			sum += i * dv * histogram[i];
		float sumB = 0, wB = 0, wF = 0, mB, mF, max = 0, between, threshold = 0;
		for (int i = 0; i < bins; ++i) {
			wB += histogram[i];
			if (wB == 0)
				continue;
			wF = pixelsCount - wB;
			if (wF == 0)
				break;
			sumB += i * dv * histogram[i];
			mB = sumB / wB;
			mF = (sum - sumB) / wF;
			between = (float) (wB * wF * (mB - mF) * (mB - mF));
			if (between > max) {
				max = between;
				threshold = i;
			}
		}
		return (threshold + 0.5f) * dv;
	}

}
