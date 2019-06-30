package tracks;

import java.util.Collection;
import java.util.Iterator;
import java.util.TreeMap;

import graph.Node;

/** 
 * Class that manages all tracklets and their storage
 * Should only be modified through tracks
 */
public class TrackletStorage extends TreeMap<Integer, Tracklet> {
	
	/* for the sake of serialization... */
	private static final long serialVersionUID = -5605184113581024555L;
	private int nextId;
	
	public TrackletStorage() {
		nextId = 1;
	}
	
	/** 
	 * 
	 * @param tracklet Tracklet to be added
	 * @return id of the added tracklet
	 */
	public int addTracklet(Tracklet tracklet) {
		int id = nextId;
		++nextId;
		put(id, tracklet);
		return id;
	}
	
	/**
	 * 
	 * @param trackletId id of the tracklet to append node to. Creates new tracklet if id is not found
	 * @param node Node to be added
	 */
	public void addNode(int trackletId, Node node) {
		Tracklet tracklet = get(trackletId);
		//tracklet can be null if id is not in Keys;
		tracklet.add(node);		
	}
}
