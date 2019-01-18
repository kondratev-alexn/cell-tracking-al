package graph;

import java.io.Serializable;

public class TrackMitosisInfo implements Serializable {
	public int trackIndex;
	public int mitosisStartSlice;
	public int mitosisEndSlice;
	
	public TrackMitosisInfo(int trackIndex, int mitosisStartSlice, int mitosisEndSlice) {
		this.trackIndex = trackIndex;
		this.mitosisEndSlice = mitosisEndSlice;
		this.mitosisStartSlice = mitosisStartSlice;
	}
	
	@Override
	public String toString() {
		return "TrackMitosisInfo [trackIndex=" + trackIndex + ", mitosisStartSlice=" + mitosisStartSlice
				+ ", mitosisEndSlice=" + mitosisEndSlice + "]";
	}
}
