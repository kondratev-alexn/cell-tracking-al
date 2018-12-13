package properties;

import java.awt.Point;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;

import cellTracking.ImageComponentsAnalysis;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageProcessor;
import statistics.Area;
import statistics.CoefficientOfVariation;
import statistics.Max;
import statistics.Mean;
import statistics.Measure;
import statistics.Median;
import statistics.Slice;
import statistics.StandartDeviation;

/* Class for calculating, representing calculated track metrics and saving them into designated format. 
 * INPUT    - 2 channels of original data,
 * 			- .txt and .tif files representing ctc tracking result (obtained using different plugin) 
 * 
 * OUTPUT - txt properties header
 * 		  - file for each track, containing calculated properties
 * */

public class FormatSaver {

	public void calculate(TrackCTCMap tracks, StackDetection stack, ImagePlus ch1, ImagePlus ch2, ImagePlus ratio,
			String dir, String name) throws Exception {
		// ArrayList<Measure> statistics = new ArrayList<Measure>(5);
		// Slice sliceMeasure = new Slice();
		// Area area = new Area();
		// Mean mean = new Mean();
		// Median median = new Median();
		// StandartDeviation stdDev = new StandartDeviation();
		// CoefficientOfVariation cv = new CoefficientOfVariation();
		//
		// statistics.add(sliceMeasure);
		// statistics.add(area);
		// statistics.add(mean);
		// statistics.add(median);
		// statistics.add(stdDev);
		// statistics.add(cv);

		ArrayList<PropertiesColumn> columns = statisticColumns(ch1, ch2, ratio);

		// write header file
		makeHeaderFile(dir + '\\' + "header.txt", columns, ';');

		// create file for each track
		for (TrackCTC track : tracks.tracksMap().values()) {
			PrintWriter pw = new PrintWriter(
					new File(dir + "\\" + String.format(name + "StatiscicsTrack%04d.txt", track.index())));
			int startSlice = track.startSlice();
			int endSlice = track.endSlice();
			for (int slice = startSlice; slice <= endSlice; ++slice) {
				Roi roi = stack.detectionRoi(slice, track.index());

				// get a string in one file representing track stats
				// Double[] sliceRoiStats = calculateStatisticsSlice(statistics, roi, ch1,
				// slice);
				StringBuilder statStr = statisticsString(columns, roi, ';');

				// now write the string into corresponding track file
				pw.write(statStr.toString());
			}

			pw.close();
		}
	}

	private Double[] calculateStatisticsSlice(ArrayList<Measure> measurements, Roi roi, ImagePlus imp, int slice) {
		Double[] result = new Double[measurements.size()];
		for (int i = 0; i < measurements.size(); ++i) {
			double stat = measurements.get(i).calculate(roi, imp);
			result[i] = stat;
		}
		return result;
	}

	/*
	 * Properties list: Slice Area ch1: mean median stddev coefvar ch2: mean median
	 * stddev coefvar ratio: mean median, stddev, coefvar, 95%confIntervalMean
	 * mean1/mean2 if there is saturated pixel (??)
	 */

	private ArrayList<PropertiesColumn> statisticColumns(ImagePlus ch1, ImagePlus ch2, ImagePlus ratio) {
		ArrayList<PropertiesColumn> list = new ArrayList<PropertiesColumn>(15);

		Slice sliceMeasure = new Slice();
		Area area = new Area();

		Max max = new Max();
		Mean mean = new Mean();
		Median median = new Median();
		StandartDeviation stdDev = new StandartDeviation();
		CoefficientOfVariation cv = new CoefficientOfVariation();

		ImagePlus imp = null;
		String name = null;
		Measure measure = null;

		/* common */
		imp = ch1; // 0
		measure = sliceMeasure;
		name = "slice";
		PropertiesColumn prop = new PropertiesColumn(imp, name, measure);
		list.add(prop);

		imp = ch1; // 1
		measure = area;
		name = "area";
		prop = new PropertiesColumn(imp, name, measure);
		list.add(prop);

		/* ch1 */
		imp = ch1; // 2
		measure = max;
		name = "c1_max";
		prop = new PropertiesColumn(imp, name, measure);
		list.add(prop);
		
		imp = ch1; // 3
		measure = mean;
		name = "c1_mean";
		prop = new PropertiesColumn(imp, name, measure);
		list.add(prop);

		imp = ch1; // 4
		measure = median;
		name = "c1_median";
		prop = new PropertiesColumn(imp, name, measure);
		list.add(prop);

		imp = ch1;
		measure = stdDev;
		name = "c1_stddev";
		prop = new PropertiesColumn(imp, name, measure);
		list.add(prop);

		imp = ch1;
		measure = cv;
		name = "c1_coefvar";
		prop = new PropertiesColumn(imp, name, measure);
		list.add(prop);

		/* ch2 */
		imp = ch2; // 7
		measure = max;
		name = "c2_max";
		prop = new PropertiesColumn(imp, name, measure);
		list.add(prop);
		
		imp = ch2; // 8
		measure = mean;
		name = "c2_mean";
		prop = new PropertiesColumn(imp, name, measure);
		list.add(prop);

		imp = ch2;
		measure = median;
		name = "c2_median";
		prop = new PropertiesColumn(imp, name, measure);
		list.add(prop);

		imp = ch2;
		measure = stdDev;
		name = "c2_stddev";
		prop = new PropertiesColumn(imp, name, measure);
		list.add(prop);

		imp = ch2;
		measure = cv;
		name = "c2_coefvar";
		prop = new PropertiesColumn(imp, name, measure);
		list.add(prop);

		/* ratio */
		imp = ratio; // 12
		measure = max;
		name = "ratio_max";
		prop = new PropertiesColumn(imp, name, measure);
		list.add(prop);
		
		imp = ratio; // 13
		measure = mean;
		name = "ratio_mean";
		prop = new PropertiesColumn(imp, name, measure);
		list.add(prop);

		imp = ratio;
		measure = median;
		name = "ratio_median";
		prop = new PropertiesColumn(imp, name, measure);
		list.add(prop);

		imp = ratio; // 15
		measure = stdDev;
		name = "ratio_stddev";
		prop = new PropertiesColumn(imp, name, measure);
		list.add(prop);

		imp = ratio;
		measure = cv;
		name = "ratio_coefvar";
		prop = new PropertiesColumn(imp, name, measure);
		list.add(prop);

		return list;
	}

