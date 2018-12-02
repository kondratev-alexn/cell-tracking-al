package properties;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;

import cellTracking.ImageComponentsAnalysis;
import ij.ImagePlus;
import ij.gui.Roi;
import statistics.Area;
import statistics.CoefficientOfVariation;
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

	public void calculate(TrackCTCMap tracks, StackDetection stack, ImagePlus imp, String dir) throws FileNotFoundException {
		ArrayList<Measure> statistics = new ArrayList<Measure>(5);
		Slice sliceMeasure = new Slice();
		Area area = new Area();
		Mean mean = new Mean();
		Median median = new Median();
		StandartDeviation stdDev = new StandartDeviation();
		CoefficientOfVariation cv = new CoefficientOfVariation();

		statistics.add(sliceMeasure);
		statistics.add(area);
		statistics.add(mean);
		statistics.add(median);
		statistics.add(stdDev);
		statistics.add(cv);
		
		//write header file 		
		makeHeaderFile(dir + '\\' + "header.txt", statistics, ';');
		
		//create file for each track
		for (TrackCTC track : tracks.tracksMap().values()) {
			PrintWriter pw = new PrintWriter(new File(dir + "\\" + String.format("statiscics_track_%d.txt", track.index())));
			int startSlice = track.startSlice();
			int endSlice = track.endSlice();
			for (int slice = startSlice; slice <= endSlice; ++slice) {
				Roi roi = stack.detectionRoi(slice, track.index());
				
				// get a string in one file representing track stats
				Double[] sliceRoiStats = calculateStatisticsSlice(statistics, roi, imp, slice);
				StringBuilder statStr = statisticsString(sliceRoiStats, statistics, ';');
				
				//now write the string into corresponding track file
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
	
	private StringBuilder statisticsString(Double[] statistics, ArrayList<Measure> measurements, char splitSymbol) {
		StringBuilder result = new StringBuilder();
		if (statistics.length != measurements.size())
			return result;
		for (int i=0; i< statistics.length - 1; ++i) {
			String statString = measurements.get(i).toString(statistics[i]);
			result.append(statString+splitSymbol);
		}
		result.append(measurements.get(statistics.length-1).toString(statistics[statistics.length-1]));
		result.append(System.getProperty("line.separator"));
		return result;
	}
	
	private void makeHeaderFile(String filePath, ArrayList<Measure> measurements, char splitSymbol) throws FileNotFoundException {
		if (measurements.isEmpty())
			return;
		StringBuilder header = new StringBuilder();
		PrintWriter pw = new PrintWriter(new File(filePath));

		header.append(measurements.get(0).name());
		for (int i=1; i<measurements.size(); ++i) {
			header.append(splitSymbol + measurements.get(i).name());
		}			
		pw.write(header.toString());		
		pw.close();
	}
}
