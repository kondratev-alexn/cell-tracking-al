package properties;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

import ij.gui.Roi;

public class TrackCTCMap {
	/* mapping between track indexes (intensity) and track structure */
	private HashMap<Integer, TrackCTC> _tracks;
	/* filled stack to retrieve actual detections */
	StackDetection filledStack;
	
	public TrackCTCMap(String ctcResultTxt, StackDetection filledStack) {
		this.filledStack = filledStack;
		_tracks = new HashMap<Integer, TrackCTC>(10);
		fillTracks(ctcResultTxt, filledStack);
	}
	
	/* create tracks and fill map while checking for correctness. Stack must be filled beforehand */
	private void fillTracks(String ctcResultTxt, StackDetection filledStack) {
		try(BufferedReader br = new BufferedReader(new FileReader(ctcResultTxt))) {
		    for(String line; (line = br.readLine()) != null; ) {
		        // process the line.
				TrackCTC track = new TrackCTC(line, filledStack);
				
				if (_tracks.containsKey(track.index())) 
					throw new Exception(String.format("Track with index %d already exists.", track.index()));
				
				_tracks.put(track.index(), track);
		    }
		    
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public Roi getDetectionRoi(int trackIndex, int slice) {
		return filledStack.detectionRoi(slice, trackIndex);
	}
	
	public HashMap<Integer, TrackCTC> tracksMap() {
		return _tracks;
	}
}