	private StringBuilder statisticsString(ArrayList<PropertiesColumn> list, Roi roi, char splitSymbol)
			throws Exception {
		StringBuilder result = new StringBuilder();
		double mean1 = 1, mean2 = 1, ratio_mean = 1, ratioSD = 1;
		int size = 0;
		for (int i = 0; i < list.size(); ++i) {
			Measure measure = list.get(i).measure;
			ImagePlus imp = list.get(i).imp;
			double v = measure.calculate(roi, imp);
			if (i == 1 && measure.name() == "Area")
				size = (int) v;
			if (i == 3 && measure.name() == "Mean")
				mean1 = v;
			if (i == 8 && measure.name() == "Mean")
				mean2 = v;
			if (i == 13 && measure.name() == "Mean")
				ratio_mean = v;
			if (i == 15 && measure.name() == "StdDev")
				ratioSD = v;
			String statString = measure.toString(v);
			result.append(statString + splitSymbol);
		}

		ImagePlus imp = list.get(list.size() - 1).imp; // ratio image here
		double v;
		String statString;

		/* also append 95% conf interval and mean1/mean2 value */

		/* 0.95 mean confidence interval */
		//double[] interval = confidenceInterval(imp, roi, 0.95);
		double[] interval = meanConfidenceInterval(ratio_mean, ratioSD, size, 0.95);
		v = interval[0];
		statString = String.format("%.3f", v);
		result.append(statString + splitSymbol);

		v = interval[1];
		statString = String.format("%.3f", v);
		result.append(statString + splitSymbol);

		/* mean1/mean2 */
		v = mean1 / mean2;
		statString = String.format("%.3f", v);
		result.append(statString);

		result.append(System.getProperty("line.separator"));
		return result;
	}

	private double[] meanConfidenceInterval(double mean, double stdDev, int size, double confidence) throws Exception {
		if (confidence < 0 || confidence > 1) {
			throw new Exception("Incorrect confidence value");
		}
		double alpha = (1 - confidence) / 2;
		double t_alpha = 1.65; // close for n = 200 to n=300, which is the case
		double error = t_alpha * stdDev / Math.sqrt(size);
		double[] res = new double[2];
		res[0] = mean - error;
		res[1] = mean + error;
		return res;
	}

	/* confidence in [0,1] */
	private double[] confidenceInterval(ImagePlus imp, Roi roi, double confidence) throws Exception {
		if (confidence < 0 || confidence > 1) {
			throw new Exception("Incorrect confidence value");
		}
		double t = (1 - confidence) / 2;
		ImageProcessor ip = imp.getStack().getProcessor(roi.getPosition() + 1);
		ArrayList<Double> hist = new ArrayList<Double>(100);
		double total = 0;
		for (Point p : roi) {
			if (Measure.isPointIn(p, ip)) {
				float v = ip.getf(p.x, p.y);
				if (Float.isFinite(v)) {
					hist.add((double) v);
					total += v;
				}
			}
		}
		double part = total * t;
		Collections.sort(hist);

		double sum = 0;
		int i = -1;
		do {
			sum += hist.get(++i);
		} while (sum < part);
		double lower = hist.get(i);

		i = hist.size();
		sum = 0;
		do {
			sum += hist.get(--i);
		} while (sum < part);
		double upper = hist.get(i);

		double[] res = new double[2];
		res[0] = lower;
		res[1] = upper;
		return res;
	}

	private StringBuilder statisticsStringOld(Double[] statistics, ArrayList<Measure> measurements, char splitSymbol) {
		StringBuilder result = new StringBuilder();
		if (statistics.length != measurements.size())
			return result;
		for (int i = 0; i < statistics.length - 1; ++i) {
			String statString = measurements.get(i).toString(statistics[i]);
			result.append(statString + splitSymbol);
		}
		result.append(measurements.get(statistics.length - 1).toString(statistics[statistics.length - 1]));

		result.append(System.getProperty("line.separator"));
		return result;
	}

	private void makeHeaderFile(String filePath, ArrayList<PropertiesColumn> measurements, char splitSymbol)
			throws FileNotFoundException {
		if (measurements.isEmpty())
			return;
		StringBuilder header = new StringBuilder();
		PrintWriter pw = new PrintWriter(new File(filePath));

		for (int i = 0; i < measurements.size(); ++i) {
			header.append(measurements.get(i).name + splitSymbol);
		}
		header.append("confidence_interval_lower" + splitSymbol);
		header.append("confidence_interval_upper" + splitSymbol);
		header.append("mean_ratio");

		pw.write(header.toString());
		pw.close();
	}

	private void makeHeaderFileOld(String filePath, ArrayList<Measure> measurements, char splitSymbol)
			throws FileNotFoundException {
		if (measurements.isEmpty())
			return;
		StringBuilder header = new StringBuilder();
		PrintWriter pw = new PrintWriter(new File(filePath));

		header.append(measurements.get(0).name());
		for (int i = 1; i < measurements.size(); ++i) {
			header.append(splitSymbol + measurements.get(i).name());
		}
		pw.write(header.toString());
		pw.close();
	}
}
