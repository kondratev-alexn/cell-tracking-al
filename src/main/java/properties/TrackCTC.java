package properties;

import java.util.ArrayList;

import ij.ImagePlus;
import ij.gui.Roi;
import statistics.Measure;

// class representing a track in CTC format (components are in each slice, has parent track, child track (tracks), has start and end slice)
public class TrackCTC {
	private int _startSlice;
	private int _endSlice;
	
	/* indexes are the same as intensity in result ctc sequences */
	private int _index;
	private int _parentIndex;
	
	public ArrayList<Integer> _childIndexes;
	
	public TrackCTC(String trackTxtLine, StackDetection filledStack) throws Exception {
		String[] split = trackTxtLine.split(" ");
		if (split.length != 4) {
			throw new Exception(String.format("Incorrent line in ctc tracks file"));
		}
		
		_index = Integer.parseInt(split[0]);
		_startSlice = Integer.parseInt(split[1]);
		_endSlice = Integer.parseInt(split[2]);
		_parentIndex = Integer.parseInt(split[3]);
		_childIndexes = new ArrayList<Integer>(1);
		
		if (_startSlice >_endSlice)
			throw new Exception(String.format("Incorrect slice interval for track %d", _index));
		
//		System.out.println(trackTxtLine);
//		System.out.format("index %d start%d end %d %n", _index, _startSlice, _endSlice);
		
		if (!filledStack.checkTrackCorrectness(_index, _startSlice, _endSlice))
			throw new Exception(String.format("Track %d is incorrect: detection is missing from one or more slices", _index));
		
	}
	
	public int index() {
		return _index;
	}
	
	public int parentIndex() {
		return _parentIndex;
	}
	
	public int startSlice() {
		return _startSlice;
	}
	
	public int endSlice() {
		return _endSlice;
	}	
}
