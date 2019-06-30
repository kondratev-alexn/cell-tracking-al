package tracks;


/* Class describing a track in cell-tracking graph, for more convenient usage */
public class TrackAdj {
	
	private int firstSlice; 
	private int firstComponentIndex;
	private int lastSlice;
	
	private int length; //number of actual components in track
	
	private int startAdjIndex;
	private int endAdjIndex;
	
	private boolean endedOnMitosis;
	private boolean isWhiteBlobParent;
	
	TrackAdj(int startIndex, int endIndex, int firstSlice, int lastSlice, int firstComponentsIndex, int trackLength) {
		startAdjIndex = startIndex;
		endAdjIndex = endIndex;
		this.firstSlice = firstSlice;
		this.lastSlice = lastSlice;
		this.firstComponentIndex = firstComponentsIndex;
		endedOnMitosis = false;
		isWhiteBlobParent = false;
		length = trackLength;
	}
	
	public int getStartAdjIndex() {
		return startAdjIndex;
	}
	
	public int getEndAdjIndex() {
		return endAdjIndex;
	}
	
	public int getLength() {
		return length;
	}
	
	public void setEndedOnMitosys() {
		endedOnMitosis = true;
	}
	
	public void setAsWhiteBlobParent() {
		isWhiteBlobParent = true;
	}
	
	public boolean isWhiteBlobParent() {
		return isWhiteBlobParent;
	}
	
	public int getFirstSlice() {
		return firstSlice;
	}
	
	public int getLastSlice() {
		return lastSlice;
	}
	
	public int getFirstComponentIndex() {
		return firstComponentIndex;
	}
	
	public boolean isEndedOnMitosis() {
		return endedOnMitosis;
	}
	
	public void decreaseLength(int decreaseBy) {
		length -= decreaseBy;
	}
	
	public void changeValuesOnFirstComponentRemoval(int newFirstSlice, int newFirstComponentIndex, int newStartAdjIndex) {
		firstSlice = newFirstSlice;
		firstComponentIndex = newFirstComponentIndex;
		startAdjIndex = newStartAdjIndex;
	}
	
	@Override
	public String toString() {
		return "Track [" + startAdjIndex + ", " + endAdjIndex + "]";
	}
}
